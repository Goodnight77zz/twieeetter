package com.example.backend.service;

import com.example.backend.entity.Comment;
import com.example.backend.entity.CommentLike;
import com.example.backend.entity.User;
import com.example.backend.repository.CommentLikeRepository;
import com.example.backend.repository.CommentRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CommentService {

    @Autowired private CommentLikeRepository commentLikeRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    // 评论点赞/倒赞逻辑
    public String toggleCommentLike(Long userId, Long commentId, Boolean isLike) {
        User user = userRepository.findById(userId).orElseThrow();
        Comment comment = commentRepository.findById(commentId).orElseThrow();

        Optional<CommentLike> existingOpt = commentLikeRepository.findByUserIdAndCommentId(userId, commentId);

        if (existingOpt.isPresent()) {
            CommentLike existing = existingOpt.get();
            // 如果之前操作和现在一样（比如之前点了赞，现在又点赞），则取消（删除记录）
            if (existing.getIsLike().equals(isLike)) {
                commentLikeRepository.delete(existing);
                return isLike ? "取消点赞" : "取消倒赞";
            } else {
                // 如果不一样（比如之前点赞，现在点倒赞），则更新状态
                existing.setIsLike(isLike);
                commentLikeRepository.save(existing);
                return isLike ? "已转为点赞" : "已转为倒赞";
            }
        } else {
            // 新记录
            CommentLike newLike = new CommentLike();
            newLike.setUser(user);
            newLike.setComment(comment);
            newLike.setIsLike(isLike);
            commentLikeRepository.save(newLike);

            // 🔥 发送通知 (只有点赞才发通知，倒赞不发以免伤人心)
            if (isLike) {
                notificationService.send(comment.getUser(), user, 4, commentId, comment.getTweet().getId());
            }
            return isLike ? "点赞成功" : "倒赞成功";
        }
    }
}