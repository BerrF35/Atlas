# Project Atlas — Stage 2 Implementation Handoff

**Author:** Primary Implementation Engineer / Lead Architect  
**Date:** 2026-10-13  
**Target Hardware:** OnePlus Nord 5 (Indian Variant) · OxygenOS 16 / Android 16 (API 36 compile/target, minSdk 31)  
**Package Namespace:** `com.berrf35.atlas`  
**Artifact Status:** Debug APK assembled at `app/build/outputs/apk/debug/app-debug.apk` (84,953,519 bytes / ~84.9 MB)  
**Build Health:** 100% build pass across all 11 modules (`:app:assembleDebug`, `:core:*:compileDebugKotlin`, `:feature:*:compileDebugKotlin`). All 349 tasks up-to-date or successfully executed.  
**Test Suite Health:** All unit and integration test suites pass with 0 failures (`testDebugUnitTest`). `:core:data:testDebugUnitTest` executed 23 tests with 23 passes (100% pass rate).

---

## 1. Executive Summary & Deliverables

In Stage 2, Project Atlas transitioned from an initial UI shell into a functional, local-first movement intelligence system. The implementation strictly adheres to the engineering contracts in `docs/` (`DATA_MODEL.md`, `TRACKING_AND_CLASSIFICATION.md`, `MAPS_AND_ANALYTICS.md`, `PRIVACY_AND_SECURITY.md`, and `DEVICE_AND_PLATFORM.md`).

Key achievements in this phase:
1. **Persistent Local Database Architecture:** Implemented complete 11-table schema in native SQLite with WAL mode, foreign keys, transactions, and explicit separation of raw observations from derived journeys.
2. **Real Android Background Location Recorder:** Created `AtlasTrackingService` as a Foreground Service with `FOREGROUND_SERVICE_TYPE_LOCATION`, notification channel, adaptive battery profiles, batch buffering, quality filtering, and `BootReceiver`.
3. **Automatic Journey Reconstruction:** Built `TripSegmenter` with quality gating, stay detection (visits vs stops), journey confirmation thresholds (>=120m displacement or >=180s moving time), and explicit gap preservation for GPS signal loss.
4. **Kinematic Transport Classifier:** Built `BaselineTransportClassifier` distinguishing Walking, Running, Bicycle, Motorcycle, Car, Metro, and Stationary, with strict Bicycle vs. Motorcycle separation, calibrated posterior confidence bands, and Viterbi temporal smoothing.
5. **Real Street Mapping & Vector Fallback:** Integrated official MapLibre Native Android SDK (`org.maplibre.gl:android-sdk:13.6.1`) with OpenFreeMap vector tile style, Android lifecycle management, and a vector Canvas fallback with honest offline badging.
6. **Analytics & Place Intelligence:** Implemented `AnalyticsAndPlaces` with period distance rollups, 7×24 hour-weekday density matrices, DBSCAN-style visit clustering into places, and recurring corridor detection.
7. **Exports, Corrections, and Privacy:** Implemented `DataExporter` supporting GPX 1.1, GeoJSON FeatureCollection, and RFC 4180 CSV, with Privacy Zone circular geofence clipping and immutable correction audit trails.
8. **Automated Verification:** Authored and verified 23 automated unit and integration tests across data persistence, classification, segmentation, exports, and analytics.

---

## 2. Subsystem Implementations

### 2.1 Persistent Local Data (`:core:data`)
- **Schema Implementation:** Conforms to `docs/DATA_MODEL.md`. Created [`AtlasDbHelper.kt`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/database/AtlasDbHelper.kt) and [`AtlasDao.kt`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/database/AtlasDao.kt).
  - `tracking_session`: Records recording intervals, app version, device fingerprint.
  - `observation`: Append-only raw GNSS fixes (`id`, `session_id`, `time_utc_ms`, `lat_e7`, `lon_e7`, `accuracy_cm`, `altitude_cm`, `speed_mmps`, `bearing_cdeg`, `provider`, `is_mock`, `source`, `zone_id`, `quality_flags`).
  - `health_event`: Diagnostic logs (provider toggled, battery throttling, permissions).
  - `liveness_span`: Heartbeat records for truthful tracking coverage calculation.
  - `journey`: Reconstructed trips (`id`, `from_utc_ms`, `to_utc_ms`, `state`, `distance_m`, `moving_duration_ms`, `stopped_duration_ms`, `gap_duration_ms`, `primary_mode`, `origin_place_name`, `destination_place_name`, `is_deleted`).
  - `segment`: Granular movement/gap slices linked to journeys with GeoJSON point arrays.
  - `stay`: Stationary intervals classified as STOP, TRANSFER, or VISIT.
  - `place`: Confirmed/candidate places with center coordinate and geofence radius.
  - `route_cluster`: Recurring origin-destination corridors.
  - `user_correction`: Immutable audit trail for manual mode overrides.
  - `daily_aggregate`: Precomputed rollups for rapid calendar rendering.
