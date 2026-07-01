# Driving Intelligence Scoring Strategy

_Last updated: 2026-06-30_

> **Status: SOURCE-OF-TRUTH for the scoring product direction (owner-supplied 2026-06-30), DIRECTION not
> implementation.** This is the fuller product/engineering version of the analytical model summarized in
> [ADVISORY_ASSESSMENT.md](ADVISORY_ASSESSMENT.md) §1.1; the roadmap entry is `ROADMAP_NEW.md` → "Metric
> consolidation" (Rev CX candidate). **Verified against code 2026-06-30 (branch `ux-premium-modular-v1`):**
> the §4 "already supported" claims are accurate — `crawlFraction` / `belowLimitLoad` / `longestNoBreakS`
> are real persisted `trips` columns (schema v22, [Entities.kt](core-engine/src/main/java/com/cartrip/analyzer/data/Entities.kt):98-100),
> `drawdownCount` / `drawdownSeverity` are v21, `DriverLoad` EWMA is implemented, and `speedCv` / `stopGoDensity`
> are correctly flagged as not-yet-stored. **Phasing rule:** Phase A/B (this doc + UI/model-only
> consolidation, no schema/threshold change) is the actionable part; Phases C–E (new engine aggregates,
> personal-baseline model, premium/backend) are **tabled behind DB-replay + the ADVISORY §7 parameter-sweep**
> before any owner-calibrated Stress/DriverLoad score is touched.

This document turns the current score-taxonomy discussion into a product and engineering roadmap. The goal is to reduce score clutter without losing meaning, make the app easier to explain, and ground the analytics in metrics that can be defended from the data the phone already records.

## 1. Executive summary

The app should move from five partially-overlapping public scores - Safety, Comfort, Pace, Stress, and Fuel - to a clearer three-pillar driving-intelligence model:

| Pillar | User-facing meaning | Absorbs | Primary question answered |
|---|---|---|---|
| **Smoothness** | How controlled and comfortable your driving inputs were | Safety + Comfort + parts of Pace | Did you drive smoothly and avoid sharp inputs? |
| **Demand / Load** | How difficult the drive itself was | Stress + Driver Load | Was this an easy cruise, a stop-and-go grind, or a demanding trip? |
| **Efficiency** | How much fuel, money, and energy the drive used | Fuel + parts of Pace | Did this drive cost more than it needed to? |

This is stronger than five separate scores because it separates **driver style** from **road/context demand** and keeps **fuel/energy** as an outcome. That is the core product idea: the app should not simply judge the driver; it should explain whether the drive was smooth, whether the situation was demanding, and what it cost.

The highest-value positioning is:

> A private driving-intelligence app that shows how you drive, how hard the drive was, and what it costs you - with transparent, evidence-backed metrics instead of a black-box insurance score.

## 2. Why consolidation is needed

The current score family overlaps:

- **Safety** and **Comfort** are mostly different slices of the same motion signal. Safety is the extreme tail of the acceleration distribution: hard braking, hard acceleration, hard cornering, speeding severity. Comfort is the body of the distribution: jerk, vibration, roughness, and repeated smaller motion discomfort.
- **Pace** mixes two ideas: moving efficiently through the route, and driving aggressively. Those should not be fused.
- **Stress** mostly describes the trip environment, not the driver: congestion, stop-and-go, no-break stretches, forced slowdowns, traffic delay.
- **Fuel** is an outcome: a cost/energy result caused by vehicle, route, traffic, weather, speed, and driver inputs.
- **Driver Load** is valuable, but it must be framed carefully. Phone GPS + motion sensors can estimate recent driving exposure and environmental task demand. They cannot directly measure mental workload, fatigue, medical readiness, or cognitive state.

The current system is close to something much stronger: a driving-science-style decomposition of **style**, **demand**, and **outcome**.

## 3. Product model: style vs demand vs outcome

### 3.1 Smoothness = driver style

Smoothness should be the main driver-controllable style score.

It should answer:

- Did I brake, accelerate, corner, and transition smoothly?
- Did I create sharp spikes or high jerk?
- Am I improving over time?
- Which places or situations make my inputs less smooth?

