package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "tweets")
@Data
public class Tweet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1000) // 论文摘要或描述，给长一点
    private String content;

    // === 新增：文件相关字段 ===
    private String filePath;         // 文件在硬盘上的真实路径
    private String originalFilename; // 文件的原始名字 (例如: algorithm_v1.pdf)
    // =======================

    private LocalDateTime createTime;

    // 关联用户 (多对一)
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User author;
}