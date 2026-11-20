package il.ipp.enforcement.adapters.out.persistence;

import il.ipp.enforcement.domain.Enforcement.Crossing;
import il.ipp.enforcement.domain.Enforcement.Direction;
import il.ipp.enforcement.domain.Enforcement.PermitSnapshot;
import il.ipp.enforcement.domain.EnforcementPorts.CaseRecord;
import il.ipp.enforcement.domain.EnforcementPorts.CaseRepository;
import il.ipp.enforcement.domain.EnforcementPorts.CrossingRepository;
import il.ipp.enforcement.domain.EnforcementPorts.PermitSnapshots;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
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
class JdbcCaseRepository implements CaseRepository {
  private final JdbcTemplate jdbc;

  JdbcCaseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<CaseRecord> find(UUID id) {
    return jdbc.query("select * from enf_case where id = ?", JdbcCaseRepository::map, id).stream().findFirst();
  }

  @Override
  public boolean hasOpenCase(UUID personId, String violationType) {
    Integer n = jdbc.queryForObject("select count(*) from enf_case where person_id = ? and violation_type = ? "
        + "and status in ('OPEN','SANCTIONED','APPEALED')", Integer.class, personId, violationType);
    return n != null && n > 0;
  }

  @Override
  public void insert(CaseRecord c) {
    jdbc.update("insert into enf_case(id, person_id, national_id, violation_type, details, status, proposed_sanction, "
            + "proposed_days, opened_at, updated_at, updated_by, version) values (?,?,?,?,?,?,?,?,?,?,?,0)",
        c.id(), c.personId(), c.nationalId(), c.violationType(), c.details(), c.status(), c.proposedSanction(),
        c.proposedDays(), Timestamp.from(c.openedAt()), Timestamp.from(c.updatedAt()), c.updatedBy());
  }

  @Override
  public boolean update(CaseRecord c) {
    return jdbc.update("update enf_case set status = ?, sanction_id = ?, sanction_until = ?, appeal_text = ?, "
            + "decision_note = ?, updated_at = ?, updated_by = ?, version = version + 1 where id = ? and version = ?",
        c.status(), c.sanctionId(), c.sanctionUntil() == null ? null : Date.valueOf(c.sanctionUntil()), c.appealText(),
        c.decisionNote(), Timestamp.from(c.updatedAt()), c.updatedBy(), c.id(), c.version()) == 1;
  }

  /** Keyset on (opened_at desc, id). Cursor = "<epochMillis>_<uuid>". */
  @Override
  public List<CaseRecord> list(String status, String after, int limit) {
    StringBuilder sql = new StringBuilder("select * from enf_case where 1=1");
    List<Object> args = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      sql.append(" and status = ?");
      args.add(status);
    }
    if (after != null) {
      String[] c = after.split("_", 2);
      sql.append(" and (opened_at, id) < (?, ?)");
      args.add(Timestamp.from(Instant.ofEpochMilli(Long.parseLong(c[0]))));
      args.add(UUID.fromString(c[1]));
    }
    sql.append(" order by opened_at desc, id desc limit ?");
    args.add(limit);
    return jdbc.query(sql.toString(), JdbcCaseRepository::map, args.toArray());
  }

  @Override
  public List<CaseRecord> byPerson(UUID personId) {
    return jdbc.query("select * from enf_case where person_id = ? order by opened_at desc", JdbcCaseRepository::map,
        personId);
  }

  @Override
  public Map<String, Long> countByStatus() {
    Map<String, Long> m = new LinkedHashMap<>();
    jdbc.query("select status, count(*) from enf_case group by status order by status",
        rs -> {
          m.put(rs.getString(1), rs.getLong(2));
        });
    return m;
  }

  static CaseRecord map(ResultSet rs, int i) throws SQLException {
    Date until = rs.getDate("sanction_until");
    return new CaseRecord(rs.getObject("id", UUID.class), rs.getObject("person_id", UUID.class),
        rs.getString("national_id"), rs.getString("violation_type"), rs.getString("details"), rs.getString("status"),
        rs.getString("proposed_sanction"), rs.getInt("proposed_days"), rs.getObject("sanction_id", UUID.class),
        until == null ? null : until.toLocalDate(), rs.getString("appeal_text"), rs.getString("decision_note"),
        rs.getTimestamp("opened_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
        rs.getString("updated_by"), rs.getLong("version"));
  }
}

