# MPEI Lite UI layout rules

## Source of truth
Use `AppSpacing` for **app-owned** layout spacing: `xxs=4dp`, `xs=8dp`, `sm=12dp`, `md=16dp`, `lg=24dp`, `xl=32dp`. Pages use `screenHorizontal=16dp`, between sections `sectionGap=12dp`, and between sibling content `contentGap=8dp`.

Use Material 3's own defaults for native components (buttons, text fields, app bars, tabs, FABs), rather than overriding their internal padding. Responsive layouts may choose larger margins by width class; use explicit tokens, not arbitrary one-off values. Preserve 48dp minimum touch targets.

## Exceptions
Icon sizes, baselines, animation offsets and stroke widths are not layout spacing. A visual exception should have a comment explaining its rationale. If a new increment recurs across screens, add a semantic token instead of duplicating its numeric value.

## Migration process
This PR introduces tokens and applies them to Settings, shared ServiceStatus, and Mail composer. Migrate Schedule, BARS, Map and the mail list in follow-up PRs once competing navigation changes merge. Do not perform a global find-and-replace: distinguish component internals from external layout. Validate 320dp width, tablet width, font scale 2.0, light/dark and keyboard insets.

References: https://m3.material.io/foundations/layout/understanding-layout/spacing and https://developer.android.com/develop/ui/compose/designsystems/material3.
