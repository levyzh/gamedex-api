package com.gamedex.gamedex_api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class CommentController {

    // A "record" is Java shorthand for a class that just holds data — this
    // one line generates the field, constructor, and getter. This is the
    // shape of the POST request body: {"content": "..."}. We deliberately
    // do NOT accept the Comment entity as the body — it has an author field,
    // and a client could fill it in and post as someone else. This record
    // has only content, so there's nothing else TO send. (DTO = "data
    // transfer object", the general name for request/response shapes.)
    public record NewCommentRequest(String content) {}

    // Same shape for edits. It happens to be identical to NewCommentRequest
    // today, but they're separate on purpose: if edits ever need an extra
    // field, the create shape shouldn't change with it.
    public record EditCommentRequest(String content) {}

    // The shape GET responses actually send back. We do NOT return the
    // Comment entity directly anymore: it now holds a full Profile behind
    // getAuthor(), and serializing that would either expose more of the
    // profile than callers need or trigger a lazy-load Hibernate can't
    // satisfy outside a transaction. Instead we flatten just the two
    // author fields callers want (username, avatarUrl) onto a response
    // record. `from` is a small factory method — one place that knows how
    // to build this shape from a Comment, instead of repeating the mapping
    // everywhere we need it.
    public record CommentResponse(
            Long id,
            Integer gameId,
            String content,
            OffsetDateTime createdAt,
            OffsetDateTime editedAt,
            String authorUsername,
            String authorAvatarUrl) {

        public static CommentResponse from(Comment comment) {
            Profile author = comment.getAuthor();
            return new CommentResponse(
                    comment.getId(),
                    comment.getGameId(),
                    comment.getContent(),
                    comment.getCreatedAt(),
                    comment.getEditedAt(),
                    author.getUsername(),
                    author.getAvatarUrl());
        }
    }

    // The controller needs both repositories. We declare them as final
    // fields and take them in the constructor — and Spring, which
    // constructs this controller at startup, sees the parameters and hands
    // us the repositories it generated. That's "dependency injection": you
    // declare what you need, Spring supplies it. We never write
    // `new CommentRepository...`.
    private final CommentRepository comments;
    private final ProfileRepository profiles;

    public CommentController(CommentRepository comments, ProfileRepository profiles) {
        this.comments = comments;
        this.profiles = profiles;
    }

    // {gameId} is a path variable — /api/games/3498/comments puts 3498
    // into the gameId parameter. We map each Comment to a CommentResponse
    // so the JSON includes the author's username/avatar instead of the raw
    // entity.
    @GetMapping("/api/games/{gameId}/comments")
    public List<CommentResponse> commentsForGame(@PathVariable Integer gameId) {
        return comments.findByGameIdOrderByCreatedAtDesc(gameId)
                .stream()
                .map(CommentResponse::from)
                .toList();
    }

    // POST = create. Security config guarantees only requests with a valid
    // JWT reach this method; anonymous POSTs are rejected before our code
    // runs. @RequestBody tells Spring to parse the request's JSON into a
    // NewCommentRequest. @ResponseStatus makes the success reply 201
    // Created (the precise status for "a new resource now exists").
    @PostMapping("/api/games/{gameId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Integer gameId,
                                      @RequestBody NewCommentRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {

        // THE rule of this whole migration: the author is whoever the
        // validated token says — jwt.getSubject() is the "sub" claim,
        // which equals auth.uid() in Supabase. The client never gets a
        // say. This is RLS's "insert own only" policy re-expressed in Java.
        UUID userId = UUID.fromString(jwt.getSubject());

        validateContent(request.content());

        // Comment now needs an actual Profile, not a bare uuid.
        // getReferenceById() does NOT query the database — it hands back
        // a lightweight proxy that just remembers the id. That's all
        // Hibernate needs to write the right value into the user_id
        // column on insert; we never need to read this profile's other
        // fields here, so there's no reason to pay for a real SELECT.
        Profile author = profiles.getReferenceById(userId);

        // Build the new comment (constructor sets createdAt, leaves id
        // null) and save it. save() sends the INSERT and returns the
        // comment WITH the id Postgres assigned. We map it to the same
        // CommentResponse shape GET uses, so a comment looks identical no
        // matter which endpoint returned it. Note: `author` is still the
        // id-only proxy here, so when from() asks for the username,
        // Hibernate runs ONE real SELECT to load the profile — the fair
        // price of including the author's name in the response.
        return CommentResponse.from(
                comments.save(new Comment(author, gameId, request.content())));
    }

    // PUT = update an existing resource. The comment lives at its own
    // address now — /api/comments/{id} — because once we have the
    // comment's id, the game id adds nothing. This path matches no
    // permitAll rule in SecurityConfig, so anyRequest().authenticated()
    // already guards it: a valid JWT is required before our code runs.
    @PutMapping("/api/comments/{id}")
    public CommentResponse editComment(@PathVariable Long id,
                                       @RequestBody EditCommentRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {

        // findById returns an Optional<Comment> — a box that either holds
        // a comment or is empty. Java uses it instead of returning null so
        // "might not exist" is visible in the type and can't be forgotten.
        // orElseThrow = "give me the comment, or if the box is empty,
        // throw this" → the 404 case handled in one line.
        Comment comment = comments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comment not found"));

        // THE ownership check — RLS's "update own only" policy as Java.
        // 403 Forbidden, not 401: 401 means "we don't know who you are"
        // (Spring already handled that); 403 means "we know exactly who
        // you are, and this comment isn't yours."
        UUID userId = UUID.fromString(jwt.getSubject());
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only edit your own comments");
        }

        validateContent(request.content());

        // No UPDATE statement anywhere: we loaded this entity through
        // Hibernate, so Hibernate is tracking it. We change the object,
        // call save(), and Hibernate writes the UPDATE for us — including
        // the edited_at stamp that editContent() set. Mapped to the same
        // CommentResponse shape as GET and POST.
        comment.editContent(request.content());
        return CommentResponse.from(comments.save(comment));
    }

    // DELETE = remove. Same shape as edit minus the body: find it (404),
    // check it's yours (403) — RLS's "delete own only" as Java — then
    // delete. Success is 204 No Content: "done, nothing to return" is
    // the standard answer for a delete.
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long id,
                              @AuthenticationPrincipal Jwt jwt) {

        Comment comment = comments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comment not found"));

        UUID userId = UUID.fromString(jwt.getSubject());
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only delete your own comments");
        }

        comments.delete(comment);
    }

    // Shared by POST and PUT — the same 1–2000 char rule as the table's
    // CHECK constraint. The database would reject bad content anyway, but
    // checking here turns it into a clean 400 Bad Request with a message,
    // instead of a raw SQL error surfacing as a confusing 500.
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment cannot be empty");
        }
        if (content.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is too long (max 2000 characters)");
        }
    }
}