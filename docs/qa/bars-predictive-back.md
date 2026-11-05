# BARS detail predictive-back regression

The detail is a nested overlay above the persistent BARS dashboard, not a Navigation Compose destination. Keep the selected class identifier as the **source of route truth**, even when a background refresh briefly replaces the discipline list. Preserve the most recently rendered discipline content until exit.

For gesture back, `PredictiveBackDetail` drives the sheet to its final horizontal offset. When it commits, do not run `SpatialScreenOverlay`'s second slide. For toolbar/back-button navigation (no gesture progress), continue to use the normal overlay exit.

Manual QA on Android 14 (predictive-back developer option enabled) and Android 15+: open a class, interrupt/complete a swipe from either edge, retry, rapidly open/close the same class, and refresh grades while a class is open. Assert the detail never vanishes on its own and the overview stays composed beneath it. Run `BarsTaskFlowUiTest` on an emulator; local JVM tests cannot exercise system back gestures.

References: https://developer.android.com/develop/ui/compose/system/predictive-back-setup and https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture.
