package com.example.backend.controller;

import com.example.backend.entity.Tweet;
import com.example.backend.entity.User;
import com.example.backend.entity.Comment;
import com.example.backend.entity.CommentLike;
import com.example.backend.entity.TweetRating;
import com.example.backend.entity.TweetLike;
import com.example.backend.repository.TweetRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.repository.CommentRepository;
import com.example.backend.repository.TweetLikeRepository;
import com.example.backend.repository.CommentLikeRepository; // 新增
import com.example.backend.repository.TweetRatingRepository; // 新增
import com.example.backend.service.TweetService;
import com.example.backend.service.RatingService;
import com.example.backend.service.NotificationService;
import com.example.backend.service.CommentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tweets")
public class TweetController {

    @Autowired private TweetService tweetService;
    @Autowired private TweetRepository tweetRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private TweetLikeRepository tweetLikeRepository;
    @Autowired private RatingService ratingService;
    @Autowired private NotificationService notificationService;
    @Autowired private CommentService commentService;
    @Autowired private CommentLikeRepository commentLikeRepository;
    @Autowired private TweetRatingRepository ratingRepository;


    @PostMapping
    public String postTweet(
            @RequestParam("content") String content,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        try {
            tweetService.postTweetWithFile(content, userId, tags, file);
            return "发布成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "发布失败: " + e.getMessage();
        }
    }

    @GetMapping("/{tweetId}/comment-count")
    public long getCommentCount(@PathVariable Long tweetId) {
        return commentRepository.countByTweetId(tweetId);
    }

    @GetMapping("/search")
    public List<Tweet> searchTweets(@RequestParam String keyword) {
        return tweetService.searchTweets(keyword);
    }

    @GetMapping
    public List<Tweet> getAllTweets() {
        return tweetService.getAllTweets();
    }

    @GetMapping("/user/{userId}")
    public List<Tweet> getUserTweets(@PathVariable Long userId) {
        return tweetRepository.findByAuthorIdOrderByCreateTimeDesc(userId);
    }

    // === 🔥 核心修改：返回详情 + 平均分 + 雷达数据 + 我的评分 ===
    @GetMapping("/{id}/detail-dto")
    public Map<String, Object> getTweetDetailWithScore(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(() -> new RuntimeException("推文不存在"));

        double avgScore = ratingService.calculateAverageScore(id);
        String scoreStr = String.format("%.1f", avgScore);

        // 🔥 修改：类型改为 Map<String, Object>
        Map<String, Object> radar = ratingService.getRadarScores(id);

        com.example.backend.entity.TweetRating myRating = null;
        if (userId != null) {
            myRating = ratingRepository.findByUserIdAndTweetId(userId, id).orElse(null);
        }

        return Map.of(
                "tweet", tweet,
                "avgScore", scoreStr,
                "radar", radar,
                "myRating", myRating != null ? myRating : "null"
        );
    }

