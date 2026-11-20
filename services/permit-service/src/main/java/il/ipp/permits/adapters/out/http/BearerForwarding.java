package il.ipp.permits.adapters.out.http;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Forwards the caller's JWT to downstream services (zero-trust: every hop re-validates the token). */
enum BearerForwarding implements ClientHttpRequestInterceptor {
  INSTANCE;

  @Override
  public ClientHttpResponse intercept(HttpRequest req, byte[] body, ClientHttpRequestExecution exec)
      throws IOException {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
      req.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue());
    }
    return exec.execute(req, body);
  }
}
