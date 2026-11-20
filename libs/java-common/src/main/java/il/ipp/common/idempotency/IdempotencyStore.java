package il.ipp.common.idempotency;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stores the created resource id per Idempotency-Key, so a retried POST returns the same resource. */
public class IdempotencyStore {
  private final JdbcTemplate jdbc;

  public IdempotencyStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<String> find(String key, String scope) {
    return jdbc.query("select resource_id from idempotency_keys where idem_key = ? and scope = ?",
        (rs, i) -> rs.getString(1), key, scope).stream().findFirst();
  }

  /** @return true if stored; false if a concurrent request already stored this key. */
  public boolean remember(String key, String scope, String resourceId) {
    return jdbc.update("insert into idempotency_keys(idem_key, scope, resource_id, created_at) "
        + "values (?, ?, ?, now()) on conflict do nothing", key, scope, resourceId) == 1;
  }
}
