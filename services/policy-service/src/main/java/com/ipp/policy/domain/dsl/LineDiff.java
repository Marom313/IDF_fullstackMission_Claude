package com.ipp.policy.domain.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal line diff (LCS based) between two canonical policy texts.
 * Policies are small (tens of lines), so O(n*m) is fine and avoids an extra dependency
 * (one less artifact to mirror into the closed network).
 */
public final class LineDiff {

    public enum Kind { SAME, ADDED, REMOVED }

    public record Line(Kind kind, String text) { }

    public static List<Line> diff(String before, String after) {
        String[] a = before.split("\n", -1);
        String[] b = after.split("\n", -1);
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Line> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.length && j < b.length) {
            if (a[i].equals(b[j])) { out.add(new Line(Kind.SAME, a[i])); i++; j++; }
            else if (lcs[i + 1][j] >= lcs[i][j + 1]) { out.add(new Line(Kind.REMOVED, a[i++])); }
            else { out.add(new Line(Kind.ADDED, b[j++])); }
        }
        while (i < a.length) out.add(new Line(Kind.REMOVED, a[i++]));
        while (j < b.length) out.add(new Line(Kind.ADDED, b[j++]));
        // drop the empty trailing line produced by the final '\n'
        if (!out.isEmpty() && out.getLast().text().isEmpty() && out.getLast().kind() == Kind.SAME) out.removeLast();
        return out;
    }

    private LineDiff() { }
}
