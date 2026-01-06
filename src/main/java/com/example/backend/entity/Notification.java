package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 接收通知的人 (比如文章作者、被回复的人)
    @ManyToOne
    @JoinColumn(name = "recipient_id")
    private User recipient;

    // 触发通知的人 (点赞者、评论者)
    @ManyToOne
    @JoinColumn(name = "actor_id")
    private User actor;

    // 通知类型: 1=评论文章, 2=回复评论, 3=点赞文章, 4=点赞评论
    private Integer type;

    // 关联的目标ID (文章ID 或 评论ID)
    private Long targetId;

    // 关联的文章ID (方便跳转)
    private Long tweetId;

    private boolean isRead = false; // 是否已读

    private LocalDateTime createTime;
}