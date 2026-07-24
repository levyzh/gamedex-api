package com.gamedex.gamedex_api.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;

// One row in the follows table: "follower follows followed." No surrogate id —
// the pair of columns IS the key — so two @Id fields + @IdClass, exactly like
// CommentLike. Bare on purpose: no associations, no created_at mapped.
@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
public class Follow {

    @Id
    @Column(name = "follower_id")
    private UUID followerId;

    @Id
    @Column(name = "followed_id")
    private UUID followedId;

    protected Follow() {
    }

    public Follow(UUID followerId, UUID followedId) {
        this.followerId = followerId;
        this.followedId = followedId;
    }

    public UUID getFollowerId() {
        return followerId;
    }

    public UUID getFollowedId() {
        return followedId;
    }
}
