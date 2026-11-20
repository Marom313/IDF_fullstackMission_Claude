package il.ipp.common.web;

import java.util.UUID;
import org.slf4j.MDC;

/** Correlation id travels gateway → services → Kafka events → logs (MDC key "traceId"). */
public final class CorrelationId {
  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "traceId";

  private CorrelationId() {}

  public static String current() {
    String id = MDC.get(MDC_KEY);
    return id != null ? id : UUID.randomUUID().toString();
  }
}
