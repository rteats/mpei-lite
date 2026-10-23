# MPEI Neo: four-tab, no-Home navigation — implementation specification

**Status:** design decision approved; implementation NOT started.  
**Scope:** replace the super-app launcher with four direct service destinations using existing functionality only. No new services.  
**Primary device:** compact Android phone, portrait first; tablets and landscape must remain usable.  
**Source of truth:** this plan is proposed future behavior; README, UI_COMPONENTS and ARCHITECTURE describe the *current* application until implementation ships.

## 1. Decision and rationale

MPEI Neo has four stable services: **Пары**, **БАРС**, **Почта**, **Корпуса**. The current Home presents a next-lesson preview followed by four editable service-launch tiles. This makes the UI recognizable, but the Home itself is also a mandatory stop when moving between ordinary services. Its tile editor (compact/regular/expanded, ordering, persistence) is disproportionate to a fixed set of four destinations.

**Decision:** delete Home as a destination and use a Material 3 bottom navigation bar with exactly four labeled items: **Пары | БАРС | Почта | Корпуса**. Schedule becomes the default entry. Settings is a secondary destination, never a fifth tab. Retain the useful next/current-lesson preview by integrating it into Schedule, without showing the same lesson twice.

Goal: task-first access; direct service switching; no extra gateway or feature growth; same functionality and authenticated sessions.

### Why this is an appropriate Android pattern

Android's Compose NavigationBar is designed for 3–5 stable, equal-level destinations. Multiple back stacks can retain state between top-level destinations. Reference material:
- https://developer.android.com/develop/ui/compose/components/navigation-bar
- https://developer.android.com/guide/navigation/navigation-3/recipes/common-ui
- https://developer.android.com/guide/navigation/backstack/multi-back-stacks
- https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns
- https://developer.android.com/design/ui/mobile/guides/foundations/accessibility

These are design precedents, not a requirement to add Navigation 3 or change Gradle modules. Preserve the project's existing single-module architecture unless evidence justifies a dependency.

## 2. Final expected experience

### Launch and top-level navigation
- Normal fresh launch starts on **Пары**, showing the current/next-lesson summary near the top and the timetable underneath.
- Four labeled navigation items are always available on the *top-level* service surfaces. Selected item is visually and accessibly indicated. No Home route, launcher tile grid, hidden horizontal service pager, or fifth Settings tab.
- One tap changes services: BARS -> Mail, Mail -> Schedule, Map -> BARS. Returning to a tab restores its last meaningful state rather than resetting the list, search, map camera, timetable week, or loaded data.
- Selecting **Пары** again while already on its root returns to **today** (retain the familiar Schedule icon behavior). Reselecting another root may scroll to the beginning, but never clears authentication or cached data. Do not make ordinary switching reset state.
- The main tab bar remains visible in the Campus root, with map controls/pan bounds adjusted to the reduced viewport. The map has no Home-exit swipe strip.
- The main tab bar hides for true nested/full-screen flows (BARS discipline/grade planner, opened mail message, mail composer, embedded browser, full-screen search, Settings). After Back it returns to the correct selected tab. Avoid jumping/resizing the content when it hides/shows.
- For larger displays, a navigation rail can replace the bar if useful; not a prerequisite for initial rollout.

### Headers and search
- Search remains *contextual*: Schedule group/teacher/room, BARS disciplines, Mail inbox/sent, Campus building/room. Do NOT silently mix service indexes into one global results feed.
- Schedule keeps a prominent search entry; Mail and BARS can use compact search actions where space is more valuable. Campus prioritizes its existing alphabetic building selector, with access to room search. Settings has a title bar and no search.
- Preserve the **element-by-element** search expansion (input, edge controls, background and results move independently) rather than fading in or morphing a whole page.
- Anchor expansion to the actual entry control's measured bounds, even when the entry is an icon rather than a text field. Use a safe fallback if bounds have not been measured after activity recreation. No layout jump when IME opens/closes.
- The global app-bar behavior and contextual hints must follow the selected destination, not a lingering transition/previous destination.
- Offer consistent access to **Settings** from a recognizable overflow menu in each top-level destination (or a single equivalently discoverable placement that survives service changes). Keep refresh and Mail compose available without overloading the toolbar.

