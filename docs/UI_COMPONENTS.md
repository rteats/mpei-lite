# MPEI Lite · UI component map

| What you see | Compose implementation | Behavior |
| --- | --- | --- |
| Four-tab navigation | FourTabNavigationBar.kt / MpeiNeoApp.kt | Material 3 tabs: Пары, БАРС, Почта, Корпуса. Schedule is default; Settings is secondary. Tab switches crossfade without creating a tab history. |
| Nested Back | MpeiNeoApp.kt / BackSlideState.kt | Actual parent reveals under Settings and explicit Schedule-to-Campus links. Root Back from other tabs returns Schedule; root Schedule leaves system Back to Android. |
| Schedule service | MpeiNeoApp.kt / ScheduleAgenda.kt | Schedule has its own scrolling and week gestures, independent of service navigation. |
| Shared search app bar | SearchAppBar.kt | Contextual top search, Schedule refresh, Mail compose, and Settings from each service. Search expands its field, edge controls and results separately. |
| Scoped search results | GlobalSearchScreen.kt | Search is limited to the selected service; cached BARS/Mail matches and Schedule targets use existing providers. |
| Schedule target picker | ScheduleDockedSearch.kt | A separate favorites button opens the existing specialized timetable picker, including favorites, rename and category filters. The normal search bar opens unified scoped search; week-refresh button remains. |
| Campus building selector | CampusMapScreen.kt | Map uses its alphabetic building keyboard rather than a misleading free-text search: choosing a building focuses/highlights the actual vector geometry. |
| Favorite row overflow | ScheduleDockedSearch.kt | Rename or remove favorite. Blank name restores the original display name. |
| Custom-name persistence | UserPreferences.kt | Names stored separately by schedule-target type and id; existing favorites are preserved. |
| Date-grouped timeline | ScheduleAgenda.kt | Native sticky day headers in a bounded, scrolling LazyColumn. Pull to refresh; weeks loaded on demand. |
| Class card and summary | ScheduleNextLessonStrip.kt / ScheduleScreen.kt | Compact current/next-lesson strip links to the exact cached lesson in the timetable. Lesson cards retain inline type, classroom and progress. |
| BARS «Автомат» plan | BarsAutoPlanner / BarsAutoPlan.kt / BarsAutoFocus.kt | Goal-first weighted 4.20 planner with impact-ranked upcoming tests, editable future marks (3/4/5), a minimum-grade-step recommendation, bounds and a compact cross-course focus panel. Actual BARS grades stay fixed. |
| Settings | SettingsScreen.kt | Update, diagnostics and cache; refresh-on-launch toggle removed. |

## Data behavior

Favorites are stored in the existing DataStore favorites list. Optional custom names
live in a separate map keyed by ScheduleTarget.favoriteKey() (type plus id).
The stored target retains its official name, which remains searchable.
Removing a favorite also removes its alias. Local favorites match even one-character
queries, while remote search is debounced by the ViewModel.

At every app launch the selected current week is loaded from cache if available
and refreshed from the network; the user can also pull down in the agenda for
an explicit refresh. The former month/year, Today button, stale badge, refresh
button, and target selector card are removed.

The application launches on Schedule. Four equal-level destinations occupy a
Material 3 bottom navigation bar. A tab switch retains saveable state and uses
a short crossfade. The BARS WebView remains in one long-lived composition.
The bar hides for Settings, full-screen search, embedded browsers and native
Mail/BARS details. Settings is available in the app-bar controls on each tab.


## Linked destination focus

The Schedule next-lesson strip resolves a cached lesson's real week,
date, start time and classroom. Its one-shot target scrolls into view with
the existing outline. Ordinary tab switching does not trigger highlights.
Campus building links retain an explicit return origin; routine tab visits
clear that transient link.


## Service navigation, back swipes and WebViews

Four primary services use direct tabs. Nested native details and Settings
retain a parent-reveal rightward Back animation; tab changes do not slide
over an imaginary Home page. Root Back returns to Schedule, where Android
handles Back-to-home. The Campus root no longer has a service-exit swipe
zone; a narrow zone is available for an explicit Schedule-to-Campus return.
BARS and Mail WebViews preserve their separate history and exit behavior.


## BARS weighted-grade forecasts

The new planner consumes only BARS control activities with readable weights.
Fractional weights, decimal commas and percentages are supported; absent or
inconsistent weights are not guessed. Actual graded work is fixed, and students
can assign only 3, 4 or 5 to future KMs. It shows the weighted 4.2 goal,
current earned sum, future-3 lower bound, future-5 upper bound and whether
4.2 remains achievable. The default recommendation raises grades on
higher-weight controls first, minimizing grade-point improvements relative to
all threes; the app does not pretend to know test difficulty. Each upcoming
KM shows its name, week, weight and marginal score gain. Scenarios persist
across tab/detail navigation via saveable in-memory state; the state resets
when the student/semester/official KMs change. The BARS subject overview
surfaces up to three cross-course priorities for convenient navigation.
The old enumeration solver remains for compatibility/tests but is no
longer the user-facing layout. A 4.2 prediction is not a guarantee that an
instructor will grant an automatic exam grade. No scenario ever writes to BARS.

