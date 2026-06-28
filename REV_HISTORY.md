# Revision History and Handoff

This file is the working handoff for the main branch. The UX redesign worktree was intentionally not used for the June 23, 2026 field-test fixes.

For the full Claude Code continuation brief, including UX worktree notes, GNSS/raw-measurement findings, and a prioritized next-step backlog, see `HANDOFF.md` (authoritative; supersedes `CLAUDE_CODE_HANDOFF.md`).

## Rev BK (2026-06-28) — battery: throttle GPS during sustained stops

Owner-flagged concern; the cross-check showed GPS-on (~2 h) far exceeded active recording (~1 h), the gap
being GPS held at full rate through stops / the ~6 min pre-auto-stop idle window. Fix: while recording, once
the car has been stationary for `GPS_THROTTLE_AFTER_MS` (90 s — longer than a normal red light, so only real
parks / the idle tail throttle, no light churn), the fused request drops to a `GPS_THROTTLE_INTERVAL_MS`
(20 s) heartbeat. The **always-on accelerometer is the safety net**: the per-second ticker restores full
rate the instant `vibrationEma >= SENSOR_MOTION_VIB` (engine/movement) or a fix shows speed — so a pull-away
isn't missed. A throttled heartbeat is excluded from the GPS-gap counter (it's intentional, not lost signal).
Only the fused path is throttled (raw-GPS fallback untouched). `requestFused(interval, fastest)` refactor.

Conservative by design (no track loss while parked; fast accel-triggered restore). Battery impact needs
over-several-days measurement on-device. Left the Rev BA re-arm accelerometer at 50 Hz — lowering its rate
would shift the rate-specific jerk threshold and risk spurious arms, so it needs its own validation pass.

## Rev BJ (2026-06-28) — cross-trip recurring-event hotspots (Insights)

Roadmap item 9 (owner-requested): as routes overlap, surface places where the *same kind* of event recurs
across **distinct** trips — a pothole you hit every commute, a turn you always take sharply, a regular
hard-brake spot. Pure `EventHotspots` (grid-cluster ~55 m by (cell, kind); a hotspot needs >=2 distinct
trips so one eventful drive can't manufacture one; CORNER+SWERVE collapse to "Sharp turn"). +6 unit tests.
`TripViewModel.loadEventHotspots` locates each stored `drive_events` row via the nearest analysis point
(excludes walks/samples) and tags home/work hotspots "near Home"/"near Work". New Insights **"Recurring
spots"** card lists the strongest (most distinct trips first).

Field-validated on real data (prototype replay): clear signal already — a recurring pothole at home (5
events/3 trips), and a recurring swerve+corner at the workplace entrance (43.516,-79.670) = the same turn
into the work lot each commute. Exactly the "flag that turn/area" the owner asked for; grows with data.
(The Insights card itself wasn't visually verified — device was intermittently offline overnight; logic is
unit-tested + data-validated.) Pairs with O9 (rough-stretch geometry) and item 4 (repeated-route compare).

## Rev BI (2026-06-28) — work detection skips the home neighbourhood

Field replay of Rev BH exposed a flaw: the owner has several frequent spots *within ~1 km of home* (the
shuffle found in Rev BC), so "the most frequent non-home cluster" was a near-home spot (0.7 km away) that
got rejected by the post-check — leaving the real workplace (Speakman Dr, 34 km, fewer endpoints) never
surfaced. Fix: `detectWork` now **pre-filters** to endpoints beyond the home neighbourhood
(`WORK_MIN_FROM_HOME_M` 1 km -> 1.5 km) *before* picking the most frequent, so the distant recurring
workplace wins. Re-validated on real data: WORK now resolves to Speakman Dr (43.516,-79.671), 34.5 km from
home, 6 endpoints. (A workplace closer than 1.5 km won't auto-detect — covered by the user-settable
home/work roadmap item.)

## Rev BH (2026-06-28) — learn Work too (names read "Home -> Work")

Extends the Rev BC home learning to a second regular place. `HomeDetector.detectWork` = the most frequent
endpoint cluster that **isn't** home, provided it recurs enough (>=4) and is well clear of home (>1 km, so
it's not home parking spillover). `GeoNamer` labels endpoints at the learned work "Work" (free, no geocode),
so a commute reads "Home -> Work" / "Work -> Home". Work is detected + persisted alongside home in
`loadTripLabels` (SharedPreferences `cartrip_home`, `work_lat`/`work_lon`) and used by the single-trip
title. Pure + unit-tested (+3 `HomeDetectorTest`, +1 `GeoNamerTest`). The owner's workplace is the recurring
~34 km Speakman Dr cluster; on-device naming validation pending the next reconnect. Roadmap item 6 (work
detection) done; user-settable home/work still open.

## Rev BG (2026-06-27) — backfill speeding severity for older trips

Rev BF's `speedingSeverity` defaulted to 0 on existing trips (the migration can't recompute it), so their
Safety speeding penalty read 0 until limits were re-fetched. One-time backfill on app start
(`TripViewModel.backfillSpeedingSeverity`, pref-guarded): for each older trip with limits already fetched
(`limitCoverage > 0`), recompute `speedingSeverity` from the **stored** per-point speed limits in
`analysis_points` via the pure `SpeedLimits.speedingSummary` — **no network**. New trips already compute it
at finalize. Idempotent and cheap after the first run.

## Rev BF (2026-06-27) — magnitude-weighted speeding penalty (Safety) + limit-misread grace

The Safety speeding term was **magnitude-blind**: it penalized *% of time over* the limit equally whether
you were 4 or 40 km/h over (`speedingPct*0.8 + max(0,maxOver-20)*0.5`), so being 1 km/h over the whole trip
tanked the score. Replaced with a **magnitude-weighted, distance/time-normalized, super-linear** penalty
(owner-requested, owner-chose "balanced" strength).

- New stored metric `trips.speedingSeverity` (schema **v20**, `MIGRATION_19_20`): the mean over covered
  driving time of **`max(0, over - 5km/h)²`** — small overages forgiven (tolerance 5), big overages hurt
  disproportionately (quadratic), normalized by time (the speeding analogue of per-km event rate). Computed
  in the pure, unit-tested `SpeedLimits.speedingSummary` (+6 tests).
- **Limit-drop / misread grace:** the effective limit is the **max matched limit in the trailing 6 s**, so
  (a) exiting a highway, normal deceleration to the new lower limit isn't counted as speeding, and (b) a
  lone bad OSM limit match is smoothed over. Field-confirmed on trip 1180: a spurious single-point limit of
  40 (steady 84 km/h, 80 before/after) was inflating max-over to **+44**; the grace drops it to +28.
