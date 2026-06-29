# Roadmap additions (owner-requested 2026-06-28, post-Batch-4)

New items from the owner, assessed + ordered by value × (1/risk). Worked top-down ("clear the log,
easiest/least-risk first"). Rev letters continue from CJ.

> **⚠️ FRONTIER (updated 2026-06-28 — source v3.31 / build 142, schema v21, 200 tests, all merged to `main`
> and pushed):** The authoritative plan lives in **`HANDOFF.md` §14 / §14.1** — read that first; this box is the
> quick status. Shipped since the CK–CN batch (v3.22): **Rev CO** doc-truth pass; **Rev CP** Drive Stress Score
> depth, **then `StressScore` + `TripKind` decoupled into `analysis/` (v3.26)**; **CR export-file retention**
> (v3.27); **Room migration-test FOUNDATION** (`exportSchema=true` + `schemas/.../21.json`, v3.27); **Places
> scaffold** (flag **OFF** / inert, v3.27); **Rev CP cont. (this session, v3.28–v3.31):** Trip-Detail
> **"Reset to automatic"** + a **visible OSM/ODbL attribution** credit (v3.28), a pure **shared bar-scale**
> helper / bar-sizing audit (v3.29), AI-export **Traffic** + **"When you drive"** sections (v3.30/v3.31), and
> export value-mapping tests. **On-device verified (S25 @ 3.31):** the "©" attribution renders correctly and the
> duration bars aren't edge-to-edge. **Still open (priority order):** **Past-Trips open affordance** — it's an intentional
> two-tap (tap once to preview, then tap the *same* trip again to open), **verified working on-device
> 2026-06-29**; the small wart is the second tap is easy to miss after the preview-line shift → make it more
> discoverable (lower priority, see §14 CP); **CQ** = *activate* Places (paid — needs the owner's go-ahead, a UI toggle,
> diagnostics, Geocoder-fallback wiring); **CR** = commercialization hardening (privacy policy, Data Safety,
> background-location / foreground-only) and **SQLCipher + biometric** at-rest encryption (gated, higher-risk —
> ask first). The Tier A–C items below are older backlog; cross-reference §14 + §13.3 before starting.

## Review notes (owner, 2026-06-29, post-Stress-v2) — solutions + build plan

Owner review of the recent Insights/chart/bar/trip-view updates + a new "drive stress built up over time"
concept. Assessment, solutions, and sequencing below; **pushback marked**. (Drive Stress v2 itself — Rev CS —
is shipped + device-verified; this is what's next.)

### Build plan (rev sequence, value x (1/risk))
- **Rev CV — Trip-view event defaults — DONE (v3.33, device-verified):** the "All events" list hides
  bumps/potholes by default (the Bumps chip reveals them); the list already opened by default. `ui/TripDetailScreen.kt`.
- **Rev CT — Insights chart & filter overhaul — DONE (v3.33–v3.34, device-verified):** dynamic
  `1/3/7/30/All` days filter (default 7d, dropped 500 km) + Drive-Stress chart fixed **0..100** with y-axis
  labels, no min/max footer, x-axis label; the `TimeSeriesChart` rework also cleaned the fuel/traffic chart
  axes. **CT-fuel — DONE (v3.34, device-verified):** new **"Fuel economy vs your average"** chart — a new
  `PercentChangeChart` plots each drive's L/100km as a smoothed **% deviation from the window mean** (the 0%
  line), green fill below (more efficient) / red above, plus a dashed **OEM reference line** read from the
  live `FuelEstimator.combinedL100(vehicle)` (no hardcode — the owner's profile shows 6.4 combined vs 7.0
  estimated avg = −8.6%). _Design note:_ used the **window mean** (single self-consistent baseline shared by
  the data + the OEM line) rather than a drifting 30-day rolling mean, so the OEM line is one fixed reference
  and the chart reads as a clean trend.
- **Rev CU — Bar-sizing pass 2 — DONE (v3.34, device-verified):** applied `BarScale` to the **"When you
  drive" daypart bars** (busiest now fills ~80%, not edge-to-edge — verified Morning 16→80%, Evening 15→75%,
  Midday 10→50% on a max-20 axis). _Left as-is (already correct):_ `PeakLimitBar` fills ~87% by construction
  (`peak*1.15` headroom + labels); `SpeedingShareBar` is a true 0-100% proportion with a min-visible floor
  (full = 100% of moving time is meaningful, like the score bars). _Still open:_ `EtaCompare` lives on the
  **Trip-Detail** screen (not Insights) — code-review shows it already caps at ~80% via `niceEtaAxisMaxMin`
  (headroom 1.25); the owner's "near full width" likely a specific trip or the wipe animation, so it needs an
  owner-pointed on-device re-check rather than a blind change.
