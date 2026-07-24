package com.gamedex.gamedex_api.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

// Spring Data generates the implementation at startup. Most of what we need
// comes for free from JpaRepository:
//   - getReferenceById(UUID): id-only proxy (no query) — CommentController
//     uses it to attach an author to a new Comment.
//   - findAllById(ids): one batched query — follows lists + the feed.
//   - findById(UUID): the single-profile read (GET /api/users/{id}/profile,
//     and loading YOUR row to edit).
//   - saveAndFlush(...): the profile update uses it so a duplicate-username
//     constraint error fires immediately, where the controller can catch it.
//
// The one custom finder is the People search: case-insensitive "contains",
// capped at 20, ordered by username. Spring reads this METHOD NAME and writes
// the query — "Containing" becomes LIKE %term%, and by default it escapes %
// and _ in the term, so a user typing those searches literally instead of
// writing their own wildcard pattern.
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    List<Profile> findTop20ByUsernameContainingIgnoreCaseOrderByUsernameAsc(String term);
}