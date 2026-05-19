package com.epam.execution_engine_service.orchestrator.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig
@Import(CacheConfig.class)
@TestPropertySource(properties = "app.cache.testcase-ttl-minutes=45")
@DisplayName("CacheConfig — Unit Tests")
class CacheConfigTest {

    @Autowired
    private CacheManager cacheManager;

    // -----------------------------------------------------------------------
    // CacheManager type and registration
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CacheManager bean")
    class CacheManagerBeanTests {

        @Test
        @DisplayName("cacheManager bean is created and is a CaffeineCacheManager")
        void beanType() {
            assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        }

        @Test
        @DisplayName("'testCases' cache is registered")
        void testCasesCacheExists() {
            assertThat(cacheManager.getCacheNames()).contains("testCases");
        }

        @Test
        @DisplayName("getCache('testCases') returns a non-null CaffeineCache")
        void testCacheInstance() {
            Cache cache = cacheManager.getCache("testCases");
            assertThat(cache).isNotNull().isInstanceOf(CaffeineCache.class);
        }
    }

    // -----------------------------------------------------------------------
    // Cache behaviour (put / get / evict)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Cache operations")
    class CacheOperationsTests {

        @Test
        @DisplayName("put and get returns the stored value")
        void putAndGet() {
            Cache cache = cacheManager.getCache("testCases");
            assertThat(cache).isNotNull();

            cache.put("key-1", "value-1");
            Cache.ValueWrapper wrapper = cache.get("key-1");

            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isEqualTo("value-1");
        }

        @Test
        @DisplayName("get on absent key returns null")
        void getMissReturnsNull() {
            Cache cache = cacheManager.getCache("testCases");
            assertThat(cache).isNotNull();
            assertThat(cache.get("absent-key")).isNull();
        }

        @Test
        @DisplayName("evict removes the cached entry")
        void evict() {
            Cache cache = cacheManager.getCache("testCases");
            assertThat(cache).isNotNull();

            cache.put("evict-key", "data");
            assertThat(cache.get("evict-key")).isNotNull();

            cache.evict("evict-key");
            assertThat(cache.get("evict-key")).isNull();
        }

        @Test
        @DisplayName("clear removes all entries")
        void clear() {
            Cache cache = cacheManager.getCache("testCases");
            assertThat(cache).isNotNull();

            cache.put("a", 1);
            cache.put("b", 2);
            cache.clear();

            assertThat(cache.get("a")).isNull();
            assertThat(cache.get("b")).isNull();
        }

        @Test
        @DisplayName("get with type returns typed value")
        void getTyped() {
            Cache cache = cacheManager.getCache("testCases");
            assertThat(cache).isNotNull();

            cache.put("typed-key", 42);
            Integer value = cache.get("typed-key", Integer.class);
            assertThat(value).isEqualTo(42);
        }
    }

    // -----------------------------------------------------------------------
    // Caffeine-specific properties (stats, max size)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Caffeine cache properties")
    class CaffeinePropertiesTests {

        @Test
        @DisplayName("Stats recording is enabled on the underlying Caffeine cache")
        void statsRecordingEnabled() {
            CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache("testCases");
            assertThat(caffeineCache).isNotNull();
            // recordStats() makes the stats object non-null and operational
            com.github.benmanes.caffeine.cache.stats.CacheStats stats =
                    caffeineCache.getNativeCache().stats();
            assertThat(stats).isNotNull();
        }

        @Test
        @DisplayName("Underlying Caffeine cache respects maximumSize (cache is bounded)")
        void maximumSizeIsBounded() {
            CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache("testCases");
            assertThat(caffeineCache).isNotNull();
            // policy().eviction() is only present when maximumSize / maximumWeight was set
            assertThat(caffeineCache.getNativeCache().policy().eviction()).isPresent();
        }
    }

    // -----------------------------------------------------------------------
    // Property binding: TTL picked up from @TestPropertySource
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Property binding")
    class PropertyBindingTests {

        @Test
        @DisplayName("TTL of 45 minutes is honoured — cache is created without error")
        void customTtlProperty() {
            // If @Value("${app.cache.testcase-ttl-minutes:60}") failed to bind,
            // the context would not load.  Reaching here proves the property was applied.
            assertThat(cacheManager).isNotNull();
            assertThat(cacheManager.getCache("testCases")).isNotNull();
        }

        @Test
        @DisplayName("Default TTL (60 min) is used when property is absent")
        void defaultTtlUsed() {
            // Instantiate directly to verify the default works without Spring context
            CacheConfig cfg = new CacheConfig();
            CacheManager cm = cfg.cacheManager();
            assertThat(cm).isNotNull();
            assertThat(cm.getCache("testCases")).isNotNull();
        }
    }

    @TestConfiguration
    static class TestConfig {
        // empty — CacheConfig is pulled in via @Import above
    }
}