- **Rev CW — Driver-Load / "Drive readiness" model — DONE (v3.35, device-verified):** the cumulative,
  time-decaying load index + 24 h recovery forecast. `analysis/DriverLoad.kt` (pure leaky integrator, TAU
  28.8 h, +8 tests) DB-replay-calibrated (`SATURATION_K=1.0`: median real day ~54/Moderate, heaviest ~78);
  `DriverLoadCard` in Insights (load + readiness + one-line read + 24 h recovery curve + medical disclaimer).
  Device-verified: live load 67/Elevated, decaying from the morning peak as the model predicts. **Phase 2 —
  DONE (v3.36):** the ACWR acute(~7 d)-vs-chronic(~28 d) "above your recent norm" overload flag — a tinted
  chip (red > 1.5). Correctly suppressed on the owner's 7-day history (needs ≥14 d for a real chronic
  baseline); render-checked on-device via a temporary lowered floor. Constant tuning stays open as data lands.
- _Misc (low priority):_ trip-end trim leaves a few seconds at 0 km/h on the tail — tighten
  `AutoStop.retrospectiveStopTime` to drop a trailing standstill (one observed case; fold into a polish rev).

### Next-agent pickup — review-note batch COMPLETE; leftovers + frontier (start here)

Current state: **v3.36 / build 147, schema v22, 222 tests, CV / CT / CT-fuel / CU / CW (+ phase 2) all done +
device-verified on the S25.** (Push status: confirm with `git log origin/main..main` — CT-fuel+CU+CW were
pushed; CW phase 2 may be local-only pending owner OK.) Build/test/device workflow is HANDOFF section 2.1
(relocated Gradle init script; grep the log for `BUILD SUCCESSFUL`; bump version for runtime changes; install
+ eyeball on the S25). **The 2026-06-29 review-note build sequence is fully shipped.** What remains:

**Driver-Load follow-ups (when data allows):** the ACWR chip needs **≥14 days** of scorable history before it
surfaces (the owner had only 7.1 d on 2026-06-29) — re-verify on-device once that lands, and workshop the
`DriverLoad` constants (TAU / SATURATION_K / BASELINE_LOAD / ACWR thresholds) against more trips by DB-replay.

**Small leftovers (fold into a polish rev):**
- `EtaCompare` (Trip-Detail, `ui/TripDetailScreen.kt` ~ln 1497) — owner-pointed on-device re-check of the
  "near full width" report; the code already caps at ~80% via `niceEtaAxisMaxMin` (headroom 1.25), so this
  needs the owner to name a specific trip rather than a blind change.
- trip-end trim leaves a few seconds at 0 km/h on the tail — tighten `AutoStop.retrospectiveStopTime`.

Beyond the review notes, the older Tier A-C backlog + the §14 frontier items (Places activation **CQ**,
commercialization hardening **CR**, SQLCipher+biometric encryption — gated/ask-first) still stand.

### Item-by-item solutions
**Insights dynamic days filter (Rev CT).** Replace the `30 days / 500 km / All time` chips with
**`1d / 3d / 7d / 30d / All`** (matching the Past-Trips recency chips); the chosen window drives every Insights
section live (already windowed — widen the options + thread the window). _Pushback:_ a 1-day window is usually
0-2 drives (sparse) — keep it but **default 7d**; drop the niche `500 km` distance-window.

**Drive-Stress chart (Rev CT).** (a) **Fix the y-axis to 0..100** (full range, 0 at the x-axis) with labelled
gridlines (0/25/50/75/100) along the axis; **remove the "min X max Y" footer** (misleading on a fixed scale).
(b) **Label the x-axis:** the series is **per drive, oldest->newest** (one point per scorable drive, not per
km) — label "recent drives ->" or bucket by day for a true time axis. _Pushback:_ on a fixed 0..100 scale a
run of calm drives hugs the bottom (low contrast) — that's honest, and the **Driver-Load** curve (below) is
the better "trend over time" story. Needs a `TimeSeriesChart` variant with `yRange=0..100` + axis labels.

