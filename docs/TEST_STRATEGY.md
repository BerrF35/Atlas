# Test Strategy

Authoritative for: test levels, fixtures, quality metrics, device protocol, and release gates. Tests prove behaviours; targets marked [D] require measurement on the owner’s OnePlus Nord 5.

## 1. Test architecture

| Level | Runs | Scope |
|---|---|---|
| Unit/JVM | every change | geo, sampling, features, classifier/HMM, reconstruction, analytics, format parsers, migrations |
| Property/fuzz | nightly | coordinate/time invariants, malformed import/archive, no segment overlaps, export escaping |
| Repository/Robolectric | every change | Room transactions/migrations, DataStore, correction/reprocess, errors |
| Compose/Roborazzi | every change | every screen/state at light/dark, 100%/200% scale, narrow/wide layouts |
| Instrumentation/GMD | nightly API 34/36 | permissions, service lifecycle, map wrapper, SAF, keystore/backup, accessibility |
| Device field protocol | release candidate | Nord 5/OxygenOS truth, power, real GNSS, OEM killing, network egress |
| Benchmark | release candidate | startup, map frame pacing, reconstruction and ingestion budgets |

No test relies on live network, wall-clock time, random UUIDs or the device locale unless explicitly testing them. Inject `Clock`, dispatcher and sources. Golden fixtures contain expected schema/model version.

## 2. Fixture format and catalogue

`.atlastrack` is newline-delimited JSON with header (`formatVersion`, zone, start, provenance, transforms) followed by timestamped location, AR, IMU-window, power, permission and expected-boundary records. All values are synthetic or transformed; no raw personal trace enters git. Transform rules remove exact coordinates, shift/rotate/speed-jitter consistently, replace timestamps with relative times, and revalidate labels.

| ID | Fixture / test | Required assertions |
|---|---|---|
| F01 | stationary + short walk | no false journey; state transitions |
| F02 | walk/run/bicycle | per-mode epochs and boundaries |
| F03 | motorbike/car/bus ambiguity | calibrated uncertainty, no silent certainty |
| F04 | metro tunnel/no-fix | GAP cause, no invented route, playback pause |
| F05 | multimodal auto/bus/train transfer | segment tiling, transfer/visit rules |
| F06 | urban canyon/spikes | flags retained, no impossible distance |
| F07 | process kill/late restart | liveness and recorder-down attribution |
| F08 | pause/permission/location-off | exact causes and health copy |
| F09 | timezone/midnight/DST import | daily clipping and date grouping |
| F10 | corrections | precedence, reprocess determinism, undo revision |
| F11 | export/import/backup | round-trip, privacy zone, malformed/cancelled input |
| F12 | 3-year synthetic history | NFR-PERF-3 and aggregate equivalence |

## 3. Required test cases

### 3.1 Algorithm and data invariants

Test increasing timestamps after normalisation; raw immutability; segment intervals exactly tile journeys; no negative metric; GAP has no mode; all exported coordinates are finite WGS84; correction results are idempotent; changing processor version without raw/user change changes only derived rows; two replay runs produce byte-equivalent canonical JSON output.

### 3.2 Lifecycle and fault injection

Inject Room busy/full/exception, journal write failure, process kill before/after batch commit, out-of-order callback, service-start denial, provider disabled, temporary no-fix, reboot-before-unlock, and WorkManager stop. The test observes every accepted callback ends either in database or a visible critical health event—never silent loss. Force app process death during migration and backup restore; recovery must preserve original database or a valid staged replacement.

### 3.3 UX/accessibility

Snapshot every S01–S25 content/empty/loading/error/blocked state. Test TalkBack labels and traversal, contrast token pairs, touch targets, keyboard/back behaviour, 200% font scale, dark mode, no animation with reduce motion, map offline overlay and no truncation of health/gap text. Semantics tests verify both line-pattern and textual mode labels.

### 3.4 Personal-data fixture policy

`fixtures/private/` is ignored and never read by CI. An owner may use it locally only for research capture. Before a fixture is admitted to `fixtures/transformed/`, run transform, inspect map render against original for re-identification, remove named-place/timestamp correlation, document provenance/transform, and obtain the owner’s explicit review. Repository pre-commit and CI reject `.atlastrack` outside approved fixture paths and reject common GPX/KML/backup extensions.

## 4. Metrics and pass criteria

Algorithm report has confusion matrix, macro F1, per-class F1, ECE, boundary timing MAE, journey boundary F1 (60 s), distance relative error, and gap-cause accuracy. Baseline P0 gates: macro F1 ≥0.75; WALK/RUN F1 ≥0.85; ECE ≤0.08; no existing fixture regression >0.03 F1; reconstruction output has zero invariant violations. These are fixture gates, not real-world marketing claims.

Performance test methods: macrobenchmark 10 cold/20 warm iterations after controlled cache state; frame timing during scripted map pan/zoom; 30 replay samples for ingestion/classification; three-year fixture on physical device. Report median and p95, device/OS/build, thermal/battery state, and raw samples. A failure is not averaged away.

### 4.1 Device protocol [D]

| ID | Procedure | Pass evidence |
|---|---|---|
| DT-01 | 7-day normal use with recorder | health/gap timeline and battery delta |
| DT-02 | 2 h mixed walk/motorbike/car/bus | labelled segment comparison and CPU timings |
| DT-03 | screen-off 60 min moving/stationary | fix/IMU completeness |
| DT-04 | clear recents, force-stop separately, reboot/unlock | correct distinction and restart/gap attribution |
| DT-05 | revoke precise/background/AR/notification; toggle location | blocked copy and no false Recording |
| DT-06 | Battery Saver/Doze/poor network/airplane | cause attribution and offline map route |
| DT-07 | OxygenOS settings/restart paths after update | exact OEM copy, battery exemption recheck |
| DT-08 | 1-year export, backup/restore, range purge | timing, integrity, explicit external-file caveat |
| DT-09 | packet capture while exercising all screens | only allowed tile host/system services |
| DT-10 | 200% font/TalkBack/landscape/large-screen | all essential data usable |

## 5. Battery and storage targets [D]

Measure from 80% charge, fixed brightness/radio profile, three repeats: stationary recorder ≤0.5%/h median, mixed 2 h movement ≤4%/h median, and 24 h ordinary day ≤8% total target. These are acceptance targets, not guaranteed behaviour; report state occupancy and signal conditions. Verify raw storage growth target ≤250 MB/year extrapolated from actual write rate at 2 h/day under Balanced sampling; include WAL/map-cache separately.

## 6. Release gates

1. All required unit, property, repository, UI and nightly instrumentation tests pass on clean checkout.
2. Current migration from every supported schema and backup restore pass with no raw loss.
3. Network allow-list and redacted-log scans pass.
4. All P0 acceptance scenarios pass; P1 is complete for v1.0.
5. DT-01–DT-10 have evidence on Nord 5 or the release is explicitly labelled device-validation incomplete and not v1.0.
6. NFR-PERF targets and battery/storage measurements meet target or have signed risk/deferral decision.
7. Accessibility audit and manual TalkBack protocol pass.
8. No critical/open high-severity privacy, data-loss, or tracking-honesty defect.
