# MPEI Neo — motion system development plan

**Status:** approved design direction; planning only. All tasks below are open. No UI or animation code changes in this document.
**Parent plan:** [NO_HOME_FOUR_TAB_NAVIGATION_PLAN.md](NO_HOME_FOUR_TAB_NAVIGATION_PLAN.md).
**Decision:** use a small, unified **Material 3 motion vocabulary** with limited expressive accents. Keep interaction-driven nested Back and staged search expansion. Do *not* make every transition use an identical animation or implement a central controller for all motion.
**Target:** four directly accessible tabs (**Пары**, **БАРС**, **Почта**, **Корпуса**), no Home. Implement motion *with* the no-Home redesign to avoid refactoring disposable Home transitions twice.

## 1. Goals and non-goals

### Goals
- Every transition conveys **intent**: switch between equal-level services, enter/leave a detail, reveal search, open a component, change local data or provide input feedback.
- Consistent position, direction, speed, opacity and gesture response across comparable features.
- One route state owner; visuals, app-bar actions, selected-tab indicator and accessible focus all follow the *same* state transition without a one-frame mismatch.
- Nested interactive Back directly follows the finger; cancellation restores the source without a visual pop; completion reveals the real underlying parent.
- Animations are interruptible. A newer user action wins over a superseded animation, but no closing callback runs twice.
- Preserve BARS and OWA authenticated WebViews, map gestures, service state, and system insets.
- Minimize layout and composition work per animation frame; test on an actual compact low/mid-range phone.
- Respect system animation settings, motion accessibility requirements, and Android predictive Back.

### Non-goals
- No new service, no fifth Home tab, no horizontal swipe pager for changing services.
- No gradients, bloom, universal card-to-page morph, decorative overshoot, list stagger, or attention-seeking motion added by default.
- No forced addition of Navigation 3 or third-party animation dependency. Evaluate existing Compose APIs first.
- No business data flow changes (BARS extraction, OWA protocol, mail state, grades, map data).
- No production animation changes in this **planning PR**.

## 2. Confirmed current implementation and problems

The following paths are in the main branch as examined on 2026-10-23. Recheck against main at implementation time.

