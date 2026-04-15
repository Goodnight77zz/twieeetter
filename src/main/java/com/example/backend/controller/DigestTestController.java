package com.example.backend.controller;

import com.example.backend.service.SubscriptionDigestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class DigestTestController {

    private final SubscriptionDigestService subscriptionDigestService;

    public DigestTestController(SubscriptionDigestService subscriptionDigestService) {
        this.subscriptionDigestService = subscriptionDigestService;
    }

    @PostMapping("/send-subscription-digest")
    public Map<String, Object> sendSubscriptionDigest() {
        return subscriptionDigestService.sendDigestNow();
    }
}
