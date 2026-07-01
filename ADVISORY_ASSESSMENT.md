# Advisory Assessment — Driving Health Tracker + Local-First Privacy

> **Status: ADVISORY / roadmap-planning, NOT an implementation command.** This doc converts
> `driving_health_tracker_privacy_roadmap_advisory_v2.md` (owner-supplied 2026-06-30) into durable
> guidance, *cross-checked against the actual code*. Nothing here is wired into the app. Product
> decisions (especially the connected-features defaults in §2.3) are still open and must be made by
> the owner before the related code changes.
>
> **Verified against:** branch `ux-premium-modular-v1`, app `versionName 3.36` / `versionCode 147`
> ([app/build.gradle.kts](app/build.gradle.kts)), Room schema v22 (`core-engine/schemas/.../22.json`).
> Every egress / privacy claim below was checked by reading the cited `file:line`. Re-verify before
> publishing any public privacy statement — the code is the source of truth, this doc is a snapshot.

---

## 0. TL;DR — why this is not a rewrite

The original advisory was written without knowledge of how mature the repo already is. After verifying
against the source:

- **Storage is already local-first.** Room DB only; no developer-run server; **no analytics or
  crash-reporting SDK** in either module (verified: [app/build.gradle.kts](app/build.gradle.kts),
  [core-engine/build.gradle.kts](core-engine/build.gradle.kts)).
- **"Sustained crawl" analytics already ship** — [StopAndGo.kt](core-engine/src/main/java/com/cartrip/analyzer/analysis/StopAndGo.kt)
  (crawl fraction, below-limit load, longest-no-break) already feeds Stress v2
  ([StressScore.kt](core-engine/src/main/java/com/cartrip/analyzer/analysis/StressScore.kt)).
- **Most of the privacy/release roadmap already exists** in [ROADMAP_NEW.md](ROADMAP_NEW.md) +
  HANDOFF §12/§13.5 (SQLCipher encryption "CO", Data Safety / background-location "CR", `Entitlements`
  seam, raw-sample retention purge, export/retention utilities).

So the work is **positioning + disclosure + one missing detector band + a sweep tool**, not a broad
rewrite. The two genuinely new engineering items are (1) a discrete **Speed-Interruption** detector to
fill the gap between the strict Drawdown and the continuous crawl signal (§6), and (2) the
**parameter-sweep evidence tool** (§7). The biggest *product* item is the Driving-Health
naming/taxonomy layer (§1).

---

## 1. Driving Health Tracker positioning

**Direction:** position the app as a *private Driving Health Tracker* — "your driving health, measured
after every trip" — not a mileage logger or generic trip analyzer. The commercial wedge is recurring,
private insight (monthly health report), not raw trip playback. The trust wedge is "your driving data
is yours."

**Score taxonomy — mostly a relabel of what already exists, plus one new composite:**

| Product-facing name | Backing logic today | Work |
|---|---|---|
| **Safety Health** | Safety/Pace rings, hard-event + speeding logic | relabel |
| **Comfort Health** | Comfort ring, rough-road / bumpy / lateral signals | relabel |
| **Fuel Health** | `FuelEstimator` (L/100km, % vs avg, OEM line) | relabel |
| **Driver Load / Driving Stress** | Stress v2 + DriverLoad (ACWR/Banister-based, already disclaimered) | keep |
| **Overall Driving Health** | — | **new composite roll-up** of the above |

The only *new analytics* is the Overall composite — a weighted roll-up of existing pillars, not a new
sensor model. Everything else is naming, onboarding, and dashboard language.

**Information architecture (aligns with the `:ui-next` module already in flight):** Drive / Trips /
Health(Reports) + a Settings/Privacy area.

**Language rules (copy guardrails):**
- Use "estimated / approximate / trend / indicator," never "measured" for fuel/stress.
- Use "patterns to review," never "bad / unsafe / high-risk driver."
- Never "risk score," "driver risk profile," or "insurer-grade." (Code already leans this way — make
  it a written rule.)

### 1.1 Analytical organizing principle — Style / Demand / Efficiency (science-backed)

