# M04 FTP authentication — secure Windows credential storage review

**Status (2026-10-22):** WinCred native adapter and an opt-in FTP credential provider are implemented, and Windows CI XML verifies native synthetic-credential tests at SHA `56719e3dfe60` (Run `39702021922`: 129/129 tests across all suites). The production UI and downloader registry still deliberately do **not** expose FTP; `AnonymousFtpCredentialsProvider` remains the default. Further restart/integration tests and credential UI safety gates must pass before activation.

## Why this exists

- FTP passwords MUST NOT be written to the DownloadItem JSON, FTP resume sidecars, Git source, app telemetry, screenshots, or logs. An in-memory test provider exists solely to exercise synthetic local FTP integration.
- Preserve the actual DownloadItem/Job/Queue/Monitor architecture, Windows 11 x64 first, and the prior working HTTP/HLS serializers. Add a **nullable opaque credential reference** to the FTP model only after migration/backward-compatibility/backup tests.
- FTP protocol itself sends credentials and file contents unencrypted: Windows secure-at-rest secrets do NOT make the network connection secure. Do not silently change this claim or imply FTP == FTPS/SFTP.

## Open-source-first recommendation

Use Microsoft **Windows Credential Manager**, generic credentials scoped to the current Windows user, with native `CredWriteW`, `CredReadW`, `CredDeleteW`, `CredFree` via maintained Java Native Access/JNA Platform, rather than implementing home-grown reversible encryption or storing FTP passwords in JSON. Inspect the exact Java wrapper methods/API signatures and transitive dependency version before adding it.

- JNA upstream: https://github.com/java-native-access/jna — dual-licensed Apache-2.0 OR LGPL-2.1-or-later; choose/document Apache-2.0. No additional native engine should be shipped before clean Windows packaging verification.
- Microsoft WinCred CREDENTIALW: https://learn.microsoft.com/en-us/windows/win32/api/wincred/ns-wincred-credentialw
- Microsoft CredWriteW: https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credwritew
- Microsoft CredReadW: https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credreadw
- Credential persistence: prefer `CRED_PERSIST_LOCAL_MACHINE` (per-user, same computer) when available; check OS/session policy and handle rejected permissions without storing a fallback plaintext file. Never select `CRED_PERSIST_ENTERPRISE` by default because credentials may roam.

## Updated upstream vetting — 2026-10-22

- **Avoid reimplementing secret cryptography.** Verified the existing [Microsoft credential-secure-storage-for-java](https://github.com/microsoft/credential-secure-storage-for-java) library and its Maven Central release `com.microsoft:credential-secure-storage:1.0.3` (MIT), but Microsoft **archived the repository 2025-03-07**. Do not automatically adopt an unmaintained secret-store adapter in a new security-critical Windows build.
- Preferred maintained primitive remains [JNA Platform](https://github.com/java-native-access/jna), available as `net.java.dev.jna:jna-platform:5.19.1` (Apache-2.0 OR LGPL-2.1 option), binding Microsoft's official `CredWriteW/CredReadW/CredDeleteW/CredFree`. Reference implementations from the original Microsoft VSTS Java library can be *reviewed* under its MIT terms, not copied blindly.
- All native probes must run with the **actual Windows runner current-user logon session**; no fabricated success based on Linux/JVM compilation. Treat WinCred read-missing and permissions as distinct failures with no plaintext fallback. Do not log the target reference or credential blobs.
- This source review **does not satisfy or waive** the Windows write/read/delete and secret-redaction test gates below, and it does not activate the FTP UI.

## Design/API contract

- `FtpCredentialsProvider.resolve(item)` returns a **transient** login (already implemented in `FtpCredentialsProvider.kt`); production's anonymous-only fallback is intentionally strict.
- `FtpDownloadItem.credentialRef: String?` is a random opaque ID, never a reversible password. Map `application namespace + credentialReference` to `CRED_TYPE_GENERIC`; do not use bare URL containing user info as a target.
- Username may persist in the FTP item. Password should be read from WinCred **only on resume**; handle credential deletion, Windows logoff, auth 530, profile mismatch, and interrupted updates by a clear UI state rather than infinite retries.
- On password changes, write new WinCred entry before updating the item reference; if item update fails, garbage-collect orphaned reference safely. On item deletion, do not delete a shared credential still needed by another task.
- Avoid logging WinCred structures or errors that contain secrets, minimize secret lifetime in RAM, wipe native buffers where possible; no embedded API keys or test user credentials beyond local synthetic fixtures.
- Do not silently offer authenticated FTP to arbitrary remote servers as a 'secure' connection; communicate plain FTP risks, allow FTPS/SFTP only through distinct, verified transports.

## Required proof before production enablement

1. Verify JNA Platform `WinCred`/Advapi32 native API mappings on clean Windows runner and exact library licenses/artifact hashes.
2. WinCred write/read/delete round-trip using **synthetic credentials** and `CRED_PERSIST_LOCAL_MACHINE`; assert target isolation, Unicode user and password handling, permission failures, profile/logoff behavior.
3. No password or native blob in persisted JSON, sidecar, ZIP package contents, logs, Git or crash diagnostics (redacted test).
4. Normal FTP error 530 and missing references produce actionable UI errors; no freeze, infinite retry or secret leak. Anonymous login still works with no WinCred entry.
5. End-to-end Add URL / Pause / Resume / Restart / Queue / Stop All with in-memory local FTP server and protected credential provider; preserve existing HTTP/HLS regression suites.
6. Windows clean installation of one EXE, live LAN and WAN regression where environment permits; record source SHAs and actual test XML evidence in master plan, not optimistic checkboxes.

**Until all gates are verified:** do not register FTP in the live UI or claim authenticated FTP ready.

## Verified implementation versus remaining gates (2026-10-22)

- The app reuses its existing JNA Platform dependency for a current-user Windows generic credential vault. The native `CREDENTIALW` structure was made reflectively visible after Windows XML revealed a JNA access error; no plaintext fallback was introduced.
- Native tests in [Run 39702021922](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/39702021922) passed on a Windows runner: 129 total across the current suites (0 failures/errors/skips), including 3 WindowsFtpCredentialVaultTest cases.
- The persisted FTP DTO accepts an optional UUID `credentialRef` and retains old-JSON compatibility. The secret is resolved only at download time by the unregistered `WindowsCredentialFtpProvider`.
- The native vault test does **not** prove GUI prompts, actual disk-process restart, Windows logoff/profile behavior, secure reference garbage collection, external FTP or secure transport; newer serialized-task and local FTP end-to-end tests are staged and await their own CI.
- FTP remains explicitly **unencrypted** at the protocol layer. Do not confuse OS-protected password storage with TLS-protected credentials in transit; verified FTPS/SFTP would require separate transports.
