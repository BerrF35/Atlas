# Model Handoff

Authoritative for: how downstream implementation models execute this blueprint without redesigning it.

## 1. Start here

Read [INDEX.md](INDEX.md), [GLOSSARY.md](GLOSSARY.md), this file, the assigned work item in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md), and its acceptance row. Then inspect the actual checkout. The current workspace is planning-only; WI-001 alone creates the application scaffold after preserving the remote repository history and these documents.

## 2. Operating rules

1. Treat the authoritative documents as contracts. Do not substitute libraries, change schema/enums, rename domain terms, redesign UI, or broaden network/data collection for convenience.
2. Implement only the assigned work item and owned files. If a required interface is absent, add a blueprint defect rather than guessing.
3. Never modify immutable raw rows except the hard-purge path. Never route-map-match or infer across a GAP. Never transmit location other than explicit user export or allowed map tiles.
4. Preserve all existing unrelated changes. Do not reset/reformat/rewrite other work items. Use additive commits and run the specified tests before handoff.
5. P0 must work with network unavailable. Do not add an account, server, analytics, remote config, Crashlytics, key, or telemetry.

## 3. Assignment packet

The Director/Integrator gives: `WI-ID`, allowed files/modules, branch, predecessor commit, requirements, expected tests, fixture IDs, and explicit unresolved defects. The agent replies only with factual status, changed files, commands/tests and results, performance/device evidence, and new defects/risks. It does not claim visual/device validation it did not perform.

## 4. Blueprint-defect protocol

File a dated entry in `STATUS.md` with: ID, source document/section, exact conflict or missing decision, impact, minimal options, and blocking status. Do not edit an authoritative spec to self-approve an interpretation. For security/privacy/data-loss or raw-data ambiguity, stop the work item after preserving diagnostics. The Director resolves by editing the authoritative document and recording an ADR when needed.

## 5. Integration protocol

Before merge, the owner supplies requirement/work-item traceability, tests and outputs, migration/rollback statement, privacy impact, screenshot/accessibility evidence for UI, and fixture/metric delta for algorithms. Integrator verifies file ownership, checks no private track/secret, runs clean `check`, and updates `STATUS.md`. Merge order follows IMPLEMENTATION_PLAN §3; conflicts are resolved by the owner of the contract, not by arbitrary code order.

## 6. Reporting template

```
WI-XXX — status: complete | blocked | needs review
Scope: <one sentence>
Changed: <paths>
Evidence: <commands and pass/fail; device/OS if applicable>
Metrics: <before/after or not measured>
Risks/defects: <IDs or none>
```

“Complete” means the full definition of done, not compilation. “Not measured” is valid and preferable to a guessed result.

## 7. Spec change control

Only the Engineering Director changes `INDEX`, glossary, product, platform, architecture, data model, tracking, maps, UI, privacy, contracts, test strategy, implementation plan, acceptance, or decisions. A change updates every impacted reference in the same edit, adds/updates an ADR for material trade-offs, re-runs cross-document audit, and increments blueprint version/date in INDEX. `STATUS.md` is editable by assigned owners for factual progress/defects only.
