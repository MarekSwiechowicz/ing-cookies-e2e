package pl.ing.cookies;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import java.util.List;
import java.util.Optional;

/**
 * Page object for the cookie consent banner on ing.pl. The selectors rely on the
 * {@code js-*} classes the bank adds for automation rather than on copy or DOM position.
 */
public class CookieBanner {

  private static final String URL = "https://www.ing.pl/";
  private static final int NAVIGATION_ATTEMPTS = 3;

  private final BrowserSession session;
  private final Page page;

  public CookieBanner(BrowserSession session) {
    this.session = session;
    this.page = session.page();
  }

  private Locator customizeButton() {
    return page.locator(".js-cookie-policy-main-settings-button");
  }

  private Locator declineAllButton() {
    return page.locator(".js-cookie-policy-main-decline-button");
  }

  /** "Zaakceptuj zaznaczone" (accept selected); the bank's class name says decline. */
  private Locator acceptSelectedButton() {
    return page.locator(".js-cookie-policy-settings-decline-button");
  }

  private Locator toggle(String name) {
    return page.locator("div[role=switch][name=" + name + "]");
  }

  private Locator analyticalToggle() {
    return toggle("CpmAnalyticalOption");
  }

  private Locator marketingToggle() {
    return toggle("CpmMarketingOption");
  }

  /**
   * Opens the home page and waits for the consent banner. Navigation is retried because
   * the anti-bot layer occasionally returns an interstitial page without the banner.
   */
  public CookieBanner open() {
    RuntimeException last = null;
    for (int attempt = 1; attempt <= NAVIGATION_ATTEMPTS; attempt++) {
      try {
        page.navigate(URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        customizeButton().waitFor(new Locator.WaitForOptions().setTimeout(20_000));
        return this;
      } catch (RuntimeException e) {
        last = e;
        failIfSecurityChallenge();
      }
    }
    throw new AssertionError("The cookie banner did not appear after " + NAVIGATION_ATTEMPTS + " attempts", last);
  }

  /**
   * For traffic from outside Poland ING serves a security page with hCaptcha instead of
   * the home page. That is neither a test defect nor a banner regression, so the state is
   * reported explicitly instead of as a selector timeout. Detection looks at captcha
   * frames because the block page reloads itself and reading its HTML tends to fail.
   */
  private void failIfSecurityChallenge() {
    boolean challenge = page.frames().stream()
        .anyMatch(frame -> frame.url().contains("hcaptcha") || frame.url().contains("captcha"));
    if (challenge) {
      throw new SecurityChallengeException(
          "ing.pl served its hCaptcha security page instead of the home page. "
              + "This happens for requests from IP addresses outside Poland, such as GitHub Actions runners. "
              + "Run the test from a Polish connection (-Dmode=live) or offline from the recording (-Dmode=har).");
    }
  }

  public static class SecurityChallengeException extends RuntimeException {
    public SecurityChallengeException(String message) {
      super(message);
    }
  }

  public CookieBanner customize() {
    customizeButton().click();
    assertThat(analyticalToggle()).isVisible();
    return this;
  }

  public CookieBanner allowAnalyticalOnly() {
    setToggle(analyticalToggle(), true);
    setToggle(marketingToggle(), false);
    return this;
  }

  public CookieBanner acceptSelected() {
    acceptSelectedButton().click();
    return this;
  }

  public CookieBanner declineAll() {
    declineAllButton().click();
    return this;
  }

  private void setToggle(Locator toggle, boolean expected) {
    if (isChecked(toggle) != expected) {
      toggle.click();
    }
    page.waitForCondition(() -> isChecked(toggle) == expected,
        new Page.WaitForConditionOptions().setTimeout(5_000));
  }

  private boolean isChecked(Locator toggle) {
    return "true".equals(toggle.getAttribute("aria-checked"));
  }

  public boolean analyticalEnabled() {
    return isChecked(analyticalToggle());
  }

  public boolean marketingEnabled() {
    return isChecked(marketingToggle());
  }

  public boolean isVisible() {
    return customizeButton().isVisible();
  }

  /** The banner script writes the cookie after the click, so we wait on browser state. */
  public String waitForConsentCookie() {
    page.waitForCondition(() -> findCookie(Cookies.CONSENT).isPresent(),
        new Page.WaitForConditionOptions().setTimeout(15_000));
    return findCookie(Cookies.CONSENT).orElseThrow().value;
  }

  /** Waits for a named cookie, e.g. one written by the analytics scripts after consent. */
  public void waitForCookie(String name) {
    page.waitForCondition(() -> findCookie(name).isPresent(),
        new Page.WaitForConditionOptions().setTimeout(20_000));
  }

  public Optional<Cookie> findCookie(String name) {
    return session.cookies().stream().filter(c -> c.name.equals(name)).findFirst();
  }

  public List<String> cookieNames() {
    return session.cookies().stream().map(c -> c.name).toList();
  }

  public CookieBanner reload() {
    page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    return this;
  }
}
