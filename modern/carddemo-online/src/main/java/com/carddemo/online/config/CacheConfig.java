package com.carddemo.online.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Caching is a no-op by default; the {@code redis} profile backs read-heavy views with Memorystore. */
@Configuration
@EnableCaching
public class CacheConfig {
}
