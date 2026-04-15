package com.example.backend.service;

import com.example.backend.entity.Tweet;
import com.example.backend.entity.UserSubscription;
import com.example.backend.repository.TweetRepository;
import com.example.backend.repository.UserSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionDigestService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_TWEETS_PER_EMAIL = 5;

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TweetRepository tweetRepository;
    private final EmailService emailService;
    private final SubscriptionService subscriptionService;
    private final String appBaseUrl;

    public SubscriptionDigestService(UserSubscriptionRepository userSubscriptionRepository,
                                     TweetRepository tweetRepository,
                                     EmailService emailService,
                                     SubscriptionService subscriptionService,
                                     @Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.userSubscriptionRepository = userSubscriptionRepository;
        this.tweetRepository = tweetRepository;
        this.emailService = emailService;
        this.subscriptionService = subscriptionService;
        this.appBaseUrl = appBaseUrl;
    }

    @Transactional
    public Map<String, Object> sendDigestNow() {
        return sendDigestForAllSubscriptions();
    }

    @Scheduled(cron = "${app.digest.cron:0 0 8 * * ?}")
    @Transactional
    public void runScheduledDigest() {
        sendDigestForAllSubscriptions();
    }

    private Map<String, Object> sendDigestForAllSubscriptions() {
        List<UserSubscription> subscriptions = userSubscriptionRepository
                .findBySubscriptionTypeOrderByCreateTimeDesc(SubscriptionService.TYPE_RESEARCH_AREA);

        int processed = 0;
        int sent = 0;
        int skipped = 0;

        for (UserSubscription subscription : subscriptions) {
            processed++;
            if (sendDigestForSubscription(subscription)) {
                sent++;
            } else {
                skipped++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processedSubscriptions", processed);
        result.put("sentEmails", sent);
        result.put("skippedSubscriptions", skipped);
        result.put("executedAt", LocalDateTime.now());
        return result;
    }

    private boolean sendDigestForSubscription(UserSubscription subscription) {
        if (subscription == null || subscription.getUser() == null) {
            return false;
        }

        String email = subscription.getUser().getEmail();
        if (email == null || email.isBlank()) {
            return false;
        }

        String researchArea = subscription.getTargetValue();
        if (researchArea == null || researchArea.isBlank()) {
            return false;
        }

        LocalDateTime since = subscription.getLastEmailSentAt();
        if (since == null) {
            since = subscription.getCreateTime() != null
                    ? subscription.getCreateTime()
                    : LocalDateTime.now().minusDays(7);
        }

        List<Tweet> tweets = tweetRepository
                .findByResearchAreaIgnoreCaseAndCreateTimeAfterOrderByCreateTimeDesc(researchArea, since)
                .stream()
                .filter(tweet -> tweet.getAuthor() != null
                        && subscription.getUser().getId() != null
                        && !subscription.getUser().getId().equals(tweet.getAuthor().getId()))
                .limit(MAX_TWEETS_PER_EMAIL)
                .toList();

        if (tweets.isEmpty()) {
            return false;
        }

        String displayArea = formatResearchArea(researchArea);
        String subject = "[Open Research Platform] " + displayArea + " latest research updates";
        String html = buildDigestHtml(subscription, displayArea, tweets, since);

        emailService.sendHtmlMail(email, subject, html);
        subscription.setLastEmailSentAt(LocalDateTime.now());
        userSubscriptionRepository.save(subscription);
        return true;
    }

    private String buildDigestHtml(UserSubscription subscription,
                                   String displayArea,
                                   List<Tweet> tweets,
                                   LocalDateTime since) {
        String username = subscription.getUser().getUsername();
        StringBuilder builder = new StringBuilder();
        builder.append("<div style='font-family:Arial,sans-serif;line-height:1.6;color:#1f2937;padding:16px;'>");
        builder.append("<h2 style='margin-bottom:8px;'>Latest research in ")
                .append(escapeHtml(displayArea))
                .append("</h2>");
        builder.append("<p>Hello ")
                .append(escapeHtml(username == null ? "researcher" : username))
                .append(", here are the newest publications shared in your subscribed area since ")
                .append(escapeHtml(TIME_FORMATTER.format(since)))
                .append(".</p>");
        builder.append("<ul style='padding-left:20px;'>");

        for (Tweet tweet : tweets) {
            builder.append("<li style='margin-bottom:14px;'>")
                    .append("<strong>")
                    .append(escapeHtml(defaultText(tweet.getTitle(), "Untitled research")))
                    .append("</strong><br>")
                    .append("Authors: ")
                    .append(escapeHtml(defaultText(tweet.getAuthors(), "Unknown")))
                    .append("<br>")
                    .append("Institution: ")
                    .append(escapeHtml(defaultText(tweet.getInstitution(), "Unknown")))
                    .append("<br>")
                    .append("Published at: ")
                    .append(escapeHtml(formatTime(tweet.getCreateTime())))
                    .append("<br>")
                    .append("Summary: ")
                    .append(escapeHtml(shorten(tweet.getContent(), 180)))
                    .append("<br>")
                    .append("<a href='")
                    .append(appBaseUrl)
                    .append("/detail.html?id=")
                    .append(tweet.getId())
                    .append("'>View details</a>")
                    .append("</li>");
        }

        builder.append("</ul>");
        builder.append("<p style='margin-top:20px;color:#6b7280;'>This digest is generated automatically according to your research-area subscriptions on Open Research Platform.</p>");
        builder.append("</div>");
        return builder.toString();
    }

    private String formatResearchArea(String value) {
        String normalized = subscriptionService.normalizeValue(value);
        if (normalized == null) {
            return "your subscribed area";
        }
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "Unknown" : TIME_FORMATTER.format(time);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String shorten(String value, int maxLength) {
        String normalized = defaultText(value, "No abstract provided.").replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
