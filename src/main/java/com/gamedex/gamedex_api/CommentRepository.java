package com.gamedex.gamedex_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

// A repository is the data-access layer. We write NO implementation —
// at startup, Spring Data sees this interface and generates the code.
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // "join fetch c.author" loads each comment AND its author in ONE query,
    // instead of one query for the comments plus one per author (the N+1
    // problem flagged in the notes). This is the read the GET endpoint uses.
    @Query("select c from Comment c join fetch c.author " +
           "where c.gameId = :gameId order by c.createdAt desc")
    List<Comment> findByGameIdWithAuthor(@Param("gameId") Integer gameId);
}
