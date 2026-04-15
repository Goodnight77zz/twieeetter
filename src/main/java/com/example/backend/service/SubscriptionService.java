package com.example.backend.service;

import com.example.backend.entity.User;
import com.example.backend.entity.UserSubscription;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.UserSubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SubscriptionService {

    public static final String TYPE_RESEARCH_AREA = "RESEARCH_AREA";

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    public List<UserSubscription> getMySubscriptions(Long userId) {
        return userSubscriptionRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }

    public UserSubscription addResearchAreaSubscription(Long userId, String researchArea) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String normalizedValue = normalizeValue(researchArea);
        if (normalizedValue == null) {
            throw new RuntimeException("订阅方向不能为空");
        }

        return userSubscriptionRepository
                .findByUserIdAndSubscriptionTypeAndTargetValue(userId, TYPE_RESEARCH_AREA, normalizedValue)
                .orElseGet(() -> {
                    UserSubscription subscription = new UserSubscription();
                    subscription.setUser(user);
                    subscription.setSubscriptionType(TYPE_RESEARCH_AREA);
                    subscription.setTargetValue(normalizedValue);
                    subscription.setCreateTime(LocalDateTime.now());
                    return userSubscriptionRepository.save(subscription);
                });
    }

    public void removeSubscription(Long subscriptionId, Long userId) {
        UserSubscription subscription = userSubscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("订阅不存在"));

        if (subscription.getUser() == null || !subscription.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权删除该订阅");
        }

        userSubscriptionRepository.delete(subscription);
    }

    public List<User> findSubscribersByResearchArea(String researchArea) {
        String normalizedValue = normalizeValue(researchArea);
        if (normalizedValue == null) {
            return List.of();
        }

        List<UserSubscription> subscriptions = userSubscriptionRepository
                .findBySubscriptionTypeAndTargetValue(TYPE_RESEARCH_AREA, normalizedValue);

        Set<Long> seen = new LinkedHashSet<>();
        return subscriptions.stream()
                .map(UserSubscription::getUser)
                .filter(user -> user != null && user.getId() != null)
                .filter(user -> seen.add(user.getId()))
                .toList();
    }

    public String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
