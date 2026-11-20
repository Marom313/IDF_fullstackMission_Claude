package il.ipp.common.seed;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic "mock world" shared by all services (fixed seed → identical data on every run).
 * Each service seeds only the entities it owns, but they all agree on ids and relations
 * without calling each other. ALL DATA IS SYNTHETIC; national ids use the reserved 90/98 prefixes.
 *
 * Demo persons (memorable ids):
 *   0 → 900000001 worker1, employed by employer1, active work permit  → GREEN
 *   1 → 900000002 driver1, active driver permit                        → GREEN
 *   2 → 900000003 active entry-ban sanction                            → RED
 *   3 → 900000004 security status PENDING                              → REFER_TO_CLERK
 *   4 → 900000005 no permit at all                                     → RED
 */
public final class SeedCatalog {
  public static final long SEED = 20261120L;
  public static final int PERSONS = 5000;
  public static final int EMPLOYERS = 200;
  public static final List<String> REGIONS =
      List.of("JERUSALEM", "TEL_AVIV", "CENTER", "SHARON", "HAIFA", "NORTH", "SHFELA", "SOUTH");
  public static final List<String> SECTORS = List.of("CONSTRUCTION", "AGRICULTURE", "INDUSTRY", "SERVICES");
  public static final List<String> GATES =
      List.of("GATE-NORTH-1", "GATE-NORTH-2", "GATE-CENTER-1", "GATE-CENTER-2", "GATE-SOUTH-1");

  private SeedCatalog() {}

  public static UUID personId(int i) {
    return UUID.nameUUIDFromBytes(("ipp-person-" + i).getBytes(StandardCharsets.UTF_8));
  }

  public static UUID employerId(int j) {
    return UUID.nameUUIDFromBytes(("ipp-employer-" + j).getBytes(StandardCharsets.UTF_8));
  }

  public static String nationalId(int i) {
    return i < 5 ? "90000000" + (i + 1) : String.format("98%07d", i);
  }

  /** -1 = not employed (≈40%). */
  public static int employerIndexOf(int i) {
    return i % 10 < 6 ? (i * 7) % EMPLOYERS : -1;
  }

  public static String employerSector(int j) {
    return SECTORS.get(j % SECTORS.size());
  }

  public static String employerRegion(int j) {
    return REGIONS.get(j % REGIONS.size());
  }

  public static String personRegion(int i) {
    int e = employerIndexOf(i);
    return e >= 0 ? employerRegion(e) : REGIONS.get(i % REGIONS.size());
  }

  public static String securityStatus(int i) {
    if (i == 3 || i % 50 == 7) {
      return "PENDING";
    }
    if (i > 4 && i % 97 == 11) {
      return "DENIED";
    }
    return "CLEAR";
  }

  /** Demo usernames linked to seeded entities (ABAC uses preferred_username). */
  public static String personUsername(int i) {
    return switch (i) {
      case 0 -> "worker1";
      case 1 -> "driver1";
      default -> null;
    };
  }

  public static String employerUsername(int j) {
    return j == 0 ? "employer1" : null;
  }

  public static boolean isDriver(int i) {
    return i == 1 || (i > 4 && i % 25 == 3);
  }
}
