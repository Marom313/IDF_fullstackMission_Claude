package il.ipp.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * SSE fan-out: each gateway instance consumes status events with its OWN consumer group
 * (anonymous/unique group id) so every instance sees every event, then pushes a small
 * "invalidate" message to connected browsers. The browser re-fetches through TanStack Query
 * (data never travels over SSE – only "what changed", so no data leaks to unauthorized users).
 */
@RestController
class EventStream {
  private static final Set<String> STAFF = Set.of("ROLE_CLERK", "ROLE_SYS_ADMIN", "ROLE_ENFORCEMENT_OFFICER",
      "ROLE_AUDITOR", "ROLE_CROSSING_OFFICER", "ROLE_EMPLOYER");
  private final Sinks.Many<ChangeEvent> sink = Sinks.many().multicast().directBestEffort();
  private final ObjectMapper json;

  EventStream(ObjectMapper json) {
    this.json = json;
  }

  record ChangeEvent(String kind, String entityId, String personId, String status) {}

  @KafkaListener(topics = {"permits.permit.status-changed.v1", "enforcement.sanction.imposed.v1",
      "enforcement.sanction.lifted.v1"}, groupId = "gateway-sse-#{T(java.util.UUID).randomUUID()}",
      properties = {"auto.offset.reset=latest"})
  void onEvent(String message) throws Exception {
    JsonNode env = json.readTree(message);
    JsonNode p = env.path("payload");
    String type = env.path("type").asText();
    String kind = type.startsWith("Permit") ? "permit" : "sanction";
    sink.tryEmitNext(new ChangeEvent(kind, p.path(kind.equals("permit") ? "permitId" : "caseId").asText(null),
        env.path("personId").asText(null), p.path("status").asText(type)));
  }

  @GetMapping(path = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  Flux<ServerSentEvent<ChangeEvent>> stream(Authentication auth) {
    boolean staff = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(STAFF::contains);
    Flux<ServerSentEvent<ChangeEvent>> events = sink.asFlux()
        // non-staff (worker/driver) only get a generic "permit changed" ping without ids
        .map(e -> staff ? e : new ChangeEvent(e.kind(), null, null, null))
        .map(e -> ServerSentEvent.builder(e).event("change").build());
    Flux<ServerSentEvent<ChangeEvent>> heartbeat = Flux.interval(Duration.ofSeconds(25))
        .map(i -> ServerSentEvent.<ChangeEvent>builder().comment("keep-alive").build());
    return Flux.merge(events, heartbeat);
  }
}
