package edu.vt.hokiehub.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The category tree is the one thing this service caches, and the cache is on the
 * public path every page hits — so when it broke, it took the endpoint with it.
 *
 * It did break. `GET /api/categories` answered 400 "object is not an instance of
 * declaring class" in production: the serializer had been handed the shared MVC
 * ObjectMapper, which carries no type information, so what came back out of Redis
 * was a LinkedHashMap and the cast failed. Every existing test ran with
 * `spring.cache.type=simple`, an in-memory cache that hands back the identical
 * object and therefore cannot fail this way.
 *
 * This one runs against real Redis, and reads the value back twice — the second
 * read is served from the cache, which is the path that was broken.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CategoryCacheIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // The real cache, not the in-memory stand-in the other suites use.
        registry.add("spring.cache.type", () -> "redis");
        registry.add("hokiehub.supabase.url", () -> "https://test-project.supabase.co");
        registry.add("hokiehub.rate-limit.enabled", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StringRedisTemplate redisTemplate;

    @org.junit.jupiter.api.BeforeEach
    void emptyTheCache() {
        // Otherwise one test's entries decide the next one's outcome.
        var keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    @Test
    @DisplayName("the tree survives a round trip through Redis and reads the same twice")
    void treeRoundTripsThroughRedis() throws Exception {
        String first = mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Written to Redis, under the versioned prefix.
        assertThat(redisTemplate.keys("hokiehub:*")).isNotEmpty();

        // The second read comes back out of the cache — the path that was broken.
        String second = mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);

        JsonNode tree = json.readTree(second);
        assertThat(tree.size()).isEqualTo(12);
        assertThat(tree.findValuesAsText("name")).contains("Textbooks", "Electronics");
        // Children have to survive too; a tree flattened to twelve bare parents
        // would still be twelve elements and still be wrong.
        assertThat(tree.get(0).get("children").isArray()).isTrue();
    }

    @Test
    @DisplayName("a cache entry written by an older, incompatible build cannot be read back")
    void staleEntryUnderAnOldPrefixIsIgnored() throws Exception {
        // What actually happened: an entry left by a previous build, in a shape
        // this one cannot read. Under the old unprefixed key it poisoned the
        // endpoint; the version prefix means this build never looks at it.
        redisTemplate.opsForValue().set("categories::tree", "{\"garbage\":true}");

        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }
}