- Safety penalty now `min(45, 0.8 × speedingSeverity)`, replacing the old two terms. Field-validated on
  real drives: 1 km/h over → 0 penalty; recent trips re-score sensibly (1179→85, 1176→70, 1180→75, 1181→63;
  1181 lowest because it was sustained 13-22 over, vs 1180's brief peaks). `speedingPct`/`maxOver` kept for
  display.
- **Existing trips show no speeding penalty until their speed limits are re-fetched** (severity defaults 0
  after the migration; the recording flow computes it for new trips at finalize via `refreshForTrip`).

## Rev BE (2026-06-27) — speed-aware event threshold + truthful peak-G (from a big drive-day review)

Two detector refinements from reviewing a day of real drives (trips 1176-1181), both field-validated by
DB replay.

1. **Speed-aware longitudinal (brake/accel) display threshold** (owner-requested — "the g threshold to tag
   an event is a bit low"). `DisplayEvents` flagged brake/accel at a flat **0.28 g**; now it ramps like the
   existing turn logic: **0.42 g at ≤20 km/h easing to 0.35 g at ≥50 km/h** (linear between). At a crawl a
   0.3 g blip is usually a speed bump / parking nudge / stop-start jerk; at speed the same g is a real hard
   brake. DISPLAY layer only — detector/scoring thresholds unchanged. Replay over the 5 drives: flagged
   brake/accel markers **20 → 6** (the weak low-speed ones drop, the genuine ≥0.42 g ones stay). +3 tests.

2. **Truthful peak-G** (owner-observed under-reporting). `maxHorizGForce` was the p99.5 of *every* motion
   sample — diluted to ~0.13 g by the tens of thousands of calm cruising samples even when the trip had a
   real 0.5 g brake (field: stored peak 0.11-0.19 g while the hardest detected maneuver was 0.37-0.51 g).
   Now it's the **sustained** peak (`FusedEventDetector.sustainedPeak`: the highest horizontal-g level held
   for ≥`PEAK_SUSTAIN_SAMPLES`=7 consecutive samples, ~150 ms) — rejects a lone 1-3 sample phone/handling
   spike (the reason p99.5 was used) while keeping a genuine ~1 s maneuver. Removed the now-unused
   `percentile`. **Existing trips need Re-analyze** to refresh the stored value; new trips get it
   automatically. Calm-drive scores are unaffected (peaks still < the 0.55 g safety-penalty knee), but a
   future genuinely hard maneuver (>0.55 g) will now correctly register instead of being washed out.

Deferred to roadmap (HANDOFF §9): battery optimization (item 8, owner-flagged "ok for now"), and a new
**cross-trip recurring-event hotspots** feature (item 9, owner-requested) — flag turns/bumps/brake-spots
that recur across overlapping trips, growing with data.

## Rev BD (2026-06-27) — flag walks in the trip list (walk icon + moving-avg speed, no driving scores)

Walks were recorded as trips but the past-trips card showed driving Safety/Comfort/Pace scores, which are
meaningless for a walk (Safety from gait jostle, Pace from a bogus driving ETA). Now the trip card detects a
non-drive (`TripKind.isLikelyNonDrive` — top speed in 0.1..12 km/h, honouring the manual `userIsDrive`
override) and renders it differently:
- a **walking-person icon** next to the trip name, and
- in place of the three score columns, the **moving-average speed** (`Format.avgSpeedKmh` — 2 decimals,
  e.g. "4.75 km/h", from `avgMovingSpeedMps`, idle excluded). +1 `FormatTest`.
Drives are unchanged (Safety/Comfort/Pace as before). Scoped to the past-trips list card for now; the
trip-detail screen and any deeper non-drive handling (separate metrics vs. just hiding) are left for later.

Field-validated against 3 real walks this morning (trips 1171/1172/1173): all three correctly flagged
non-drives (top speed 5.6-7.9 km/h) with sane moving-avg pace (4.54-4.75 km/h). (Battery cross-check from
the owner's screenshots: GPS-on ~2 h vs ~1 h actually recorded — the gap is the pre-auto-stop idle window +
foreground auto-detect, not background runaway; the watcher logged only no-op charger events + a few
restarts, no phantom auto-records, BT scanning 0. A low-rate-GPS-while-stationary optimization is a possible
future battery win.)

## Rev BC (2026-06-27) — better trip names: learn Home + name "loops" by where they went

Two naming complaints fixed. (1) The geocoder fell back to "<area> loop" whenever a trip's start and end
resolved to the **same** name — and "North York" is a former municipality covering ~100 km², so most
round trips on that side of the city read "North York loop", saying nothing. (2) Home was never used by the
geocoded path, though most trips begin or end there.

- **Home is now learned from frequency** (`HomeDetector`, pure, +5 tests; roadmap O6): the most frequent
  endpoint cluster across all trips is home. Grid-densest cell + a 250 m radius **refinement** so a cluster
  split across a cell boundary is counted whole. An endpoint within 200 m of home is named **"Home"** (free,
  no geocode). Persisted in SharedPreferences from the trip-list refresh so the single-trip title uses it
  too. Field-validated: lands 18 m from the owner's actual home and labels 17/34 trips "Home -> ..." (the
  owner confirmed which of two nearby frequent spots is home — the hardcoded "Harrison Garden" was stale;
  detection picked the right one).
- **Same-name trips are named by their farthest point** (`GeoNamer.compose` gains `via` + `roundTrip`):
  the farthest track point from the start is the destination, so a loop reads "North York -> Scarborough
  -> back" (start/end physically coincide) or "North York -> Scarborough" (same coarse area, ended
  elsewhere). Only a trip that genuinely stayed in one area falls back to "X loop"/"X drive". Distinct
  endpoints ("A -> B") are unchanged. With Home learned, most trips read the short, clear "Home -> X".
- `TripViewModel.loadTripLabels` now does two passes (learn home from all endpoints, then name each trip),
  loading each track once; the via point is only geocoded for same-name trips, so the per-refresh geocode
  budget barely changes.

## Rev BB (2026-06-27) — START-side trip trim (drop the parked prefix on auto trips)

The stop side already trims retrospectively to the moment the car came to rest (`AutoStop`), but the
**start** side didn't — an auto-armed trip begins recording the instant the trigger fires (charger / BT /
the new Rev BA motion re-arm), which is usually while still parked (seatbelt, warm-up, backing out). Those
leading stationary seconds contaminated every auto trip's start: the geocoded start location was the
parking spot, idle time + fuel idle-burn counted the warm-up, and door-slam/settling jostle landed near t0.

Fix — the mirror of the stop trim, hardened by replaying it over the on-device trip DB (see below):
- `AutoStart.retrospectiveStartTime` (pure, +8 tests): find the first **sustained** pull-away — a motion
  run that exceeds `MOVING_MPS` (4 km/h) and stays out of a full stop for `DEPART_SUSTAIN_MS` (3 s) — then
  the true start is the last at/below-`STATIONARY_MPS` (0.7 m/s) sample just before that run, kept as a
  zero-speed ZUPT anchor. The sustain requirement was added after field replay showed GPS at trip start is
  noisy: a cold fix reports speed 0 while position jitters, and a car backing out *creeps* above walking
  pace for a second then pauses — the naive "first sample above threshold" latched onto that and
  under-trimmed (real case: trip 1169 picked a 2 s creep at t+4 s instead of the real departure at t+16 s).
  By construction it never trims a sample above `MOVING_MPS`, so real driving distance is structurally
  preserved. Returns null (no trim) when no sustained departure is found (no GPS / non-drive) or the car
  was already departing from the first sample (re-armed mid-motion).
- **Field-validated (offline replay over 34 real trips from the device DB):** 0 trips lose real distance
  (kept-distance ≥ recorded distance everywhere — the trimmed prefixes are parked idle, parking-lot
  maneuvering, and stale cold-start fixes), 0 real drives over-trimmed into the discard, 26/34 get a
  sensible parked-prefix trim, and the 1169 under-trim is fixed (3.6 s → 15.9 s).
- `RecordingService` captures a small leading `(t, speed)` window from trip start through the first motion
  (closed once moving, capped at `START_TRACK_MAX_SAMPLES`), and on stop — **auto trips only** — deletes
  the leading locations/motions/gnss rows before `startCut` via new `delete*Before` DAO queries, mirroring
  the existing `delete*After` end-trim. Time bases all share the monotonic `elapsedRealtime` ms clock, so a
  single cutoff trims locations and motions together (same as the stop side).
- **Over-trim safety:** the start trim runs *before* `finalizeTrip`, so the analyzer recomputes
  distance/duration over the trimmed range and the existing too-short discard (< `MIN_TRIP_DISTANCE_M` 5 m
  **or** < `MIN_TRIP_DURATION_S` 10 s) now also catches a trip trimmed down to nothing (e.g. a brief crawl
  that never became a real drive) — deleted, not saved, with the "Trip not recorded" notice.
- Manual trips are untouched (Start means "begin now"); only `autoStarted` trips are trimmed.

## Rev BA (2026-06-27) — re-arm auto-record on motion (close the long-idle silent-miss gap)

Failure mode closed: an in-car trigger (charger/BT) only arms a **provisional** trip that discards itself
if no motion is confirmed within `MOTION_CONFIRM_MS` (90 s). So plug in, then sit longer than that window
(warm-up, a phone call, adjusting) before pulling away, and the provisional is already gone with nothing to
restart it — the whole drive goes **silently unrecorded**. This was the last known reliability gap on the
primary (charger) path.

Fix: while **armed-but-not-recording**, `AutoRecordWatchService` now runs a pure `MotionRearmDetector` on
the accelerometer; sustained driving-level vibration re-arms a fresh provisional via the normal policy.
- `MotionRearmDetector` (pure, +11 tests): gravity-cancelling jerk EMA (same `0.94/0.06` as the recorder)
  must hold >= `0.40` continuously for >= 4 s to fire, with a 15 s cooldown. Threshold sits above the 0.30
  motion-confirm gate, so a re-arm always clears confirm; a false fire only ever costs one discarded
  provisional (the fresh provisional runs its own GPS/vibration confirm + the Rev AV short-trip discard),
  so no false trip can persist.
- `AutoRecordController.isArmedNotRecording()` — one policy-correct definition of the gap window (trigger
  present + not recording), reading the same sticky-charge/car-BT state as `reevaluate`.
