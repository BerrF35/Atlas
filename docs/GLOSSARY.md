# Glossary — Canonical Domain Vocabulary

This file is authoritative for terminology. Code identifiers in parentheses are the **exact** names to use in Kotlin/SQL.

## 1. Data layers

| Term | Code | Definition |
|---|---|---|
| **Observation** | `LocationObservation`, table `observation` | One location fix exactly as delivered by the OS, plus ingestion metadata. Immutable after insert. Never edited, never re-ordered in storage. |
| **Motion window** | `MotionWindow`, table `motion_window` | Summary features computed on-device from accelerometer/gyroscope samples over a fixed 5-second window. Raw IMU samples are **not** stored (except in Research Capture, see TRACKING §3.6). Immutable. |
| **Activity sample** | `ActivitySample`, table `activity_sample` | One Activity Recognition (Google Play services) result: an activity type with confidence, or an enter/exit transition. Immutable. |
| **Raw layer** | — | Observations + motion windows + activity samples + liveness spans + health events. Source of truth. Survives every reprocessing. |
| **Derived layer** | — | Everything computed from the raw layer + user corrections: epochs, segments, journeys, stays, places' statistics, aggregates, geometry caches. May be deleted and rebuilt at any time. |
| **User layer** | — | User corrections, notes, confirmed places, settings. Never derived; survives reprocessing; anchored to **time intervals** or **place ids**, never to derived row ids. |

## 2. Recording concepts

| Term | Code | Definition |
|---|---|---|
| **Tracking enabled** | `TrackingIntent.ENABLED` | The user's *wish* to record. Says nothing about whether recording is actually happening. |
| **Recorder** | `RecorderService` | The foreground service that acquires observations. |
| **Tracking session** | `TrackingSession`, table `tracking_session` | One continuous lifetime of the recorder process with recording enabled. It ends when the service stops, the process dies or the device shuts down. |
| **Liveness span** | `LivenessSpan`, table `liveness_span` | Interval during which the recorder demonstrably ran (heartbeat every 60 s extends `to_utc_ms`). Used to tell "nothing happened" apart from "we weren't watching". |
| **Sampling state** | `SamplingState` | Recorder state machine state: `OFF`, `BLOCKED`, `STATIONARY`, `PROBING`, `MOVING`, `STOP_PENDING`, `NO_FIX`, `PAUSED`. Defined in TRACKING §2. |
| **Health event** | `HealthEvent`, table `health_event` | Typed record of something affecting recording quality (permission revoked, FGS start failed, write failure…). |
| **Tracking health** | `TrackingHealth` | Live computed status shown to the user: `RECORDING`, `RESTING`, `DEGRADED`, `INTERRUPTED`, `BLOCKED`, `PAUSED`, `OFF`. Never derived from the toggle alone. |
| **Coverage** | `coverage` | Fraction of the time that tracking was enabled during which the recorder was alive, permitted, and receiving fixes at the expected cadence (or confirmed stationary). Defined in MAPS_AND_ANALYTICS §5. |

## 3. Reconstruction concepts

