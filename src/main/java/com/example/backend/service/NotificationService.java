package com.example.backend.service;

import com.example.backend.entity.Notification;
import com.example.backend.entity.User;
import com.example.backend.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    /**
     * 发送通知
     * @param recipient 接收者
     * @param actor 触发者
     * @param type 类型 (1:评论文章, 2:回复评论, 3:点赞文章, 4:点赞评论)
     * @param targetId 目标ID (文章ID或评论ID)
     * @param tweetId 关联的文章ID (方便跳转)
     */
    public void send(User recipient, User actor, int type, Long targetId, Long tweetId) {
        // 自己操作自己不发通知
        if (recipient.getId().equals(actor.getId())) {
            return;
        }

        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setActor(actor);
        n.setType(type);
        n.setTargetId(targetId);
        n.setTweetId(tweetId);
        n.setRead(false); // 默认为未读
        n.setCreateTime(LocalDateTime.now());

        notificationRepository.save(n);
    }

    // 获取未读数量
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    // 获取通知列表
    public List<Notification> getMyNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreateTimeDesc(userId);
    }

    // 标记已读 (可选功能)
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }
}