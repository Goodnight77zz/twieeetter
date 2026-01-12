package com.example.backend.repository;

import com.example.backend.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;



public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    Optional<CommentLike> findByUserIdAndCommentId(Long userId, Long commentId);
    // 统计某评论的点赞数
    long countByCommentIdAndIsLikeTrue(Long commentId);
    // 统计某评论的倒赞数
    long countByCommentIdAndIsLikeFalse(Long commentId);

    List<CommentLike> findByCommentId(Long commentId);
}