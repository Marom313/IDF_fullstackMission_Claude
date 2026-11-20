package il.ipp.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/** No relational DB here (Mongo only) – exclude the DataSource that java-common's JDBC starter would add. */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class AuditApplication {
  public static void main(String[] args) {
    SpringApplication.run(AuditApplication.class, args);
  }
}
