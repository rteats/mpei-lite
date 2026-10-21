# MPEI Neo · UI component map

| What you see | Compose implementation | Behavior |
| --- | --- | --- |
| Super-app home | SuperAppHome.kt | Root launcher with four customizable service tiles (Schedule, BARS, Mail, Campus), one prominent next-lesson card and concise sign-in/notification hints. Edit/Done and Settings controls live in the shared top app bar. No permanent navbar. |
| Service push/pop | MpeiNeoApp.kt | Opening a service slides it in from the right; Android Back and full-width right-swipes pop toward Home (or the contextual Schedule origin). Native service page follows the finger, with a 20% threshold and a settling animation; the Android OS owns system-back edge zones. |
| Schedule service | MpeiNeoApp.kt / ScheduleAgenda.kt | Schedule has its own scrolling and week gestures, independent of service navigation. |
| Shared search app bar | SearchAppBar.kt | Native Material 3 `AppBarWithSearch` in the root Scaffold top bar on every service; opens the current service's search with a contextual hint. The search has consistent 16dp leading/trailing screen margins. Home Edit/Done and Settings, Schedule refresh, and Mail compose occupy trailing app-bar slots. Context actions slide in/out through the right viewport edge, not from the search field corner. |
| Unified results | GlobalSearchScreen.kt | Top-aligned input searches within the active service. Home defaults to timetable targets; BARS disciplines and Inbox/Sent headers are searched locally. Timetable suggestions continue using the existing debounced lookup. |
| Schedule target picker | ScheduleDockedSearch.kt | A separate favorites button opens the existing specialized timetable picker, including favorites, rename and category filters. The normal search bar opens unified scoped search; week-refresh button remains. |
| Campus building selector | CampusMapScreen.kt | Map uses its alphabetic building keyboard rather than a misleading free-text search: choosing a building focuses/highlights the actual vector geometry. |
| Favorite row overflow | ScheduleDockedSearch.kt | Rename or remove favorite. Blank name restores the original display name. |
| Custom-name persistence | UserPreferences.kt | Names stored separately by schedule-target type and id; existing favorites are preserved. |
| Date-grouped timeline | ScheduleAgenda.kt | Native sticky day headers in a bounded, scrolling LazyColumn. Pull to refresh; weeks loaded on demand. |
| Class card | LessonCard / ScheduleScreen.kt | Collapsed by default: number pill, single-line ellipsized subject, classroom at right. Tap anywhere to expand; exact linked lessons (Home preview and search results) scroll into view and briefly show a Monet focus outline. |
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

Navigation uses a service launcher as its root instead of a bottom navigation bar. One next-lesson card appears above the service tiles; other services show concise tile status instead of duplicate preview cards. Home tile edit mode is held by the root and controlled from the same app bar as Search. A single Material 3 search app bar occupies the root Scaffold top slot, rather than floating over content. It changes its hint and search provider with the current service and opens a shared full-screen search view. Schedule favorites and target selection live in that search view.

## Linked destination focus

Navigation from Home's next-lesson card resolves the cached lesson's real week
offset, date, start time and classroom. Schedule first enters that week, scrolls
to its day and finally brings the exact lesson card into view. The same
short-lived Monet outline is used for links from search results, BARS subject
links (Home previews, cross-subject controls and search), and mail message
links (Home preview, inbox/sent, search and notification). Normal service
launches do not inherit a previous lesson target. The Campus map continues
using its existing building centering and footprint highlight. Do not add
fake links when a cached target is missing; open Schedule normally instead.

## Service navigation, back swipes and WebViews

The four primary services are launched from Home, while Settings is opened
from the Home action. Service entry uses a horizontal push animation;
returning to the previous level uses a matching pop animation.

On native pages, rightward dragging from anywhere outside Android's reserved
system-back edge zones triggers an interactive swipe back. A release past 20%
of screen width commits; a shorter gesture cancels. Back dispatches through
the existing Android stack, so Mail messages and BARS discipline details
close before leaving their service. The existing content animations handle
these internal transitions. Browser-specific Back behavior is preserved.

Schedule-to-Map classroom navigation returns to Schedule, not Home. The
BARS and OWA embedded browsers retain native WebView gestures; Android's
system Back remains available if the Compose gesture is not delivered through
a WebView. There is no global horizontal pager or Home FAB.

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
