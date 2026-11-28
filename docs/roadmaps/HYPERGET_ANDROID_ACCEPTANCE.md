# HyperGet Android — continuation and acceptance
Base: 2b82aa6cb0218e13b07e2a343ef98c6dc0c45a2f. Android-first request 2026-11-28. The existing android:app and shared downloader modules are retained, not a replacement app. Windows PRs #65/#66 and main are not changed. The canonical functionality plan remains HYPERGET_FUNCTIONALITY_MASTER_PLAN.md (all F00–F12 and original P/N/T requirements remain tracked); this is the Android adaptation, not a waiver.

## Source baseline
Existing Android screens: home, browser/tabs/bookmarks, single/multi/batch URL addition, edit, categories, queues/schedules, per-host settings, checksum, permissions, updater, background foreground service. Existence is not proof of functional parity. Package ID, shared persistence, signer configuration and original downloader remain unchanged.

## First batch
CODED: full 64-bit notification identities (tag + ID, unique data URI per task); same-app only action receivers; remove unused externally triggerable TODO; private internal progress/error/crash activities; bounded expiring per-tab browser GET metadata cache, consume once, clear on close; case-insensitive cookie/user-agent handling; text shares via CharSequence and text MIME; signing property lookup without requiring local.properties; debug label. New 6-case JUnit cache regression. No full Gradle/device result yet.

## Android release gates
- [ ] Shared engine: actual HTTP/HTTPS range, 206/416, ETag/If-Range, identity-safe resume, retries, pause/stop/cancel, disk full, atomic final file, hash, 1/4/16 active downloads and measured throughput/RAM/CPU. Port only reviewed compatible Windows fixes; no blind merge.
- [ ] FTP/FTPS/SFTP/HLS/DASH/aria2/media support: verify protocol availability, auth, redirect, assembly, resume and legal sources; no automatic torrent enablement without scope decision. Keep all master-plan requirements visible.
- [ ] Android file access: MediaStore/SAF and direct-path compatibility, storage permission, downloads folder, collision/rename/move, >4GiB, Unicode and reboot/recovery, no destructive overwrite.
- [ ] Android background: service lifecycle, notification permission and unique actions at 16 transfers, network switch/metered/Wi-Fi only, process kill, reboot, battery saver/HyperOS, scheduler/time windows. User-initiated service constraints on current Android.
- [ ] Android integration: browser share/open/text/batch, per-tab header isolation, safe WebView deep links, no cross-origin credential leakage, first-run share retention, download-link refresh, bookmarks and collector/packages.
- [ ] IDM/JDownloader functional mapping: all F03–F08 controls/settings, queues, priorities/speed limits, site rules, checksum, progress/details, batch/collector, backup and safe credentials. Windows-only native messaging/Explorer/tray/power controls are platform-specific and require explicit Android equivalent or documented exception, not silent deletion.
- [ ] UI: native adaptive Android phone/tablet portrait/landscape, Arabic RTL, accessibility, smooth 120 Hz where hardware allows, coherent premium HyperGet design. Separate visual work from functional acceptance.
- [ ] Validate Android 13 Xiaomi Pad 5 and supported older/newer API levels; real local fixtures, instrumentation, screenshots and same-SHA evidence. Signed release only with original signer if prior installed app exists; verify package/version/certificate and APK SHA-256; test update without data loss. No fake device evidence, no paid API or unnecessary builds.

## Specific next verification
Run :android:app:testDebugUnitTest and :android:app:assembleDebug for internal validation only; no debug APK as user release. Test distinct notification actions (including IDs with identical hashCode) and cross-app spoofing on device. Check browser same-tab navigation, expiry, two tabs same URL, external first-run sharing, permission flows, and notification service across API versions. Confirm generated manifest and signing environment before release. Source-only changes are not acceptance.
