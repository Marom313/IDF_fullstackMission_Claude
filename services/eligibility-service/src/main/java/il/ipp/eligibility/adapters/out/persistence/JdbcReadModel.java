package il.ipp.eligibility.adapters.out.persistence;

import il.ipp.eligibility.domain.Eligibility.PermitView;
import il.ipp.eligibility.domain.Eligibility.SanctionView;
import il.ipp.eligibility.domain.Eligibility.View;
import il.ipp.eligibility.domain.EligibilityPorts.ReadModel;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Three tables keyed by person_id so events from different topics can arrive in any order
 * (a permit event before the person event just creates the permit row first).
 */
@Repository
class JdbcReadModel implements ReadModel {
  private final JdbcTemplate jdbc;

  JdbcReadModel(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<View> load(UUID personId) {
    var person = jdbc.query("select national_id, full_name, security_status, region from elig_person where person_id = ?",
        (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, personId);
    if (person.isEmpty()) {
      return Optional.empty();
    }
    List<PermitView> permits = jdbc.query("select permit_id, type, status, valid_from, valid_to, region, policy_version "
            + "from elig_permit where person_id = ? order by valid_to desc nulls last limit 20",
        (rs, i) -> new PermitView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), date(rs.getDate(4)),
            date(rs.getDate(5)), rs.getString(6), rs.getString(7)), personId);
    List<SanctionView> sanctions = jdbc.query("select sanction_id, type, until_date, reason from elig_sanction "
            + "where person_id = ? and active", (rs, i) -> new SanctionView(rs.getObject(1, UUID.class), rs.getString(2),
        date(rs.getDate(3)), rs.getString(4)), personId);
    String[] p = person.get(0);
    return Optional.of(new View(personId, p[0], p[1], p[2], p[3], permits, sanctions));
  }

  @Override
  public Optional<UUID> personIdOf(String nationalId) {
    return jdbc.queryForList("select person_id from elig_person where national_id = ?", UUID.class, nationalId)
        .stream().findFirst();
  }

  @Override
  public void upsertPerson(UUID personId, String nationalId, String fullName, String securityStatus, String region) {
    jdbc.update("insert into elig_person(person_id, national_id, full_name, security_status, region, updated_at) "
        + "values (?,?,?,?,?, now()) on conflict (person_id) do update set national_id = excluded.national_id, "
        + "full_name = excluded.full_name, security_status = excluded.security_status, region = excluded.region, "
        + "updated_at = now()", personId, nationalId, fullName, securityStatus, region);
  }

  @Override
  public void upsertPermit(UUID permitId, UUID personId, String type, String status, LocalDate validFrom,
                           LocalDate validTo, String region, String policyVersion) {
    jdbc.update("insert into elig_permit(permit_id, person_id, type, status, valid_from, valid_to, region, "
            + "policy_version, updated_at) values (?,?,?,?,?,?,?,?, now()) on conflict (permit_id) do update set "
            + "status = excluded.status, valid_from = excluded.valid_from, valid_to = excluded.valid_to, "
            + "region = excluded.region, policy_version = excluded.policy_version, updated_at = now()",
        permitId, personId, type, status, sql(validFrom), sql(validTo), region, policyVersion);
  }

  @Override
  public void upsertSanction(UUID sanctionId, UUID personId, String type, LocalDate until, String reason,
                             boolean active) {
    jdbc.update("insert into elig_sanction(sanction_id, person_id, type, until_date, reason, active, updated_at) "
            + "values (?,?,?,?,?,?, now()) on conflict (sanction_id) do update set active = excluded.active, "
            + "until_date = excluded.until_date, updated_at = now()",
        sanctionId, personId, type, sql(until), reason, active);
  }

  private static LocalDate date(Date d) {
    return d == null ? null : d.toLocalDate();
  }

  private static Date sql(LocalDate d) {
    return d == null ? null : Date.valueOf(d);
  }
}
