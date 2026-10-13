# Data Model

Authoritative for: persistent schema, stable codes, migrations, retention, and deletion. Terms are defined in [GLOSSARY.md](GLOSSARY.md); algorithm inputs are defined in [TRACKING_AND_CLASSIFICATION.md](TRACKING_AND_CLASSIFICATION.md).

## 1. Rules

- Database: one Room database named `atlas.db`, WAL enabled, schema exported on every version. Times are signed UTC epoch milliseconds; display time is derived using the IANA zone stored with the event.
- IDs are 16-byte random values stored as BLOB and exposed as value classes (`ObservationId`, `JourneyId`, etc.). Do not use rowids as public identifiers.
- `observation`, `motion_window`, `activity_sample`, `liveness_span`, and `health_event` are append-only. Derived rows are replaced only by `ProcessingCoordinator` in one transaction. User rows are never replaced by reprocessing.
- Coordinates use WGS84 decimal degrees. Nullable coordinate fields mean unavailable, never `(0,0)`. Distances are integer metres; speeds are metres/second; accuracy is reported one-sigma metres.

## 2. Stable codes and common columns

Codes below are persisted and exported. Append codes only; never renumber or repurpose one. Every table has `created_utc_ms`; derived tables additionally have `processor_version` and `processing_run_id`.

| Type | Codes |
|---|---|
| `TransportMode` | `STATIONARY`, `WALK`, `RUN`, `BICYCLE`, `MOTORCYCLE`, `CAR`, `AUTO_RICKSHAW`, `BUS`, `TRAIN`, `METRO`, `OTHER_MOTORIZED`, `FLIGHT`, `UNKNOWN` |
| `ModeSource` | `MODEL`, `USER`, `IMPORT`, `RULE` |
| `SegmentKind` | `MOVE`, `GAP` |
| `JourneyState` | `OPEN`, `CANDIDATE`, `CONFIRMED`, `DISCARDED`, `DELETED` |
| `StayKind` | `STOP`, `TRANSFER`, `VISIT` |
| `PlaceStatus` | `CANDIDATE`, `CONFIRMED`, `IGNORED`, `DELETED` |
| `GapCause` | `RECORDER_DOWN`, `NO_FIX`, `PERMISSION_BLOCKED`, `LOCATION_OFF`, `USER_PAUSED`, `LATE_START`, `BATTERY_SAVER`, `UNKNOWN` |

`source` is `LIVE` or `IMPORT`; imports never drive tracking-health diagnostics. Any text is UTF-8, max 280 grapheme clusters unless stated otherwise. Booleans are integer 0/1.

## 3. Raw layer

| Table | Required columns | Indexes and constraints |
|---|---|---|
| `tracking_session` | `id`, `started_utc_ms`, `ended_utc_ms?`, `end_reason?`, `app_version`, `device_fingerprint_hash` | `started_utc_ms`; one open session permitted |
| `observation` | `id`, `session_id`, `time_utc_ms`, `lat_e7`, `lon_e7`, `accuracy_cm`, `altitude_cm?`, `speed_mmps?`, `bearing_cdeg?`, `vertical_accuracy_cm?`, `provider`, `is_mock`, `source`, `zone_id` | unique `(source,time_utc_ms,id)`; `(time_utc_ms)`, `(session_id,time_utc_ms)`; `lat_e7 [-900000000,900000000]`, `lon_e7 [-1800000000,1800000000]` |
| `motion_window` | `id`, `session_id`, `start_utc_ms`, `end_utc_ms`, `sample_count`, `accel_rms_mg`, `accel_variance_mg2`, `gyro_rms_mdps?`, `step_like_energy`, `quality_flags` | unique `(session_id,start_utc_ms)`; `(start_utc_ms)` |
| `activity_sample` | `id`, `session_id`, `time_utc_ms`, `kind`, `confidence_0_100?`, `transition`, `source` | `(time_utc_ms)` |
| `liveness_span` | `id`, `session_id`, `from_utc_ms`, `to_utc_ms`, `state`, `last_observation_utc_ms?` | `(from_utc_ms,to_utc_ms)`; `to >= from` |
| `health_event` | `id`, `time_utc_ms`, `type`, `severity`, `detail_code?`, `session_id?`, `redacted_detail_json?` | `(time_utc_ms)`, `(type,time_utc_ms)` |

