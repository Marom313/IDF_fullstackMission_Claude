package il.ipp.permits.application;

import static il.ipp.common.seed.SeedCatalog.*;

import il.ipp.common.events.Topics;
import il.ipp.common.outbox.OutboxWriter;
import il.ipp.permits.domain.Permit;
import il.ipp.permits.domain.PermitPorts.PermitRepository;
import il.ipp.permits.domain.PermitStatus;
import il.ipp.permits.domain.PermitType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds employment licenses, sector quotas and 8,000 permits/applications (fixed seed).
 * Uses SeedCatalog so ids match the registry without any call between services.
 */
@Component
public class PermitSeeder {
  private static final Logger log = LoggerFactory.getLogger(PermitSeeder.class);
  private final PermitRepository permits;
  private final OutboxWriter outbox;
  private final TransactionTemplate tx;
  private final JdbcTemplate jdbc;
  private final boolean enabled;

  public PermitSeeder(PermitRepository permits, OutboxWriter outbox, TransactionTemplate tx, JdbcTemplate jdbc,
                      @Value("${ipp.seed.enabled:true}") boolean enabled) {
    this.permits = permits;
    this.outbox = outbox;
    this.tx = tx;
    this.jdbc = jdbc;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seed() {
    if (!enabled) {
      return;
    }
    tx.executeWithoutResult(s -> {
      jdbc.queryForObject("select pg_advisory_xact_lock(42003)", Object.class);
      if (permits.count() > 0) {
        return;
      }
      long t0 = System.currentTimeMillis();
      Random rnd = new Random(SEED + 10);
      LocalDate today = LocalDate.now(PermitService.IL);

      // licenses: one per employer; quota = assigned workers + headroom
      int[] assigned = new int[EMPLOYERS];
      for (int i = 0; i < PERSONS; i++) {
        if (employerIndexOf(i) >= 0) {
          assigned[employerIndexOf(i)]++;
        }
      }
      Map<String, Integer> sectorUsed = new HashMap<>();
      List<Object[]> lic = new ArrayList<>();
      for (int j = 0; j < EMPLOYERS; j++) {
        lic.add(new Object[] {licenseId(j), employerId(j), employerSector(j), assigned[j] + 5, assigned[j],
            java.sql.Date.valueOf(today.plusYears(1))});
        sectorUsed.merge(employerSector(j), assigned[j], Integer::sum);
      }
      jdbc.batchUpdate("insert into employment_license(id, employer_id, sector, workers_quota, workers_assigned, "
          + "valid_to, status) values (?,?,?,?,?,?,'ACTIVE')", lic);
      for (String sector : SECTORS) {
        int used = sectorUsed.getOrDefault(sector, 0);
        int headroom = sector.equals("CONSTRUCTION") ? 25 : 150; // construction is tight → good demo of quota
        jdbc.update("insert into sector_quota(sector, total, used) values (?,?,?)", sector, used + headroom, used);
      }

      List<Permit> ps = new ArrayList<>();
      for (int i = 0; i < PERSONS && ps.size() < 8000; i++) {
        if (i == 4) {
          continue; // demo: person without any permit
        }
        int e = employerIndexOf(i);
        UUID pid = personId(i);
        if (e >= 0) {
          // last year's permit – expired (history)
          ps.add(permit(pid, i, PermitType.WORK_ENTRY, PermitStatus.EXPIRED, e, today.minusDays(400),
              today.minusDays(310), rnd));
          boolean expired = i > 4 && rnd.nextInt(10) == 0;
          LocalDate from = today.minusDays(5 + rnd.nextInt(60));
          ps.add(permit(pid, i, PermitType.WORK_ENTRY, expired ? PermitStatus.EXPIRED : PermitStatus.ACTIVE, e,
              expired ? today.minusDays(120) : from, expired ? today.minusDays(30) : from.plusDays(90), rnd));
        }
        if (isDriver(i)) {
          LocalDate from = today.minusDays(rnd.nextInt(20));
          ps.add(permit(pid, i, PermitType.DRIVER, PermitStatus.ACTIVE, -1, from, from.plusDays(30), rnd));
        }
      }
      // pending applications for the clerk queue (unemployed persons applying for medical / work)
      for (int i = 5; i < PERSONS && ps.size() < 8000; i++) {
        if (employerIndexOf(i) < 0 && !isDriver(i)) {
          PermitStatus st = rnd.nextInt(4) == 0 ? PermitStatus.IN_REVIEW : PermitStatus.SUBMITTED;
          ps.add(permit(personId(i), i, PermitType.MEDICAL, st, -1, null, null, rnd));
        }
      }
      permits.insertAll(ps);

      List<Map.Entry<String, Map<String, Object>>> events = new ArrayList<>();
      for (Permit p : ps) {
        if (p.status() == PermitStatus.ACTIVE || p.status() == PermitStatus.EXPIRED) {
          Map<String, Object> m = new HashMap<>();
          m.put("permitId", p.id().toString());
          m.put("personId", p.personId().toString());
          m.put("nationalId", p.nationalId());
          m.put("type", p.type().name());
          m.put("status", p.status().name());
          m.put("region", p.region());
          m.put("validFrom", String.valueOf(p.validFrom()));
          m.put("validTo", String.valueOf(p.validTo()));
          m.put("policyVersion", p.policyVersion());
          m.put("employerId", p.employerId() == null ? null : p.employerId().toString());
          events.add(Map.entry(p.personId().toString(), m));
        }
      }
      outbox.writeAll(Topics.PERMIT_STATUS_CHANGED, "PermitStatusChanged", events);
      log.info("seeded {} licenses and {} permits ({} events) in {} ms", lic.size(), ps.size(), events.size(),
          System.currentTimeMillis() - t0);
    });
  }

  public static UUID licenseId(int j) {
    return UUID.nameUUIDFromBytes(("ipp-license-" + j).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static Permit permit(UUID personId, int i, PermitType type, PermitStatus status, int employer,
                               LocalDate from, LocalDate to, Random rnd) {
    Map<String, Object> form = new HashMap<>();
    form.put("magneticCard", String.format("MC-%08d", 10_000_000 + i));
    if (type == PermitType.WORK_ENTRY) {
      form.put("employerId", employerId(employer).toString());
      form.put("workRegion", employerRegion(employer));
    } else if (type == PermitType.DRIVER) {
      form.put("vehiclePlate", String.format("%02d-%03d-%02d", rnd.nextInt(100), rnd.nextInt(1000), rnd.nextInt(100)));
      form.put("cargoType", List.of("FOOD", "BUILDING_MATERIALS", "TEXTILE", "FUEL").get(rnd.nextInt(4)));
      form.put("destinationRegion", REGIONS.get(rnd.nextInt(REGIONS.size())));
    } else {
      form.put("hospitalName", "Demo Medical Center " + (1 + rnd.nextInt(6)));
      form.put("appointmentDate", LocalDate.now().plusDays(3 + rnd.nextInt(30)).toString());
    }
    boolean decided = status != PermitStatus.SUBMITTED && status != PermitStatus.IN_REVIEW;
    Instant submitted = (from != null ? from : LocalDate.now().minusDays(rnd.nextInt(14)))
        .atStartOfDay(PermitService.IL).toInstant().minus(2 + rnd.nextInt(72), ChronoUnit.HOURS);
    return new Permit(UUID.nameUUIDFromBytes(("ipp-permit-" + i + "-" + type + "-" + status + "-" + from)
        .getBytes(java.nio.charset.StandardCharsets.UTF_8)), personId, nationalId(i), type, status,
        employer >= 0 ? employerId(employer) : null, employer >= 0 ? licenseId(employer) : null,
        employer >= 0 ? employerRegion(employer) : personRegion(i), from, to, "seed-v1",
        decided ? "APPROVE" : "REFER_TO_CLERK", decided ? List.of("SEEDED") : List.of("AWAITING_REVIEW"), form,
        submitted, decided ? submitted.plus(1, ChronoUnit.DAYS) : null, decided ? "clerk1" : null, 0);
  }
}
