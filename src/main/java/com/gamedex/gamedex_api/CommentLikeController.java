package com.gamedex.gamedex_api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

// Like / unlike for a comment. Both are login-only, and that's already handled:
// SecurityConfig's "anyRequest authenticated" covers these /api/comments/**
// calls (only the comments GET needed an explicit permitAll), so there's
// nothing to add there.
//
// The user is ALWAYS taken from the validated token — never the body or URL —
// the same rule as posting a comment. That's what makes a like impossible to
// forge as someone else.
@RestController
public class CommentLikeController {

    // Constructor injection (never `new` a Spring bean): Spring hands us the
    // repository when it builds this controller.
    private final CommentLikeRepository commentLikes;

    public CommentLikeController(CommentLikeRepository commentLikes) {
        this.commentLikes = commentLikes;
    }

    // POST /api/comments/{commentId}/likes → 201 Created.
    // save() inserts if the pair is new and quietly no-ops if it already
    // exists (JPA checks first, then writes), so a double-click can't blow up
    // on a duplicate key — matching how the old Supabase version behaved.
    @PostMapping("/api/comments/{commentId}/likes")
    public ResponseEntity<Void> like(
            @PathVariable Long commentId,
            @AuthenticationPrincipal Jwt jwt) {

        // The token's subject IS the user id (sub == auth.uid() == profiles.id).
        UUID userId = UUID.fromString(jwt.getSubject());

        commentLikes.save(new CommentLike(commentId, userId));

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // DELETE /api/comments/{commentId}/likes → 204 No Content.
    // Unliking something you never liked also succeeds quietly (zero rows
    // deleted, no error). And because the delete is pinned to (commentId, YOUR
    // userId), it can only ever remove your own like.
    @DeleteMapping("/api/comments/{commentId}/likes")
    public ResponseEntity<Void> unlike(
            @PathVariable Long commentId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID userId = UUID.fromString(jwt.getSubject());

        commentLikes.deleteByCommentIdAndUserId(commentId, userId);

        return ResponseEntity.noContent().build();
    }
}