package com.gamedex.gamedex_api;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The `list` (My List) domain. Same shape idea as the follows controller:
// WRITES are scoped to the caller (user id ALWAYS from the validated JWT,
// never the client), and the READ is public (a profile page must show
// someone's list when logged out).
//
//   GET    /api/users/{userId}/list   public  -> ListEntryResponse[]
//   PUT    /api/list/{gameId}         authed  -> 200 upsert (MY row)
//   DELETE /api/list/{gameId}         authed  -> 204 (idempotent)
//
// Note there is NO 403 path here (unlike comments). The row key includes YOUR
// id, so you physically cannot target another user's row — ownership is
// enforced by key construction, same as likes and follows.
@RestController
public class EntryController {

    // Server-side status validation (the "light validation" option). These
    // MUST match src/constants.ts STATUSES letter-for-letter (they're stored
    // as display strings — capitals and spaces included).
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "Playing", "Completed", "On Hold", "Dropped", "Plan to Play"
    );

    private final EntryRepository entryRepository;

    public EntryController(EntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    // ---- READ: public. Anyone can see a user's list (Stage D public-read). ----
    @GetMapping("/api/users/{userId}/list")
    public List<ListEntryResponse> getList(@PathVariable UUID userId) {
        return entryRepository.findByUserId(userId).stream()
                // Newest first is a sensible default; the frontend re-sorts
                // with its own LIST_SORTERS anyway.
                .sorted(Comparator.comparing(Entry::getAddedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(ListEntryResponse::from)
                .toList();
    }

    // ---- WRITE: upsert MY entry for a game. authed. ----
    @PutMapping("/api/list/{gameId}")
    public ResponseEntity<ListEntryResponse> upsert(
            @PathVariable Integer gameId,
            @RequestBody UpsertEntryRequest body,
            @AuthenticationPrincipal Jwt jwt) {

        // The owner is ALWAYS the token subject — never anything from the body.
        UUID me = UUID.fromString(jwt.getSubject());

        validate(body);

        // Build a COMPLETE entry and save it. Because the key is
        // (userId, gameId), save() acts as an UPSERT: existing row -> update,
        // missing row -> insert.
        Entry entry = new Entry(me, gameId, body.status(), body.score(),
                body.hours(), body.game());
        entryRepository.save(entry);

        // Re-read so the response carries the DB-filled added_at (the column
        // Hibernate never writes). One extra SELECT; fine at this scale and it
        // makes PUT and GET return the exact same shape.
        Entry saved = entryRepository.findById(new EntryId(me, gameId))
                .orElseThrow(); // just saved, so it exists
        return ResponseEntity.ok(ListEntryResponse.from(saved));
    }

    // ---- WRITE: delete MY entry for a game. authed. idempotent. ----
    @DeleteMapping("/api/list/{gameId}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer gameId,
            @AuthenticationPrincipal Jwt jwt) {

        UUID me = UUID.fromString(jwt.getSubject());
        // Pinned to (me, gameId): can only remove YOUR own row. Deleting
        // something not there is a quiet no-op (zero rows) -> still 204.
        entryRepository.deleteByUserIdAndGameId(me, gameId);
        return ResponseEntity.noContent().build();
    }

    // ---- light validation. Rejects obvious garbage with a clean 400. The UI
    // already constrains exact ranges, so score/hours use loose sanity bounds
    // rather than mirroring the UI scale. ----
    private void validate(UpsertEntryRequest body) {
        // status: required and known.
        if (body.status() == null || !ALLOWED_STATUSES.contains(body.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be one of " + ALLOWED_STATUSES);
        }
        // score: optional; if present, loose 0..100 sanity bound.
        if (body.score() != null && (body.score() < 0 || body.score() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "score must be between 0 and 100");
        }
        // hours: optional; if present, non-negative and not absurd.
        if (body.hours() != null && (body.hours() < 0 || body.hours() > 100000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "hours must be between 0 and 100000");
        }
        // game: the JSONB blob is the point of an entry — require it.
        if (body.game() == null || body.game().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "game object is required");
        }
    }

    // ---- request/response shapes (records, same style as the other domains) ----

    // The PUT body. userId is deliberately ABSENT — it comes from the JWT.
    public record UpsertEntryRequest(
            String status,
            Integer score,
            Double hours,
            Map<String, Object> game) {
    }

    // What reads return. Flat, mirrors the columns.
    public record ListEntryResponse(
            UUID userId,
            Integer gameId,
            String status,
            Integer score,
            Double hours,
            Map<String, Object> game,
            OffsetDateTime addedAt) {

        // Factory: Entry (DB shape) -> ListEntryResponse (wire shape).
        public static ListEntryResponse from(Entry e) {
            return new ListEntryResponse(
                    e.getUserId(), e.getGameId(), e.getStatus(), e.getScore(),
                    e.getHours(), e.getGame(), e.getAddedAt());
        }
    }
}
