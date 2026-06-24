# Field Test Plan — v2.40 (Rev G/H/I)

Goal: capture rich, well-annotated data tomorrow to validate the detectors and new layers
(auto-stop, peak-G, swerve/bump accuracy, speed-limit cache, GNSS quality).

## Before you drive (2 min)

1. Phone on **2.40 (51)**. Mount it firmly — a stable, fixed orientation is fine (the detector is
   orientation-agnostic, but a loose phone adds noise). Keep it plugged in (recording is power-hungry).
2. Open the app → tap the **bug icon** (top-right, next to ⓘ) → **Diagnostics**. Start a trip, then
   re-open Diagnostics and confirm, while driving the first minute:
   - **Motion**: should read ~**40–50 Hz** (not "NONE").
   - **GPS fixes**: climbing, **GPS signal: OK**.
   - **GNSS sats**: should show **several used / more seen**, **C/N0 ~25–40 dB-Hz**, ideally **L5: yes**.
   - **Sensor restarts: 0** (a number >0 means a stall was auto-recovered — note when).
   If GNSS shows 0 used with open sky, tell me — that means the callback isn't getting a fix.

## Narration (so events can be aligned)

Use a separate **voice recorder app** running for the whole drive (or talk and I'll transcribe).
When you do a test action, **say it with a time anchor** — glance at the car clock and state it, e.g.
*"8:42 and 10 seconds — hard brake now."* A spoken landmark also helps (*"approaching Yonge & Sheppard"*).
The trip's clock is wall-time, so "HH:MM:SS" anchors map cleanly onto the timeline.

Keep each maneuver **isolated** (a few seconds of normal driving before/after) so it's distinguishable.

## Scenarios (do what's safe and legal)

Aim for one trip that hits several of these, plus a couple of separate trips.

| # | Scenario | Narrate | Validates |
|---|----------|---------|-----------|
| 1 | **Hard brake** ×2–3 from ~50 km/h (safe, no tailgater) | "hard brake now" | GPS+fused brake, peak-G |
| 2 | **Hard acceleration** ×2 (firm pull from a stop) | "hard accel now" | accel detection |
| 3 | **Sharp turn at speed** (a real cornering force) | "sharp turn now" | corner detection |
| 4 | **Lane-change / gentle S at 60–90 km/h** (clear road) | "swerve now" | swerve detection at speed |
| 5 | **Parking-lot / tight low-speed turns** | "parking-lot turn" | should NOT be flagged as swerve |
| 6 | **Speed bump or known pothole** (slow, safe) | "bump now" | pothole detection, bump-veto (not a brake) |
| 7 | **Mild speeding**: ~5 km/h over a posted limit briefly | "5 over" | yellow tier |
| 8 | **Clear speeding**: ~12–15 km/h over where safe/legal | "15 over" | red tier |
| 9 | **Urban canyon / underpass / tunnel / parking garage** | "urban canyon" / "tunnel" | GNSS weak, data-quality downgrade |
| 10 | **Open highway, clear sky** | "open sky" | GNSS strong, L5 |
| 11 | **Auto-stop**: at the end, park and **leave the app recording**; don't touch it for ~7 min | "parked, leaving it" | retrospective stop time = when you actually stopped, not 7 min later |
| 12 | **Repeat road**: drive a street, then drive the **same street again** (or your normal commute both ways) | "second pass of <road>" | speed-limit cache hit (2nd pass = no network) |
| 13 | **One longer trip** (20+ km) | — | long-route ETA + speed-limit cache over many tiles |

## After each trip (optional, 30s)

- Open the trip → check the **Replay** autoplays and the speed line shows yellow/red where you sped.
- Open **Diagnostics** → **Services** → note the **speed-limit cache** line (on a repeat road it should say
  most tiles "cached", few/none "fetched").
- Don't delete trips — I'll pull the DB and analyze everything.

## What I'll analyze from the data

Raw GPS + 50 Hz motion + per-point speed limits + **per-window GNSS** + per-trip GNSS summary are all
stored. With your narration I can check: were events detected at the right moments? did the swerve
gate work (5 vs 4)? did bumps avoid false brakes (6)? did auto-stop land correctly (11)? did the cache
avoid refetching (12)? and how does GNSS quality track the urban-canyon stretch (9)?
