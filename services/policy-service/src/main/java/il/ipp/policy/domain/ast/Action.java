package il.ipp.policy.domain.ast;

import java.time.Duration;

/** Outcome of a policy branch (THEN / ELSE). */
public record Action(Outcome outcome, Duration validity, String reason) {

    public enum Outcome { APPROVE, REJECT, REFER_TO_CLERK }

    public static Action approve(Duration validity) { return new Action(Outcome.APPROVE, validity, null); }

    public static Action reject(String reason) { return new Action(Outcome.REJECT, null, reason); }

    public static Action refer(String reason) { return new Action(Outcome.REFER_TO_CLERK, null, reason); }

    /** Default ELSE when a policy does not declare one. */
    public static Action defaultElse() { return reject("תנאי המדיניות לא התקיימו"); }
}
