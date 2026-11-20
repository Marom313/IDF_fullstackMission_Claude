package il.ipp.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationIdFilter extends OncePerRequestFilter {
  /** Only accept safe ids from the outside (log-injection guard). */
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = req.getHeader(CorrelationId.HEADER);
    if (id == null || !SAFE.matcher(id).matches()) {
      id = UUID.randomUUID().toString();
    }
    MDC.put(CorrelationId.MDC_KEY, id);
    res.setHeader(CorrelationId.HEADER, id);
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove(CorrelationId.MDC_KEY);
    }
  }
}
