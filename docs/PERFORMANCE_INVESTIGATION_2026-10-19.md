# Performance investigation — 2026-10-19

## Baseline and interpretation

User-supplied `MPEI-Neo-diagnostics-2026-10-19_16-59-43-UTC.zip`,
build `0.4.0-dev.301`. Device reported **60 Hz** (16.7 ms per frame).
Two mixed-navigation `Window.FrameMetrics` sessions:

| View | Run 1 frames | Run 1 p50 / p95 (ms) | Run 1 >16.7 ms | Run 2 frames | Run 2 p50 / p95 (ms) | Run 2 >16.7 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Schedule | 534 | 26.8 / 42.7 | 509 (95%) | 119 | 21.7 / 23.6 | 83 (70%) |
| Mail | 447 | 21.5 / 41.0 | 427 (96%) | 523 | 20.9 / 23.9 | 420 (80%) |
| BARS | 477 | 23.6 / 39.3 | 474 (99%) | 737 | 11.9 / 23.4 | 280 (38%) |
| Maps | 460 | 16.4 / 24.7 | 228 (50%) | 104 | 21.9 / 26.6 | 94 (90%) |
| Settings | 114 | 21.0 / 24.1 | 114 (100%) | 213 | 20.3 / 23.3 | 116 (54%) |
| Home | 387 | 19.8 / 36.5 | 304 (79%) | 9 | 18.5 / 25.2 | 8 (89%) |

**Caution:** `TOTAL_DURATION` is not a measured user-visible dropped-frame
count and can include GPU/scheduling costs. These runs mix page transitions,
idle, scrolling, network events and cold initialization; their percentages are
not representative steady-state FPS. `callbackDrops=0` means metric callbacks
were delivered, not that Android rendered every frame on time. Home run 2
(n=9) is too small to compare.

## Concrete traces supporting changes

- BARS `load watchdog fired` at 16:37:44 with `started=true`,
  progress going from 100 to 0 and `elapsedMs=9298`, despite a watchdog
  scheduled **12 s after the original load**. `onStarted` updated
  `lastLoadStartedAt` but did not advance the timer.
- One BARS timeout before document start, stuck at progress 10:
  `started=false`, elapsed ~12 s. Therefore **both** a bounded connect idle
  deadline and an independent in-document idle deadline are necessary.
- 52 Send-folder recipients resolved in six batches in run 1. Three batches
  finished after leaving Mail, one while entering BARS. This was optional
  enrichment running in a ViewModel scope independent of screen visibility.
- Map preparation took 7–37 ms and rasterization 41–75 ms on five visits,
  re-allocating ~24.8 MiB (`2498 x 2599 x 4`) per visit. Map tap hit
  resolution was 0–1 ms; polygon search is not the observed bottleneck.
- Schedule has animated corner radius, padding, text content size and visibility
  within day cards. This may cause repeated measuring but remains **a
  hypothesis** pending frame phase tracing. The root app collects multiple
  state flows; broad recomposition has not been demonstrated.

## Implementation and tradeoffs

1. **BARS controller:** connection idle=12 s; document idle=18 s since last
   trusted start/forward progress; hard deadline=45 s from initial request.
   A matching `onPageFinished` still ends navigation tracking and Summary
   retains its separate asynchronous table timeout. Progress 100->0 is not
   counted as progress. Tradeoff: a legitimately slow page with no callback
   for 18 s or >45 s total will time out; this is intentional and testable.
2. **Mail Sent enrichment:** cancel optional recipient-resolution batches when
   Sent stops being the foreground mail view; restart unresolved cached batches
   when shown again. This does **not** suspend inbox notifications, sending or
   user-requested message loading. An in-flight blocking HTTP request may not
   terminate immediately upon coroutine cancellation.
3. **Map:** keep parsed geometry once per process and at most one
   palette-keyed soft-reference raster. Android low-memory and app-background
   trim signals evict the raster; a composable still displaying it owns a
   strong reference until it leaves. Tradeoff: retaining a 25 MiB bitmap while
   the process has sufficient memory; theme changes regenerate.
4. **Schedule:** reuse week label formatting and calculated hour-long breaks
   instead of doing that work on every scroll/pull recomposition. No animation
   removals or disruptive LazyColumn restructuring before evidence.
5. **Profiling:** maintain finite opt-in sampling. Per-frame input handling,
   layout/measure, draw and GPU p95 values are now logged, plus short interaction
   phase windows: `LESSON_TOGGLE`, `WEEK_CHANGE`, `MAIL_OPEN`,
   `NAV_PUSH`, `NAV_POP`. Zero stage p95 means that stage was unsupported
   or no positive duration was recorded; it does **not** prove zero work.

## Next on-device investigation

Run an **R8 Dev** build on the **same device, display refresh, theme, power
mode and network** as the baseline. Prefer three repeated 60-second sessions
rather than one mixed random session:

1. After app settles, profile Schedule: scroll rapidly from Monday to Sunday,
   expand/collapse the same lesson 5 times, switch week twice, return.
2. Profile Mail separately: scroll 30 messages, open/close one message 5 times,
   open Sent, navigate away and back. Check `OWA sent enrichment paused`,
   `resumed`, and per-batch `durationMs` (no recipients/subjects in logs).
3. Profile Maps separately: open map from Home twice, open from a Schedule
   classroom, pan, pinch, select two letters. Verify `campus_prepare
   cache=hit` and `campus_raster cache=hit` after first visit; change theme
   and verify a miss, then test low-memory app return.
4. Profile BARS after a cold startup and after a valid login/refresh. Verify
   normal Summary capture, automatic restore and zero premature watchdog
   firings. With an intentionally disconnected network, ensure failures
   are bounded and cached grades remain visible.

Compare `frame_profile screen=SCHEDULE/LESSON_TOGGLE` to base
`SCHEDULE`, and likewise for other phases. Use the new
`inputP95Ms/layoutP95Ms/drawP95Ms/gpuP95Ms` fields to decide whether the
next optimization targets repeated measuring, draw throughput, or the
graphics pipeline. Android Studio/Perfetto **System Trace** and Compose
composition tracing should be the next escalation if frame breakdowns show
sustained layout work; a single screen-level p95 cannot attribute a
specific composable.

## Safety/regression checks

- BARS login, 2FA, Summary AJAX, redirects and manual retry.
- Mail Outbox/Inbox state synchronization, Sent recipient persistence, worker
  notifications, message attachments, drafts and cancellation on logout.
- Map pan/pinch/edge back gesture, building В southern wing, adaptive letter
  keyboard, Monet light/dark and memory trim.
- Schedule expanded text wrapping, week gesture thresholds and return state.

Do not claim an FPS uplift until before/after runs on-device establish it.
