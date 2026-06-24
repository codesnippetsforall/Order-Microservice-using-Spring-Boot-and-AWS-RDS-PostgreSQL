package com.winsoon.orderms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * In-memory cache for local development and unit tests when Redis is disabled.
 */
@Configuration
@ConditionalOnMissingBean(RedisConnectionFactory.class)
public class SimpleCacheConfig {

    @Bean
    ConcurrentMapCacheManager cacheManager() {
        return new ConcurrentMapCacheManager(CacheNames.ORDERS, CacheNames.CUSTOMERS);
    }
}