## Native MPEI mail (OWA only)

Mail uses the university Exchange OWA `service.svc` protocol through the
currently authenticated WebView session. No IMAP credentials, IMAP socket,
certificate pinning, or SMTP transport are used. The native Inbox and Sent
folders support pagination, search, previews, read-state synchronization,
recipient suggestions, and sending text messages with attachments. The
Compose button is a FAB; the official ОСЭП browser remains available for login.

Conversation headers and opened message bodies are stored in an encrypted
per-account cache under `noBackupFilesDir`. Background notification polling
uses WorkManager and the same OWA protocol, then broadcasts refreshed headers
to the live inbox. On logout, local OWA cache and cookies are cleared without
touching the separate BARS session.

Previously supported IMAP credential and trust settings are cleared for
upgrading users; obsolete JavaMail and TLS transports were removed.

## Campus map

The Campus service in `CampusMapScreen.kt` renders an offline OpenStreetMap
campus footprint via Compose Canvas with Monet colors. Drag with one finger
to pan, and release a quick one-finger drag to let the map glide and slow to a stop.
The fling stops at the map edges or on the next touch. Pinch zoom stays centered
under the fingers and does not trigger a fling. Buildings remain tappable;
zoom/recenter controls are still available.
The map reserves a 96dp-wide left-side strip (excluding the OS's first 24dp)
between the top and bottom controls for interactive right-swipe navigation.
Everywhere else, horizontal drags move the map rather than navigate. The
Android system Back gesture and hardware Back continue to navigate normally.
OSM attribution remains visible.
See `docs/OFFLINE_CAMPUS_MAP.md`.


## Contextual search model

Search stays in the current service. Home opens timetable search (not a
cross-service mixer); Schedule opens the same schedule provider with timetable
target-type chips; BARS opens discipline lookup; Mail searches both cached
Inbox and Sent headers; Campus searches building identifiers and classroom
targets. Back closes search without changing the active service.

There is no service-scope chip bar and no stored global query history.
Schedules still support favorite toggles and aliases in the search results,
and their category filtering applies to favorites, remote results, and the
selected timetable. Mail result navigation selects Inbox or Sent before
opening the message. The Campus alphabetic building keyboard remains a
separate spatial control.

The source search field animates into the destination field; the IME opens
after the expansion to avoid layout jumps. Verify search activation/reversal
on narrow screens in light/dark Monet themes, including navigation and
WebView keyboard transitions.

## Maintainer boundaries

`MpeiNeoApp.kt` hosts Compose presentation and common transitions.
`ServiceRouteStack.kt` owns route identity, stack and gesture decisions;
`ServiceRouteSnapshot` derives the foreground and beneath destinations.
BARS extraction-related flags share one `BarsWebFlowState`, while
`BarsViewModel` remains responsible for authenticated data and cached grades.
Settings rows/cards are defined in `SettingsComponents.kt`. Advanced
performance/OWA tests are hidden under an optional diagnostics subsection;
the ZIP exporter and log copy commands remain immediately accessible.

## BARS contextual information pattern

The BARS overview, cross-course **Фокус на автомат**, per-discipline **План на автомат**,
and Summary reports share `BarsInfoButton`: a 48dp accessible info icon opening a
scrollable Material 3 dialog. Back, outside-tap or **Понятно** dismisses the dialog
without changing any BARS state.

The normal view displays only the values and controls needed to make a decision:
current target/status, progress, remaining KM mark selectors, week/weight and
recommendation presets. The dialog contains the explanation of calculation
bounds, weights, incomplete BARS data, the recommendation heuristic and the
important distinction between a hypothetical 4.2 forecast and an official
automatic exam grade. It is never an automatic grade guarantee.

Secondary student-profile facts and original Summary notes, including the
last successful verification timestamp, remain accessible from their respective
info buttons rather than consuming long scrolling sections. Genuine errors,
loading state and no-data guidance **remain inline**, because they affect the
next user action; actual grade activities and planning inputs are not hidden.

### Device test

- BARS overview: open the profile info and focus info, close via Back and tap
  outside; confirm profile facts and priority explanations remain accessible.
- Open a discipline with weighted future KM: tap info, check bounds, missing
  weights and guarantee explanation, then change grades 3/4/5. Ensure only the
  preview changes and selection remains after closing the dialog.
- Open **По неделям**, **Посещаемость** and **Контрольные**: open info to read
  source notes, verification time and reading instructions. Data cards should
  remain scannable with no duplicated explanatory footer.
- Check narrow portrait layout, system font scaling, dark/light Monet colors
  and screen-reader descriptions. The dialogs scroll for long notes.
