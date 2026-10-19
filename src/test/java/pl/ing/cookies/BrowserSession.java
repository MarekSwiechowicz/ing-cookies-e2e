package pl.ing.cookies;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.HarNotFound;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * A single isolated browser session: its own Playwright instance (the object is not
 * thread-safe, so every test thread gets one), a clean profile and tracing enabled.
 *
 * <p>The session runs in one of two modes ({@code -Dmode}):
 * <ul>
 *   <li>{@code live} (default) hits the real ing.pl service,</li>
 *   <li>{@code har} replays traffic from {@code src/test/resources/har/ing-pl.har},
 *       so the run is fully offline and identical every time.</li>
 * </ul>
 */
public class BrowserSession implements AutoCloseable {

  private static final Path ARTIFACTS = Paths.get("target", "playwright-artifacts");
  private static final Path HAR = Paths.get("src", "test", "resources", "har", "ing-pl.har");

  private final String browserName;
  private final Playwright playwright;
  private final Browser browser;
  private final BrowserContext context;
  private final Page page;

  public BrowserSession(String browserName) {
    this.browserName = browserName;
    this.playwright = Playwright.create();
    this.browser = launch(playwright, browserName);
    // Clean, deterministic profile: no storage state, fixed viewport, Polish locale.
    this.context = browser.newContext(new Browser.NewContextOptions()
        .setLocale("pl-PL")
        .setTimezoneId("Europe/Warsaw")
        .setViewportSize(1440, 900)
        .setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE));
    this.context.setDefaultTimeout(20_000);
    if (Mode.current() == Mode.HAR) {
      // Anything missing from the recording is aborted, so the test cannot reach the network.
      this.context.routeFromHAR(HAR, new BrowserContext.RouteFromHAROptions()
          .setUpdate(false)
          .setNotFound(HarNotFound.ABORT));
    }
    this.context.tracing().start(new Tracing.StartOptions()
        .setScreenshots(true).setSnapshots(true).setSources(false));
    this.page = context.newPage();
  }

  private static Browser launch(Playwright playwright, String name) {
    BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
        .setHeadless(!"false".equalsIgnoreCase(System.getProperty("headless", "true")));
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "chromium" -> playwright.chromium().launch(options);
      case "firefox" -> playwright.firefox().launch(options);
      case "webkit" -> playwright.webkit().launch(options);
      default -> throw new IllegalArgumentException("Unsupported browser: " + name);
    };
  }

  public Page page() {
    return page;
  }

  public String browserName() {
    return browserName;
  }

  public enum Mode {
    LIVE, HAR;

    public static Mode current() {
      return "har".equalsIgnoreCase(System.getProperty("mode", "live")) ? HAR : LIVE;
    }
  }

  public List<Cookie> cookies() {
    return context.cookies();
  }

  public void saveArtifacts(String label) {
    try {
      page.screenshot(new Page.ScreenshotOptions()
          .setPath(ARTIFACTS.resolve(label + "-" + browserName + ".png"))
          .setFullPage(false));
    } catch (RuntimeException ignored) {
      // a screenshot is a diagnostic aid, it must not mask the failure
    }
    context.tracing().stop(new Tracing.StopOptions()
        .setPath(ARTIFACTS.resolve(label + "-" + browserName + ".zip")));
  }

  @Override
  public void close() {
    try {
      // Close the page and detach HAR routing first, otherwise a request intercepted at
      // the last moment hits a closing context and fails the test for unrelated reasons.
      try {
        page.close();
        context.unrouteAll();
      } catch (RuntimeException ignored) {
        // cleanup must not mask the test result
      }
      context.close();
      browser.close();
    } finally {
      playwright.close();
    }
  }
}
