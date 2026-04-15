package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String content; // 评审意见

    @Column(name = "comment_type", length = 30)
    private String commentType = "discussion";

    @Column(name = "accepted_by_author")
    private Boolean acceptedByAuthor = false;

    // === 🔥 新增：学术评分字段 (1-5分) ===
    @Column(name = "score_innovation")
    private Integer scoreInnovation = 0; // 创新性

    @Column(name = "score_methodology")
    private Integer scoreMethodology = 0; // 方法论严谨性

    @Column(name = "score_utility")
    private Integer scoreUtility = 0;     // 实用价值

    private LocalDateTime createTime;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "tweet_id")
    private Tweet tweet;

    @Column(name = "parent_id")
    private Long parentId;

    @ManyToOne
    @JoinColumn(name = "reply_to_user_id")
    private User replyToUser;
}
