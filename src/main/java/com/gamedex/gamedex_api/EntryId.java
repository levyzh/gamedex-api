package com.gamedex.gamedex_api;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// The @IdClass companion for Entry. The `list` table's primary key is the
// PAIR (user_id, game_id) — no single surrogate id — so JPA needs a small
// class representing that pair. Same recipe as FollowId and CommentLikeId:
// mirror the @Id fields by name+type, be Serializable, have a no-arg
// constructor, and define equals()/hashCode() so JPA can compare keys.
public class EntryId implements Serializable {

    // These field NAMES must match the @Id field names in Entry exactly
    // (userId, gameId) — that name-matching is how @IdClass wires up.
    private UUID userId;
    private Integer gameId;

    // JPA builds keys by reflection, so it needs a no-arg constructor.
    public EntryId() {
    }

    public EntryId(UUID userId, Integer gameId) {
        this.userId = userId;
        this.gameId = gameId;
    }

    // Two EntryIds are "the same key" only when BOTH parts match.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntryId that = (EntryId) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(gameId, that.gameId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, gameId);
    }
}