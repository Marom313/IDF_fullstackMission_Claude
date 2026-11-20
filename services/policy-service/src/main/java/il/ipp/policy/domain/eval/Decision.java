package il.ipp.policy.domain.eval;

import java.time.Duration;
import java.util.List;

/**
 * Result of evaluating one case against one policy.
 *
 * @param outcome       APPROVE / REJECT / REFER_TO_CLERK, or NOT_APPLICABLE / INCOMPLETE
 * @param missingFields required form fields that were absent (outcome INCOMPLETE)
 * @param reasons       short human-readable reasons (Hebrew), derived from the trace
 * @param trace         full explanation tree (null when the condition was never evaluated)
 */
public record Decision(
        Outcome outcome,
        String policyName,
        int policyVersion,
        Duration validity,
        List<String> missingFields,
        List<String> reasons,
        Trace trace) {

    public enum Outcome { APPROVE, REJECT, REFER_TO_CLERK, NOT_APPLICABLE, INCOMPLETE }

    public Decision {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
