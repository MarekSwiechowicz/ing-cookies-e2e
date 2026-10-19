package pl.ing.cookies;

import java.util.Arrays;

/**
 * Values of the {@code cookiePolicyGDPR} consent cookie used by ing.pl.
 * The bank encodes the combination of consents as a single digit (it is not a bit mask):
 * technical cookies are always on, so only analytical and marketing ones differ.
 */
public enum ConsentLevel {
  TECHNICAL_ONLY("1", false, false),
  TECHNICAL_AND_MARKETING("2", false, true),
  TECHNICAL_AND_ANALYTICAL("3", true, false),
  ALL("4", true, true);

  private final String cookieValue;
  private final boolean analytical;
  private final boolean marketing;

  ConsentLevel(String cookieValue, boolean analytical, boolean marketing) {
    this.cookieValue = cookieValue;
    this.analytical = analytical;
    this.marketing = marketing;
  }

  public String cookieValue() {
    return cookieValue;
  }

  public boolean analytical() {
    return analytical;
  }

  public boolean marketing() {
    return marketing;
  }

  public static ConsentLevel ofCookieValue(String value) {
    return Arrays.stream(values())
        .filter(level -> level.cookieValue.equals(value))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Unknown cookiePolicyGDPR value: " + value));
  }
}
