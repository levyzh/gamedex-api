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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
public class CommentController {

    // The POST request body: {"content": "..."}. Only content — a client
    // can't spoof an author when there's no author field to send.
    public record NewCommentRequest(String content) {}

    // The PUT request body. Same shape today, kept separate on purpose.
    public record EditCommentRequest(String content) {}

    // The shape every comment endpoint returns. Now includes authorId (the
    // frontend needs it for the author link + the "is this mine?" check) and
    // the like data (likeCount + likedByMe), so the comments read no longer
    // depends on Supabase at all.
    public record CommentResponse(
            Long id,
            Integer gameId,
            String content,
            OffsetDateTime createdAt,
            OffsetDateTime editedAt,
            UUID authorId,
            String authorUsername,
            String authorAvatarUrl,
            long likeCount,
            boolean likedByMe) {

        public static CommentResponse from(Comment comment, long likeCount, boolean likedByMe) {
            Profile author = comment.getAuthor();
            return new CommentResponse(
                    comment.getId(),
                    comment.getGameId(),
                    comment.getContent(),
                    comment.getCreatedAt(),
                    comment.getEditedAt(),
                    author.getId(),
                    author.getUsername(),
                    author.getAvatarUrl(),
                    likeCount,
                    likedByMe);
        }
    }

    private final CommentRepository comments;
    private final ProfileRepository profiles;
    private final CommentLikeRepository commentLikes;

    public CommentController(CommentRepository comments,
                             ProfileRepository profiles,
                             CommentLikeRepository commentLikes) {
        this.comments = comments;
        this.profiles = profiles;
        this.commentLikes = commentLikes;
    }

    // GET is public, but if a token IS sent we read it so likedByMe can be
    // filled in per viewer. Anonymous -> jwt is null -> likedByMe is false.
    //
    // Two queries total, no matter how many comments: one join-fetch for the
    // comments+authors, one for all their likes. Then counts and "liked by me"
    // are computed in memory.
    @GetMapping("/api/games/{gameId}/comments")
    public List<CommentResponse> commentsForGame(@PathVariable Integer gameId,
                                                 @AuthenticationPrincipal Jwt jwt) {

        List<Comment> found = comments.findByGameIdWithAuthor(gameId);

        UUID me = (jwt == null) ? null : UUID.fromString(jwt.getSubject());

        List<Long> ids = found.stream().map(Comment::getId).toList();
        List<CommentLike> likes = ids.isEmpty()
                ? List.of()
                : commentLikes.findByCommentIdIn(ids);

        Map<Long, Long> countByComment = likes.stream()
                .collect(Collectors.groupingBy(CommentLike::getCommentId, Collectors.counting()));

        Set<Long> likedByMe = (me == null)
                ? Set.of()
                : likes.stream()
                       .filter(like -> like.getUserId().equals(me))
                       .map(CommentLike::getCommentId)
                       .collect(Collectors.toSet());

        return found.stream()
                .map(comment -> CommentResponse.from(
                        comment,
                        countByComment.getOrDefault(comment.getId(), 0L),
                        likedByMe.contains(comment.getId())))
                .toList();
    }

    // POST = create. Author is taken from the token, never the body.
    @PostMapping("/api/games/{gameId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Integer gameId,
                                      @RequestBody NewCommentRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());
        validateContent(request.content());

        Profile author = profiles.getReferenceById(userId);

        Comment saved = comments.save(new Comment(author, gameId, request.content()));

        // A brand-new comment has no likes yet, so 0 / false without querying.
        return CommentResponse.from(saved, 0L, false);
    }

    // PUT = update. Find (404), check ownership (403), edit, save.
    @PutMapping("/api/comments/{id}")
    public CommentResponse editComment(@PathVariable Long id,
                                       @RequestBody EditCommentRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {

        Comment comment = comments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Comment not found"));

        UUID userId = UUID.fromString(jwt.getSubject());
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only edit your own comments");
        }

        validateContent(request.content());

        comment.editContent(request.content());
        Comment saved = comments.save(comment);

        // An existing comment may already have likes — report them honestly.
        long likeCount = commentLikes.countByCommentId(saved.getId());
        boolean likedByMe = commentLikes.existsById(new CommentLikeId(saved.getId(), userId));
        return CommentResponse.from(saved, likeCount, likedByMe);
    }

    // DELETE = remove. Find (404), check ownership (403), delete.
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

    // Same 1–2000 char rule as the table's CHECK constraint, turned into a
    // clean 400 instead of a raw SQL error.
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment cannot be empty");
        }
        if (content.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is too long (max 2000 characters)");
        }
    }
}
