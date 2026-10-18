# MPEI Neo · UI component map

| What you see | Compose implementation | Behavior |
| --- | --- | --- |
| Super-app home | SuperAppHome.kt | Root launcher with four large service cards (Schedule, BARS, Mail, Campus), cached metadata, and a Settings action. No permanent navbar. |
| Service push/pop | MpeiNeoApp.kt | Opening a service slides it in from the right; Android Back and full-width right-swipes pop toward Home (or the contextual Schedule origin). Native service page follows the finger, with a 20% threshold and a settling animation; the Android OS owns system-back edge zones. |
| Schedule service | MpeiNeoApp.kt / ScheduleAgenda.kt | Schedule has its own scrolling and week gestures, independent of service navigation. |
| Bottom floating search bar | ScheduleDockedSearch.kt | Tonal search dock at screen bottom without a navbar. Favorites and suggestions expand upward; selected schedule appears in collapsed placeholder. |
| Expanded search | ScheduleDockedSearch.kt | Favorites appear in a floating panel ABOVE the bottom search input on focus. As text is entered, matching favorites stay above API results; category filters apply to both. The popup overlays the agenda without resizing it. It responds to keyboard insets and is dismissed when navigating away. |
| Favorite row overflow | ScheduleDockedSearch.kt | Rename or remove favorite. Blank name restores the original display name. |
| Custom-name persistence | UserPreferences.kt | Names stored separately by schedule-target type and id; existing favorites are preserved. |
| Date-grouped timeline | ScheduleAgenda.kt | Native sticky day headers in a bounded, scrolling LazyColumn. Pull to refresh; weeks loaded on demand. |
| Class card | LessonCard / ScheduleScreen.kt | Collapsed by default: number pill, single-line ellipsized subject, classroom at right. Tap anywhere to expand full time, kind, multiline name and icon-led room, lecturer and groups, then tap again to collapse. |
| BARS grade combinations | GradeForecastDropdown / BarsGradeCalculator.kt | Every discipline's details sheet has an expandable calculator for weighted grade combinations satisfying Σ(grade × weight) ≥ 4.2. Known grades stay fixed; ungraded controls are varied 2–5. Does not invent missing weights. |
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

Navigation uses a service launcher as its root instead of a bottom navigation bar. Search suggestions remain above the schedule dock and adapt to the IME.

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

The calculator consumes only BARS control activities with readable grade
weights. Decimal comma, fractional weights and percentages are supported;
missing or inconsistent weights are not silently guessed. It keeps existing
grades fixed, explores future values 2–5 and lists up to 30 combinations
which meet a weighted sum of at least 4.2. The sum, known weight total, and
limitations are visible. Forecasts are local and never modify BARS grades.

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
to pan, pinch to zoom around the finger centroid, and double-tap to zoom toward
a point. Buildings remain tappable; zoom/recenter controls are still available.
The map reserves a 96dp-wide left-side strip (excluding the OS's first 24dp)
between the top and bottom controls for interactive right-swipe navigation.
Everywhere else, horizontal drags move the map rather than navigate. The
Android system Back gesture and hardware Back continue to navigate normally.
OSM attribution remains visible.
See `docs/OFFLINE_CAMPUS_MAP.md`.
