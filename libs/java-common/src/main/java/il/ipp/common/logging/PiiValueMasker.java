package il.ipp.common.logging;

import com.fasterxml.jackson.core.JsonStreamContext;
import java.util.regex.Pattern;
import net.logstash.logback.mask.ValueMasker;

/**
 * Masks PII inside any logged string value: 9-digit ids (keeps last 2) and Israeli phone numbers.
 * Example: "check 123456789" → "check *******89".
 */
public class PiiValueMasker implements ValueMasker {
  /** 9 digits not part of a longer number, a decimal fraction or a timestamp (nanos are preceded by '.'). */
  private static final Pattern ID = Pattern.compile("(?<![\\d.:])\\d{7}(\\d{2})(?![\\dZ])");
  private static final Pattern PHONE = Pattern.compile("(?<!\\d)0(5\\d)-?\\d{3}-?\\d{4}(?!\\d)");

  @Override
  public Object mask(JsonStreamContext context, Object value) {
    if (value instanceof CharSequence cs) {
      return maskText(cs.toString());
    }
    return null; // null = leave value unchanged
  }

  public static String maskText(String s) {
    String out = ID.matcher(s).replaceAll("*******$1");
    return PHONE.matcher(out).replaceAll("0$1-***-****");
  }
}