### Schedule as the former Home's useful content
- Move/repurpose HomeNextLessonCard into a compact, context-aware Schedule summary; place it *above* or *within* the agenda, not as a second permanent full-size copy of the upcoming lesson.
- Preserve start/end time, location, lesson type, upcoming/ongoing state, and in-progress backdrop where useful. A tap scrolls to and focuses the exact lesson (not just the day); location still links to the correct campus building.
- Preserve the BARS discipline link only when it matches a real discipline, and ensure it is optional rather than competing with basic lesson details.
- Avoid duplicated content and excessive vertical height: collapse or reduce the summary once the target lesson is already visible, and never obscure the timetable.
- Reuse the existing Moscow-time calculation and cached week data. Distinguish "no future classes" from "data not loaded", "refreshing", "failure" and "no selected timetable". Never invent a later class from absent cached weeks.
- If the next lesson is not within loaded weeks, offer an honest empty state and use the existing agenda's fetch/refresh flow; do not start an independent Home-style network pipeline.

### BARS and Mail status
- Surface genuinely useful state with restrained labels/badges on **БАРС** and **Почта**. Examples: verified new-grade change, known unread messages, login required. Do not claim "no changes" when cache is missing/stale or login expired.
- Decide whether unread count or simple dot best fits the current source of truth. Do not add permanent duplicate previews of grades/mail above the Schedule agenda.
- Badge state must update when the corresponding action is consumed/read, and should not expose student or mail content to telemetry.

## 3. Navigation policy — explicit behavior contract

| User action / state | Destination | Back behavior |
| --- | --- | --- |
| Cold launch, no explicit intent | Schedule root | Back at Schedule root exits the task |
| Tap a different bottom item | That service's saved root/stack | No growing history of tab-to-tab transitions |
| Back from another tab's root | Schedule root | Subsequent Back at Schedule exits |
| Tap selected Schedule root item | Schedule at today | Does not create a new route |
| Open BARS discipline | BARS discipline detail | Back returns BARS overview |
| Open BARS goal planner/report | Existing parent in BARS | Back returns its immediate BARS parent |
| Open Mail message or composer | Selected folder / draft context | Back returns Inbox/Sent or prompts appropriately for unsent data |
| Enter embedded BARS or OWA portal | Full-screen WebView | Web history first if intended; explicit exit returns native service, NEVER old Home |
| Tap a Schedule classroom link | Focused Campus building | Back returns originating Schedule lesson/week; plain Campus tab visits do not create Schedule link history |
| Search -> select result in same service | Exact target | Back uses that service's nested stack |
| Explicit link between services | Target service + one-shot origin | Back can return to the originating service/context; manually choosing a tab clears that transient origin |
| Mail notification on cold/warm app | Mail folder/message | Details close to Mail; no intermediate Home; preserve task and intent behavior |
| Open Settings | Settings detail from current service | Back returns that service, not Schedule or Home |
| Switch tab while a nested route is active | Newly selected tab | Returning to original tab restores its nested context if safe |
| System recreates Activity | Saved destination/feature states | Restore the correct tab without "Home" or phantom focus |
| Update succeeds and relaunches app | Previously selected service where valid | Preserve update-relaunch behavior and fallback to Schedule |

**Implementation decisions to verify on device:**
- Choose a simple, deterministic **exit-through-Schedule** root policy; do not push every tab selection onto a stack. Returning from an explicit cross-service link is an exception, and must be bounded to one remembered origin.
- Root-level manual full-width swipe-to-Home is removed. Keep Android system Back/predictive Back and finger-following gesture for *nested page dismissal* where it does not compete with horizontal scroll, map pan, WebView or keyboard.
- NavigationBar selection and route resolution must share **one state owner**. A selected item must always match the foreground service. A navigation source (notification, search, focus link, update relaunch, Settings) cannot desynchronize them.
- Search overlay and app-bar changes must not trigger double navigation or stale Back callbacks during transitions.

## 4. Architecture and state migration

Current code uses ServiceNavigationState + ServiceRouteSnapshot with an empty stack for Home, a single active service and a Schedule->Map special stack. MpeiNeoApp renders SuperAppHome as the background; other services push over it. It also keeps the BARS screen composed offscreen to preserve one WebView and an extraction session.

**Proposed ownership model** (refine during implementation; no mandate to use a specific navigation library):

