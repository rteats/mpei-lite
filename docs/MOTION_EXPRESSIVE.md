# Material 3 Expressive motion: decisions and implementation

Sources reviewed on 24 October 2026:
- [Google Material motion physics](https://m3.material.io/styles/motion/overview/how-it-works)
- [Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
- [Transition patterns](https://m3.material.io/styles/motion/transitions/transition-patterns)
- [Compose Material 3 MotionScheme reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)

## Spatial-only trial (24 October 2026)

**Temporary developer test:** all application-owned custom transitions now use
Material 3 Expressive **spatial** motion tokens, including week transitions,
visual focus and the campus keyboard fade. The goal is to compare the feel on a
real device before deciding which cues should use effects springs.

Additional coverage: full-screen Settings and Search overlays, Mail detail and
composer, embedded Mail portal, BARS discipline detail, top app-bar title and
action changes, bottom navigation visibility, Settings section/chevron, BARS
report chooser and automatic-grade planner, and lesson-card padding/color.

For exit animations, Mail retains a snapshot of the outgoing message or draft
and BARS retains the outgoing discipline. Navigation and account session state
still change immediately; the UI does not retain a login or delay a Back action.

At the theme boundary, `spatialOnlyMotionScheme` delegates to
`MotionScheme.expressive()` and maps fast/default/slow **effects** requests to
their matching **spatial** tokens. This also applies to native Material 3
components that read motion specs from the theme; it requires no replacement of
their internal animations. Animations hardcoded inside dependencies, direct
finger tracking, system gestures, WebView DOM motion, and time-based progress
cannot be converted by a motion scheme.

The sections below record the **original token decisions** from PR #96; this
temporary trial supersedes its effects-spring selections. A real-device review
will decide which spatial motions should become effects again.

## October 24 follow-up: navigation and component refinements

- Distant navbar jumps temporarily put the destination beside the source
  and move one pager-width. The canonical order is restored after settling;
  normal swipes still follow Schedule–BARS–Mail–Campus. Pager keys retain
  the existing BARS WebView. Only an actual drag may commit a route on settle,
  preventing a schedule lesson-focus request from causing an unrelated tab jump.
- The service-title transition clips both texts and moves in the same
  direction as the corresponding page, without overlapping glyphs.
- The bottom navigation remains visible for Mail/BARS details, portals,
  Search, and the keyboard. Only Settings covers the navbar.
- Campus zoom actions use the native Material 3
  [vertical floating toolbar](https://m3.material.io/components/toolbars/specs)
  with symmetric 24dp Material Symbols and accessible controls.
- Normal Mail and BARS detail selection no longer pulses the title; BARS
  retains a focus cue for explicit cross-service search links.
- Remove the hypothetical Автомат projection progress indicator. The
  value and reachability message remain available without a duplicate bar.

Check on device: rapid distant-tab switching, mid-flight reversal, persistent
BARS 2FA session, text clipping with large font scale, map zoom controls,
next-lesson focus, navbar in search with IME, and the Mail/BARS Back path.

## Physics instead of independent duration constants

Google's M3 Expressive motion system specifies **spatial** and **effects**
springs at **fast**, **default**, and **slow** speeds. The Compose motion
scheme provides their FiniteAnimationSpecs. Its effective stiffness/damping
and the gesture's velocity define duration and trajectory; unlike tweens,
springs handle mid-flight reversals and retargeting without restarting a
fixed clock.

The existing `MpeiNeoTheme` installs `MotionScheme.expressive()`. Every
custom animation below reads `MaterialTheme.motionScheme` rather than
duplicating Google tokens as numeric spring constants. Standard/Expressive
can thus be swapped centrally. Material 3 components already consume the
scheme internally: **do not add extra animation wrappers to their built-in
selection or loading feedback**.

| Interaction | Animated property | Theme spring token | Why |
| --- | --- | --- | --- |
| Four-tab continuous pager | page X position | `slowSpatialSpec<Float>()` | The entire content viewport travels; Google recommends slow spatial for full-screen moves |
| Bottom-bar tab switch | page X position | `slowSpatialSpec<Float>()` | Identical geometry and settling to the gesture; no mismatch between tap and drag |
| Agenda week-content change | opacity | `defaultEffectsSpec()` in / `fastEffectsSpec()` out | An alpha bounce is visibly wrong; effects springs prevent overshoot |
| Agenda edge-pull feedback | content Y position | `fastSpatialSpec<Float>()` | Rapid touch reversals need a retargetable spatial spring; threshold logic still uses raw distance |
| Lesson-card expansion | height | `fastSpatialSpec()` | Local-sized control expands quickly while retaining positional continuity |
| Map building-keyboard expansion | panel height | `defaultSpatialSpec()` | A secondary sheet partially covers content |
| Map keyboard fade | opacity | `fastEffectsSpec()` | Fast effects are critically damped and avoid flashing alpha |
| Linked-destination focus cue | opacity/emphasis | `fastEffectsSpec()` in / `defaultEffectsSpec()` out | The cue must not bounce; its 850 ms readable hold remains a deliberate delay |

### Navigation and gesture precedence

**Foundation HorizontalPager** handles slop, scroll/nested-scroll
coordination, velocity/fling settling, interruption, semantics, and direct
finger-to-page translation. A custom detector plus manual Animatable would
reimplement those behaviors and risk stealing vertical list or map touches.

The four pages are ordered Schedule → BARS → Mail → Campus. Root swipes
commit `ServiceNavigationState.open(tab)` only after the pager **settles**.
Bottom-bar taps and cross-service links commit the route immediately and
animate the pager towards it. A programmatic-scroll guard prevents a
halfway/settled pager update from being mistaken for a separate user
navigation. Ordinary swipes never add history; explicit linked destinations
retain the existing origin for Back.

The BARS page stays **composed** at every pager position, even when it is
outside the viewport: disposing its WebView would discard official portal
authentication and 2FA state. Foundation Pager precomposes all four cheap
page slots (`beyondViewportPageCount = 3`) to guarantee this; the other
feature UIs mount only while adjacent to the current/target page. Their
saveable state remains in the app's existing `SaveableStateHolder`.

**Gesture ownership:** Root swiping is disabled while Settings, Search,
the IME, embedded OWA/BARS portals, or nested Mail/BARS details are open.
Schedule keeps vertical list/edge-week scrolling. Campus retains pan/pinch
gestures; on real devices, verify the map detector consumes a pan before the
horizontal pager claims it. Do not replace map pan with pager gestures.

### Explicit exclusions

Do not restore search field morphing, per-page Back-slide progress, custom
navigation velocity trackers, or multiple animation timers. System Back
belongs to the destination's existing handlers. Effects springs are only
for opacity/color, spatial springs for position/size. No animation is part
of the repository/session lifecycle.

### QA before Stable

- Finger swipe Schedule → BARS → Mail → Campus and back; verify page tracks
  touch throughout, snaps with velocity, and rapidly reverses without
  landing on an unintended tab.
- Tap bottom tabs during an unfinished swipe. Deep-link from a lesson to a
  campus building/BARS discipline, then press Back to the real origin.
- Revisit BARS after several page changes: verify 2FA/cookies and marks,
  including a login while another service is selected.
- Pan/zoom Campus horizontally; scroll the Schedule vertically; ensure
  those child gestures do not accidentally change root services.
- Open OWA, message/compose/BARS details, Settings, search and keyboard:
  horizontal pager should not hijack their gestures.
- Rotate the device and test with Android's animation scale reduced/off;
  the pager must retain the correct selected page and stop cleanly.

Pure JVM tests check page order, retention and gesture gating. PR CI checks
compilation and lint; final WebView and gesture conflicts require hardware QA.