Smoothness should include two subviews, not two separate top-level scores:

| Subview | What it means | Example UI language |
|---|---|---|
| **Safety events** | rare high-severity maneuvers | "2 hard brakes, mostly near Sheppard" |
| **Comfort / control** | sustained smoothness, jerk, roughness, vibration | "Smoother than your 30-day average" |

This retains the meaning of Safety and Comfort while reducing UI clutter. Users can still tap into the old concepts, but the headline becomes one understandable question: **was this drive smooth?**

### 3.2 Demand / Load = the drive context

Demand / Load should describe the external and temporal burden of the drive.

It should answer:

- Was the road situation demanding?
- Was there sustained stop-and-go driving?
- Did the drive have long no-break stretches?
- Was the route unusually congested relative to free-flow?
- Has the user had a heavy recent driving period?

This is the missing axis that fixes the trip-1189 problem: a smooth driver in heavy stop-and-go traffic should not be called "calm" just because they avoided harsh inputs. The correct read is: **high-demand drive, smooth handling**.

Demand / Load should include:

| Signal | Meaning |
|---|---|
| Stop-and-go density | number of crawl/stop/go cycles per km or per hour |
| Crawl fraction | share of moving time below a low-speed threshold |
| Below-limit load | how far below normal/posted speed the route operated |
| Speed coefficient of variation | instability of speed profile, useful for congestion and interrupted flow |
| Longest no-break stretch | sustained driving without a real rest interval |
| Drawdowns / speed interruptions | forced cruise-to-slow-to-recover events |
| Google ETA/free-flow gap | external congestion and delay context where available |
| Recent cumulative load | time-decaying exposure from recent drives |

The user-facing claim should be **driving demand**, **route load**, or **driver load from recent driving**, not "mental stress" or "fitness to drive."

### 3.3 Efficiency = fuel, dollars, and energy outcome

Efficiency should absorb Fuel and the useful parts of Pace.

It should answer:

- How much did this drive cost?
- Was this drive more or less efficient than my normal?
- Which behaviors or route segments likely wasted fuel?
- Would leaving earlier, avoiding stop-and-go, or smoothing acceleration save money?

Efficiency should include:

| Signal | Meaning |
|---|---|
| Estimated L/100 km | direct user-understandable fuel economy |
| Cost per trip / week / month | monetizable, easy-to-understand value |
| Idle/crawl cost | fuel wasted in stop-and-go and long idle periods |
| Relative positive acceleration (RPA) | energy intensity from positive acceleration |
| Positive kinetic energy (PKE) | acceleration energy per distance |
| Vehicle specific power (VSP) | power-demand proxy for fuel/emissions modeling |
| Percent vs personal baseline | "8% better than your average" |
| Percent vs OEM/reference | "near/above/below expected for this vehicle" |

Efficiency should be kept separate from Smoothness because an inefficient drive can be caused by traffic and route demand, not just driver behavior. That separation makes the app feel fairer and more useful.

## 4. Evidence-backed metric menu

The engine should compute transparent intermediate metrics and then roll them into the three pillars. These metrics are more defensible than bespoke black-box scores.

