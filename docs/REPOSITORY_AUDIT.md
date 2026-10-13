# Repository Audit

Authoritative for: verified state of the existing repository and the local workspace, and the integration strategy.

## 1. Inspection method

| Source | Method | Time (IST) |
|---|---|---|
| `https://github.com/BerrF35/Atlas` | GitHub REST API: `GET /repos/BerrF35/Atlas`, `GET /repos/.../git/trees/HEAD?recursive=1`, `GET /repos/.../commits`; raw `LICENSE` | 2026-10-13 14:24 |
| Local folder `C:\Users\Admin\Desktop\atlas` | Directory listing | 2026-10-13 14:23 |

## 2. Verified findings [V]

### 2.1 Remote repository

| Property | Value |
|---|---|
| Full name | `BerrF35/Atlas` (public, not a fork, not a template) |
| Description | "personal movement tracker" |
| Default branch | `main` |
| Created / last push | 2026-10-13T08:43:35Z / 2026-10-13T08:43:36Z |
| Commits | 1, "Initial commit" by *Jaijitesh Suryaprakash J*, GitHub-verified signature |
| Tree | **One file: `LICENSE`** (1,081 bytes) |
| License | MIT (SPDX `MIT`) |
| Languages detected | none (`language: null`, size 0) |
| Issues / PRs / releases / wiki content | none |

### 2.2 Local workspace

`C:\Users\Admin\Desktop\atlas` was **empty** before this planning run. It is not a git working copy.

## 3. Assessment

| Question | Answer |
|---|---|
| Reusable application code, utilities, models, assets? | **None.** The repository has no source, build files, documentation or assets. |
| Existing technology stack or dependency versions? | None. There are no constraints to inherit. |
| Existing design decisions? | Only two: the name "Atlas" and the description "personal movement tracker", which match the product vision. |
| Unsuitable assumptions for background tracking? | None present, because no code exists. |
| What can be retained? | `LICENSE` (MIT), the repo name, `main` as the default branch, and the commit history. |
| What must be discarded? | Nothing. |

### 3.1 Implications

1. **Greenfield.** Technology choice is driven purely by the requirements (ARCHITECTURE §2). There is no migration cost from existing code.
2. **License compatibility.** MIT lets us depend on the Apache-2.0 (AndroidX, Kotlin), BSD-2 (MapLibre Native), and OFL-1.1 (IBM Plex fonts) components chosen. OSM data is ODbL, so attribution is required (MAPS_AND_ANALYTICS §1). All third-party notices go in an in-app "Licences" screen (UI_UX S25).
3. **MIT on a public repo means the code is public.** No secrets may ever be committed. There are no API keys by design (ADR-008). Personal fixtures recorded on the owner's device **must not** be committed unless they have been transformed (TEST_STRATEGY §3.4).

## 4. Integration strategy [R]

1. The local `atlas` folder becomes the working copy:
   `git init` → `git remote add origin https://github.com/BerrF35/Atlas.git` → `git fetch` → `git checkout -t origin/main`. This happens in **WI-001**, before any other file is created, so `LICENSE` and history are preserved. The `docs/` folder created during planning must then be committed on top.
2. Branching: trunk-based. Each work item is developed on `wi/<id>-<slug>` and merged into `main` by the Integrator after the gates in MODEL_HANDOFF §5.
3. Repository layout is defined in ARCHITECTURE §4 and INDEX §7.
4. Add `.gitignore` (Android/Gradle/IDE, plus `/fixtures/private/`, `*.atlasbackup`, `local.properties`), `.editorconfig`, and `README.md` (short, pointing to `docs/INDEX.md`) in WI-001.
5. Continuous integration: GitHub Actions workflow `ci.yml` (WI-002) running `./gradlew check` (unit tests, lint, detekt, ktlint), JVM screenshot tests, and assembleDebug on every PR. Instrumentation tests run on a Gradle Managed Device (API 34 and 36 images) in a nightly workflow.

## 5. Risk notes

| Risk | Mitigation |
|---|---|
| The planning docs and the scaffold are written by different models | The scaffold work item (WI-001) is owned by the Integrator, who must not alter `docs/` except `STATUS.md` |
| Personal location data committed to a public repo by accident | `.gitignore` for `fixtures/private/`; a pre-commit check (WI-002) rejects files matching `*.atlastrack` outside `fixtures/synthetic/` and `fixtures/transformed/` |
