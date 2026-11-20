package il.ipp.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import il.ipp.common.idempotency.IdempotencyStore;
import il.ipp.common.inbox.InboxGuard;
import il.ipp.common.kafka.KafkaErrorConfig;
import il.ipp.common.outbox.OutboxRelay;
import il.ipp.common.outbox.OutboxWriter;
import il.ipp.common.security.SecurityConfig;
import il.ipp.common.web.CorrelationIdFilter;
import il.ipp.common.web.ProblemDetailsAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared building blocks, auto-configured into every servlet service that depends on java-common. */
@AutoConfiguration(afterName = {
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
    "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({ProblemDetailsAdvice.class, SecurityConfig.class, KafkaErrorConfig.class})
@EnableScheduling
public class CommonAutoConfiguration {

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    var reg = new FilterRegistrationBean<>(new CorrelationIdFilter());
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return reg;
  }

  @Bean
  @ConditionalOnBean(JdbcTemplate.class)
  OutboxWriter outboxWriter(JdbcTemplate jdbc, ObjectMapper json) {
    return new OutboxWriter(jdbc, json);
  }

  @Bean
  @ConditionalOnBean(JdbcTemplate.class)
  InboxGuard inboxGuard(JdbcTemplate jdbc) {
    return new InboxGuard(jdbc);
  }

  @Bean
  @ConditionalOnBean(JdbcTemplate.class)
  IdempotencyStore idempotencyStore(JdbcTemplate jdbc) {
    return new IdempotencyStore(jdbc);
  }

  @Bean
  @ConditionalOnBean(JdbcTemplate.class)
  @ConditionalOnProperty(name = "ipp.outbox.enabled", havingValue = "true", matchIfMissing = true)
  OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx) {
    return new OutboxRelay(jdbc, kafka, tx);
  }
}
