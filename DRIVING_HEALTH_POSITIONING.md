# Driving Health Tracker — Positioning & Copy (proposals)

> **Status: ADVISORY / copy proposals, NOT final UI strings.** Companion to
> [ADVISORY_ASSESSMENT.md](ADVISORY_ASSESSMENT.md) (§1 positioning, §1.1 Style/Demand/Efficiency
> backbone, §2 privacy egress truth table). This doc turns that direction into concrete, paste-ready
> text for naming, onboarding, dashboard labels, score explanations, privacy screens, and store copy.
> Privacy strings must pass **legal review** and be **re-verified against the code** before shipping.
> Nothing here is implemented.

---

## 1. Name & taglines

**Working product name:** *Driving Health* (or *Driving Health Tracker*). Positions against fitness
trackers, not mileage loggers.

- **Primary tagline:** *Your driving health, measured after every trip.*
- **Privacy tagline:** *Driving insights without driving surveillance.*
- **Data-ownership line:** *Your trips stay on your phone by default.*

Avoid: "trip logger," "mileage tracker," "driving score," "risk score" — all either crowded or
insurance-coded.

---

## 2. Score taxonomy — current → product names

Aligns with the §1.1 backbone: **Safety & Comfort are two views into one Smoothness (Style) axis;
Demand is its own axis; Efficiency is an outcome.** User-facing names stay friendly; the analytical
grouping sits underneath.

| Today (code) | Product name | Axis (§1.1) | Plain-English meaning |
|---|---|---|---|
| Safety ring | **Safety Health** | Style (rare large events) | hard braking / cornering / speeding exposure |
| Comfort ring | **Comfort Health** | Style (sustained ripple) | how smooth the ride was — bumps, jerk, sharp turns |
| Pace ring | folds into Safety Health + Efficiency | Style + Efficiency | speeding exposure → Safety; cruising/flow → Efficiency |
| Drive Stress (`StressScore`) | **Driving Stress** | Demand | how demanding the drive was (congestion, crawl, no-break) |
| Driver Load (`DriverLoad`) | **Driver Load / Readiness** | Demand (cumulative) | recent driving load + recovery |
| Fuel (`FuelEstimator`) | **Fuel Health** | Efficiency (outcome) | estimated fuel use / cost & trend |
| — (new) | **Overall Driving Health** | composite | one headline number — see §3 |

---

## 3. "Overall Driving Health" composite — design (honoring the §1.1 guardrail)

**The guardrail (from the owner's §1.1):** *do NOT average a Style score with a Demand score into one
number.* That re-introduces the trip-1189 confound (a hard-but-smoothly-driven commute reads "bad"; an
easy-but-jerky cruise reads "good"). Instead **condition Style on Demand.**

**Proposed shape (to validate on real trips, not implement yet):**
- Headline = **Smoothness, expressed relative to how demanding the drive was.** e.g. compute a raw
  Style score, then report it within a Demand band ("smooth *for a heavy stop-and-go commute*").
- **Demand stays its own visible axis** (Driving Stress / Driver Load), never folded into the headline
  as a penalty.
- **Efficiency (Fuel) stays its own outcome metric**, shown alongside, not averaged in.
- Net: the dashboard shows **one conditioned headline + three honest axes** (Style-vs-demand, Demand,
  Efficiency), not a single blended average.

**Plain-English presentation example:**
> **Overall: Smooth drive — for a busy one.**
> You drove smoothly given the heavy traffic. Demand was high (stop-and-go); fuel use was about average.

---

## 4. Onboarding (4 screens, copy draft)

**Screen 1 — What it is**
> **Driving Health**
> Like a fitness tracker for your driving. After each trip you get simple scores and trends for
> smoothness, comfort, speeding, traffic stress, and fuel — built only from your own drives.

**Screen 2 — Your data stays yours**
> **Local-first by default**
> Your trip history is stored on this phone. We don't run a server that collects your driving history,
> and we don't use ad or analytics trackers. Some features can look things up online (speed limits,
> traffic times) — you control those in Settings.

**Screen 3 — Why permissions**
> **Location, only while you drive**
> Location records your route and speed during a trip. Background location is optional — it's only for
> hands-free auto-recording, and you can turn it off and record manually.

**Screen 4 — Honest about estimates**
> **Estimates, not verdicts**
> Fuel cost, stress, and driver load are *estimates* from your trip data — useful for spotting trends,
> not exact measurements. We show patterns to review, never "good/bad driver" labels.

