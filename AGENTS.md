# Repository agent instructions

This is a Kotlin/Jetpack Compose Android app using Material 3 Expressive.

- Read `docs/material3/README.md` and `docs/material3/rules.json` before making UI changes. Official Material 3 docs take precedence over visual guesses.
- Prefer Material 3 components and the existing app theme. Do not add Material 2 UI imports or bypass the sensitive-component ownership restrictions in `scripts/check_material3.py`.
- Treat MPEI Lite requirements separately from official Google recommendations; cite affected rule IDs and exact source pages in every UI PR.
- Before modifying navigation, search, tabs, FABs or animations, inspect the entire interaction path, including back, system insets, keyboard, screen size and accessibility.
- Add regression coverage for behavior changes; do not silently bless screenshots or weaken checks to make a failing UI change pass.
- Run `python3 scripts/check_material3.py`, `python3 scripts/check_architecture.py` and `python3 -m unittest discover -s scripts -p 'test_*.py' -q`, plus Gradle lint/tests and emulator tests where available.
- Do not claim 100% M3 compliance. Record unsatisfied or untestable design rules in the audit and state what remains unverified.
