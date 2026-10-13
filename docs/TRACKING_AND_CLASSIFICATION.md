# Tracking and Classification

Authoritative for: sampling, evidence quality, features, classification, reconstruction, correction semantics, and evaluation. Data fields are in [DATA_MODEL.md](DATA_MODEL.md).

## 1. Inputs and non-negotiable rules

The live pipeline accepts location fixes, activity-recognition samples, 50 Hz accelerometer and gyroscope samples, GNSS status, power/permission changes, and user pause events. Raw callbacks are timestamped with `elapsedRealtimeNanos` plus wall-clock capture time. Arrival order is never trusted. No inference fabricates a route through a gap or turns an uncertain posterior into a certain label.

Quality filter: reject from modelling but retain with flags a fix that is mock (release build), >120 s stale, has accuracy >200 m, has impossible coordinate bounds, or implies >70 m/s between high-quality adjacent fixes without a matching long gap. Imported points are processed separately and never joined to live journeys.

## 2. Sampling state machine

| State | Entry evidence | Location request | Sensors and exit |
|---|---|---|---|
| `OFF` | tracking intent disabled | none | transition to `BLOCKED`/`STATIONARY` on enable |
| `BLOCKED` | required permission/provider/precision missing | passive health check only | recheck on lifecycle, broadcast, and user action |
| `STATIONARY` | stationary AR, low motion and no displacement for 3 min | balanced, 10 min interval, 15 min max delay | 5 s IMU probe every 5 min; exit on AR movement, geofence exit, or >50 m displacement |
| `PROBING` | evidence of movement or low-confidence stationary | high accuracy, 15 s interval | 50 Hz IMU for 30 s; enter `MOVING` with two agreeing signals, otherwise `STATIONARY` |
| `MOVING` | probe confirmed movement | high accuracy 2 s, min distance 5 m, max delay 10 s | 50 Hz IMU, AR transitions; go `STOP_PENDING` after 30 s low movement |
| `STOP_PENDING` | potential stop | balanced 10 s, max delay 30 s | maintain sensors; back to moving on displacement; become stationary/visit after rule thresholds |
| `NO_FIX` | expected high-quality fixes absent for 60 s | high accuracy, 10 s interval | keep IMU/AR; return on valid fix; create cause-bounded gap after 5 min |
| `PAUSED` | active user pause | no requests | exact pause interval is recorded; resume enters `PROBING` |

`SamplingDecision` changes requests idempotently. It never starts a foreground service. The service may start automatically only from a currently valid Android exemption *and* with `ACCESS_BACKGROUND_LOCATION`; otherwise it records a health event and posts a tap-to-resume notification. A recorder restart always begins in `PROBING`.

## 3. Ingestion and liveness

`ObservationIngestor` appends a batch every 10 s or 20 observations, on state change, and in `onDestroy`. If Room is temporarily unavailable, it writes an encrypted app-private journal with monotonic sequence numbers; journal replay deduplicates by observation ID before the next normal insert. A failure to write either store emits critical `RAW_WRITE_FAILED` and stops claiming `RECORDING`.

The recorder extends the active liveness span every 60 s. A gap is attributed using the strongest evidence in this order: explicit pause; permission/provider change; battery-saver event; absence of liveness; `NO_FIX`; late recorder start; otherwise unknown. A gap remains a GAP segment even if later points make a straight connector visually tempting.

### 3.6 Research Capture (debug-only)

Research Capture is an explicit labelled session for creating fixtures. It writes raw IMU samples and all received fixes to an encrypted app-private capture file, tagged with user-selected mode and start/end time; it does not add a production database table, run automatically, or ship in release builds. Stopping it finalises and validates the file. Export requires the transformed-fixture policy in TEST_STRATEGY §3.4; private capture files are excluded from backup by default and deleted by full reset.

## 4. Epochs and features

Moving time is divided into non-overlapping 15 s epochs anchored to UTC multiples. An epoch has at least one usable location feature or a motion/AR feature; otherwise it is `UNKNOWN` with quality flags. Features are versioned, normalised by the parameter bundle, and stored only in the derived `feature_json` for explainability.