- One root selected service enum (Schedule, Bars, Mail, Maps); **Settings is outside the tab collection**.
- Independent saveable substate/back stack per service, including scroll, current week, detail pages, selected folder, map camera and search as appropriate.
- A separate optional *transient return origin* for typed explicit cross-service links; do not confuse this with ordinary tab history.
- Shell owns the selected tab, root bar visibility, app bars, shared search reveal and route transitions. Feature screens own their detail routes and view state. Typed AppNavigationRequest + AppNavigationTargets remain the entry for deep links and one-shot focus.
- Retain the **single long-lived BARS WebView** and BarsWebViewTransport/BarsSessionCoordinator. Do not create four simultaneous copies of service screens or eagerly reload BARS merely to preserve a tab.
- Keep OWA auth, cached message state, notification worker and browser separate from top-level route ownership. Do not reauthenticate or lose drafts when changing tabs.
- Save only safe route identifiers for activity/process restoration; never save cookies, auth URLs, student/message details in navigation preferences or logs.

### Known implementation traps from current repository

1. **BARS mounted sibling:** MpeiNeoApp keeps BARS composed separately from serviceContent to avoid losing the authenticated WebView. A naive bottom-nav conversion that mounts/unmounts it on tab changes can break login, grade refresh, Summary extraction and back.
2. **Schedule->Campus:** ServiceRouteStack explicitly nests MAPS over SCHEDULE when viaClassroom=true. A flat selectedTab assignment would lose Back-to-lesson behavior.
3. **Search source geometry:** SearchOverlayState currently tracks one source entryBounds. Contextual icon entries on some tabs need correct per-entry measurement and bounds freezing during expand/collapse.
4. **Animation/gesture code:** reveal, NavigationExitTransaction, serviceBackGesture and map's 96dp strip currently assume Home beneath every service. Rework/remove only the root assumptions; preserve nested BARS/Mail page gestures and WebView rules.
5. **BARS auth exit:** onAuthBrowserExit currently calls goBackOneService; removing Home must not make exiting login unexpectedly close the entire activity.
6. **Update relaunch:** UpdateRelaunchState records a list of old route enum names; migrate/interpret it so old installs safely restore a service, normalize invalid saved states to Schedule and do not lose Shizuku relaunch behavior.
7. **Mail notification intents:** MainActivity's initialMailTab and mailNotificationTarget can override the root. Check cold start, onNewIntent and rapid duplicate tap cases.
8. **Settings:** SettingsScreen currently provides onResetHomeLayout; drop the setting, related data and obsolete tests, while preserving theme/update/mail/BARS/diagnostics settings.
9. **Appbar inset:** Scaffold currently owns a full-width top search field. Bottom nav adds a new system-inset boundary; check timetable sticky headers, keyboard, campus controls and edge-to-edge layouts.
10. **Tests/guard:** scripts/check_architecture.py and service-route / appbar tests assert the existing owner/API shape. Update guards to enforce the *new* owner and never delete checks just to silence them.

### Source hotspots (current main)

| File | Planned change |
| --- | --- |
| app/src/main/java/com/rteats/mpeineo/ui/MpeiNeoApp.kt | New root scaffold/top-level bar, tab selection, visibility, Back, insets, saved state and transition ownership |
| app/src/main/java/com/rteats/mpeineo/ui/ServiceRouteStack.kt | Replace Home-based route rules with tab/deep-link/back policy |
| app/src/main/java/com/rteats/mpeineo/ui/ServiceNavigationState.kt | One saveable tab owner; multiple feature histories and typed origin routing |
| app/src/main/java/com/rteats/mpeineo/ui/AppNavigationRequests.kt | Remove Home-specific intent, preserve/reuse exact target focus and notifications |
| app/src/main/java/com/rteats/mpeineo/ui/DestinationFocus.kt | Keep explicit, one-shot cross-feature pulse; no stale highlight on normal tab select |
| app/src/main/java/com/rteats/mpeineo/ui/HomeDashboard.kt | Extract reusable next-lesson domain logic and compact Schedule summary; delete old redundant Home previews |
| app/src/main/java/com/rteats/mpeineo/ui/SuperAppHome.kt | Remove after preview migration and test coverage |
| app/src/main/java/com/rteats/mpeineo/ui/HomeLayoutPreferences.kt | Remove tile sizes/editor/preferences after successful migration |
| app/src/main/java/com/rteats/mpeineo/ui/SearchAppBar.kt | Destination-specific search affordances, Settings access, safe source measurements |
| app/src/main/java/com/rteats/mpeineo/ui/GlobalSearchScreen.kt | Keep scoped result providers and independent search-element expansion |
| app/src/main/java/com/rteats/mpeineo/ui/SearchOverlayState.kt | Preserve search state and frozen source geometry across transient transitions |
| app/src/main/java/com/rteats/mpeineo/ui/SettingsScreen.kt | Remove Home layout reset; preserve all existing settings |
| app/src/main/java/com/rteats/mpeineo/ui/BarsScreen.kt | Retain native detail stack and WebView transport; fix auth exit route |
| app/src/main/java/com/rteats/mpeineo/ui/MailScreen.kt | Preserve Inbox/Sent, drafts, message focus, OWA/browser and notification flow |
| app/src/main/java/com/rteats/mpeineo/ui/CampusMapScreen.kt | Adjust map viewport/controls and gesture arbitration for root nav bar |
| app/src/main/java/com/rteats/mpeineo/MainActivity.kt | Restore launch, notification and newIntent selection correctly |
| app/src/main/java/com/rteats/mpeineo/update/UpdateRelaunchState.kt | Migrate saved route format safely |
| scripts/check_architecture.py | Validate new navigation ownership, retain session boundaries |
| docs/UI_COMPONENTS.md, docs/ARCHITECTURE.md, README.md | Update current-state docs only after working implementation |
| app/src/test and app/src/androidTest | Rewrite Home-route tests as tab/deep-link/navigation regression tests |

