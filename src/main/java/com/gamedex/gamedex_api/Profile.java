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

    // Both nullable in the table, so plain String fields are fine — they'll
    // just be null when the column is.
    private String bio;

    // Hibernate's camelCase↔snake_case translation maps this to the
    // avatar_url column automatically, same as elsewhere.
    private String avatarUrl;

    // Whether strangers may see this user's game list. Maps to list_public.
    // Boolean (not boolean) so a null column value stays null.
    private Boolean listPublic;

    private OffsetDateTime createdAt;

    // JPA requires a no-arg constructor so Hibernate can build instances
    // when loading rows. Protected = Hibernate can use it, our code can't
    // accidentally make an empty Profile.
    protected Profile() {
    }

    // Still NO public constructor: this API never CREATES profiles (Supabase
    // Auth + the signup trigger own that). But as of the profiles migration
    // it now UPDATES them — so we add controlled mutation "doors" below.
    // Same discipline as Comment.editContent(): no free setters, only named
    // methods that describe exactly what may change, so a stray line can't
    // quietly overwrite a field it shouldn't.

    // The user editing their own profile: username, bio, and list privacy.
    // The CONTROLLER is what guarantees this only ever runs on the caller's
    // OWN row (it loads the row by the JWT sub); the entity just applies the
    // change. Username-format validation happens before this is called.
    public void editProfile(String username, String bio, Boolean listPublic) {
        this.username = username;
        this.bio = bio;
        this.listPublic = listPublic;
    }

    // A separate door for the avatar, because it changes on its own flow: the
    // image file uploads to Supabase Storage (which stays Supabase's job
    // forever), and only the resulting public URL is saved here.
    public void updateAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

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