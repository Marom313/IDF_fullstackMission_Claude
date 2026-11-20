package il.ipp.eligibility.adapters.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.eligibility.domain.Eligibility.View;
import il.ipp.eligibility.domain.EligibilityPorts.ViewCache;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Keys (all versioned "v1:" so a deploy can invalidate everything, all with TTL):
 *   v1:elig:{personId}  → JSON view, TTL 10min + jitter
 *   v1:nid:{nationalId} → personId, TTL 1h (immutable mapping)
 *   v1:lock:{personId}  → single-flight lock, TTL 2s
 * Redis errors degrade to a DB read (cache is an optimisation, never a dependency).
 */
@Component
class RedisViewCache implements ViewCache {
  private static final Logger log = LoggerFactory.getLogger(RedisViewCache.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper json;

  RedisViewCache(StringRedisTemplate redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  @Override
  public Optional<View> get(UUID personId) {
    try {
      String s = redis.opsForValue().get("v1:elig:" + personId);
      return s == null ? Optional.empty() : Optional.of(json.readValue(s, View.class));
    } catch (Exception e) {
      log.warn("redis get failed: {}", e.toString());
      return Optional.empty();
    }
  }

  @Override
  public void put(UUID personId, View view, Duration ttl) {
    try {
      redis.opsForValue().set("v1:elig:" + personId, json.writeValueAsString(view), ttl);
    } catch (Exception e) {
      log.warn("redis put failed: {}", e.toString());
    }
  }

  @Override
  public void evict(UUID personId) {
    try {
      redis.delete("v1:elig:" + personId);
    } catch (Exception e) {
      log.warn("redis evict failed: {}", e.toString());
    }
  }

  @Override
  public Optional<UUID> personIdOf(String nationalId) {
    try {
      String s = redis.opsForValue().get("v1:nid:" + nationalId);
      return s == null ? Optional.empty() : Optional.of(UUID.fromString(s));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  @Override
  public void putPersonId(String nationalId, UUID personId) {
    try {
      redis.opsForValue().set("v1:nid:" + nationalId, personId.toString(), Duration.ofHours(1));
    } catch (Exception e) {
      log.warn("redis put failed: {}", e.toString());
    }
  }

  @Override
  public boolean tryLock(UUID personId, Duration ttl) {
    try {
      return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("v1:lock:" + personId, "1", ttl));
    } catch (Exception e) {
      return true; // redis down → just rebuild
    }
  }

  @Override
  public void unlock(UUID personId) {
    try {
      redis.delete("v1:lock:" + personId);
    } catch (Exception ignored) {
      // lock has a TTL anyway
    }
  }
}