`provider` is `FLP`, `GPS`, `NETWORK`, `PASSIVE`, or `IMPORT`. `quality_flags` is a documented bitmask: `1=MOCK`, `2=STALE`, `4=LOW_ACCURACY`, `8=OUT_OF_ORDER`, `16=SENSOR_INCOMPLETE`. Invalid or late points are retained with flags and excluded by reconstruction, never dropped.

## 4. Derived layer

| Table | Required columns | Indexes and constraints |
|---|---|---|
| `processing_run` | `id`, `kind`, `started_utc_ms`, `finished_utc_ms?`, `range_from_utc_ms`, `range_to_utc_ms`, `cursor_utc_ms?`, `status`, `processor_version`, `error_code?` | `(status,started_utc_ms)` |
| `classification_epoch` | `id`, `from_utc_ms`, `to_utc_ms`, `mode`, `posterior`, `feature_json`, `model_version`, `is_overridden` | unique `(from_utc_ms,to_utc_ms,processor_version)`; `(from_utc_ms)` |
| `stay` | `id`, `from_utc_ms`, `to_utc_ms`, `kind`, `lat_e7`, `lon_e7`, `radius_m`, `place_id?`, `arrival_accuracy_m?` | `(from_utc_ms,to_utc_ms)`, `(place_id,from_utc_ms)` |
| `journey` | `id`, `from_utc_ms`, `to_utc_ms?`, `state`, `origin_stay_id?`, `destination_stay_id?`, `distance_m`, `moving_duration_ms`, `stopped_duration_ms`, `gap_duration_ms`, `geometry_id?`, `is_deleted`, `deleted_utc_ms?` | `(from_utc_ms)`, `(state,to_utc_ms)`, `to >= from` |
| `segment` | `id`, `journey_id`, `ordinal`, `from_utc_ms`, `to_utc_ms`, `kind`, `mode?`, `mode_source?`, `posterior?`, `distance_m`, `moving_duration_ms`, `stopped_duration_ms`, `gap_cause?`, `geometry_id?`, `boundary_uncertainty_ms` | unique `(journey_id,ordinal)`; `(journey_id,from_utc_ms)`; GAP has null mode/source; MOVE has non-null mode/source |
| `geometry_cache` | `id`, `owner_kind`, `owner_id`, `zoom_bucket`, `polyline6`, `bbox_min_lat_e7`, `bbox_min_lon_e7`, `bbox_max_lat_e7`, `bbox_max_lon_e7`, `point_count`, `is_raw` | unique `(owner_kind,owner_id,zoom_bucket,is_raw)` |
| `daily_aggregate` | `local_date`, `zone_id`, `distance_m`, `journey_count`, `moving_ms`, `stopped_ms`, `gap_ms`, `covered_ms`, `mode_distance_json`, `mode_moving_json`, `uncertain_distance_m`, `updated_utc_ms` | primary key `(local_date,zone_id)` |

The processor deletes and recreates only derived rows whose intervals intersect its expanded `ProcessingRange`; a journey crossing the range is included whole. Geometry is a cache and may be regenerated. A `DELETED` journey remains excluded from aggregates and exports until a hard purge.

## 5. User layer and transfer tables

