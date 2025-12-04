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

}