package com.example.backend.repository;

import com.example.backend.entity.TweetRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TweetRatingRepository extends JpaRepository<TweetRating, Long> {
    // 找某人对某文的评分
    Optional<TweetRating> findByUserIdAndTweetId(Long userId, Long tweetId);

    // 找某文的所有评分（用来计算平均分）
    List<TweetRating> findByTweetId(Long tweetId);
}
