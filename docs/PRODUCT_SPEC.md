# Product Specification

Authoritative for: goals, non-goals, personas, user journeys, functional requirements (`PRD-*`), priorities.
Terminology: [GLOSSARY.md](GLOSSARY.md). Visual and textual behaviour: [UI_UX_SPEC.md](UI_UX_SPEC.md).

---

## 1. Product statement

Atlas is a **personal geographical memory**. It records movement passively, reconstructs it into an honest and editable history, and helps its owner understand their movement over days, months and years without sending anything off the phone.

### 1.1 Goals (ranked)

1. **G1 Faithful record.** Capture every movement that the OS permits. Preserve the raw evidence permanently, and never silently lose data.
2. **G2 Honest reconstruction.** Turn evidence into journeys, segments and stays. Show uncertainty and gaps instead of hiding them.
3. **G3 Effortless.** No start/stop buttons in normal use, and minimal corrections.
4. **G4 Insight.** Answer "where, how, how much, how often, how has it changed" in seconds.
5. **G5 Private by construction.** Local-only data, no account, minimal network.
6. **G6 Frugal.** Battery, storage and CPU budgets are explicit and measured (TEST_STRATEGY §6).

### 1.2 Non-goals

- Fitness coaching, training plans, heart-rate, calories (ADR-014).
- Navigation and routing to destinations.
- Social sharing, leaderboards, accounts, cloud sync.
- Vehicle telemetry (OBD, fuel).
- Tracking other people. Atlas records only the device it runs on, for its owner.
- Guaranteed uninterrupted recording. That is impossible on Android (DEVICE_AND_PLATFORM §4). Atlas guarantees **detection and disclosure** of interruptions instead.

### 1.3 Persona

The owner (single user): an Indian smartphone user in a metro or tier-2 city on a OnePlus Nord 5 who walks, rides a motorbike or scooter, sometimes cycles, takes autos, buses, the metro and occasionally trains. They are technically capable and value precision and privacy, and they want the app to be quietly correct.

## 2. Core user journeys

| ID | Journey | Success condition |
|---|---|---|
| UJ-1 | **First run.** Install → understand → grant permissions → recording starts. | Under 3 min. Recording health shows `RECORDING` or `RESTING` with a real fix. Every OEM restriction is explained, with a direct shortcut. |
| UJ-2 | **Glance today.** Open the app → see today's map, distance, journeys, and whether recording is healthy. | First meaningful paint in under 1 s warm, under 2 s cold (NFR-PERF-1). Health is visible without a tap. |
| UJ-3 | **Inspect a journey.** Tap a journey → route by mode, segments, stops, times, speeds, and gaps. Play it back. | Every segment shows its mode, distance, duration and confidence band. Gaps are drawn and explained. |
| UJ-4 | **Correct a journey.** Change a mode, move a transition, split or merge journeys, delete a false journey. | Change applied in under 2 s for a journey of 2 h or less. Statistics update. The correction survives a classifier upgrade. |
| UJ-5 | **Explore history.** Browse the calendar → pick a day/week/month → filter by mode/place. | Any day from the past 3 years opens in under 1.5 s (NFR-PERF-3). |
| UJ-6 | **Understand patterns.** Insights for week/month/year/lifetime; recurring routes, frequent places, heatmap. | Every metric has a tappable definition. No misleading precision. |
| UJ-7 | **Diagnose.** "Why is yesterday 14:00–15:00 missing?" → Diagnostics explains the cause (e.g. process killed, permission revoked). | The cause is shown for at least 95% of gaps longer than 5 min in device tests (TEST_STRATEGY §4.1). |
| UJ-8 | **Own the data.** Export a date range to GPX/CSV/GeoJSON, make a full encrypted backup, restore on a new phone, delete everything. | Export of 1 year finishes in under 2 min on device, is cancellable, and is never partial without saying so. |
| UJ-9 | **Pause.** "Don't record for the next hour", or "until I resume". | The pause is honoured, and the paused interval is shown as `USER_PAUSED`, not as a failure. |

## 3. Functional requirements

Priority: **P0** = must ship in v1.0; **P1** = should ship in v1.0; **P2** = v1.x (designed now, built later).
Every requirement maps to work items and tests in [ACCEPTANCE_CRITERIA.md](ACCEPTANCE_CRITERIA.md).

