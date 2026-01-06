package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String nickname;

    private String password;

    private String email;


    private String avatar; // 存头像的文件名 (例如: uuid_avatar.jpg)

    @Column(length = 200)
    private String bio;    // 个人简介 (Bio)

    @Column(columnDefinition = "int default 0")
    private Integer reputation = 1; // 学术信誉分

    // 辅助方法：获取用户头衔
    public String getAcademicTitle() {
        if (reputation < 100) return "研究生 (Student)";
        if (reputation < 500) return "讲师 (Lecturer)";
        return "教授 (Professor)";
    }

    // 辅助方法：获取评分权重 (核心逻辑)
    public double getWeight() {
        if (reputation < 100) return 1.0;
        if (reputation < 500) return 2.0; // 讲师权重 x2
        return 5.0; // 教授权重 x5
    }

}