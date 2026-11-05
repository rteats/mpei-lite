# Initial MPEI Lite Material 3 audit — 2026-11-05

**Status: not a completed compliance audit.** This is a baseline inventory and a set of recorded, previously reported discrepancies. It is deliberately not a Google certification. The `screen-audit.json` inventory is machine-checked to catch newly added `*Screen.kt` entry points that lack a review record. Other composables, dialogs, navigation transitions and WebView content must also be included in the manual audit.

## Verified findings awaiting existing PRs

| Area | Existing issue | Existing open PR | How to verify before marking reviewed |
| --- | --- | --- | --- |
| BARS class detail | Enter/back transition may lose the detail screen | [#123](https://github.com/rteats/mpei-lite/pull/123) | Repeat predictive back and refresh during the animation; content remains mounted |
| Primary shell | Settings destination and detail view navigation behavior | [#124](https://github.com/rteats/mpei-lite/pull/124) | Settings is secondary; mail detail/compose preserve nav; search remains usable on detail |
| Mail folder tabs/FAB | Icon alignment and medium FAB type | [#125](https://github.com/rteats/mpei-lite/pull/125) | Inspect tabs and FAB against approved component specs, verify touch bounds |
| Schedule search | Placeholder truncation and tone | [#126](https://github.com/rteats/mpei-lite/pull/126) | Test compact widths and increased font scale |
| Shared layout spacing | Consistent app-owned spacing (not component-internal spacing) | [#127](https://github.com/rteats/mpei-lite/pull/127) | Audit affected screens and ensure exceptions are documented |

[#128](https://github.com/rteats/mpei-lite/pull/128) addresses map data bundling, not direct Material visual compliance; no design-validation credit is assigned to it.

## Required screen review procedure

For each tracked entrypoint in `screen-audit.json`, review its child components and all states (loading, empty, offline, error, content, edit, navigation). For each applicable Material 3 component, record: the specific official component guidance, whether a recommendation or a mandatory rule, the chosen Compose API, deliberate deviations with rationale, accessibility checks, and automated/manual evidence. Only change the status to `reviewed` after storing evidence links. Do not treat an open PR as a completed fix.

Suggested source entrypoints:
- https://m3.material.io/get-started
- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/develop/ui/compose/accessibility/testing
- https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites

This baseline covers **nine `*Screen.kt` files plus the application shell**, not the full component tree. Remaining tasks include custom component wrappers, current guideline interpretation for each state and viewport, approved visual baselines, and broader emulator tests.
