# Engineering Contracts

Authoritative for: code-level types, interfaces, errors, concurrency, ownership, and implementation rules. This document deliberately names contracts without implementing them.

## 1. Kotlin conventions

Kotlin 2.1+, explicit public API in pure modules, immutable `data class` values, sealed interfaces for closed sets, `Instant`/`Duration` at boundaries, and UTC `Long` only at SQLite/Android boundaries. Public functions document thread and failure contract. No nullable boolean, no magic numeric mode/status, no platform Android type in JVM modules, no UI formatter in engine modules.

## 2. Shared model contracts

```kotlin
@JvmInline value class JourneyId(val bytes: ByteArray)
data class TimeRange(val fromUtcMs: Long, val toUtcMs: Long) { /* from < to, end exclusive */ }
data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyM: Float?, val timeUtcMs: Long)
sealed interface TransportMode { val code: String } // codes in DATA_MODEL §2
data class SegmentView(val id: SegmentId, val range: TimeRange, val kind: SegmentKind,
  val mode: TransportMode?, val source: ModeSource?, val posterior: Float?, val metrics: SegmentMetrics)
sealed interface RecorderEvent { data class Flushed(...); data class StateChanged(...); data class SessionRecovered(...) }
sealed interface ProcessingCommand { data class RefreshOpenJourney(...); data class ReprocessRange(...); data class ApplyCorrection(...) }
```

Identifiers are opaque; callers compare equality only. Float posterior is finite in `[0f,1f]`; all domain constructors validate bounds. `TimeRange` cannot cross unvalidated order. Any JSON crossing storage/export uses `@Serializable`, explicit `formatVersion`, and unknown-field tolerance only where forward compatibility is designed.

## 3. Result and error model

```kotlin
sealed interface AtlasResult<out T> { data class Ok<T>(val value: T): AtlasResult<T>; data class Err(val error: AtlasError): AtlasResult<Nothing> }
sealed interface AtlasError { val code: String; val recoverability: Recoverability }
```

Required error families: `PermissionMissing`, `ProviderDisabled`, `ForegroundStartDenied`, `StorageFull`, `RawWriteFailed`, `DatabaseUnavailable`, `InvalidInput`, `NotFound`, `Conflict`, `ExportFailed`, `ImportInvalid`, `BackupAuthenticationFailed`, `MigrationFailed`, `NetworkUnavailable`, `Cancelled`, `Unsupported`. UI maps codes to user text; repositories never emit literal UI strings. Cancellation is rethrown as `CancellationException`, not converted to error. Programmer bugs throw and are caught only at app boundary for redacted local crash record.

## 4. Ports

| Interface | Owner | Contract |
|---|---|---|
| `LocationSource` | tracking | `Flow<LocationObservation>`; starts/stops only from recorder dispatcher; delivers raw, may be out of order |
| `ActivitySource`, `SensorSource`, `GnssSource` | tracking | callback/flow adapters; no persistence and no inference |
| `TrackingController` | core:data facade | `StateFlow<TrackingHealth>`; enable, disable, pause, resume; serialises intent changes |
| `RawRepository` | core:data | append-only batch insert; range reads sorted by event time; purge only through `PurgeRepository` |
| `DerivedRepository` | core:data | reads plus `replaceRange` callable only by processing module |
| `CorrectionRepository` | core:data | append correction then enqueue processing; never updates a correction |
| `ReconstructionEngine` | engine:reconstruction | pure `reconstruct(input: ReconstructionInput): ReconstructionOutput` |
| `Classifier` | engine:classification | pure `classify(epochs, parameters): ClassificationResult` |
| `AnalyticsEngine` | engine:analytics | pure aggregates from confirmed journeys/segments |
| `MapRepository` | core:map | local style/cache/offline-region state only |
| `TransferRepository` | io:transfer | create/cancel/query user-visible jobs; all URI access scoped and temporary |

Flows are cold unless explicitly documented as shared. Repository reads emit `StateFlow`/`Flow`; a data write returns only after durable transaction completion. Android callback adapters use `trySend` and expose overflow count as health evidence.

