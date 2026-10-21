# IDM — Download protocols and media coverage (2026-10-22)

**This is a traceability matrix for existing P0–P9, M00–M11 and N01–N26**, not a new roadmap, a license to replace the codebase, or a claim of completed features. It translates the user's explicit requirement that the application should go beyond classic IDM, while still preserving the base Windows downloader and the separate Torrent engine demanded by **P7-11**.

## Working rule

Different download content is not one protocol: HTTP file, segmented HTTP, cookies/password-protected HTTP, HLS, MPEG-DASH, authenticated FTP, SFTP and BitTorrent each need actual engine, validation and progress states. A supported file extension alone is insufficient. Keep the user-facing New URL router accurate: never make OK enabled for a scheme without a functioning DownloadJob/engine and appropriate credential handling.

| Feature / protocol | Existing status and reference | Requirement / future acceptance |
| --- | --- | --- |
| HTTP/HTTPS files, Range, multipart resume | Existing `HttpDownloader`; regression evidence E027–E038, 108/108 on SHA `4eaaea47...`, live network environment outstanding | P2, M02/M05, HTTP test fixture, partial/ETag changes, proxy/cert/cookies and WAN |
| HLS streams, segment resume | Existing `HLSDownloader`, prior intermittent HLS test analyzed; 3 repeat successes E035 | P7-01, M02/M03, C04/C06/C07, encrypted/authenticated playlists and master/sub playlists |
| FTP binary transfer | Commons Net transport + loopback tests E039: 112/112; **not yet a user-facing FTP task** | M04, N07–N12, dedicated item/job registry and secure auth, queue, user UI, LAN/WAN |
| FTPS | Commons Net has FTPSClient but current code uses unencrypted FTPClient only | Security/use-case review then isolated FTPS tests, certificate/hostname validation, UI scheme and connection settings |
| SFTP | Distinct SSH file transfer protocol; FTP libraries are not SFTP | Evaluate a maintained SSH/SFTP Java library (e.g. SSHJ, Apache MINA sshd) with host-key verification; no silent FTP substitution |
| HLS/MPEG-DASH and media site extraction | Existing P7-01 and yt-dlp integration workstreams; coverage must be tested per site and stream | Quality/audio/subtitle/playlists where available, verified yt-dlp binary, ffmpeg/remux and repeatable fixtures; exclude bypassing DRM |
| BitTorrent (.torrent and magnet) | P7-11 **not implemented**; source evaluation in `docs/TORRENT_P7-11_ENGINE_DECISION_2026-10-22.md` | Standalone native/desktop module with independent DB/settings/UI, DHT/Peers/Trackers/Seeding, gated test T01–T07 |
| Metalink / mirror selection | Not independently verified | Investigate separately in M10; integrate multi-source checksums only if real download engine/test evidence |
| Browser integration / context capture | Existing separate repository preserved, M03/M11 security/real browser checks still open | Cookies/headers, secure Native Messaging, context menu/download button, Edge/Chrome supported versions |
| Batch/scheduled downloads, queues | Existing code and P2/P4 capabilities under regression | M02/M08/M10; concurrency, persistence and UX evidence, no duplicate jobs |
| Arbitrary protected/DRM content | **Not promised** | Do not claim unrestricted support; require valid authorized URLs/accounts and supported protocols, observe DRM and site limits |

## Design constraints

1. The regular HTTP/HLS/FTP engine, downloading files at host/server links, remains the application's main system. Progress waves, speed graphs and part tables reflect real monitor state.
2. Torrent has isolated session/state and upload/seeding controls; never silently join its DB with ordinary downloader JSON or task IDs.
3. No untrusted URI or downloaded file can auto-execute; validate URLs/paths/redirects, apply least privilege and correct credentials, and avoid exposing secrets in logs/artifacts.
4. Every new protocol gets a real loopback fixture (including file hash) **before** Add URL accepts it; separately validate actual WAN. No unsupported claims from sample screenshots, a disabled button or a compiled dependency.
5. Package one Windows EXE after all code/third-party DLL disclosures and clean installer tests; no separate portable archive bundled by default.

**Current execution sequence:** resolve M04 full FTP integration (not button-only), continue M03/M05 and the UI/config acceptance gates M06–M09, then open independent P7-11/Torrent in M10 without erasing previous components. Record results in the original execution plan Evidence table after each verified gate.