| Metric | Pillar | Formula / implementation gist | Data support |
|---|---|---|---|
| Harsh event rate | Smoothness | count hard brake/accel/corner per hour or per 100 km | already supported |
| Harsh event severity | Smoothness | magnitude-weighted exceedance, e.g. amount above threshold | already supported for some events |
| RMS jerk | Smoothness | RMS of derivative of acceleration | supported if accel sampling is stable |
| Comfort RMS acceleration | Smoothness | band-limited or frequency-weighted acceleration RMS | supported as directional, not lab-grade |
| Rough stretch / bump density | Smoothness + road quality | events or roughness per km | already partly supported |
| Stop-and-go density | Demand / Load | stop-to-go or crawl-to-recovery cycles per km/hour | supported through `StopAndGo` path |
| Crawl fraction | Demand / Load | moving time below low-speed threshold | already stored in schema v22 |
| Below-limit load | Demand / Load | mean shortfall vs posted/expected speed | already stored in schema v22 |
| Longest no-break stretch | Demand / Load | longest moving stretch without rest interval | already stored in schema v22 |
| Speed CV | Demand / Load | standard deviation(speed) / mean(speed) | supported from points; consider aggregate |
| Drawdown count/severity | Demand / Load | cruise-to-slow-to-recover speed interruptions | already stored in schema v21 |
| Recent load EWMA | Demand / Load | exponential decay of stress-weighted driving time | already implemented in `DriverLoad.kt` |
| RPA | Efficiency | `(1 / distance) * sum(v * positive_accel * dt)` | supported from speed/accel |
| PKE | Efficiency | `sum(v_final^2 - v_initial^2) / distance` over acceleration phases | supported from speed trace |
| VSP | Efficiency | approximate power demand from speed, acceleration, road load, and optional grade | supported as approximation; improve with grade later |
| Fuel delta vs baseline | Efficiency | percent above/below personal mean or rolling baseline | already partly implemented |
| Cost impact | Efficiency | fuel estimate * local gas price | already partly implemented |

## 5. Guardrails: what not to overclaim

This model is credible only if the app is honest about what the phone can and cannot measure.

### 5.1 Do not call sensor-derived load "mental workload"

Human-factors workload tools such as NASA-TLX or detection-response-task methods measure perceived/cognitive workload. Phone GPS and motion sensors cannot directly measure mental demand, fatigue, alertness, sleep debt, distraction, eye movement, or reaction time.

Acceptable language:

- "high-demand drive"
- "heavy stop-and-go load"
- "recent driving load is elevated"
- "this was a demanding traffic pattern"

Avoid:

- "your mental load was high"
- "you were fatigued"
- "you were unsafe to drive"
- "medical readiness"

### 5.2 Treat ACWR as advisory, not a hard science claim

The acute:chronic workload ratio idea is useful as a product analogy: recent load compared with normal load. But the ratio itself has known statistical criticisms in sports-science literature, especially numerator/denominator coupling and threshold overinterpretation.

Preferred product direction:

- Keep the **time-decaying load** idea.
- Prefer **personal-baseline z-scores**, percentile bands, or EWMA deviation from normal.
- If an ACWR chip remains, label it as "above your recent norm" and keep it suppressed until enough history exists.
- Do not imply injury-risk prediction or medical fatigue prediction.

### 5.3 Treat ISO-style comfort as directional

The app can borrow the ISO 2631 concept of frequency-weighted acceleration and RMS vibration, but a phone mount is not a calibrated seat-pad sensor. Use language such as "comfort proxy," "ride roughness," and "relative trend," not lab-certification language.

### 5.4 Treat harsh events as surrogate safety, not crash prediction

Hard braking and similar events are useful safety surrogates, especially at aggregate/hotspot level, but an individual trip score should not be sold as a crash-risk probability. Better language:

- "more safety-critical events than usual"
- "this intersection repeatedly produces hard braking"
- "higher-risk pattern worth watching"

## 6. How this adds app value

### 6.1 Clearer daily user experience

Instead of showing five separate numbers, the app can show a three-card trip readout:

1. **Smoothness:** "Good control. 1 hard brake, low jerk."
2. **Demand / Load:** "High-demand drive: 41% crawl time, long no-break stretch."
3. **Efficiency:** "About $4.20 fuel; 9% worse than your average, mostly stop-and-go."

This immediately tells a useful story. A user can understand whether the issue was their behavior, the road/traffic, or the cost.

### 6.2 Fairer coaching

The app should avoid blaming the user for traffic. The most differentiated insight is conditional coaching:

| Situation | Old-style read | Better read |
|---|---|---|
| Smooth highway commute | high score | "Low demand, smooth drive" |
| Stop-and-go crawl, no harsh inputs | confusing "calm" score | "High demand, handled smoothly" |
| Empty road with aggressive inputs | medium stress | "Low demand, rough style" |
| Efficient but uncomfortable drive | ambiguous | "Efficient, but less smooth than normal" |