- **Data Integrity & Separation:**
  - Raw observations are strictly append-only and cannot be altered by UI operations.
  - Soft deletion (`is_deleted = 1`) hides journeys from timeline queries while preserving historical data.
  - Demo data (`AtlasDemoData.kt`) is completely isolated from production database queries via `AtlasSettings.isDemoMode`.

### 2.2 Background Location Tracking (`:core:data`)
- **Service Architecture:** Implemented [`AtlasTrackingService.kt`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/tracking/AtlasTrackingService.kt).
  - Runs as an ongoing Android Foreground Service with `android:foregroundServiceType="location"`.
  - Notification channel `"atlas_tracking_channel"` with low-priority notification avoiding audio interruption.
  - Lifecycle actions: `ACTION_START`, `ACTION_STOP`, `ACTION_PAUSE`, `ACTION_RESUME`.
- **Sampling Profiles:**
  - `PRECISE`: 2s interval, 2m min distance.
  - `BALANCED`: 5s interval, 5m min distance.
  - `SAVER`: 15s interval, 15m min distance.
- **Buffering & Robustness:**
  - In-memory buffer flushes in atomic SQLite transactions every 10 seconds or 20 fixes.
  - Filters out fixes with accuracy > 200m or invalid coordinate bounds.
  - Detects provider disabled/enabled events and writes `health_event` rows.
  - [`BootReceiver`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/tracking/AtlasTrackingService.kt#L299) registered in manifest for device restart recovery.

### 2.3 Automatic Journey Reconstruction (`:core:data`)
- **Pipeline Implementation:** [`TripSegmenter.kt`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/reconstruction/TripSegmenter.kt).
  - Filters raw observations by quality thresholds.
  - Rejects micro-displacement jitter (< 120m displacement AND < 180s duration).
  - Detects stay pauses:
    - 30s – 3m: Internal segment pause.
    - 3m – 10m: STOP / TRANSFER stay.
    - >= 10m: VISIT stay (splits trip into separate journeys).
  - Partitions tracks into sub-legs separated by missing-data gaps (>= 180s without fix), creating explicit `SegmentKind.GAP` with `GapCause.NO_FIX` rather than fabricating fake routes across outages.

### 2.4 Transport Classification (`:core:data`)
- **Kinematic Classifier:** [`BaselineTransportClassifier.kt`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/classification/BaselineTransportClassifier.kt).
  - Divides trips into 15-second analysis epochs.
  - Computes median speed, max speed, acceleration variance, and heading rate of change.
  - **Bicycle vs. Motorcycle Separation:**
    - Bicycle: 8 – 35 km/h, max speed < 38 km/h, smooth acceleration (< 1.8 m/s²).
    - Motorcycle: Max speed > 32 km/h with burst accelerations (> 0.4 m/s² above 35 km/h) or nimble heading deltas.
  - Calibrated posterior probabilities with confidence bands: `HIGH` (>= 0.80), `MEDIUM` (0.55 – 0.79), `LOW` (< 0.55).
  - Temporal Viterbi-style smoothing prevents flickering during momentary traffic light stops.

### 2.5 Real Mapping Integration (`:core:map`)
- **MapLibre Native SDK Integration:** Added `org.maplibre.gl:android-sdk:13.6.1` to `:core:map`.
- **MapLibre Component:** [`MapLibreNativeMap.kt`](file:///c:/Users/Admin/Desktop/atlas/core/map/src/main/kotlin/com/berrf35/atlas/core/map/MapLibreNativeMap.kt).
  - Uses `MapView` with OpenFreeMap vector tile style (`https://tiles.openfreemap.org/styles/liberty`).
  - Full Android lifecycle handling: observers for `ON_START`, `ON_RESUME`, `ON_PAUSE`, `ON_STOP`, `ON_DESTROY`.
  - Camera bounds calculation centering route segments.
- **Vector Canvas Fallback:** [`AtlasMap.kt`](file:///c:/Users/Admin/Desktop/atlas/core/map/src/main/kotlin/com/berrf35/atlas/core/map/AtlasMap.kt).
  - Crisp vector canvas rendering for offline operation, previews, and screenshot testing.
  - Displays honest status badge: *"Basemap offline · Vector route rendered from local cache"*.

### 2.6 Analytics and Place Intelligence (`:core:data`)
- **Engine Implementation:** [`AnalyticsAndPlaces.kt`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/analytics/AnalyticsAndPlaces.kt).
  - Computes period analytics for Day, Week, Month, Year, and All Time.
  - Generates 7-day distance bars, mode percentage breakdowns, and personal records.
  - Builds 7×24 hour-weekday movement density matrix with 5 discrete intensity levels.
  - Spatial stay clustering: clusters `VISIT` stay centroids within 80 meters into candidate places.
  - Recurring corridor detection: clusters trips with >= 3 journeys between same origin and destination.

### 2.7 Data Exports and Privacy (`:core:data`)
- **Exporter Implementation:** [`DataExporter.kt`](file:///c:/Users/Admin/Desktop/atlas/core/data/src/main/kotlin/com/berrf35/atlas/core/data/export/DataExporter.kt).
  - **GPX 1.1:** Standard XML with `<trk>`, `<trkseg>`, `<trkpt>`, timestamps, elevation, and mode extensions.
  - **GeoJSON:** RFC 7946 FeatureCollection with `LineString` route geometries and `Point` stop features.
  - **CSV:** RFC 4180 format with ISO-8601 timestamps, coordinates, accuracy, speed, bearing, and mock flags.
- **Privacy Zone Geofencing:**
  - Checks coordinates against all confirmed places where `isPrivacyZone == true`.
  - Clips internal coordinates within the privacy radius from exports, preserving sensitive home/work origins.

---

## 3. Verification & Test Execution Results

All test suites were compiled and executed against the verified JDK 17 toolchain (`.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat :core:data:testDebugUnitTest`).

### 3.1 `:core:data` Automated Tests (23 / 23 Passed)

| Test Class | Test Method | Verification Target | Result |
|---|---|---|---|
| `BaselineTransportClassifierTest` | `testClassifyStationaryTrack` | Speed < 1.8 km/h classified as STATIONARY with HIGH confidence | **PASS** |
| `BaselineTransportClassifierTest` | `testClassifyWalkingTrack` | 4.7 km/h track classified as WALK with posterior >= 0.70 | **PASS** |
| `BaselineTransportClassifierTest` | `testClassifyRunningTrack` | 11.5 km/h track classified as RUN with step cadence | **PASS** |
| `BaselineTransportClassifierTest` | `testClassifyBicycleTrack` | 19.8 km/h smooth track classified as BICYCLE, strictly NOT motorcycle | **PASS** |
| `BaselineTransportClassifierTest` | `testClassifyMotorcycleTrack` | 50.4 km/h burst track classified as MOTORCYCLE, strictly NOT bicycle | **PASS** |
| `BaselineTransportClassifierTest` | `testViterbiSmoothingDuringTrafficStop` | Momentary stop during car trip remains smoothed as CAR | **PASS** |
| `TripSegmenterTest` | `testFilterObservationsRejectsInaccurateAndOutOfBounds` | Inaccurate (>200m) and out-of-bounds fixes discarded | **PASS** |
| `TripSegmenterTest` | `testRejectsMicroJitterBelowConfirmationThreshold` | Jitter < 120m and < 180s does not create confirmed journey | **PASS** |
| `TripSegmenterTest` | `testReconstructsConfirmedMovingJourney` | Valid 500m movement creates confirmed journey with metrics | **PASS** |
| `TripSegmenterTest` | `testSplitsJourneyAtLongVisitStay` | 15-minute halt stay splits track into 2 distinct journeys | **PASS** |
| `TripSegmenterTest` | `testPreservesMissingDataIntervalAsGapSegment` | 4-minute missing fix interval creates GAP segment with NO_FIX | **PASS** |
| `DataExporterTest` | `testExportGpxGeneratesValidXml` | GPX 1.1 root tag, tracks, segments, points, and mode tags | **PASS** |
| `DataExporterTest` | `testExportGpxAppliesPrivacyZoneClipping` | Coordinates inside privacy radius clipped from output | **PASS** |
| `DataExporterTest` | `testExportGeoJsonGeneratesValidFeatureCollection` | RFC 7946 LineString and Point FeatureCollection | **PASS** |
| `DataExporterTest` | `testExportCsvGeneratesRfc4180Format` | Header row and formatted observation rows | **PASS** |
| `AtlasDaoTest` | `testInsertAndQueryObservations` | Batch insertion and chronological retrieval of observations | **PASS** |
| `AtlasDaoTest` | `testInsertJourneyWithSegmentsAndStays` | Hierarchical insertion of journey, segments, and stays | **PASS** |
| `AtlasDaoTest` | `testSoftDeletePreservesObservations` | Soft deletion hides journey but preserves raw observations | **PASS** |
| `AtlasDaoTest` | `testUserCorrectionUpdatesModeAndRecordsAudit` | Mode override updates segment and inserts user_correction row | **PASS** |
| `AtlasDaoTest` | `testPlacesCrudAndPrivacyZoneFlag` | Place creation, update, privacy zone flag, and deletion | **PASS** |
| `AnalyticsAndPlacesTest` | `testComputePeriodAnalyticsSumsDistancesAndComputesModeShare` | Metric sum and 50/50 mode share calculations | **PASS** |
| `AnalyticsAndPlacesTest` | `testDetectRecurringCorridorsGroupsFrequentTrips` | Detects corridor when journey count >= 3 between places | **PASS** |
| `AnalyticsAndPlacesTest` | `testClusterPlacesFromStaysGroupsNearbyVisits` | Clusters multiple stays into candidate places | **PASS** |

### 3.2 Full Project Test Suite
- `.\gradlew.bat testDebugUnitTest` executed across all modules: **BUILD SUCCESSFUL** (349 tasks executed/up-to-date, 0 failures).

---

## 4. Build Artifacts & Output Paths

| Artifact | File Path | File Size | Description |
|---|---|---|---|
| **Debug APK** | `app/build/outputs/apk/debug/app-debug.apk` | 84,953,519 bytes (~84.9 MB) | Standalone installable APK with native MapLibre binaries (`libmaplibre.so`), fonts, and full feature set |
| **Database Schema** | `core/data/src/main/kotlin/com/berrf35/atlas/core/data/database/AtlasDbHelper.kt` | 10.9 KB | 11-table SQLite WAL schema conforming to `DATA_MODEL.md` |
| **DAO & Queries** | `core/data/src/main/kotlin/com/berrf35/atlas/core/data/database/AtlasDao.kt` | 27.6 KB | CRUD operations, batch inserts, audit logs, and queries |
| **Classifier** | `core/data/src/main/kotlin/com/berrf35/atlas/core/data/classification/BaselineTransportClassifier.kt` | 8.9 KB | Kinematic rule engine & Viterbi smoother |
| **Segmenter** | `core/data/src/main/kotlin/com/berrf35/atlas/core/data/reconstruction/TripSegmenter.kt` | 13.1 KB | Automatic trip reconstruction & gap detection |
| **Tracking Service**| `core/data/src/main/kotlin/com/berrf35/atlas/core/data/tracking/AtlasTrackingService.kt` | 11.4 KB | Foreground location recorder service |
| **MapLibre Engine** | `core/map/src/main/kotlin/com/berrf35/atlas/core/map/MapLibreNativeMap.kt` | 3.5 KB | MapLibre SDK composable with OpenFreeMap style |
| **Data Exporter** | `core/data/src/main/kotlin/com/berrf35/atlas/core/data/export/DataExporter.kt` | 7.9 KB | GPX 1.1, GeoJSON, CSV exporter with privacy clipping |

---

## 5. Physical Device & Emulator Operational Notes

1. **OnePlus Nord 5 / OxygenOS 16 Requirements:**
   - Onboarding screen S03 outlines the required user actions for background battery optimization. Users must set Atlas to *"Don't optimize"* in *Settings > Battery > More settings > App battery management* to prevent OxygenOS from killing the foreground service after extended screen-off intervals.
   - Background location permission (`ACCESS_BACKGROUND_LOCATION`) must be selected as *"Allow all the time"* in system settings after granting foreground location.
2. **Offline Map Tiles:**
   - When online, MapLibre fetches tiles from OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`).
   - When offline or when network is disabled, `AtlasMap` seamlessly renders the vector Canvas route with the honest offline badge.
3. **Demo Mode Toggle:**
   - In *Settings*, toggling "Demo Mode" allows instant inspection of the complete UI using pre-populated Bangalore multimodal journeys (`AtlasDemoData`).
   - When "Demo Mode" is disabled, all screens reactively switch to live database queries backed by `AtlasDao`.
