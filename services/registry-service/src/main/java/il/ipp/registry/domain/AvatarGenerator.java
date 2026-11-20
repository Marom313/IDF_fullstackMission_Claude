package il.ipp.registry.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Generates a generic silhouette avatar (SVG). NEVER a photo of a real person.
 * Deterministic per person id so the same person always gets the same picture.
 */
public final class AvatarGenerator {
  private static final String[] BG = {"#1f3a5f", "#4b5d3a", "#8a6d3b", "#3d4f63", "#5a4a3a", "#2e4b4b"};
  private static final String[] FG = {"#c9d6e3", "#d8cfae", "#e6d3a7", "#cfd8dc", "#e0c9a6", "#b8d1cf"};

  private AvatarGenerator() {}

  public static byte[] svgFor(UUID personId) {
    int h = Math.abs(personId.hashCode());
    String bg = BG[h % BG.length];
    String fg = FG[(h / 7) % FG.length];
    String svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 150" width="240" height="300" role="img" aria-label="silhouette">
          <rect width="120" height="150" fill="%s"/>
          <circle cx="60" cy="56" r="26" fill="%s"/>
          <path d="M14 150 C14 108 34 92 60 92 C86 92 106 108 106 150 Z" fill="%s"/>
          <text x="60" y="144" font-size="8" text-anchor="middle" fill="%s" font-family="sans-serif">SPECIMEN</text>
        </svg>
        """.formatted(bg, fg, fg, bg);
    return svg.getBytes(StandardCharsets.UTF_8);
  }
}