| Term | Code | Definition |
|---|---|---|
| **Epoch** | `Epoch`, table `classification_epoch` | Fixed 15-second interval of a moving period: the unit of classification. |
| **Stay** | `Stay`, table `stay` | Interval during which the user stayed within a small radius. Kinds: `PAUSE` (30 s–3 min, inside a segment; not persisted as a stay row, see below), `STOP` (3–10 min, inside a journey), `TRANSFER` (a wait between modes inside a journey), `VISIT` (between journeys). |
| **Pause** | — | Short halt (30 s ≤ d < 180 s) such as a traffic signal. Accounted as *stopped time* inside its segment and never shown as a stay row. |
| **Stop** | `StayKind.STOP` | Halt of 3–10 min inside a journey, or longer if covered by the transfer rule. |
| **Visit** | `StayKind.VISIT` | Stay that separates two journeys (≥ 10 min, or ≥ 3 min at a confirmed place). |
| **Journey** | `Journey`, table `journey` | Movement between two visits (or between a visit and an interruption). Contains ≥ 1 segment. |
| **Candidate journey** | `JourneyState.CANDIDATE` | A journey whose end is still provisional (the recorder is in `STOP_PENDING`, or the last stay is still shorter than the boundary threshold). Never counted in analytics. |
| **Open journey** | `JourneyState.OPEN` | The journey currently in progress (recorder `MOVING`). Shown live; not counted in analytics until closed. |
| **Confirmed journey** | `JourneyState.CONFIRMED` | Closed journey that has passed the minimum-journey rules. Counted in analytics. |
| **Segment** | `Segment`, table `segment` | Maximal sub-interval of a journey with a single transport mode (`kind = MOVE`) or with missing data (`kind = GAP`). Segments of a journey tile it completely: no overlaps, no holes. |
| **Transition** | `ModeTransition` | The boundary between two consecutive MOVE segments with different modes. It has a best-estimate time and an uncertainty half-width (`boundary_uncertainty_ms`). |
| **Gap / Missing-data interval** | `SegmentKind.GAP`, `GapCause` | Interval with no usable observations where observations were expected. Causes: `RECORDER_DOWN`, `NO_FIX`, `PERMISSION_BLOCKED`, `LOCATION_OFF`, `USER_PAUSED`, `LATE_START`. |
| **Uncertain segment** | `confidence < 0.55` | Segment whose mode posterior is below the "medium" band. Displayed with explicit uncertainty (UI_UX S07). |
| **Corrected segment** | `ModeSource.USER` | Segment whose mode was set by a user correction. Confidence is defined as 1.0. |
| **Journey boundary** | — | Time instant separating two journeys. Produced by visit rules, interruption rules, or a user `SPLIT` correction. |
| **Processing range** | `ProcessingRange` | Time range that is reprocessed atomically, expanded to anchor points (TRACKING §7). |
| **Reconstruction** | `ReconstructionEngine` | Pure function `(raw layer slice, user layer, params) → derived layer slice`. Deterministic. |

## 4. Places and routes

| Term | Code | Definition |
|---|---|---|
| **Place** | `Place`, table `place` | A user-meaningful location defined by a centre and radius. Status: `CANDIDATE` (suggested by clustering), `CONFIRMED` (accepted or named by the user), `IGNORED` (the user said "not a place"). |
| **Privacy zone** | `place.is_privacy_zone` | A place whose interior coordinates are blurred in exports and screenshots-safe views (PRIVACY §7). |
| **Recurring route** | `RouteCluster`, table `route_cluster` | Group of ≥ 3 confirmed journeys sharing start place, end place and a similar path. Status `SUGGESTED`, `CONFIRMED`, `DISMISSED`. |

## 5. Transport-mode vocabulary (Indian English, binding)

| UI label (en-IN) | Enum `TransportMode` | Notes |
|---|---|---|
| Stationary | `STATIONARY` | Not a segment mode; used in epochs only |
| Walk | `WALK` | |
| Run | `RUN` | |
| **Bicycle** | `BICYCLE` | Pedal-powered cycle. UI never says "bike" for this. |
| **Motorbike / scooter** | `MOTORCYCLE` | Motorised two-wheeler: motorcycles, scooters, mopeds, electric two-wheeler scooters (e.g. Ather, Ola-type). |
| Car | `CAR` | Includes taxis and ride-hail cars. |
| Auto-rickshaw | `AUTO_RICKSHAW` | User-assignable; not predicted by the v1 classifier. |
| Bus | `BUS` | |
| Train | `TRAIN` | Classifier output "rail" maps here and is displayed as "Train / metro" until the user refines it. |
| Metro | `METRO` | User-assignable refinement of rail. |
| Other motorised | `OTHER_MOTORIZED` | Ferries, kick e-scooters, tractors, etc. User-assignable. |
| Flight | `FLIGHT` | Suggested only for long gaps with implausible ground speed; never auto-confirmed. |
| Unknown | `UNKNOWN` | Evidence insufficient. |

"Bike" alone is ambiguous in Indian usage and is **banned from UI copy**. Use "Bicycle" or "Motorbike / scooter".

## 6. Confidence vocabulary

| Band | Range of calibrated posterior | UI treatment |
|---|---|---|
| High | ≥ 0.80 | Label shown plainly |
| Medium | 0.55 – 0.80 | Label shown plainly, uncertainty available in detail |
| Low | < 0.55 | Label shown as uncertain: "Car or bus?", or "Unknown" (UI_UX S07) |

"Confidence" always means the posterior probability of the chosen label given the classifier version. It never means GPS accuracy. GPS accuracy is always called **accuracy** and is given in metres (1-sigma radius as reported by Android).