| Current path | Observation | Migration intent |
| --- | --- | --- |
| \`ui/MpeiNeoApp.kt\` | Root service pages use \`reveal\` + manual \`animateReveal()\`; root Back reveals Home, and top-bar context can change before content finishes. | Delete Home-related transitions; introduce tab-switch motion distinct from nested push/pop; align app bar and foreground identity. |
| \`ui/BackSlideState.kt\` | Shared manual progress/exit state, 310 ms enter, 260 ms exit, 20% swipe threshold; feature-level \`BackSlideState\` copies exist. | Reuse gesture-progress concept; separate route commit, swipe progress and settlement policy; centralize tunable motion tokens, not all component states. |
| \`ui/BarsDashboardContent.kt\` | Native BARS discipline uses \`detailSlide\` with its real overview underneath. | Preserve this exact parent reveal; unify with other nested transitions. |
| \`ui/MailScreen.kt\` | Message and composer each have \`BackSlideState\`, with separate scene and closing callbacks. | Keep separate nested state but apply shared transition contract and completion rules; never lose a draft. |
| \`ui/BarsScreen.kt\` | Browser animation moves WebView, exit zone and toolbar independently using one progress; session remains alive. | Preserve single WebView and identical translation for all browser parts; no WebView shared-element morph. |
| \`ui/EmbeddedBrowserExitZone.kt\` | 80dp left strip with drag threshold, separate from browser history Back. | Preserve touch isolation; coordinate predictive/system Back and explicit exit. |
| \`ui/SearchRevealMotion.kt\` | Search has one normalized progress with staged field, backdrop, controls and results. | Keep staged, **element-by-element** expansion; source anchor must work from compact icon actions as well as full-width search. |
| \`ui/GlobalSearchScreen.kt\` | The input transforms between measured bounds; controls move from the edges and results enter later. | Prevent source geometry/IME jumps and interrupted search glitches. |
| \`ui/SearchAppBar.kt\` | Shared app bar shows different actions by destination; activation triggers a separately rendered search overlay. | Define destination-specific search entry while synchronizing toolbar controls with route motion. |
| \`ui/ScheduleAgenda.kt\` | Week change uses 140 ms crossfade; pull-to-change-week follows the finger through an independent vertical visual offset; obsolete comment refers to a bottom search dock. | Retain physical vertical week gesture; add subtle directional feedback *if reliable*; prevent concurrent week animation and pull movement. |
| \`ui/ServiceRouteStack.kt\` | Root route and swipe policies still encode Home as parent. | Replace policy for no-Home four-tab navigation; avoid full-screen Back gesture stealing Map/Schedule interaction. |

**Root cause:** good local animations exist, but there is no shared transition semantics contract for equal-level navigation versus nested Back, app bars and keyboard coordination.

## 3. Motion vocabulary — proposed starting values

**All values are hypotheses for prototype/device tuning, not measurements or hard limits.** Use shared tokens in one small Kotlin motion policy layer when implementing.

| Type | Trigger and relationship | Recommended visual treatment | Initial values |
| --- | --- | --- | --- |
| \`TAB_SWITCH\` | Switch among equal-level Пары/БАРС/Почта/Корпуса | Root container remains stable; content short crossfade with optional very small displacement (no full-width slide); Material navigation selected indicator animates. | 180–220 ms |
| \`DETAIL_PUSH\` | BARS discipline, Mail message, Mail compose, Settings or true nested native detail | Foreground page enters from right while real parent stays composed; minimal/nonexistent parent scaling. | 260–310 ms |
| \`DETAIL_POP\` | Back from a nested page | Foreground page follows finger toward right, exposing real stationary parent; button/system Back uses same final positions. | Finger-driven; settle around 150–260 ms, tune based on remaining distance and velocity |
| \`SEARCH_EXPAND\` | Tap contextual search field/icon | Field expands from real entry location; controls enter from viewport edges; backdrop and results reveal in staged progress. No whole-page fade as the primary transition. | 280–320 ms |
| \`SEARCH_COLLAPSE\` | Back or close search | Exact visual inverse of expansion (including input and toolbar); dismiss IME without layout discontinuity. | Similar or slightly shorter, interruption-safe |
| \`LOCAL_CONTENT\` | Switch timetable week, Inbox/Sent view, BARS subreport, loading/content states | Fade or subtle content-direction cue; stay inside existing page and keep global chrome stationary. | 120–200 ms |
| \`MATERIAL_COMPONENT\` | Dialog, sheet, menu, toggle, chips, navigation indicator | Material 3 defaults when possible; only customize if there is a clear need. | Library defaults |
| \`DESTINATION_FOCUS\` | Explicit link to a lesson, BARS discipline or Campus building | Short, one-shot Monet outline or existing building focus; no pulse when opening a tab normally. | Preserve existing brief pulse; avoid multiple pulses per request |
| \`GESTURE_FEEDBACK\` | Drag/pull to change week, map pan/fling, nested Back | Position tracks gesture, then snaps/settles physically on release. | Frame-synchronous drag; no fixed tween while finger is down |
| \`NO_MOTION\` | Initial frame from saved state, background refresh, data reload that changes no meaningful layout, animator-disabled fallback | Show correct state immediately without fake forward navigation. | Snap / minimal crossfade only when accessible |

Motion direction must express navigation structure:
- **Horizontal full-page movement**: deeper nested page or returning from it. This should never imply a service hierarchy for ordinary tab switches.
- **Vertical/short fades**: content changes within a current page; week gesture remains vertical.
- **Source-anchored expansion**: search and *selective* true shared-element transitions.
- **Menus/sheets**: use Material component behavior, not ad hoc custom slides.

Do not make every property change animate; input, typography and static toolbars must remain readable throughout transitions.

## 4. Shared runtime contract (implementation guidance, no code yet)

Favor a **motion policy / token set** plus several small scoped state holders over a monolithic "AnimationManager."

**One route owner** resolves: \`selectedTab\`, foreground route, nested parent and optional explicit cross-service return origin. Presentation responds to route identity and a transition descriptor; screen-specific state stays with the feature.

Possible transition descriptor: \`type\`, \`from\`, \`to\`, \`requestId\` and current normalized progress. \`requestId\` identifies the *latest* navigation intent. It must not include grades, message bodies, cookies or auth URLs.

Lifecycle:
1. **Idle** — scene rendered in stable final position; no ghost overlay or pending completion.
2. **Entering** — destination and source are mounted if needed; positions derive from one progress; route and toolbar know the intended foreground.
3. **Interactive** — gesture directly drives progress (no tween competing with touch).
4. **Settling** — after release, resume toward 0 or 1 from current offset/velocity.
5. **Committed/Cancelled** — route state updated exactly once and obsolete overlays released. A cancelled gesture leaves the parent/child state unchanged.

Invariants:
- Visual destination, selected tab, app-bar context and accessibility focus cannot disagree when the transition is stable; no frame in which old actions interact with new content.
- Rapid A -> B -> C switches: **C wins**. No late A/B completion or duplicate stack pop.
- Reversing gesture mid-swipe or aborting it must not destroy the child, reload data or reset scroll.
- Completed Back commits once; cancelling does not change navigation.
- One normalized progress drives the same page, toolbar and gesture affordance; separate independent motion only where necessary for hierarchy (e.g. search's staged elements).
- The root bar stays mounted during tab switching; transitions affect *content* and selected indicator, not the entire Scaffold.
- Keep actual previous native page composed during a nested return; do not take a screenshot or create a second BARS WebView as an animation surrogate.
- Recomposition or a change to screen size/IME bounds cannot leave an animating page offset permanently wrong.
- Explicit cross-service links (Schedule -> Campus, Schedule -> BARS) may use an origin-aware transition and return target; ordinary bottom-tab taps do not push on a global history.

### Predictive Back and device/system Back

- Integrate AndroidX \`PredictiveBackHandler\` for custom nested flows where it improves the system gesture. Use its progress stream to drive the *same* transition that on-screen Back triggers.
- Define fallback behavior on older gesture/3-button navigation and test Android's system Back; native WebView history remains separate from exiting the browser.
- **At root Schedule, allow the OS to handle Back-to-home.** Do not install an always-enabled intercept solely for logging or custom animation, since doing so may suppress Android's system Back-to-home preview.
- At other root tabs, if the chosen no-Home navigation policy is "Back goes to Schedule", that intercept is intentional. Once at Schedule root, release Back to the OS.
- Do not reimplement a full-width Back overlay on a map, horizontal list, embedded WebView or schedule gesture.
- Android references: https://developer.android.com/develop/ui/compose/system/predictive-back ; https://developer.android.com/develop/ui/compose/system/predictive-back-progress ; https://developer.android.com/develop/ui/compose/system/predictive-back-setup .

### Motion interruption and system settings

- For gesture settlement, prefer a **critically damped / non-bouncy spring** or another bounded settle curve with no overshoot beyond the viewport. Choose based on testing, not on fixed milliseconds for every release distance.
- Animation settings: support system animator-disabled or zero-scale mode by rendering end states immediately, while preserving route commit and callback ordering. Check theme changes and visibility/resume during transitions.
- No mandatory user-facing "animation speed" configuration at this stage. Use OS settings and a debug-only test override if needed.

## 5. Detailed screen contracts

### A. Four primary tabs
- Tab press registers navigation immediately. Selected indicator animates while source content is replaced with the destination; do not wait for the old screen's exit slide.
- Root navigation bar must not move horizontally with the page.
- Restore last visited week/list position/map camera/BARS discipline or mail folder according to navigation plan.
- Enter a tab from a cold start or a notification **without** a fictitious switch animation from Schedule.
- Tab reselction on Schedule root returns to today; no extra page push or repeated large transition.
- Ensure transitions do not trigger new BARS WebView initialization or Mail reauthentication.
- Rapid tapping must not show an intermediate unintended screen or leave the bar with the wrong selected item.

### B. Native nested BARS/Mail/Settings
- Push: current page remains as the real underneath surface; child enters from right.
- Back: child slides right following touch; on cancellation it returns to exact prior state. Child must not visually pop in the last frame.
- All nested parents share **relative movement**, not necessarily identical total duration at every drag velocity.
- BARS discipline/goal planning, Mail message/compose, Settings and reports must use explicit parent/back policy; no "Back to Home".
- For sensitive draft state, Back behavior must preserve the current close/discard/autosave semantics.

### C. Embedded browser
- Treat BARS and OWA native WebViews as *retained views*, not as shared-element sources.
- Animate the WebView, browser bar and exit affordance together from exactly one progress value; no toolbar lag, blank top strip or WebView flash.
- Differentiate webpage history Back from leaving browser mode. Respect keyboard focus, login/2FA and browser search fields.
- Keep reserved edge-strip gestures narrow; do not intercept browser scroll or the map keyboard.
- On auth/login exit, return to the appropriate native BARS/Mail screen and keep recovery/cache status visible.

### D. Search
- Preserve independently animated field, actions, surface and results; **no single full-page mask morph**.
- From a text field, the field transforms between measured source/destination positions.
- From a compact search icon, use a controlled source-anchored expansion or immediate entry-field construction inside the top app bar; do not stretch a 48dp icon into an unreadable TextField. Choose one consistent policy after prototype screenshots.
- Freeze source bounds at activation; handle unmeasured bounds, screen rotation and source disappearance with a safe fallback. Reverse to the actual visible source whenever possible.
- Allow search to open/close in quick succession without removing its parent Scaffold or replaying an orphan transition.
- Keyboard should only receive focus after the input is ready; on Back prefer proper IME dismissal/search policy before leaving an unrelated service.
- Do not animate a service change through a stale search scope. Closing/selection must clear the right overlay and not lose target focus.

### E. Timetable and local state
- Keep week pull movement directly controlled by finger. If a directional week transition is added, it must not compete with ongoing pull-offset animation.
- Use 120–200 ms fade or small directional hint for loaded week changes; avoid animating 7 days of content as many independent cards.
- Keep list items stable via keys; animate only meaningful insertions/removals, not cache verification or every refresh.
- Current lesson progress is data/time feedback, not a repeating attention animation.
- Direct target highlights remain one-shot and may animate even when most navigation motion is reduced, subject to accessibility policy.

### F. Status, feedback and chrome
- Standardize toggles/chips/badges through Material components. Prefer instant or subtle change for an unread dot / new grade, not pulsing badges.
- Buttons and toolbar controls should enter with the **same navigation state change**, rather than waiting until a page transition finishes; any deliberate stagger must be part of an explicit shared timeline.
- Search, Settings, refresh and compose controls retain consistent touch targets and do not overlap navigation indicators.
- Loading indicators follow Material defaults, with no whole-screen page reveal just because data refreshed.
- Avoid unnecessary splash delays beyond the existing system splash behavior. Returning to an already mounted app should not replay the splash animation.

## 6. Accessibility and performance contracts

- Test animator duration scale 0x and normal scale; verify no stuck dim surfaces, gesture state, or deferred route callbacks.
- Test TalkBack: focus moves to the new screen heading when nested route is opened; tab labels and selected states remain descriptive. No focus jumping to offscreen retained BARS WebView.
- Test narrow screens, 1.3x+ font scaling, landscape, IME, gesture navigation and 3-button navigation.
- Respect lifecycle: rotation/background/foreground mid-transition does not expose stale source/destination or fire delayed callbacks afterward.
- Prefer \`graphicsLayer\` transform/alpha for movement and fades; avoid per-frame changes to full screen layout measurement. Use Compose tracing and profiler to confirm, not guess.
- Count frame misses and jank with the existing UI frame profiler. Compare before/after on the **same physical device, task sequence and build type**; no invented speed/performance claims.
- Prototype on the user's phone and verify BARS + OWA retained WebViews, which have different performance costs from pure Compose pages.
- Performance stop rule: if a shared-element or nonessential expressive effect causes visible frame drops, touch issues or session disruption, remove it and keep the simpler motion policy.

## 7. Implementation checklist — all pending

### Phase M0 — specification, measurements, tests
- [ ] Freeze navigation roles from the no-Home plan, including Back policy and nested bar visibility.
- [ ] Inventory every \`AnimatedContent\`, \`AnimatedVisibility\`, \`BackSlideState\`, \`animate*\`, \`graphicsLayer\`, \`BackHandler\`, gesture exit zone and local tween; classify by vocabulary above.
- [ ] Record short videos / frame traces of current root transition, BARS detail, Mail message, Mail compose, browser exit, search, week pull and Settings.
- [ ] Define tokens and deterministic transition-state unit tests before visual implementation.
- [ ] Document interruption cases and route commit ownership, including Activity recreation and update relaunch.

### Phase M1 — shell and tab motion (with navigation redesign)
- [ ] Introduce semantic transition types + shared token definitions with feature-owned progress.
- [ ] Implement root tab crossfade/subtle motion and persistent NavigationBar selection.
- [ ] Remove root slides to/from Home, Home reveal and root swipe-to-Home.
- [ ] Synchronize route/tab, app bar, toolbar controls, IME and root insets during transitions.
- [ ] Define reduced/disabled-animation fallback and predictable rapid-switch cancellation.

### Phase M2 — nested and system Back
- [ ] Reuse/refine BackSlideState semantics for BARS details, Mail messages/compose, Settings and browser shells.
- [ ] Integrate predictive Back progress for applicable nested routes and hardware/system Back fallback.
- [ ] Guarantee exact parent reveal and single route commit on success; no route mutation on cancel.
- [ ] Preserve WebView instance, input state, login/2FA and correct browser-history behavior.
- [ ] Eliminate slide teleport, one-frame old-page pop and asynchronous toolbar exit mismatch.

### Phase M3 — search and local content
- [ ] Preserve staged element search expansion; support text-field and icon entry anchors.
- [ ] Handle Search/IME state, source bounds and reverse transition when switching tabs or closing quickly.
- [ ] Align Schedule week and content-change animations with local-content tokens.
- [ ] Align Material components, grade/mail status indicators and explicit destination focus.
- [ ] Consider *one* optional pure Compose shared-element prototype after everything above is stable; keep it only when useful and measurable.

### Phase M4 — validation and cleanup
- [ ] UI-test Back/cancel/commit, repeated quick open-close, A->B->C tab sequence, rotation and process recreation.
- [ ] Test Schedule->Campus->Back, cross-service lesson/BARS links, Mail notification, Settings return, browser history/exit.
- [ ] Test map pan/fling/pinch, timetable swipe, keyboard in search and WebView under gesture navigation.
- [ ] Verify zero animator scale, screen readers, font scale and narrow/landscape layout.
- [ ] Compare profiler, frame pacing, touch latency, WebView memory and start-up before/after; tune/remove costly effects.
- [ ] Update tests, component docs and architecture rules so new screens use approved motion patterns rather than ad hoc durations.
- [ ] Complete Dev/R8 smoke tests and device checks before merging any implementation PR.

## 8. Code ownership / impacted files

| Layer | Likely files | Responsibility |
| --- | --- | --- |
| Motion policy (new small UI helper) | e.g. \`ui/AppMotion.kt\` | Named specs, progress math, common transition types; no network or route owner |
| App shell | \`ui/MpeiNeoApp.kt\`, \`ui/ServiceNavigationState.kt\`, \`ui/ServiceRouteStack.kt\` | Root transitions, foreground destination, Back policy and stable app bar/nav state |
| Nested back | \`ui/BackSlideState.kt\`, \`ui/NavigationExitTransaction.kt\` | Interactive state/settlement and single completion semantics |
| Features | \`ui/BarsDashboardContent.kt\`, \`ui/BarsScreen.kt\`, \`ui/MailScreen.kt\`, \`ui/MailComposeScreen.kt\` | Use shared policy without creating duplicate WebView/feature state |
| Search | \`ui/SearchRevealMotion.kt\`, \`ui/SearchOverlayState.kt\`, \`ui/GlobalSearchScreen.kt\`, \`ui/SearchAppBar.kt\` | Source-anchored expansion, staged content and IME ownership |
| Timetable / campus | \`ui/ScheduleAgenda.kt\`, \`ui/CampusMapScreen.kt\` | Local week transition and safe gesture arbitration |
| QA | \`app/src/test\`, \`app/src/androidTest\`, \`docs/PERFORMANCE_PROFILING.md\`, \`scripts/check_architecture.py\` | Reproducible correctness and jank checks, architecture constraints |

## 9. Acceptance checklist / definition of done

The motion redesign can be considered complete only when:

1. Tab changes feel immediate; no full-screen forward/back slide or temporary Home frame; correct selected indicator and content.
2. A nested BARS or Mail page can be opened, swiped partially, cancelled, and swiped out, with the real parent continuously visible and correct state after each path.
3. Search opened from every screen expands as **multiple coordinated elements**, and a quick reverse never causes a blank overlay, wrong search hint or keyboard jump.
4. No single-frame snap at the end of any page reveal/dismissal, including BARS browser and Mail composer; toolbar and content do not separate.
5. Rapid navigation, repeated Back, update relaunch and activity recreation cannot invoke stale completion callbacks, duplicate navigation or restore a Home route.
6. Map pan/zoom, week pull, browser history and text input remain reliable.
7. BARS and OWA authenticated sessions, offline cache, notification flows and background tasks behave the same as before.
8. All animations respect system animation scale/reduced motion policy and do not block TalkBack, Back or tap targets.
9. The existing performance profiler shows no unacceptable jank or first-interaction regression on tested hardware; costly decorative effects are removed rather than excused.
10. New motion constants and semantics have one documented home, and direct custom hard-coded scene transitions are either removed or documented as deliberate exceptions.

## 10. Proposed future implementation PR split

1. **Core motion and four-tab shell:** tokens, transition semantics, no-Home tab animation and root Back; coordinated with navigation foundation PR.
2. **Nested/back/browser motion:** gesture progress, predictive Back, Mail/BARS/Settings nested pages, retained WebViews.
3. **Search and polish:** staged search from new contextual entries, week/local transitions, accessibility, profiling and cleanup.

Keep this as a *planning file within the existing no-Home draft PR*. Do not create a competing implementation PR or claim the motion system has been implemented.

### Sources

- Compose animation guide: https://developer.android.com/develop/ui/compose/animation/quick-guide
- Customize specs and springs: https://developer.android.com/develop/ui/compose/animation/customize
- Predictive Back in Compose: https://developer.android.com/develop/ui/compose/system/predictive-back
- Predictive Back manual progress: https://developer.android.com/develop/ui/compose/system/predictive-back-progress
- Predictive Back system setup: https://developer.android.com/develop/ui/compose/system/predictive-back-setup
- Compose shared elements: https://developer.android.com/develop/ui/compose/animation/shared-elements
