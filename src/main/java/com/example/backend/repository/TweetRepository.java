package com.example.backend.repository;

import com.example.backend.entity.Tweet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public interface TweetRepository extends JpaRepository<Tweet, Long> {

    List<Tweet> findAllByOrderByCreateTimeDesc();

    long countByAuthorId(Long userId);

    List<Tweet> findByAuthorIdOrderByCreateTimeDesc(Long userId);

    List<Tweet> findByResearchAreaIgnoreCaseAndCreateTimeAfterOrderByCreateTimeDesc(String researchArea, LocalDateTime createTime);

    @Query("""
            SELECT t FROM Tweet t
            WHERE t.title LIKE %:keyword%
               OR t.content LIKE %:keyword%
               OR t.tags LIKE %:keyword%
               OR t.authors LIKE %:keyword%
               OR t.keywords LIKE %:keyword%
               OR t.researchArea LIKE %:keyword%
               OR t.institution LIKE %:keyword%
               OR t.journalOrConference LIKE %:keyword%
               OR t.doi LIKE %:keyword%
            ORDER BY t.createTime DESC
            """)
    List<Tweet> searchByKeyword(@Param("keyword") String keyword);

    @Query("""
            SELECT t FROM Tweet t
            WHERE (
                    :keyword IS NULL OR :keyword = ''
                    OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.tags) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.authors) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.keywords) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.researchArea) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.institution) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.journalOrConference) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(t.doi) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
              AND (:researchArea IS NULL OR :researchArea = '' OR t.researchArea = :researchArea)
              AND (:contentType IS NULL OR :contentType = '' OR t.contentType = :contentType)
              AND (:publicationType IS NULL OR :publicationType = '' OR t.publicationType = :publicationType)
              AND (:status IS NULL OR :status = '' OR t.status = :status)
              AND (:language IS NULL OR :language = '' OR t.language = :language)
            ORDER BY t.createTime DESC
            """)
    List<Tweet> searchByAdvancedFilters(
            @Param("keyword") String keyword,
            @Param("researchArea") String researchArea,
            @Param("contentType") String contentType,
            @Param("publicationType") String publicationType,
            @Param("status") String status,
            @Param("language") String language
    );

    @Query("""
            SELECT t FROM Tweet t
            WHERE t.id <> :tweetId
              AND (
                    (:researchArea IS NOT NULL AND :researchArea <> '' AND t.researchArea = :researchArea)
                 OR (:contentType IS NOT NULL AND :contentType <> '' AND t.contentType = :contentType)
                 OR (:keywords IS NOT NULL AND :keywords <> '' AND t.keywords LIKE CONCAT('%', :keywords, '%'))
                 OR (:tags IS NOT NULL AND :tags <> '' AND t.tags LIKE CONCAT('%', :tags, '%'))
              )
            ORDER BY t.createTime DESC
            """)
    List<Tweet> findRelatedTweets(
            @Param("tweetId") Long tweetId,
            @Param("researchArea") String researchArea,
            @Param("contentType") String contentType,
            @Param("keywords") String keywords,
            @Param("tags") String tags,
            Pageable pageable
    );

    default List<Map<String, Object>> findDiscoveryDtos(
            String keyword,
            String researchArea,
            String contentType,
            String publicationType,
            String status,
            String language,
            String sortBy,
            int limit,
            com.example.backend.service.RatingService ratingService,
            TweetLikeRepository tweetLikeRepository,
            CommentRepository commentRepository
    ) {
        List<String> keywordVariants = expandKeywordVariants(keyword);
        List<Tweet> tweets = keywordVariants.isEmpty()
                ? searchByAdvancedFilters(keyword, researchArea, contentType, publicationType, status, language)
                : keywordVariants.stream()
                .flatMap(variant -> searchByAdvancedFilters(variant, researchArea, contentType, publicationType, status, language).stream())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        return tweets.stream()
                .map(tweet -> {
                    long likeCount = tweetLikeRepository.countByTweetId(tweet.getId());
                    long commentCount = commentRepository.countByTweetId(tweet.getId());
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("tweet", tweet);
                    dto.put("signals", ratingService.getEvaluationSignals(tweet, likeCount, commentCount));
                    return dto;
                })
                .sorted(resolveComparator(sortBy))
                .limit(Math.max(limit, 1))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> expandKeywordVariants(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalized);

        String lower = normalized.toLowerCase(Locale.ROOT);
        Map<String, List<String>> synonymMap = buildSearchSynonymMap();

        synonymMap.forEach((key, values) -> {
            if (lower.contains(key)) {
                variants.addAll(values);
            }
            for (String value : values) {
                if (lower.contains(value.toLowerCase(Locale.ROOT))) {
                    variants.add(key);
                    variants.addAll(values);
                }
            }
        });

        return new ArrayList<>(variants);
    }

    private static Map<String, List<String>> buildSearchSynonymMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("ai", List.of("artificial intelligence", "人工智能", "machine intelligence"));
        map.put("artificial intelligence", List.of("ai", "人工智能", "machine intelligence"));
        map.put("ml", List.of("machine learning", "机器学习"));
        map.put("machine learning", List.of("ml", "机器学习"));
        map.put("nlp", List.of("natural language processing", "自然语言处理"));
        map.put("natural language processing", List.of("nlp", "自然语言处理"));
        map.put("cv", List.of("computer vision", "计算机视觉"));
        map.put("computer vision", List.of("cv", "计算机视觉"));
        return map;
    }

    private static Comparator<Map<String, Object>> resolveComparator(String sortBy) {
        String normalized = sortBy == null ? "latest" : sortBy.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "hot" -> Comparator.comparingLong(item -> -readLong(item, "signals", "hotScore"));
            case "toprated", "top_rated", "rating" -> Comparator
                    .comparingDouble((Map<String, Object> item) -> -readDouble(item, "signals", "averageScore"))
                    .thenComparingLong(item -> -readLong(item, "signals", "ratingCount"))
                    .thenComparing(item -> readTime(item, "tweet", "createTime"), Comparator.reverseOrder());
            case "mostdiscussed", "discussed", "comments" -> Comparator
                    .comparingLong((Map<String, Object> item) -> -readLong(item, "signals", "comments"))
                    .thenComparingLong(item -> -readLong(item, "signals", "hotScore"));
            case "latest", "newest" -> Comparator.comparing(
                    item -> readTime(item, "tweet", "updateTime") != null ? readTime(item, "tweet", "updateTime") : readTime(item, "tweet", "createTime"),
                    Comparator.reverseOrder()
            );
            default -> Comparator.comparingLong(item -> -readLong(item, "signals", "hotScore"));
        };
    }

    @SuppressWarnings("unchecked")
    private static long readLong(Map<String, Object> wrapper, String wrapperKey, String key) {
        Object nested = wrapper.get(wrapperKey);
        if (nested instanceof Map<?, ?> nestedMap) {
            Object value = nestedMap.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static double readDouble(Map<String, Object> wrapper, String wrapperKey, String key) {
        Object nested = wrapper.get(wrapperKey);
        if (nested instanceof Map<?, ?> nestedMap) {
            Object value = nestedMap.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return 0.0;
    }

    private static java.time.LocalDateTime readTime(Map<String, Object> wrapper, String wrapperKey, String key) {
        Object nested = wrapper.get(wrapperKey);
        if (nested instanceof Tweet tweet) {
            return "updateTime".equals(key) ? tweet.getUpdateTime() : tweet.getCreateTime();
        }
        return null;
    }
}
