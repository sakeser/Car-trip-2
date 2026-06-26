# Claude Notes: v2.68 Pre-Field-Test Fix/Review List

Date: 2026-06-25
Installed phone build verified by ADB: `versionName=2.68`, `versionCode=79`
Latest main commit reviewed: `348f287` / Rev AD
Review mode: read-only assessment converted into suggested actions.

## Highest Priority Before Field Test

### P0 - Do not build/install from the dirty UX worktree

The current checked-out folder `C:\Users\sinan\OneDrive\Desktop\cartrip` is on `ux-redesign-v1`, has dirty UI changes, and its checked-out `app/build.gradle.kts` still reports `versionName="2.21"` / `versionCode=32`.

The phone already has the correct `v2.68/79` installed. If another build/install is needed before the drive, build from `main` / `origin/main` at `348f287`, not from the dirty UX branch.

Action:
- Use the mainline worktree/branch for field-test fixes.
- Do not merge or reset the dirty UX branch as part of this task.
- If you must work in this folder, first switch safely only after protecting the dirty UX changes.

### P0 - Enable and verify auto-record settings before driving

Device read did not show saved filtered `cartrip_autorecord` prefs or existing auto-record log entries. That likely means auto-record still needs to be enabled in-app before the test.

Action:
- In app: Home -> Options -> Auto-record drives.
- Enable auto-record.
- Confirm `Require wireless charging` is set intentionally.
- If testing Bluetooth, choose the car device, but see the Bluetooth trigger caveat below.
- After enabling, open Diagnostics once and confirm the Auto-record log card exists.

### P0 - Avoid Home foreground auto-start masking the test

`HomeScreen.AutoTripDetection` can auto-start recording while the app is open on Home after sustained movement. That is separate from the new charger/Bluetooth auto-record controller. If the app is left foreground on Home, the drive may start via the old foreground GPS-speed helper, hiding whether the background charger/Bluetooth path worked.

Action:
- For the auto-record field test, enable auto-record, then background/lock the app before plugging in/driving.
- After the drive, inspect Diagnostics -> Auto-record log.
- If the trip starts but the log does not show `charger-on -> ARM` / `FGS start OK` / `FGS start BLOCKED`, the Home auto-start path probably masked the test.

## Important Fixes To Consider Before Field Test

### P1 - Bluetooth is not currently an alternate trigger

In `AutoRecordPolicy.Config`, `requireCharging` defaults to `true`. `triggerPresent(...)` returns only `chargeOk` when `requireCharging=true`, even if `useBluetooth=true`.

Current behavior:
- Bluetooth only corroborates while charging is present.
- Bluetooth alone will not arm recording.
- The UI says "Also use car Bluetooth", but it is not a true alternate trigger in this build.

Suggested fix:
- Either expose `requireCharging` in the Auto-record UI, or change the policy so `useBluetooth && carBtConnected` can trigger when the user enables Bluetooth.
- Update `AutoRecordPolicyTest.bluetoothIgnoredWhenChargingRequired` if the desired behavior changes.
- Clarify UI copy if Bluetooth is intended only as a corroborating signal.

Recommended before field test if Bluetooth behavior is being tested. Otherwise document that this test is charger-only.

### P1 - Trigger-drop auto-stop does not use retrospective trim

The 6-minute GPS idle auto-stop path uses `AutoStop.retrospectiveEndTime(...)` and trims to the real stop moment. The charger/Bluetooth trigger-drop path calls `autoStopGrace()`, waits 8 seconds, then calls plain `stopRecording()`.

Current effect:
- Unplug-triggered stop can include extra parked/unplug tail time.
- It is saved through the normal stop path rather than explicitly using the auto-stop trim cutoff.

Suggested fix:
- When `ACTION_AUTO_STOP_GRACE` fires, compute the same retrospective rest timestamp from `recentSpeedTrack` if available.
- Call `stopRecording(TrimCutoff(...))` and preserve `TripEndReason.AUTO_STOP`.
- Add a small unit or service-adjacent test if practical, or at least verify with a short drive plus parked/unplug stop.

