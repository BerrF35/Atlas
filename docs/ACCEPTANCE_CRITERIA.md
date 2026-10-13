# Acceptance Criteria

Authoritative for: objective product acceptance and traceability. “Pass” means observed evidence, not a code assertion alone.

## 1. Traceability matrix

| Requirement group | Primary work items | Required evidence |
|---|---|---|
| PRD-TRK-01–08 | WI-009–012, WI-027, WI-090 | lifecycle fault tests, DT-01/03–07, diagnostics screenshot |
| PRD-CLS-01–09 | WI-020–021, WI-040–041 | F02–F05 report, model checksum, uncertainty UI |
| PRD-REC-01–09 | WI-022–024 | F05/F07/F10, atomic reprocess benchmark |
| PRD-MAP-01–11 | WI-051, WI-053–055 | map layer/offline/playback screenshots, attribution and cache tests |
| PRD-ANL-01–09 | WI-025, WI-055 | timezone aggregate goldens, chart/table semantics |
| PRD-PLC-01–06 | WI-026, WI-056 | clustering/route fixtures and edit tests |
| PRD-DAT-01–08 | WI-070–073 | format validation, SAF cancel, backup/migration/purge evidence |
| PRD-PRV-01–06 | WI-052, WI-056, WI-072, WI-074 | packet capture, log scan, privacy export/lock tests |
| PRD-DIA-01–04 | WI-012, WI-041, WI-057 | gap attribution fixtures and diagnostic redaction |
| NFR-PERF/BAT/STO/REL/A11Y/PRIV | WI-042, WI-080, WI-090–091 | benchmark/device/a11y/network release reports |

## 2. P0 scenario acceptance

| ID | Given / when | Pass criteria |
|---|---|---|
| AC-01 | Fresh install, all required grants and valid settings | recorder notification is visible; health becomes Recording/Resting only after evidence; no coordinate leaves device |
| AC-02 | Precise/background location missing or provider off | health is Blocked, action reaches correct Settings screen, no false route is made |
| AC-03 | Mixed-mode fixture or labelled device run | segments tile journey; bicycle/motorbike remain distinct; low confidence is explicit |
| AC-04 | 10-minute recorder loss between valid fixes | exactly one GAP with evidence-based cause; map/playback do not fabricate path |
| AC-05 | Correct mode/split/merge/delete a ≤2 h journey | preview is clear, correction persists after reprocess, aggregate updates atomically in ≤2 s target [D] |
| AC-06 | Open historical day from 3-year fixture | correct local-day routes/metrics load ≤1.5 s target [D], with coverage/estimated label |
| AC-07 | Tiles unavailable | routes/markers/legend remain usable; plain-canvas notice and attribution reachable |
| AC-08 | Export range including privacy zone, cancel midway | safe transform applies by default; cancel leaves no unlabelled partial file |
| AC-09 | Backup then restore with wrong then right passphrase | wrong passphrase does not alter data; right passphrase stages/validates before replace |
| AC-10 | Purge selected range and full reset | confirmation names scope; all matching local data is gone; user is warned existing external exports remain |
| AC-11 | Diagnostics selected gap | interval, cause, evidence and next action are shown; unknown remains unknown |
| AC-12 | 200% text/TalkBack/reduce motion | essential content/action remains operable and announced; no required information is map-only |

## 3. Negative and honesty cases

- Approximate location, denied notification, battery saver, process kill, force-stop, reboot-before-unlock, full storage, corrupted backup, expired URI permission, malformed GPX, DB migration interruption, and unavailable Play services all create specific error/health evidence and leave raw data intact where physically possible.
- No journey/analytics includes `OPEN`, `CANDIDATE`, `DELETED`, GAP distance, or raw points rejected by quality rules.
- No screen calls an unknown gap “GPS lost” unless that evidence exists; no UI uses ambiguous “bike”.
- No non-allow-listed traffic, external text/URI/coordinates in release logs, analytics SDK, account identifier, remote config, or unconsented reverse geocoding.

## 4. v1.0 exit criteria

All P0 and P1 requirements are mapped to a completed WI with green automated evidence, all TEST_STRATEGY §6 gates pass, open risks are accepted only if not privacy/data-loss/honesty critical, and `STATUS.md` records the reproducible release commit, app/model/schema versions and Nord 5 evidence references. P2 must be absent or visibly disabled, never half-exposed.
