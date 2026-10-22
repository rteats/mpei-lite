#!/usr/bin/env python3
"""Small dependency/ownership guard for MPEI Neo.

Deliberately checks only important established boundaries; does not enforce
arbitrary Kotlin file sizes, package layers, or speculative module structure.
Run from anywhere: python3 scripts/check_architecture.py
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/rteats/mpeineo/ui"
issues: list[str] = []


def content(relative: str) -> str:
    file = UI / relative
    if not file.is_file():
        issues.append(f"Missing architecture owner: {file.relative_to(ROOT)}")
        return ""
    return file.read_text(encoding="utf-8")


session = content("BarsSessionCoordinator.kt")
presentation = content("BarsWebFlowState.kt")
app = content("MpeiNeoApp.kt")
navigation = content("ServiceNavigationState.kt")
search = content("SearchOverlayState.kt")
bars_screen = content("BarsScreen.kt")
bars_transport = content("BarsWebViewTransport.kt")

# The session decision engine must remain JVM-testable without Compose/Android.
for forbidden in (
    r"^import\s+android\.",
    r"^import\s+androidx\.compose\.",
    r"^import\s+androidx\.lifecycle\.",
    r"\bWebView\s*\(",
):
    if re.search(forbidden, session, re.MULTILINE):
        issues.append(f"BarsSessionCoordinator cannot depend on {forbidden}")

# Session state must have exactly one owner. UI state can hold viewport,
# diagnostics or focus, but never active operations or extraction generations.
for token in (
    "summaryFetchAttempted",
    "summaryExtractionStarted",
    "summaryRouteDiscoveryGeneration",
    "marksExtractionStarted",
    "redirectingToMarks",
):
    if re.search(r"\b(var|val)\s+" + token + r"\b", presentation):
        issues.append(f"BarsWebFlowState must not own '{token}'")

for stale in (
    r"\bvar\s+routeNames\b",
    r"\bvar\s+globalSearchOpen\b",
    r"\bvar\s+globalQuery\b",
    r"\bvar\s+searchOverlayMounted\b",
    r"\bvar\s+searchEntryBounds\b",
    r"\bvar\s+searchScope\b",
):
    if re.search(stale, app):
        issues.append(f"MpeiNeoApp should delegate state ownership: {stale}")

for marker, filename, source in (
    ("fun open(", "ServiceNavigationState.kt", navigation),
    ("fun pop(", "ServiceNavigationState.kt", navigation),
    ("fun activate(", "SearchOverlayState.kt", search),
    ("fun close(", "SearchOverlayState.kt", search),
    ("fun enterInteractiveBrowser(", "BarsSessionCoordinator.kt", session),
):
    if marker not in source:
        issues.append(f"{filename} is missing expected API: {marker}")

# The UI may compose AndroidView, but browser policy and the JS bridge must
# remain inside the transport instead of regrowing in BarsScreen.kt.
for forbidden in (
    r"^import\\s+android\\.webkit\\.",
    r"\\bevaluateJavascript\\s*\\(",
    r"\\bBarsNavigationController\\s*\\(",
    r"\\bBarsJavascriptBridge\\s*\\(",
    r"\\bCookieManager\\b",
    r"\\bWebViewClient\\b",
):
    if re.search(forbidden, bars_screen, re.MULTILINE):
        issues.append(f"BarsScreen must delegate Android transport: {forbidden}")

for marker in (
    "class BarsWebViewTransport",
    "fun create(context:",
    "fun release(view:",
    "fun onBrowserModeChanged(",
    "fun refreshMarks(",
    "fun refreshSummary(",
    "fun handleBrowserBack(",
):
    if marker not in bars_transport:
        issues.append(f"BarsWebViewTransport missing required transport API: {marker}")

if issues:
    print("Architecture boundary violations:")
    for issue in issues:
        print(f"  - {issue}")
    sys.exit(1)

print("Architecture boundaries verified (BARS transport/session, search, navigation).")
