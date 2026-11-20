package il.ipp.common.web;

/**
 * Business error with a stable code (e.g. PERMIT_INVALID_TRANSITION) and an HTTP status.
 * Lives in common/web on purpose: domain packages throw their own exceptions, adapters translate.
 */
public class DomainException extends RuntimeException {
  private final String code;
  private final int status;

  public DomainException(String code, int status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public static DomainException notFound(String what) {
    return new DomainException("NOT_FOUND", 404, what + " not found");
  }

  public static DomainException conflict(String code, String message) {
    return new DomainException(code, 409, message);
  }

  public static DomainException forbidden(String message) {
    return new DomainException("FORBIDDEN", 403, message);
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
