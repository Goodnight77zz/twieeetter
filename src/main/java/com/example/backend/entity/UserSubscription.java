package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Data
public class UserSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String subscriptionType;

    @Column(nullable = false, length = 200)
    private String targetValue;

    private LocalDateTime createTime;

    @Column(name = "last_email_sent_at")
    private LocalDateTime lastEmailSentAt;
}
