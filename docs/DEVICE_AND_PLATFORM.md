# Device and Platform Constraints

Authoritative for: target device facts, Android platform rules affecting Atlas, OxygenOS behaviour, and the guaranteed / improvable / must-test split.

---

## 1. Target device: OnePlus Nord 5 (India)

| Property | Value | Evidence |
|---|---|---|
| SoC | Qualcomm Snapdragon 8s Gen 3 (4 nm), Adreno 735 | [V] GSMArena spec page |
| Launch OS | Android 15 / OxygenOS 15 | [V] multiple spec sources |
| Current OS | OxygenOS 16 (Android 16) rolled out in India from late Nov 2025 (builds `CPH2707_16.0.1.x`) | [V] OnePlus community / press. **The owner's installed version is unknown [A]**: WI-090 records it from `Build.VERSION` |
| Update policy | 4 major OS upgrades, 6 years of security updates | [V] |
| Battery | 6800 mAh (India/global variant). Note: EU variant 5200 mAh | [V] GSMArena |
| GNSS | GPS, GLONASS, Galileo, BDS, QZSS, **NavIC** | [V] GSMArena. Dual-frequency (L1+L5) **not confirmed** [A] |
| Sensors | Accelerometer, gyroscope, proximity, compass, under-display optical fingerprint | [V] |
| Barometer | **Absent** | [V] GSMArena sensor list |
| Google Play services | Present on Indian retail units | [A] (standard for the OnePlus India SKU). WI-090 verifies `GoogleApiAvailability` |
| Significant-motion sensor | Unknown | [A] runtime check `SensorManager.getDefaultSensor(TYPE_SIGNIFICANT_MOTION)` |
| Hardware sensor FIFO / batching size | Unknown | [A] runtime check `Sensor.fifoMaxEventCount` |
| GNSS measurement / status support | Unknown | [A] runtime check `LocationManager.gnssCapabilities` |

### 1.1 Consequences for design [R]

- **No barometer** → GPS altitude is the only altitude source. Its vertical error is typically several times the horizontal error. Elevation gain is therefore **not shown** in v1 (ADR-013). Altitude is stored for export.
- **NavIC + multi-constellation** → good open-sky fixes are expected. Urban canyons (old city cores, flyover undersides) and metro tunnels will still produce gaps and multipath; the design must not depend on dual-frequency.
- **Large battery** → the absolute power budget is generous, but OxygenOS's battery manager is aggressive (§3). That aggression, not capacity, is the binding constraint.
- **Snapdragon 8s Gen 3** → on-device feature extraction and classification are trivially cheap (< 5 ms per epoch). CPU is not a constraint; wake-ups are.

## 2. SDK targets [R]

| Setting | Value | Rationale |
|---|---|---|
| `minSdk` | **31** (Android 12) | One primary device on Android 16. Android 12 gives the modern FGS-start restrictions, approximate-location UI, `PendingIntent` mutability rules and SplashScreen API. Avoids pre-12 branches while still covering most active Android devices. |
| `targetSdk` | **36** (Android 16) | Google Play requires API 36 for new apps and updates from 31 Aug 2026 [V]. The device runs Android 16. Adopting the newest behaviours lets us test them. |
| `compileSdk` | 36 (or newer stable at WI-001 time) | |
| 16 KB page size | All native libs must be 16 KB-aligned | [V] Play requirement for apps targeting Android 15+. MapLibre Native ships 16 KB-aligned binaries in current releases [V]. WI-001 adds a CI check (`zipalign -c -P 16` / APK Analyzer) |

## 3. Platform rules that shape the architecture

### 3.1 Location and foreground service [V]

1. Continuous background location requires a **foreground service with `foregroundServiceType="location"`**, the `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` permissions, and a visible notification. Without an FGS, background apps receive location only a few times per hour (Android 8+ background location limits).
2. Starting a location FGS while the app is in the background requires `ACCESS_BACKGROUND_LOCATION` ("Allow all the time") **and** one of the FGS background-start exemptions (Android 12+). The relevant exemptions for Atlas are:
   - receipt of `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`;
   - receipt of a **geofencing** or **activity-recognition transition** event;
   - the user has turned off battery optimisation for the app;
   - user interaction with a notification or widget, or a visible activity.
   An exemption alone is not enough for background location access on Android 14+: the service must also have `ACCESS_BACKGROUND_LOCATION`; unsupported paths must fall back to a user-visible resume action.
3. Android 15 forbids starting `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` and `microphone` FGS types from `BOOT_COMPLETED`. **`location` is not on that list** [V], but it is still subject to the while-in-use rule in 2. Atlas starts the recorder from `BOOT_COMPLETED` only when background location is granted, catches `ForegroundServiceStartNotAllowedException`, and falls back (TRACKING §2). [D] Confirm on device.
4. Android 14+: users can dismiss FGS notifications, and dismissing does not stop the service [V]. Atlas re-posts the notification on the next state change.
5. Android 12+: the user may grant **approximate** location only. Approximate location (~2 km) is useless for routes. Atlas treats it as `BLOCKED` with a specific explanation.
6. Background location must be requested **separately, after** foreground location. On Android 11+ it can only be granted in system Settings ("Allow all the time"). Atlas deep-links there.
7. `ACTIVITY_RECOGNITION` is a runtime permission (Android 10+).
8. `POST_NOTIFICATIONS` is a runtime permission (Android 13+). If it is denied, the FGS still runs, but its notification is hidden from the shade. Atlas warns, because the user loses visibility of recording.

### 3.2 Doze, App Standby, Battery Saver [V]

