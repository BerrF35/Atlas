# Privacy and Security

Authoritative for: threat model, permission use, network policy, local protection, export/backup, diagnostics, and deletion.

## 1. Security posture

Atlas contains highly sensitive location history. It is single-user, offline-first, has no identity/account system, and does not claim to protect an unlocked device from its owner. The product promises data minimisation, transparent collection, explicit egress, and recoverable operations; it does not promise uninterrupted Android background execution or invisibility from a device administrator/root attacker.

## 2. Threat model and controls

| Threat | Control | Residual risk |
|---|---|---|
| Lost unlocked phone | optional biometric/device-credential app gate; Android lockscreen; no notification coordinates | recent-app snapshot and unlocked OS remain user responsibility |
| Lost locked phone | credential-encrypted app sandbox, `allowBackup=false`, no cloud backup | rooted/forensic attack outside OS model |
| Malicious network / tracking SDK | no telemetry/ads/crash SDK; manifest/network-security allow-list; CI traffic test | Map tile host sees IP and requested tile areas when online |
| Export shared unintentionally | preview scope, explicit SAF chooser, privacy-zone transform default on, no share history retained | recipient may copy the exported file |
| Diagnostic data leak | redacted default; coordinate inclusion requires a separate deliberate switch | timestamps/device metadata can still be sensitive |
| Public repository secret/data leak | no keys; git checks reject private tracks/backups; transformed fixtures only | contributor error requires review |
| Corrupt database/failed migration | encrypted recovery archive, transactional migrations, restore workflow | abrupt storage failure may lose journal not yet fsynced |

## 3. Permissions and manifest boundary

| Permission/capability | Why | When requested | If denied |
|---|---|---|---|
| precise foreground location | routes/current map | onboarding S02 | Tracking Blocked |
| background location | passive continuous recording | after foreground grant via Settings | Tracking Blocked; history still browsable |
| foreground service/location FGS | declared for recorder | install-time manifest | cannot record |
| activity recognition | lower-power sampling/classification evidence | onboarding after location | degrade to location/IMU; explain |
| notifications | visible recorder/transfer/health notice | onboarding, optional for operation | recorder runs but warns notification hidden |
| biometric | optional app lock only | when enabled | lock stays off |

No contacts, calendar, microphone, camera, Bluetooth, advertising ID, precise alarm, or internet permission is used for product features. `INTERNET` is declared solely for map tiles and is guarded by a network-security configuration: HTTPS only; cleartext false; only the configured tile/style host is acceptable at runtime. Certificate pinning is not used because public tile infrastructure rotates certificates; host changes require a signed app update, not a remote config.

## 4. Network allow-list and validation

| Egress | Allowed | Data | User control |
|---|---|---|---|
| configured OpenFreeMap style/tile HTTPS host | yes | tile coordinates, normal HTTP metadata | map cache/offline controls; host fixed in build |
| Android system permission/settings UI | platform-owned | system-managed | user initiated |
| all analytics, crash reports, geocoding, time sync, update checks, ads | no | none | n/a |

The app code contains no general HTTP client. MapLibre network logs must exclude coordinates beyond tile URLs and never enter Diagnostics. The release test captures device traffic while exercising every screen; any non-allow-listed hostname fails the release gate.

## 5. Local storage and app lock

Location database, journals, backups-in-progress, diagnostics, screenshots and imports live in credential-encrypted internal storage. SAF-selected export destinations are external by user choice and cannot be controlled after write. `android:allowBackup="false"` plus data-extraction rules exclude database, files, cache, and external app-specific files. Android Keystore stores only the app-lock key material; it does not encrypt the primary database in v1 (ADR-012). App lock covers launch/resume after configurable 0/1/5/15 minute timeout and obscures content in recents; tracking service continues while UI is locked.

Logs use `AtlasLog`: release logs contain stable event codes and redacted counts/timestamps, never latitude, longitude, polyline, address, export URI, passphrase or raw diagnostic payload. Crash records omit coordinates and are local-only.

## 6. Export, backup and restore

GPX/CSV/GeoJSON export is user-initiated, foregrounded, cancellable, streamed to a temporary internal file, validated, then copied once to the granted URI. Cancellation/failure deletes the temporary file and attempts to delete destination; if destination deletion fails, UI labels it incomplete. The export manifest states time range, generation time, source (live/import), fields, privacy transform, and Atlas format version.

`.atlasbackup` is an archive with a plaintext non-sensitive header (`formatVersion`, KDF parameters, salt, nonce, ciphertext length) and AES-256-GCM encrypted payload (database logical dump, settings, private assets, manifest). Key derivation is PBKDF2-HMAC-SHA-256, 600,000 iterations, random 16-byte salt; fresh 12-byte nonce per archive; associated data includes format version. Passphrases never enter logs, settings, clipboard, or recovery. Restore validates GCM before altering data, stages to a new database, runs migrations, validates invariants, then atomically swaps after explicit Replace or Import-as-separate-history choice. Wrong passphrase provides no oracle beyond “Could not open backup.”

## 7. Privacy zones and data deletion

A privacy zone is a confirmed place with a circular radius. Safe export clips all route points inside the zone and adds a boundary point displaced to the perimeter; it does not replace them with a fake route. The default export is safe when any zone intersects selection; user may explicitly choose unmodified export after a warning. Screenshot-safe map mode masks interiors and strips place names. In-app history is unmodified so the owner can inspect the truth.

Soft journey deletion hides derived history only. Range purge permanently removes all listed source and derivative rows as specified in DATA_MODEL §7; full reset wipes database, internal journal, cache, diagnostics, map cache and keys, then restarts onboarding. It cannot delete files already written via SAF; the UI says so before confirmation.

## 8. Incident and review requirements

Each release reviews manifest diff, dependency licences/transitive network behaviour, network allow-list test, redacted-log scan, backup cryptography test vectors, and restore/purge tests. A suspected leak triggers: stop affected release distribution, preserve non-sensitive evidence, identify egress/data/scope, rotate or disable the relevant host/configuration in a patched build, and disclose to the owner with known facts only. No remote kill switch exists.
