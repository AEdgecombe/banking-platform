package com.example.config;

import com.example.model.Account;
import com.example.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

/**
 * Caching sits outside the transaction (higher precedence than @Transactional, which defaults
 * to LOWEST_PRECEDENCE). That way a cache eviction happens after the commit, so a reader
 * cannot re-cache the old balance between the eviction and the commit.
 */
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer accountsCache(ObjectMapper objectMapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Account.class);
        return builder -> builder.withCacheConfiguration(
                AccountService.ACCOUNTS_CACHE,
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .serializeValuesWith(SerializationPair.fromSerializer(serializer)));
    }
}
