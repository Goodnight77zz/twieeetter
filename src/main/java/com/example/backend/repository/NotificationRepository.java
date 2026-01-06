package com.example.backend.repository;

import com.example.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;



public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 统计未读数量 (用于右上角红点)
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // 获取我的消息列表 (按时间倒序)
    List<Notification> findByRecipientIdOrderByCreateTimeDesc(Long recipientId);
}