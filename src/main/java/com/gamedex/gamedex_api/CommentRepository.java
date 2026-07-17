package com.gamedex.gamedex_api;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

// A repository is the data-access layer. We write NO implementation —
// at startup, Spring Data sees this interface and generates the code.
// JpaRepository<Comment, Long> = "manages Comment entities whose id is a Long"
// and that alone provides findAll(), findById(), count(), etc. for free.
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Custom queries are DERIVED FROM THE METHOD NAME. Spring parses
    // "findByGameIdOrderByCreatedAtDesc" and writes:
    //   SELECT * FROM comments WHERE game_id = ? ORDER BY created_at DESC
    // Same query comments.ts makes supabase-js build for us.
    List<Comment> findByGameIdOrderByCreatedAtDesc(Integer gameId);
}