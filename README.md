# Car Trip Analyzer

An Android app that records your phone's sensors during a car trip, then reconstructs
speed and acceleration, detects driving events, scores the drive, and visualizes the
route on a map with charts.

Built with Kotlin + Jetpack Compose, Room (local storage), and the **Google Maps SDK**
(`maps-compose`) for the map/route display. A **Google Maps API key is required** to render maps
(`MAPS_API_KEY` in `local.properties`; the app builds without one but shows blank maps). Speed limits
come from **OpenStreetMap** via the free Overpass API (cached locally). Target device: Samsung Galaxy
S25 (Android 14/15), minSdk 26.

> **Note:** this README covers the original v1.x build flow + core concepts. The app has since grown
> well past it (auto-record, fuel/cost, drawdowns, Drive Stress Score, Insights, Google Sheets sync).
> See **HANDOFF.md** for the authoritative current state (source 3.26 / build 137, Room schema v21).

---

## How to get the APK

> The project is complete and ready to compile. Pick whichever path is easiest for you.
> Both produce `app-debug.apk`, which installs directly on your S25.

### Option A — GitHub Actions (no tools to install) ✅ recommended

1. Create a new repository on GitHub (private is fine).
2. Upload everything in this folder to the repo (or `git init` here, commit, and push).
3. GitHub runs the workflow in `.github/workflows/build-apk.yml` automatically on push.
   (Or go to the **Actions** tab → **Build APK** → **Run workflow**.)
4. When it finishes (~3–5 min), open the run → **Artifacts** → download
   `car-trip-analyzer-debug`. Inside is `app-debug.apk`.
5. Transfer that APK to your phone and install (see "Install on the S25" below).

### Option B — Android Studio (one click)

1. Install Android Studio (Koala or newer).
2. **Open** this folder as a project. Let it sync — Studio downloads the Gradle wrapper,
   SDK 34, and dependencies automatically.
3. Plug in the S25 (USB debugging on) and press **Run ▶**, or
   **Build → Build APK(s)** to get `app/build/outputs/apk/debug/app-debug.apk`.

### Option C — Command line

Requires JDK 17 + Android SDK (platform 34, build-tools 34):

```bash
bash ./gradlew assembleDebug  # macOS/Linux
.\gradlew.bat assembleDebug   # Windows PowerShell
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Install on the Samsung S25

1. Copy `app-debug.apk` to the phone (USB, Google Drive, or email to yourself).
2. Open it with the **Files** app. Android will ask to allow installing unknown apps —
   tap **Settings**, enable **Allow from this source**, go back, and **Install**.
3. Launch **Car Trip Analyzer**.
4. On first **Start trip**, grant **Location** ("While using the app" is enough for manual recording)
   and **Notifications**. Make sure phone GPS/Location is turned on.
   - **Hands-free auto-record** (optional) additionally needs Location set to **"Allow all the time"**
     so a trip can start in the background while the app is closed — set this in the Auto-record screen.

---

## Cloud sync & export (v1.2)

- **Google Sheets auto-append.** Connect a Google account (Home screen) and every trip
  appends to a "Car Trip Analyzer Log" sheet in your Drive — tabs for Summary (one row/trip),
  Samples (per-second cleaned data), and Events. Opens directly in Excel / Microsoft 365.
  One-time setup (signing key + Google Cloud OAuth) is in **GOOGLE_SHEETS_SETUP.md**.
- **Native .xlsx per trip.** Each trip is also written as an `.xlsx` on the phone; the trip
  detail screen has a **Share Excel** button.
- **Open in Google Maps.** Trip detail has buttons to open the start, end, or route in Google
  Maps.

Cloud sync is optional — the app records and analyzes fine without it.

## Using it

- **Start trip** → mount the phone and drive. A persistent notification shows it's
  recording. The screen shows live elapsed time, speed, distance, and a running count of
  hard braking / acceleration / cornering events.
- **End trip** → the app analyzes everything and saves the trip.
- **Past trips** → tap any trip to see the full summary: smoothness score, the route on a
  map with event markers, and speed / longitudinal / lateral acceleration charts.

For best GPS speed accuracy, give the phone a clear view of the sky (windshield mount).
Indoor or parking-garage tests will have little/no GPS data.

---

## What it measures, and how

Recorded each trip:
- **GPS** (location, speed, bearing) via Android's GPS provider, as fast as the phone reports (~1 Hz).
- **Linear acceleration** and **gyroscope** at ~25 Hz (for peak g-force).

Derived metrics (computed when you end the trip):

| Metric | Method |
|---|---|
| Distance, duration, idle time | Sum of GPS segment distances; idle = speed < 0.5 m/s |
| Max / average speed | From GPS speed |
| Longitudinal acceleration (accel/brake) | Δspeed / Δt between GPS fixes — orientation-independent |
| Lateral acceleration (cornering) | speed × yaw-rate, where yaw-rate comes from GPS bearing change |
| Peak g-force | Magnitude of the phone's linear-acceleration vector |
| Hard brake / accel / corner counts | Thresholds with a 2 s debounce so one maneuver counts once |
| Smoothness score (0–100) | 100 minus a penalty for events per km |

**Event thresholds** (in `analysis/TripAnalyzer.kt`, easy to tune):
hard accel ≥ 2.5 m/s², hard brake ≥ 3.0 m/s², hard corner ≥ 3.5 m/s².

Deriving longitudinal/lateral acceleration from GPS rather than the raw accelerometer
means results don't depend on how the phone is oriented in the mount — a common failure
mode for sensor-only trip apps. The raw accelerometer is still recorded and used for the
peak g-force figure.

### Data quality / filtering (v1.1)

v1.0 produced impossible values (e.g. thousands of km/h) because coarse network-location
fixes were mixed with GPS and tiny time gaps between fixes blew up `distance ÷ time`.
v1.1 fixes this:

- **Fused location, GPS-only fallback.** Uses Google's fused location provider at high
  accuracy (with `LocationManager` GPS as a fallback); the coarse NETWORK provider is no
  longer used.
- **Monotonic timestamps.** Δt comes from the device's elapsed-realtime clock, so duplicate
  or out-of-order fix times can't create huge speeds.
- **Accuracy gate.** Fixes worse than 35 m accuracy are dropped.
- **Outlier/jump rejection.** A fix whose distance from the last good point implies > 75 m/s
  (~270 km/h) and isn't backed by the chip's own Doppler speed is discarded.
- **Speed smoothing.** Speed is low-pass filtered before being differentiated into
  acceleration.
- **Plausibility caps.** Longitudinal accel beyond 8 m/s² and lateral beyond 12 m/s² are
  treated as noise and ignored.
- **Robust peak g.** Peak g-force uses the 99th percentile of motion magnitude instead of a
  single spike.

The trip detail screen shows how many GPS fixes survived filtering ("Used X of Y fixes").
Tunable constants live at the top of `analysis/TripAnalyzer.kt`.

---

## Project layout

```
app/src/main/java/com/cartrip/analyzer/
  TripApp.kt              Application (app init; starts the auto-record watcher)
  MainActivity.kt         Compose host, navigation, runtime permissions
  data/                   Room entities, DAO, database
  record/                 Foreground recording service + live state
  analysis/TripAnalyzer.kt   Pure-Kotlin metrics/event engine
  ui/                     Compose screens, charts, map, theme
.github/workflows/build-apk.yml   CI that outputs the APK
```
