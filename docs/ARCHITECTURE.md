# MPEI Neo architecture and UX contracts

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
- `SearchOverlayState`: query, search scope, activation, frozen source bounds,
  reverse transition lifetime and restoration.
- `scripts/check_architecture.py`: CI guard for the above ownership boundaries.
  The guard runs for every PR and rolling Dev push.

The UI still has more work to do: the Android-specific BARS WebView transport
and request watchdogs can be moved into a session adapter; feature-specific
focus requests and IME transitions still live inside the root shell. Avoid
moving those until user-device validation of WebView and Back handling.

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
