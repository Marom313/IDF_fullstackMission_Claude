package il.ipp.common.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Poison-pill handling: 3 attempts with exponential backoff, then the record goes to &lt;topic&gt;.DLT
 * (same partition). Deserialization errors (ErrorHandlingDeserializer) go straight to the DLT.
 * Spring Boot applies a CommonErrorHandler bean to the default listener container factory.
 */
@Configuration
public class KafkaErrorConfig {

  @Bean
  CommonErrorHandler ippKafkaErrorHandler(KafkaTemplate<String, String> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template,
        (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));
    ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
    backOff.setMaxAttempts(2); // 1 initial + 2 retries = 3 attempts
    DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
    handler.addNotRetryableExceptions(IllegalArgumentException.class,
        com.fasterxml.jackson.core.JsonProcessingException.class);
    return handler;
  }
}
