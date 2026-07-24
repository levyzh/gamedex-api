package com.gamedex.gamedex_api.follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

// Data door for follows. Key type is FollowId (the composite key class).
public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    // Counts for the stats endpoint.
    long countByFollowedId(UUID followedId); // how many follow this user
    long countByFollowerId(UUID followerId); // how many this user follows

    // The rows behind the lists: who follows a user, and who a user follows.
    List<Follow> findByFollowedId(UUID followedId); // → their followers' ids
    List<Follow> findByFollowerId(UUID followerId); // → the ids they follow

    // Unfollow, pinned to (me, them) so it can only remove your own follow.
    // @Transactional is required for a derived delete.
    @Transactional
    void deleteByFollowerIdAndFollowedId(UUID followerId, UUID followedId);
}
