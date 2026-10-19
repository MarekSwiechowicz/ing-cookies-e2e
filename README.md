# ING.pl analytics cookie consent test (Java + Playwright)

[![ING cookie consent E2E](https://github.com/MarekSwiechowicz/ing-cookies-e2e/actions/workflows/e2e.yml/badge.svg)](https://github.com/MarekSwiechowicz/ing-cookies-e2e/actions/workflows/e2e.yml)

End-to-end test for the cookie consent flow on [ing.pl](https://www.ing.pl/): open the site,
go to **Dostosuj** (customise), enable **analytical** cookies only, confirm with
**Zaakceptuj zaznaczone**, then verify what actually landed in browser storage. Runs in
Chromium, Firefox and WebKit in parallel, locally and in GitHub Actions.

## Running it

Requires JDK 17+ and Maven 3.9+. Playwright downloads its browsers on first run.

```bash
mvn test -Dmode=har                          # all three browsers, offline from the recording
mvn test                                     # same flow against the live ing.pl
mvn test -Dmode=har -Dbrowsers=firefox       # one browser
mvn test -Dbrowsers=chromium -Dheadless=false  # watch it run
```

Reports land in `target/surefire-reports/`. A failed run also leaves a screenshot and a
Playwright trace in `target/playwright-artifacts/`.

## Two modes

`-Dmode=live` (default) hits the real site. `-Dmode=har` replays
`src/test/resources/har/ing-pl.har` with no network access at all, so the run is identical
every time.

The second mode exists because ING serves a hCaptcha security page to traffic from outside
Poland, which is what GitHub runners use. The test recognises that page and skips the run
with an explicit reason instead of failing on a selector timeout. HAR mode keeps the
scenario fully covered in CI regardless of location.

Refresh the recording from a Polish connection with:

```bash
mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=pl.ing.cookies.tools.HarRecorder
```

## What is verified

ING uses its own consent mechanism, not a vendor platform. The decision is stored in the
`cookiePolicyGDPR` cookie on `.ing.pl`, whose value is a single digit encoding the chosen
combination (not a bit mask):

| value | technical | analytical | marketing | how to get it |
|---|---|---|---|---|
| `1` | yes | no | no | Reject all |
| `2` | yes | no | yes | Customise, marketing only |
| `3` | yes | yes | no | **the scenario under test** |
| `4` | yes | yes | yes | Accept all |

`analyticalConsentIsPersisted` asserts that the analytical toggle is on and the marketing
one off, that the stored consent is `3` and persistent, that `cookiePolicyGDPR__details` is
present, that the banner does not reappear after a reload, that `_ga` shows up (proving the
analytics scripts actually started) and that no marketing cookie was created.

`declineAllStoresTechnicalConsentOnly` walks the "reject all" path and expects
`cookiePolicyGDPR=1` with no analytical or marketing cookies. It is there to prove the
assertions above distinguish between states instead of passing on any outcome.

## Repeatability

No fixed `sleep` calls: every wait is a condition, including waiting on browser storage.
Selectors use the bank's own `js-cookie-policy-*` classes and `aria-checked`, not copy or
DOM position. Each test gets a fresh Playwright instance and a clean context (no stored
state, fixed viewport, `pl-PL`, `Europe/Warsaw`). Versions are pinned, navigation is retried
three times, and the security page is reported separately from a real banner failure.

The test deliberately does not block Google Analytics, because the presence of `_ga` is the
evidence that analytical consent took effect.

## Parallel execution and CI

Locally a single `mvn test` covers all three browsers: they are test parameters
(`@ParameterizedTest`) and `src/test/resources/junit-platform.properties` enables JUnit 5
parallelism on three threads. A full run takes about 11 seconds.

`.github/workflows/e2e.yml` has three jobs: a **matrix** of three parallel jobs with
`fail-fast: false`, **all-browsers-in-parallel** doing the same work in a single job, and
**live**, which runs against the real site and skips itself when the bank blocks the runner.
All three upload Surefire reports and Playwright artifacts. The pipeline runs on pushes to
`main`, on pull requests, manually, and weekly.
