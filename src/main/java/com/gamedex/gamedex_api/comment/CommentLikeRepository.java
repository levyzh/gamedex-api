package com.gamedex.gamedex_api.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

// The data door for comment_likes. The key type is CommentLikeId (the
// two-field composite key class), not a plain Long.
public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLikeId> {

    // Unlike: delete the row for (this comment, this user). Because we always
    // pass the caller's OWN id, it can only ever remove the caller's own like.
    // Deleting zero rows is silently fine — that's the "unlike something you
    // never liked" no-op. @Transactional is required for a derived delete.
    @Transactional
    void deleteByCommentIdAndUserId(Long commentId, UUID userId);

    // How many likes one comment has (used by the PUT response).
    long countByCommentId(Long commentId);

    // All like rows for a batch of comments in ONE query, so the GET endpoint
    // computes counts and "liked by me" without a query per comment.
    List<CommentLike> findByCommentIdIn(Collection<Long> commentIds);
}
