# MPEI Neo performance profiling

## When to measure

Profiling is worthwhile after a **reproducible** delay, visible dropped frames,
or a regression between releases. Do not optimize code based solely on method
size, Compose recomposition counts, or the belief that a screen is complex.

Existing instrumentation already measures some BARS navigation/network
durations and campus vector/raster preparation. These are **not** frame times.

## Lightweight on-device measurement (opt-in)

1. Install a PR or Dev build on the device whose performance matters. Disable
   the Android system's power-saving mode for comparable samples.
2. Open **Настройки → Диагностика → Замерить плавность интерфейса**.
3. For up to 60 seconds, navigate between **Пары, БАРС, Почта, Корпуса**,
   swipe back, scroll long lists, or zoom/pan the campus map.
4. The recording stops automatically after 60 seconds, on app background, or
   with **Завершить замер плавности**.
5. Copy the existing diagnostic journal. Look for `[PERF] frame_profile`.

The report groups Android **Window** frame durations by the currently selected
service and labels short windows for `NAV_PUSH`, `NAV_POP`, `LESSON_TOGGLE`,
`WEEK_CHANGE` and `MAIL_OPEN`. For each group it logs `frames`, `p50Ms`,
`p95Ms`, `maxMs`, `overBudget`, `overBudgetPct`, and `callbackDrops`.
It additionally logs the p95 of input handling, layout/measure, drawing,
render synchronization, buffer swapping, unknown delay and supported GPU
duration metrics, plus per-stage sample counts. **Stage percentiles belong to
different frames and cannot be added together**; a 0.0 ms stage can mean
unsupported/missing data rather than evidence of no work. The frame budget is derived from the device display refresh
rate at capture start. `overBudget` means total recorded frame duration
exceeded that **approximate** budget; it is **not** an exact missed-vsync or
user-visible jank count. `callbackDrops` reports sampling callbacks skipped
by Android, **not** frames proven dropped by the UI.

Do not compare samples with different refresh rates, different screens visited,
different network conditions, or different foreground/background behavior.
For a meaningful before/after comparison, repeat the same route at least
three times per build and compare p95 and over-budget ratio. Keep interaction
windows separate from base screen buckets and do not interpret the number of
sampled frames per second as measured FPS. The first
navigation after install and warm navigations should be assessed separately.

No frame-by-frame logs or page contents are collected. The capture stays
disabled until explicitly started; the worker thread and listener are removed
when it ends. UI measurements should be collected on a physical device.

## Startup timing

Use the same physical device, display refresh rate, installed app data and
network conditions for before/after comparisons. The in-app frame profiler
does **not** capture startup. A shell measurement for the Dev package:

```bash
adb shell am start -S -W -n com.rteats.mpeineo.dev/com.rteats.mpeineo.MainActivity
```

`TotalTime` is an approximate Activity launch / first-display measure, **not**
time until all cached lessons are visible. Repeat the same command at least
10 times (discard the first run after installing) and compare medians and p90.
For a quick repeatable report use
`bash scripts/benchmark_startup.sh com.rteats.mpeineo.dev 10`.
Switch package to `com.rteats.mpeineo` for stable. Do not compare a Dev build
to a Debug build; Dev CI already enables R8. For true cold, warm and hot startup
classification and time-to-fully-drawn use Macrobenchmark / Android Studio.

Startup budget: first render requires the app shell and the Schedule UI.
BARS grade snapshots, encrypted mail caches, mail server sync, Chromium BARS
session, expired update APK cleanup and WorkManager reconciliation are not
prerequisites. The BARS WebView still remains mounted once created, and the
mail cache still restores when requested. Verify first BARS visit, 2FA
persistence across tab swipes, cached mail and offline schedule after each change.

## If a regression is measured

- **Cold launch**: use Android Studio Startup Profiler or the Macrobenchmark
  library on a benchmarkable non-debug build. The in-app 60-second capture
  starts only **after** opening Settings and therefore does **not** measure
  cold-start latency.
- **Long UI frames**: record a Perfetto/System Trace or Android Studio System
  Trace for the exact action, then inspect main-thread work, GPU rendering,
  Compose work and blocking disk/network operations.
- **BARS and Mail delays**: compare existing request/session timings with frame
  statistics. A slow server response does not necessarily imply UI jank.
- **Campus map**: compare initial preparation/rasterization timings in
  `[PERF]` with actual pinch/drag frame times to distinguish setup latency from
  gesture rendering delays.

Do not add baseline profiles, change Compose rendering architecture, or apply
micro-optimizations without a recorded bottleneck. When a fix is made, rerun
the same scenario and retain a before/after comparison in the PR.
