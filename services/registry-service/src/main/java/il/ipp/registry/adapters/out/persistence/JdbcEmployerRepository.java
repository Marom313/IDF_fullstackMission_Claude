package il.ipp.registry.adapters.out.persistence;

import il.ipp.registry.domain.Employer;
import il.ipp.registry.domain.RegistryPorts.EmployerRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmployerRepository implements EmployerRepository {
  private static final String COLS = "id, name, business_number, sector, region, username";
  private final JdbcTemplate jdbc;

  JdbcEmployerRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Employer> findById(UUID id) {
    return jdbc.query("select " + COLS + " from employer where id = ?", this::map, id).stream().findFirst();
  }

  @Override
  public Optional<Employer> findByUsername(String username) {
    return jdbc.query("select " + COLS + " from employer where username = ?", this::map, username)
        .stream().findFirst();
  }

  @Override
  public List<Employer> all() {
    return jdbc.query("select " + COLS + " from employer order by name", this::map);
  }

  @Override
  public void insertAll(List<Employer> employers) {
    jdbc.batchUpdate("insert into employer(" + COLS + ") values (?,?,?,?,?,?)",
        employers.stream().map(e -> new Object[] {e.id(), e.name(), e.businessNumber(), e.sector(), e.region(),
            e.username()}).toList());
  }

  private Employer map(ResultSet rs, int i) throws SQLException {
    return new Employer(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("business_number"),
        rs.getString("sector"), rs.getString("region"), rs.getString("username"));
  }
}
