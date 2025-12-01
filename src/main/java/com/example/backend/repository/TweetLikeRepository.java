package com.example.backend.repository;

import com.example.backend.entity.TweetLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TweetLikeRepository extends JpaRepository<TweetLike, Long> {
    // 检查某人是否点赞了某文
    boolean existsByUserIdAndTweetId(Long userId, Long tweetId);

    // 查找点赞记录（为了取消点赞）
    Optional<TweetLike> findByUserIdAndTweetId(Long userId, Long tweetId);

    // 统计点赞数
    long countByTweetId(Long tweetId);
}