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
// Spans THREE tables, all server-side, in THREE queries (no N+1):
// follows (who I follow) -> list (their entries) -> profiles (the authors).
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
        //    (I never follow myself, so my own entries can't appear here.)
        List<UUID> followedIds = followRepository.findByFollowerId(me).stream()
                .map(Follow::getFollowedId)
                .toList();

        // Guard: follow nobody -> empty feed, and skip the queries.
        if (followedIds.isEmpty()) {
            return List.of();
        }

        // 2. Their newest 30 list entries, newest first — ONE query (the finder
        //    name carries the limit, the IN, and the ordering).
        List<Entry> entries =
                entryRepository.findTop30ByUserIdInOrderByAddedAtDesc(followedIds);

        // 3. Batch-load the author profiles in ONE query, keyed by id so the
        //    stitch step is a cheap map lookup instead of a query per row.
        List<UUID> authorIds = entries.stream()
                .map(Entry::getUserId)
                .distinct()
                .toList();
        Map<UUID, Profile> authors = profileRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(Profile::getId, p -> p));

        // 4. Stitch each entry to its author and map to the wire shape, but
        //    FIRST respect list privacy: if the author's list is private
        //    (list_public = false), their entries are dropped from the feed —
        //    a private list shouldn't leak through someone else's feed. A null
        //    author (shouldn't happen for a followed user) or a null flag is
        //    treated as public (the column has a default; this is defensive).
        //    NOTE: this filter runs AFTER the 30-cap, so a feed dominated by
        //    private-list follows can return fewer than 30 items. Fine at this
        //    scale; revisit with a DB-side join+filter if it ever matters.
        return entries.stream()
                .filter(e -> {
                    Profile author = authors.get(e.getUserId());
                    return author != null
                            && !Boolean.FALSE.equals(author.getListPublic());
                })
                .map(e -> FeedItemResponse.from(e, authors.get(e.getUserId())))
                .toList();
    }

    // Mirrors the frontend FeedItem EXACTLY: flat entry fields + a nested
    // author. Note there is NO `hours` here — the feed doesn't show it.
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
            if (p == null) {
                return new Author("unknown", null);
            }
            return new Author(p.getUsername(), p.getAvatarUrl());
        }
    }
}