- An FGS with an active location request keeps receiving fixes in Doze in practice. Doze still defers jobs, alarms and network for the app. Atlas does not depend on network or alarms for recording.
- `PowerManager.getLocationPowerSaveMode()` reports how Battery Saver restricts location (e.g. `LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF`, `LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF`, `LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF`). Atlas reads this on every power-save change, records a health event and shows a warning. Gaps during these periods are attributed to cause `BATTERY_SAVER`.
- App hibernation ("Pause app activity if unused", Android 11+) can revoke permissions from apps without user interaction for months. Atlas asks the user to disable it, using `IntentCompat.createManageUnusedAppRestrictionsIntent`.

### 3.3 Android 16 specifics [V]

- **Edge-to-edge is mandatory** for apps targeting 36, with no opt-out. All screens handle insets (UI_UX §1.2).
- **Predictive back** is on by default. Use `BackHandler` / `OnBackPressedDispatcher`. Never `onBackPressed()`.
- **JobScheduler/WorkManager quotas are stricter**, including for jobs running while the app has an FGS. **Consequence [R]:** live and incremental processing runs *inside the recorder process on a coroutine*, not in WorkManager. WorkManager is used only for deferrable, resumable work (full reprocess, exports, compaction), and every such job must checkpoint and resume (ARCHITECTURE §6).
- On large screens (sw ≥ 600 dp), orientation locks are ignored. Atlas supports both orientations everywhere anyway (UI_UX §1.2).

### 3.4 Sensors [V]/[D]

- Since Android 9, background apps do not receive continuous-mode sensor events. An app with a running FGS is treated as foreground for this purpose [V]. [D] Verify screen-off delivery on the Nord 5.
- Sampling above 200 Hz needs `HIGH_SAMPLING_RATE_SENSORS`. Atlas samples at 50 Hz, so the permission is not needed.
- Non-wake-up sensor events are batched in a hardware FIFO while the application processor sleeps and are **dropped if the FIFO overflows** [V]. While `MOVING`, location callbacks every 1–2 s wake the AP, so overflow should not occur. Atlas measures the actual sample count per window and stores `sample_count`, so coverage is visible [D].

### 3.5 Direct Boot [V]

The database lives in credential-encrypted storage, which is unavailable until the first unlock after reboot. `BOOT_COMPLETED` arrives only after unlock. **Known limitation:** Atlas records nothing between power-on and first unlock. The gap is attributed to `RECORDER_DOWN` with the boot event as its cause.

## 4. OnePlus / OxygenOS behaviour

| Behaviour | Status | Atlas response |
|---|---|---|
| Aggressive background killing, even of FGS apps, especially after "clear all" in recents | [V] dontkillmyapp.com ranks OnePlus among the strictest | Onboarding step "Keep Atlas running" with exact instructions and deep links (UI_UX S03); liveness spans make kills visible; watchdog restarts via the exemptions in §3.1 |
| Battery optimisation setting reverting after OS updates | [V] reported (dontkillmyapp) | Re-check on every app start and daily from the recorder; warn when it reverts |
| "App battery management": *Allow background activity*, *Allow auto-launch* | [V] reported; exact menu names vary by OxygenOS version [A] | Onboarding shows a generic path + `ACTION_APPLICATION_DETAILS_SETTINGS` deep link. WI-090 records the exact OxygenOS 16 path on the owner's device and updates copy |
| Recents "Lock" (padlock) prevents clearing | [V] | Recommended in onboarding |
| Deep optimisation / sleep standby optimisation | [A] | Device test DT-07 (TEST_STRATEGY §4.1) |

### 4.1 What Android guarantees vs. what Atlas improves vs. what must be tested

| Category | Items |
|---|---|
| **Guaranteed by Android** (when permissions are granted and the FGS is running) | Location callbacks are delivered to an FGS; FGS start is allowed from the listed exemptions; `BOOT_COMPLETED` is delivered after unlock; Room/SQLite transactions are atomic and durable |
| **Improved by app logic** | Restart after kill (watchdog, AR transitions, geofence exit, boot); lower power via the state machine; gap attribution via liveness spans; data durability via WAL + journal spill; honest health reporting |
| **Must be measured on the Nord 5 [D]** | Kill frequency under OxygenOS with and without battery-optimisation exemption; screen-off sensor delivery; AR transition latency; batching behaviour; battery drain per state; GNSS behaviour in Bengaluru/Chennai/Mumbai-type urban canyons and metro tunnels [A on city]; behaviour under Battery Saver; FGS start from boot on OxygenOS 16 (TEST_STRATEGY §4.1) |

## 5. Location-provider behaviour [R]

- **Primary:** Fused Location Provider (`com.google.android.gms:play-services-location`) via `LocationRequest.Builder` with `Priority.PRIORITY_HIGH_ACCURACY` / `PRIORITY_BALANCED_POWER_ACCURACY` / `PRIORITY_PASSIVE`. Rationale: best fused fix quality, batching support, and the AR and Geofencing APIs from the same dependency.
- **Fallback:** platform `LocationManager` with `GPS_PROVIDER` + `NETWORK_PROVIDER` when Play services is unavailable (`PlayServicesUnavailable` health event). The adapter interface makes this transparent (ENGINEERING_CONTRACTS §4).
- **GNSS status:** `LocationManager.registerGnssStatusCallback` while `MOVING`, to record satellites used in fix. This is diagnostic, and also evidence for tunnel/underground detection. It is cheap, because GNSS is already powered.
- **Indoor / metro:** expect network-based fixes with 20–100 m accuracy, or no fixes at all. The `NO_FIX` state handles this (TRACKING §2).
