package com.example.backend.repository;

import com.example.backend.entity.TweetFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TweetFavoriteRepository extends JpaRepository<TweetFavorite, Long> {

    boolean existsByUserIdAndTweetId(Long userId, Long tweetId);

    long countByTweetId(Long tweetId);

    Optional<TweetFavorite> findByUserIdAndTweetId(Long userId, Long tweetId);

    List<TweetFavorite> findByUserIdOrderByCreateTimeDesc(Long userId);
}