---

## 5. Score explanation cards (plain-English, tap-to-expand)

Each score gets: one-line meaning + main contributors + a limitation. Examples:

- **Smoothness / Comfort Health** — "How smooth the ride felt." *Driven by:* braking & acceleration
  evenness, cornering, road bumps. *Note:* rough roads can lower this even when you drove well.
- **Safety Health** — "Patterns worth reviewing." *Driven by:* hard braking/cornering, speeding
  exposure. *Note:* a few sharp events on one trip is normal; watch the trend.
- **Driving Stress** — "How demanding this drive was." *Driven by:* stop-and-go, congestion vs. the
  road's normal speed, time without a break. *Note:* this is about the road/traffic, not your skill.
- **Driver Load / Readiness** — "Recent driving load." *Driven by:* how much demanding driving you've
  done lately, decaying with rest. *Note:* a wellness indicator from driving only — **not** a
  fitness-to-drive or medical assessment.
- **Fuel Health** — "Estimated fuel use & cost." *Driven by:* speed, acceleration, idling, your vehicle
  profile. *Note:* an estimate unless calibrated to real fill-ups / OBD.

**Coaching tone — prefer / avoid:**
- ✅ "This trip had higher driver load than usual." / "Most hard braking was near these spots." /
  "Your smoothness improved this week." / "This route was slower but smoother."
- ❌ "You are a bad/unsafe/high-risk driver." / "Insurance risk score."

---

## 6. Privacy & "Your Data" screen (grounded in the verified egress table)

> Copy below matches [ADVISORY_ASSESSMENT.md](ADVISORY_ASSESSMENT.md) §2.1 as of 2026-06-30. **Re-verify
> against the code and pass legal review before shipping.** Do not ship "no data ever leaves your phone."

**Top statement**
> **Your driving data is yours.** Your trip history is stored on this device. We do not run a central
> driving-history server, we don't sell your driving data, and there are no ad or analytics trackers in
> this app.

**"What's stored on this phone"**
> Trips, route & speed samples, motion samples, scores, and your vehicle/fuel settings — all local.

**"What can leave this phone (and when)"**
> - **Speed limits** — looked up from OpenStreetMap to score speeding (route area only).
> - **Traffic-time estimates** — looked up from Google for the trip's start/end.
> - **Place names** — optional; off unless you turn it on.
> - **Fuel prices** — a public price file (no personal data).
> - **Google Sheets sync** — optional; only if you connect your own Google account.
>
> Manage these under **Connected features** — on by default, one switch to turn off. Trip recording and
> analysis stay fully on-device either way; turning it off just reduces speed-limit / traffic extras.
> Google Sheets sync is separate and stays **off until you turn it on**. *(Decided 2026-06-30; see
> ADVISORY_ASSESSMENT §2.3.)*

**Controls (the screen should offer)**
> Export my data · Delete a trip · Delete all driving history · Clear raw sensor samples · Disconnect
> Google · Manage connected features.

**No-insurance line (only if true in policy + implementation)**
> This app is for personal driving insight, not insurance scoring. We don't sell your driving history to
> insurers or advertisers.

---

## 7. App-store description (draft)

> **A private driving health tracker.** Understand every trip: smoothness, comfort, speeding, hard
> braking and acceleration, cornering, road roughness, traffic stress, and fuel cost — with simple
> scores and trends built only from your own drives.
>
> **Local-first by default.** Your trip history is stored on your phone. No ad or analytics trackers, no
> central driving-history server, and we don't sell your data. Optional connected features (speed
> limits, traffic times, place names, Google Sheets sync) are yours to turn on.
>
> **Go premium** for monthly driving-health reports, fuel-cost insights, driver-load trends, route
> comparisons, recurring trouble spots, and advanced exports.

---

## 8. Open decisions that gate this copy

1. **Final product name** (Driving Health vs Driving Health Tracker vs other).
2. ~~Connected-features defaults~~ — **DECIDED 2026-06-30:** opt-out + disclosed (on by default, master
   toggle). Implemented; see ADVISORY §2.3.
3. ~~`autoSync` default~~ — **DECIDED 2026-06-30:** opt-in (default off); Sheets demoted to legacy/export.
   Implemented.
4. **Whether the "we don't sell / no insurance" lines are policy-committed** (legal review).
5. **Overall Driving Health composite** — validate the §3 conditioning approach on real trips (pairs
   with the speed-interruption sweep + a future Style/Demand metrics check) before it becomes a headline.
