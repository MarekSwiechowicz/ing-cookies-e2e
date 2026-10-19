package pl.ing.cookies;

import java.util.List;

public final class Cookies {

  public static final String CONSENT = "cookiePolicyGDPR";

  public static final String CONSENT_DETAILS = "cookiePolicyGDPR__details";

  /** Cookies that may appear only after analytical consent. */
  public static final List<String> ANALYTICAL = List.of("_ga", "s_cc");

  /** Cookies that may appear only after marketing consent. */
  public static final List<String> MARKETING = List.of("_fbp", "_gcl_au", "_uetsid", "_uetvid", "MUID", "IDE", "test_cookie");

  private Cookies() {
  }
}