| Table | Required columns | Behaviour |
|---|---|---|
| `user_correction` | `id`, `kind`, `from_utc_ms`, `to_utc_ms`, `payload_json`, `created_utc_ms`, `supersedes_id?`, `is_active` | Immutable revision record. Kinds: `SET_MODE`, `MOVE_BOUNDARY`, `SPLIT`, `MERGE`, `NOT_A_JOURNEY`, `DELETE_JOURNEY`, `RESTORE_JOURNEY`. |
| `place` | `id`, `name?`, `lat_e7`, `lon_e7`, `radius_m`, `status`, `is_privacy_zone`, `created_utc_ms`, `updated_utc_ms` | Name required for `CONFIRMED`; radius 25–500 m. |
| `route_cluster` | `id`, `origin_place_id`, `destination_place_id`, `status`, `name?`, `fingerprint`, `member_count`, `updated_utc_ms` | only confirmed journeys, minimum three members. |
| `route_member` | `route_cluster_id`, `journey_id`, `similarity`, `joined_utc_ms` | primary key `(route_cluster_id,journey_id)` |
| `settings_snapshot` | `id`, `time_utc_ms`, `settings_schema`, `redacted_json` | audit-only snapshots on material setting changes. |
| `export_job` | `id`, `kind`, `requested_from_utc_ms`, `requested_to_utc_ms`, `status`, `progress_0_100`, `output_uri?`, `started_utc_ms?`, `finished_utc_ms?`, `error_code?` | URI is never logged or backed up as a usable grant. |

Corrections target time intervals, not derived IDs. When overlaps conflict, the most recent active correction wins within its interval; `MERGE` and `SPLIT` are applied before `SET_MODE`; `NOT_A_JOURNEY` dominates all derived movement in its interval. The UI exposes undo by creating a superseding correction, not by mutation.

## 6. Referential integrity and transactions

Foreign keys are enabled with `ON DELETE RESTRICT` for raw evidence and `CASCADE` only within disposable derived children (`journey → segment`, `route_cluster → route_member`). A reprocess transaction writes `processing_run`, removes intersecting derived rows, writes replacement epochs/stays/journeys/segments/geometries, recomputes affected `daily_aggregate` dates, then commits. UI observers therefore see old or new, never an empty middle state.

## 7. Time, location, and privacy invariants

1. Interval end is exclusive: `[from_utc_ms, to_utc_ms)`. Adjacent segments exactly tile a journey.
2. `zone_id` is the device zone at capture; period views group by the zone in effect for each observation, not the zone selected today.
3. A raw hard purge deletes every raw, derived, correction, place assignment, cache, export job and diagnostic record intersecting the selected interval in one transaction, then recomputes the two boundary ranges. It cannot be undone.
4. A privacy-zone flag affects export and screenshot-safe rendering, never in-app storage or displayed route geometry.

## 8. Migration and recovery

- Every schema change has a tested `Migration(n,n+1)`, exported Room schema JSON, migration test starting from the oldest supported schema, and a backup/restore round trip.
- Additive fields first ship nullable or with a safe default; population occurs in a resumable worker. Raw tables are never rebuilt for a derived-feature release.
- Before a destructive migration, create an encrypted, app-private recovery archive and record `MIGRATION_STARTED`. On failure, preserve the old database, surface Recovery in Diagnostics, and allow export/restore; never call destructive fallback migration in release.
- Database corruption: rename the unreadable database and WAL to `atlas-corrupt-<timestamp>` in app-private storage, offer redacted diagnostics and restore, and never upload it.

## 9. Retention and storage budgets

Default is Keep forever. Compaction after N months (3, 6, 12, 24 or 36) replaces eligible raw observations only after a verified geometry cache and export-grade simplified track exist; it preserves journey/segment metrics, health events, and one representative point per 30 s plus all stop/transition points. The user sees the estimated reclaim before confirmation. Original raw data is then unrecoverable except from a backup. Retention never runs while an export, import, reprocess, or recovery is active.

The planning estimate of 250 MB/year at 2 movement hours/day is a target to measure, not a guarantee. `storage_breakdown` is calculated from actual SQLite page counts and app-private map cache, not guessed row sizes.
