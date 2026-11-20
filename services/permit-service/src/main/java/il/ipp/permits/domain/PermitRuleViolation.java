package il.ipp.permits.domain;

/** Domain invariant violated (translated to 409/422 Problem Details by the web adapter). */
public class PermitRuleViolation extends RuntimeException {
  private final String code;

  public PermitRuleViolation(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
