package com.example.backend.repository;

import com.example.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // 统计未读数量 (用于右上角红点)
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // 获取我的消息列表 (按时间倒序)
    List<Notification> findByRecipientIdOrderByCreateTimeDesc(Long recipientId);

    // 获取未读消息列表
    List<Notification> findByRecipientIdAndIsReadFalse(Long recipientId);
}
