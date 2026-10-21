# IDM — Open-source reuse / FTP integration evidence (2026-10-21)

This is an implementation note, **not** a replacement for the original 82 requirements, the master repair plan, or `docs/Download_Manager_Execution_Plan_v1.md`.

## Source and license policy

- Existing codebase: preserve the original AB Download Manager-based architecture and native browser extension repositories; no cloning unrelated full applications over this one.
- **Chosen FTP production library:** Apache Commons Net `commons-net:commons-net:3.13.0`, Apache 2.0, from Maven Central. Source: https://commons.apache.org/proper/commons-net/ ; API: https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html . Avoid implementing FTP commands and passive socket protocol ourselves.
- **Test-only server:** Apache FtpServer `org.apache.ftpserver:ftpserver-core:1.2.1`, Apache 2.0; https://central.sonatype.com/artifact/org.apache.ftpserver/ftpserver-core . Must not be included in release runtime classpath.
- **Future icon pack candidate:** Lucide SVG icons (ISC, preserve notices, some Feather-derived SVGs have separate MIT notices). Keep two *independently selectable* icon styles in our app with three sizes; imported assets must be converted to safe desktop Compose resources and visually verified, not hot-linked.
- **Media integration:** existing yt-dlp logic should be reused / kept updatable rather than writing site extractors from scratch; yt-dlp's optional FFmpeg + JavaScript runtime dependencies each require separate review, and no DRM bypass. https://github.com/yt-dlp/yt-dlp
- Future libraries must have pinned versions, attribution, security review, reproducible Gradle verification and test evidence. No copy/paste code blindly from GitHub.

## What is implemented in the source now

- `downloader/core/src/desktopMain/kotlin/ir/amirab/downloader/ftp/FtpTransferClient.kt`: JVM blocking FTP transport using Commons Net (production dependency). Auth supplied separately, optional anonymous login, passive/BINARY, SIZE and MDTM, REST on safe partial, streaming to a `.ftp-part` file, persisted resume identity sidecar containing no credentials, prevents stale resume after remote identity change, abort/cancel closes active socket, refuses to overwrite an existing output, requires full expected byte count and successful server completion before atomic final move.
- `downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/ftp/FtpTransferClientTest.kt`: Apache FTP server integration scenarios, real socket/control/data connections, bytes/hash, Resume with REST and preserved partial, changed remote rejection, invalid login/scheme.
- `downloader/core/build.gradle.kts`: production FTP client only in desktopMain; server only in desktopTest. Android base is untouched.

## Non-negotiable status distinction

FTP backend transport in a JVM module **does not yet** mean the app accepts `ftp://` links in its Add URL window or maintains FTP Job state end-to-end. The required `FtpDownloadCredentials/Item/Job/Downloader`, UI LinkChecker/Add/Edit Inputs, downloader registry + JSON serializer registration, queue/limits/retry and Native Integration must be implemented and exercised before turning on URL acceptance. **Do not just remove the disabled OK check.**

Still missing: TLS/FTPS or SFTP (not equivalent to plain FTP), proxy support, URL userinfo migration, explicit credential storage, restart of completed job metadata, performance/concurrent FTP limits, authenticated WAN validation and Windows UI checks. FTP is **unencrypted** by nature; never claim safe credentials on public untrusted networks.

## Verification gates

1. Windows CI compiles test and production dependency; test suite and packaging green; loopback FTP tests verify file SHA and partial handling.
2. Add a type-safe dedicated serializable FTP download item and queue adapter without changing legacy JSON/HTTP/HLS. Back up data first if a migration is later needed.
3. Wire Add URL to FTP backend and only then enable OK; test passive, auth, resume, error UI, start/stop, tray, performance and no leaked permits.
4. LAN + public FTP/HTTPS verification separate from loopback. Keep results labelled by environment; do not claim completion from compile only.

## Stop conditions

Missing or changed remote SIZE/MDTM, failed FTP reply or wrong credentials must return a visible meaningful error and preserve (not silently discard) the local partial. If the remote identifier is unknowable, safe resume is blocked rather than appending possibly unrelated bytes. Avoid embedding passwords in URL/log/snapshot. No user data was touched by the integration test fixture.
