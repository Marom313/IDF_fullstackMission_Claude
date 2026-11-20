package il.ipp.enforcement.application;

import static il.ipp.common.seed.SeedCatalog.*;

import il.ipp.enforcement.domain.Enforcement.Crossing;
import il.ipp.enforcement.domain.Enforcement.Direction;
import il.ipp.enforcement.domain.Enforcement.Violation;
import il.ipp.enforcement.domain.Enforcement.ViolationType;
import il.ipp.enforcement.domain.EnforcementPorts.CrossingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Seeding of 50,000 historical crossings + demo sanction, and the periodic overstay detector. */
@Component
class EnforcementJobs {
  private static final Logger log = LoggerFactory.getLogger(EnforcementJobs.class);
  private final CrossingRepository crossings;
  private final EnforcementService service;
  private final TransactionTemplate tx;
  private final JdbcTemplate jdbc;
  private final boolean seed;

  EnforcementJobs(CrossingRepository crossings, EnforcementService service, TransactionTemplate tx, JdbcTemplate jdbc,
                  @Value("${ipp.seed.enabled:true}") boolean seed) {
    this.crossings = crossings;
    this.service = service;
    this.tx = tx;
    this.jdbc = jdbc;
    this.seed = seed;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seed() {
    if (!seed) {
      return;
    }
    tx.executeWithoutResult(s -> {
      jdbc.queryForObject("select pg_advisory_xact_lock(42004)", Object.class);
      Boolean seeded = jdbc.queryForObject("select exists(select 1 from enf_case)", Boolean.class);
      if (Boolean.TRUE.equals(seeded)) {
        return;
      }
      long t0 = System.currentTimeMillis();
      Random rnd = new Random(SEED + 20);
      Instant now = Instant.now();
      List<Crossing> batch = new ArrayList<>(5000);
      int total = 0;
      // employed persons commute: alternating ENTRY/EXIT pairs over the last 75 days
      for (int day = 75; day >= 1 && total < 50_000; day--) {
        for (int i = 5; i < PERSONS && total < 50_000; i += 1 + rnd.nextInt(9)) {
          if (employerIndexOf(i) < 0) {
            continue;
          }
          Instant in = now.minus(day, ChronoUnit.DAYS).plus(5 + rnd.nextInt(3), ChronoUnit.HOURS);
          String gate = GATES.get(i % GATES.size());
          batch.add(new Crossing(UUID.randomUUID(), personId(i), nationalId(i), gate, Direction.ENTRY, in, "SEED"));
          batch.add(new Crossing(UUID.randomUUID(), personId(i), nationalId(i), gate, Direction.EXIT,
              in.plus(9 + rnd.nextInt(3), ChronoUnit.HOURS), "SEED"));
          total += 2;
          if (batch.size() >= 5000) {
            crossings.insertAll(batch);
            batch.clear();
          }
        }
      }
      crossings.insertAll(batch);
      // historical crossings are balanced (entry+exit), so presence starts empty = nobody inside
      log.info("seeded {} crossing events in {} ms", total, System.currentTimeMillis() - t0);

      // demo person #2 (900000003): previous overstay → active entry ban (closes the loop for the demo)
      service.openCase(personId(2), nationalId(2), new Violation(ViolationType.OVERSTAY,
          "SEED: entered 2026-09-01, permit expired 2026-09-30, exit recorded 2026-10-12"), "seed");
    });
  }

  @Scheduled(fixedDelayString = "${ipp.enforcement.overstay-scan-ms:30000}", initialDelay = 45_000)
  void overstayScan() {
    int opened = service.detectOverstays();
    if (opened > 0) {
      log.info("overstay detector opened {} cases", opened);
    }
  }

  static LocalDate today() {
    return LocalDate.now(EnforcementService.IL);
  }
}
