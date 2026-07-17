package com.gamedex.gamedex_api;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

// An "entity" is a class that mirrors a database table, one field per
// column. This is the same job rowToComment() does in the frontend —
// except instead of us writing the translation, Hibernate reads these
// annotations and does the row-to-object mapping for us.
@Entity
@Table(name = "comments") // which table this class mirrors
public class Comment {

    @Id // marks the primary key — every entity must declare one
    // The table's id is an IDENTITY column: Postgres generates it on
    // insert. This annotation tells Hibernate "send the insert without an
    // id, the database assigns one and returns it." Without it, Hibernate
    // would expect US to supply the id and saving new comments would fail.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne = "many comments can point to one profile." Unlike the
    // plain fields below, this one doesn't hold a raw value — it holds the
    // actual related Profile object. Hibernate uses @JoinColumn to know
    // WHICH column stores the link (user_id), but in Java we work with
    // the Profile itself, not its uuid. Asking for comment.getAuthor()
    // triggers Hibernate to fetch that Profile row (by default, only when
    // you actually call it — this is "lazy loading").
    @ManyToOne
    @JoinColumn(name = "user_id")
    private Profile author;

    private Integer gameId;

    private String content;

    // timestamptz maps to OffsetDateTime (a timestamp that knows its
    // timezone offset — Java has several date types; this is the right
    // one for Postgres timestamptz).
    private OffsetDateTime createdAt;

    // Nullable in the database (null = never edited), so it can be null here.
    private OffsetDateTime editedAt;

    // Hibernate requires a no-argument constructor to build instances.
    protected Comment() {}

    // The constructor for creating a NEW comment (used by the POST
    // endpoint). It takes only what a new comment genuinely needs and
    // decides the rest itself:
    //   - id stays null       → the database assigns it on insert
    //   - createdAt = now     → set here, not trusted from the client
    //   - editedAt stays null → null means "never edited"
    // Note there are still no setters: once built, a comment's fields
    // can't be quietly changed from outside. Hard to misuse on purpose.
    public Comment(Profile author, Integer gameId, String content) {
        this.author = author;
        this.gameId = gameId;
        this.content = content;
        this.createdAt = OffsetDateTime.now();
    }

    // The ONE sanctioned way to change a comment. Deliberately not a plain
    // setContent(): content and editedAt must always change together
    // ("every edit is stamped"), so this method is the only door and it
    // does both. Rules enforced by shape, not by remembering.
    public void editContent(String newContent) {
        this.content = newContent;
        this.editedAt = OffsetDateTime.now();
    }

    // Getters — how the outside world reads the fields. When Spring turns
    // this object into JSON for a response, it calls these.
    public Long getId() { return id; }
    public Profile getAuthor() { return author; }
    public Integer getGameId() { return gameId; }
    public String getContent() { return content; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getEditedAt() { return editedAt; }
}