- The watch is driven by a **self-correcting 3 s ticker** that registers/unregisters the accelerometer
  (`SENSOR_DELAY_GAME`, `TYPE_LINEAR_ACCELERATION` -> `ACCELEROMETER`, matching the recorder), so every
  edge settles uniformly with no new broadcast wiring: unplug / trip-start / service-restart turn it off
  within one tick; on fire it funnels through `reevaluate("rearm-motion")` (a just-unplugged race no-ops).
- Degraded modes: no accelerometer -> feature self-disables (logged once); FGS background-block doesn't
  apply (the watch is itself a running FGS, so the start succeeds); jostle/handling filtered by sustain +
  cooldown + the provisional's own confirm.

## Rev AZ (2026-06-26) — cluster potholes separately so bumps can't bridge maneuvers

Field finding: the event-cluster grows by adjacency (each event within `CLUSTER_TIME_MS=4s`, up to an
8 s span), so frequent **potholes were bridging two distinct maneuvers** into one marker and hiding one of
them (trip 1165: a swerve and a corner 8 s apart, chained via a pothole between them, showed as one swerve;
trip 1168: a corner + accel + bumps collapsed to one pothole). Fix: `DisplayEvents.clean` now **partitions
events into a driving stream and a pothole stream and clusters each independently** (`clusterStream`), so a
bump can't chain two maneuvers and a maneuver-coincident bump shows on its own. Driving events still cluster
among themselves, so a genuinely combined moment (e.g. accel through a curve, 2 s apart) stays one marker;
swerve/corner that are truly one maneuver still merge, while ones 5 s+ apart stay separate.

Regression-confirmed on the field trips (old vs new): trip 1165 recovers the hidden CORNER, trip 1168
recovers the hidden ACCEL, and trips 1126/1128/1130/1166/1169/1170 are unchanged (no driving markers lost);
a couple of previously-masked coincident potholes now show. +3 `DisplayEventsTest` cases (pothole doesn't
bridge swerve/corner; coincident pothole+brake both show; same-kind events still merge). **103 tests.**
- v2.89/100 → **v2.90/101**. No schema change. Installed on S25.

## Rev AY (2026-06-26) — tighten the bump-echo veto (field-test event audit)

Field-data audit of the auto-record drives (trips 1164-1170) cross-checked every tagged event against the
raw speed/gyro/gravity-decomposed accel. Findings: auto-on, motion-confirm, stop/trim, and the <5m/<10s
discard all worked; high-confidence events + potholes were all sound. One borderline displayed event (trip
1168: `ACCEL 0.36g conf 0.90` with FLAT GPS speed 32->32, coincident with a pothole) was a pothole's
horizontal shake mislabeled as an accel.

RCA: `DisplayEvents.isBumpEcho` only vetoed pothole-coincident fused brake/accel at **conf < 0.6**, so the
high-confidence one slipped through. Fix: also veto a high-confidence fused brake/accel that coincides with a
pothole **when the GPS speed slope contradicts the direction** (accel not actually speeding up / brake not
slowing) via a new `speedSlopeKmh` helper + `BUMP_ECHO_MIN_SLOPE_KMH=3.0`. Fails open when GPS context is
thin, and never fires away from potholes — so a genuine brake/accel over a bump (which moves the 1 Hz speed
by several km/h) survives. +5 `DisplayEventsTest` cases (flat-on-bump dropped, rising-over-bump kept, low-conf
preserved, no-bump kept, no-GPS fail-open). **100 tests**, no schema change.
- v2.88/99 → **v2.89/100**. Built + tests green; device was disconnected at install time (still on v2.88).

## Rev AX (2026-06-26) — fix "Your trip icon" not affecting the map marker

The Options -> "Your trip icon" picker (Car/Arrow/Person/Dot) saved correctly but the map replay marker
never changed: `TripMap` read the glyph via a **no-key `remember { }`** that cached the first value and
ignored later changes. Added `UiPrefs.rememberYouIcon()` (observes the pref via an
`OnSharedPreferenceChangeListener`) and used it in `TripMap`, so the replay marker now reflects the choice
live and on every trip open. Verified on-device: switching to Dot makes the moving marker a blue dot.
- v2.87/98 → **v2.88/99**. 95 tests, no schema change.

## Rev AW (2026-06-26) — re-imagined Speeding visual (Trip detail)

The Driving card's Speeding block was visually sloppy (a full-trip color sparkline crammed under the
label, plus a narrow vertical peak gauge on the side). Replaced with two clean, well-proportioned
**horizontal bars**:
- **Share bar** — "Over the limit — X% of moving time" with a red fill = fraction of covered moving time
  spent over the limit (uses the existing `SpeedingSummary.coveredMovingDurationS`).
- **Top-speed-vs-limit bar** — a horizontal bar that's blue up to the limit and red for the overage, with
  "Top speed N km/h" + "Limit M km/h" + "+over" labels.
Removed `SpeedTierSparkline` and `PeakSpeedGauge` (+ the now-unused `SPEED_YELLOW`). Verified on-device on
a highway trip (2% of moving time, limit 50 / peak 71 / +21 over). The event-summary rows already used
horizontal intensity bars, so the whole Driving card now reads consistently.
- v2.86/97 → **v2.87/98**. 95 tests, no schema change.

## Rev AV (2026-06-26) — discard trivially short trips + alert

- **A trip under 5 m total distance OR under 10 s total duration is no longer saved.** After
  `finalizeTrip`, `RecordingService.stopRecording` checks the final metrics; if the trip is below
  `MIN_TRIP_DISTANCE_M` (5.0) **or** `MIN_TRIP_DURATION_S` (10.0) it deletes it via `deleteTripWithData`,
  returns to idle (no summary opens), and **alerts the user it wasn't recorded** — a "Trip not recorded"
  notification (works even when backgrounded, e.g. an auto-stop) plus an in-app message. Catches accidental
  Start→Stop taps and brief moves that never became a real drive. Applies to manual and auto stops.
  Verified on-device: a 22 s / 0 m manual trip was discarded with no junk trip + the notice fired.
- v2.85/96 → **v2.86/97**. 95 tests, no schema change.

## Rev AU (2026-06-26) — Trip-detail polish (date eyebrow, short-trip fuel chip)

Aesthetic batch on the Trip-detail screen (verified on-device):
- **Date eyebrow in the hero.** The hero showed time-only ("3:24pm – 3:38pm") and the screen title is
  also time-only, so a past trip's *day* was invisible. Added a small muted `relativeDay` line
  ("Today" / "Yesterday" / "3 Jun 2026") tightly grouped above the time range.
- **Suppress the misleading economy chip on very short trips.** `FuelCostCard` showed an alarming red
  "212% more fuel vs rated" for a 664 m errand, where cold-start + idle make L/100km meaninglessly high.
  The vs-rated chip is now gated to trips ≥ 3 km (the litres/cost/L/100km stats still show); real drives
  are unaffected.
- v2.84/95 → **v2.85/96**. 95 tests, no schema change.

## Rev AT (2026-06-26) — Past Trips multi-select (batch delete)

- Long-pressing a trip now enters a **multi-select mode** instead of a single-trip popup. The top app bar
  becomes a **contextual action bar** (count + Rename + Delete) above the map: tap trips to toggle a
  checkbox, **Delete** removes all selected at once (confirm dialog), **Rename** shows only when exactly one
  is selected. System Back / the X cancels selection. `TripViewModel.deleteTrips(ids)` deletes each via the
  atomic `deleteTripWithData`. Single tap still previews the route; a second tap opens the trip.
- v2.83/94 → **v2.84/95**. 95 tests, no schema change.

## Rev AS (2026-06-26) — Codex review fixes (bg-location CTA, auto-arm logging)

Addressed a read-only Codex review of `6ebe32b..HEAD`:
- **P2 — "Allow all the time" CTA now works on Android 11+.** The runtime permission dialog cannot grant
  `ACCESS_BACKGROUND_LOCATION` on Android 11+ (it must be set in Settings), so the button felt broken.
  `requestBackgroundLocation()` now routes by API: API 29 → inline dialog; **API 30+ → opens app Settings**
  (Permissions > Location > "Allow all the time"). Copy updated; the redundant second button removed.
- **P2 — a blocked background auto-arm no longer logs a phantom "provisional trip started".** `startRecording()`
  now returns `Boolean`; `autoArm()` returns early when the foreground start was blocked, so it skips the
  success log and the motion-confirm job (which would have run against a recording that never began). Cleaner
  field diagnostics.
