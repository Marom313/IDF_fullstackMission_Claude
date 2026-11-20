package il.ipp.registry.application;

import static il.ipp.common.seed.SeedCatalog.*;

import il.ipp.common.events.Topics;
import il.ipp.common.outbox.OutboxWriter;
import il.ipp.registry.domain.Employer;
import il.ipp.registry.domain.Person;
import il.ipp.registry.domain.RegistryPorts.EmployerRepository;
import il.ipp.registry.domain.RegistryPorts.PersonRepository;
import il.ipp.registry.domain.SecurityStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Seeds 200 employers + 5,000 persons (Data Faker, fixed seed) once, and publishes them via the outbox. */
@Component
public class RegistrySeeder {
  private static final Logger log = LoggerFactory.getLogger(RegistrySeeder.class);
  private final PersonRepository persons;
  private final EmployerRepository employers;
  private final OutboxWriter outbox;
  private final TransactionTemplate tx;
  private final JdbcTemplate jdbc;
  private final boolean enabled;

  public RegistrySeeder(PersonRepository persons, EmployerRepository employers, OutboxWriter outbox,
                        TransactionTemplate tx, JdbcTemplate jdbc, @Value("${ipp.seed.enabled:true}") boolean enabled) {
    this.persons = persons;
    this.employers = employers;
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
    // advisory lock: with --scale only one instance seeds
    tx.executeWithoutResult(s -> {
      jdbc.queryForObject("select pg_advisory_xact_lock(42001)", Object.class);
      if (persons.count() > 0) {
        return;
      }
      long t0 = System.currentTimeMillis();
      Faker en = new Faker(Locale.ENGLISH, new Random(SEED));
      Faker ar = new Faker(Locale.forLanguageTag("ar"), new Random(SEED + 1));
      Random rnd = new Random(SEED + 2);

      List<Employer> emps = new ArrayList<>();
      for (int j = 0; j < EMPLOYERS; j++) {
        String name = j == 0 ? "Demo Builders Ltd" : en.company().name();
        emps.add(new Employer(employerId(j), name, String.format("51%07d", j), employerSector(j),
            employerRegion(j), employerUsername(j)));
      }
      employers.insertAll(emps);

      List<Person> ps = new ArrayList<>();
      List<Map.Entry<String, Map<String, Object>>> events = new ArrayList<>();
      LocalDate today = LocalDate.of(2026, 11, 20);
      for (int i = 0; i < PERSONS; i++) {
        int e = employerIndexOf(i);
        LocalDate birth = today.minusYears(22 + rnd.nextInt(38)).minusDays(rnd.nextInt(365));
        Person p = new Person(personId(i), nationalId(i), ar.name().firstName(), ar.name().lastName(), birth,
            String.format("MC-%08d", 10_000_000 + i), today.plusMonths(6 + rnd.nextInt(18)),
            SecurityStatus.valueOf(securityStatus(i)), personRegion(i), e >= 0 ? employerId(e) : null,
            personUsername(i), String.format("059%07d", rnd.nextInt(10_000_000)));
        ps.add(p);
        events.add(Map.entry(p.id().toString(), personPayload(p)));
      }
      persons.insertAll(ps);
      outbox.writeAll(Topics.PERSON_UPSERTED, "PersonUpserted", events);
      log.info("seeded {} employers and {} persons in {} ms", emps.size(), ps.size(), System.currentTimeMillis() - t0);
    });
  }

  static Map<String, Object> personPayload(Person p) {
    Map<String, Object> m = new HashMap<>();
    m.put("personId", p.id().toString());
    m.put("nationalId", p.nationalId());
    m.put("fullName", p.fullName());
    m.put("birthDate", p.birthDate().toString());
    m.put("securityStatus", p.securityStatus().name());
    m.put("region", p.region());
    m.put("employerId", p.employerId() == null ? null : p.employerId().toString());
    return m;
  }
}
