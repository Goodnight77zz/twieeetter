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
import com.example.backend.repository.CommentLikeRepository;
import com.example.backend.repository.TweetRatingRepository;
import com.example.backend.service.TweetService;
import com.example.backend.service.RatingService;
import com.example.backend.service.CommentService;
import com.example.backend.service.MeiliSearchService;
import com.example.backend.service.NotificationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tweets")
public class TweetController {

    private static final Logger log = LoggerFactory.getLogger(TweetController.class);

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
    @Autowired private MeiliSearchService meiliSearchService;

    @PostMapping
    public String postTweet(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "authors", required = false) String authors,
            @RequestParam(value = "institution", required = false) String institution,
            @RequestParam(value = "researchArea", required = false) String researchArea,
            @RequestParam(value = "keywords", required = false) String keywords,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "publicationType", required = false) String publicationType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "journalOrConference", required = false) String journalOrConference,
            @RequestParam(value = "publicationDate", required = false) String publicationDate,
            @RequestParam(value = "doi", required = false) String doi,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "referencesText", required = false) String referencesText,
            @RequestParam(value = "projectLinks", required = false) String projectLinks,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        try {
            tweetService.postTweetWithFile(
                    title,
                    content,
                    userId,
                    tags,
                    authors,
                    institution,
                    researchArea,
                    keywords,
                    contentType,
                    publicationType,
                    status,
                    journalOrConference,
                    publicationDate,
                    doi,
                    language,
                    visibility,
                    referencesText,
                    projectLinks,
                    file
            );
            return "发布成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "发布失败: " + e.getMessage();
        }
    }

    @PostMapping("/{tweetId}/edit")
    public String updateTweet(
            @PathVariable Long tweetId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "authors", required = false) String authors,
            @RequestParam(value = "institution", required = false) String institution,
            @RequestParam(value = "researchArea", required = false) String researchArea,
            @RequestParam(value = "keywords", required = false) String keywords,
            @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "publicationType", required = false) String publicationType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "journalOrConference", required = false) String journalOrConference,
            @RequestParam(value = "publicationDate", required = false) String publicationDate,
            @RequestParam(value = "doi", required = false) String doi,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "referencesText", required = false) String referencesText,
            @RequestParam(value = "projectLinks", required = false) String projectLinks,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        try {
            tweetService.updateTweetWithFile(
                    tweetId,
                    userId,
                    title,
                    content,
                    tags,
                    authors,
                    institution,
                    researchArea,
                    keywords,
                    contentType,
                    publicationType,
                    status,
                    journalOrConference,
                    publicationDate,
                    doi,
                    language,
                    visibility,
                    referencesText,
                    projectLinks,
                    file
            );
            return "更新成功";
        } catch (Exception e) {
            e.printStackTrace();
            return "更新失败: " + e.getMessage();
        }
    }

    @GetMapping("/{tweetId}/comment-count")
    public long getCommentCount(@PathVariable Long tweetId) {
        return commentRepository.countByTweetId(tweetId);
    }

    @GetMapping("/search")
    public List<Map<String, Object>> searchTweets(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) String researchArea,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String publicationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String language,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        String normalizedKeyword = normalizeNullableText(keyword);
        String normalizedResearchArea = normalizeNullableText(researchArea);
        String normalizedContentType = normalizeNullableText(contentType);
        String normalizedPublicationType = normalizeNullableText(publicationType);
        String normalizedStatus = normalizeNullableText(status);
        String normalizedLanguage = normalizeNullableText(language);
        String normalizedSort = normalizeSort(sort);
        int safeLimit = limit == null || limit < 1 ? 50 : limit;

        if (meiliSearchService.isEnabled()) {
            try {
                List<Long> ids = meiliSearchService.searchTweetIds(
                        normalizedKeyword,
                        normalizedResearchArea,
                        normalizedContentType,
                        normalizedPublicationType,
                        normalizedStatus,
                        normalizedLanguage,
                        safeLimit
                );
                if (!ids.isEmpty()) {
                    log.info("Search path=MEILISEARCH keyword='{}' ids={} limit={}", normalizedKeyword, ids, safeLimit);
                    Map<Long, Tweet> tweetMap = tweetRepository.findAllById(ids).stream()
                            .collect(Collectors.toMap(Tweet::getId, tweet -> tweet));
                    return ids.stream()
                            .map(tweetMap::get)
                            .filter(java.util.Objects::nonNull)
                            .map(this::toSearchDto)
                            .sorted(buildSearchComparator(normalizedSort))
                            .limit(safeLimit)
                            .toList();
                }
                log.info("Search path=DB_FALLBACK reason=meilisearch_empty keyword='{}' limit={}", normalizedKeyword, safeLimit);
            } catch (Exception e) {
                log.warn("Search path=DB_FALLBACK reason=meilisearch_exception keyword='{}' limit={} message={}", normalizedKeyword, safeLimit, e.getMessage());
            }
        } else {
            log.info("Search path=DB_FALLBACK reason=meilisearch_disabled keyword='{}' limit={}", normalizedKeyword, safeLimit);
        }

        return tweetRepository.findDiscoveryDtos(
                normalizedKeyword,
                normalizedResearchArea,
                normalizedContentType,
                normalizedPublicationType,
                normalizedStatus,
                normalizedLanguage,
                normalizedSort,
                safeLimit,
                ratingService,
                tweetLikeRepository,
                commentRepository
        );
    }

    @PostMapping("/reindex")
    public Map<String, Object> rebuildSearchIndex() {
        tweetService.rebuildSearchIndex();
        return Map.of(
                "success", true,
                "message", "索引重建任务已触发"
        );
    }

    @GetMapping("/discovery")
    public List<Map<String, Object>> getDiscoveryFeed(
            @RequestParam(required = false, defaultValue = "hot") String sort,
            @RequestParam(required = false, defaultValue = "8") Integer limit
    ) {
        return tweetService.getDiscoveryFeedDtos(
                normalizeText(sort),
                limit == null ? 8 : limit,
                ratingService,
                tweetLikeRepository,
                commentRepository
        );
    }

    @GetMapping
    public List<Map<String, Object>> getAllTweets(
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        return tweetService.getAllTweetDtos(
                normalizeText(sort),
                limit == null ? 50 : limit,
                ratingService,
                tweetLikeRepository,
                commentRepository
        );
    }

    @Cacheable(cacheNames = "tweets:list", key = "'user:' + #userId")
    @GetMapping("/user/{userId}")
    public List<Tweet> getUserTweets(@PathVariable Long userId) {
        return tweetRepository.findByAuthorIdOrderByCreateTimeDesc(userId);
    }

    @GetMapping("/{id}")
    public Tweet getTweetById(@PathVariable Long id) {
        return tweetService.getTweetByIdCached(id);
    }

    @GetMapping("/{id}/detail-dto")
    public Map<String, Object> getTweetDetailWithScore(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        Tweet tweet = tweetService.recordView(id);

        long likeCount = tweetLikeRepository.countByTweetId(id);
        long commentCount = commentRepository.countByTweetId(id);
        Map<String, Object> signals = ratingService.getEvaluationSignals(tweet, likeCount, commentCount);
        String scoreStr = String.valueOf(signals.get("averageScoreText"));

        TweetRating myRating = null;
        if (userId != null) {
            myRating = ratingRepository.findByUserIdAndTweetId(userId, id).orElse(null);
        }

        return Map.of(
                "tweet", tweet,
                "avgScore", scoreStr,
                "radar", signals.get("radar"),
                "signals", signals,
                "myRating", myRating != null ? myRating : "null"
        );
    }

    @GetMapping("/{id}/signals")
    public Map<String, Object> getTweetEvaluationSignals(@PathVariable Long id) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(() -> new RuntimeException("推文不存在"));
        long likeCount = tweetLikeRepository.countByTweetId(id);
        long commentCount = commentRepository.countByTweetId(id);
        return ratingService.getEvaluationSignals(tweet, likeCount, commentCount);
    }

    @PostMapping("/{id}/share")
    public Map<String, Object> recordTweetShare(@PathVariable Long id) {
        Tweet tweet = tweetService.recordShare(id);
        return buildSignalMutationResponse(tweet, "shares", tweet.getShareCount());
    }

    @PostMapping("/{id}/bookmark")
    public Map<String, Object> toggleTweetBookmark(@PathVariable Long id, @RequestParam Long userId) {
        Map<String, Object> favoriteState = tweetService.toggleFavorite(userId, id);
        Tweet tweet = tweetRepository.findById(id).orElseThrow(() -> new RuntimeException("推文不存在"));
        Map<String, Object> response = buildSignalMutationResponse(tweet, "bookmarks", tweet.getBookmarkCount());
        response.putAll(favoriteState);
        return response;
    }

    @GetMapping("/{id}/bookmark-status")
    public Map<String, Object> getTweetBookmarkStatus(@PathVariable Long id, @RequestParam Long userId) {
        return tweetService.getFavoriteStatus(userId, id);
    }

    @GetMapping("/favorites")
    public List<Map<String, Object>> getMyFavorites(@RequestParam Long userId) {
        return tweetService.getUserFavoriteDtos(userId);
    }

    @Cacheable(cacheNames = "tweets:related", key = "#id")
    @GetMapping("/{id}/related")
    public List<Tweet> getRelatedTweets(@PathVariable Long id) {
        Tweet tweet = tweetRepository.findById(id).orElseThrow(() -> new RuntimeException("推文不存在"));

        String keywordSeed = firstNonBlankToken(tweet.getKeywords());
        String tagSeed = firstNonBlankToken(tweet.getTags());

        return tweetRepository.findRelatedTweets(
                id,
                normalizeText(tweet.getResearchArea()),
                normalizeText(tweet.getContentType()),
                keywordSeed,
                tagSeed,
                PageRequest.of(0, 6)
        );
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        try {
            Tweet tweet = tweetRepository.findById(id).orElseThrow(() -> new RuntimeException("文件不存在"));
            if (tweet.getFilePath() == null || tweet.getFilePath().isEmpty()) {
                throw new RuntimeException("该研究未上传附件");
            }
            tweet.setDownloadCount(tweet.getDownloadCount() == null ? 1 : tweet.getDownloadCount() + 1);
            tweetService.touchInteraction(tweet);
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
            @RequestParam Integer s1, @RequestParam Integer s2, @RequestParam Integer s3,
            @RequestParam Integer s4, @RequestParam Integer s5
    ) {
        try {
            ratingService.rateTweet(userId, tweetId, s1, s2, s3, s4, s5);
            return "评分已更新";
        } catch (Exception e) {
            return "评分失败: " + e.getMessage();
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "comments:list", allEntries = true),
            @CacheEvict(cacheNames = "comments:tree", key = "#tweetId")
    })
    @PostMapping("/{tweetId}/comments")
    public String addComment(
            @PathVariable Long tweetId, @RequestParam Long userId, @RequestParam String content,
            @RequestParam(required = false) Long parentId, @RequestParam(required = false) Long replyToUserId,
            @RequestParam(required = false, defaultValue = "discussion") String commentType
    ) {
        Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        Comment comment = new Comment();
        comment.setContent(content);
        comment.setCommentType(commentType == null || commentType.isBlank() ? "discussion" : commentType.trim().toLowerCase());
        comment.setUser(user);
        comment.setTweet(tweet);
        comment.setCreateTime(java.time.LocalDateTime.now());

        if (parentId != null) {
            comment.setParentId(parentId);
            if (replyToUserId != null) {
                User replyToUser = userRepository.findById(replyToUserId).orElse(null);
                comment.setReplyToUser(replyToUser);
                notificationService.send(replyToUser, user, NotificationService.TYPE_COMMENT_REPLY, tweetId, tweetId);
            } else {
                Comment parentComment = commentRepository.findById(parentId).orElseThrow();
                notificationService.send(parentComment.getUser(), user, NotificationService.TYPE_COMMENT_REPLY, tweetId, tweetId);
            }
        } else {
            notificationService.send(tweet.getAuthor(), user, NotificationService.TYPE_TWEET_COMMENT, tweetId, tweetId);
        }

        tweetService.touchInteraction(tweet);
        tweetRepository.save(tweet);
        commentRepository.save(comment);
        return "评论成功";
    }

    @Cacheable(cacheNames = "comments:list", key = "#tweetId + ':' + (#userId == null ? 'anon' : #userId)")
    @GetMapping("/{tweetId}/comments")
    public List<Map<String, Object>> getComments(@PathVariable Long tweetId, @RequestParam(required = false) Long userId) {
        List<Comment> comments = commentRepository.findByTweetIdOrderByCreateTimeDesc(tweetId);
        Map<Long, Comment> commentMap = comments.stream().collect(Collectors.toMap(Comment::getId, item -> item));
        Long tweetAuthorId = tweetRepository.findById(tweetId)
                .map(tweet -> tweet.getAuthor() != null ? tweet.getAuthor().getId() : null)
                .orElse(null);
        return comments.stream().map(comment -> {
            long likeCount = commentLikeRepository.countByCommentIdAndIsLikeTrue(comment.getId());
            boolean liked = userId != null && commentLikeRepository.findByUserIdAndCommentId(userId, comment.getId())
                    .map(CommentLike::getIsLike)
                    .orElse(false);
            Comment parentComment = comment.getParentId() != null ? commentMap.get(comment.getParentId()) : null;
            User commentUser = comment.getUser();
            int reputation = commentUser != null && commentUser.getReputation() != null ? commentUser.getReputation() : 0;
            String academicTitle = commentUser != null ? commentUser.getAcademicTitle() : "Research Participant";
            double userWeight = commentUser != null ? commentUser.getWeight() : 1.0;
            boolean topScholar = commentUser != null && reputation >= 500;
            boolean acceptedByAuthor = Boolean.TRUE.equals(comment.getAcceptedByAuthor());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", comment.getId());
            item.put("content", comment.getContent());
            item.put("commentType", comment.getCommentType());
            item.put("createTime", comment.getCreateTime());
            item.put("parentId", comment.getParentId());
            item.put("parentCommentType", parentComment != null ? parentComment.getCommentType() : null);
            item.put("user", commentUser);
            item.put("replyToUser", comment.getReplyToUser());
            item.put("likeCount", likeCount);
            item.put("isLiked", liked);
            item.put("isFeatured", likeCount >= 3 || acceptedByAuthor);
            item.put("isAuthorReply", tweetAuthorId != null && commentUser != null && tweetAuthorId.equals(commentUser.getId()));
            item.put("academicTitle", academicTitle);
            item.put("reputation", reputation);
            item.put("userWeight", userWeight);
            item.put("isTopScholar", topScholar);
            item.put("isAcceptedByAuthor", acceptedByAuthor);
            item.put("canAccept", userId != null && tweetAuthorId != null && tweetAuthorId.equals(userId) && commentUser != null && !tweetAuthorId.equals(commentUser.getId()));
            return item;
        }).toList();
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "comments:list", allEntries = true),
            @CacheEvict(cacheNames = "comments:tree", key = "#result['tweetId']", condition = "#result != null && #result.containsKey('tweetId')")
    })
    @PostMapping("/comments/{commentId}/accept")
    public Map<String, Object> toggleCommentAccepted(@PathVariable Long commentId, @RequestParam Long userId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new RuntimeException("评论不存在"));
        Tweet tweet = comment.getTweet();
        if (tweet == null || tweet.getAuthor() == null || tweet.getAuthor().getId() == null) {
            throw new RuntimeException("文章作者不存在");
        }
        if (!tweet.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("只有作者可以采纳讨论");
        }
        boolean nextAccepted = !Boolean.TRUE.equals(comment.getAcceptedByAuthor());
        comment.setAcceptedByAuthor(nextAccepted);
        commentRepository.save(comment);
        if (nextAccepted && comment.getUser() != null) {
            Integer currentReputation = comment.getUser().getReputation() == null ? 0 : comment.getUser().getReputation();
            comment.getUser().setReputation(currentReputation + 20);
            userRepository.save(comment.getUser());
            notificationService.send(comment.getUser(), tweet.getAuthor(), NotificationService.TYPE_COMMENT_ACCEPTED, comment.getId(), tweet.getId());
        }
        return Map.of(
                "accepted", nextAccepted,
                "commentId", comment.getId(),
                "tweetId", tweet.getId()
        );
    }


    @GetMapping("/comments/{commentId}/status")
    public Map<String, Object> getCommentStatus(@PathVariable Long commentId, @RequestParam Long userId) {
        long likes = commentLikeRepository.countByCommentIdAndIsLikeTrue(commentId);
        boolean liked = commentLikeRepository.findByUserIdAndCommentId(userId, commentId)
                .map(CommentLike::getIsLike)
                .orElse(false);
        return Map.of("count", likes, "isLiked", liked);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "tweets:list", allEntries = true),
            @CacheEvict(cacheNames = "tweets:detail", key = "#tweetId"),
            @CacheEvict(cacheNames = "tweets:related", allEntries = true)
    })
    @PostMapping("/{tweetId}/like")
    public Map<String, Object> toggleLike(@PathVariable Long tweetId, @RequestParam Long userId) {
        Tweet tweet = tweetRepository.findById(tweetId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        TweetLike like = tweetLikeRepository.findByUserIdAndTweetId(userId, tweetId).orElse(null);
        boolean isLiked;
        if (like == null) {
            like = new TweetLike();
            like.setTweet(tweet);
            like.setUser(user);
            tweetLikeRepository.save(like);
            notificationService.send(tweet.getAuthor(), user, NotificationService.TYPE_TWEET_LIKE, tweetId, tweetId);
            isLiked = true;
        } else {
            tweetLikeRepository.delete(like);
            isLiked = false;
        }

        tweetService.touchInteraction(tweet);
        tweetRepository.save(tweet);
        return Map.of(
                "count", tweetLikeRepository.countByTweetId(tweetId),
                "isLiked", isLiked
        );
    }

    @GetMapping("/{tweetId}/like-status")
    public Map<String, Object> getLikeStatus(@PathVariable Long tweetId, @RequestParam Long userId) {
        boolean liked = tweetLikeRepository.findByUserIdAndTweetId(userId, tweetId).isPresent();
        long count = tweetLikeRepository.countByTweetId(tweetId);
        return Map.of("count", count, "isLiked", liked);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "comments:list", allEntries = true),
            @CacheEvict(cacheNames = "comments:tree", key = "#result['tweetId']", condition = "#result != null && #result.containsKey('tweetId')")
    })
    @PostMapping("/comments/{commentId}/like")
    public Map<String, Object> toggleCommentLike(@PathVariable Long commentId, @RequestParam Long userId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();

        CommentLike like = commentLikeRepository.findByUserIdAndCommentId(userId, commentId).orElse(null);
        boolean isLiked;
        if (like == null) {
            like = new CommentLike();
            like.setComment(comment);
            like.setUser(user);
            like.setIsLike(true);
            commentLikeRepository.save(like);
            isLiked = true;
        } else {
            if (Boolean.TRUE.equals(like.getIsLike())) {
                commentLikeRepository.delete(like);
                isLiked = false;
            } else {
                like.setIsLike(true);
                commentLikeRepository.save(like);
                isLiked = true;
            }
        }
        return Map.of(
                "count", commentLikeRepository.countByCommentIdAndIsLikeTrue(commentId),
                "isLiked", isLiked,
                "tweetId", comment.getTweet() != null ? comment.getTweet().getId() : null
        );
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "tweets:list", allEntries = true),
            @CacheEvict(cacheNames = "tweets:detail", key = "#id"),
            @CacheEvict(cacheNames = "tweets:related", allEntries = true),
            @CacheEvict(cacheNames = "users:stats", allEntries = true),
            @CacheEvict(cacheNames = "users:archive", allEntries = true),
            @CacheEvict(cacheNames = "users:interest", allEntries = true)
    })
    @DeleteMapping("/{id}")
    public void deleteTweet(@PathVariable Long id) {
        tweetRepository.deleteById(id);
    }

    private Map<String, Object> buildSignalMutationResponse(Tweet tweet, String field, Long value) {
        long likeCount = tweetLikeRepository.countByTweetId(tweet.getId());
        long commentCount = commentRepository.countByTweetId(tweet.getId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(field, value == null ? 0L : value);
        response.put("signals", ratingService.getEvaluationSignals(tweet, likeCount, commentCount));
        return response;
    }

    private String normalizeText(String text) {
        return text == null ? null : text.trim();
    }

    private String normalizeNullableText(String text) {
        String normalized = normalizeText(text);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private String normalizeSort(String sort) {
        String normalized = normalizeNullableText(sort);
        return normalized == null ? "latest" : normalized;
    }

    private Map<String, Object> toSearchDto(Tweet tweet) {
        long likeCount = tweetLikeRepository.countByTweetId(tweet.getId());
        long commentCount = commentRepository.countByTweetId(tweet.getId());
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("tweet", tweet);
        dto.put("signals", ratingService.getEvaluationSignals(tweet, likeCount, commentCount));
        return dto;
    }

    private java.util.Comparator<Map<String, Object>> buildSearchComparator(String sort) {
        String normalized = normalizeSort(sort).toLowerCase();
        return switch (normalized) {
            case "hot" -> java.util.Comparator.comparingLong((Map<String, Object> item) -> readLongSignal(item, "hotScore")).reversed();
            case "toprated", "top_rated" -> java.util.Comparator
                    .comparingDouble((Map<String, Object> item) -> readDoubleSignal(item, "averageScore"))
                    .reversed()
                    .thenComparing(java.util.Comparator.comparingLong((Map<String, Object> item) -> readLongSignal(item, "ratingCount")).reversed());
            case "mostdiscussed", "most_discussed" -> java.util.Comparator
                    .comparingLong((Map<String, Object> item) -> readLongSignal(item, "commentCount"))
                    .reversed();
            default -> java.util.Comparator.comparing(
                    (Map<String, Object> item) -> {
                        Tweet tweet = (Tweet) item.get("tweet");
                        LocalDateTime time = tweet.getUpdateTime() != null ? tweet.getUpdateTime() : tweet.getCreateTime();
                        return time == null ? LocalDateTime.MIN : time;
                    }
            ).reversed();
        };
    }

    @SuppressWarnings("unchecked")
    private long readLongSignal(Map<String, Object> item, String key) {
        Object signalsObj = item.get("signals");
        if (signalsObj instanceof Map<?, ?> signals) {
            Object value = signals.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private double readDoubleSignal(Map<String, Object> item, String key) {
        Object signalsObj = item.get("signals");
        if (signalsObj instanceof Map<?, ?> signals) {
            Object value = signals.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return 0D;
    }

    private String firstNonBlankToken(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(text.split("[,，;；\\s]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .findFirst()
                .orElse(null);
    }
}
