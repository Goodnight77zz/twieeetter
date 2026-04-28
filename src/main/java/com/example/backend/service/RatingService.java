package com.example.backend.service;

import com.example.backend.entity.*;
import com.example.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RatingService {

    @Autowired private TweetRatingRepository ratingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TweetRepository tweetRepository;

    public void rateTweet(Long userId, Long tweetId, int s1, int s2, int s3, int s4, int s5) {
        User user = userRepository.findById(userId).orElseThrow();
        Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();

        TweetRating rating = ratingRepository.findByUserIdAndTweetId(userId, tweetId)
                .orElse(new TweetRating());

        if (rating.getId() == null) {
            rating.setUser(user);
            rating.setTweet(tweet);
        }

        rating.setScore1(s1);
        rating.setScore2(s2);
        rating.setScore3(s3);
        rating.setScore4(s4);
        rating.setScore5(s5);

        ratingRepository.save(rating);
        tweet.setLastInteractionTime(LocalDateTime.now());
        tweetRepository.save(tweet);
        updateAuthorReputation(tweet.getAuthor());
    }

    public double calculateAverageScore(Long tweetId) {
        List<TweetRating> ratings = ratingRepository.findByTweetId(tweetId);
        if (ratings.isEmpty()) return 0.0;

        double totalWeightedScore = 0;
        double totalWeight = 0;

        for (TweetRating r : ratings) {
            double weight = r.getUser().getWeight();
            double avgOfThisUser = (safeInt(r.getScore1()) + safeInt(r.getScore2()) + safeInt(r.getScore3()) + safeInt(r.getScore4()) + safeInt(r.getScore5())) / 5.0;
            totalWeightedScore += avgOfThisUser * weight;
            totalWeight += weight;
        }

        return totalWeight == 0 ? 0 : (totalWeightedScore / totalWeight);
    }

    public Map<String, Object> getRadarScores(Long tweetId) {
        List<TweetRating> ratings = ratingRepository.findByTweetId(tweetId);
        Map<String, Object> result = new HashMap<>();

        result.put("s1", 0.0);
        result.put("s2", 0.0);
        result.put("s3", 0.0);
        result.put("s4", 0.0);
        result.put("s5", 0.0);
        result.put("expertS1", 0.0);
        result.put("expertS2", 0.0);
        result.put("expertS3", 0.0);
        result.put("expertS4", 0.0);
        result.put("expertS5", 0.0);
        result.put("expertCount", 0);
        result.put("expertScore", 0.0);
        result.put("ratingCount", 0);

        if (ratings.isEmpty()) return result;

        double sum1 = 0, sum2 = 0, sum3 = 0, sum4 = 0, sum5 = 0;
        double totalWeight = 0;

        int expertCount = 0;
        double expertTotalScore = 0;
        double expertSum1 = 0, expertSum2 = 0, expertSum3 = 0, expertSum4 = 0, expertSum5 = 0;

        for (TweetRating r : ratings) {
            double weight = r.getUser().getWeight();
            double avgOfThisUser = (safeInt(r.getScore1()) + safeInt(r.getScore2()) + safeInt(r.getScore3()) + safeInt(r.getScore4()) + safeInt(r.getScore5())) / 5.0;

            sum1 += safeInt(r.getScore1()) * weight;
            sum2 += safeInt(r.getScore2()) * weight;
            sum3 += safeInt(r.getScore3()) * weight;
            sum4 += safeInt(r.getScore4()) * weight;
            sum5 += safeInt(r.getScore5()) * weight;
            totalWeight += weight;

            if (r.getUser().getReputation() >= 20) {
                expertCount++;
                expertTotalScore += avgOfThisUser;
                expertSum1 += safeInt(r.getScore1());
                expertSum2 += safeInt(r.getScore2());
                expertSum3 += safeInt(r.getScore3());
                expertSum4 += safeInt(r.getScore4());
                expertSum5 += safeInt(r.getScore5());
            }
        }

        if (totalWeight > 0) {
            result.put("s1", Math.round(sum1 / totalWeight * 10.0) / 10.0);
            result.put("s2", Math.round(sum2 / totalWeight * 10.0) / 10.0);
            result.put("s3", Math.round(sum3 / totalWeight * 10.0) / 10.0);
            result.put("s4", Math.round(sum4 / totalWeight * 10.0) / 10.0);
            result.put("s5", Math.round(sum5 / totalWeight * 10.0) / 10.0);
        }

        result.put("expertCount", expertCount);
        result.put("expertScore", expertCount > 0 ? Math.round(expertTotalScore / expertCount * 10.0) / 10.0 : 0.0);
        if (expertCount > 0) {
            result.put("expertS1", Math.round(expertSum1 / expertCount * 10.0) / 10.0);
            result.put("expertS2", Math.round(expertSum2 / expertCount * 10.0) / 10.0);
            result.put("expertS3", Math.round(expertSum3 / expertCount * 10.0) / 10.0);
            result.put("expertS4", Math.round(expertSum4 / expertCount * 10.0) / 10.0);
            result.put("expertS5", Math.round(expertSum5 / expertCount * 10.0) / 10.0);
        }
        result.put("ratingCount", ratings.size());

        return result;
    }

    public Map<String, Object> getEvaluationSignals(Tweet tweet, long likeCount, long commentCount) {
        double avgScore = calculateAverageScore(tweet.getId());
        Map<String, Object> radar = getRadarScores(tweet.getId());
        long downloadCount = safeLong(tweet.getDownloadCount());
        long viewCount = safeLong(tweet.getViewCount());
        long shareCount = safeLong(tweet.getShareCount());
        long bookmarkCount = safeLong(tweet.getBookmarkCount());
        int ratingCount = ((Number) radar.getOrDefault("ratingCount", 0)).intValue();
        long expertCount = ((Number) radar.getOrDefault("expertCount", 0)).longValue();
        double expertScore = ((Number) radar.getOrDefault("expertScore", 0.0)).doubleValue();
        LocalDateTime lastInteractionTime = tweet.getLastInteractionTime() != null ? tweet.getLastInteractionTime() : tweet.getCreateTime();
        long recencyBoost = calculateRecencyBoost(lastInteractionTime);
        long hotScore = Math.round(
                likeCount * 3
                        + commentCount * 4
                        + downloadCount * 2
                        + viewCount
                        + shareCount * 5
                        + bookmarkCount * 4
                        + avgScore * 5
                        + ratingCount * 2
                        + expertCount * 3
                        + recencyBoost
        );

        Map<String, Object> signals = new HashMap<>();
        signals.put("likes", likeCount);
        signals.put("comments", commentCount);
        signals.put("downloads", downloadCount);
        signals.put("views", viewCount);
        signals.put("shares", shareCount);
        signals.put("bookmarks", bookmarkCount);
        signals.put("averageScore", Math.round(avgScore * 10.0) / 10.0);
        signals.put("averageScoreText", String.format("%.1f", avgScore));
        signals.put("ratingCount", ratingCount);
        signals.put("expertCount", expertCount);
        signals.put("expertScore", expertCount > 0 ? Math.round(expertScore * 10.0) / 10.0 : 0.0);
        signals.put("hotScore", hotScore);
        signals.put("freshnessLevel", resolveFreshnessLevel(lastInteractionTime));
        signals.put("engagementLevel", resolveEngagementLevel(hotScore, ratingCount, commentCount, shareCount, bookmarkCount));
        signals.put("lastInteractionTime", lastInteractionTime != null ? lastInteractionTime.toString() : null);
        signals.put("updateTime", tweet.getUpdateTime() != null ? tweet.getUpdateTime().toString() : null);
        signals.put("radar", radar);
        return signals;
    }

    public Map<Long, Map<String, Object>> getEvaluationSignalsBatch(
            List<Tweet> tweets,
            Map<Long, Long> likeCounts,
            Map<Long, Long> commentCounts
    ) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (tweets == null || tweets.isEmpty()) {
            return result;
        }

        List<Long> tweetIds = tweets.stream()
                .map(Tweet::getId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        Map<Long, RatingAggregate> ratingByTweetId = loadRatingAggregates(tweetIds);

        for (Tweet tweet : tweets) {
            if (tweet == null || tweet.getId() == null) {
                continue;
            }
            long likeCount = likeCounts.getOrDefault(tweet.getId(), 0L);
            long commentCount = commentCounts.getOrDefault(tweet.getId(), 0L);
            RatingAggregate aggregate = ratingByTweetId.getOrDefault(tweet.getId(), RatingAggregate.EMPTY);
            result.put(tweet.getId(), buildSignalsFromAggregate(tweet, likeCount, commentCount, aggregate));
        }
        return result;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private long calculateRecencyBoost(LocalDateTime lastInteractionTime) {
        if (lastInteractionTime == null) {
            return 0L;
        }
        long hours = Math.max(0, Duration.between(lastInteractionTime, LocalDateTime.now()).toHours());
        if (hours < 24) return 18L;
        if (hours < 72) return 10L;
        if (hours < 168) return 4L;
        return 0L;
    }

    private String resolveFreshnessLevel(LocalDateTime lastInteractionTime) {
        if (lastInteractionTime == null) {
            return "steady";
        }
        long hours = Math.max(0, Duration.between(lastInteractionTime, LocalDateTime.now()).toHours());
        if (hours < 24) return "active";
        if (hours < 168) return "recent";
        return "steady";
    }

    private String resolveEngagementLevel(long hotScore, int ratingCount, long commentCount, long shareCount, long bookmarkCount) {
        long deepSignals = ratingCount + commentCount + shareCount + bookmarkCount;
        if (hotScore >= 80 || deepSignals >= 18) return "high";
        if (hotScore >= 30 || deepSignals >= 6) return "medium";
        return "emerging";
    }

    private void updateAuthorReputation(User author) {
        author.setReputation(author.getReputation() + 1);
        userRepository.save(author);
    }

    private Map<Long, RatingAggregate> loadRatingAggregates(List<Long> tweetIds) {
        Map<Long, RatingAggregate> aggregateMap = new HashMap<>();
        if (tweetIds == null || tweetIds.isEmpty()) {
            return aggregateMap;
        }

        for (Object[] row : ratingRepository.aggregateSignalsByTweetIds(tweetIds)) {
            if (row == null || row.length < 11) {
                continue;
            }

            Long tweetId = asLong(row[0]);
            if (tweetId == null) {
                continue;
            }

            long ratingCount = asLong(row[1]) == null ? 0L : asLong(row[1]);
            double weightedTotal = asDouble(row[2]);
            double weightedTotalDenominator = asDouble(row[3]);
            double weightedS1 = asDouble(row[4]);
            double weightedS2 = asDouble(row[5]);
            double weightedS3 = asDouble(row[6]);
            double weightedS4 = asDouble(row[7]);
            double weightedS5 = asDouble(row[8]);
            long expertCount = asLong(row[9]) == null ? 0L : asLong(row[9]);
            double expertScore = asDouble(row[10]);

            double weightDenominator = weightedTotalDenominator <= 0 ? 0 : weightedTotalDenominator / 5.0;
            double averageScore = weightedTotalDenominator <= 0 ? 0 : weightedTotal / weightedTotalDenominator;
            double s1 = weightDenominator <= 0 ? 0 : weightedS1 / weightDenominator;
            double s2 = weightDenominator <= 0 ? 0 : weightedS2 / weightDenominator;
            double s3 = weightDenominator <= 0 ? 0 : weightedS3 / weightDenominator;
            double s4 = weightDenominator <= 0 ? 0 : weightedS4 / weightDenominator;
            double s5 = weightDenominator <= 0 ? 0 : weightedS5 / weightDenominator;

            aggregateMap.put(tweetId, new RatingAggregate(
                    ratingCount,
                    averageScore,
                    s1,
                    s2,
                    s3,
                    s4,
                    s5,
                    expertCount,
                    expertScore
            ));
        }

        return aggregateMap;
    }

    private Map<String, Object> buildSignalsFromAggregate(Tweet tweet, long likeCount, long commentCount, RatingAggregate aggregate) {
        double avgScore = aggregate.averageScore;
        long downloadCount = safeLong(tweet.getDownloadCount());
        long viewCount = safeLong(tweet.getViewCount());
        long shareCount = safeLong(tweet.getShareCount());
        long bookmarkCount = safeLong(tweet.getBookmarkCount());
        int ratingCount = (int) aggregate.ratingCount;
        long expertCount = aggregate.expertCount;
        double expertScore = aggregate.expertScore;
        LocalDateTime lastInteractionTime = tweet.getLastInteractionTime() != null ? tweet.getLastInteractionTime() : tweet.getCreateTime();
        long recencyBoost = calculateRecencyBoost(lastInteractionTime);
        long hotScore = Math.round(
                likeCount * 3
                        + commentCount * 4
                        + downloadCount * 2
                        + viewCount
                        + shareCount * 5
                        + bookmarkCount * 4
                        + avgScore * 5
                        + ratingCount * 2
                        + expertCount * 3
                        + recencyBoost
        );

        Map<String, Object> radar = new HashMap<>();
        radar.put("s1", roundOneDecimal(aggregate.s1));
        radar.put("s2", roundOneDecimal(aggregate.s2));
        radar.put("s3", roundOneDecimal(aggregate.s3));
        radar.put("s4", roundOneDecimal(aggregate.s4));
        radar.put("s5", roundOneDecimal(aggregate.s5));
        radar.put("expertS1", 0.0);
        radar.put("expertS2", 0.0);
        radar.put("expertS3", 0.0);
        radar.put("expertS4", 0.0);
        radar.put("expertS5", 0.0);
        radar.put("expertCount", expertCount);
        radar.put("expertScore", expertCount > 0 ? roundOneDecimal(expertScore) : 0.0);
        radar.put("ratingCount", ratingCount);

        Map<String, Object> signals = new HashMap<>();
        signals.put("likes", likeCount);
        signals.put("comments", commentCount);
        signals.put("downloads", downloadCount);
        signals.put("views", viewCount);
        signals.put("shares", shareCount);
        signals.put("bookmarks", bookmarkCount);
        signals.put("averageScore", roundOneDecimal(avgScore));
        signals.put("averageScoreText", String.format("%.1f", avgScore));
        signals.put("ratingCount", ratingCount);
        signals.put("expertCount", expertCount);
        signals.put("expertScore", expertCount > 0 ? roundOneDecimal(expertScore) : 0.0);
        signals.put("hotScore", hotScore);
        signals.put("freshnessLevel", resolveFreshnessLevel(lastInteractionTime));
        signals.put("engagementLevel", resolveEngagementLevel(hotScore, ratingCount, commentCount, shareCount, bookmarkCount));
        signals.put("lastInteractionTime", lastInteractionTime != null ? lastInteractionTime.toString() : null);
        signals.put("updateTime", tweet.getUpdateTime() != null ? tweet.getUpdateTime().toString() : null);
        signals.put("radar", radar);
        return signals;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private static class RatingAggregate {
        private static final RatingAggregate EMPTY = new RatingAggregate(0, 0, 0, 0, 0, 0, 0, 0, 0);

        private final long ratingCount;
        private final double averageScore;
        private final double s1;
        private final double s2;
        private final double s3;
        private final double s4;
        private final double s5;
        private final long expertCount;
        private final double expertScore;

        private RatingAggregate(long ratingCount,
                                double averageScore,
                                double s1,
                                double s2,
                                double s3,
                                double s4,
                                double s5,
                                long expertCount,
                                double expertScore) {
            this.ratingCount = ratingCount;
            this.averageScore = averageScore;
            this.s1 = s1;
            this.s2 = s2;
            this.s3 = s3;
            this.s4 = s4;
            this.s5 = s5;
            this.expertCount = expertCount;
            this.expertScore = expertScore;
        }
    }
}
