package com.example.backend.repository;

import com.example.backend.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByUserIdAndCommentId(Long userId, Long commentId);
    long countByCommentIdAndIsLikeTrue(Long commentId);
    long countByCommentIdAndIsLikeFalse(Long commentId);
    List<CommentLike> findByCommentId(Long commentId);
}
