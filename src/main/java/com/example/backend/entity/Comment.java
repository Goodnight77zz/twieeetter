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
    private String content; // 评论内容

    private LocalDateTime createTime;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user; // 谁评论的

    @ManyToOne
    @JoinColumn(name = "tweet_id")
    private Tweet tweet; // 评论哪篇文章
}