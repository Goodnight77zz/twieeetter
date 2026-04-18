package com.example.backend.repository;

import com.example.backend.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    // 获取某篇文章的所有评论，按时间倒序
    List<Comment> findByTweetIdOrderByCreateTimeDesc(Long tweetId);
    long countByTweetId(Long tweetId);

    @Query("SELECT c.tweet.id, COUNT(c) FROM Comment c WHERE c.tweet.id IN :tweetIds GROUP BY c.tweet.id")
    List<Object[]> countByTweetIds(@Param("tweetIds") List<Long> tweetIds);

}
