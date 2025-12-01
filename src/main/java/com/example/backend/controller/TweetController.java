package com.example.backend.controller;

import com.example.backend.entity.Tweet;
import com.example.backend.repository.TweetRepository; // 必须导入这个
import com.example.backend.repository.UserRepository;
import com.example.backend.service.TweetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.backend.entity.User;

import java.util.List;

@RestController
@RequestMapping("/api/tweets")
public class TweetController {

    @Autowired
    private TweetService tweetService;


    @Autowired
    private TweetRepository tweetRepository;


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.backend.repository.CommentRepository commentRepository;

    @Autowired
    private com.example.backend.repository.TweetLikeRepository tweetLikeRepository;
    // 发布接口 (支持文件上传)
    @PostMapping
    public String postTweet(
            @RequestParam("content") String content,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "file", required = false) MultipartFile file // file 改为非必填，防止报错
    ) {
        try {
            tweetService.postTweetWithFile(content, userId, file);
            return "发布成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "发布失败: " + e.getMessage();
        }
    }

    // 获取所有推文列表 (广场用)
    @GetMapping
    public List<Tweet> getAllTweets() {
        return tweetService.getAllTweets();
    }

    // === 获取某个用户的所有推文 (稿件管理用) ===
    @GetMapping("/user/{userId}")
    public List<Tweet> getUserTweets(@PathVariable Long userId) {
        // 这里用到了 tweetRepository，所以上面必须定义它
        return tweetRepository.findByAuthorIdOrderByCreateTimeDesc(userId);
    }

    // === 获取单条推文详情 (详情页用) ===
    @GetMapping("/{id}")
    public Tweet getTweetDetail(@PathVariable Long id) {
        return tweetRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("推文不存在"));
    }

    // 发表评论
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

    // 获取评论列表
    @GetMapping("/{tweetId}/comments")
    public List<com.example.backend.entity.Comment> getComments(@PathVariable Long tweetId) {
        return commentRepository.findByTweetIdOrderByCreateTimeDesc(tweetId);
    }

    // 点赞/取消点赞 (Toggle)
    @PostMapping("/{tweetId}/like")
    public String toggleLike(@PathVariable Long tweetId, @RequestParam Long userId) {
        // 如果已经点赞，就取消
        if (tweetLikeRepository.existsByUserIdAndTweetId(userId, tweetId)) {
            com.example.backend.entity.TweetLike like = tweetLikeRepository.findByUserIdAndTweetId(userId, tweetId).get();
            tweetLikeRepository.delete(like);
            return "取消点赞";
        } else {
            // 没点赞，就添加
            Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();
            User user = userRepository.findById(userId).orElseThrow();

            com.example.backend.entity.TweetLike like = new com.example.backend.entity.TweetLike();
            like.setTweet(tweet);
            like.setUser(user);
            tweetLikeRepository.save(like);
            return "点赞成功";
        }
    }

    // 获取点赞状态 (数量 + 我是否点过赞)
    @GetMapping("/{tweetId}/like-status")
    public java.util.Map<String, Object> getLikeStatus(@PathVariable Long tweetId, @RequestParam Long userId) {
        long count = tweetLikeRepository.countByTweetId(tweetId);
        boolean isLiked = tweetLikeRepository.existsByUserIdAndTweetId(userId, tweetId);
        return java.util.Map.of("count", count, "isLiked", isLiked);
    }
}