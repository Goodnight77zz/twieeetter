package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tweet_likes", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "tweet_id"}) // 防止重复点赞
})
@Data
public class TweetLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "tweet_id")
    private Tweet tweet;
}