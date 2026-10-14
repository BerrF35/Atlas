# Project Atlas — Engineering Blueprint Index

Status: **Stage 2 implementation complete · Persistent local database, tracking recorder, kinematic classifier, segmentation engine, MapLibre SDK, exports, and UI fully verified and integrated**
Blueprint version: **2.0** (2026-10-13)
Stage 1 UI Handoff: [UI_IMPLEMENTATION_HANDOFF.md](UI_IMPLEMENTATION_HANDOFF.md)
Stage 2 Engine Handoff: [STAGE2_IMPLEMENTATION_HANDOFF.md](STAGE2_IMPLEMENTATION_HANDOFF.md)
Owner: Engineering Director (planning model). Changes to this set follow the process in [MODEL_HANDOFF.md §7](MODEL_HANDOFF.md).

---

## 1. What Atlas is (one paragraph)

Atlas is a native Android application that continuously and automatically records where its owner goes. It reconstructs each day into **stays** (time spent at a location) and **journeys** (movement between stays). Each journey is made of one or more **segments**, and each segment has one transport mode: walk, run, bicycle, motorcycle/scooter, car, auto-rickshaw, bus, train/metro and so on. All data stays on the phone: there is no account, no server and no telemetry. The interface is monochrome and map-first, and it reports honestly what was measured, what was inferred and what is missing.

## 2. Scope of this blueprint

| In scope (v1.0 product) | Explicitly out of scope (v1.0) |
|---|---|
| Continuous background recording on Android 12+ with a primary device of OnePlus Nord 5 (India) | iOS, web, desktop clients |
| On-device transport-mode classification (interpretable baseline + HMM smoothing) | Cloud inference, LLM classification |
| Multimodal journey reconstruction, editing, correction | Social features, sharing feeds |
| MapLibre map with OSM vector tiles, monochrome style | Turn-by-turn navigation |
| Analytics (day → lifetime), places, recurring routes, heatmap | Calorie / CO₂ estimates (rejected, see ADR-014) |
| Export GPX / CSV / GeoJSON / encrypted backup; import of Atlas backups and GPX | Account system, cloud sync, ads, analytics SDKs |
| Diagnostics and tracking-health reporting | Map matching against road network (deferred, see ADR-011) |

## 3. Document map

Read in this order. Each document is **authoritative** for the topics listed; others must cross-reference rather than restate.