## 5. Implementation checklist (not started)

### Phase 0 — baselines and contracts
- [ ] Agree on exact bottom-nav appearance: fixed order, labels, Monet colors, icon states, 48dp minimum targets, selected/unselected semantics.
- [ ] Capture before screenshots and task-step counts for Schedule/BARS/Mail/Map, search and nested Back.
- [ ] Add unit tests for desired route matrix before changing the shell.
- [ ] Identify all saved Home/route state and update migration/fallback rules.
- [ ] Select a low-dependency multiple-back-stack implementation compatible with current Compose architecture.

### Phase 1 — root structure
- [ ] Make Schedule the default root; do not create or render a Home destination.
- [ ] Introduce exactly four Material 3 NavigationBar items; no Home/Settings item.
- [ ] Implement direct tab switching with stable selected state, state restoration and no duplicate destination pushes.
- [ ] Keep each feature's remembered state; preserve BARS singleton WebView and service background-sync contract.
- [ ] Add consistent Settings access, title-only Settings app bar and return-to-origin handling.
- [ ] Implement hide/show policy for nested screens, Search, keyboard, embedded browsers and Settings; check edge-to-edge padding.
- [ ] Implement deterministic root Back: nested child first, link origin second, non-Schedule root -> Schedule, Schedule root -> exit.
- [ ] Remove root gesture and map Home-exit strip while retaining nested interactive back and Android system navigation.
- [ ] Use a short and coherent transition for **tab switches** (not fake forward push/pop to a Home parent); keep real-time gesture slides for nested Back.

### Phase 2 — task-first Schedule
- [ ] Move the useful next/current-lesson summary into Schedule using existing cached data and Moscow-time logic.
- [ ] Avoid rendering two copies of the same lesson or an oversized dashboard block above the list.
- [ ] Tap preview -> exact lesson, correct date/week and focus outline; ordinary tab select produces no focus cue.
- [ ] Keep room -> Campus and matched discipline -> BARS links, with correct return origin.
- [ ] Handle no selected timetable, no cache, pending load, refresh failure, expired lesson and end-of-week distinctly.
- [ ] Keep selected week, Today/reselect behavior, week scrolling and progress bar correct.

### Phase 3 — contextual app bars and badges
- [ ] Schedule: prominent search + existing week-refresh action without overflowing on narrow phones.
- [ ] BARS: compact discipline search and existing native grade/planner controls.
- [ ] Mail: compact inbox/sent search and compose control, with no duplicate search controls.
- [ ] Campus: preserve alphabet keyboard; optional room search, no misleading default full-width app search.
- [ ] Preserve search-element expansion from each source shape, including IME/layout restoration and reverse animation.
- [ ] Show only verified unread/new-grade/login status; avoid "all clear" when source state is unknown.
- [ ] No added Home-style grade/mail cards, service-scope chips, universal search index or new network calls.