**Fuel & Cost chart (Rev CT).** Same y-axis treatment; plot **% change vs a 30-day rolling baseline** (=0%
line) so it reads "better/worse than your norm" (keep absolute L/100km in the caption); **smooth harder**
(longer trailing window); **red/green fill** when worse/better than the 30-day baseline; optional **OEM
reference line** from the Tucson combined L/100km rating (the `FuelEstimator.Vehicle` knows the vehicle).
_Pushback:_ "% change" needs an anchor — use the rolling 30-day mean as 0%, OEM as a separate reference line.

**Bars — auto-sizing pass 2 (Rev CU).** `BarScale` (v3.29) already gives the ETA bar a ~80% nice axis and
de-edge-to-edged the duration bars, but the owner still sees you-vs-traffic near full width: **(1) re-check
`EtaCompare` on-device** (likely the animated wipe, or actual ~= axis max — ensure the ~80% cap holds + the
scale is legible); **(2) apply BarScale to the rest** — the "When you drive" daypart bars (busiest daypart
fills full width today), `PeakLimitBar`, `SpeedingShareBar`, Insights mini-bars — each with a **minimum
visible bar**, ~80-90% max fill, a labelled scale, and padding. Leave the 0-100 score bars (full = 100 is correct).

**Trip-view event defaults (Rev CV).** Default the **"All events" list open** (confirm post Rev CE) and the
**Bumps/potholes filter OFF** so bumps don't clutter the default view — initial event-filter state in `ui/TripDetailScreen.kt`.

### ⭐ Driver-Load / "Drive readiness" — cumulative, time-decaying stress model (Rev CW, design v0.1)

The owner's concept: a single number that **builds with recent stressful driving and decays with time** (rest),
so "you may have to wait to lower it." This is a real, **evidence-based** pattern — a recency-weighted
cumulative load with exponential decay — not a per-trip score and not an average.

**Evidence base (established models this maps onto):**
- **Acute:Chronic Workload Ratio (ACWR), EWMA form** (Williams et al., Br J Sports Med 2017, PMID 28003238):
  injury risk rises when recent ("acute") load outpaces the longer ("chronic") baseline; the EWMA version
  weights recent loads more + decays older ones — more sensitive than rolling averages (optimal ratio
  ~0.8-1.5, risk climbs >1.5). Exactly "are you driving more/harder than your norm lately."
- **Banister fitness-fatigue (impulse-response) model:** state = slow-decaying "fitness" minus fast-decaying
  "fatigue", each an exponentially-weighted sum of impulses. Our load = the **fatigue** term (fast decay).
- **Two-process model of sleep regulation** (Borbely): homeostatic pressure builds with wake, **decays
  exponentially** with rest — same leaky-integrator math.
- **Driver fatigue / time-on-task** (FMCSA hours-of-service evidence): crash risk rises with consecutive +
  cumulative driving hours and insufficient rest — grounds "driving too much recently = elevated risk."

**Model (v0.1):**
- Each drive deposits a **load impulse** `I_i = (stress_i / 100) * durationHours_i` (stress-weighted driving
  time: a calm 5-min hop ~ 0; a 45-min stressful crawl = large).
- **Driver Load** `L(t) = sum_i I_i * exp(-(t - t_i) / TAU)` — an exponentially-weighted leaky-integrator sum:
  **additive**, accumulates per drive, **decays with time** (same math as the ACWR "acute" term).
