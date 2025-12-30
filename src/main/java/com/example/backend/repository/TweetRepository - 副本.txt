package com.example.backend.repository;

import com.example.backend.entity.Tweet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TweetRepository extends JpaRepository<Tweet, Long> {

    // 原有方法
    List<Tweet> findAllByOrderByCreateTimeDesc();
    long countByAuthorId(Long userId);
    List<Tweet> findByAuthorIdOrderByCreateTimeDesc(Long userId);

    // === 搜索方法 (内容包含 OR 标签包含) ===
    // Spring Data JPA 会自动解析这个名字，生成 SQL:
    // SELECT * FROM tweets WHERE content LIKE %?% OR tags LIKE %?% ORDER BY create_time DESC
    List<Tweet> findByContentContainingOrTagsContainingOrderByCreateTimeDesc(String contentKeyword, String tagKeyword);
}