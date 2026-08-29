package edu.vt.hokiehub.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cached values are stored as JSON rather than Java serialisation, so the cache can be
 * inspected with redis-cli and does not break when a class changes package.
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class CacheConfig {

    /**
     * Part of every key, so a build can never read an entry written by a build that
     * meant something different by it. Bump it when a cached shape changes.
     *
     * Expiry is not a substitute: the hour an entry takes to lapse is an hour of a
     * broken endpoint.
     */
    private static final String CACHE_VERSION = "v2";

    @Bean
    RedisCacheConfiguration cacheConfiguration(ObjectMapper objectMapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
                // The category tree changes on the order of never; an hour is a safe floor
                // and evictTree() handles the rare explicit change.
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "hokiehub:" + CACHE_VERSION + ":" + cacheName + ":")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheMapper(objectMapper))));
    }

    /**
     * A private copy of the mapper, with polymorphic typing that covers final types.
     *
     * This cache never round-tripped. Reading the category tree back out of Redis
     * failed outright, which surfaced as `GET /api/categories` answering 400 —
     * taking down the endpoint every page depends on.
     *
     * The cause is that `CategoryResponse` is a record, and records are final.
     * Jackson's usual `NON_FINAL` typing writes no `@class` for a final type, while
     * the deserializer — which only knows it is reading an `Object` — requires one.
     * Written without, demanded on read. `EVERYTHING` types the records too.
     *
     * It is a copy because the mapper passed in is the one Spring MVC uses for HTTP
     * responses: configuring that in place would stamp `@class` onto the API's own
     * JSON. Handing it over unmodified, as this once did, is the other half of the
     * same mistake.
     *
     * The validator is restricted to this application's types and the collections
     * holding them. Unrestricted polymorphic deserialisation turns a cache into a
     * way to instantiate arbitrary classes.
     */
    private static ObjectMapper cacheMapper(ObjectMapper base) {
        return base.copy().activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("edu.vt.hokiehub.")
                        .allowIfSubType("java.util.")
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
    }
}
