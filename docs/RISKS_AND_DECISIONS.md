# Risks and Decisions

Authoritative for: architectural decisions, assumptions, open validation, and risk disposition. A decision is superseded only by a newer dated ADR.

## 1. Architecture decision records

| ADR | Decision | Status and rationale |
|---|---|---|
| ADR-001 | Native Kotlin + Compose Android | Accepted. Background tracking requires first-class Android service/permission control. |
| ADR-002 | Android 12 minimum, target 36 | Accepted. Matches support/rules and primary device; test API 34/36. |
| ADR-003 | Raw immutable, derived rebuildable | Accepted. Preserves evidence and enables model upgrades/corrections. |
| ADR-004 | Single app process | Accepted. Avoids Room/process races; separate process does not solve OxygenOS killing. |
| ADR-005 | Fused Location Provider with platform fallback | Accepted. Uses Play services when present without making it a single point of failure. |
| ADR-006 | Interpretable classifier + HMM, no ML runtime | Accepted for v1. Labels/fixtures are insufficient for opaque on-device ML; calibration and correction matter more. |
| ADR-007 | On-device only, no account/telemetry | Accepted. Primary privacy promise; limits sync and remote recovery. |
| ADR-008 | MapLibre + OpenFreeMap-compatible vector tiles | Accepted provisionally. No key, style control and offline API; host terms/availability must be rechecked at WI-051. |
| ADR-009 | IBM Plex bundled fonts | Accepted. Readable mono metrics with no font-network call. |
| ADR-010 | Custom Compose Canvas charts | Accepted. Small metric set; avoids chart dependency and preserves accessibility contract. |
| ADR-011 | No map matching in v1 | Accepted. Road-match service would imply egress/infra and can hide uncertainty. |
| ADR-012 | No SQLCipher primary database in v1 | Accepted with limitation. Android credential encryption + app lock; revisit for threat model change. |
| ADR-013 | No elevation analytics | Accepted. Nord 5 has no barometer; GPS altitude remains export-only. |
| ADR-014 | No calories/CO2 estimates | Accepted. Inputs/assumptions would imply false precision. |
| ADR-015 | Time-interval correction anchors | Accepted. Derived IDs change on reprocess; time ranges survive. |
| ADR-016 | Explicit gap as segment | Accepted. Honesty beats visually continuous routes. |

## 2. Active risks

| ID | Risk | Likelihood / impact | Mitigation and exit evidence | Owner |
|---|---|---|---|---|
| R-01 | OxygenOS kills recorder despite FGS | high / high | onboarding, liveness, supported restart paths, DT-01/04/07 | Tracking |
| R-02 | Android background-start rule blocks automatic restart | high / high | strict start matrix, notification fallback, API 34/36 tests | Tracking |
| R-03 | mode quality insufficient for Indian mixed transport | high / medium | uncertainty, corrections, labelled transforms, F02–F05/DT-02 | Intelligence |
| R-04 | tile host availability/terms change | medium / medium | no functional dependency; blank canvas fallback; review before release | Maps |
| R-05 | public repository receives personal location data | medium / high | ignore/CI guard/provenance review | Integrator |
| R-06 | battery target missed | medium / high | state measurement, adapt policy only with fixture regression check | Tracking |
| R-07 | database/storage pressure corrupts or loses evidence | low / high | journal, transaction/fault tests, retention preview, recovery archive | Data |
| R-08 | backup passphrase loss | medium / medium | explicit irreversible warning; no recovery claim | Data ownership |
| R-09 | Android/MapLibre dependency drift | medium / medium | pin at WI-001; compatibility/native binary CI | Integrator |
| R-10 | UI becomes generic/over-carded during implementation | medium / medium | screenshot review against UI_UX and component ownership | Experience |

## 3. Assumptions requiring validation

| ID | Assumption | Validation |
|---|---|---|
| A-01 | owner’s Nord 5 has Play services and installed OxygenOS version supports stated paths | WI-090 runtime/device capture |
| A-02 | 50 Hz sensor samples are delivered screen-off while FGS active | DT-03 |
| A-03 | background location plus a valid exemption permits intended restart paths on current Android/OxygenOS | API tests + DT-04/07 |
| A-04 | MapLibre chosen release is 16 KB aligned and offline API meets product needs | WI-001/WI-051 CI and manual test |
| A-05 | 250 MB/year and battery budgets are reachable | WI-042/DT-01/02 |

## 4. Research evidence and uncertainty

Planning was checked against current primary Android guidance on background FGS restrictions, Android 16 job quota behaviour, and MapLibre Android/offline APIs on 2026-10-13. These sources support the explicit restart/start restriction and quota policy; exact OEM menu wording, radio/sensor behaviour, map-host availability, and device performance remain [D]/[A] until WI-090. Sources: [Android foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [Android FGS changes](https://developer.android.com/develop/background-work/services/fgs/changes), [Android long-running work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running), [MapLibre offline API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html).
