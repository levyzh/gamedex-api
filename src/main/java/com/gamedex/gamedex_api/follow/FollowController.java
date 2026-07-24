package com.gamedex.gamedex_api.follow;

import com.gamedex.gamedex_api.profile.Profile;
import com.gamedex.gamedex_api.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Follow / unfollow + the reads a user page needs (stats, follower/following
// lists). Writes take the follower from the token; reads are public (a profile
// page works logged-out) — SecurityConfig permits GET on these paths.
@RestController
public class FollowController {

    // The numbers a user page shows. followedByMe/followsMe are false when the
    // viewer is logged out.
    public record FollowStatsResponse(long followers, long following,
                                      boolean followedByMe, boolean followsMe) {}

    // A light profile row for the followers/following lists.
    public record ProfileSummary(UUID id, String username, String avatarUrl) {
        public static ProfileSummary from(Profile p) {
            return new ProfileSummary(p.getId(), p.getUsername(), p.getAvatarUrl());
        }
    }

    private final FollowRepository follows;
    private final ProfileRepository profiles;

    public FollowController(FollowRepository follows, ProfileRepository profiles) {
        this.follows = follows;
        this.profiles = profiles;
    }

    // POST /api/users/{userId}/follow → 201. Follower = token user, followed =
    // path user. Idempotent (composite key). You can't follow yourself.
    @PostMapping("/api/users/{userId}/follow")
    public ResponseEntity<Void> follow(@PathVariable UUID userId,
                                       @AuthenticationPrincipal Jwt jwt) {

        UUID me = UUID.fromString(jwt.getSubject());
        if (me.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't follow yourself");
        }

        follows.save(new Follow(me, userId));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // DELETE /api/users/{userId}/follow → 204. Pinned to (me, userId): can only
    // remove your own follow; unfollowing a non-follow is a quiet no-op.
    @DeleteMapping("/api/users/{userId}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable UUID userId,
                                         @AuthenticationPrincipal Jwt jwt) {

        UUID me = UUID.fromString(jwt.getSubject());
        follows.deleteByFollowerIdAndFollowedId(me, userId);
        return ResponseEntity.noContent().build();
    }

    // GET /api/users/{userId}/follow-stats → counts + (if a token is sent) the
    // viewer's relationship to this user.
    @GetMapping("/api/users/{userId}/follow-stats")
    public FollowStatsResponse stats(@PathVariable UUID userId,
                                     @AuthenticationPrincipal Jwt jwt) {

        long followers = follows.countByFollowedId(userId);
        long following = follows.countByFollowerId(userId);

        boolean followedByMe = false;
        boolean followsMe = false;
        if (jwt != null) {
            UUID me = UUID.fromString(jwt.getSubject());
            // (me → userId) = do I follow them; (userId → me) = do they follow me.
            followedByMe = follows.existsById(new FollowId(me, userId));
            followsMe = follows.existsById(new FollowId(userId, me));
        }

        return new FollowStatsResponse(followers, following, followedByMe, followsMe);
    }

    // GET /api/users/{userId}/followers → the people who follow this user.
    @GetMapping("/api/users/{userId}/followers")
    public List<ProfileSummary> followers(@PathVariable UUID userId) {
        List<UUID> ids = follows.findByFollowedId(userId).stream()
                .map(Follow::getFollowerId)
                .toList();
        return toSummaries(ids);
    }

    // GET /api/users/{userId}/following → the people this user follows.
    @GetMapping("/api/users/{userId}/following")
    public List<ProfileSummary> following(@PathVariable UUID userId) {
        List<UUID> ids = follows.findByFollowerId(userId).stream()
                .map(Follow::getFollowedId)
                .toList();
        return toSummaries(ids);
    }

    // Load the given profiles in ONE query (findAllById) and shape them into
    // summaries, sorted by username for a stable order.
    private List<ProfileSummary> toSummaries(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return profiles.findAllById(ids).stream()
                .map(ProfileSummary::from)
                .sorted(Comparator.comparing(ProfileSummary::username, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
