package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "friendships")
@Data
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 谁发起的关注 (粉丝)
    @ManyToOne
    @JoinColumn(name = "follower_id")
    private User follower;

    // 关注了谁 (偶像)
    @ManyToOne
    @JoinColumn(name = "following_id")
    private User following;

    private LocalDateTime createTime;
}