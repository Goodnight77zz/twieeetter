 package com.example.backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
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
    public SimpleKeyGenerator simpleKeyGenerator() {
        return new SimpleKeyGenerator();
    }
}
