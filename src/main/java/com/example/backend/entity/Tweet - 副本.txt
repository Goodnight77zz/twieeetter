package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data; // 既然你用了 Lombok，我就继续用 Data
import java.time.LocalDateTime;

@Entity
@Table(name = "tweets")
@Data
public class Tweet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000)
    private String content;

    private String filePath;
    private String originalFilename;

    // === 🔥 新增：标签字段 ===
    @Column(name = "tags")
    private String tags;

    private LocalDateTime createTime;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User author;
}