    // 下载接口
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        try {
            Tweet tweet = tweetRepository.findById(id).orElseThrow(() -> new RuntimeException("文件不存在"));
            if (tweet.getFilePath() == null || tweet.getFilePath().isEmpty()) {
                throw new RuntimeException("该研究未上传附件");
            }
            tweet.setDownloadCount(tweet.getDownloadCount() == null ? 1 : tweet.getDownloadCount() + 1);
            tweetRepository.save(tweet);

            Path filePath = Paths.get(tweet.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                String originalFilename = tweet.getOriginalFilename();
                String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                        .body(resource);
            } else {
                throw new RuntimeException("无法读取文件");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{tweetId}/rate")
    public String rateTweet(
            @PathVariable Long tweetId, @RequestParam Long userId,
            @RequestParam Integer s1, @RequestParam Integer s2, @RequestParam Integer s3
    ) {
        try {
            ratingService.rateTweet(userId, tweetId, s1, s2, s3);
            return "评分已更新";
        } catch (Exception e) {
            return "评分失败: " + e.getMessage();
        }
    }

    @PostMapping("/{tweetId}/comments")
    public String addComment(
            @PathVariable Long tweetId, @RequestParam Long userId, @RequestParam String content,
            @RequestParam(required = false) Long parentId, @RequestParam(required = false) Long replyToUserId
    ) {
        Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setUser(user);
        comment.setTweet(tweet);
        comment.setCreateTime(java.time.LocalDateTime.now());

        if (parentId != null) {
            comment.setParentId(parentId);
            if (replyToUserId != null) {
                User replyToUser = userRepository.findById(replyToUserId).orElse(null);
                comment.setReplyToUser(replyToUser);
                notificationService.send(replyToUser, user, 2, tweetId, tweetId);
            } else {
                Comment parentComment = commentRepository.findById(parentId).orElseThrow();
                notificationService.send(parentComment.getUser(), user, 2, tweetId, tweetId);
            }
        } else {
            notificationService.send(tweet.getAuthor(), user, 1, tweetId, tweetId);
        }

        commentRepository.save(comment);
        return "评论成功";
    }

    @GetMapping("/{tweetId}/comments")
    public List<Comment> getComments(@PathVariable Long tweetId) {
        return commentRepository.findByTweetIdOrderByCreateTimeDesc(tweetId);
    }

    @GetMapping("/comments/{commentId}/status")
    public Map<String, Object> getCommentStatus(@PathVariable Long commentId, @RequestParam Long userId) {
        long likes = commentLikeRepository.countByCommentIdAndIsLikeTrue(commentId);
        long dislikes = commentLikeRepository.countByCommentIdAndIsLikeFalse(commentId);
        int myAction = 0;
        var action = commentLikeRepository.findByUserIdAndCommentId(userId, commentId);
        if (action.isPresent()) {
            myAction = action.get().getIsLike() ? 1 : 2;
        }
        return Map.of("likes", likes, "dislikes", dislikes, "myAction", myAction);
    }

    @PostMapping("/comments/{commentId}/like")
    public String likeComment(@PathVariable Long commentId, @RequestParam Long userId, @RequestParam Boolean isLike) {
        return commentService.toggleCommentLike(userId, commentId, isLike);
    }

    @PostMapping("/{tweetId}/like")
    public String toggleLike(@PathVariable Long tweetId, @RequestParam Long userId) {
        if (tweetLikeRepository.existsByUserIdAndTweetId(userId, tweetId)) {
            TweetLike like = tweetLikeRepository.findByUserIdAndTweetId(userId, tweetId).get();
            tweetLikeRepository.delete(like);
            return "取消点赞";
        } else {
            Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();
            User user = userRepository.findById(userId).orElseThrow();
            TweetLike like = new TweetLike();
            like.setTweet(tweet);
            like.setUser(user);
            tweetLikeRepository.save(like);

            if (!tweet.getAuthor().getId().equals(userId)) {
                notificationService.send(tweet.getAuthor(), user, 3, tweetId, tweetId);
            }
            return "点赞成功";
        }
    }

    @GetMapping("/{tweetId}/like-status")
    public Map<String, Object> getLikeStatus(@PathVariable Long tweetId, @RequestParam Long userId) {
        long count = tweetLikeRepository.countByTweetId(tweetId);
        boolean isLiked = tweetLikeRepository.existsByUserIdAndTweetId(userId, tweetId);
        return Map.of("count", count, "isLiked", isLiked);
    }

    @Transactional // 🔥 关键：添加事务注解，保证删除过程要么全成功，要么回滚
    @DeleteMapping("/{tweetId}")
    public String deleteTweet(@PathVariable Long tweetId, @RequestParam Long userId) {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("推文不存在"));

        // 验证权限
        if (!tweet.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("无权删除他人的推文");
        }

        // === 1. 删除该推文下所有评论的相关数据 ===
        List<Comment> comments = commentRepository.findByTweetIdOrderByCreateTimeDesc(tweetId);
        for (Comment c : comments) {
            // 先删评论的点赞/倒赞
            List<CommentLike> cLikes = commentLikeRepository.findByCommentId(c.getId());
            commentLikeRepository.deleteAll(cLikes);
        }
        // 再删评论本身
        commentRepository.deleteAll(comments);

        // === 2. 删除该推文的所有点赞（包括其他用户的）===
        List<TweetLike> likes = tweetLikeRepository.findByTweetId(tweetId);
        tweetLikeRepository.deleteAll(likes);

        // === 3. 删除该推文的所有评分 ===
        // 确保你注入了 ratingRepository
        List<TweetRating> ratings = ratingRepository.findByTweetId(tweetId);
        ratingRepository.deleteAll(ratings);

        // === 4. 最后删除推文 ===
        tweetRepository.delete(tweet);

        return "删除成功";
    }
}