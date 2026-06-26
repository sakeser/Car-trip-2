# Claude Review Handoff - Rev AO / v2.79

Date: 2026-06-26

Scope reviewed:
- Main worktree: `C:\Users\sinan\OneDrive\Desktop\cartrip-main`
- Latest source: `f93a7e2` docs on top of `ada46a7` Rev AO / v2.79 code
- Installed S25 package verified as `versionName=2.79`, `versionCode=90`
- Unit test XMLs showed 92 tests, 0 failures/errors

## Before The Next Field Test

### P1 - Fix charger event stale-state handling

Files:
- `app/src/main/java/com/cartrip/analyzer/record/AutoRecordWatchService.kt`
- `app/src/main/java/com/cartrip/analyzer/record/AutoRecordController.kt`

Issue:
`AutoRecordWatchService` receives `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` and immediately calls `AutoRecordController.reevaluate()`. `reevaluate()` reads the sticky `ACTION_BATTERY_CHANGED` state instead of using the broadcast event as the authoritative edge.

The on-device auto-record log already shows inverted/stale reads:
- `charger-off -> ARM [chg=true ...]`
- `charger-on -> no-op [chg=false ...]`

Risk:
This can miss a true mount/charging start or arm after unplugging. This is the highest field-test risk because Rev AO relies on charger events as the reliable hands-free trigger.

Suggested fix:
- Pass the power event edge into the controller, or add a dedicated `onPowerChanged(connected: Boolean, source: String)` path.
- Treat `ACTION_POWER_CONNECTED` as `charging=true` and `ACTION_POWER_DISCONNECTED` as `charging=false`.
- If wireless-vs-wired still matters, schedule a delayed sticky battery reread for `plugged == BATTERY_PLUGGED_WIRELESS`, but do not let the delayed sticky state invert the edge.
- Add tests around connect/disconnect edge handling.

### P1/P2 - Bluetooth runtime receiver may be registered too restrictively

File:
- `app/src/main/java/com/cartrip/analyzer/record/AutoRecordWatchService.kt`

Issue:
The watcher registers one combined receiver with `ContextCompat.RECEIVER_NOT_EXPORTED`. Android docs note that some system broadcasts, including highly privileged framework-app broadcasts such as Bluetooth/telephony, may require an exported runtime receiver to receive all broadcasts.

Risk:
Power events may work, while Bluetooth connect/disconnect remains unreliable. This matches the earlier field-test pattern where BT did not fire.

Suggested fix:
- Split power and Bluetooth runtime receivers.
- Keep power as `RECEIVER_NOT_EXPORTED`.
- Consider registering Bluetooth ACL actions with `RECEIVER_EXPORTED`, while still filtering by saved car MAC address before acting.
- Add auto-record log entries for receiver registration mode and BT event drops.

### P2 - Auto-record enable lacks permission preflight

Files:
- `app/src/main/java/com/cartrip/analyzer/ui/AutoRecordScreen.kt`
- `app/src/main/java/com/cartrip/analyzer/record/RecordingService.kt`

Issue:
The Auto-record screen starts the watcher without checking whether location and notification permissions are currently granted. The S25 has them now, but a fresh install or revoked permission can leave auto-record enabled while the recording foreground service later fails.

Risk:
On Android 14+, foreground services using while-in-use permissions can throw at creation/start time if permission state is wrong. Field tests on a reset install could silently fail or show only a fallback.

