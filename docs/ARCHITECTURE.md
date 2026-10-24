# MPEI Lite architecture

MPEI Lite uses a single Android app module. The important boundaries are
**feature UI → ViewModel/coordinator → repository → network and persistence**.
Pure models, search rules, grade calculations and route rules remain testable
without Android UI.

## Application shell

`MpeiNeoApp.kt` composes four top-level HorizontalPager pages, secondary
Settings and a single contextual search screen. `ServiceNavigationState`
owns committed routes; Foundation Pager owns finger-driven page movement
and spring settling. There are no custom transition jobs or Back-slide
progress controllers. See [motion notes](MOTION_EXPRESSIVE.md). The system Back action and explicit Back buttons
close nested destinations.

BARS has a retained, full-size WebView composition in its dedicated pager
page even when offscreen. All four pager slots stay precomposed, but only the
nearby non-BARS feature content mounts; no detached duplicate WebView or
cross-page touch layer is needed.

## Feature ownership

- **Schedule:** repository and file cache own target and agenda data. Search
  provides group/teacher/classroom matches and saved favorites.
- **BARS:** `BarsSessionCoordinator` controls extraction sessions, expiry,
  cancellation and browser mode. `BarsWebViewTransport` owns Android WebView
  lifecycle and JavaScript bridge. The UI does not own capture operations.
  Local 4.2 projections are not official exam decisions.
- **Mail:** `MailViewModel` and `MailComposeController` own drafts and mailbox
  actions. OWA repositories own authenticated network requests and cache.
  WorkManager remains for notification polling.
- **Campus:** the bundled OSM geometry is rendered offline, with pan/zoom,
  selected building and classroom focus managed by the campus screen.
- **Updates:** manual GitHub APK download, package/version/signature validation,
  and the standard Android installer. No privileged or background APK install.
- **Diagnostics:** keep bounded logs and ZIP export. Performance investigations
  can use developer profiling tools without adding a user-facing mode.

## Verification and ownership

`scripts/check_architecture.py` checks key session and route boundaries.
Unit tests protect parsers, grade algorithms, cache integrity and navigation.
Device testing is required for real BARS 2FA, OWA login, notifications,
accessibility and WebView lifecycle. Do not treat a passing compile job as a
complete integration test.

Keep internal package names and app IDs intact so installed users retain their
data and can update in place. Visual branding may change independently.
