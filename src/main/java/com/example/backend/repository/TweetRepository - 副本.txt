package com.example.backend.repository;

import com.example.backend.entity.Tweet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TweetRepository extends JpaRepository<Tweet, Long> {
    // 获取所有推文，按时间倒序（最新的在最上面）
    List<Tweet> findAllByOrderByCreateTimeDesc();

    // 统计某人发了多少推文
    long countByAuthorId(Long userId);
    List<Tweet> findByAuthorIdOrderByCreateTimeDesc(Long userId);
}