package il.ipp.common.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, extracted from the Keycloak JWT. */
public record CurrentUser(String username, Set<String> roles) {

  public static CurrentUser get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return new CurrentUser("system", Set.of());
    }
    Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
        .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toSet());
    return new CurrentUser(jwt.getClaimAsString("preferred_username"), roles);
  }

  public boolean has(String role) {
    return roles.contains(role);
  }

  public boolean hasAny(String... rs) {
    for (String r : rs) {
      if (roles.contains(r)) {
        return true;
      }
    }
    return false;
  }
}