## 5. Processing contract

`ProcessingCoordinator.submit(command)` is non-blocking and coalesces overlapping tail refreshes but never drops corrections, purge boundaries or full-reprocess checkpoints. It is the only writer to `classification_epoch`, `stay`, `journey`, `segment`, `geometry_cache`, `daily_aggregate`, and `processing_run`. Commands execute on one limited-parallelism dispatcher; reads can occur concurrently from Room snapshots. `replaceRange` validates output tiling, FK ownership, correction precedence and aggregate dates before commit.

All UI `ViewModel`s consume immutable screen models created by use cases; they never query a DAO or hold a MapLibre object. Callback thread → channel → recorder dispatcher → database/processing is the only legal tracking path. No `GlobalScope`, blocking I/O on Main, direct database access from feature modules, or mutable singleton state.

## 6. Navigation and screen contract

Routes are `@Serializable` typed objects containing IDs/date only, never labels, coordinates, full models, URI grants or secrets. Deep links are internal only. ViewModels accept `SavedStateHandle`, load by ID, expose `StateFlow<ScreenState>`, and model `Loading`, `Content`, `Empty`, `Blocked`, and `Failure` distinctly. One-off messages use event flows with stable action IDs.

## 7. Format contracts

GPX: 1.1 UTF-8, `<trkseg>` split at gaps, `<time>` UTC ISO-8601, Atlas extensions namespaced `https://atlas.app/schema/1` for mode/confidence/cause. CSV: RFC 4180 UTF-8 with header and ISO UTC timestamp. GeoJSON: RFC 7946 `FeatureCollection`, WGS84, segment properties. Every export has a manifest sidecar/metadata entry. Backup reader supports current and previous two format versions; import rejects unknown required versions before writes.

## 8. Observability contract

`AtlasLog` accepts event code plus typed redacted attributes only (`count`, `durationMs`, `state`, `errorCode`). It refuses coordinate, URI, text-note and passphrase types at compile-time wrapper level. Health events are stable enums with optional small redacted JSON schema. Debug fixture injection is impossible in release via build-time source-set separation and a release test.

## 9. Dependency and build contract

Only `:app` binds Hilt implementations. Core JVM modules have no Android dependency and may use constructor injection. New dependency requires licence, size, network, 16 KB native-binary, privacy and maintenance review in an ADR. Version changes update `libs.versions.toml`, lockfile/version audit, and `STATUS.md`. Build is reproducible with Gradle wrapper and version catalog; releases fail if dependency guard violates ARCHITECTURE §4.

## 10. Data and compatibility rules

Schema/code changes require migration, fixture upgrade, export compatibility test and an ADR if semantics change. Parameter/model changes require immutable version, golden fixture comparison, and a reprocess plan. A correction anchors time range and survives both. Never remove enum code, change time inclusivity, reinterpret accuracy, or silently recalculate historical user corrections.

## 11. Definition of a complete code change

One work item changes only owned files, builds, passes listed unit/instrumentation/screenshot tests, updates documentation/status, contains no secrets/private tracks, and includes a migration/rollback explanation where persistence changes. PR description names requirement IDs, work item IDs, tests, and known limitations. Review rejects unrelated formatting churn.

## 12. File ownership

| Area | Owner work items | May edit |
|---|---|---|
| build convention, root, CI | WI-001, WI-002 | Integrator only |
| model/geo/common/testing | WI-003–006 | Core lane |
| database/datastore/data | WI-007–012 | Data lane |
| tracking/sampling/features | WI-020–027 | Tracking lane |
| classification/reconstruction/processing | WI-030–039 | Intelligence lane |
| designsystem/map/features | WI-050–069 | Experience lane |
| formats/transfer/privacy | WI-070–079 | Data ownership lane |
| fixtures/test/benchmark | WI-040–049, WI-080–094 | Quality lane |
| docs/STATUS.md | assigned work-item owner | status entry only; Director owns spec changes |

Cross-owner changes require an integration issue naming both owners and interface test before merge. `docs/` specifications are read-only to implementation models except `STATUS.md` and an approved blueprint-defect entry.
