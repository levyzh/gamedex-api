package com.gamedex.gamedex_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

// Same idea as CommentRepository: an empty interface, and Spring Data
// generates the implementation at startup. We don't add any custom finder
// methods here — the only thing we use from this repository is
// getReferenceById(UUID), which JpaRepository provides for free. It hands
// back a Profile "proxy" containing just the given id, with NO query run
// against the database. That's exactly enough for CommentController to
// attach an author to a new Comment without looking the profile up first.
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
}
