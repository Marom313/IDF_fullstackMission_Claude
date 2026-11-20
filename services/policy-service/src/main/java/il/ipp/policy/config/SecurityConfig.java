package il.ipp.policy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Defence in depth: the gateway already validates JWTs, but the service checks roles again so a
 * request that bypasses the gateway (internal network) still cannot publish a policy.
 * <p>
 * {@code ipp.security.enabled=false} (default for local runs and tests) permits everything;
 * compose sets it to true together with the Keycloak issuer URI.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, @Value("${ipp.security.enabled:false}") boolean enabled) throws Exception {
        http.csrf(csrf -> csrf.disable())     // stateless JSON API behind the gateway, no cookies
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (!enabled) {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // service-to-service (permit-service). The gateway never routes /internal/**, so it is reachable
                // only inside the compose network. Production: mTLS or a client-credentials token (GAPS).
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/policies/evaluate", "/api/policies/required-fields").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/policies/**").hasAnyRole("POLICY_ADMIN", "CLERK", "AUDITOR", "SYS_ADMIN")
                .requestMatchers("/api/policies/**").hasAnyRole("POLICY_ADMIN", "SYS_ADMIN")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(keycloakRoles())));
        return http.build();
    }

    /** Keycloak puts realm roles in {@code realm_access.roles}; map them to Spring {@code ROLE_*} authorities. */
    static Converter<Jwt, AbstractAuthenticationToken> keycloakRoles() {
        return jwt -> {
            Object realmAccess = jwt.getClaims().get("realm_access");
            Collection<GrantedAuthority> authorities = List.of();
            if (realmAccess instanceof Map<?, ?> ra && ra.get("roles") instanceof Collection<?> roles) {
                authorities = roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList();
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
        };
    }
}