### 3.1 Recording (TRK)

| ID | P | Requirement |
|---|---|---|
| PRD-TRK-01 | P0 | Record location continuously in the background via a `location`-type foreground service while tracking is enabled and permissions allow. |
| PRD-TRK-02 | P0 | Adapt sampling to motion state using the state machine in TRACKING §2: high-rate while moving, near-zero cost while stationary. |
| PRD-TRK-03 | P0 | Detect movement start within a median of ≤ 60 s and a 90th percentile of ≤ 180 s of departure on device [D]. Represent undetected lead-in as a `LATE_START` gap. |
| PRD-TRK-04 | P0 | Persist each observation durably within 10 s of receipt (buffered batch commit). Never drop an observation silently on write failure: retry, spill to the journal file, and raise a health event. |
| PRD-TRK-05 | P0 | Resume recording automatically after process death, app update and reboot (after first unlock) wherever Android permits. Record the interruption. |
| PRD-TRK-06 | P0 | Maintain liveness spans and health events so that every gap can be attributed to a cause. |
| PRD-TRK-07 | P0 | Show truthful tracking health in-app and in the persistent notification. |
| PRD-TRK-08 | P0 | Pause recording for 30 min / 1 h / 4 h / until resumed, from the app and from the notification. |
| PRD-TRK-09 | P1 | Battery profiles: Precise, Balanced (default), Saver; their sampling policy is defined in TRACKING §2. |
| PRD-TRK-10 | P1 | Record accelerometer/gyroscope motion windows while `MOVING` (Balanced and Precise only). |
| PRD-TRK-11 | P1 | Record Activity Recognition updates and transitions. |
| PRD-TRK-12 | P2 | Quick Settings tile for pause/resume. |
| PRD-TRK-13 | P1 | Detect and warn about: approximate-only permission, background permission missing, location services off, Battery Saver location restrictions, battery optimisation active, unused-app hibernation enabled, notification permission denied. |

### 3.2 Classification (CLS)

| ID | P | Requirement |
|---|---|---|
| PRD-CLS-01 | P0 | Classify each 15 s moving epoch into {STATIONARY, WALK, RUN, BICYCLE, MOTORCYCLE, CAR, BUS, TRAIN} with a probability distribution, fully on-device. |
| PRD-CLS-02 | P0 | Temporal smoothing via HMM with the transition structure in TRACKING §5. No transport-mode segment shorter than the minimums in TRACKING §6 unless a user correction defines it. |
| PRD-CLS-03 | P0 | Store a calibrated confidence per segment and show uncertainty honestly (Low band → "X or Y?" / "Unknown"). |
| PRD-CLS-04 | P0 | Work with any subset of {GPS, IMU, Activity Recognition}. Missing evidence widens uncertainty and never causes a crash or an "unknown everywhere" result when GPS exists. |
| PRD-CLS-05 | P0 | Record the classifier id, version and parameter hash for every prediction. |
| PRD-CLS-06 | P0 | User corrections act as hard constraints that survive reprocessing and classifier upgrades. |
| PRD-CLS-07 | P1 | Label-collection mode: notification actions to declare the current mode for ground truth. |
| PRD-CLS-08 | P2 | Local personalisation of baseline likelihood parameters from corrections, adopted only if it beats the baseline on held-out data (TRACKING §8). |
| PRD-CLS-09 | P2 | Rail-proximity evidence from cached vector tiles (deferred; see ADR-008 and ADR-011). |

### 3.3 Reconstruction (REC)

| ID | P | Requirement |
|---|---|---|
| PRD-REC-01 | P0 | Reconstruct stays, journeys and segments per TRACKING §7 deterministically from the raw and user layers. |
| PRD-REC-02 | P0 | A journey may contain any number of segments of different modes, including GAP segments. |
| PRD-REC-03 | P0 | Distinguish pause / stop / transfer / visit by the rules in TRACKING §6. |
| PRD-REC-04 | P0 | Merge fragments split by recorder interruption when they are physically continuous (TRACKING §6). |
| PRD-REC-05 | P0 | Discard micro-movements (journeys below the minimums) without deleting raw data. |
| PRD-REC-06 | P0 | Show the open journey live, refreshed at least every 60 s while the UI is visible. |
| PRD-REC-07 | P0 | Corrections: set mode for a range, move a transition, split a journey, merge adjacent journeys, mark "not a journey", delete a journey (soft), purge raw data for a range (hard, confirmed). |
| PRD-REC-08 | P0 | Reprocess an affected range after any correction within 2 s for ranges up to 2 h of movement (on device [D]). |
| PRD-REC-09 | P1 | Full-history reprocessing after a version change, resumable and chunked, with progress in Diagnostics. |

