# MPEI Lite · Material 3 Expressive component adoption

Verified against Google's component overviews and the app's pinned
`androidx.compose.material3:material3:1.5.0-alpha29` (October 2026).
See also [the spring motion plan](MOTION_EXPRESSIVE.md) and
[the app architecture](ARCHITECTURE.md).

## Decision table

| M3 component / spec | Where | Code and decision |
| --- | --- | --- |
| [Navigation bar](https://m3.material.io/components/navigation-bar/overview) | Four primary services | `ShortNavigationBar` with `ShortNavigationBarItem`, 4 permanently labeled items, unread Mail badge, EqualWeight on compact phones / Centered on medium width, top vs leading icons. Material handles selected-indicator motion. Pager from PR #96 handles page content separately. |
| [Button groups](https://m3.material.io/components/button-groups/overview) | BARS «Автомат» 3/4/5 and Mail Inbox/Sent | `ToggleButton` with `ButtonGroupDefaults.connected*ButtonShapes`, selected-state colors and radio semantics. Never allow deselection into an invalid state. Settings theme and notification cadence were already implemented with connected groups and remain unchanged. |
| [Search](https://m3.material.io/components/search/overview) | Per-service search | Existing compact toolbar launch button remains, as established in the UX reset. The full-screen controlled text field now uses `SearchBarDefaults.inputFieldShape` and `inputFieldColors`; results are native `ListItem` rows with Material `FilterChip` controls. Avoid instantiating a second `SearchBarState`, which would compete with `SearchOverlayState` and the Schedule remote search/debounce. No search morph or extra modal window is reintroduced. |
| [Lists](https://m3.material.io/components/lists/overview) | Mail inbox rows, Settings actions and toggles | `ListItem` supplies vertical rhythm, leading icon/avatar, headline, supporting and trailing content. Mail unread emphasis remains in typography and container color. Settings switches have **one** parent `Modifier.toggleable(role = Switch)`; child `Switch(onCheckedChange = null)` avoids duplicate TalkBack/tap handling. |
| [Top app bars](https://m3.material.io/components/app-bars/overview) | Four primary pages and Settings | Existing `TopAppBar` is already compliant and retained; compact titles/action icons conserve schedule and Mail vertical space. Embedded BARS/OWA browsers own a dedicated toolbar. Do not add a second title or a large collapsing header. |
| [Bottom sheets](https://m3.material.io/components/bottom-sheets/overview) | Extended BARS help | `BarsInfoButton` opens an accessible, scrollable `ModalBottomSheet` with drag handle, Back/scrim dismiss, and direct dismiss action. Favorite renaming and true decisions retain `AlertDialog`, which is a better semantic fit. |
| [Loading indicators](https://m3.material.io/components/loading-indicator/overview) | Schedule search lookup, BARS and Mail waiting | `LoadingIndicator` for search, existing `ContainedLoadingIndicator` for card/portal wait states and built-in Material pull-to-refresh loading. No new progress spinners for instantaneous changes. |
| [Progress indicators](https://m3.material.io/components/progress-indicators/overview) | BARS hypothetical 4.2 projection | `LinearWavyProgressIndicator` on the grade **forecast**, visually distinguishing it from actual marks. Precise APK download percent remains `LinearProgressIndicator` (the user needs an exact value); lesson elapsed time remains its existing background fill because that is timeline content, not a wait spinner. |
| [Cards](https://m3.material.io/components/cards/overview) | Schedule/BARS discipline surfaces | Existing native `Card`/`Surface` with Monet roles and appropriate shapes retained. No nested card wrappers. |
| [Chips](https://m3.material.io/components/chips/overview) | Schedule target search filters | Existing Material `FilterChip` correct for an independently scrolling, multi-option filter; unlike adjacent grade/folder options, should not become connected buttons. |
| [Icon buttons](https://m3.material.io/components/icon-buttons/overview) | Search, refresh, favorites, Settings, BARS help | Already native `IconButton`, with accessibility descriptions and minimum touch targets; no custom graphics introduced. |
| [Snackbars](https://m3.material.io/components/snackbar/overview) | Mail send/attachment-save success | `SnackbarHost` displays **confirmed** transient actions (`Письмо отправлено`, `Сохранено: …`) without permanently occupying an inbox row. Cached-mail and error recovery notices remain persistent `ServiceStatus` banners so important information cannot disappear after a timeout. |
| [Toolbars](https://m3.material.io/components/toolbars/overview) | OWA/BARS browser controls | Existing dedicated embedded browser toolbar covers Back/Forward/Refresh/open-in-browser; not replaced with a second floating tool strip. |
| [Dialogs](https://m3.material.io/components/dialogs/overview) | Favorite rename, confirmations | Existing `AlertDialog` retained where users edit a small field and must explicitly save/cancel. |

## Deliberately not introduced

- [FAB menu](https://m3.material.io/components/fab-menu/overview): Mail has
  one primary compose action; a menu creates an extra decision for no gain.
- [Tabs](https://m3.material.io/components/tabs/overview): adding a parallel
  four-service tab row would duplicate the navigation bar and pager.
- A global search morph and scrollable large top bar: both were explicitly
  removed during the UI reset to keep navigation and content stable.
- More backdrop layers for BARS: its authenticated WebView must remain
  composed with its real lifecycle through root page transitions.

## Verification

- No BARS/OWA network code or app identifiers changed.
- Native Compose UI-test coverage continues to verify the four tab tags and
  scannable BARS planner. A focused BARS modal-sheet smoke assertion and
  JVM Mail snackbar-notice policy tests are added.
- PR CI: JVM tests, instrumentation test compilation, Android lint, and R8
  release compilation. These do not replace actual-device QA.
- Device checks: short navigation bar at 360dp and 600dp, TalkBack Settings
  toggles, Inbox/Sent switching with cached messages, BARS 3/4/5 tap states,
  search IME/results, sheet swipe-dismiss and Back, long localized titles,
  Mail send/save snackbar, dark/light Monet, and the retained BARS WebView
  during swipe as implemented in PR #96.
