package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth") // 所有接口都以 /api/auth 开头
public class AuthController {

    @Autowired
    private UserService userService;

    // 注册接口: POST http://localhost:8080/api/auth/register
    @PostMapping("/register")
    public String register(@RequestBody User user) {
        try {
            userService.register(user);
            return "注册成功";
        } catch (Exception e) {
            return "注册失败: " + e.getMessage();
        }
    }

    // 登录接口: POST http://localhost:8080/api/auth/login
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        User user = userService.login(username, password);
        if (user != null) {
            // 返回 JSON 数据，告诉前端是谁登录了
            return Map.of(
                    "message", "success",
                    "userId", user.getId(),
                    "username", user.getUsername()
            );
        } else {
            return Map.of("message", "fail");
        }
    }
}