### 3.4 Maps (MAP)

| ID | P | Requirement |
|---|---|---|
| PRD-MAP-01 | P0 | MapLibre map with Atlas monochrome light and dark styles and correct OSM attribution. |
| PRD-MAP-02 | P0 | Render journey routes with per-mode encoding (MAPS_AND_ANALYTICS §3). GAP segments are drawn as gap connectors. |
| PRD-MAP-03 | P0 | Live position with an accuracy circle while the UI is visible. |
| PRD-MAP-04 | P0 | Stop and transition markers. Tapping a marker shows its details. |
| PRD-MAP-05 | P0 | Journey playback with a scrubber and timestamp. |
| PRD-MAP-06 | P0 | Simplified geometry per zoom from cached levels. Raw points stay available in "Show recorded points" mode, including an accuracy visualisation. |
| PRD-MAP-07 | P1 | Heatmap of all or filtered history. |
| PRD-MAP-08 | P1 | Recurring-route view and comparison of journeys on a route. |
| PRD-MAP-09 | P1 | Offline: ambient tile cache (default 256 MB) plus user-downloaded offline regions. |
| PRD-MAP-10 | P0 | Usable without network: routes draw on a blank styled canvas when tiles are unavailable, with a "Map tiles unavailable offline" notice. |
| PRD-MAP-11 | P2 | Optional map matching through a user-configured self-hosted service (ADR-011). |

### 3.5 Analytics (ANL)

| ID | P | Requirement |
|---|---|---|
| PRD-ANL-01 | P0 | Day, week, month, year and lifetime statistics as defined in MAPS_AND_ANALYTICS §6. |
| PRD-ANL-02 | P0 | Distance and time by mode; journey count; moving vs stopped time; average and median journey duration. |
| PRD-ANL-03 | P0 | Statistics recompute after corrections, deletions and reprocessing. Daily aggregates are kept consistent transactionally. |
| PRD-ANL-04 | P0 | Display precision rules (MAPS_AND_ANALYTICS §5). Estimated values are visibly marked. |
| PRD-ANL-05 | P1 | Records (longest journey, longest day, max distance per mode, …). |
| PRD-ANL-06 | P1 | Hour × weekday distribution; weekday vs weekend. |
| PRD-ANL-07 | P1 | Trends vs the previous period and a 6-month trend chart. |
| PRD-ANL-08 | P0 | Coverage and uncertainty metrics per day/period. |
| PRD-ANL-09 | P2 | Elevation gain/loss. Disabled on devices without a barometer, including the Nord 5 (ADR-013). |

### 3.6 Places & routes (PLC)

| ID | P | Requirement |
|---|---|---|
| PRD-PLC-01 | P1 | Suggest candidate places from repeated visits (MAPS_AND_ANALYTICS §7). A single visit never creates a candidate. |
| PRD-PLC-02 | P1 | Confirm, rename, resize, merge, split, ignore and delete places. |
| PRD-PLC-03 | P1 | Assign visits to places. Visits show the place name in the timeline. |
| PRD-PLC-04 | P1 | Suggest recurring routes. The user confirms, names or dismisses them. |
| PRD-PLC-05 | P1 | Mark a place as a privacy zone. |
| PRD-PLC-06 | P2 | Optional reverse geocoding (off by default) through the Android `Geocoder`, with consent. |

### 3.7 Data management (DAT)

