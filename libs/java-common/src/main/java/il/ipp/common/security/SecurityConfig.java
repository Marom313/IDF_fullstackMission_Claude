package il.ipp.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every service is also a resource server (defense in depth – not only the gateway checks JWTs).
 * JWKS is fetched from the internal Keycloak URL, but the issuer is validated against the public one.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  SecurityFilterChain ippSecurity(HttpSecurity http,
                                  @Value("${ipp.security.public-paths:}") String[] publicPaths) throws Exception {
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> {
          a.requestMatchers("/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**",
              "/swagger-ui/**", "/swagger-ui.html").permitAll();
          if (publicPaths.length > 0) {
            a.requestMatchers(publicPaths).permitAll();
          }
          a.anyRequest().authenticated();
        })
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(keycloakRoles())));
    return http.build();
  }

  @Bean
  @ConditionalOnMissingBean
  JwtDecoder jwtDecoder(@Value("${ipp.security.jwk-set-uri}") String jwks,
                        @Value("${ipp.security.issuer}") String issuer) {
    NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer)));
    return d;
  }

  /** Keycloak puts realm roles in realm_access.roles → map to ROLE_X authorities. */
  static JwtAuthenticationConverter keycloakRoles() {
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
    return c;
  }
}
