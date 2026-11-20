package com.ipp.policy.domain.dsl;

import java.time.Duration;

/** DSL durations: {@code 90d}, {@code 12h}, {@code 30m}. */
public final class Durations {

    private Durations() { }

    public static Duration parse(String text) {
        long amount = Long.parseLong(text.substring(0, text.length() - 1));
        return switch (text.charAt(text.length() - 1)) {
            case 'd' -> Duration.ofDays(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'm' -> Duration.ofMinutes(amount);
            default -> throw new IllegalArgumentException("Bad duration: " + text);
        };
    }

    /** Inverse of {@link #parse}: picks the largest exact unit. */
    public static String format(Duration d) {
        long minutes = d.toMinutes();
        if (minutes % (24 * 60) == 0) return (minutes / (24 * 60)) + "d";
        if (minutes % 60 == 0) return (minutes / 60) + "h";
        return minutes + "m";
    }
}