| ID | P | Requirement |
|---|---|---|
| PRD-DAT-01 | P0 | Export GPX 1.1, CSV (observations, segments, journeys), GeoJSON for a date range or a single journey. |
| PRD-DAT-02 | P0 | Full backup `.atlasbackup` (encrypted with a passphrase) and restore. |
| PRD-DAT-03 | P1 | Import GPX tracks as `source = IMPORT` observations. |
| PRD-DAT-04 | P0 | Delete a journey (soft), purge a date range (hard), delete all data (full reset). |
| PRD-DAT-05 | P1 | Retention policy for raw observations: Keep forever (default), or compact raw data older than N months to simplified geometry (DATA_MODEL §9). |
| PRD-DAT-06 | P0 | Storage usage breakdown and storage-pressure warnings. |
| PRD-DAT-07 | P0 | Export jobs are cancellable and resumable-safe: a partial file is deleted or clearly labelled. |
| PRD-DAT-08 | P0 | Schema migrations never lose raw data. A failed migration falls back to a recovery path (DATA_MODEL §9). |

### 3.8 Privacy & security (PRV)

| ID | P | Requirement |
|---|---|---|
| PRD-PRV-01 | P0 | No location or derived data leaves the device except through user-initiated exports/shares. |
| PRD-PRV-02 | P0 | No account, analytics, crash-reporting SDK or advertising SDK. |
| PRD-PRV-03 | P0 | `allowBackup=false` and data-extraction rules exclude everything. |
| PRD-PRV-04 | P1 | Optional app lock (device credential / biometric). |
| PRD-PRV-05 | P0 | Diagnostic exports are redacted by default (no coordinates). |
| PRD-PRV-06 | P1 | Privacy zones blur coordinates in exports (configurable). |

### 3.9 Diagnostics (DIA)

| ID | P | Requirement |
|---|---|---|
| PRD-DIA-01 | P0 | A Diagnostics screen with live state, a permission/OEM checklist, today's coverage, a recent health event log and gap attribution. |
| PRD-DIA-02 | P0 | Per-day coverage timeline (24 h strip) showing recorded, stationary-confirmed, gap-by-cause. |
| PRD-DIA-03 | P1 | Redacted diagnostic bundle export (JSON). |
| PRD-DIA-04 | P1 | Research capture: raw IMU and all fixes for labelled sessions, for fixture creation. Off by default. |

### 3.10 Non-functional (NFR)

| ID | Requirement |
|---|---|
| NFR-PERF-1 | Day screen: warm start to first meaningful content ≤ 1.0 s, cold ≤ 2.0 s on Nord 5 [D]. |
| NFR-PERF-2 | Map pan/zoom on a day with ≤ 20 journeys at ≥ 55 fps median [D]. |
| NFR-PERF-3 | Opening any historical day ≤ 1.5 s with 3 years of synthetic history (≈ 3 M observations). |
| NFR-PERF-4 | Live ingestion CPU: ≤ 2 ms per observation median on device. Per-epoch feature + classification ≤ 5 ms [D]. |
| NFR-BAT-1 | Battery budgets in TEST_STRATEGY §5 (targets to validate, not claims). |
| NFR-STO-1 | Raw storage growth ≤ 250 MB/year at 2 h/day of movement under Balanced (DATA_MODEL §9). |
| NFR-REL-1 | Zero silent observation loss in fault-injection tests (TEST_STRATEGY §3.2). |
| NFR-A11Y-1 | WCAG 2.2 AA contrast, TalkBack-complete, 200% font scale without truncating essential data. |
| NFR-PRIV-1 | Network traffic restricted to the allow-list (PRIVACY §4), verified by test. |

## 4. Prioritisation rationale

P0 is the smallest set that delivers G1–G3 and G5 with honesty: recording, reconstruction, correction, map, core stats, export/delete, diagnostics. Places, routes, heatmap and records (P1) are where the insight value lies, but they depend on a trustworthy P0 base. P2 items need real labelled data (personalisation), extra infrastructure (map matching) or hardware the device lacks (barometric elevation).

## 5. Release definition

- **v0.1 "Recorder"**: P0 TRK + Diagnostics + raw export. Used on the device for ≥ 7 days to collect labelled data and device measurements.
- **v0.5 "Reconstruction"**: P0 CLS/REC/MAP + Day screen + Journey detail + corrections.
- **v1.0**: all P0 + P1, all release gates in TEST_STRATEGY §6 passed.
