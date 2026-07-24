package com.gamedex.gamedex_api.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

// One row in comment_likes: "this user liked this comment." The table has no
// surrogate id — its key is the two columns together — so this entity carries
// TWO @Id fields and names the companion key class with @IdClass.
//
// Deliberately bare compared with Comment: like/unlike return nothing, so we
// don't map created_at (the DB default fills it) and we map no @ManyToOne
// associations. Fewer columns mapped = less to reason about; we add them later
// only if a read endpoint ever needs them.
@Entity
@Table(name = "comment_likes")
@IdClass(CommentLikeId.class)
public class CommentLike {

    // Both columns are part of the key, so BOTH get @Id. camelCase↔snake_case
    // would auto-map these anyway; @Column(name=...) is spelled out just to
    // make the mapping unmistakable.
    @Id
    @Column(name = "comment_id")
    private Long commentId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    // JPA needs a no-arg constructor to rebuild rows on read. protected =
    // "framework only," so our code always uses the real constructor below.
    protected CommentLike() {
    }

    // The only way WE make one. A like is fully described by which comment and
    // which user; created_at is left to the database default, so it's not here.
    public CommentLike(Long commentId, UUID userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

    // Read-only getters, no setters: a like is never edited — it exists or it
    // doesn't (like/unlike = insert/delete), so there's nothing to mutate.
    public Long getCommentId() {
        return commentId;
    }

    public UUID getUserId() {
        return userId;
    }
}