package com.example.backend.service;

import com.example.backend.entity.Tweet;
import com.example.backend.entity.TweetFavorite;
import com.example.backend.entity.User;
import com.example.backend.repository.TweetFavoriteRepository;
import com.example.backend.repository.TweetRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TweetService {

    @Autowired private TweetRepository tweetRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TweetFavoriteRepository tweetFavoriteRepository;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private NotificationService notificationService;
    @Autowired private MeiliSearchService meiliSearchService;

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Caching(evict = {
            @CacheEvict(cacheNames = "tweets:list", allEntries = true),
            @CacheEvict(cacheNames = "tweets:detail", allEntries = true),
            @CacheEvict(cacheNames = "users:stats", key = "#userId"),
            @CacheEvict(cacheNames = "users:archive", key = "#userId"),
            @CacheEvict(cacheNames = "users:interest", key = "#userId")
    })
    public Tweet postTweetWithFile(String title,
                                   String content,
                                   Long userId,
                                   String tags,
                                   String authors,
                                   String institution,
                                   String researchArea,
                                   String keywords,
                                   String contentType,
                                   String publicationType,
                                   String status,
                                   String journalOrConference,
                                   String publicationDate,
                                   String doi,
                                   String language,
                                   String visibility,
                                   String referencesText,
                                   String projectLinks,
                                   MultipartFile file) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        Tweet tweet = new Tweet();
        tweet.setTitle(title);
        tweet.setContent(content);
        tweet.setAuthor(user);
        tweet.setTags(tags);
        tweet.setAuthors(authors);
        tweet.setInstitution(institution);
        tweet.setResearchArea(researchArea);
        tweet.setKeywords(keywords);
        tweet.setContentType(contentType);
        tweet.setPublicationType(publicationType);
        tweet.setStatus(status);
        tweet.setJournalOrConference(journalOrConference);
        tweet.setPublicationDate(publicationDate);
        tweet.setDoi(doi);
        tweet.setLanguage(language);
        tweet.setVisibility(visibility);
        tweet.setReferencesText(referencesText);
        tweet.setProjectLinks(projectLinks);
        LocalDateTime now = LocalDateTime.now();
        tweet.setCreateTime(now);
        tweet.setUpdateTime(now);
        tweet.setLastInteractionTime(now);
        tweet.setDownloadCount(0L);
        tweet.setViewCount(0L);
        tweet.setShareCount(0L);
        tweet.setBookmarkCount(0L);

        if (file != null && !file.isEmpty()) {
            applyUploadedFile(tweet, file);
        }
        Tweet savedTweet = tweetRepository.save(tweet);
        meiliSearchService.indexTweet(savedTweet);
        notifyResearchAreaSubscribers(savedTweet);
        return savedTweet;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "tweets:list", allEntries = true),
            @CacheEvict(cacheNames = "tweets:detail", key = "#tweetId"),
            @CacheEvict(cacheNames = "users:archive", key = "#userId"),
            @CacheEvict(cacheNames = "users:interest", key = "#userId")
    })
    public Tweet updateTweetWithFile(Long tweetId,
                                     Long userId,
                                     String title,
                                     String content,
                                     String tags,
                                     String authors,
                                     String institution,
                                     String researchArea,
                                     String keywords,
                                     String contentType,
                                     String publicationType,
                                     String status,
                                     String journalOrConference,
                                     String publicationDate,
                                     String doi,
                                     String language,
                                     String visibility,
                                     String referencesText,
                                     String projectLinks,
                                     MultipartFile file) throws IOException {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("研究成果不存在"));

        if (tweet.getAuthor() == null || !tweet.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("无权编辑他人的研究成果");
        }

        tweet.setTitle(title);
        tweet.setContent(content);
        tweet.setTags(tags);
        tweet.setAuthors(authors);
        tweet.setInstitution(institution);
        tweet.setResearchArea(researchArea);
        tweet.setKeywords(keywords);
        tweet.setContentType(contentType);
        tweet.setPublicationType(publicationType);
        tweet.setStatus(status);
        tweet.setJournalOrConference(journalOrConference);
        tweet.setPublicationDate(publicationDate);
        tweet.setDoi(doi);
        tweet.setLanguage(language);
        tweet.setVisibility(visibility);
        tweet.setReferencesText(referencesText);
        tweet.setProjectLinks(projectLinks);
        tweet.setUpdateTime(LocalDateTime.now());

        if (file != null && !file.isEmpty()) {
            deleteExistingFile(tweet);
            applyUploadedFile(tweet, file);
        }

        Tweet savedTweet = tweetRepository.save(tweet);
        meiliSearchService.indexTweet(savedTweet);
        return savedTweet;
    }

    @Cacheable(
            cacheNames = "tweets:list",
            key = "'all:' + (#sort == null ? 'latest' : #sort.trim().toLowerCase()) + ':' + (#limit == null ? 50 : #limit)",
            sync = true
    )
    public List<Map<String, Object>> getAllTweetDtos(String sort, Integer limit, RatingService ratingService,
                                                     com.example.backend.repository.TweetLikeRepository tweetLikeRepository,
                                                     com.example.backend.repository.CommentRepository commentRepository) {
        return tweetRepository.findDiscoveryDtos(
                null,
                null,
                null,
                null,
                null,
                null,
                sort == null ? "latest" : sort.trim().toLowerCase(),
                limit == null ? 50 : limit,
                ratingService,
                tweetLikeRepository,
                commentRepository
        );
    }

    @Cacheable(
            cacheNames = "tweets:list",
            key = "'discovery:' + (#sort == null ? 'hot' : #sort.trim().toLowerCase()) + ':' + (#limit == null ? 8 : #limit)",
            sync = true
    )
    public List<Map<String, Object>> getDiscoveryFeedDtos(String sort, Integer limit, RatingService ratingService,
                                                          com.example.backend.repository.TweetLikeRepository tweetLikeRepository,
                                                          com.example.backend.repository.CommentRepository commentRepository) {
        return tweetRepository.findDiscoveryDtos(
                null,
                null,
                null,
                null,
                null,
                null,
                sort == null ? "hot" : sort.trim().toLowerCase(),
                limit == null ? 8 : limit,
                ratingService,
                tweetLikeRepository,
                commentRepository
        );
    }

    public Tweet getTweetByIdCached(Long tweetId) {
        return tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("推文不存在"));
    }

    public List<Tweet> getAllTweets() {
        return tweetRepository.findAllByOrderByCreateTimeDesc();
    }

    public List<Tweet> searchTweets(String keyword) {
        return tweetRepository.searchByKeyword(keyword);
    }

    @CacheEvict(cacheNames = "tweets:detail", key = "#tweetId")
    public Tweet recordView(Long tweetId) {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("研究成果不存在"));
        tweet.setViewCount(safeIncrement(tweet.getViewCount()));
        touchInteraction(tweet);
        return tweetRepository.save(tweet);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "tweets:list", allEntries = true),
            @CacheEvict(cacheNames = "tweets:detail", key = "#tweetId")
    })
    public Tweet recordShare(Long tweetId) {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("研究成果不存在"));
        tweet.setShareCount(safeIncrement(tweet.getShareCount()));
        touchInteraction(tweet);
        return tweetRepository.save(tweet);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "tweets:list", allEntries = true),
            @CacheEvict(cacheNames = "tweets:detail", key = "#tweetId"),
            @CacheEvict(cacheNames = "tweets:favorites", key = "#userId"),
            @CacheEvict(cacheNames = "tweets:bookmark-status", key = "#userId + ':' + #tweetId"),
            @CacheEvict(cacheNames = "users:interest", key = "#userId")
    })
    public Map<String, Object> toggleFavorite(Long userId, Long tweetId) {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("研究成果不存在"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        TweetFavorite existing = tweetFavoriteRepository.findByUserIdAndTweetId(userId, tweetId).orElse(null);
        boolean isFavorited;
        if (existing == null) {
            TweetFavorite favorite = new TweetFavorite();
            favorite.setUser(user);
            favorite.setTweet(tweet);
            favorite.setCreateTime(LocalDateTime.now());
            tweetFavoriteRepository.save(favorite);
            tweet.setBookmarkCount(tweetFavoriteRepository.countByTweetId(tweetId));
            isFavorited = true;
        } else {
            tweetFavoriteRepository.delete(existing);
            tweet.setBookmarkCount(tweetFavoriteRepository.countByTweetId(tweetId));
            isFavorited = false;
        }

        touchInteraction(tweet);
        tweetRepository.save(tweet);
        return buildFavoriteState(tweet, userId, isFavorited);
    }

    @Cacheable(cacheNames = "tweets:bookmark-status", key = "#userId + ':' + #tweetId")
    public Map<String, Object> getFavoriteStatus(Long userId, Long tweetId) {
        Tweet tweet = tweetRepository.findById(tweetId)
                .orElseThrow(() -> new RuntimeException("研究成果不存在"));
        boolean isFavorited = tweetFavoriteRepository.existsByUserIdAndTweetId(userId, tweetId);
        syncBookmarkCount(tweet);
        return buildFavoriteState(tweet, userId, isFavorited);
    }

    @Cacheable(cacheNames = "tweets:favorites", key = "#userId")
    public List<Map<String, Object>> getUserFavoriteDtos(Long userId) {
        return tweetFavoriteRepository.findByUserIdOrderByCreateTimeDesc(userId).stream()
                .map(favorite -> {
                    Tweet tweet = favorite.getTweet();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("favoriteId", favorite.getId());
                    item.put("favoriteCreateTime", favorite.getCreateTime());
                    item.put("id", tweet.getId());
                    item.put("title", tweet.getTitle());
                    item.put("content", tweet.getContent());
                    item.put("researchArea", tweet.getResearchArea());
                    item.put("publicationType", tweet.getPublicationType());
                    item.put("authors", tweet.getAuthors());
                    item.put("createTime", tweet.getCreateTime());
                    item.put("updateTime", tweet.getUpdateTime());
                    return item;
                })
                .toList();
    }

    public Tweet touchInteraction(Tweet tweet) {
        tweet.setLastInteractionTime(LocalDateTime.now());
        return tweet;
    }

    @CacheEvict(cacheNames = "tweets:list", allEntries = true)
    public void rebuildSearchIndex() {
        meiliSearchService.indexAll(tweetRepository.findAll());
    }

    private long safeIncrement(Long value) {
        return value == null ? 1L : value + 1L;
    }

    private void syncBookmarkCount(Tweet tweet) {
        long count = tweetFavoriteRepository.countByTweetId(tweet.getId());
        if (tweet.getBookmarkCount() == null || tweet.getBookmarkCount() != count) {
            tweet.setBookmarkCount(count);
            tweetRepository.save(tweet);
        }
    }

    private Map<String, Object> buildFavoriteState(Tweet tweet, Long userId, boolean isFavorited) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tweetId", tweet.getId());
        result.put("userId", userId);
        result.put("count", tweet.getBookmarkCount() == null ? 0L : tweet.getBookmarkCount());
        result.put("isFavorited", isFavorited);
        return result;
    }

    private void applyUploadedFile(Tweet tweet, MultipartFile file) throws IOException {
        String uuid = UUID.randomUUID().toString();
        String newFileName = uuid + "_" + file.getOriginalFilename();

        File destFile = new File(uploadDir + newFileName);
        if (!destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
        }
        file.transferTo(destFile);

        tweet.setFilePath(destFile.getAbsolutePath());
        tweet.setOriginalFilename(file.getOriginalFilename());
    }

    private void deleteExistingFile(Tweet tweet) {
        if (tweet.getFilePath() == null || tweet.getFilePath().isBlank()) {
            return;
        }
        try {
            File oldFile = new File(tweet.getFilePath());
            if (oldFile.exists()) {
                oldFile.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyResearchAreaSubscribers(Tweet tweet) {
        if (tweet == null || tweet.getAuthor() == null) {
            return;
        }

        List<User> subscribers = subscriptionService.findSubscribersByResearchArea(tweet.getResearchArea());
        for (User subscriber : subscribers) {
            if (subscriber == null || subscriber.getId() == null) {
                continue;
            }
            notificationService.sendSubscriptionNotification(subscriber, tweet.getAuthor(), tweet.getId());
        }
    }
}