This is the key differentiator versus generic telematics apps: the app separates **what the driver controlled** from **what the road imposed**.

### 6.3 More actionable insights

The three-pillar model supports better coaching:

- "Your smoothness drops most on left turns at this intersection."
- "Your commute is high demand after 8:15 AM; leaving 12 minutes earlier typically reduces crawl time."
- "Fuel efficiency worsens when crawl fraction exceeds 35%; this route costs about $18/month more in traffic."
- "You handled a high-load drive well: high demand, low harsh-event rate."
- "Your recent driving load is elevated versus your normal week; expect the load score to decay after rest."

These are stronger than generic "drive safer" messages because each insight maps to a concrete signal.

### 6.4 Stronger premium story

The premium tier can be framed as **Driving Intelligence**, not just more charts.

Free tier:

- trip recording
- basic three-pillar summary
- recent history
- map/event review

Premium tier:

- personal baselines and trend intelligence
- monthly driving report
- route/time-of-day demand analysis
- fuel-cost waste analysis
- recurring hotspot detection
- AI coaching from compact summaries
- long-term lifetime history
- cohort/vehicle comparisons if a backend is added

Premium value examples:

- "Your smoothness improved 18% over 3 months."
- "Your Tuesday evening commute is your highest-load pattern."
- "Three locations explain 42% of your hard braking."
- "Stop-and-go traffic likely costs you about $23/month in extra fuel."
- "Compared with similar Tucson drivers, your efficiency is top quartile."

The differentiator is not that the app has a score. The differentiator is that it explains the **why** behind the score and turns it into a habit, route, time, place, and cost story.

## 7. Recommended UI architecture

### 7.1 Rename public score surfaces

Use these public labels:

- **Smoothness** instead of showing Safety and Comfort as peer top-level scores.
- **Demand / Load** instead of Stress as a standalone judgment score.
- **Efficiency** instead of Fuel Economy as a narrow fuel card.

Keep the older terms as drill-downs:

- Smoothness -> Safety events, Comfort/jerk, Roughness
- Demand / Load -> Stop-and-go, Congestion, Drawdowns, Recent load
- Efficiency -> Fuel, Cost, Idling/crawl waste, Acceleration energy

### 7.2 Use a two-axis trip interpretation

Each trip should be classifiable into a 2x2 style/demand grid:

|  | Low demand | High demand |
|---|---|---|
| **Smooth style** | Easy smooth drive | Smooth under pressure |
| **Rough style** | Avoidable harsh inputs | Difficult and rough |

This gives the app a human explanation layer:

- "Smooth under pressure" is a positive coaching moment.
- "Avoidable harsh inputs" is a driver-actionable coaching moment.
- "Difficult and rough" means separate route/traffic advice from driving-style advice.

### 7.3 Make the headline conditional

Avoid one raw composite that blends style and demand. If a headline number is needed, make it conditional:

- **Drive Quality:** smoothness adjusted for demand band.
- **Handled Well:** high if Smoothness is good relative to drives with similar Demand.
- **Driving Health:** only as a dashboard umbrella, not a mathematical sum of all pillars.

Example:

> Drive Quality: 82 - smooth for a high-demand trip.

This is more defensible than averaging Smoothness + Demand + Efficiency.

## 8. Engineering roadmap

### Phase A - Documentation and taxonomy alignment

Status: this document.

- Treat the three-pillar model as the scoring source of truth.
- Update future specs, Claude prompts, and UI copy to use Smoothness / Demand-Load / Efficiency.
- Keep existing Safety / Comfort / Pace / Stress / Fuel metrics as internal or drill-down concepts.

### Phase B - Low-risk UI consolidation

No schema change required.

- Add a three-card trip summary on Trip Detail.
- Re-label Insights sections around the three pillars.
- Add explanatory tooltips: "Style," "Demand," "Outcome."
- Add a 2x2 trip interpretation label: easy smooth, smooth under pressure, avoidable harsh inputs, difficult and rough.
- Keep old scores visible only as expandable details during transition.

### Phase C - Engine metric cleanup