@Repository
class JdbcCrossingRepository implements CrossingRepository {
  private static final String INSERT = "insert into crossing_events(id, person_id, national_id, gate, direction, "
      + "occurred_at, source) values (?,?,?,?,?,?,?) on conflict do nothing";
  private final JdbcTemplate jdbc;

  JdbcCrossingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Crossing c) {
    jdbc.update(INSERT, row(c));
  }

  @Override
  public void insertAll(List<Crossing> list) {
    jdbc.batchUpdate(INSERT, list.stream().map(JdbcCrossingRepository::row).toList());
  }

  private static Object[] row(Crossing c) {
    return new Object[] {c.id(), c.personId(), c.nationalId(), c.gate(), c.direction().name(),
        Timestamp.from(c.occurredAt()), c.source()};
  }

  @Override
  public List<Crossing> byPerson(UUID personId, int limit) {
    return jdbc.query("select * from crossing_events where person_id = ? order by occurred_at desc limit ?",
        (rs, i) -> new Crossing(rs.getObject("id", UUID.class), rs.getObject("person_id", UUID.class),
            rs.getString("national_id"), rs.getString("gate"), Direction.valueOf(rs.getString("direction")),
            rs.getTimestamp("occurred_at").toInstant(), rs.getString("source")), personId, limit);
  }

  @Override
  public long count() {
    // estimate is O(1) on a big partitioned table – good enough for the dashboard
    Long n = jdbc.queryForObject("select coalesce(sum(reltuples), 0)::bigint from pg_class c join pg_inherits i "
        + "on i.inhrelid = c.oid where i.inhparent = 'crossing_events'::regclass", Long.class);
    if (n == null || n <= 0) {
      n = jdbc.queryForObject("select count(*) from crossing_events", Long.class);
    }
    return n == null ? 0 : n;
  }

  @Override
  public void updatePresence(UUID personId, String nationalId, boolean inside, Instant at, String gate) {
    jdbc.update("insert into presence(person_id, national_id, inside, last_crossing_at, last_entry_at, last_gate) "
            + "values (?,?,?,?,?,?) on conflict (person_id) do update set inside = excluded.inside, "
            + "last_crossing_at = excluded.last_crossing_at, last_gate = excluded.last_gate, "
            + "last_entry_at = coalesce(excluded.last_entry_at, presence.last_entry_at), "
            + "national_id = coalesce(excluded.national_id, presence.national_id) "
            + "where presence.last_crossing_at is null or presence.last_crossing_at <= excluded.last_crossing_at",
        personId, nationalId, inside, Timestamp.from(at), inside ? Timestamp.from(at) : null, gate);
  }

  @Override
  public List<UUID> insideSince(Instant before, int limit) {
    return jdbc.queryForList("select person_id from presence where inside and last_entry_at < ? limit ?", UUID.class,
        Timestamp.from(before), limit);
  }

  @Override
  public Optional<Instant> lastEntry(UUID personId) {
    return jdbc.query("select last_entry_at from presence where person_id = ?",
        (rs, i) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), personId).stream().findFirst();
  }
}

@Repository
class JdbcPermitSnapshots implements PermitSnapshots {
  private final JdbcTemplate jdbc;

  JdbcPermitSnapshots(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void upsert(UUID personId, PermitSnapshot p) {
    jdbc.update("insert into permit_snapshot(permit_id, person_id, type, status, valid_from, valid_to, region) "
            + "values (?,?,?,?,?,?,?) on conflict (permit_id) do update set status = excluded.status, "
            + "valid_from = excluded.valid_from, valid_to = excluded.valid_to, region = excluded.region",
        p.permitId(), personId, p.type(), p.status(), d(p.validFrom()), d(p.validTo()), p.region());
  }

  @Override
  public List<PermitSnapshot> of(UUID personId) {
    return jdbc.query("select * from permit_snapshot where person_id = ?",
        (rs, i) -> new PermitSnapshot(rs.getObject("permit_id", UUID.class), rs.getString("type"),
            rs.getString("status"), ld(rs.getDate("valid_from")), ld(rs.getDate("valid_to")), rs.getString("region")),
        personId);
  }

  private static Date d(LocalDate x) {
    return x == null ? null : Date.valueOf(x);
  }

  private static LocalDate ld(Date x) {
    return x == null ? null : x.toLocalDate();
  }
}
