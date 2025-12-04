package com.example.backend.controller;

import com.example.backend.entity.Tweet;
import com.example.backend.repository.TweetRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.TweetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.backend.entity.User;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tweets")
public class TweetController {

    @Autowired private TweetService tweetService;
    @Autowired private TweetRepository tweetRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.example.backend.repository.CommentRepository commentRepository;
    @Autowired private com.example.backend.repository.TweetLikeRepository tweetLikeRepository;

    // ===  tags 参数 ===
    @PostMapping
    public String postTweet(
            @RequestParam("content") String content,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "tags", required = false) String tags, // 接收前端传来的标签
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        try {
            // 调用升级后的 Service 方法
            tweetService.postTweetWithFile(content, userId, tags, file);
            return "发布成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "发布失败: " + e.getMessage();
        }
    }

    // === 🔥 新增：搜索接口 ===
    @GetMapping("/search")
    public List<Tweet> searchTweets(@RequestParam String keyword) {
        return tweetService.searchTweets(keyword);
    }

    // 原有接口保持不变...
    @GetMapping
    public List<Tweet> getAllTweets() {
        return tweetService.getAllTweets();
    }

    @GetMapping("/user/{userId}")
    public List<Tweet> getUserTweets(@PathVariable Long userId) {
        return tweetRepository.findByAuthorIdOrderByCreateTimeDesc(userId);
    }

    @GetMapping("/{id}")
    public Tweet getTweetDetail(@PathVariable Long id) {
        return tweetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("推文不存在"));
    }

    @PostMapping("/{tweetId}/comments")
    public String addComment(@PathVariable Long tweetId, @RequestParam Long userId, @RequestParam String content) {
        Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        com.example.backend.entity.Comment comment = new com.example.backend.entity.Comment();
        comment.setContent(content);
        comment.setUser(user);
        comment.setTweet(tweet);
        comment.setCreateTime(java.time.LocalDateTime.now());

        commentRepository.save(comment);
        return "评论成功";
    }

    @GetMapping("/{tweetId}/comments")
    public List<com.example.backend.entity.Comment> getComments(@PathVariable Long tweetId) {
        return commentRepository.findByTweetIdOrderByCreateTimeDesc(tweetId);
    }

    @PostMapping("/{tweetId}/like")
    public String toggleLike(@PathVariable Long tweetId, @RequestParam Long userId) {
        if (tweetLikeRepository.existsByUserIdAndTweetId(userId, tweetId)) {
            com.example.backend.entity.TweetLike like = tweetLikeRepository.findByUserIdAndTweetId(userId, tweetId).get();
            tweetLikeRepository.delete(like);
            return "取消点赞";
        } else {
            Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();
            User user = userRepository.findById(userId).orElseThrow();

            com.example.backend.entity.TweetLike like = new com.example.backend.entity.TweetLike();
            like.setTweet(tweet);
            like.setUser(user);
            tweetLikeRepository.save(like);
            return "点赞成功";
        }
    }

    @GetMapping("/{tweetId}/like-status")
    public Map<String, Object> getLikeStatus(@PathVariable Long tweetId, @RequestParam Long userId) {
        long count = tweetLikeRepository.countByTweetId(tweetId);
        boolean isLiked = tweetLikeRepository.existsByUserIdAndTweetId(userId, tweetId);
        return Map.of("count", count, "isLiked", isLiked);
    }
}