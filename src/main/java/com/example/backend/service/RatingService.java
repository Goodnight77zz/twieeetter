package com.example.backend.service;

import com.example.backend.entity.*;
import com.example.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class RatingService {

    @Autowired private TweetRatingRepository ratingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TweetRepository tweetRepository;

    public void rateTweet(Long userId, Long tweetId, int s1, int s2, int s3) {
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

        ratingRepository.save(rating);
        updateAuthorReputation(tweet.getAuthor());
    }

    public double calculateAverageScore(Long tweetId) {
        List<TweetRating> ratings = ratingRepository.findByTweetId(tweetId);
        if (ratings.isEmpty()) return 0.0;

        double totalWeightedScore = 0;
        double totalWeight = 0;

        for (TweetRating r : ratings) {
            double weight = r.getUser().getWeight();
            double avgOfThisUser = (r.getScore1() + r.getScore2() + r.getScore3()) / 3.0;
            totalWeightedScore += avgOfThisUser * weight;
            totalWeight += weight;
        }

        return totalWeight == 0 ? 0 : (totalWeightedScore / totalWeight);
    }

    // 🔥 修改：返回类型改为 Map<String, Object> 以包含统计数量
    public Map<String, Object> getRadarScores(Long tweetId) {
        List<TweetRating> ratings = ratingRepository.findByTweetId(tweetId);
        Map<String, Object> result = new HashMap<>();

        // 默认值
        result.put("s1", 0.0);
        result.put("s2", 0.0);
        result.put("s3", 0.0);
        result.put("expertCount", 0); // 专家人数
        result.put("expertScore", 0.0); // 专家平均分

        if (ratings.isEmpty()) return result;

        double sum1 = 0, sum2 = 0, sum3 = 0;
        double totalWeight = 0;

        // 专家统计变量
        int expertCount = 0;
        double expertTotalScore = 0;

        for (TweetRating r : ratings) {
            double weight = r.getUser().getWeight();
            double avgOfThisUser = (r.getScore1() + r.getScore2() + r.getScore3()) / 3.0;

            sum1 += r.getScore1() * weight;
            sum2 += r.getScore2() * weight;
            sum3 += r.getScore3() * weight;
            totalWeight += weight;

            // 🔥 统计专家 (信誉分 >= 20 视为认证学者/专家)
            if (r.getUser().getReputation() >= 20) {
                expertCount++;
                expertTotalScore += avgOfThisUser;
            }
        }

        if (totalWeight > 0) {
            result.put("s1", Math.round(sum1 / totalWeight * 10.0) / 10.0);
            result.put("s2", Math.round(sum2 / totalWeight * 10.0) / 10.0);
            result.put("s3", Math.round(sum3 / totalWeight * 10.0) / 10.0);
        }

        // 填入专家数据
        result.put("expertCount", expertCount);
        result.put("expertScore", expertCount > 0 ? Math.round(expertTotalScore / expertCount * 10.0) / 10.0 : 0.0);

        return result;
    }

    private void updateAuthorReputation(User author) {
        author.setReputation(author.getReputation() + 1);
        userRepository.save(author);
    }
}