- **TAU ~ 1.2 days** (half-life ~20 h): a drive 3 days old contributes `exp(-3/1.2) ~ 8%` ("little
  difference" — matches the owner). Tunable; calibrate over time.
- **Hourly + 24 h forecast:** with no further driving `L(t+h) = L(t) * exp(-h/TAU)` — show the recovery curve
  + "back to baseline by ~9pm".
- **Scale to 0-100** ("Driver Load" / inverse "Readiness"), calibrated so a normal commute peaks moderate, a
  heavy multi-stressful-drive day peaks high.
- **Phase 2 (the evidence-backed flag): ACWR overlay** — acute EWMA (TAU~1.5 d) vs chronic EWMA (TAU~21 d);
  ratio >1.5 = "well above your recent norm." Surfaces overload independent of the absolute scale.

**UX:** an Insights "Driver Load / Readiness" gauge + the 24 h recovery curve + a one-line read. Pure /
computable from the trip list (stress + duration + timestamp) — **no schema change**, forecastable.

**Disclaimer (important):** a heuristic "driving load" indicator from the user's own data — **NOT a medical /
fitness-to-drive assessment**. Real fatigue depends on sleep, which the app doesn't measure ("time not
driving" is only a proxy for recovery). Frame as wellness/awareness, not a safety certification.

**Workshop questions (calibrate over time):** what Load level reads "high / consider resting"? Does a mid-day
non-driving gap count as recovery (yes, via decay)? Should night / very-early driving deposit more load?
Include the ACWR "vs your norm" flag in v1 or defer to phase 2?

## ⭐ Drive Stress Score — depth (owner-requested, HIGH interest)

### ✅ Shipped in Rev CP (v3.24/build 135)
- **Trip hero placement.** `StressHeroPill` headlines the trip-detail hero — a labeled pill on the green→red
  stress scale (NOT a 4th green=good ring, since higher = worse). Replaced the old compact "Drive stress:" line.
- **Km-weighted average.** `StressScore.kmWeightedAvg(trips)` — each trip's 0..100 score weighted by distance
  so long stressful drives count proportionally more. ⚠️ This is a km-**weighted average on the 0..100 band**,
  NOT a per-km burden rate (it does not divide stress by distance). UI labels it "km-weighted."
- **Insights trend.** `StressTrendCard` replaced the single-average `StressSummaryRow`: km-weighted headline +
  a **trailing-average-smoothed** `TimeSeriesChart` of how stress evolved + a delta vs the previous window
  (rise = red). Pure `series`/`trailingAvg` helpers, unit-tested.

### ⏳ Still open (CP follow-ups)
- **Decouple `StressScore` out of `ui` — ✅ DONE (v3.26/build 137).** `StressScore` + `TripKind` (both pure)
  now live in `analysis/`; `color()` is `ui/StressColors`; `export/ExportData` imports `analysis.StressScore`
  (export→ui dependency removed). Tests moved to the `analysis` package; 179 green.
- **EMA vs trailing-average tuning** + per-user re-calibration as data grows (the trend currently uses a
  5-trip trailing average; an EMA may read smoother). Re-validate calibration by DB-replay.
- **(Optional) a true per-distance "burden" metric** — if we actually want stress *per km* (not a weighted
  average), that needs a defined stress-per-distance unit; design first. Not the same as `kmWeightedAvg`.

Notes: computed from stored `TripEntity` aggregates → **no schema change**. Needs enough trips to read as a
trend (owner's data was ~1 week at ship; grows over time).

## Tier A — easy, low risk (UI polish)
- **CK — Past-trips filter compaction + scroll affordance. ✅ DONE (v3.22).** Custom compact chips + an
  always-visible list scrollbar thumb. `ui/TripListScreen.kt`.
- **CL — Smart bar sizing. ✅ PARTIAL (v3.22).** You-vs-traffic now scales to a nice round-minute axis
  (~80% fill, minute scale). **Still TODO:** apply the same to `SpeedingShareBar`, `PeakLimitBar`
  (`ui/TripDetailScreen.kt`), `ui/Charts.kt` (`TimeSeriesChart`/`DivergingBarChart`), and `DurationBar`
  (`ui/TripListScreen.kt`) — nice max + headroom + **minimum** bar + scale labels, no edge-to-edge / near-zero.
- **CN — AI-readable export + share. ✅ DONE (v3.22).** `ui/AiInsightsExport.kt` builds a compact markdown
  summary (no raw GPS) shared via the Insights "Share for AI insights" button.

## Tier B — medium (analysis / value), low–medium risk
- **CM — POI-aware endpoint naming. ⏸ PAUSED — owner exploring the paid Google option.** The free on-device
  `Geocoder` rarely returns business names ("IKEA"), so a free version is best-effort only. The reliable path
  is **Places API (New)**. **Cost for a user like the owner: ~$0–3/month** (likely $0 within Google's free
  per-SKU allowance) with endpoint-only queries + aggressive cell caching — full analysis in **HANDOFF §13.4**.
  When enabled: new `cloud/Places.kt` + extend the `GeoNamer` cell cache (HANDOFF §11.4).

## Tier C — larger, higher risk (do deliberately, verify carefully)
- **CO — Encrypt-at-rest + biometric lock. ⏸ PAUSED — queued for a focused rev.** SQLCipher-encrypt the
  local DB (location history is sensitive) and gate app access behind BiometricPrompt. Needs a one-time
  encrypted-DB migration of the existing plaintext `cartrip.db`, a key in the Android Keystore, and a graceful
  fallback (device-credential when no biometrics). Pairs with the commercialization "secure the data" item and
  is a **pre-launch** privacy headline. Higher risk (DB layer + key management) — full plan in **HANDOFF
  §13.3 / §13.5**. Do not rush; verify the migration on-device.

## Commercialization / Play Store launch
Detailed phased roadmap + premium tiers in **HANDOFF §12 and §13.5**: pre-launch hardening (battery, CO
encryption, polish, AI coaching from the CN export), compliance (background-location disclosure +
foreground-only default, privacy policy, Data Safety), monetization infra (replace Sheets with a real
account/backend storing only compact summaries; Play Billing), and the premium dashboard (stress/drawdown
trends — built; Places destination analytics; cohort comparisons; AI coaching).

## Settings architecture — graduate from scattered options to a proper Settings system (premium redesign, UX item)
**Status: future redesign requirement (owner-requested 2026-06-29). NOT scheduled for implementation yet — this is
product/navigation/settings-architecture direction, not a task. Belongs to the premium-redesign track (see
`REDESIGN_PHASE1.md` and the `ux-premium-modular-v1` branch), and should land only after the engine extraction +
new UI shell, not before.**

**Problem.** Settings/preferences are currently scattered with no single home: the Home **Options sheet** (`Tune` icon
→ Guide / Diagnostics / Vehicle & fuel / Auto-record + an inline auto-record toggle), plus separate Vehicle, Auto-record,
Diagnostics, Guide screens, plus 8+ independent pref stores (`UiPrefs` — satellite map mode, "your trip" icon, event
threshold; `AutoRecordPrefs`; `GnssLoggingPrefs`; `settings/VehiclePrefs`; `cloud/CloudPrefs`; `cloud/PlacesPrefs`;
plus state in `GeoNamer`/`TripViewModel`). A user has no clear place to understand or customize app behavior.

**Direction.** As the app becomes a premium/local-first driving-intelligence product, the redesign should add a proper
top-level **Settings** area (the "More/Settings" tab in the UX build spec). Proposed sections:
1. **Recording** — manual vs auto-record; foreground-only vs background hands-free; wireless-charging requirement;
   Bluetooth/car pairing; haptics; battery/data retention.
2. **Vehicle & Fuel** — vehicle profile; fuel price; auto-update gas price; calibration. *(backed by `settings/VehiclePrefs`)*
3. **Maps & Display** — default map mode (satellite); "your trip" icon; event marker/filter prefs; units (if added). *(`UiPrefs`)*
4. **Insights** — stress trend smoothing; event sensitivity / g-force threshold; show/hide **beta** metrics; Places
   naming toggle (only if billing/API enabled).
5. **Privacy & Data** — exported-file cleanup; raw GNSS logging; delete all data; data retention; AI-export disclosure;
   cloud/Sheets sync controls. *(`GnssLoggingPrefs`, export retention, `CloudPrefs`)*
6. **Premium / Account (later)** — subscription status; enabled premium features; cloud backup; billing/account
   controls. *(driven by the `Entitlements` seam in `:core-engine`; wires up when Play Billing lands)*

**Notes / boundaries.**
- The current Home **Options sheet stays as the legacy-app mechanism** — do not rip it out. The redesigned premium app
  graduates to the proper Settings screen/system; legacy `ui/` remains the working oracle during extraction.
- Natural backend consolidation point: the `com.cartrip.analyzer.settings` package (created in Phase 1A) can grow into
  a coherent settings layer that the scattered `*Prefs` migrate behind, with a Settings UI on top.
- Sequencing: depends on the new UI shell; the beta-metric toggles (4) also depend on the analytics-validation track
  (so users can hide not-yet-trustworthy scores). Premium/Account (6) depends on the Entitlements→Play Billing swap.
- Keep it honest and local-first: privacy/data controls (5) are a launch-credibility headline, pairs with CO encryption.

## Cross-cutting principle (owner): always look for novel, innovative ways to present and add value/analysis.
Examples to fold in opportunistically: AI coaching from the CN export; stress/drawdown trends over time;
"your worst-traffic times/routes"; per-destination analytics once POI naming (CM) lands.
