# Material 3 Expressive motion: decisions and implementation

Sources reviewed on 24 October 2026:
- [Google Material motion physics](https://m3.material.io/styles/motion/overview/how-it-works)
- [Applying easing and duration](https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration)
- [Transition patterns](https://m3.material.io/styles/motion/transitions/transition-patterns)
- [Compose Material 3 MotionScheme reference](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)

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
