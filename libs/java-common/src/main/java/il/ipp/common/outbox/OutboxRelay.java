package il.ipp.common.outbox;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the outbox and publishes to Kafka. FOR UPDATE SKIP LOCKED lets several instances
 * (docker compose --scale) relay in parallel without publishing the same row twice.
 */
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH = 200;
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final TransactionTemplate tx;

  public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.tx = tx;
  }

  record Row(String id, String topic, String key, String payload) {}

  @Scheduled(fixedDelayString = "${ipp.outbox.poll-ms:500}")
  public void relay() {
    int sent;
    do {
      Integer n = tx.execute(status -> {
        List<Row> rows = jdbc.query(
            "select id::text, topic, msg_key, payload::text from outbox where sent_at is null "
                + "order by created_at limit " + BATCH + " for update skip locked",
            (rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        if (rows.isEmpty()) {
          return 0;
        }
        try {
          List<CompletableFuture<?>> futures =
              rows.stream().<CompletableFuture<?>>map(r -> kafka.send(r.topic(), r.key(), r.payload())).toList();
          CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
          log.warn("outbox relay failed, will retry: {}", e.toString());
          status.setRollbackOnly();
          return 0;
        }
        jdbc.batchUpdate("update outbox set sent_at = now() where id = ?::uuid",
            rows.stream().map(r -> new Object[] {r.id()}).toList());
        return rows.size();
      });
      sent = n == null ? 0 : n;
    } while (sent == BATCH);
  }

  @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
  public void purgeSent() {
    jdbc.update("delete from outbox where sent_at < now() - interval '1 day'");
  }
}
