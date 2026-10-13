# Implementation Plan

Authoritative for: implementation sequence, work-item scope, dependencies, and definition of done. This is planning only; no work item is started by this document.

## 1. Sequencing principles

Build the evidence path before the experience path: scaffold → raw capture/diagnostics → deterministic reconstruction → correction/export → polished UI → insight features → release proof. Work items may proceed in parallel only after their interfaces and fixtures exist. An Integrator owns branch integration and never changes specifications to make code fit.

## 2. Work items

| ID | Scope | Depends on | Definition of done |
|---|---|---|---|
| WI-001 | Initialise remote checkout, Android/Gradle scaffold, modules, licence/readme/gitignore | — | repo history preserved; debug app builds; 16 KB check configured |
| WI-002 | CI, convention plugins, static/dependency/privacy guards | WI-001 | PR and nightly workflows pass a smoke fixture |
| WI-003 | core:model/common IDs, time, errors, clocks | WI-001 | contract tests compile from all JVM modules |
| WI-004 | core:geo | WI-003 | geodesy/simplification property tests |
| WI-005 | core:testing fixture/replay harness | WI-003 | F01 reader and deterministic replay |
| WI-006 | DataStore/settings and design-token foundation | WI-001, WI-003 | migration/settings/token snapshots |
| WI-007 | Room schema/DAOs/migrations/journal | WI-003 | raw append/derived atomic transaction/fault tests |
| WI-008 | core:data repositories and health model | WI-007 | flows/error contracts tested |
| WI-009 | manifest/permissions/notification/service shell | WI-001, WI-006 | permission and FGS instrumentation smoke |
| WI-010 | tracking sources, ingestion, liveness/health | WI-007, WI-009 | fake callback/journal/fault tests |
| WI-011 | sampling state machine | WI-003, WI-005 | F01/F08 transition tests |
| WI-012 | diagnostics data queries/coverage strip | WI-008, WI-010 | gap cause and day coverage tests |
| WI-020 | feature epochs and quality filters | WI-004, WI-005 | F02/F06 golden features |
| WI-021 | baseline classifier/HMM/calibration | WI-020 | F02–F05 report gate |
| WI-022 | reconstruction/stay/segment/journey engine | WI-020, WI-021 | tiling/correction golden tests |
| WI-023 | processing coordinator/live-tail/full reprocess | WI-007, WI-022 | atomic/restart/coalescing tests |
| WI-024 | correction repository/undo semantics | WI-008, WI-023 | F10 and ≤2 h benchmark |
| WI-025 | analytics engine/daily aggregates | WI-022 | midnight/timezone aggregate tests |
| WI-026 | places and routes engine | WI-004, WI-022 | clustering/route false-positive tests |
| WI-027 | recorder watchdog/boot/AR restart policy | WI-009, WI-010 | denial and restart-path tests |
| WI-040 | transformed synthetic fixture catalogue | WI-005 | F01–F12 validated, provenance recorded |
| WI-041 | research capture (debug only) | WI-010, WI-040 | release exclusion verified |
| WI-042 | performance harness/macrobenchmark | WI-023, WI-040 | baseline reports archived locally |
| WI-050 | designsystem components/accessibility | WI-006 | token/component snapshots and semantics |
| WI-051 | MapLibre wrapper/styles/attribution/offline map | WI-050 | offline/no-tile and layer tests |
| WI-052 | onboarding S01–S03 | WI-008, WI-009, WI-050 | permission/OEM flow tests |
| WI-053 | Today/Day map/History S04–S06 | WI-012, WI-025, WI-051 | states/screenshots/navigation pass |
| WI-054 | Journey/detail/playback/edit S07–S10 | WI-024, WI-051 | gap playback and edit preview tests |
| WI-055 | Insights S11–S14 | WI-025, WI-026, WI-051 | chart/table/heatmap tests |
| WI-056 | Places S15–S16 | WI-026, WI-051 | candidate/edit/privacy tests |
| WI-057 | Settings/diagnostics/about S17–S25 | WI-012, WI-050 | destructive/state-copy tests |
| WI-070 | GPX/CSV/GeoJSON formats | WI-003, WI-022 | standards/round-trip/privacy tests |
| WI-071 | SAF export/import jobs | WI-070, WI-008 | cancel/partial/URI tests |
| WI-072 | encrypted backup/restore | WI-007, WI-071 | crypto vectors/staged restore tests |
| WI-073 | retention/purge/storage usage | WI-007, WI-025 | boundary/reclaim/no-undo tests |
| WI-074 | app lock/network security/log redaction | WI-009, WI-050 | lock/traffic/log tests |
| WI-080 | accessibility, screenshots, large screens | WI-052–057 | S01–S25 matrices pass |
| WI-090 | Nord 5 device validation/measurement | WI-010–012, WI-042, WI-080 | DT-01–DT-10 evidence and OEM copy update |
| WI-091 | release hardening/signing/provenance | WI-002, WI-070–074, WI-090 | all release gates and reproducible release candidate |

## 3. Critical path and integration

Critical path: WI-001 → WI-003/007/009 → WI-010/011 → WI-020/021/022 → WI-023/024/025 → WI-053/054/070/071 → WI-072/074 → WI-080/090/091. Experience work may start token/onboarding/map shell after WI-006/009, but must consume real repository interfaces before merge. Quality work starts with WI-005 and runs continuously.

Each item has one owner branch `wi/<id>-<slug>`, small commits, no merge commit required, and an integration PR containing requirement IDs, test evidence and doc/status update. Failed device measurement produces an ADR/risk; it does not cause silent threshold editing.

## 4. Definitions of done

All WIs satisfy ENGINEERING_CONTRACTS §11 plus: new screen matches UI_UX screenshots in light/dark/200%; new persistence has migration and deletion effects; new classifier/algorithm updates fixture report; new egress/storage capability passes privacy review; any externally observable contract has negative tests. A work item cannot be marked done because code compiles alone.
