# MPEI Neo architecture and UX contracts

Merged branch maintenance is automated by `.github/workflows/prune-merged-branches.yml`:
after each `main` push the workflow deletes only unprotected, PR-free
branch heads which are provably ancestors of `main`. Release branches,
unmerged work and open PRs are never removed.

The project intentionally stays in **one Gradle module** until the boundaries
below are stable and measured. A package or file split alone is not a refactor:
the owner of mutable state and the direction of dependencies must change.

## Dependency direction

```text
App shell (navigation, animation, app bars)
  ├── Home / Schedule / BARS / Mail / Campus / Settings screens
  │     └── feature ViewModel or state holder (commands + immutable view state)
  │            └── repositories / authenticated session coordinators
  │                   └── network, WebView bridge, local snapshot store
  └── shared theme, components and diagnostics

Pure models, parsers, navigation policies and grade planners depend on no UI.
```

**Full-page Back:** service transitions, BARS discipline, Mail message and
Mail composer, and embedded WebView exits all use the same full-width,
finger-following 20% swipe threshold and 260 ms exit / 310 ms entry policy.
The underlying native page stays composed while a nested page slides away.
Modal sheets and search reveal intentionally retain their own animations.

**Shell:** routes, back policy, transitions, IME coordination and deep-link
dispatch. Does not initiate BARS extraction or Mail network calls.
**Feature UI:** renders immutable state and invokes commands. Does not decide
authentication, retries, cache freshness or multi-document workflows.
**Feature coordinator:** owns operations that span several asynchronous
steps, such as a BARS Main → Summary capture. It enforces cancellation and
one active session mode. UI cannot accidentally start two conflicting flows.
**Repository:** snapshots, freshness, source provenance and persistence.
**Domain:** deterministic rules for planning and mapping raw BARS activities.

### Navigation and bars

- Search belongs to the destination, not to the app shell. Home and searchable
  services use a contextual search app bar; Settings has a title and Back button.
  More destination types can opt out without changing global search semantics.
- The BARS WebView is one long-lived resource so cookies/2FA survive navigation.
  It must not be recreated when Home becomes visible.
- BARS and OWA use the same `EmbeddedBrowserToolbar` with history-based Back
  and Forward, a current-page Reload, and explicit Copy URL. Buttons are enabled
  from WebView history callbacks; the site label never displays the path or
  auth parameters. Controls reserve their own height instead of covering form
  fields. The browser toolbar does not replace system Back or native routing.
- Both embedded portals have a small left-edge swipe exit strip, separate
  from WebView history Back. Mail returns to inbox; BARS returns to native
  grades, or Home if the only visible UI is the official login/2FA form.
  Keep the edge target narrow so regular browser scrolling remains available.
- Schedule lesson highlights originate only from explicit cross-feature links;
  the matched lesson acknowledges its one-shot request after the focus pulse,
  and ordinary service navigation clears any stale pending request.
- BARS browser mode is user-controlled. Auto-navigation to Summary must stop
  before the interactive browser is exposed. Pending route-discovery and
  delayed callbacks must be invalidated.
- Extracting Main while browsing is read-only, but a stale Summary payload
  must never update the repository after browser mode begins.
- A failed Summary capture does **not** erase cached grades or a previously
  valid Summary snapshot. The UI presents partial-success states explicitly.
- The app shell navigates to the BARS feature; the BARS feature owns its own
  overview → discipline → goal plan route.

## UX: task-first BARS

1. **Overview:** disciplines and grade/progress summary, compact next-action
   hint; do not duplicate a complete planner above the discipline list.
2. **Discipline:** evaluated and remaining activities, current average and
   one 4.2 goal affordance.
3. **Goal plan:** future-grade scenarios with explanations behind a single
   info affordance; share the exact same planning function as the overview.
4. **Reports:** attendance, weekly grade matrix, and raw diagnostics remain
   accessible but are secondary to the overview.

The app can be simplified without removing features by reducing the number
of simultaneous choices on each level.

## Rollout checks

1. Unit-test top-bar policy, stack routes, BARS mode/cancellation, and grade
   planning. Document risks before refactoring a source adapter.
2. Build and lint the signed PR APK. Verify BARS cold launch with cached
   marks, login/2FA, refresh, Summary timeout, and interactive browser while
   Summary discovery is pending on a physical device.
3. Test Back and swipe-back on Home, Settings, BARS discipline and embedded
   browsers; verify the IME never intercepts back navigation.
4. Track *owners of mutable state*, *coupling across features*, and
   *duplicate code paths*, not just Kotlin line count.
5. Only merge independently verified PRs and publish a Dev build after the
   integration check.

## Implemented boundaries (October 2026)

- `BarsSessionCoordinator`: pure Kotlin state machine for automatic Main and
  Summary extraction, once-per-document marks, generation cancellation and
  browser takeover. The WebView remains an adapter in `BarsScreen` for now.
- `BarsWebFlowState`: presentation-only WebView flags (viewport, diagnostics,
  manual connection intention), not the owner of extraction operations.
- `ServiceNavigationState`: one saveable owner for service push/pop, Back preview
  and the Schedule → Campus nested service stack.
- `AppNavigationRequest`: typed cross-feature navigation and focus intent.
  `AppNavigationTargets` owns unique IDs and acknowledgement for lesson,
  discipline and campus highlights; the shell only dispatches and animates.
- `SearchOverlayState`: query, search scope, activation, frozen source bounds,
  reverse transition lifetime and restoration.
- `MailComposeController`: draft/account snapshot, autosave, debounced recipient
  lookup, outgoing SAF grants and sending; the Mail ViewModel remains the
  combined UI state facade and owns mailbox sync and message pagination.
- `MailPagePolicy`: pure inbox/Sent pagination invariants (server offsets,
  deduplicated visible rows, preserving sent-recipient enrichment, and
  exhaustion). The Mail ViewModel retains account sessions and I/O orchestration.
- `scripts/check_architecture.py`: CI guard for the above ownership boundaries.
  The guard runs for every PR and rolling Dev push.

The Android-specific BARS WebView adapter is now `BarsWebViewTransport`.
It owns WebView configuration, retained session instance, main-frame callbacks,
JS bridge, document readiness watchdogs, background refresh and teardown.
`BarsScreen` only composes native content and WebView visibility and forwards
user commands; it must not make extraction or navigation decisions.
`BarsSessionCoordinator` remains the pure policy/state machine, separate
from Android transport.

Still outstanding: test real university 2FA, cold-start session restoration,
Main→Summary transition, interrupted captures and IME/gesture behavior on a
physical device. The root shell retains platform IME and transition coordination;
feature-specific one-shot focus targets are owned by AppNavigationTargets.
A future Mail synchronization refactor should follow device validation and
an explicit check of account-switch and logout races.

## Next review criteria

- No second owner of session operation state; callbacks are ignored after
  cancellation or document change.
- Native grades and attendance remain visible on recoverable network failure.
- Back navigation from a BARS discipline returns to its overview rather than
  Home, including gesture navigation.
- Search keeps its source-anchored animation and does not appear in Settings.
- A new feature cannot silently add separate global-search/route flags to
  `MpeiNeoApp.kt`.

Keep Mail and Schedule data providers independent; stay in one Gradle module
until these boundaries have survived device testing.
