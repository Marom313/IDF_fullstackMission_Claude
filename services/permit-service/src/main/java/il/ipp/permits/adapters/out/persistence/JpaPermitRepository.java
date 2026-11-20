package il.ipp.permits.adapters.out.persistence;

import il.ipp.permits.domain.Permit;
import il.ipp.permits.domain.PermitPorts.PermitRepository;
import il.ipp.permits.domain.PermitStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JpaPermitRepository implements PermitRepository {
  @PersistenceContext
  private EntityManager em;
  private final JdbcTemplate jdbc;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  JpaPermitRepository(JdbcTemplate jdbc, com.fasterxml.jackson.databind.ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public Optional<Permit> findById(UUID id) {
    return Optional.ofNullable(em.find(PermitEntity.class, id)).map(JpaPermitRepository::toDomain);
  }

  @Override
  public Permit save(Permit p) {
    PermitEntity e = em.find(PermitEntity.class, p.id());
    boolean isNew = e == null;
    if (isNew) {
      e = new PermitEntity();
      e.id = p.id();
    } else if (e.version != p.version()) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(PermitEntity.class, p.id());
    }
    e.personId = p.personId();
    e.nationalId = p.nationalId();
    e.type = p.type();
    e.status = p.status();
    e.employerId = p.employerId();
    e.licenseId = p.licenseId();
    e.region = p.region();
    e.validFrom = p.validFrom();
    e.validTo = p.validTo();
    e.policyVersion = p.policyVersion();
    e.recommendation = p.recommendation();
    e.reasons = new ArrayList<>(p.reasons());
    e.formData = new LinkedHashMap<>(p.formData());
    e.submittedAt = p.submittedAt();
    e.decidedAt = p.decidedAt();
    e.decidedBy = p.decidedBy();
    if (isNew) {
      em.persist(e);
    }
    em.flush();
    return toDomain(e);
  }

  @Override
  public List<Permit> byPerson(UUID personId) {
    return em.createQuery("select p from PermitEntity p where p.personId = :pid order by p.submittedAt desc",
        PermitEntity.class).setParameter("pid", personId).setMaxResults(100).getResultList()
        .stream().map(JpaPermitRepository::toDomain).toList();
  }

  /** Keyset pagination on (submittedAt, id). Cursor = "<epochMillis>_<uuid>". */
  @Override
  public List<Permit> queue(List<PermitStatus> statuses, String after, int limit) {
    String jpql = "select p from PermitEntity p where p.status in :st"
        + (after != null ? " and (p.submittedAt > :ts or (p.submittedAt = :ts and p.id > :id))" : "")
        + " order by p.submittedAt, p.id";
    var q = em.createQuery(jpql, PermitEntity.class).setParameter("st", statuses).setMaxResults(limit);
    if (after != null) {
      String[] c = after.split("_", 2);
      q.setParameter("ts", Instant.ofEpochMilli(Long.parseLong(c[0]))).setParameter("id", UUID.fromString(c[1]));
    }
    return q.getResultList().stream().map(JpaPermitRepository::toDomain).toList();
  }

  @Override
  public List<Permit> byEmployer(UUID employerId, PermitStatus status, String after, int limit) {
    String jpql = "select p from PermitEntity p where p.employerId = :emp"
        + (status != null ? " and p.status = :st" : "")
        + (after != null ? " and p.id > :after" : "") + " order by p.id";
    var q = em.createQuery(jpql, PermitEntity.class).setParameter("emp", employerId).setMaxResults(limit);
    if (status != null) {
      q.setParameter("st", status);
    }
    if (after != null) {
      q.setParameter("after", UUID.fromString(after));
    }
    return q.getResultList().stream().map(JpaPermitRepository::toDomain).toList();
  }

  @Override
  public List<UUID> activeExpiredBefore(LocalDate day, int limit) {
    return jdbc.queryForList("select id from permit where status = 'ACTIVE' and valid_to < ? limit ?",
        UUID.class, java.sql.Date.valueOf(day), limit);
  }

  @Override
  public long count() {
    Long n = jdbc.queryForObject("select count(*) from permit", Long.class);
    return n == null ? 0 : n;
  }

  @Override
  public Map<String, Long> countByStatus() {
    Map<String, Long> m = new LinkedHashMap<>();
    jdbc.query("select status, count(*) from permit group by status order by status",
        rs -> {
          m.put(rs.getString(1), rs.getLong(2));
        });
    return m;
  }

  static Permit toDomain(PermitEntity e) {
    return new Permit(e.id, e.personId, e.nationalId, e.type, e.status, e.employerId, e.licenseId, e.region,
        e.validFrom, e.validTo, e.policyVersion, e.recommendation, e.reasons, e.formData, e.submittedAt, e.decidedAt,
        e.decidedBy, e.version);
  }

  /** Bulk insert for seeding (bypasses JPA for speed). */
  @Override
  public void insertAll(List<Permit> permits) {
    jdbc.batchUpdate("insert into permit(id, person_id, national_id, type, status, employer_id, license_id, region, "
            + "valid_from, valid_to, policy_version, recommendation, reasons, form_data, submitted_at, decided_at, "
            + "decided_by, version) values (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,0)",
        permits.stream().map(p -> {
          try {
            return new Object[] {p.id(), p.personId(), p.nationalId(), p.type().name(), p.status().name(),
                p.employerId(), p.licenseId(), p.region(),
                p.validFrom() == null ? null : java.sql.Date.valueOf(p.validFrom()),
                p.validTo() == null ? null : java.sql.Date.valueOf(p.validTo()), p.policyVersion(),
                p.recommendation(), json.writeValueAsString(p.reasons()), json.writeValueAsString(p.formData()),
                Timestamp.from(p.submittedAt()), p.decidedAt() == null ? null : Timestamp.from(p.decidedAt()),
                p.decidedBy()};
          } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
          }
        }).toList());
  }
}