| # | Document | Authoritative for |
|---|---|---|
| 0 | [GLOSSARY.md](GLOSSARY.md) | Every domain term (Observation, Stay, Journey, Segment, Gap, …). **Terms used anywhere must match.** |
| 1 | [PRODUCT_SPEC.md](PRODUCT_SPEC.md) | Goals, non-goals, user journeys, functional requirements (`PRD-*` IDs), priorities |
| 2 | [REPOSITORY_AUDIT.md](REPOSITORY_AUDIT.md) | Verified state of `github.com/BerrF35/Atlas` and the local `atlas` folder; integration strategy |
| 3 | [DEVICE_AND_PLATFORM.md](DEVICE_AND_PLATFORM.md) | OnePlus Nord 5 facts, Android 12–16 rules, OxygenOS behaviour, what is guaranteed vs. must be device-tested |
| 4 | [ARCHITECTURE.md](ARCHITECTURE.md) | Tech stack, module graph, runtime processes, threading, data flow |
| 5 | [DATA_MODEL.md](DATA_MODEL.md) | Database schema (tables, columns, indexes), enums and codes, migrations, retention |
| 6 | [TRACKING_AND_CLASSIFICATION.md](TRACKING_AND_CLASSIFICATION.md) | Sampling state machine, sensors, filtering, features, classifier, HMM, segmentation, journey assembly, evaluation |
| 7 | [MAPS_AND_ANALYTICS.md](MAPS_AND_ANALYTICS.md) | Map provider, styles, geometry pipeline, playback, every metric definition and aggregation rule, places & routes algorithms |
| 8 | [UI_UX_SPEC.md](UI_UX_SPEC.md) | Design system tokens, components, every screen, wireframes, microcopy, accessibility |
| 9 | [PRIVACY_AND_SECURITY.md](PRIVACY_AND_SECURITY.md) | Threat model, permissions, data flow, network policy, exports, deletion |
| 10 | [ENGINEERING_CONTRACTS.md](ENGINEERING_CONTRACTS.md) | Kotlin interfaces, shared types, error model, concurrency rules, file ownership, coding standards |
| 11 | [TEST_STRATEGY.md](TEST_STRATEGY.md) | Test architecture, fixture format and catalogue, metrics, device protocol, release gates |
| 12 | [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | Work items `WI-*`, dependencies, critical path, parallel lanes, definitions of done |
| 13 | [ACCEPTANCE_CRITERIA.md](ACCEPTANCE_CRITERIA.md) | Requirement → work item → test traceability matrix, negative cases, known limitations |
| 14 | [MODEL_HANDOFF.md](MODEL_HANDOFF.md) | Instructions for downstream implementation models, assignment order, reporting rules |
| 15 | [RISKS_AND_DECISIONS.md](RISKS_AND_DECISIONS.md) | ADRs, rejected alternatives, assumptions, risks, open questions |
| 16 | [STATUS.md](STATUS.md) | **Single source of truth for completion status** of every work item |
| 17 | [CROSS_DOCUMENT_CONSISTENCY_AUDIT.md](CROSS_DOCUMENT_CONSISTENCY_AUDIT.md) | Final terminology, contract, traceability and validation-limit audit |

The repository-root [AGENTS.md](../AGENTS.md) is a short pointer file so agent tooling discovers these rules automatically.

## 4. Precedence when documents disagree

1. `ENGINEERING_CONTRACTS.md` and `DATA_MODEL.md` (code-level contracts) win over prose elsewhere.
2. `GLOSSARY.md` wins on terminology.
3. `UI_UX_SPEC.md` wins on anything visual or textual in the UI.
4. `RISKS_AND_DECISIONS.md` ADRs record *why*; if an ADR conflicts with a spec, the **newer** dated artefact wins and the conflict must be reported as a defect against the blueprint (see MODEL_HANDOFF §7).

## 5. Evidence markers used throughout

| Marker | Meaning |
|---|---|
| **[V]** | Verified fact with a cited or inspected source |
| **[R]** | Engineering recommendation / decision made by this blueprint |
| **[A]** | Unverified assumption — must not be relied on without the listed validation |
| **[D]** | Hardware- or OEM-dependent behaviour — requires measurement on the OnePlus Nord 5 |

## 6. Instructions to implementation models (summary)

Full rules are in [MODEL_HANDOFF.md](MODEL_HANDOFF.md). The non-negotiables:

1. Do not redesign architecture, schema, enums or visuals. If something is missing or wrong, file a blueprint defect in `STATUS.md` and stop that task.
2. Only edit files owned by your assigned work item (ownership table in ENGINEERING_CONTRACTS §12).
3. Every work item ends with the tests listed in its definition of done passing locally, and with `STATUS.md` updated. Attach the command output as evidence.
4. Raw observations are immutable. Never write code that edits or deletes rows in `observation`, `motion_window`, `activity_sample`, except through the specified purge paths.
5. Never send location data off-device. The only permitted network calls are listed in PRIVACY_AND_SECURITY §4.
6. Bicycle (`BICYCLE`) and motorcycle/scooter (`MOTORCYCLE`) are distinct everywhere: the enum, UI, analytics and exports. The word "bike" is never used alone in UI copy.

## 7. Workspace layout (target)

```
atlas/                      ← local folder; becomes the git working copy of BerrF35/Atlas
├── AGENTS.md               ← pointer for agent tooling
├── LICENSE                 ← from the existing repo (MIT)
├── docs/                   ← this blueprint (planning-only; no source code)
├── gradle/ , build-logic/  ← created by WI-001
├── app/ , core/ , engine/ , feature/ , io/ , tools/ , benchmark/   ← created by work items
└── fixtures/               ← deterministic track fixtures (WI-040)
```

No application source code exists yet. That is intentional: this run produced planning only.
