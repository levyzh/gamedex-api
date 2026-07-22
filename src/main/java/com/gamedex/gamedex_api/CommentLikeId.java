package com.gamedex.gamedex_api;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// This tiny class exists for ONE reason: comment_likes has no single id
// column. A row's identity is the PAIR (commentId, userId) together — that
// pair IS the primary key. JPA can't express "the key is two columns" from
// the entity alone, so it needs a small companion class carrying exactly
// those two fields. The entity points at it with @IdClass(CommentLikeId.class).
//
// The rules JPA puts on this class:
//  • its field names + types must MATCH the @Id fields on the entity
//    (commentId: Long, userId: UUID) — that's how JPA lines the two up.
//  • it needs a no-arg constructor (JPA builds it reflectively).
//  • it must implement Serializable.
//  • it must define equals() + hashCode(), because JPA uses them to decide
//    whether two keys point at "the same row" — the same way a HashMap needs
//    them to find a key. Skip them and every identity check quietly misses.
public class CommentLikeId implements Serializable {

    // These mirror the @Id fields on CommentLike, by name and type.
    private Long commentId;
    private UUID userId;

    // JPA requires a no-arg constructor; we never call it ourselves.
    public CommentLikeId() {
    }

    public CommentLikeId(Long commentId, UUID userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

    // "Two keys are equal when BOTH parts match." Objects.equals/hash handle
    // nulls for us and keep this readable.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommentLikeId)) return false;
        CommentLikeId other = (CommentLikeId) o;
        return Objects.equals(commentId, other.commentId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commentId, userId);
    }
}