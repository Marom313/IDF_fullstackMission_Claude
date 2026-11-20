package com.ipp.mockstate.domain;

/**
 * 9-digit identity numbers with the standard check digit used by Israeli-format IDs
 * (digits weighted 1,2,1,2…; two-digit products are summed; total must be divisible by 10).
 * <p>
 * Mock IDs are derived from a population index: body = {@value #BASE} + index, so the mock can tell
 * "exists in the synthetic population" from "well-formed but unknown" without storing anything.
 * The 4xxxxxxxx range is used deliberately so mock IDs are easy to recognise in logs.
 */
public final class IdNumbers {

    public static final int BASE = 40_000_000;

    private IdNumbers() { }

    public static boolean isValid(String id) {
        if (id == null || !id.matches("\\d{5,9}")) return false;
        String padded = "0".repeat(9 - id.length()) + id;
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int d = (padded.charAt(i) - '0') * ((i % 2) + 1);
            sum += d > 9 ? d - 9 : d;
        }
        return sum % 10 == 0;
    }

    public static String forIndex(int index) {
        if (index < 0 || index >= 10_000_000) throw new IllegalArgumentException("index out of range");
        String body = Integer.toString(BASE + index);           // 8 digits
        for (int check = 0; check <= 9; check++) {
            String candidate = body + check;
            if (isValid(candidate)) return candidate;
        }
        throw new IllegalStateException("unreachable");
    }

    /** Population index encoded in a mock ID, or -1 if the ID is not from the mock range. */
    public static int indexOf(String id) {
        if (!isValid(id) || id.length() != 9) return -1;
        int body = Integer.parseInt(id.substring(0, 8));
        int index = body - BASE;
        return index >= 0 && index < 10_000_000 ? index : -1;
    }
}
