package com.example.backend.controller;

import com.example.backend.entity.UserSubscription;
import com.example.backend.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @GetMapping("/my")
    public List<UserSubscription> getMySubscriptions(@RequestParam Long userId) {
        return subscriptionService.getMySubscriptions(userId);
    }

    @PostMapping
    public Map<String, Object> addSubscription(
            @RequestParam Long userId,
            @RequestParam String researchArea
    ) {
        UserSubscription subscription = subscriptionService.addResearchAreaSubscription(userId, researchArea);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "订阅成功");
        result.put("subscription", subscription);
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteSubscription(@PathVariable Long id, @RequestParam Long userId) {
        subscriptionService.removeSubscription(id, userId);
        return Map.of("message", "取消订阅成功");
    }
}
