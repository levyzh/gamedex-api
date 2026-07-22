package com.gamedex.gamedex_api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The `profiles` domain — the LAST table to move off direct-Supabase onto
// this API. Same shape ideas as the follows/list controllers:
//   - READS are public (a user page + People search must work logged-out).
//   - WRITES take the user id ALWAYS from the validated JWT, never the body,
//     so you can only ever change YOUR OWN row (ownership by construction,
//     like the list PUT — no 403 path needed).
//
//   GET /api/users/{userId}/profile   public  -> ProfileResponse
//   GET /api/users/search?q=...       public  -> ProfileSummary[]
//   PUT /api/me/profile               authed  -> 200 (username/bio/privacy)
//   PUT /api/me/avatar                authed  -> 200 (avatar_url only)
@RestController
public class ProfileController {

    // The SAME rule the database enforces and the frontend checks for instant
    // feedback: 3–20 chars, lowercase letters / digits / underscores. We
    // re-check it HERE because this is the TRUSTED copy — the browser's check
    // can be bypassed and the database's error is cryptic; this one is the gate.
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_]{3,20}$");

    private final ProfileRepository profiles;

    public ProfileController(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    // ---- READ: one profile by id. public. ----
    @GetMapping("/api/users/{userId}/profile")
    public ProfileResponse getProfile(@PathVariable UUID userId) {
        Profile p = profiles.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No such profile"));
        return ProfileResponse.from(p);
    }

    // ---- READ: People search. public. ----
    @GetMapping("/api/users/search")
    public List<ProfileSummary> search(@RequestParam(value = "q", required = false) String q) {
        String term = (q == null) ? "" : q.trim();
        if (term.isEmpty()) {
            return List.of();   // an empty box searches for nothing
        }
        return profiles.findTop20ByUsernameContainingIgnoreCaseOrderByUsernameAsc(term)
                .stream()
                .map(ProfileSummary::from)
                .toList();
    }

    // ---- WRITE: edit MY profile (username, bio, list privacy). authed. ----
    @PutMapping("/api/me/profile")
    public ProfileResponse updateProfile(@RequestBody UpdateProfileRequest body,
                                         @AuthenticationPrincipal Jwt jwt) {

        UUID me = UUID.fromString(jwt.getSubject());

        // Validate the username BEFORE touching the row — a clean 400 with a
        // plain-words message beats a raw database constraint error.
        if (body.username() == null || !USERNAME.matcher(body.username()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Usernames must be 3-20 characters, using only lowercase letters, numbers, and underscores.");
        }

        // The bio column is NOT NULL (default '') in the DB. The frontend always
        // sends a string, but a null from any other caller would make the DB
        // reject the write with an ugly 500. Coerce null -> "" so the invariant
        // "bio is never null" is guaranteed here, at the trusted boundary.
        String bio = (body.bio() == null) ? "" : body.bio();

        // Load MY row (by the token subject) — THIS is what makes it
        // impossible to edit anyone else's profile. It always exists thanks
        // to the signup trigger, but guard anyway.
        Profile p = profiles.findById(me)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Your profile row is missing"));

        p.editProfile(body.username(), bio, body.listPublic());

        // saveAndFlush (not save): force the write to hit the DB NOW, so a
        // duplicate-username violation is thrown HERE where we can catch and
        // translate it — instead of later, outside our try.
        try {
            profiles.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            // The only unique constraint on this row is the username, so a
            // violation can only mean one thing.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The username \"" + body.username() + "\" is already taken.");
        }

        return ProfileResponse.from(p);
    }

    // ---- WRITE: save MY avatar URL. authed. ----
    // The image FILE still uploads to Supabase Storage on the frontend (that
    // stays Supabase's job); this endpoint only records the resulting URL.
    @PutMapping("/api/me/avatar")
    public ProfileResponse updateAvatar(@RequestBody UpdateAvatarRequest body,
                                        @AuthenticationPrincipal Jwt jwt) {

        UUID me = UUID.fromString(jwt.getSubject());

        if (body.avatarUrl() == null || body.avatarUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "avatarUrl is required");
        }

        Profile p = profiles.findById(me)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Your profile row is missing"));

        p.updateAvatarUrl(body.avatarUrl());
        profiles.save(p);

        return ProfileResponse.from(p);
    }

    // ---- request/response shapes (records, same style as the other domains) ----

    // PUT /api/me/profile body. NO id field — the id comes from the JWT.
    public record UpdateProfileRequest(String username, String bio, Boolean listPublic) {}

    // PUT /api/me/avatar body.
    public record UpdateAvatarRequest(String avatarUrl) {}

    // Full profile — mirrors the frontend Profile type exactly.
    public record ProfileResponse(UUID id, String username, String bio,
                                  String avatarUrl, Boolean listPublic,
                                  OffsetDateTime createdAt) {
        public static ProfileResponse from(Profile p) {
            return new ProfileResponse(p.getId(), p.getUsername(), p.getBio(),
                    p.getAvatarUrl(), p.getListPublic(), p.getCreatedAt());
        }
    }

    // Light row for the People search — mirrors the frontend ProfileSummary.
    // (FollowController nests an identical one; kept separate so each
    // controller owns its own wire shapes.)
    public record ProfileSummary(UUID id, String username, String avatarUrl) {
        public static ProfileSummary from(Profile p) {
            return new ProfileSummary(p.getId(), p.getUsername(), p.getAvatarUrl());
        }
    }
}