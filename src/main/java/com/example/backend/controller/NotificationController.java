package com.example.backend.controller;

import com.example.backend.entity.Notification;
import com.example.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    // 获取未读数量 (用于轮询红点)
    @GetMapping("/unread-count")
    public long getUnreadCount(@RequestParam Long userId) {
        return notificationService.getUnreadCount(userId);
    }

    // 获取消息列表
    @GetMapping
    public List<Notification> getMyNotifications(@RequestParam Long userId) {
        return notificationService.getMyNotifications(userId);
    }

    // 标记单条已读
    @PostMapping("/{id}/read")
    public String markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return "success";
    }

    // 一键标记全部已读
    @PostMapping("/mark-all-read")
    public Map<String, Object> markAllAsRead(@RequestParam Long userId) {
        int updatedCount = notificationService.markAllAsRead(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("updatedCount", updatedCount);
        return result;
    }
}
