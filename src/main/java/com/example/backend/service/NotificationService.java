package com.example.backend.service;

import com.example.backend.entity.Notification;
import com.example.backend.entity.User;
import com.example.backend.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public static final int TYPE_TWEET_LIKE = 0;
    public static final int TYPE_TWEET_COMMENT = 1;
    public static final int TYPE_COMMENT_REPLY = 2;
    public static final int TYPE_SUBSCRIPTION_NEW_RESEARCH = 3;
    public static final int TYPE_COMMENT_LIKE = 4;
     public static final int TYPE_COMMENT_ACCEPTED = 5;

    /**
     * 发送通知
     * @param recipient 接收者
     * @param actor 触发者
     * @param type 类型 (0:点赞文章, 1:评论文章, 2:回复评论, 3:订阅推送新成果, 4:点赞评论, 5:作者采纳评论)
     * @param targetId 目标ID (文章ID或评论ID)
     * @param tweetId 关联的文章ID (方便跳转)
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "notifications:list", key = "#recipient.id", condition = "#recipient != null && #recipient.id != null"),
            @CacheEvict(cacheNames = "notifications:unread", key = "#recipient.id", condition = "#recipient != null && #recipient.id != null")
    })
    public void send(User recipient, User actor, int type, Long targetId, Long tweetId) {
        if (recipient == null || actor == null || recipient.getId() == null || actor.getId() == null) {
            return;
        }

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

    public void sendSubscriptionNotification(User recipient, User actor, Long tweetId) {
        send(recipient, actor, TYPE_SUBSCRIPTION_NEW_RESEARCH, tweetId, tweetId);
    }

    // 获取未读数量
    @Cacheable(cacheNames = "notifications:unread", key = "#userId")
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    // 获取通知列表
    @Cacheable(cacheNames = "notifications:list", key = "#userId")
    public List<Notification> getMyNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreateTimeDesc(userId);
    }

    // 标记单条已读
    @Caching(evict = {
            @CacheEvict(cacheNames = "notifications:list", key = "#result", condition = "#result != null"),
            @CacheEvict(cacheNames = "notifications:unread", key = "#result", condition = "#result != null")
    })
    public Long markAsRead(Long notificationId) {
        return notificationRepository.findById(notificationId).map(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
            return n.getRecipient() != null ? n.getRecipient().getId() : null;
        }).orElse(null);
    }

    // 标记当前用户所有未读通知为已读
    @Caching(evict = {
            @CacheEvict(cacheNames = "notifications:list", key = "#userId"),
            @CacheEvict(cacheNames = "notifications:unread", key = "#userId")
    })
    public int markAllAsRead(Long userId) {
        List<Notification> unreadList = notificationRepository.findByRecipientIdAndIsReadFalse(userId);
        unreadList.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unreadList);
        return unreadList.size();
    }
}
