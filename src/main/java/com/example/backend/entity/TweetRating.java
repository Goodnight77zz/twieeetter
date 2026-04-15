package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tweet_ratings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "tweet_id"}) // 🔥 核心：物理层面保证每人每文只有一条记录
})
@Data
public class TweetRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer score1; // 创新性
    private Integer score2; // 方法严谨性
    private Integer score3; // 证据充分性
    private Integer score4; // 可复现性
    private Integer score5; // 应用/传播价值

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "tweet_id")
    private Tweet tweet;
}