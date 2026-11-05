# Material 3 verification matrix

The existing PR workflow runs Lint and JVM tests. The new emulator job adds a real Android API 35 instrumented test exercising the mailbox tabs with the Android Accessibility Test Framework. This is **initial coverage**, not a proof that every screen satisfies every Material 3 rule.

| Check | Current automated coverage | Remaining work |
| --- | --- | --- |
| Material 3 architecture | Separate policy PR #129 | More comprehensive approved UI components and token rules |
| Android Lint/JVM tests | Existing `verify` job | Broader component contracts |
| Compose instrumentation | API 35 emulator, focused test | Entire app navigation, predictive back, font/IME and device matrix |
| Accessibility | ATF on mail folder tabs | All actionable screens, manual TalkBack, Voice Access |
| Visual regression | **Not enabled** | Approved screenshot references, visual diff runner and reviewers |
| Material documentation updates | Manual review | Versioned and periodically refreshed guidance |

## Why screenshot assertions are not claimed yet

The repository uses Android Gradle Plugin 9.4.1. Google's newer built-in Compose Preview Screenshot Testing test-suite integration requires AGP 9.5.0-alpha03 or higher and is experimental: https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites . A separate dependency and CI compatibility review is required before baselines and screenshot checking can become a *required* check. Do not check in placeholder reference images or label ordinary Compose tests 'screenshot verification'.

## Suggested comprehensive matrix

- Devices: compact phone portrait, landscape, medium tablet, expanded tablet/foldable.
- Settings: light and dark theme, dynamic color available/unavailable, font scale 1.0 and 1.5+, accessible contrast modes.
- States: first load, loading, error, offline, empty, content, detail, compose, search, keyboard active, back-in-progress.
- Manual: TalkBack focus order and actions, physical keyboard, touch targets, orientation and split screen.

Tests should stay deterministic: mock remote BARS and mail dependencies where possible, avoid authenticated accounts, and save diagnostics for failures.


## Integration regression run

The emulator workflow also runs `BarsTaskFlowUiTest`, including the BARS detail
open / Back / reopen regression merged through PR #123. The test suite is
selected by an explicit two-class instrumentation filter so both the Material 3
accessibility checks and the BARS detail tests run in the same API 35 emulator.
These tests validate dispatcher Back, not a real touch-driven predictive
gesture; physical gesture cancellation still requires device QA.