> Added 2026-06-30 (owner question: "is there a science-backed way to consolidate Safety / Comfort /
> Pace / Stress / Fuel?"). This is the **analytical backbone under the §1 product names** — it does not
> rename the user-facing pillars; it says how they relate and where a naive roll-up goes wrong.
> ADVISORY, not implemented.

**The core finding — five scores are ~2 driver axes + 1 outcome, not five dimensions.** Several read the
*same* physical signal:
- **Safety and Comfort are one accelerometer signal at two magnitudes** — Safety = the rare large spikes
  (harsh brake/corner = a *surrogate safety measure* in naturalistic-driving research); Comfort = the
  sustained ripple (frequency-weighted RMS acceleration + jerk = the ISO 2631 ride-comfort construct).
  Tail vs. body of one distribution.
- **Pace** straddles aggression (hard accel) and efficiency (speed/cruising).
- **Stress** is mostly about the *road*, not the driver. **Fuel** is an *outcome*, not a behaviour.

**The one move that matters: separate STYLE from DEMAND.** The canonical review (Sagberg et al. 2015,
*A Review of Research on Driving Styles and Road Safety*) separates driving **style** (habitual,
voluntary — what you do) from **skill** and from **situational/context** factors (what the road demands).
Single-number telematics scores fail because they blend these. **Our own trip-1189 finding is exactly
that failure**: a stressful stop-and-go crawl scored 18/Calm under Stress v1 because the score saw only
the (smooth) inputs and was blind to the (high) demand — Stress v2's fix (now 78) was effectively *adding
the demand axis* via `StopAndGo`.

**Proposed consolidation (5 → 3, each independently defensible):**

| Analytical pillar | Absorbs (today's scores) | What it is | Evidence base |
|---|---|---|---|
| **Smoothness (Style axis)** — *driver-controlled* | Safety + Comfort | accel/brake/corner magnitude + jerk | harsh-event rate = surrogate safety measure (naturalistic driving); ISO 2631 freq-weighted RMS accel + jerk = ride comfort |
| **Demand / Load (Context axis)** — *road-imposed* | Stress + Driver-Load | stop-and-go density, speed variance, congestion, time-of-day | task-demand density; **partly shipped** as `StopAndGo.kt` (crawl fraction / below-limit load / longest-no-break) |
| **Efficiency (Outcome)** | Fuel + Pace | energy / $ per distance | RPA / PKE / VSP — validated driving-dynamics & emissions metrics |

**Reconciliation with the §1 taxonomy + a guardrail on the Overall composite.** The §1 product names
(Safety / Comfort / Fuel Health, Driver Load / Stress) **stay** — present Safety and Comfort as two
*views into* Smoothness. But the proposed **"Overall Driving Health" composite needs a guardrail: do NOT
average a style score with a demand score into one number** — that re-introduces the 1189 confound (a
hard-but-smoothly-driven commute reads "bad," an easy-but-jerky cruise "good"). Instead **condition style
on demand**: report "smooth *for how demanding this drive was*" (style normalized within a demand band),
keeping Demand as its own honest axis rather than a penalty folded into the headline.

**Evidence-based metric menu + what our data supports** (1 Hz GPS speed + ~50 Hz accel + stored aggregates):

| Metric | Captures | Gist | Feasible here? |
|---|---|---|---|
| **RPA** (Relative Positive Acceleration) | aggression + eco | (1/d)·Σ v·a⁺·Δt | ✅ |
| **PKE** (Positive Kinetic Energy) | aggression + eco | Σ(v_f²−v_i²)/d over accel phases | ✅ |
| **VSP** (Jiménez light-duty) | fuel/emissions proxy | v(1.1a+0.132)+0.000302v³ | ⚠️ grade=0 approx (add grade later via barometer) |
| **RMS jerk** (da/dt) | smoothness/comfort | RMS of accel derivative | ✅ |
| **Freq-weighted RMS accel** (ISO 2631 W_d, 0.5–10 Hz) | comfort | band-pass + weight, RMS | ⚠️ phone-mount noise — directional, not lab-grade |
| **Harsh-event rate** /h or /100 km | surrogate safety | count of decel ≥ 3.0 m/s² | ✅ already computed |
| **Stop-and-go density** | demand/load | stop→go cycles per km / % time crawling | ✅ partly in `StopAndGo` |
| **Speed CV** | congestion/demand | σ(v)/μ(v) | ✅ |

RPA/PKE/VSP are one family (energy/power per distance) → one engine computation feeds both Efficiency and
the intensity term of Smoothness.

**The "driver load" science specifically — two different sciences, do NOT conflate:**
- **Human-factors *workload*** (NASA-TLX; Detection Response Task / ISO 17488; Yerkes-Dodson) = *cognitive*
  demand. **Not measurable** from phone accel+GPS (needs glances / HR / reaction time) — never label a
  sensor-derived metric "mental load."
- **Sports-science *training load*** (session-RPE, ACWR) = exposure × intensity ("dose") — what the current
  `DriverLoad` borrows. The dose/leaky-integrator idea is sound. **But the ACWR *ratio* is statistically
  discredited**: the acute numerator is mathematically coupled to the chronic denominator, manufacturing
  spurious correlations (Impellizzeri et al. 2020; Lolli et al.). **Recommendation:** keep the dose load,
  **drop the coupled ratio** — express acute load as a **z-score / EWMA against the person's own baseline**.
  Bonus: this dissolves the awkward "needs ≥14 days before the overload chip shows" gate (a personal z-score
  degrades gracefully instead of being suppressed).

What we *can* defensibly call "driver load" = **environmental task demand** (stop-and-go density + speed
variance + harsh-event density + congestion) — the same Demand axis above.

**References:** Sagberg et al. 2015 (driving-styles review); ISO 2631-1 (ride comfort); Jiménez-Palacios
(VSP, MIT); RPA/PKE (real-world emissions / driving dynamics); Impellizzeri et al. 2020 + Lolli et al.
(ACWR pitfalls). Source URLs in the 2026-06-30 chat analysis that produced this section.

**Status / sequencing.** Direction only — no code. Fits the existing discipline: the metric menu is
pure / computable from stored tracks → **validate by DB-replay / the §7 parameter-sweep before changing
any owner-calibrated score**. Belongs to the analytics-validation + `:ui-next` presentation tracks; see
`ROADMAP_NEW.md` → "Metric consolidation." The **fuller product + UI + phased-engineering spec** (three-card
trip readout, the 2×2 grid, conditional headline, free/premium split, and a `Rev CX` UI-only first-rev ticket)
lives in [DRIVING_INTELLIGENCE_SCORING.md](DRIVING_INTELLIGENCE_SCORING.md) — the scoring source-of-truth.

---

## 2. Privacy / local-first / data-egress truth table

### 2.1 Egress truth table (verified)

| Path | Endpoint | What leaves the device | Gating | Default |
|---|---|---|---|---|
| Speed limits | OSM Overpass (3 mirrors) | route bounding boxes / coords | runs on **every** trip finalize, fail-soft, 30-day tile cache ([RecordingService.kt:336](core-engine/src/main/java/com/cartrip/analyzer/record/RecordingService.kt)) | **On (automatic)** |
| Live ETA / traffic time | Google Routes | trip start/end coords | on finalize **if** a Maps key is configured ([RecordingService.kt:500-514](core-engine/src/main/java/com/cartrip/analyzer/record/RecordingService.kt)) | **On if key present** |
| Place naming | Google Places (New) | lat/lon | `PlacesPrefs.enabled()` ([PlacesPrefs.kt](core-engine/src/main/java/com/cartrip/analyzer/cloud/PlacesPrefs.kt)) | **Off** |
| Sheets sync | Google Sheets v4 | full trip rows (export schema) | `CloudPrefs.autoSync` **and** signed-in Google account ([TripSync.kt:43](core-engine/src/main/java/com/cartrip/analyzer/cloud/TripSync.kt)) | Off until sign-in, **then auto** |
| Gas price | ontario.ca public CSV | nothing personal (GET of public file) | automatic ([GasPrice.kt:22](core-engine/src/main/java/com/cartrip/analyzer/cloud/GasPrice.kt)) | On (no PII) |
| Map tiles | Google Maps SDK | map-view coordinates | when a map is viewed | On view |
| Analytics / crash | — | — | none present in either build file | **None** |

**The honesty gap (as discovered) — now addressed, see §2.3:** storage is local-first, but at discovery
**OSM Overpass speed-limit lookup and Google Routes ETA fired automatically** (no explicit opt-in) and
**Sheets auto-sync defaulted on once signed in**. As of 2026-06-30 the two lookups are gated behind the
disclosed `ConnectedFeaturesPrefs` master toggle (default on) and `autoSync` defaults off — so the table
rows above describe the *pre-fix* behavior; current behavior is in §2.3. (Needs on-device verification.)

### 2.2 Accurate privacy claims (and forbidden claims)

**Defensible today:**
- Trip history is stored on-device (Room); no developer-run central driving-history server.
- No third-party analytics or crash-reporting SDK.
- We don't sell driving data. (Backed by: no backend, no ad/analytics SDK — but get legal review
  before publishing, and keep it true when the planned monetization backend lands.)
- No insurance scoring.

**Owner's suggested framing (endorsed as accurate):**
> "Your trip history is stored on this device. We do not run a central driving-history server or sell
> your driving data. Some optional or connected features may look up speed limits, traffic-time
> estimates, place names, fuel prices, or sync data to your own Google account when enabled."

**Must NOT claim:**
- "We collect no data" / "no data ever leaves your phone" — false while Overpass/Routes can run
  automatically and Sheets/Places can be enabled.
- "Unlike other apps, we don't sell your data" — unprovable, legal/credibility risk.

**Forward risk:** HANDOFF §12 plans to "replace Sheets with a real account/backend storing only
compact summaries." The moment that lands, the app is no longer purely local-first — the policy must
already be worded for an opt-in cloud (the framing above accommodates it). Keep the "no analytics SDK"
and "no selling" claims true through that transition or revise them.

### 2.3 P0 connected-features decision — DECIDED + IMPLEMENTED (2026-06-30)

**Owner decision (2026-06-30):** connected enrichment = **opt-out + disclosed** (a product strength, not
a legal apology); Sheets auto-sync = **opt-in (default off)**, with Sheets directionally demoted to
legacy/export. Local-first by *architecture* (core recording/analysis/scoring/history always on-device),
not "offline-only."

| Feature | Decision | Implemented |
|---|---|---|
| OSM speed-limit lookup | **on by default, disclosed**, behind a master "Connected features" toggle | ✅ gated by `ConnectedFeaturesPrefs` at [RecordingService.kt:336](core-engine/src/main/java/com/cartrip/analyzer/record/RecordingService.kt) |
| Google Routes ETA | **on by default**, same master toggle | ✅ guard at top of `fetchLiveEta` ([RecordingService.kt:500](core-engine/src/main/java/com/cartrip/analyzer/record/RecordingService.kt)) |
| Sheets auto-sync | **opt-in, default off** | ✅ `CloudPrefs.autoSync` default flipped to `false` ([CloudPrefs.kt:10](core-engine/src/main/java/com/cartrip/analyzer/cloud/CloudPrefs.kt)) |
| Places naming | leave off | ✅ unchanged |

**Implemented (2026-06-30):** new master switch `cloud/ConnectedFeaturesPrefs.kt` (default **on**); both
auto-firing lookups gated by it; `autoSync` default flipped to off; a user-facing **Connected features**
toggle + disclosure copy added to the Home Options sheet, and the Sheets connect copy corrected ("syncing
stays off until you turn it on"). Caching unchanged (Overpass already has a 30-day tile cache; Routes
fires once per trip-end). **Needs an on-device build to verify** the Compose toggle renders + the gates
behave end-to-end. Future: fold this into the proper Settings → Privacy & Data area (Settings redesign
in [ROADMAP_NEW.md](ROADMAP_NEW.md)).

---

## 3. What already exists — do NOT rebuild

| Advisory asked for | Already in repo |
|---|---|
| Local-first storage, no central DB | ✅ Room only; no backend |
| No analytics / crash SDK | ✅ verified both build files |
| Raw-sample retention controls | ✅ `purgeExpiredRawSamples` + `RAW_SENSOR_RETENTION_MS` ([RecordingService.kt:517](core-engine/src/main/java/com/cartrip/analyzer/record/RecordingService.kt)); [ExportRetention.kt](core-engine/src/main/java/com/cartrip/analyzer/export/ExportRetention.kt) |
| Sustained-crawl / congestion signals | ✅ [StopAndGo.kt](core-engine/src/main/java/com/cartrip/analyzer/analysis/StopAndGo.kt) |
| Driver Load / Stress (non-clinical) | ✅ Stress v2 + DriverLoad (Rev CW), medical disclaimer in UI |
| Premium entitlement abstraction | ✅ [Entitlements.kt](core-engine/src/main/java/com/cartrip/engine/premium/Entitlements.kt) |
| AI export without raw GPS | ✅ [AiInsightsExport.kt](app/src/main/java/com/cartrip/analyzer/ui/AiInsightsExport.kt) |
| Offline re-analysis for detector validation | ✅ `reanalyzeTrip` ([TripFinalizer.kt:107](core-engine/src/main/java/com/cartrip/analyzer/data/TripFinalizer.kt)) — the basis for the sweep |
| Encryption-at-rest, Data Safety, bg-location | ⏳ planned (CO / CR) — not done |

---

## 4. What needs to change before public release (P0 blockers)

These create legal/policy/credibility risk if shipped wrong:

- **P0-1 Egress honesty** — ✅ **DONE in code (2026-06-30, needs device verify)**: Overpass + Routes
  gated behind the disclosed `ConnectedFeaturesPrefs` master toggle (§2.3). The single most important
  item; the positioning depends on it being true.
- **P0-2 `autoSync` default** — ✅ **DONE in code (2026-06-30, needs device verify)**: flipped to opt-in
  (default off), §2.3.
- **P0-3 Privacy policy + Play Data Safety form** matching the §2.1 table. Legal review required before
  any "we don't sell data" statement.
- **P0-4 Background-location compliance** (CR) — foreground-only default + prominent disclosure if
  "Allow all the time" is ever requested.
- **P0-5 "Delete all my data" + "Export my data"** reachable from one screen. Export largely exists;
  deletion needs a clear, single surface (Data Safety effectively requires it).

---

## 5. P0 / P1 / P2 roadmap implications

**P0 — release blockers:** P0-1…P0-5 above.

**P1 — pre-release / paid-beta quality:**
- P1-1 Driving-Health positioning layer (name, onboarding, dashboard copy, score taxonomy — §1).
- P1-2 IA simplification to Drive / Trips / Health (aligns with `:ui-next`).
- P1-3 Developer-mode gating of raw-GNSS / sample loaders / debug exports ([DebugScreen.kt](app/src/main/java/com/cartrip/analyzer/ui/DebugScreen.kt)).
- P1-4 Speed-Interruption detector + parameter-sweep tool (§6, §7) — **sweep/report first**.
- P1-5 "Your Data" privacy dashboard (surface what's stored / what can leave / retention / Google
  disconnect). Trust-as-a-feature.
- P1-6 SQLCipher at-rest encryption (CO) — strong trust headline; not a hard Play blocker → P1.
- P1-7 Entitlement → Play Billing wiring for the freemium split.

**P2 — future enhancements:** monthly Driving-Health report (premium hook), route comparison,
recurring trouble-spot alerts, optional cloud backup, multi-vehicle, find-my-car, anonymized
aggregates (only with a real consent design).

---

## 6. Drawdowns / Speed Interruptions — technical recommendation

**Verified current detector** ([Drawdowns.kt](core-engine/src/main/java/com/cartrip/analyzer/analysis/Drawdowns.kt)) —
the advisory's description is accurate:
`CRUISE_MIN_KMH=60`, `DROP_FRACTION=0.5` (trough ≤ 50% of cruise), `RECOVERY_FRACTION=0.75`,
`RECOVERY_WINDOW_S=150`, `MIN_CRUISE_S=4`, `EDGE_TRIM_S=5`; severity = Σ(km/h lost)²; retrigger
advances past the recovery point; **no time-to-trough term; no speed-limit awareness** (limits aren't
fetched yet at first finalize).

**Recommendation — keep strict, add a middle band, reuse crawl:**
1. **Keep `Drawdowns` unchanged** as the strict **"Major Slowdown"** baseline. It's persisted
   (`drawdownCount`/`drawdownSeverity`), feeds Stress v2, and runs at record time before limits exist.
   Do not disturb it.
2. **Add a new pure, configurable `SpeedInterruptions` detector** for the missing middle:
   `MAJOR_FORCED_SLOWDOWN`, `TRAFFIC_WAVE`, `URBAN_INTERRUPTION`.
3. **Reuse `StopAndGo` for `SUSTAINED_CRAWL`** — do not reinvent it.
4. **Rename the confusing param:** use `minLossFraction` with `dropFloor = cruise * (1 - minLossFraction)`
   (the advisory is right that `DROP_FRACTION=0.5` reads ambiguously).
5. **Speed-limit awareness:** `referenceSpeedKmh = min(observedCruise, speedLimit * 1.05)` when a limit
   is known, else `observedCruise` — never rewards speeding; degrades gracefully when the limit is
   unknown (`speedLimitKmh = 0`).
6. **Severity** extends Σ(Δkm/h)² with multipliers (abruptness from time-to-trough, duration,
   expected-speed shortfall, recovery confidence) — **only wire in after calibration**.
7. **Retrigger:** require a short re-cruise (3–6 s) + cooldown (5–10 s); merge troughs separated by a
   few seconds — to avoid counting 1 Hz GPS oscillation as multiple waves.

**Sequencing constraint (grounded in code):** at first finalize, speed limits are **not yet fetched** —
Overpass runs *after* finalize ([RecordingService.kt:336](core-engine/src/main/java/com/cartrip/analyzer/record/RecordingService.kt)),
so `annotatedPoints` ([TripFinalizer.kt:82](core-engine/src/main/java/com/cartrip/analyzer/data/TripFinalizer.kt))
only carry limits on a **re-analysis** (`preserveSpeedLimits` + `reanalyzeTrip`). A limit-aware detector
must run after the limit fetch, or rely on the re-analyze path. Persisting richer typed events implies a
**schema migration** (currently v22; migration-test foundation exists; needs an emulator per the Rev CP
note).

**Do NOT silently change Stress v2 / DriverLoad inputs** — both are owner-calibrated against real trips
(1187 calm / 1189 crawl). Recalibrate explicitly after the sweep.

---

## 7. Parameter-sweep plan (evidence first; no production change)

Matches the established DB-replay discipline and the "comparison before change" instruction.

1. **Data source:** the persisted **1 Hz analysis track** (`AnalysisPointEntity`: `tMs`, `speedKmh`,
   `speedLimitKmh`) — **not** raw samples, so the sweep survives the retention purge. This is exactly
   what `Drawdowns` / `StopAndGo` consume.
2. **Harness:** detectors are pure, so the sweep runs as an off-device JVM artifact over tracks
   exported to CSV. Lives in **test sources** (`core-engine/src/test/.../analysis/`) so it cannot ship
   or be wired into production by accident. Productionizing later = move the candidate detector to
   `main/` + wire it in (a separate, explicit step).
3. **Configs (the advisory's five):** Current Strict / Balanced / Highway Wave / Urban Wave / Sensitive.
4. **Per trip × config output (markdown + CSV):** major-slowdown count, traffic-wave count,
   sustained-crawl seconds, slowdown-burden score, and the largest event's
   cruise/trough/recovery/time-to-trough — the advisory's exact column set.
5. **Trip set:** representative shapes (calm, highway compression, urban arterial, stop-and-go) **plus**
   the known narrated field-test trips (1187 calm, 1189 crawl, 845/847) as ground-truth anchors.
   ⚠️ The field-test trips live only in the on-device DB — they must be **exported once** (CSV of
   `AnalysisPointEntity`) to be included. Until then the harness runs on synthetic representative
   tracks that match the advisory's example shapes, to prove the method.
6. **Decision flow:** review overlays vs intuition → keep strict metric for "Major Slowdowns" → adopt a
   sensitive band for "Traffic Waves" → keep `StopAndGo` for crawl → **then** recalibrate
   DriverLoad/Stress.

**Deliverable of this step is a report, not a threshold change.**

### 7.1 Run status & first findings (synthetic trips, 2026-06-30)

Built + run: harness at `core-engine/src/test/.../analysis/SpeedInterruptionSweep.kt` (pure, isolated in
test sources, includes faithful ports of the live `Drawdowns`/`StopAndGo` as the baseline). Report:
[reports/speed_interruption_sweep.md](reports/speed_interruption_sweep.md) (+ `.csv`). Ran on 8
representative synthetic trips matching the advisory's example shapes (no real trips ingested yet).

**Real-trip export path wired (2026-06-30).** Diagnostics → **Export trip tracks (CSV)** writes
`all_tracks.csv` (`tripId,tMs,speedKmh,speedLimitKmh`) from `AnalysisPointEntity` via the new pure
`export/SweepTrackExport.kt` (read-only, debug-only, no production path touched); the harness's
`loadRealTrips()` ingests it. Combined-file ingestion was verified end-to-end on a fabricated 2-trip
export (real trips flow through the same detectors, identical results to their synthetic twins). The
on-device button + share sheet still need an Android build to confirm. Unit test: `SweepTrackExportTest`.

Headline findings (to be **confirmed on real trips** before any change):
- **Production fired 0 discrete events on all 8 trips** — incl. textbook urban / crawl / stop-and-go —
  confirming the under-reporting hypothesis. (The continuous `StopAndGo` crawl signal still fires on
  the crawl/stop-and-go trips, which is why Stress v2 isn't fully blind.)
- **The cruise-EXIT rule is the dominant lever, not the loss-fraction threshold.** The first run
  surfaced a bug: production's absolute cruise floor (`cruiseMin*0.85`) *absorbs* a shallow fast-road
  dip (110→70) into the cruise, so it's never evaluated. Switching the candidate to a **relative hold**
  (cruise ends on a >10% dip from its running peak) is what makes highway compression detectable at all.
- **`cruiseMin` gates city capture:** ≥55 km/h misses 50 km/h arterials entirely (Balanced caught
  nothing on stop-and-go); the urban band needs `cruiseMin ≤ 45`.
- **`minLossFraction` changes recall *and* event segmentation** (0.30 split one wave into two on the
  moderate-waves trip; 0.38 merged them).
- **Recovery window matters only for long crawls**, which `StopAndGo` already covers — so the discrete
  detector doesn't need a large window; keep crawl as the crawl tool.
- **No false positives on the two calm controls** for any config — encouraging, but synthetic; real GPS
  noise will need smoothing / min-dwell before trusting low-loss thresholds.

Emerging recommendation (validate on real trips): keep strict `Drawdowns` = **Major Slowdowns**; add a
**Traffic-Wave / Speed-Interruption** band at `minLossFraction ≈ 0.30–0.35` with the **relative
cruise-exit** and `cruiseMin ≈ 45–50` (spans highway + arterial); keep `StopAndGo` for **Sustained
Crawl**. This is the advisory's three-way split, now with evidence behind the parameter ranges.

---

## 8. Risks, assumptions, and calibration notes

- **GPS speed noise at 1 Hz** will fight low-loss (30–40%) wave thresholds — expect false waves without
  smoothing + min-dwell + cooldown (consistent with prior GPS-noise findings). Validate on real tracks,
  not only synthetic.
- **Speed-limit coverage is partial** — Overpass is fail-soft; many points will have `speedLimitKmh = 0`
  (unknown). The Urban band depends most on limits, so expected-speed logic must degrade to observed
  cruise.
- **Schema migration** required to persist typed events (v22; migration tests need an emulator).
- **Calibration coupling** — changing detector inputs moves owner-calibrated Stress/DriverLoad scores;
  recalibrate, don't silently shift.
- **Record-time guarantee** — keep a limit-free baseline (`Drawdowns`) that runs before limits exist.
- **Privacy claims must be re-verified at release** against §2.1 — especially that no analytics/crash
  SDK has crept in, and that the planned monetization backend is opt-in before any "local-first" copy
  ships.
- **Legal review** before publishing "we don't sell data" / "no insurance scoring."
- **Assumption:** the field-test trips' raw/analysis points still exist on-device and can be exported.
  If raw was purged, the 1 Hz `AnalysisPointEntity` track usually persists longer and is sufficient for
  the sweep (it only needs speed + limit + time).

---

## 9. Recommended sequencing / next steps

1. **Make the §2.3 connected-features + `autoSync` product decisions** (opt-in vs opt-out vs disclosed)
   — these gate the honesty of the whole positioning and are tiny once decided.
2. **Build the parameter-sweep report** (§7) — self-contained, evidence-only, no production change.
   *(DONE on synthetic trips + export path wired — see §7.1 + [reports/speed_interruption_sweep.md](reports/speed_interruption_sweep.md).
   Next for this item: on-device, run Diagnostics → Export trip tracks (CSV), then re-run the harness on
   `all_tracks.csv` (esp. field-test trips 1187, 1189, 845, 847) before drawing production conclusions.)*
3. **Draft the Driving-Health naming/taxonomy** (§1) — cheap, unblocks UI/onboarding copy.

Defer until after the sweep: any threshold change, any Stress/DriverLoad recalibration, and any new
persisted event schema. Defer until the §2.3 decision: the connected-features toggles and the
`autoSync` default flip.