Likely minor schema additions for persisted aggregates.

- Add `speedCv`, `stopGoDensity`, `rpa`, `pke`, and optional `vspApprox` aggregates.
- Reuse existing `crawlFraction`, `belowLimitLoad`, `longestNoBreakS`, `drawdownCount`, and `drawdownSeverity`.
- Define exact per-100 km / per-hour normalization rules.
- Add DB-replay tests using known trips, including the trip-1189 high-demand crawl anchor.

### Phase D - Personal baseline model

- Replace or supplement ACWR with EWMA deviation from personal norm.
- Add z-score or percentile labels: below normal, normal, elevated, unusually high.
- Use enough-history gates that degrade gracefully: show "building baseline" rather than hiding the concept entirely.
- Keep medical/fatigue disclaimer.

### Phase E - Premium intelligence layer

- Monthly report: Smoothness, Demand, Efficiency, trend, best/worst routes, estimated cost waste.
- Route/time pattern analysis: commute windows, recurring high-load segments, high-cost departure times.
- AI coaching: generate plain-language insights from compact local summaries, not raw GPS.
- Optional backend: cohort/vehicle comparisons using only opt-in compact aggregates.

## 9. Suggested implementation ticket

**Rev CX - Three-pillar driving-intelligence taxonomy**

Purpose: consolidate public scoring into Smoothness, Demand / Load, and Efficiency while preserving current metrics as drill-downs.

Scope:

- Add `DrivingPillar`/`DrivingIntelligence` pure model in `analysis/` or `ui/insights` boundary.
- Compute pillar cards from existing trip aggregates first; do not change analyzer thresholds in this rev.
- Trip Detail: show three cards with concise explanations.
- Insights: reorganize sections under the three pillars.
- AI export: add a "Driving intelligence" section with Smoothness / Demand / Efficiency summary.
- Keep Safety/Comfort/Pace/Stress/Fuel labels available in detailed rows for continuity.
- Add copy guardrails: not a medical/fatigue assessment; Demand is context, not blame.

Out of scope for first rev:

- Changing detector thresholds.
- Adding new sensor-processing formulas.
- Replacing `DriverLoad.kt` internals.
- Backend/cohort features.

Acceptance criteria:

- A user can explain a trip in one sentence: style, demand, outcome.
- High-demand/smooth trips do not look like bad driving.
- Low-demand/rough trips are clearly identified as driver-actionable.
- Fuel/cost insights remain separate from safety/style judgments.
- Existing tests still pass; new copy/model tests cover the four 2x2 trip labels.

## 10. Reference anchors for future citations

Use these as the evidence basis in product copy, specs, and investor-style explanations:

- Driving style vs safety literature: driving style is a habitual behavioral construct distinct from skill and from situational factors.
- Naturalistic-driving safety literature: hard braking and related kinematic events are commonly used as high-density surrogate safety measures.
- ISO 2631 / ride-comfort literature: ride comfort is commonly evaluated using vibration/acceleration exposure, especially frequency-weighted RMS acceleration; phone data is a proxy, not a certified measurement.
- Eco-driving / emissions literature: RPA, PKE, and VSP connect speed/acceleration profiles to energy, fuel, and emissions intensity.
- Human-factors workload literature: true mental workload needs subjective ratings, reaction-time tasks, eye/physiology signals, or similar measures; do not infer it directly from phone motion data.
- Training-load literature: EWMA load and baseline deviation are useful analogies; ACWR thresholds and ratios require caution due to known statistical pitfalls.

## 11. Product positioning language

Short version:

> Car Trip Analyzer is a private driving-intelligence app. It does not just score you. It separates how smoothly you drove, how demanding the trip was, and what it cost you, then turns that into practical coaching about routes, timing, habits, fuel, and recurring problem spots.

More direct app-store style:

> See every drive through three clear lenses: Smoothness, Demand, and Efficiency. Learn whether a trip was rough because of your inputs, because traffic made it hard, or because it cost more fuel than usual. Your data stays local-first, and every insight is built from transparent trip signals instead of a black-box insurance score.
