package com.gamedex.gamedex_api;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

// Mirrors the "profiles" table in Supabase, the same way Comment mirrors
// "comments". One row per user; the id is the SAME uuid as the JWT "sub"
// claim (auth.uid()) — profiles.id IS the user's identity everywhere.
@Entity
@Table(name = "profiles")
public class Profile {

    // NOTE: no @GeneratedValue here, unlike Comment. Comment ids are
    // assigned by Postgres on insert (identity column). Profile ids are
    // assigned by Supabase Auth when the account is created — by the time
    // this entity exists, the id already does. We just read it.
    @Id
    private UUID id;

    private String username;

    // Both nullable in the table (your rows show EMPTY/NULL), so plain
    // String fields are fine — they'll just be null when the column is.
    private String bio;

    // Hibernate's camelCase↔snake_case translation maps this to the
    // avatar_url column automatically, same as elsewhere.
    private String avatarUrl;

    // Whether strangers may see this user's game list. Maps to the
    // list_public column (camelCase↔snake_case, like avatarUrl). Added so
    // the FEED can respect list privacy — a private list's entries should
    // not surface in other people's feeds. Boolean (not boolean) so a null
    // column value stays null rather than silently becoming false.
    private Boolean listPublic;

    private OffsetDateTime createdAt;

    // JPA requires a no-arg constructor so Hibernate can build instances
    // when loading rows. Protected = Hibernate can use it, our code can't
    // accidentally make an empty Profile.
    protected Profile() {
    }

    // No public constructor at all — deliberately. This API never CREATES
    // profiles (Supabase Auth + the frontend own that). This entity exists
    // only so we can READ profile info when serving comments/follows/feed.
    // No setters, no mutation methods: read-only by shape.

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Boolean getListPublic() {
        return listPublic;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}