Suggested fix:
- On enabling auto-record, request/check `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `POST_NOTIFICATIONS` where applicable.
- Show an explicit disabled/error state if permissions are missing.
- In `AutoRecordController.arm()` and `RecordingService.startInForeground()`, log the actual exception message/class in the auto-record log.

### P2 - CDM pairing on the phone looks suspicious

Files:
- `app/src/main/java/com/cartrip/analyzer/record/CompanionCarManager.kt`
- `app/src/main/java/com/cartrip/analyzer/ui/AutoRecordScreen.kt`

Observed device state:
- App prefs list Tucson as `00:92:A5:39:E3:42`.
- `dumpsys companiondevice` shows two `com.cartrip.analyzer` associations for `3C:E0:02:E3:48:26`.

Risk:
CDM may be observing a different or duplicate device from the chosen Tucson BT device. If `onDeviceAppeared` fires for the wrong nearby device, auto-record can arm at the wrong time. If it never fires for Tucson, CDM remains a misleading "paired" status.

Suggested fix:
- Dedupe associations before calling `startObservingDevicePresence`.
- Prefer `setSingleDevice(true)` unless there is a specific reason to support multiple car devices.
- Surface the associated CDM display name/address in the UI.
- Add an option to clear/disassociate old CDM associations.
- For the next field test, either confirm `3C:E0:02:E3:48:26` is actually the relevant car/adapter or clear/re-pair.

### P2 - Raw GNSS cleanup is incomplete

Files:
- `app/src/main/java/com/cartrip/analyzer/ui/TripViewModel.kt`
- `app/src/main/java/com/cartrip/analyzer/data/TripDao.kt`
- `app/src/main/java/com/cartrip/analyzer/record/RecordingService.kt`

Issue:
The new `gnss_measurements` table is not consistently handled everywhere older raw tables are handled.

Specific gaps:
- UI trip delete removes `gnss_samples` but not `gnss_measurements`.
- Auto-stop trim deletes `gnss_samples` after cutoff but not `gnss_measurements`.
- DAO "delete all" and sample-delete helpers do not include GNSS tables.

Risk:
Raw GNSS is enabled on the S25 and the app DB is already about 83 MB. Orphaned rows can grow quickly, confuse exports/analysis, and make repeated field testing messy.

Suggested fix:
- Add `deleteGnssMeasurementsAfter(id, after)`.
- Call `deleteGnssMeasurements(id)` in `TripViewModel.deleteTrip()`.
- Include GNSS tables in all clear/sample-delete helper paths as appropriate.
- Consider central DAO transaction helpers such as `deleteTripWithRaw(id)` to avoid missing future tables.

### P2 - Raw GNSS schema is not yet sufficient for serious lane-level offline work

Files:
- `app/src/main/java/com/cartrip/analyzer/data/Entities.kt`
- `app/src/main/java/com/cartrip/analyzer/record/RecordingService.kt`
- `app/src/main/java/com/cartrip/analyzer/data/AppDatabase.kt`

Issue:
`GnssMeasurementSample` stores ADR and Doppler fields, but not enough epoch/receiver-clock metadata for robust raw GNSS reconstruction.

Missing or worth adding before R&D field drives:
- `GnssClock.timeNanos`
- `GnssClock.fullBiasNanos`
- `GnssClock.biasNanos` and uncertainty when present
- `GnssClock.elapsedRealtimeNanos`
- `GnssClock.hardwareClockDiscontinuityCount`
- `GnssMeasurement.receivedSvTimeNanos`
- `receivedSvTimeUncertaintyNanos`
- `state`
- `multipathIndicator`
- `codeType`
- carrier phase / AGC fields where available

Risk:
If the field test is intended to capture lane-detection data, the current schema may collect high-volume data that is not sufficient for the intended offline processing. Missing raw fields cannot be recovered after the drive.

Suggested fix:
- Decide whether the upcoming field test is for app auto-record validation or GNSS R&D.
- If GNSS R&D: add the fields above and bump schema before driving.
- If not GNSS R&D: turn raw GNSS off for the field test to reduce DB growth.

### P2 - Local debug APK artifact is stale

File:
- `app/build/outputs/apk/debug/output-metadata.json`

Issue:
The installed phone is v2.79/90, but the local debug APK metadata under this worktree still says v2.34/45.

Risk:
Someone reinstalling from `app/build/outputs/apk/debug/app-debug.apk` may accidentally put an old build on the phone.

Suggested fix:
- Rebuild before any reinstall: `.\gradlew.bat assembleDebug`.
- Verify metadata and installed version after install.
- Consider deleting stale build outputs before handoff if they keep causing confusion.

## Good To Fix Soon

### P3 - Auto-record log is too small/noisy for multi-drive debugging

File:
- `app/src/main/java/com/cartrip/analyzer/record/AutoRecordLog.kt`

Issue:
The ring buffer stores only 80 lines. Duplicate `cdm-observe started` entries can consume a lot of that budget.

Risk:
After a multi-stop field test, the oldest and most important trigger entries may be pushed out.

Suggested fix:
- Increase `MAX` to at least 300 for field testing.
- Dedupe repeated CDM observe-start lines.
- Add a one-tap export/share for the auto-record log from Diagnostics.

### P3 - Bluetooth state is only event-fed

Files:
- `app/src/main/java/com/cartrip/analyzer/record/AutoRecordController.kt`
- `app/src/main/java/com/cartrip/analyzer/record/AutoRecordWatchService.kt`

Issue:
`carBtConnected` defaults to `false` and only changes after future ACL broadcasts. When the watcher starts after the car is already connected, Bluetooth-only triggering can miss the drive until reconnect.

Risk:
Bluetooth is not a reliable alternate trigger in all lifecycle orderings.

Suggested fix:
- On watcher startup, query connected profiles/devices if permission allows.
- Or treat CDM presence as the preferred BT-like path and de-emphasize the classic ACL checkbox.

### P3 - Auto-record settings copy is stale

File:
- `app/src/main/java/com/cartrip/analyzer/ui/AutoRecordScreen.kt`

Issue:
The UI still says charging and car Bluetooth can only auto-start while the app is open unless paired. Rev AO changes that with the persistent watcher.

Risk:
User/field tester may configure the wrong thing or distrust the correct path.

Suggested fix:
- Update copy to explain:
  - Persistent watcher handles charging/classic BT while auto-record is enabled.
  - CDM is secondary/experimental for this classic-BT car.
  - Charging edge plus motion-confirm is the main field-test path.

### P3 - Dead receiver files still contain stale comments

Files:
- `app/src/main/java/com/cartrip/analyzer/record/PowerConnectionReceiver.kt`
- `app/src/main/java/com/cartrip/analyzer/record/CarBluetoothReceiver.kt`

Issue:
These files are no longer manifest-registered, but comments describe the old manifest-receiver architecture.

Risk:
Future agents may search for charger/BT logic and edit the dead files.

Suggested fix:
- Delete them if not used.
- Or mark them clearly as dead/superseded by `AutoRecordWatchService`.

### P3 - Manual drive/non-drive override has no reset-to-auto control

Files:
- `app/src/main/java/com/cartrip/analyzer/ui/TripDetailScreen.kt`
- `app/src/main/java/com/cartrip/analyzer/ui/TripViewModel.kt`

Issue:
`userIsDrive` supports `null` for auto mode, but the UI only flips forced drive/non-drive.

Risk:
Once manually overridden, a trip cannot be returned to auto-classification from the UI.

Suggested fix:
- Add a third menu item or small segmented control: Auto / Drive / Non-drive.

## Current Field-Test Configuration Notes

Observed on connected S25:
- Installed app: `versionName=2.79`, `versionCode=90`
- Auto-record enabled
- `requireWireless=false`
- `useBluetooth=false`
- `companionAssociated=true`
- Watcher foreground service currently running
- Location, notification, and Bluetooth permissions granted
- Raw GNSS logging currently enabled
- App DB roughly 83 MB

Recommendation:
- For an auto-record trigger test, fix P1 first, clear the auto-record log, and disable raw GNSS unless the drive is explicitly for GNSS R&D.
- For a GNSS R&D drive, add missing raw GNSS clock/epoch fields first.

## References

- Android runtime receiver export behavior: https://developer.android.com/develop/background-work/background-tasks/broadcasts
- Android foreground-service background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android notification permission behavior: https://developer.android.com/about/versions/13/behavior-changes-13
- `GnssClock` API reference: https://developer.android.com/reference/android/location/GnssClock
- `GnssMeasurement` API reference: https://developer.android.com/reference/android/location/GnssMeasurement
