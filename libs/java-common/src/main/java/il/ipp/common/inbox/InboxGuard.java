package il.ipp.common.inbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer. Call inside the handler's transaction:
 * returns false if this (messageId, consumer) was already processed, so the handler skips it.
 */
public class InboxGuard {
  private final JdbcTemplate jdbc;

  public InboxGuard(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean firstTime(String messageId, String consumer) {
    return jdbc.update(
            "insert into inbox(message_id, consumer, processed_at) values (?, ?, now()) on conflict do nothing",
            messageId, consumer) == 1;
  }
}
