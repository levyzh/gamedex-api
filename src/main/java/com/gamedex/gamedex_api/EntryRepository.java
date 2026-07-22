package com.gamedex.gamedex_api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

// Data-access for the `list` table. JpaRepository<Entry, EntryId> gives us
// save() (our upsert), findById, existsById, etc. keyed by the composite id.
public interface EntryRepository extends JpaRepository<Entry, EntryId> {

    // All of one user's list entries (the public GET). Spring derives the
    // SQL from the method name: WHERE user_id = ?.
    List<Entry> findByUserId(UUID userId);

    // Delete pinned to (me, gameId) — you can only ever remove your OWN row.
    // Derived deletes must be @Transactional (same rule we hit on the
    // follows/likes deletes).
    @Transactional
    void deleteByUserIdAndGameId(UUID userId, Integer gameId);

    // For the FEED: the newest 30 list entries across a SET of users (the
    // people I follow), newest first. Everything is derived from the method
    // name — Top30 (the limit), In (user_id IN (…)), OrderByAddedAtDesc — so
    // it's ONE query that lets the DB do the ordering and the cap.
    List<Entry> findTop30ByUserIdInOrderByAddedAtDesc(Collection<UUID> userIds);
}