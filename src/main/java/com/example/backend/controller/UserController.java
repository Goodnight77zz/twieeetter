package com.example.backend.controller;

import com.example.backend.entity.Tweet;
import com.example.backend.entity.User;
import com.example.backend.entity.Friendship;
import com.example.backend.entity.TweetFavorite;
import com.example.backend.entity.UserSubscription;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.TweetFavoriteRepository;
import com.example.backend.repository.TweetLikeRepository;
import com.example.backend.repository.TweetRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.UserSubscriptionRepository;
import com.example.backend.service.RatingService;
import com.example.backend.service.SubscriptionService;
import com.example.backend.service.UserService;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TweetRepository tweetRepository;
    @Autowired
    private TweetFavoriteRepository tweetFavoriteRepository;
    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;
    @Autowired
    private RatingService ratingService;
    @Autowired
    private TweetLikeRepository tweetLikeRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private SubscriptionService subscriptionService;

    // 获取用户详情: GET /api/users/{id}
    @GetMapping("/{id}")
    public User getUserProfile(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    // 更新资料: POST /api/users/{id}
    @PostMapping("/{id}")
    public String updateProfile(
            @PathVariable Long id,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "bio", required = false) String bio,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar
    ) {
        try {
            userService.updateProfile(id, nickname, bio, avatar);
            return "更新成功";
        } catch (Exception e) {
            return "更新失败: " + e.getMessage();
        }
    }

    // === 搜索用户接口 ===
    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam String keyword) {
        return userService.searchUsers(keyword);
    }

    // === 关注/加好友接口 ===
    @PostMapping("/{id}/follow")
    public String followUser(@PathVariable Long id, @RequestParam Long targetUserId) {
        try {
            userService.followUser(id, targetUserId);
            return "关注成功";
        } catch (Exception e) {
            return "失败: " + e.getMessage();
        }
    }

    // 获取我的好友(关注)列表
    @GetMapping("/{id}/following")
    public List<User> getMyFollowing(@PathVariable Long id) {
        return userService.getMyFollowing(id);
    }

    @GetMapping("/{id}/follow-status")
    public Map<String, Object> getFollowStatus(@PathVariable Long id, @RequestParam Long targetUserId) {
        boolean following = userService.isFollowing(id, targetUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", id);
        result.put("targetUserId", targetUserId);
        result.put("following", following);
        return result;
    }

    // === 获取用户统计数据 ===
    @GetMapping("/{id}/stats")
    public Map<String, Long> getUserStats(@PathVariable Long id) {
        long tweetCount = tweetRepository.countByAuthorId(id);
        long followingCount = userRepository.countFollowing(id);
        long followerCount = userRepository.countFollowers(id);

        return Map.of(
                "tweetCount", tweetCount,
                "followingCount", followingCount,
                "followerCount", followerCount
        );
    }

    @Cacheable(cacheNames = "users:archive", key = "#id")
    @GetMapping("/{id}/research-archive")
    public Map<String, Object> getUserResearchArchive(@PathVariable Long id) {
        User user = userService.getUserById(id);
        List<Tweet> tweets = tweetRepository.findByAuthorIdOrderByCreateTimeDesc(id);

        long totalDownloads = tweets.stream()
                .mapToLong(tweet -> tweet.getDownloadCount() == null ? 0L : tweet.getDownloadCount())
                .sum();

        double avgScore = tweets.isEmpty()
                ? 0.0
                : tweets.stream()
                .mapToDouble(tweet -> ratingService.calculateAverageScore(tweet.getId()))
                .average()
                .orElse(0.0);

        List<String> topResearchAreas = getTopLabels(tweets, true);
        List<String> hotTags = getTopLabels(tweets, false);

        String primaryResearchDirection = topResearchAreas.isEmpty() ? "" : topResearchAreas.get(0);

        return Map.of(
                "userId", id,
                "displayName", user != null ? (user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : user.getUsername()) : "",
                "researchDirection", primaryResearchDirection,
                "topResearchAreas", topResearchAreas,
                "workCount", tweets.size(),
                "averageScore", Math.round(avgScore * 10.0) / 10.0,
                "totalDownloads", totalDownloads,
                "hotTags", hotTags
        );
    }

    @Cacheable(cacheNames = "users:interest", key = "#id")
    @GetMapping("/{id}/interest-profile")
    public Map<String, Object> getInterestProfile(@PathVariable Long id) {
        List<Tweet> publishedTweets = tweetRepository.findByAuthorIdOrderByCreateTimeDesc(id);
        List<TweetFavorite> favorites = tweetFavoriteRepository.findByUserIdOrderByCreateTimeDesc(id);
        List<Tweet> favoriteTweets = favorites.stream()
                .map(TweetFavorite::getTweet)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<UserSubscription> subscriptions = userSubscriptionRepository.findByUserIdOrderByCreateTimeDesc(id);
        List<Friendship> followingLinks = friendshipRepository.findAllByFollowerId(id);
        List<Tweet> followingTweets = followingLinks.stream()
                .map(Friendship::getFollowing)
                .filter(java.util.Objects::nonNull)
                .map(User::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .flatMap(userId -> tweetRepository.findByAuthorIdOrderByCreateTimeDesc(userId).stream().limit(5))
                .toList();

        Map<String, AreaSignal> signals = new LinkedHashMap<>();
        publishedTweets.forEach(tweet -> addTweetSignal(signals, tweet, 1.2, "publish", false));
        favoriteTweets.forEach(tweet -> addTweetSignal(signals, tweet, 2.4, "favorite", true));
        subscriptions.forEach(subscription -> addSubscriptionSignal(signals, subscription, 3.2));
        followingTweets.forEach(tweet -> addTweetSignal(signals, tweet, 0.8, "followed-author", isRecent(tweet.getCreateTime(), 45)));
        publishedTweets.forEach(tweet -> addTagSignals(signals, tweet.getTags(), tweet.getResearchArea(), 0.35));
        favoriteTweets.forEach(tweet -> addTagSignals(signals, tweet.getTags(), tweet.getResearchArea(), 0.55));

        List<Map<String, Object>> interestAreas = signals.values().stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(6)
                .map(this::toInterestAreaDto)
                .toList();

        String dominantInterest = interestAreas.isEmpty() ? "" : String.valueOf(interestAreas.get(0).get("name"));
        String profileSummary = buildInterestSummary(dominantInterest, interestAreas, subscriptions.size(), favorites.size(), publishedTweets.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("userId", id);
        summary.put("dominantInterest", dominantInterest);
        summary.put("interestAreas", interestAreas);
        summary.put("publishedTopAreas", getTopLabels(publishedTweets, true));
        summary.put("favoriteTopAreas", getTopLabels(favoriteTweets, true));
        summary.put("subscriptionAreas", subscriptions.stream()
                .map(UserSubscription::getTargetValue)
                .map(this::normalizeLabel)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        summary.put("followingAreaHints", getTopLabels(followingTweets, true));
        summary.put("interestSignalCount", publishedTweets.size() + favorites.size() + subscriptions.size() + followingTweets.size());
        summary.put("interestProfileReady", !interestAreas.isEmpty());
        summary.put("profileSummary", profileSummary);
        summary.put("signalModel", List.of("subscription", "favorite", "publish", "followed-author", "tag-affinity", "recency-boost"));
        return summary;
    }

    // 取消关注接口
    @PostMapping("/{id}/unfollow")
    public String unfollowUser(@PathVariable Long id, @RequestParam Long targetUserId) {
        try {
            userService.unfollowUser(id, targetUserId);
            return "已取消关注";
        } catch (Exception e) {
            return "操作失败: " + e.getMessage();
        }
    }

    private List<String> getTopLabels(List<Tweet> tweets, boolean useResearchArea) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Tweet tweet : tweets) {
            String raw = useResearchArea ? tweet.getResearchArea() : tweet.getTags();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] items = useResearchArea ? new String[]{raw} : raw.split("[,;，；\\n\\r]+");
            for (String item : items) {
                String label = item == null ? "" : item.trim();
                if (label.isEmpty()) {
                    continue;
                }
                counts.put(label, counts.getOrDefault(label, 0) + 1);
            }
        }
        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void addTweetSignal(Map<String, AreaSignal> signals, Tweet tweet, double baseWeight, String source, boolean includeEngagementBoost) {
        String area = normalizeLabel(tweet.getResearchArea());
        if (area == null) {
            return;
        }
        double score = baseWeight;
        if (isRecent(tweet.getCreateTime(), 60) || isRecent(tweet.getUpdateTime(), 60)) {
            score += 0.4;
        }
        if (includeEngagementBoost) {
            score += Math.min(1.2, safeLong(tweet.getBookmarkCount()) * 0.08 + safeLong(tweet.getViewCount()) * 0.005 + safeLong(tweetLikeRepository.countByTweetId(tweet.getId())) * 0.12);
        }
        AreaSignal signal = signals.computeIfAbsent(area, AreaSignal::new);
        signal.score += score;
        signal.signalCount++;
        switch (source) {
            case "publish" -> signal.publishCount++;
            case "favorite" -> signal.favoriteCount++;
            case "followed-author" -> signal.followingSignalCount++;
            default -> {
            }
        }
    }

    private void addSubscriptionSignal(Map<String, AreaSignal> signals, UserSubscription subscription, double weight) {
        String area = normalizeLabel(subscription.getTargetValue());
        if (area == null) {
            return;
        }
        double score = weight;
        if (isRecent(subscription.getCreateTime(), 45)) {
            score += 0.5;
        }
        AreaSignal signal = signals.computeIfAbsent(area, AreaSignal::new);
        signal.score += score;
        signal.subscriptionCount++;
        signal.signalCount++;
    }

    private void addTagSignals(Map<String, AreaSignal> signals, String tags, String fallbackArea, double weight) {
        String area = normalizeLabel(fallbackArea);
        if (area == null || tags == null || tags.isBlank()) {
            return;
        }
        String[] items = tags.split("[,;，；\\n\\r]+");
        int bonusCount = Math.min(items.length, 3);
        if (bonusCount <= 0) {
            return;
        }
        AreaSignal signal = signals.computeIfAbsent(area, AreaSignal::new);
        signal.score += weight * bonusCount;
        signal.tagSignalCount += bonusCount;
        signal.signalCount += bonusCount;
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isRecent(LocalDateTime time, long withinDays) {
        return time != null && Duration.between(time, LocalDateTime.now()).toDays() <= withinDays;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private Map<String, Object> toInterestAreaDto(AreaSignal signal) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", signal.name);
        item.put("score", Math.round(signal.score * 10.0) / 10.0);
        item.put("publishCount", signal.publishCount);
        item.put("favoriteCount", signal.favoriteCount);
        item.put("subscriptionCount", signal.subscriptionCount);
        item.put("followingSignalCount", signal.followingSignalCount);
        item.put("tagSignalCount", signal.tagSignalCount);
        item.put("signalCount", signal.signalCount);
        item.put("reasons", buildReasons(signal));
        return item;
    }

    private List<String> buildReasons(AreaSignal signal) {
        List<String> reasons = new ArrayList<>();
        if (signal.subscriptionCount > 0) reasons.add("subscription");
        if (signal.favoriteCount > 0) reasons.add("favorite");
        if (signal.publishCount > 0) reasons.add("publish");
        if (signal.followingSignalCount > 0) reasons.add("followed-author");
        if (signal.tagSignalCount > 0) reasons.add("tag-affinity");
        return reasons;
    }

    private String buildInterestSummary(String dominantInterest, List<Map<String, Object>> interestAreas, int subscriptionCount, int favoriteCount, int publishCount) {
        if (dominantInterest == null || dominantInterest.isBlank()) {
            return "当前行为信号较少，尚未形成稳定兴趣领域。";
        }
        String secondary = interestAreas.size() > 1 ? String.valueOf(interestAreas.get(1).get("name")) : dominantInterest;
        return "当前兴趣画像以 " + dominantInterest + " 为核心，同时对 " + secondary + " 保持持续关注；综合了订阅、收藏、发文与关注作者线索。";
    }

    private static class AreaSignal {
        private final String name;
        private double score;
        private int publishCount;
        private int favoriteCount;
        private int subscriptionCount;
        private int followingSignalCount;
        private int tagSignalCount;
        private int signalCount;

        private AreaSignal(String name) {
            this.name = name;
        }
    }
}
