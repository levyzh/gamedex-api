package com.gamedex.gamedex_api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// The FEED domain: "games recently added by people you follow." It's your
// PERSONAL feed, so — like /api/me — the user comes from the validated JWT,
// never a path param. Authed only: no SecurityConfig rule is needed because
// /api/feed isn't in the public list, so it falls through to
// anyRequest().authenticated().
//
// This is the first endpoint that spans THREE tables, all server-side:
// follows (who I follow) -> list (their entries) -> profiles (the authors).
// It does so in exactly THREE queries, no N+1.
@RestController
public class FeedController {

    private final FollowRepository followRepository;
    private final EntryRepository entryRepository;
    private final ProfileRepository profileRepository;

    public FeedController(FollowRepository followRepository,
                          EntryRepository entryRepository,
                          ProfileRepository profileRepository) {
        this.followRepository = followRepository;
        this.entryRepository = entryRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping("/api/feed")
    public List<FeedItemResponse> getFeed(@AuthenticationPrincipal Jwt jwt) {
        UUID me = UUID.fromString(jwt.getSubject());

        // 1. Who do I follow? Pull the followed ids out of my follow rows.
        //    (I never follow myself — self-follow is rejected — so my own
        //    entries can't appear in my feed. That's the behavior we want.)
        List<UUID> followedIds = followRepository.findByFollowerId(me).stream()
                .map(Follow::getFollowedId)
                .toList();

        // Guard: follow nobody -> empty feed, and skip the queries. An empty
        // IN () clause is pointless and some DBs dislike it — return early.
        if (followedIds.isEmpty()) {
            return List.of();
        }

        // 2. Their newest 30 list entries, newest first. ONE query — the
        //    finder name carries the limit, the IN, and the ordering.
        List<Entry> entries =
                entryRepository.findTop30ByUserIdInOrderByAddedAtDesc(followedIds);

        // 3. Batch-load the author profiles in ONE query (the same
        //    findAllById pattern the follows lists use), keyed by id so the
        //    stitch step is a cheap map lookup instead of a query per row.
        List<UUID> authorIds = entries.stream()
                .map(Entry::getUserId)
                .distinct()
                .toList();
        Map<UUID, Profile> authors = profileRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));

        // 4. Stitch each entry to its author and map to the wire shape. Order
        //    is already newest-first from the DB, so we keep it.
        return entries.stream()
                .map(e -> FeedItemResponse.from(e, authors.get(e.getUserId())))
                .toList();
    }

    // Mirrors the frontend FeedItem EXACTLY: flat entry fields + a nested
    // author. Note there is NO `hours` here — the feed doesn't show it, and
    // FeedItem doesn't carry it.
    public record FeedItemResponse(
            UUID userId,
            Integer gameId,
            Map<String, Object> game,
            String status,
            Integer score,
            OffsetDateTime addedAt,
            Author author) {

        public static FeedItemResponse from(Entry e, Profile author) {
            return new FeedItemResponse(
                    e.getUserId(),
                    e.getGameId(),
                    e.getGame(),
                    e.getStatus(),
                    e.getScore(),
                    e.getAddedAt(),
                    Author.from(author));
        }
    }

    // The nested author block: { username, avatarUrl } — matches FeedItem.author.
    public record Author(String username, String avatarUrl) {
        public static Author from(Profile p) {
            // A followed user should always have a profile row, but guard
            // against a missing one rather than risk a NullPointerException.
            if (p == null) {
                return new Author("unknown", null);
            }
            return new Author(p.getUsername(), p.getAvatarUrl());
        }
    }
}