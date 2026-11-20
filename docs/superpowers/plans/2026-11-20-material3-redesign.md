# MPEI Lite Material 3 redesign implementation plan

**Design** · [Approved proposal and prototype evidence](../../material3/redesign-research-2026-11-20.md)

**Platform** · Kotlin, Compose Foundation, Material 3 Expressive, Activity Compose, DataStore.

## 1. Destination state and motion

- [x] Add shell coverage for Mail portal tab restoration, active reselect, Map root Back, and preserved Mail navigation.
- [x] Key saved Compose state by destination in `MpeiNeoApp`. Preserve Mail portal, login, message, composer, folder scroll, BARS detail and planner, and Map camera. Keep notification routing distinct from nav-item reselect.
- [x] Use a seekable effects transition for peer navigation. Keep Back inside nested pages, then seek Schedule from secondary roots. Leave back-to-home at Schedule root to Android.
- [x] Extend predictive detail motion to composer while preserving draft handling, IME priority and sending guard. Verify ordinary, canceled and committed Back in the shared overlay tests and verify BARS detail refresh.

## 2. Continuous timetable

- [x] Test date-to-index mapping in both directions across an academic week and a year boundary, collisions and unknown start times, and deterministic Russian abbreviation and alias precedence.
- [x] Add the date-keyed grid with native axis scrolling, a Monday divider, touch targets, font-aware sizing, visible-week fetching and explicit week controls.
- [x] Use the existing week repository and alias preferences in `MainViewModel`. Keep teacher requests separate from the selected timetable and widget target. Test ambiguous teacher names.
- [x] Add a Material lesson sheet, local alias edit/reset and a separate teacher route. Cover the sheet, abbreviation draft restoration, boundary drags and the pinned heading in emulator tests.

## 3. Schedule chrome and end-to-end verification

- [x] Test Settings access from the Schedule overflow and Back to Schedule, and verify Mail search/navigation while the portal is open and after reselect.
- [x] Keep the Material search keyboard/Back interaction and existing Settings screen. Add accessible week controls and use the collapsed field overflow for Settings.
- [x] Run the Python and Gradle checks. Capture the emulator system trace and analyze the frame timeline.
- [ ] Verify authenticated Mail and BARS work, teacher lookup against real timetable data, TalkBack, light/dark and expanded-window behavior, actual gesture cancellation, and frame deadlines on representative hardware. See the pending checks in the [Material 3 audit](../../material3/redesign-research-2026-11-20.md).
