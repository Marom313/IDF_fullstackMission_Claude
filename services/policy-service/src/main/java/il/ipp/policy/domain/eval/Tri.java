package il.ipp.policy.domain.eval;

/**
 * Three-valued logic (Kleene). UNKNOWN appears when a fact is missing, e.g. the state registry was
 * unavailable. A policy must never silently approve on missing data, so UNKNOWN routes the case to a clerk.
 */
public enum Tri {
    TRUE, FALSE, UNKNOWN;

    public static Tri of(boolean b) { return b ? TRUE : FALSE; }

    public Tri and(Tri o) {
        if (this == FALSE || o == FALSE) return FALSE;
        if (this == UNKNOWN || o == UNKNOWN) return UNKNOWN;
        return TRUE;
    }

    public Tri or(Tri o) {
        if (this == TRUE || o == TRUE) return TRUE;
        if (this == UNKNOWN || o == UNKNOWN) return UNKNOWN;
        return FALSE;
    }

    public Tri not() {
        return switch (this) {
            case TRUE -> FALSE;
            case FALSE -> TRUE;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
