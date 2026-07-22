package com.gamedex.gamedex_api;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// The @IdClass companion for Follow — same idea as CommentLikeId. The follows
// table has no single id column; a row's identity is the PAIR
// (follower_id, followed_id). This little class mirrors those two @Id fields
// (by name + type), implements Serializable, has a no-arg constructor, and
// defines equals()/hashCode() so JPA can compare keys.
public class FollowId implements Serializable {

    private UUID followerId;
    private UUID followedId;

    public FollowId() {
    }

    public FollowId(UUID followerId, UUID followedId) {
        this.followerId = followerId;
        this.followedId = followedId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FollowId)) return false;
        FollowId other = (FollowId) o;
        return Objects.equals(followerId, other.followerId)
            && Objects.equals(followedId, other.followedId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followerId, followedId);
    }
}
