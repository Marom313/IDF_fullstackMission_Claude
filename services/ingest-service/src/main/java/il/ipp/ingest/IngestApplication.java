package il.ipp.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/** File-based pipeline (no relational DB): dedup ledger = archive directory keyed by SHA-256. */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class IngestApplication {
  public static void main(String[] args) {
    SpringApplication.run(IngestApplication.class, args);
  }
}