Recommended before field test if unplug auto-stop timing is one of the key things being judged.

### P1 - Background location FGS start may be blocked on Android 14

This is likely expected platform behavior, not just an app bug. Android 12+ restricts starting foreground services from background, and Android 14 checks while-in-use permissions for location foreground services when the service is created.

Current code logs:
- `FGS start OK`
- `FGS start BLOCKED (<exception>) -> tap-to-start notif`

Suggested field-test focus:
- Treat `FGS start BLOCKED` as useful evidence.
- If blocked, next implementation step is likely `CompanionDeviceManager` with `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`, or another user-visible/user-initiated start path.
- Make sure `POST_NOTIFICATIONS` is granted so the fallback notification can actually appear. Device check showed it granted for `v2.68`.

Reference:
- Android docs: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

## Useful Improvements Before Or During Field Test

### P2 - Diagnostics auto-record log should refresh

`DebugScreen` reads `AutoRecordLog.entries(ctx)` inside `remember`, so if Diagnostics is left open, new log entries may not appear until the screen is reopened.

Suggested fix:
- Add a Refresh button or make log entries state-driven.
- Add a Clear button if useful before a clean field test.
- At minimum, tell the tester to leave/re-enter Diagnostics after the drive.

### P2 - CI-built APKs may be keyless for maps/traffic

`app/build.gradle.kts` reads `MAPS_API_KEY` from `local.properties` or environment. The local machine has a key in `local.properties`, but `.github/workflows/build-apk.yml` does not visibly inject `MAPS_API_KEY`.

Current phone build has maps key available locally, so this is probably fine for the installed build.

Suggested fix:
- If using GitHub Actions for future APKs, add a repository secret/env injection for `MAPS_API_KEY`.
- Verify Diagnostics -> Build -> Maps API key says present before field tests that depend on maps/Routes.

### P2 - README and some handoff docs are stale

`README.md` still says osmdroid / no API key, while current `main` uses Google Maps metadata and Routes.

Suggested fix:
- Update README after field-critical work.
- Do not let stale README instructions drive the field-test install path.

## Can Defer Until After Field Test

### P3 - Rev AD UI batch looks acceptable

The `v2.68` trip-detail UI changes are small and coherent:
- You-icon picker now drives the map replay marker.
- Replay Play increments `cameraResetKey` and resets the route fit.
- Redundant "Refresh speed limits" button is removed when limits are already present.
- Fuel chip text is shorter and constrained to one line.

No blocking issue found here for field testing.

Possible polish later:
- If the map marker icon is changed while TripMap is already composed, it may require reopening the trip/map to reflect the updated `UiPrefs.youIcon(...)` because the glyph is read through `remember { ... }`.

### P3 - Docs mention `v2.64`/older status in places

`HANDOFF.md` and `REV_HISTORY.md` lag behind the pasted Claude update for Revs AC/AD. This is not a runtime issue, but it can confuse future agents.

Suggested fix:
- After field test or the next code pass, refresh docs through `v2.68`.

## Recommended Field-Test Checklist

Before leaving:
- Confirm app version on phone: `2.68 (79)`.
- Enable Auto-record drives.
- Confirm Location, Notifications, and Bluetooth permissions are granted.
- Confirm Maps key present in Diagnostics.
- If testing background auto-record, background/lock the app before plugging in/driving.

During drive:
- Do one normal manual recording if detector validation is the priority.
- Do one auto-record attempt with app backgrounded if auto-record is the priority.
- Narrate key events with wall-clock time anchors.

After drive:
- Open Diagnostics fresh.
- Capture Auto-record log lines, especially:
  - `charger-on -> ARM`
  - `FGS start OK`
  - `FGS start BLOCKED (...)`
  - `AUTO_ARM: provisional trip started`
  - `motion-confirm OK/FAILED`
  - `AUTO_STOP grace`
  - `auto-stopped`
- Open the trip detail and check route, events, speed limits, and replay.
- Do not delete trips until DB/log review is complete.

