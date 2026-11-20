package il.ipp.policy.domain.ast;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A fully parsed policy. Immutable.
 *
 * @param appliesTo      optional scope condition (null = applies to everything)
 * @param requiredFields form fields that must be present before evaluation (drives dynamic forms in the UI)
 * @param elseAction     never null: {@link Action#defaultElse()} when the source has no ELSE
 */
public record PolicyDefinition(
        String name,
        int version,
        LocalDate effectiveFrom,
        Node appliesTo,
        List<String> requiredFields,
        Node when,
        Action thenAction,
        Action elseAction) {

    public PolicyDefinition {
        Objects.requireNonNull(name);
        Objects.requireNonNull(when);
        Objects.requireNonNull(thenAction);
        requiredFields = requiredFields == null ? List.of() : List.copyOf(requiredFields);
        elseAction = elseAction == null ? Action.defaultElse() : elseAction;
    }
}
