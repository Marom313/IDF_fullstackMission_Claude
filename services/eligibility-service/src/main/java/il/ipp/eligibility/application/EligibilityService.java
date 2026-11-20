package il.ipp.eligibility.application;

import il.ipp.common.security.CurrentUser;
import il.ipp.common.web.DomainException;
import il.ipp.eligibility.domain.Eligibility;
import il.ipp.eligibility.domain.Eligibility.Purpose;
import il.ipp.eligibility.domain.Eligibility.Result;
import il.ipp.eligibility.domain.Eligibility.View;
import il.ipp.eligibility.domain.EligibilityPorts.AuditSink;
import il.ipp.eligibility.domain.EligibilityPorts.ReadModel;
import il.ipp.eligibility.domain.EligibilityPorts.ViewCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/**
 * Hot path: Redis hit (~2ms) → decide. Miss → single-flight rebuild from the Postgres read model.
 * NO synchronous call to any other service here.
 */
@Service
public class EligibilityService {
  private static final ZoneId IL = ZoneId.of("Asia/Jerusalem");
  private final ReadModel readModel;
  private final ViewCache cache;
  private final AuditSink audit;
  private final Counter hits;
  private final Counter misses;
  private final Timer timer;

  public EligibilityService(ReadModel readModel, ViewCache cache, AuditSink audit, MeterRegistry meters) {
    this.readModel = readModel;
    this.cache = cache;
    this.audit = audit;
    this.hits = meters.counter("ipp.eligibility.cache", "result", "hit");
    this.misses = meters.counter("ipp.eligibility.cache", "result", "miss");
    this.timer = Timer.builder("ipp.eligibility.check").publishPercentiles(0.5, 0.95, 0.99).register(meters);
  }

  public record CheckResponse(UUID personId, String nationalId, String fullName, String photoUrl, String decision,
                              java.util.List<String> reasons, String policyVersion, UUID permitId, Instant checkedAt,
                              boolean cacheHit) {}

  public CheckResponse check(String nationalId, UUID personId, Purpose purpose, String region) {
    return timer.record(() -> doCheck(nationalId, personId, purpose, region));
  }

  private CheckResponse doCheck(String nationalId, UUID personId, Purpose purpose, String region) {
    UUID pid = personId != null ? personId : resolve(nationalId);
    boolean[] hit = {true};
    View v = cache.get(pid).orElseGet(() -> {
      hit[0] = false;
      return rebuild(pid);
    });
    (hit[0] ? hits : misses).increment();
    Result r = Eligibility.decide(v, purpose, region, LocalDate.now(IL), Instant.now());
    audit.checked(v, purpose.name(), r, CurrentUser.get().username());
    return new CheckResponse(v.personId(), v.nationalId(), v.fullName(),
        "/api/registry/persons/" + v.personId() + "/photo", r.decision().name(), r.reasons(), r.policyVersion(),
        r.permitId(), r.checkedAt(), hit[0]);
  }

  private UUID resolve(String nationalId) {
    if (nationalId == null || !nationalId.matches("\\d{9}")) {
      throw new DomainException("INVALID_ID", 400, "nationalId must be 9 digits");
    }
    return cache.personIdOf(nationalId).orElseGet(() -> {
      UUID id = readModel.personIdOf(nationalId).orElseThrow(() -> DomainException.notFound("Person"));
      cache.putPersonId(nationalId, id);
      return id;
    });
  }

  /** Single-flight: one caller rebuilds, concurrent callers wait briefly and re-read the cache. */
  private View rebuild(UUID pid) {
    boolean locked = cache.tryLock(pid, Duration.ofSeconds(2));
    try {
      if (!locked) {
        for (int i = 0; i < 10; i++) {
          sleep(20);
          Optional<View> v = cache.get(pid);
          if (v.isPresent()) {
            return v.get();
          }
        }
      }
      View v = readModel.load(pid).orElseThrow(() -> DomainException.notFound("Person"));
      // TTL 10 min + up to 60s jitter → keys don't all expire at the same moment
      cache.put(pid, v, Duration.ofSeconds(600 + ThreadLocalRandom.current().nextInt(60)));
      return v;
    } finally {
      if (locked) {
        cache.unlock(pid);
      }
    }
  }

  public double hitRatio() {
    double h = hits.count();
    double m = misses.count();
    return h + m == 0 ? 0 : h / (h + m);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