### Phase 4 — remove the obsolete launcher
- [ ] Remove SuperAppHome and Home tile editor, resize/reorder controls and preferences.
- [ ] Remove reset-Home-layout from Settings.
- [ ] Remove Home-only search entry and FocusHomeLesson request; keep generic next-lesson focus logic where still needed.
- [ ] Remove Home-specific animation/background assumptions and legacy logging names; keep bounded transition state.
- [ ] Migrate update-relaunch and saved routing data; older installs must not crash.
- [ ] Update README, UI_COMPONENTS, ARCHITECTURE and test tags/screenshots to describe shipped behavior.

### Phase 5 — correctness and polish
- [ ] Unit test back stacks, tab reselect, link origin, saved-state restore, tab selection with a queued transition, invalid saved routes.
- [ ] UI test no Home on launch; four tab items; tap any tab from any other in one action; Settings accessible.
- [ ] UI test Schedule->Map->Back, Schedule->BARS->Back, mail notification -> exact message -> Inbox; no stale focus pulse.
- [ ] Verify BARS cold launch, grades after re-login, browser 2FA, Main->Summary, background extraction, WebView continuity across tabs and interrupted navigation.
- [ ] Verify OWA inbox/sent, message read-state, compose/draft/attachment, notification delivery, background worker and WebView auth.
- [ ] Verify map pan/fling/pinch/alphabet keyboard do not conflict with root navigation or system-back gesture.
- [ ] Verify search from each service, IME, screen rotations, process recreation, narrow screens, 1.3x+ font scale, light/dark Monet and screen reader.
- [ ] Check reduced-motion/system animator-off behavior, no visual frame pop, touch target sizes, contrast and focus order.
- [ ] Keep current performance profiler; compare startup, BARS first-use, tab-switch frames and memory against pre-change baseline.
- [ ] Run architecture guard, unit tests, Android UI tests, lint and R8 Dev build; manually test on device before merging.
- [ ] Confirm Dev update (including Shizuku auto-update/relaunch) returns to the correct selected service or safe Schedule fallback.

## 6. Expected result / release acceptance

**Must pass:**
1. At launch there is no launcher grid or route named Home; the first usable screen is Schedule unless a valid explicit intent/update restore selects another service.
2. Exactly four labeled direct service choices remain visible at top level and accessible in one tap; switching preserves previously loaded content and local position.
3. Existing Schedule/BARS/Mail/Campus/Settings functionality, deep links, WebViews, authentication and notifications still work.
4. The next-lesson summary is immediately useful without a redundant second large lesson card; tapping it leads to the correct lesson.
5. Nested Back returns to its proper parent; root Back never reveals an obsolete Home; specific Schedule->Campus link returns to Schedule.
6. Search stays contextual; transition remains an expansion of individual elements, not a whole-page morph; no keyboard or app-bar jump.
7. Root map pan/zoom, BARS extraction and OWA account/session continuity are not regressed.
8. No unfinished navigation flags, obsolete Home editor settings, duplicate WebViews or stale Home tests remain.

**Targets for an eventual usability comparison, not claimed outcomes:**
- BARS -> Mail and Mail -> Schedule need one deliberate top-level action instead of Back+tile.
- Task flows should need fewer navigation actions with no measurable increase in error rate.
- The timetable should retain at least the same at-a-glance lesson utility as the old Home.
- Frame timing, first usable screen, memory and BARS cold-start correctness should not regress.
- Test with several real students and at least one compact portrait device before declaring the redesign better.

## 7. Explicit non-goals / constraints

- No implementation is part of the planning PR.
- No new services, feature rewrites, universal cross-service search, chat/dashboard feed, or additional customization layer.
- No new Compose/Navigation dependency unless the existing code cannot reliably support the stated contract.
- No reintroduction of a horizontal service pager or global full-screen gesture that steals map/schedule swipes.
- Do not alter BARS parsing, OWA protocol, credentials, grade forecasts or data caches as part of visual navigation work.
- Do not treat cosmetic animations as a substitute for task flow correctness.

## 8. Suggested implementation PR sequence

1. **Navigation foundations:** route model, root bar, state restoration and tests; preserve old UI until migration works behind a development switch if needed.
2. **Screen/interaction migration:** Schedule lesson summary, correct cross-links, contextual toolbars/search, settings, status.
3. **Removal and regression:** eliminate Home remnants, update docs/guards, device verification, performance and R8.

These stages are recommendations for later coding PRs. This specification PR contains documentation only and should remain unmerged/draft until a maintainer intentionally adopts it as the baseline plan.
