package il.ipp.registry.adapters.out.persistence;

import il.ipp.registry.domain.Person;
import il.ipp.registry.domain.RegistryPorts.PersonRepository;
import il.ipp.registry.domain.SecurityStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPersonRepository implements PersonRepository {
  private static final String COLS = "id, national_id, first_name, last_name, birth_date, magnetic_card, "
      + "magnetic_card_valid_to, security_status, region, employer_id, username, phone";
  private final JdbcTemplate jdbc;

  JdbcPersonRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Person> findById(UUID id) {
    return jdbc.query("select " + COLS + " from person where id = ?", this::map, id).stream().findFirst();
  }

  @Override
  public Optional<Person> findByNationalId(String nationalId) {
    return jdbc.query("select " + COLS + " from person where national_id = ?", this::map, nationalId)
        .stream().findFirst();
  }

  @Override
  public Optional<Person> findByUsername(String username) {
    return jdbc.query("select " + COLS + " from person where username = ?", this::map, username).stream().findFirst();
  }

  /** Keyset pagination (WHERE id &gt; :after ORDER BY id) – stable and O(log n) unlike OFFSET. */
  @Override
  public List<Person> search(String region, UUID employerId, UUID after, int limit) {
    StringBuilder sql = new StringBuilder("select " + COLS + " from person where 1=1");
    List<Object> args = new ArrayList<>();
    if (region != null && !region.isBlank()) {
      sql.append(" and region = ?");
      args.add(region);
    }
    if (employerId != null) {
      sql.append(" and employer_id = ?");
      args.add(employerId);
    }
    if (after != null) {
      sql.append(" and id > ?");
      args.add(after);
    }
    sql.append(" order by id limit ?");
    args.add(limit);
    return jdbc.query(sql.toString(), this::map, args.toArray());
  }

  @Override
  public long count() {
    Long n = jdbc.queryForObject("select count(*) from person", Long.class);
    return n == null ? 0 : n;
  }

  @Override
  public void insertAll(List<Person> persons) {
    jdbc.batchUpdate("insert into person(" + COLS + ") values (?,?,?,?,?,?,?,?,?,?,?,?)",
        persons.stream().map(p -> new Object[] {p.id(), p.nationalId(), p.firstName(), p.lastName(),
            Date.valueOf(p.birthDate()), p.magneticCard(), Date.valueOf(p.magneticCardValidTo()),
            p.securityStatus().name(), p.region(), p.employerId(), p.username(), p.phone()}).toList());
  }

  private Person map(ResultSet rs, int i) throws SQLException {
    return new Person(rs.getObject("id", UUID.class), rs.getString("national_id"), rs.getString("first_name"),
        rs.getString("last_name"), rs.getDate("birth_date").toLocalDate(), rs.getString("magnetic_card"),
        rs.getDate("magnetic_card_valid_to").toLocalDate(), SecurityStatus.valueOf(rs.getString("security_status")),
        rs.getString("region"), rs.getObject("employer_id", UUID.class), rs.getString("username"),
        rs.getString("phone"));
  }
}
