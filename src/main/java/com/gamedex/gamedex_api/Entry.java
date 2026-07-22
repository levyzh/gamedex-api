package com.gamedex.gamedex_api;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// @Entity mirroring the `list` table (My List). Like Follow and CommentLike,
// its identity is a PAIR — (user_id, game_id) — so it carries TWO @Id fields
// and points at @IdClass(EntryId.class) instead of a generated id.
//
// THE NEW CONCEPT this slice: the `game` column is JSONB in Postgres (the
// whole Game object stored as JSON). @JdbcTypeCode(SqlTypes.JSON) tells
// Hibernate "this column is JSON"; Hibernate then uses Jackson (already on
// the classpath) to turn the Map into JSON on write and back on read. We keep
// it as a generic Map<String,Object> so we DON'T have to re-declare the whole
// Game shape in Java — the frontend owns that shape, we just store/echo it.
@Entity
@Table(name = "list")
@IdClass(EntryId.class)
public class Entry {

    // --- composite key: raw columns (no Profile association needed here; the
    // list GET doesn't embed author info — the feed will do that later). ---
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "game_id")
    private Integer gameId;

    // --- the per-entry data the user sets ---
    private String status;

    // score/hours are nullable (an entry can exist un-scored / with no hours).
    private Integer score;

    // Postgres `real`; Double reads/writes it fine and matches the JS number
    // the frontend uses.
    private Double hours;

    // The JSONB column. Map<String,Object> <-> JSON, handled by Jackson.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "game")
    private Map<String, Object> game;

    // added_at is filled by the DB default on insert. insertable=false +
    // updatable=false = "Hibernate, never write this column" — so the DB owns
    // it and an upsert can't accidentally null it out. We re-read after saving
    // to get the real value back for the response.
    @Column(name = "added_at", insertable = false, updatable = false)
    private OffsetDateTime addedAt;

    // JPA needs a no-arg constructor; protected keeps it away from our code.
    protected Entry() {
    }

    // The constructor our upsert uses: build a FULL entry, then save() it.
    // Because the key is (userId, gameId), save() behaves as an UPSERT —
    // existing row -> update, missing row -> insert. No setters needed: each
    // write constructs a fresh, complete Entry.
    public Entry(UUID userId, Integer gameId, String status, Integer score,
                 Double hours, Map<String, Object> game) {
        this.userId = userId;
        this.gameId = gameId;
        this.status = status;
        this.score = score;
        this.hours = hours;
        this.game = game;
    }

    // Read-only getters (the controller maps these into the response).
    public UUID getUserId() { return userId; }
    public Integer getGameId() { return gameId; }
    public String getStatus() { return status; }
    public Integer getScore() { return score; }
    public Double getHours() { return hours; }
    public Map<String, Object> getGame() { return game; }
    public OffsetDateTime getAddedAt() { return addedAt; }
}