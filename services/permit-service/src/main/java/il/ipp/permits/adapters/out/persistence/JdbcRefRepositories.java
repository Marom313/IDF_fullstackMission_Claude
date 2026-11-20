package il.ipp.permits.adapters.out.persistence;

import il.ipp.permits.domain.PermitPorts.License;
import il.ipp.permits.domain.PermitPorts.LicenseRepository;
import il.ipp.permits.domain.PermitPorts.PersonRef;
import il.ipp.permits.domain.PermitPorts.PersonRefRepository;
import il.ipp.permits.domain.PermitPorts.SectorQuota;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPersonRefRepository implements PersonRefRepository {
  private final JdbcTemplate jdbc;

  JdbcPersonRefRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<PersonRef> find(UUID personId) {
    return jdbc.query("select * from person_ref where person_id = ?", JdbcPersonRefRepository::map, personId)
        .stream().findFirst();
  }

  @Override
  public Optional<PersonRef> findByNationalId(String nationalId) {
    return jdbc.query("select * from person_ref where national_id = ?", JdbcPersonRefRepository::map, nationalId)
        .stream().findFirst();
  }

  @Override
  public void upsert(PersonRef r) {
    jdbc.update("insert into person_ref(person_id, national_id, full_name, birth_date, security_status, region, "
            + "employer_id) values (?,?,?,?,?,?,?) on conflict (person_id) do update set national_id = excluded.national_id, "
            + "full_name = excluded.full_name, birth_date = excluded.birth_date, security_status = excluded.security_status, "
            + "region = excluded.region, employer_id = excluded.employer_id",
        r.personId(), r.nationalId(), r.fullName(), r.birthDate() == null ? null : Date.valueOf(r.birthDate()),
        r.securityStatus(), r.region(), r.employerId());
  }

  static PersonRef map(ResultSet rs, int i) throws SQLException {
    Date bd = rs.getDate("birth_date");
    return new PersonRef(rs.getObject("person_id", UUID.class), rs.getString("national_id"),
        rs.getString("full_name"), bd == null ? null : bd.toLocalDate(), rs.getString("security_status"),
        rs.getString("region"), rs.getObject("employer_id", UUID.class));
  }
}

@Repository
class JdbcLicenseRepository implements LicenseRepository {
  private final JdbcTemplate jdbc;

  JdbcLicenseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<License> findById(UUID id) {
    return jdbc.query("select * from employment_license where id = ?", JdbcLicenseRepository::map, id)
        .stream().findFirst();
  }

  @Override
  public Optional<License> byEmployer(UUID employerId) {
    return jdbc.query("select * from employment_license where employer_id = ? and status = 'ACTIVE'",
        JdbcLicenseRepository::map, employerId).stream().findFirst();
  }

  @Override
  public List<SectorQuota> quotas() {
    return jdbc.query("select sector, total, used from sector_quota order by sector",
        (rs, i) -> new SectorQuota(rs.getString(1), rs.getInt(2), rs.getInt(3)));
  }

  /**
   * Conditional UPDATE = atomic check-and-decrement (no lost update even with 3 instances).
   * Both rows must succeed, inside the caller's transaction.
   */
  @Override
  public boolean consumeQuota(UUID licenseId, String sector) {
    int s = jdbc.update("update sector_quota set used = used + 1 where sector = ? and used < total", sector);
    if (s == 0) {
      return false;
    }
    int l = jdbc.update("update employment_license set workers_assigned = workers_assigned + 1 "
        + "where id = ? and workers_assigned < workers_quota", licenseId);
    if (l == 0) {
      throw new IllegalStateException("LICENSE_QUOTA_EXHAUSTED");
    }
    return true;
  }

  static License map(ResultSet rs, int i) throws SQLException {
    return new License(rs.getObject("id", UUID.class), rs.getObject("employer_id", UUID.class), rs.getString("sector"),
        rs.getInt("workers_quota"), rs.getInt("workers_assigned"), rs.getDate("valid_to").toLocalDate(),
        rs.getString("status"));
  }
}
