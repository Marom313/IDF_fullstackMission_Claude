package il.ipp.common.web;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** RFC 7807 Problem Details for every error, with business code + traceId. */
@RestControllerAdvice
public class ProblemDetailsAdvice {
  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  @ExceptionHandler(DomainException.class)
  ProblemDetail domain(DomainException e) {
    return problem(HttpStatus.valueOf(e.status()), e.code(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException e) {
    ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
    Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
    p.setProperty("fields", fields);
    return p;
  }

  @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
      IllegalArgumentException.class})
  ProblemDetail badRequest(Exception e) {
    return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ProblemDetail optimistic(OptimisticLockingFailureException e) {
    return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
        "The record was changed by someone else. Reload and try again.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail denied(AccessDeniedException e) {
    return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception e) {
    log.error("unhandled error", e);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
  }

  static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setType(URI.create("urn:ipp:error:" + code.toLowerCase()));
    p.setTitle(code);
    p.setProperty("code", code);
    p.setProperty("traceId", CorrelationId.current());
    return p;
  }
}