- **P3 — stop haptic** kept immediate (that's the point of the tactile cue) but the comment is now honest:
  it fires when the stop is *registered*; finalize/save follows and a rare failure is recovered as PARTIAL.
- **P3 — refreshed the stale `HANDOFF.md` header/TL;DR** (was still saying v2.81 / "Rev AQ may not be pushed").
- v2.82/93 → **v2.83/94**. 95 tests, no schema change.

## Rev AR (2026-06-26) — recording haptics + code-verified failure-mode matrix

- **Haptic cues** (`record/Haptics.kt`, `VIBRATE` permission) so the driver can *feel* the trip state
  without looking — directly to make field tests easier:
  - light double-tick = auto-record **armed** (provisional started, motion not yet confirmed)
  - one firm buzz = **recording** (manual start, or a provisional that just motion-confirmed)
  - two firm buzzes = **stopped/saved** (a real trip ended)
  - soft tick = **discarded** (a provisional that never moved)
  Wired into `startRecording` (armed vs recording by `autoStarted`), the motion-confirm-OK branch,
  `discardRecording`, and `stopRecording`. Amplitude-controlled where supported, pattern-only otherwise;
  no-op without a vibrator. Always-on (a user toggle is a noted follow-up).
- **Failure-mode / degraded-mode matrix** added to `HANDOFF.md` §3 — ~24 scenarios reviewed by reading the
  code paths (auto-record lifecycle, GPS loss, sensor stall, service-kill recovery, permission revocation,
  GNSS, etc.), each marked Code✓ / Device✓ / KNOWN GAP. Conclusion: only background hands-free start, reboot
  re-arm, and the haptic feel still need a real drive.
- v2.81/92 → **v2.82/93**. 95 tests, no schema change.

## Rev AQ (2026-06-26) — fix the background auto-record crash (background location)

Field test (real drives) surfaced a hard crash + crash-loop: plugging in with the app **closed** crashed
it, while with the app **open** it auto-started fine. The on-device crash buffer was unambiguous:

    java.lang.SecurityException: Starting FGS with type location ... targetSDK=34 requires
    [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION] and the app must be in the eligible state/exemptions
      at RecordingService.startInForeground(RecordingService.kt:950)  <- startForeground(..., TYPE_LOCATION)

- **Root cause:** on Android 14, starting a `location`-typed FGS from the **background** requires the app to
  currently have location access — i.e. **`ACCESS_BACKGROUND_LOCATION` ("Allow all the time")**. The app only
  ever requested while-in-use location, so the watcher-triggered background arm threw at `startForeground`,
  the exception escaped `onStartCommand`, and the process crashed; the persistent watcher then re-armed on
  restart and re-fired `AUTO_ARM` → **crash loop**. (The same logs confirm the Rev AP charging-edge fix works:
  `watch-start -> ARM [chg=true wl=true]`.)
- **Fix 1 (crash safety):** `startInForeground()` returns `Boolean` and try/catches the location FGS start.
  On failure `startRecording()` bails via `onForegroundStartBlocked()` — logs it, posts a "tap to enable
  hands-free" notice, `stopSelf()`. No crash, no loop. Manual/foreground start is unaffected.
- **Fix 2 (make it work):** declared `ACCESS_BACKGROUND_LOCATION`; added an Auto-record card that requests
  "Allow all the time" (with an app-settings fallback) when auto-record is on but background location isn't
  granted (clears on resume once granted).
- v2.80/91 → **v2.81/92**. 95 tests, no schema change. Installed on S25; background location granted.

## Rev AP (2026-06-26) — fix the charger trigger (P1), harden the watcher, GNSS cleanup

Post-Rev-AO review (`CLAUDE_REVIEW_HANDOFF_V2_79.md`) + live device validation. The Rev AO watcher's
runtime receiver was confirmed to fire reliably on **every real** charge event, but the decision itself
was broken — fixed here.

- **P1 (critical): the charging trigger read inverted/stale.** `AutoRecordController.reevaluate()` decided
  arm/stop from the sticky `ACTION_BATTERY_CHANGED` intent, which **lags** the `POWER_CONNECTED/DISCONNECTED`
  broadcast. The on-device log proved it on real plug events: `charger-on -> no-op [chg=false]` (missed the
  real trigger) and `charger-off -> ARM [chg=true]` (armed on the wrong edge). Fix: the watcher now passes
  the broadcast **edge** (`chargingEdge = true/false`) and the controller trusts it over the sticky read
  (`AutoRecordPolicy.effectiveCharging`, +3 tests). A lagging sticky is tagged `(sticky lagged)` in the log.
  For `requireWireless`, a delayed settle re-read resolves the plug *type* (which the sticky may not have
  caught up to at a connect edge). **Note:** `dumpsys battery unplug/reset` does NOT broadcast power events
  on this S25 — only a real cable plug/unplug exercises this path, so it must be confirmed on the next drive.
- **Bluetooth receiver split + exported.** The single combined `RECEIVER_NOT_EXPORTED` receiver became two:
  power stays not-exported; Bluetooth ACL is now `RECEIVER_EXPORTED` (privileged framework broadcasts can be
  dropped for not-exported receivers on some OEM builds — the suspected "BT never fired" cause). Still filters
  by the saved car MAC. Registration mode is logged.
- **Better arm() failure logging.** `FGS start BLOCKED` now logs the exception **message**, not just the class.
- **Raw-GNSS cleanup gaps (storage).** `gnss_measurements` rows were orphaned by trip delete and auto-stop
  trim. Added `deleteGnssMeasurementsAfter` + a central `@Transaction deleteTripWithData(id)` that removes
  every raw + derived table atomically; routed trip-delete and discard through it; added the measurements
  trim to the auto-stop path. Single source of truth so a future raw table can't be forgotten.
- **Cleanup:** deleted the dead `PowerConnectionReceiver`/`CarBluetoothReceiver` (unregistered since Rev AO);
  refreshed the Auto-record screen copy (it wrongly said background start needs the app open).
- v2.79/90 → **v2.80/91**. 92 → **95 tests**, 0 failures. No schema change (still v19). Installed on S25.

## Rev AL–AO (2026-06-26) — walk toggle, lane-data capture, working hands-free auto-record

- **Rev AL (v2.76): manual walk/non-drive toggle.** Schema v17→v18: nullable `trips.userIsDrive`
  (null = auto, true = drive, false = walk). `TripKind` respects it everywhere, so a toggled walk is
  excluded from You-vs-traffic, Pace, and fuel. Trip-detail ⋮ menu "Mark as walk / not a drive" ↔
  "Mark as a drive".
- **Rev AM (v2.77): fix crash on "Pair car".** `CompanionDeviceManager.associate()` threw a system
  `RemoteException` (`enforceUsesCompanionDeviceFeature`) because the manifest lacked
  `<uses-feature android:name="android.software.companion_device_setup">`. Added it + wrapped `associate()`
  in try/catch (surfaces as a log line + in-app error instead of crashing).
- **Rev AN (v2.78): lane-detection data enabler.** Schema v18→v19: per-fix accuracy estimates on
  `locations` (bearing/speed/vertical) + a raw `gnss_measurements` table (per-satellite carrier phase =
  `accumulatedDeltaRange` + Doppler = `pseudorangeRate`, C/N0, L1/L5 carrier frequency), captured via
  `GnssMeasurementsEvent.Callback` behind a Diagnostics "High-precision GNSS" toggle (off by default —
  voluminous). Validated: one drive captured 3,224 rows / 23 sats / 888 L5-band.
- **Rev AO (v2.79): persistent "armed" watcher — hands-free auto-start that works.** Field test proved
  CompanionDeviceManager can't pair/observe a classic-Bluetooth car (its chooser only lists *discoverable*
  devices; the Tucson never appears) and a manifest `ACTION_POWER_CONNECTED` receiver never fires
  backgrounded. New `AutoRecordWatchService`: a persistent low-importance FGS that, while auto-record is
  enabled, runtime-registers the charger/BT broadcasts (these DO fire while a service runs) and — being a
  running FGS — lets `AutoRecordController.arm()` start `RecordingService` from the background. Started from
  the settings toggle + `TripApp.onCreate` + `BootReceiver`; removed the dead manifest receivers; permissions
  `FOREGROUND_SERVICE_SPECIAL_USE` + `RECEIVE_BOOT_COMPLETED`. Validated end-to-end on-device via a simulated
  `dumpsys battery` charge cycle (`ARM → FGS start OK → AUTO_ARM → motion-confirm FAILED → discard`). Cost: a
  permanent silent "Auto-record on" notification.

