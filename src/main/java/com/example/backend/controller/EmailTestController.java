package com.example.backend.controller;

import com.example.backend.service.EmailService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class EmailTestController {

    private final EmailService emailService;

    public EmailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send-email")
    public Map<String, Object> sendTestEmail(
            @RequestParam String to,
            @RequestParam(defaultValue = "Open Research Platform 测试邮件") String subject,
            @RequestParam(defaultValue = "这是一封来自开放研究分享平台的测试邮件") String content
    ) {
        emailService.sendSimpleMail(to, subject, content + "\n\n发送时间: " + LocalDateTime.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("to", to);
        result.put("subject", subject);
        result.put("message", "测试邮件发送请求已完成");
        return result;
    }
}
