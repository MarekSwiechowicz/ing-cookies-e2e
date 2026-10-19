package pl.ing.cookies;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.options.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The scenario from the assignment: on ing.pl open "Dostosuj" (customise), consent to
 * analytical cookies only, confirm the choice and verify what actually ended up in
 * browser storage.
 *
 * <p>Browsers come from the {@code -Dbrowsers} property (all three by default) and,
 * thanks to the JUnit configuration in {@code junit-platform.properties}, each one runs
 * in its own thread in parallel.
 */
class CookieConsentTest {

  private static Stream<String> browsers() {
    return Arrays.stream(System.getProperty("browsers", "chromium,firefox,webkit").split(","))
        .map(String::trim)
        .filter(name -> !name.isEmpty());
  }

  @DisplayName("Analytics-only consent is stored in the browser")
  @ParameterizedTest(name = "{0}")
  @MethodSource("browsers")
  void analyticalConsentIsPersisted(String browserName) {
    runInBrowser(browserName, "analytical-consent", banner -> {
      banner.open().customize().allowAnalyticalOnly();

      assertThat(banner.analyticalEnabled()).as("analytical cookies toggle").isTrue();
      assertThat(banner.marketingEnabled()).as("marketing cookies toggle").isFalse();

      banner.acceptSelected();

      ConsentLevel level = ConsentLevel.ofCookieValue(banner.waitForConsentCookie());
      assertThat(level).isEqualTo(ConsentLevel.TECHNICAL_AND_ANALYTICAL);
      assertThat(level.analytical()).isTrue();
      assertThat(level.marketing()).isFalse();

      Cookie consent = banner.findCookie(Cookies.CONSENT).orElseThrow();
      assertThat(consent.domain).isEqualTo(".ing.pl");
      assertThat(consent.path).isEqualTo("/");
      assertThat(consent.expires)
          .as("consent is persistent, not a session cookie")
          .isGreaterThan(System.currentTimeMillis() / 1000d);
      assertThat(banner.findCookie(Cookies.CONSENT_DETAILS))
          .as("consent metadata")
          .isPresent();

      banner.reload();
      assertThat(banner.isVisible()).as("banner after reload").isFalse();
      assertThat(banner.findCookie(Cookies.CONSENT).orElseThrow().value)
          .isEqualTo(ConsentLevel.TECHNICAL_AND_ANALYTICAL.cookieValue());

      // Google Analytics writes _ga on the first page load after consent. Waiting for the
      // exact name matters: _ga_<streamId> lands slightly earlier than _ga itself.
      banner.waitForCookie("_ga");
      assertThat(banner.cookieNames()).contains("_ga");

      assertNoCookiesFrom(banner.cookieNames(), Cookies.MARKETING);
    });
  }

  @DisplayName("Declining everything sets no analytical cookies")
  @ParameterizedTest(name = "{0}")
  @MethodSource("browsers")
  void declineAllStoresTechnicalConsentOnly(String browserName) {
    runInBrowser(browserName, "decline-all", banner -> {
      banner.open().declineAll();

      assertThat(ConsentLevel.ofCookieValue(banner.waitForConsentCookie()))
          .isEqualTo(ConsentLevel.TECHNICAL_ONLY);

      banner.reload();
      assertThat(banner.isVisible()).isFalse();
      assertNoCookiesFrom(banner.cookieNames(), Cookies.ANALYTICAL);
      assertNoCookiesFrom(banner.cookieNames(), Cookies.MARKETING);
    });
  }

  private static void assertNoCookiesFrom(List<String> cookieNames, List<String> forbiddenPrefixes) {
    assertThat(cookieNames)
        .as("cookies outside the granted consent")
        .noneMatch(name -> forbiddenPrefixes.stream().anyMatch(name::startsWith));
  }

  private interface Scenario {
    void run(CookieBanner banner);
  }

  private static void runInBrowser(String browserName, String label, Scenario scenario) {
    try (BrowserSession session = new BrowserSession(browserName)) {
      try {
        scenario.run(new CookieBanner(session));
      } catch (CookieBanner.SecurityChallengeException blocked) {
        // The site never let us in, so there is nothing to test; not a banner defect.
        session.saveArtifacts(label + "-blocked");
        Assumptions.abort(blocked.getMessage());
      } catch (Throwable failure) {
        session.saveArtifacts(label);
        throw failure;
      }
    }
  }
}