## Rev AG–AK (2026-06-25) — auto-record root-cause fix, harsh-stop, you-vs-traffic redesign

- **Rev AG (v2.71): CompanionDeviceManager hands-free auto-start.** Field test confirmed manifest
  broadcast receivers (charger `ACTION_POWER_CONNECTED`, classic-BT `ACL_CONNECTED`) never fire while the
  app is backgrounded on the S25 (the decision log held a single foreground entry across multiple
  background test drives). Fix: associate the car once via `CompanionDeviceManager` (system dialog from
  the Auto-record screen), then `startObservingDevicePresence` → the OS calls `record/CarPresenceService`
  directly on connect and grants a background FGS start (API 34+). New: `CompanionCarManager.kt`,
  `CarPresenceService.kt`; `AutoRecordController.onCompanionPresence` arms the existing provisional trip +
  90 s motion-confirm. **NEEDS the owner's real drive to verify** (the `cdm-observe → onDeviceAppeared →
  ARM → FGS start OK` chain can't be exercised over USB).
- **Rev AH (v2.72): harsh-stop detector recalibrated from 27 real trips.** It fired on 0/27. Pulled the
  live DB, replayed the detector in Python: (1) the stop-gate required the *previous* 1 Hz GPS sample
  ≥ 8 km/h, missing ~90% of stops on a normal decel ramp; (2) sample-to-sample "jerk" on 50 Hz accel was
  noise. Now a stop = crossing < 3 km/h preceded by real movement within 4 s; harshness = smoothed peak
  horizontal decel ≥ 3.0 m/s² (~0.31 g). 7 harsh stops across the 27 trips. +3 tests.
- **Rev AI (v2.73): harsh stops as first-class events.** `EventType.HARSH_STOP` (timestamped); map marker
  (magenta octagon + exclamation), toggleable "Stops" filter chip, penalized in Safety (×2.5) + Comfort
  (×3.0). Compact event filter bar.
- **Rev AJ (v2.74): you-vs-traffic redesign + trip-screen cleanup.** One to-scale time axis (slate
  no-traffic + red traffic-delay box, a "you" marker anywhere, animated). Removed the Data-quality row,
  the detector-comparison, and event raw-signal dumps; "Advanced & charts" → "More stats".
- **Rev AK (v2.75): timeline + title polish.** Fixed the timeline's overlapping scale labels (fixed
  left/right legend) and tightened the scale; trip-title disambiguation is now time-only (no date).

## Current phone build

- Package: `com.cartrip.analyzer`
- Installed on S25: `versionName=2.75`, `versionCode=86` (Rev AK). All revisions through **AK** are pushed
  to `origin/main` (`c236b89`).
- Build artifact (relocated, see note): `C:\Users\sinan\cartrip-build-out\app\outputs\apk\debug\app-debug.apk`
- Maps key: now present in `cartrip-main\local.properties` (gitignored), copied from the original worktree; do not commit or print it.

### Build note: OneDrive lock workaround

OneDrive holds handles on `app/build` (kotlin-classes / snapshot), which breaks incremental
Kotlin compile with `Unable to delete directory`. Builds now relocate output out of OneDrive via an
init script:

```powershell
.\gradlew.bat --init-script 'C:\Users\sinan\cartrip-build-tools\relocate-build.gradle' :app:assembleDebug --no-daemon
```

The APK then lands under `C:\Users\sinan\cartrip-build-out\app\outputs\...`.

## Rev U (v2.59): hands-free auto-recording trigger — FIRST CUT (not yet device-tested)

Auto start/stop so the owner never taps Start (the phone always wireless-charges on a mount in the
hybrid Tucson). **Built + unit-tested with no device connected — the owner installs/tests within the
hour; the Android background-FGS path MUST be verified on device (see caveat).**

- **`record/AutoRecordPolicy.kt`** (pure, 7 unit tests): `triggerPresent` / `shouldArm` / `shouldStop`
  from booleans (enabled, charging, wireless, carBtConnected, recording). Charging is the primary in-car
  signal; Bluetooth (a specific car device) optional; `requireWireless` ignores wired charging at home.
- **`record/AutoRecordPrefs.kt`** (SharedPreferences `cartrip_autorecord`): enabled (default off),
  requireCharging, requireWireless, useBluetooth, carBtAddress/Name; service consts MIN_SPEED_KMH=5,
  MOTION_CONFIRM_MS=45 000, STOP_GRACE_MS=8 000.
- **`record/AutoRecordController.kt`**: reads live battery + config, applies the policy, drives the
  service. Wraps `startForegroundService` in try/catch; on failure posts a **"Drive detected — tap to
  start"** notification (a tap is a user interaction that DOES allow the FGS start).
- **Receivers** (manifest, `exported=false`): `PowerConnectionReceiver`
  (`ACTION_POWER_CONNECTED/DISCONNECTED`) and `CarBluetoothReceiver` (`ACL_CONNECTED/DISCONNECTED`,
  inert until a car device is picked — matches by address, no `BLUETOOTH_CONNECT` needed to read it).
- **`RecordingService`**: `ACTION_AUTO_ARM` records **provisionally** and runs a 45 s motion-confirm —
  if speed never reaches 5 km/h it **silently discards** the trip (deletes all rows, `RecordingState.reset`,
  no junk trip from charging-while-parked). `ACTION_AUTO_STOP_GRACE` stops after 8 s unless
  `ACTION_CHARGING_RESUMED` cancels it. Manual Start/Stop untouched (`autoStarted=false`).
- **`ui/AutoRecordScreen.kt`** (Home → Sensors icon): enable toggle, require-wireless, use-Bluetooth +
  paired-device picker, and a note about the background-start limitation.
- Manifest: two receivers + `BLUETOOTH_CONNECT`.

⚠️ **Open / verify on device:** (1) Android 12+ throws `ForegroundServiceStartNotAllowedException` when a
`location` FGS is started from a background receiver — confirm whether the charge-connect path silently
starts or falls back to the tap-to-start notification; the hands-free fix is
`CompanionDeviceManager.startObservingDevicePresence`. (2) Whether `ACL_CONNECTED` reaches a manifest
receiver on Android 14 (may need runtime registration). (3) End-to-end: plug in + drive → records;
unplug → stops after grace; plug while parked → discards. 7 tests added (73 total). Build green.

## Rev T (v2.58): hybrid fuel defaults + Past-Trips list/map polish

Owner fix-requests:
- **Hybrid Tucson:** `FuelEstimator.DEFAULT` reseeded for a **2023 Tucson Hybrid (HEV) AWD** — ~6.4 city
  / 6.6 hwy L/100km (was the 2.5L gas 10.2/8.4). Tests updated (a hybrid's city economy is *better* than
  its highway economy, so the old "city > hwy" assumption was dropped). NOTE: the on-device profile is
  stored in prefs and isn't changed by the default — set City 6.4 / Hwy 6.6 (calibration 1.0) in the
  fuel settings, or keep calibrating from real car readings.
- **Past-Trips trip rows:** title now spans the **full card width on its own line** (number + name +
  SAMPLE badge), single-line with ellipsis; distance/duration **and** the Safe/Comf/Pace scores moved to
  a compact meta row below — so geocoded "A → B" names no longer wrap to 2 lines.
- **Score header:** "Speed" → **"Pace"** (consistent with the hero/Insights) + `softWrap=false` so it
  never wraps to a 2nd line.
- **Frozen map:** bigger — near-edge (6 dp side padding) and taller (264 dp).
- **Map markers:** start/end icons smaller (72 px) and **semi-transparent (alpha 0.7)** so the route
  shows through; the **replay car rides on top** (`zIndex=10`) above the route and all other markers.

Build + 66 tests green; installed v2.58/69. All six fixes verified on device.

## Rev S (v2.56–2.57): ETA range gauge, peak-speed gauge, fuel card, 2-line names (UI polish)

**v2.57 completed the two remaining HANDOFF §9.1 items the v2.56 WIP had not:**
- **Speeding `PeakSpeedGauge`** now headlines the overage **"+X km/h over"** (red, highlighted) when
  speeding, falling back to the peak speed when not — matching the "+X over" spec.
- **Fuel & cost card** rebuilt: per-stat **icons** (fuel/cost/economy), **split value/unit** so the
  `L/100km` no longer wraps the number (`FuelCell`), and a highlighted **economy-rating chip** comparing
  the drive's L/100km to the vehicle's effective combined rating ("12% less/more fuel than your Tucson's
  9.4 rating", green/red with a trend icon).

v2.56 had finished the first two §9.1 items:
- **You vs Google ETA** redrawn as a shared time axis: Google's best-case..typical estimate is a single
  **range band**, "You" is a bar landing on the same axis (left of the band = faster), rows ordered
  shortest-first, You coloured by its deficit and pulsing. Replaces the old three stacked bars
  (`EtaBars`/`EtaBarRow`/`etaVsGoogleText` removed).
- **Speeding row**: the peak-vs-limit readout is now a compact **vertical `PeakSpeedGauge`** on the side
  (peak filled bottom-up, blue to the limit then red for the overage, limit tick) next to the full-width
  speed-tier sparkline (replaces the horizontal `PeakVsLimitBar`).
- **Past Trips**: place-name labels with a "→" (e.g. "Mississauga → North York") wrap to 2 lines with
  ellipsis instead of truncating after the arrow; score column tightened.

Build + 66 tests green; installed v2.56/67 (later verified on device in the Rev T pass).

## Rev R (v2.55): past-trips compaction & driving infographics (UI overhaul 3/3)

- **Past trips compacted:** the preview map is pulled up and shorter (240 -> 196 dp, less vertical
  padding), the "Tap a trip to preview ..." hint line is removed, and the standalone Safe/Comf/Speed
  header row is gone — those column labels now ride on the **first section header row** (e.g. next to
  "Last 24 hours"), so more trips are visible.
- **Speeding shown visually:** a thin **trip sparkline** (blue, turning yellow/red where you sped)
  spans the whole trip, the "% of covered drive" text is removed, and "Peak X in a Y zone" becomes a
  **peak-vs-limit bar** (blue up to the limit, red overage) with a short caption.
- **Hard braking / acceleration / sharp turns** rows became small **infographics**: an intensity bar
  (strongest g vs a per-type "very hard" reference) plus **count pips** + the number.
- **Verified on device (unlocked this time):** list names/layout, header "Mississauga -> North York",
  headline total cost ($3.90, matching the Fuel & cost card), you-vs-traffic bars + rabbit emoji +
  "03:11 faster than Google (9%)", relocated filter chips, speeding sparkline + peak-vs-limit, and the
  sharp-turns intensity bar all render correctly. Build + 66 tests green; installed v2.55/66.

## Rev Q (v2.54): header, you-vs-traffic, fuel headline (UI overhaul 2/3)

- **Trip-detail header** is no longer just the date: manual trip name -> reverse-geocoded place-name
  (`GeoNamer`, cached/fail-soft) -> relative date. New `Format.relativeDay` ("Today"/"Yesterday"/date)
  and `TripViewModel.loadTripTitle` (IO), driven via `produceState`.
- **You vs traffic redesigned:** the 3-dots-on-a-line gauge is replaced by **three stacked,
  animated horizontal bars** (No traffic / Google / Actual, legend order == bar order) on a shared
  offset scale (shortest still ~30% so all read as bars). Bars grow in on load; the **Actual bar
  pulses**. Adds an explicit **+/- vs Google** line ("m:ss faster/slower (x%)"), a **tinted Maps
  pin** on the Google row, better colours, and a **playful emoji animal** (turtle/dolphin/rabbit/
  horse) for how the drive compared with Google's typical time. Emoji are built from code points so
  the source stays ASCII (the Cp1252 trap).
- **Fuel cost confirmed live — no Re-analyze needed:** `FuelCostCard` (and now the headline)
  recompute from the current `VehiclePrefs` + the trip's stored aggregates every composition, so
  changing economy/price and returning to the screen updates the numbers automatically. **Total
  cost** added to the `TripHero` headline next to time + distance.
- Build + 66 tests green; installed v2.54/65. UI not visually confirmed (device dozing; launched OK).

## Rev P (v2.53): trip-map markers & replay (UI overhaul 1/3)

First slice of the trip-detail UI overhaul. Map markers redrawn as symbolic glyphs (no letters):
brake = red **stop-sign octagon**, turn = bent **turn arrow**, swerve = **S-curve**, pothole = amber
**bump sign** (hump), start = green **flag**, end = red **checkered flag**, and the "you"/replay
marker = a small **car silhouette**. Brake & turn markers are ~30% smaller (`markerIcon` now takes a
size). All drawn with Canvas paths in `TripMap.kt` (ASCII-safe).

- **Start/End markers are now tappable like the bottom-left chips:** first tap shows the label
  popup, a second tap opens Google Maps for that point (`onStartOpen`/`onStopOpen`; map-click clears
  the armed state; info-window tap also opens Maps).
- **Replay timing:** ~50% longer (`tripMs / 240f`, 5 s up to ~20 min, capped 45 s), a **1 s start
  delay** on open, and the speed/clock **readouts throttled to ~5 Hz with an eased speed** so the
  digits stay readable (the cursor/map still animate at 60fps).
- **Filter chips moved below the replay scrubber** and compacted, with a "Filter" affordance and
  per-type labels so they read as filters, not stats.
- Build + 66 tests green, installed v2.53/64. UI not visually confirmed (device dozing; launched
  without crashing).

## Rev O (v2.52): reverse-geocoded trip names

Past-trips list now names unnamed trips by reverse-geocoding their start/end, e.g.
`North York -> Scarborough`, falling back to the static `TripLabeler` on any failure. No schema change.

- New `GeoNamer` (ui): pure, Android-free label composition (`describe`/`compose`/`cellKey`) layered
  over an Android `Geocoder` lookup that is **fail-soft** -- if the geocoder is absent, offline,
  rate-limited, returns null/empty, or throws, it returns null and the caller uses `TripLabeler`.
  - **Caching:** results are cached in a `cartrip_geocache` SharedPreferences file keyed by
    *quantized* coordinates (~110 m cells, 3 dp), so the list does not re-geocode every trip on every
    refresh. A "no usable name here" sentinel is cached too (so dead cells aren't retried); transient
    failures are **not** cached (a later refresh can succeed).
  - **Budget:** `TripViewModel.loadTripLabels` shares a per-refresh `GeoNamer.Budget` (12 live
    lookups) so a cold cache can't fire dozens of network calls at once; cached cells are free, so
    over a few refreshes (and across runs, via SharedPreferences) the whole list fills in.
  - Only **unnamed** trips are geocoded -- the list shows `trip.name.ifBlank { label }`, so a
    user-named trip never spends a geocode. Still runs entirely on `Dispatchers.IO`.
  - The visible separator is a real arrow glyph built from its code point (`GeoNamer.ARROW =
    0x2192.toChar()...`) rather than a literal `->` in source: the Kotlin compiler reads a BOM-less
    `.kt` as the platform charset (Cp1252 on Windows) and mojibakes a literal arrow, so the source is
    kept pure ASCII. (Found the hard way: the first build's test failed on exactly this.)
- 8 new pure unit tests in `GeoNamerTest` (describe priority, compose forms incl. loop/one-sided/null
  fallback, cell quantization). Full suite **66 tests green**.
- Folded in the small **Option 2** UI cleanup: removed the noisy `fusedConfidence` ("forward-axis
  confidence") line from the trip-detail data-quality section -- it's empirically ~0.06 even on the
  best trip and never gates trust. (The scattered dead composables in `TripDetailScreen.kt` --
  `FactorBar`, `FactorCell`, `TripLinksCard`, `ScorePanel`, etc. -- were left alone: risky cosmetic
  surgery in a critical screen for no functional gain.)
- Build + tests + install OK (v2.52/63 on the S25). UI smoke test **not** visually confirmed: the
  device was locked/dozing, so the app launched without crashing but the labels weren't eyeballed.

## Rev N (v2.51): fuel & trip-cost feature

Estimates fuel use and $ cost per trip from stored aggregates (distance, avg moving speed, idle) — no
schema change. New pure `FuelEstimator` (analysis): a speed-dependent L/100km curve anchored on the
vehicle's city/highway ratings (U-shaped: worst crawling, best ~35–95 km/h, mild aero penalty past 110)
+ idle burn, all scaled by a `calibration` factor. Seeded for a **2023 Hyundai Tucson 2.5L AWD**
(~10.2 city / 8.4 hwy L/100km) at a **GTA regular price ~$1.84/L** (June 2026: McTeague ~178¢, StatCan
~189¢); everything user-editable.

- `VehiclePrefs` (SharedPreferences) stores the single active vehicle profile.
- `VehicleScreen` (Home → fuel icon): edit year/make/model, city/hwy economy, fuel price, and a
  calibration factor — incl. a "calibrate from your car's reported combined L/100km" helper.
- Trip detail gains a **Fuel & cost** card (litres, $, effective L/100km); Insights gains **Fuel / trip**
  and **Cost / trip** mini-stats with a window total.
- **Calibration loop:** the car reports actual distance / fuel / L-100km per trip; feeding those back
  (edit the ratings or the factor, or via the helper) converges the estimate. 5 `FuelEstimatorTest`
  added (+5 FuelEstimatorTest). On-device UI later verified in the Rev T pass.

## Rev M (v2.50): fused event magnitude = maneuver peak

Fused events fired (and stored their magnitude) at the *first* sample that crossed the threshold, but
a hard maneuver keeps building afterward — a narrated 0.5 g brake was stored as 0.28 g (~2× under). The
stored magnitude is now the peak over a 1500 ms forward window (`PEAK_WINDOW_MS`); the longitudinal peak
scan ignores samples that are vertical-bump- or turn-dominated so a following pothole/corner can't
inflate it. Event counts and the percentile `maxHorizGForce` are unchanged — this only fixes the
per-event severity shown on map markers, the replay, the export sheet, and (next) the Safety term.
One regression test added (`eventMagnitudeReflectsManeuverPeakNotFirstCrossing`); 52 tests total.

## Rev L (v2.49): promoted the fused detector into the Safety score

The 1 Hz GPS detector that drove Safety is blind to hard braking/cornering — trips 845/847 (and most
of the trip history) reported `hardBrakeCount=0`/`hardCornerCount=0` despite many narrated hard events,
because the Kalman + lateral low-pass wash brief ~0.3 g events below the 3.0 / 3.5 m/s² thresholds. So
**Safety was pinned at 98–100 regardless of how hard you drove** (only speeding or a peak-G outlier
moved it).

- Safety's hard-maneuver term is now `gpsHardPenalty` (GPS exposure %) **blended by motion-Hz trust
  with** `fusedHardPenalty = min(28, (motionBrake·7 + motionAccel·3.5 + motionTurn·1.2) / max(2 km, dist))`.
  On dense-motion trips (trust → 1) the corner-corrected sensor detector drives the term; old/low-rate
  trips still use GPS exposure unchanged. Turns are weighted low (firm city corners aren't unsafe) and
  the term is capped so one rough stretch can't zero the score.
- Field-calibrated on the trip set: calm long drives stay 97–98, the deliberately-aggressive test drives
  drop to ~92–95, and a dense-hard benchmark to ~89–90. Three `TripScoresTest` added (51 tests total).
- Existing trips show the new score after **Re-analyze** (re-runs the detector + recomputes counts/scores).

Open refinements: fused event magnitudes under-report (fire at first threshold crossing, not the peak),
so a severity-weighted term would be a better long-term Safety signal than a raw count rate; and a
dedicated severe-corner count (≥~0.45 g) would let hard cornering register in Safety without penalizing
normal turns.

## Rev K (v2.48): field-data calibration — corners no longer logged as linear accel

Analyzed two narrated field drives (`Recording 34.txt`→trip 845; `Recording 35.txt`→trip 847) pulled
off the S25, aligned to the transcripts. Capture was excellent (motion ~46 Hz, clean 1 Hz GPS/0 gaps,
GNSS now logged). Confirmed the owner's hunch — **fast corners were being scored as large linear
accelerations**:

- Trip 847 logged its single hardest "ACCEL" (0.47 g, conf 0.90) 1.4 s after a CORNER during the
  narrated "curve / hard turn"; both narrated swerves on each trip also fabricated phantom ACCEL/BRAKE.
- Root cause in `FusedEventDetector`: the longitudinal turn-veto compared the **instantaneous**
  centripetal estimate (`speed × gyro-yaw`) to the horizontal-accel magnitude, but those two peak on
  different samples within a corner, and corner/longitudinal have independent debounces.
- **Fix:** the veto now uses a **windowed** centripetal max (`CORNER_VETO_MS = 450`); and an
  ambiguous-GPS-slope spike during clear rotation (`AMB_TURN_YAW = 0.20` rad/s or
  `AMB_TURN_LAT_G = 0.15` g, windowed) is treated as steering instead of being guessed as brake/accel.
- Validated offline against the exact stored raw data: removes precisely the 6 corner/swerve-contaminated
  longitudinals across both trips while keeping every genuine narrated brake/accel. Two regression tests
  added (`cornerForceNotMiscountedAsAcceleration`, `swerveWithAmbiguousSlopeIsNotALongitudinal`); 48 tests green.

Still open from this field test (see HANDOFF §8 O7): the **GPS detector that drives the score** reports
0 hard brakes / 0 hard corners on both trips despite many narrated hard events (1 Hz Kalman + lateral
low-pass wash transients under the 3.0 / 3.5 m/s² thresholds). Decision pending — lower GPS thresholds
vs. promote the fused detector to scored.

## Rev J (v2.42–v2.43): detection fixes + graphical UX — branch `rev-g-functional`

- **J1 (v2.42) — event detection:** fixed the clustering bug where distinct slow-drive events
  10–30 s apart merged into one (3 swerves + a brake across 31 s). `DisplayEvents` now clusters by
  time only (4 s window, hard 8 s span cap); the detail card's grouped-signals list is ±6 s
  (no spatial chaining). **Speed-aware turn/swerve flagging**: <10 km/h never; 10–30 km/h needs
  ≥0.40g; ≥30 km/h needs ≥0.30g (a 0.26g swerve at 20 km/h is gone). Corner detection 0.27→0.32 g.
  Brake/accel left as-is. **Variable replay autoplay**: max(5 s, tripSeconds/360), capped 30 s.
- **J2 (v2.43) — graphical UX:** landscape (car-mount) shows one full-screen Start/Stop button for
  eyes-free pressing (verified on device); portrait buttons enlarged. Trip hero shows Safety/Comfort/
  Pace as small score rings (fixes word-wrap); avg speed dropped from the headline. Event filter
  toggles are compact icon chips.

- **J3 (v2.44) — graphical redesign:** trip hero drops the overall ring, centered/compacted (time +
  clock/route chips + Safety/Comfort/Pace rings). **You vs Traffic** is now one range gauge
  (free-flow→typical band + a "you" marker, Maps green/amber/red, one-word verdict, tight legend) —
  much less text. Replay header: big circular play button, clean M:SS/M:SS timer, live SpeedGauge.
  Removed dead Eta composables.
- **J4 (v2.45) — rough road:** replaced the opaque "rough road %" with discrete **rough stretches**
  (runs of ≥1 s sustained vibration) + a `bumpyScore` (RMS×duration). Schema v17 (migration 16→17);
  Road & ride card shows the stretch count.

- **J5 (v2.46) — review feedback:** lowercase am/pm with no dots/space ("12:14pm"); You-vs-Traffic
  gauge line now runs only between the two outer dots (no overhang), recoloured (blue "No traffic" /
  grey "Google" / perf-coloured "Actual"), legend renamed; replay speed readout averaged so it
  doesn't flicker.
- **J6 (v2.47) — consolidation:** folded the standalone Events card into the Driving card as an
  expandable "All events · N" list (summaries + speeding always visible, detail on demand). Removed
  dead EventsSection/CountPill.

Test suite 46, all green. All of the 2026-06-24 review feedback addressed and verified on device.

## Rev I (v2.40–v2.41): field-test prep + review UX — branch `rev-g-functional`

Field-test enablement (capture visibility + richer data) and review-experience changes.

- **Debug screen** (home title-bar bug icon): live capture health — motion Hz, GPS Hz, GNSS
  sats/C/N0/L5, sensor restarts — so capture can be confirmed mid-drive; build info; last
  speed-limit cache + lookup/Routes diagnostics. Validated live: motion 44 Hz, GNSS callback firing,
  cache served tiles 2/2 from cache.
- **Per-window GNSS capture**: new `gnss_samples` table (schema v16) written ~every 3 s for
  route-level GNSS analysis; fail-soft, trimmed/purged/deleted with the trip. Validated: 19 samples
  on a 58 s trip; per-trip `gnssSampleCount` populated.
- **Speeding colour tiers** (`SpeedTier`, unit-tested): yellow 0–10 km/h over, red 10+; on the map
  overlay and the replay speed curve. Replaces the old single-threshold red.
- **Bumps/potholes off by default** in the event filter; **"Speed" → "Speeding"**; trimmed event
  detail text.
- **Replay autoplay**: scrubs the whole trip over ~5 s on load, then a play button to replay; manual
  scrubbing pauses.
- **Past Trips**: frozen map on top + scrolling list; first tap previews a trip's route on the map
  (and shows time/quality/vs-Google), second tap opens it.
- `FIELD_TEST_PLAN.md` added (pre-drive check, narration, 13 scenarios).

Test suite 39 unit tests (added `SpeedTier`). Migrations 13→16 verified on device. Note: live
DB reads via `run-as cat` can be WAL-stale — pull `cartrip.db-wal`/`-shm` too for accurate counts.

## Rev H (v2.37–v2.39): caching, GNSS, list structure — branch `rev-g-functional`

Addendum work, prioritised for technical value (accuracy/robustness/reduced API dependency).
First a verification pass on the prior backlog (auto-stop, ETA fallback, naming, odd-turn all done
in Rev G; You-vs-Traffic and replay timeline already existed; AM/PM, debug mode, passive mode,
fuel/cost still open).

- **H1 (v2.37):** AM/PM time formatting; Past Trips grouped into collapsible recency buckets
  (Last 24 h / 3 days / 7 days / Last month / Older) via pure `TripBuckets`.
- **H2 (v2.38) — speed-limit cache:** new Room tables (schema v14) `cached_ways` (keyed by stable
  **OSM way id**, not Google Roads placeId — this app uses OSM/Overpass, and ODbL permits caching)
  and `cached_tiles` (~2.2 km fetch markers). `SpeedLimits` is now cache-first: only Overpass-queries
  tiles not fetched within a 30-day TTL, then serves the route from cache → a repeat drive makes zero
  network calls. `Tiles` helper (pure, tested); `lastCacheStat()` for debug.
- **H3 (v2.39) — GNSS quality layer:** `GnssStatus.Callback` aggregates a per-trip summary (sats
  used, mean/top C/N0, L5 seen) into trip schema v15; `GnssQuality` (pure, tested) → Strong/Moderate/
  Weak; folded into the data-quality badge (weak sky view caps High→Medium; detail shows "GNSS N
  sats M dB-Hz L5"). Diagnostics/confidence only — does not replace fused positioning.

Test suite now 35 unit tests (all green). Migrations 13→14→15 verified on the device DB.
Deferred (next): speed-limit-cache population QA (drive same road twice), reverse-geocoded naming,
debug screen (surface `lastCacheStat`/GNSS), passive auto-start, fuel/cost, frozen-map trip selection.

## Rev G (v2.35 / v2.36): functional hardening — branch `rev-g-functional`

Directive: focus on functionality, accuracy, correctness, robustness, sensitivity — not UI. Added
the project's first unit-test suite (23 tests) and used it to verify each fix.

- **Auto-stop end time (P0):** trip end is now placed at the real rest moment (last sample >4 km/h,
  then first stationary sample after it) instead of a fixed +60 s grace ~6 min late. New pure
  `record/AutoStop.kt`, fed by a rolling speed window in `RecordingService`.
- **Robust peak-G:** `FusedEventDetector.maxHorizG` uses p99.5 of horizontal-accel magnitude, not the
  raw max. Verified on real data: trip 784's spurious 1.10 g → 0.29 g; 790's 0.73 g → 0.14 g.
- **Sensor-stall recovery:** now also recovers a mid-trip motion stall, not just startup starvation.
- **Detection accuracy:** swerves gated on speed (≥25 km/h) so routine low-speed turns aren't labelled
  swerves; vertical-bump veto stops potholes fabricating brake/accel events.
- **ETA robustness:** `RoutesClient` retries transient failures with backoff, longer timeouts for long
  routes, and surfaces `lastDiagnostic()` instead of a silent null.
- **Trip naming:** nearest-place matching guarded by a 4 km radius (no more stray GTA landmark for
  out-of-area trips); time-of-day generic fallback. Full reverse-geocoding remains a roadmap item.
- Minor: `DisplayEvents.nearestPoint` trailing-event branch fix; correct `rawFixes` on trip reload.

## Main changes since v2.21

## Build environment used

PowerShell from `C:\Users\sinan\OneDrive\Desktop\cartrip-main`.

```powershell
$env:ANDROID_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk'
$env:JAVA_HOME='C:\Users\sinan\AppData\Local\cartrip-build-tools\jdk17\jdk-17.0.19+10'
.\gradlew.bat :app:assembleDebug --no-daemon
```

Install command:

```powershell
$adb='C:\Users\sinan\AppData\Local\cartrip-build-tools\android-sdk\platform-tools\adb.exe'
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Git may not be on the default shell PATH. Use:

