# Material 3 compliance policy (incremental)

This project aims for **zero unreviewed deviations** from a versioned set of Material 3 and product-specific rules. A mathematically complete guarantee against all current or future guidance is not possible: much of the official guidance is contextual, and the upstream site changes. We do not claim an exhaustive audit or a Google certification.

## Source hierarchy

1. Official Material Design 3 guidance: https://m3.material.io/get-started
2. Google's Jetpack Compose Material 3 implementation guidance: https://developer.android.com/develop/ui/compose/designsystems/material3
3. Android accessibility, responsive layout, and Compose testing documentation
4. Explicit MPEI Lite product decisions, **never mislabeled as Google requirements**

The machine-readable [rules.json](rules.json) is the current *review registry*, not a copy of the entire site. Each rule has an ID, area, type (mandatory, recommendation or product), source, enforcement method, and review status. `implementation-review-required` means the rule is not yet proven covered. Add a rule or improve coverage as pages are audited; do not mark a rule complete without evidence.

## Enforcement currently installed

- `python3 scripts/check_material3.py`: validates the registry, blocks Material 2 UI imports in production, and restricts imports of sensitive Material 3 navigation, tabs and FAB components to their existing owner files. **Import checks are boundary guards only, not full behavioral verification.**
- `python3 -m unittest discover -s scripts -p 'test_*.py' -q`: tests the policy checker.
- The PR workflow runs both; other lint/unit checks already run.

The owner list is an *incremental migration mechanism*: existing screens retain responsibility for components until they are extracted to a shared `ui/designsystem/` facade in a future reviewed PR. Avoid cosmetic refactors that only move imports without making behavior testable.

## Review process

1. Every UI change identifies affected `rules.json` IDs in its PR, and links the exact source pages reviewed.
2. Prefer default Material 3 components and `MaterialTheme` roles; describe any override, what guideline permits it and how it is tested.
3. Review light/dark, compact/expanded, large font, IME/insets, TalkBack and predictive-back where applicable.
4. Add an instrumented interaction or accessibility test when behavior can be tested; visual reference changes require explicit review.
5. Don't approve an implementation only because an agent asserts compliance. The code and tests are the evidence.
6. Periodically revisit the official pages and reconcile changed recommendations; updates to the registry are reviewed PRs, not automatic UI rewrites.

## Not yet enforced

Fully approved component wrappers, per-screen screenshot baselines, automated screenshot diffs, broad automatic accessibility sweeps, emulator coverage of all flows, and comprehensive upstream-page monitoring. These require separate work; do not represent the present gate as complete coverage.
