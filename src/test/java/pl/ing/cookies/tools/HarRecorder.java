package pl.ing.cookies.tools;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.HarMode;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Records ing.pl network traffic into the HAR file used by {@code -Dmode=har}.
 * Run it from a Polish connection, otherwise the bank serves its security page:
 *
 * <pre>mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=pl.ing.cookies.tools.HarRecorder</pre>
 *
 * <p>Images, fonts and other binary assets are stripped from the recording afterwards:
 * they do not affect the consent mechanism but account for most of its size.
 */
public final class HarRecorder {

  private static final Path HAR = Paths.get("src", "test", "resources", "har", "ing-pl.har");
  private static final List<String> BINARY_SUFFIXES =
      List.of(".woff", ".woff2", ".ttf", ".eot", ".svg", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".mp4");

  public static void main(String[] args) throws IOException {
    Files.createDirectories(HAR.getParent());
    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch();
      BrowserContext context = browser.newContext(new Browser.NewContextOptions()
          .setLocale("pl-PL")
          .setTimezoneId("Europe/Warsaw")
          .setViewportSize(1440, 900)
          .setRecordHarPath(HAR)
          .setRecordHarMode(HarMode.FULL));
      Page page = context.newPage();
      recordConsentFlow(page);
      context.close();
      browser.close();
    }
    stripBinaryAssets();
    System.out.println("Recorded " + HAR + " (" + HAR.toFile().length() / 1024 / 1024 + " MB)");
  }

  private static void recordConsentFlow(Page page) {
    page.navigate("https://www.ing.pl/", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    page.waitForSelector(".js-cookie-policy-main-settings-button");
    page.click(".js-cookie-policy-main-settings-button");
    page.waitForSelector("div[role=switch][name=CpmAnalyticalOption]");
    page.locator("div[role=switch][name=CpmAnalyticalOption]").click();
    page.click(".js-cookie-policy-settings-decline-button");
    page.waitForTimeout(3_000);
    page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    page.waitForTimeout(5_000);
  }

  private static void stripBinaryAssets() throws IOException {
    String har = Files.readString(HAR);
    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(har).getAsJsonObject();
    com.google.gson.JsonArray entries = root.getAsJsonObject("log").getAsJsonArray("entries");
    com.google.gson.JsonArray kept = new com.google.gson.JsonArray();
    for (com.google.gson.JsonElement element : entries) {
      com.google.gson.JsonObject entry = element.getAsJsonObject();
      String url = entry.getAsJsonObject("request").get("url").getAsString();
      String mimeType = entry.getAsJsonObject("response").getAsJsonObject("content").has("mimeType")
          ? entry.getAsJsonObject("response").getAsJsonObject("content").get("mimeType").getAsString()
          : "";
      boolean binary = mimeType.startsWith("image/") || mimeType.startsWith("font/")
          || mimeType.startsWith("video/") || mimeType.startsWith("audio/")
          || BINARY_SUFFIXES.stream().anyMatch(url::endsWith);
      if (!binary) {
        kept.add(entry);
      }
    }
    root.getAsJsonObject("log").add("entries", kept);
    Files.writeString(HAR, root.toString());
    System.out.println("Stripped " + (entries.size() - kept.size()) + " of " + entries.size()
        + " HAR entries (binary assets).");
  }

  private HarRecorder() {
  }
}