```powershell
& 'C:\Program Files\Git\cmd\git.exe' status
```

## Main changes since v2.21

### Restored pre-UX functional work

- Restored the stashed functional changes that had produced the earlier installed `2.26 / 37` build.
- Added `DisplayEvents.kt` for cleaning overlapping raw detector signals into a smaller human-facing event list.
- Restored richer trip detail event presentation, clickable map event markers, and speed-limit preservation through reanalysis.
- Kept work on the main branch/worktree only; the separate UX redesign files were not merged.

### Speed-limit lookup and scoring fixes

- Rewrote `SpeedLimits.kt` to query Overpass in route-corridor chunks instead of one broad city-sized bounding box.
- Added OSM way dedupe, bounds pruning during nearest-way matching, and fail-soft diagnostics.
- Added smoothing for isolated one-point speed-limit mismatches. This avoids false peaks such as one highway point snapping to a nearby 50 km/h road.
- Added transactional speed-limit update in `TripDao.updateTripSpeedLimits(...)` so trip aggregates and per-point limits update together.
- Trip detail now uses route point coverage, not just aggregate trip coverage, to decide whether route coloring is available.
- Existing scored trips now show a `Refresh speed limits` control.

### Field-test findings

- Trip `786` morning commute had 2,488 GPS fixes but only 1 motion sample. GPS continued until trip end; motion stopped near trip start. Later trip `790` recorded about 91k motion samples, so this was likely a sensor callback starvation/startup issue rather than a permanent permission or hardware issue.
- Added sensor-stall mitigation in `RecordingService`: when GPS is alive but motion samples do not arrive after startup, the service unregisters/re-registers sensors up to three times.
- Trip `790` showed `speeding 24% / peak 59 over`. The 24% was supported by the points, but the peak was inflated by isolated limit mismatches. With smoothing, the peak becomes roughly 25 km/h over.

