package il.ipp.gateway;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Edge of the backend: validates Keycloak JWTs, rate-limits per user (Redis token bucket),
 * propagates X-Correlation-Id, routes /api/<service>/** and fans out SSE events.
 */
@SpringBootApplication
public class GatewayApplication {
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }

  @Bean
  SecurityWebFilterChain security(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(a -> a
            .pathMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
            // public key for offline QR verification on terminals
            .pathMatchers("/api/permits/keys/qr").permitAll()
            .anyExchange().authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(roles())))
        .build();
  }

  @Bean
  ReactiveJwtDecoder jwtDecoder(@Value("${ipp.security.jwk-set-uri}") String jwks,
                                @Value("${ipp.security.issuer}") String issuer) {
    NimbusReactiveJwtDecoder d = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer)));
    return d;
  }

  static Converter<Jwt, Mono<AbstractAuthenticationToken>> roles() {
    JwtAuthenticationConverter c = new JwtAuthenticationConverter();
    c.setJwtGrantedAuthoritiesConverter(jwt -> {
      Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
      Object roles = realm == null ? null : realm.get("roles");
      if (!(roles instanceof Collection<?> list)) {
        return List.<GrantedAuthority>of();
      }
      return list.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
    });
    c.setPrincipalClaimName("preferred_username");
    return new ReactiveJwtAuthenticationConverterAdapter(c);
  }

  /** Rate-limit key: the authenticated user (falls back to client IP). */
  @Bean
  KeyResolver userKeyResolver() {
    return exchange -> exchange.getPrincipal().map(p -> "u:" + p.getName())
        .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + (exchange.getRequest().getRemoteAddress() == null ? "unknown"
            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress())));
  }

  /** Correlation id: accept a safe incoming id or create one; pass downstream and echo to the client. */
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  GlobalFilter correlationId() {
    return (exchange, chain) -> {
      String id = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
      String safe = id != null && SAFE_ID.matcher(id).matches() ? id : UUID.randomUUID().toString();
      exchange.getResponse().getHeaders().set("X-Correlation-Id", safe);
      return chain.filter(exchange.mutate()
          .request(r -> r.headers(h -> h.set("X-Correlation-Id", safe))).build());
    };
  }
}