| Source | Features |
|---|---|
| GPS/network fixes | median/95th speed, acceleration, heading-change rate, stop fraction, displacement, median accuracy, fix density |
| IMU windows | acceleration RMS/variance, dominant frequency, step-like energy, gyro RMS, sample completeness |
| Activity recognition | probability mass for walking/running/on-bicycle/in-vehicle/on-foot/still and transition recency |
| Context | time since movement began, GNSS availability, tunnel/no-fix flag; no address, contacts, calendar, or network lookup |

## 5. Baseline classifier and HMM

The P0 classifier is an interpretable log-likelihood model: per-mode capped feature likelihoods plus priors yield logits for `STATIONARY`, `WALK`, `RUN`, `BICYCLE`, `MOTORCYCLE`, `CAR`, `BUS`, `TRAIN`, and `UNKNOWN`. Auto-rickshaw, metro, other motorised, and flight are never model outputs; user corrections may set them. Car, bus and motorcycle must not be represented by a single `VEHICLE` code.

Posterior = softmax(logits), calibrated on held-out fixtures using temperature scaling. Constrained HMM smoothing uses log-space forward-backward and Viterbi: self-transition prior 0.94 per epoch; WALK↔RUN and CAR↔MOTORCYCLE are less penalised than WALK↔TRAIN; direct STATIONARY↔TRAIN is disallowed unless a gap boundary exists. Parameters, transition matrix and model version are bundled as immutable JSON and tested with a golden checksum.

Output rules: highest calibrated posterior ≥0.80 is high, 0.55–0.799 medium, lower is low. At low confidence, choose `UNKNOWN` unless the top two labels share a user-visible ambiguity copy (for example “Car or bus?”). HMM smoothing changes a visible mode only if its marginal gain is at least 0.08, preventing flicker.

## 6. Segmentation and journeys

1. Filter and time-sort eligible observations; smooth only within contiguous high-quality runs using a constant-velocity Kalman filter, then use original timestamps for metrics.
2. Detect candidate stays when points remain within max(35 m, median accuracy) for 30 s. A 30 s–3 min halt is pause time inside a MOVE segment. A 3–10 min halt is `STOP`; a ≥10 min halt, or ≥3 min at a confirmed place, is a `VISIT`. A 3–10 min halt between modal runs is `TRANSFER`.
3. Split movement at confirmed visits, explicit user split, or a non-mergeable GAP. A journey is confirmed at ≥120 m displacement or ≥3 min moving time; smaller movements are discarded from derived history only.
4. Convert Viterbi runs to MOVE segments; merge same-mode neighbours separated by a pause <180 s. Preserve every missing interval as one GAP segment with a cause. Consecutive segments tile the journey without overlaps.
5. Segment distance is the sum of accepted adjacent geodesic lengths after spike rejection; time is interval duration. Stopped time is pause/stop time assigned inside the segment; GAP time is not moving or stopped time.

An interruption may be merged only when its gap is ≤15 min, endpoint separation is ≤250 m, the last/next modes are compatible, and no visit threshold is met. The merged result retains the GAP segment and its cause. No road-network map matching occurs in v1.

## 7. Corrections and reprocessing

A correction expands processing to the prior and next VISIT, or 24 h on either side where no anchor exists. It is applied to a fresh reconstruction in the precedence defined in DATA_MODEL §5, then written atomically. `SET_MODE` snaps requested bounds to epoch boundaries and preserves user-selected mode through all future model versions. `MOVE_BOUNDARY` is valid only within the adjacent 15-second uncertainty windows. `MERGE` is valid for adjacent journeys separated by ≤24 h; `SPLIT` requires a timestamp strictly inside a journey. Hard purge removes the source interval and reprocesses only remaining boundaries.

## 8. Evaluation and personalisation

Fixture evaluation reports per-mode precision, recall, F1, calibrated expected calibration error, transition median absolute error, journey boundary F1 (within 60 s), distance error, gap-attribution accuracy, and battery/CPU cost separately. P0 targets are not release claims: overall macro F1 ≥0.75 on transformed held-out tracks; walk/run F1 ≥0.85; calibrated ECE ≤0.08; no regression on the fixed release fixture set.

P2 personalisation may fit a copy of baseline parameters only from ≥30 user-corrected epochs per candidate mode, using time-grouped 80/20 split. Adopt only if held-out macro F1 improves ≥0.03 and no mode recall drops >0.03; retain baseline and show Reset if not. No raw data leaves the device.
