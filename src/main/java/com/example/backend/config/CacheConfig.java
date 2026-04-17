package com.example.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJacksonJsonRedisSerializer jsonSerializer =
                new GenericJacksonJsonRedisSerializer(new ObjectMapper());

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                )
                .entryTtl(Duration.ofMinutes(10));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("tweets:list", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("tweets:detail", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("users:profile", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("comments:list", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("comments:tree", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("notifications:list", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigurations.put("notifications:unread", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigurations.put("users:stats", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("users:archive", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("users:interest", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("users:following", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("users:follow-status", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("subscriptions:my", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("tweets:favorites", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("tweets:bookmark-status", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("tweets:related", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed. cache={}, key={}, fallback to DB. reason={}",
                        cacheName(cache), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed. cache={}, key={}, write ignored. reason={}",
                        cacheName(cache), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed. cache={}, key={}, evict ignored. reason={}",
                        cacheName(cache), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed. cache={}, clear ignored. reason={}",
                        cacheName(cache), exception.getMessage());
            }

            private String cacheName(Cache cache) {
                return cache == null ? "unknown" : cache.getName();
            }
        };
    }

    @Bean
    public SimpleKeyGenerator simpleKeyGenerator() {
        return new SimpleKeyGenerator();
    }
}