### Recording changes

- Fused Location now requests `500 ms` updates with `250 ms` fastest interval, rather than `1000 ms` / `500 ms`.
- Fused Location callback now processes every location in a delivered batch instead of only `lastLocation`.
- GPS delivery may still be capped by Android/Play Services/GNSS; the app now requests a faster cadence but cannot force the provider to supply it.

### Sensor and data-quality UI

- Data quality now reflects capture health only: motion Hz, GPS Hz, and GPS gaps.
- The old `fusion X%` label was removed from the top badge because it was only forward-axis confidence, not the amount of motion data captured.
- Forward-axis confidence remains in the beta detector section where it has context.

### Trip detail UI changes

- Route maps now fit expanded bounds so the trip initially occupies about two-thirds of the viewport instead of being tightly framed.
- Removed Driving-section percentage bars for braking, turns, acceleration, and jerky exposure from the rendered UI.
- Replaced them with a major-events summary for notable hard braking, hard acceleration, and sharp turns using the cleaned event list.
- Speeding is shown as a readable callout with:
  - time spent over the limit,
  - percent of covered drive,
  - peak recorded speed and matched speed-limit zone.

## Current caveats / next work

- The old `FactorBar` helper remains unused in `TripDetailScreen.kt` and can be removed in a cleanup pass.
- The beta sensor detector should eventually become a validated hybrid detector:
  - GPS provides route/speed context and brake-vs-accel sign.
  - Accelerometer provides true horizontal g and exact event timing.
  - Gyro provides yaw/turn evidence.
  - Vertical acceleration should suppress false brake/turn spikes caused by bumps.
- Consider replacing the single `fusedConfidence` field with clearer fields:
  - sensor capture quality,
  - axis confidence,
  - event agreement.
- Past trips cannot gain extra GPS fixes from the new 2 Hz request. Reanalysis can only recompute from samples that already exist.
