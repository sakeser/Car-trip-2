# Roadmap additions (owner-requested 2026-06-28, post-Batch-4)

New items from the owner, assessed + ordered by value × (1/risk). Worked top-down ("clear the log,
easiest/least-risk first"). Rev letters continue from CJ.

## Tier A — easy, low risk (UI polish) — DO FIRST
- **CK — Past-trips filter compaction + scroll affordance.** Make the recency chips shorter/smaller font,
  shift the whole screen up (less white space). Add a visible scrollbar/affordance on the trip list so the
  user knows there's more below. Files: `ui/TripListScreen.kt`.
- **CL — Smart bar sizing across the app.** You-vs-traffic bar shouldn't always run to the screen edge:
  auto-size to ~80% with a sensible **minimum**, and show the scale better. Audit the other bars
  (speeding share, peak-vs-limit, fuel/insights charts, duration bars) for intelligent sizing that uses
  meter space well and avoids extreme/edge-case lengths. Files: `ui/TripDetailScreen.kt` (EtaCompare,
  SpeedingShareBar, PeakLimitBar), `ui/Charts.kt`, `ui/TripListScreen.kt` (DurationBar).

## Tier B — medium (analysis / value), low–medium risk
- **CM — POI-aware endpoint naming (free tier).** Use the on-device `Geocoder`'s feature/premises name to
  tag obvious destinations ("Home → IKEA → Tim Hortons → Home") when confidence is high; fall back to the
  neighbourhood when not. Confidence-gated: pick the most specific label only when confident, else generic.
  This is a free approximation of the (skipped, paid) Places API — extend `ui/GeoNamer.kt`. NB: Android
  Geocoder POI quality is uneven; treat as best-effort with a confidence gate. Places API (New) remains the
  high-accuracy upgrade if/when billing is enabled.
- **CN — AI-readable export + share ("ask an AI about your driving").** Generate a compact, structured
  (JSON or markdown) summary of trips/metrics/trouble-spots/stress that the user can share into
  ChatGPT/Claude for generic insights. Builds on the existing export. Innovative, low backend cost (the app
  already emits compact metrics — principle 10). Add a "Share for AI insights" action + a copy-to-clipboard.

## Tier C — larger, higher risk (do deliberately, verify carefully)
- **CO — Encrypt-at-rest + biometric lock.** SQLCipher-encrypt the local DB (location history is sensitive)
  and gate app access behind biometric (BiometricPrompt). Needs a one-time encrypted-DB migration of the
  existing plaintext DB, a key in the Android Keystore, and a graceful fallback. Pairs with the
  commercialization "secure the data" item. Higher risk (DB layer + key management) — its own focused rev.

## Cross-cutting principle (owner): always look for novel, innovative ways to present and add value/analysis.
Examples to fold in opportunistically: AI coaching from the CN export; stress/drawdown trends over time;
"your worst-traffic times/routes"; per-destination analytics once POI naming (CM) lands.
