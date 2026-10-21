# P7-11 — Independent BitTorrent engine / open-source reuse

**Status:** P7-11 source review and acceptance contracts documented; runtime integration **not started**, not promised as a finished feature. Existing master execution order is authoritative: complete M02/M03/M04/M05 critical transport/restore and M06-M08 UI gates before turning on Torrent in M10. Torrent must not hold up the original HTTP/HLS/FTP release.

## Decision based on existing 82 requirements

The original `docs/Download_Manager_Execution_Plan_v1.md` requirement P7-11 already requires a standalone Torrent module supporting .torrent and magnet, per-file selection, hash validation, pause/resume, seeding, DHT, trackers, peers, speed caps and a distinct UI/settings/storage scope. Preserve it verbatim; do not replace current download manager with a torrent-first application.

### Upstream review (2026-10-22)

| Candidate | Supported strengths | License / concrete risks | Decision |
| --- | --- | --- | --- |
| [libtorrent4j](https://github.com/aldenml/libtorrent4j) + [libtorrent](https://www.libtorrent.org/) | Direct JVM facade and published Windows x64 JNI artifact, mature torrent session/peer/DHT engine; separate engine can share only UI progress abstractions | Wrapper MIT; native engine BSD; native .dll packaging, version & JNI ABI, dependency disclosures, Windows Security/AV signing and x64 loading are mandatory tests | **Preferred implementation candidate, gated by native smoke test** |
| [aria2](https://github.com/aria2/aria2) | Independent executable, JSON-RPC, Torrent/Magnet, Metalink, SFTP/HTTP; convenient fallback or separate worker | GPL-2.0 distribution obligations; external executable/process lifecycle/RPC socket access; combining/bundling must be legally reviewed | **Fallback / comparison baseline only**, not silently bundled |
| [BiglyBT](https://github.com/BiglySoftware/BiglyBT) | Feature-rich mature JVM BitTorrent engine | GPL-2.0 licensing and architecture integration weight | Reference only, not chosen |
| [frostwire-jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent) | Alternative Java wrapper with Windows JNI binary | Native library compatibility, packaging and maintenance parity testing needed | Reserve alternative |

Pinned candidate to test, **NOT yet added to Gradle**: `org.libtorrent4j:libtorrent4j:2.1.0-39` plus `org.libtorrent4j:libtorrent4j-windows:2.1.0-39` (Maven Central: https://central.sonatype.com/artifact/org.libtorrent4j/libtorrent4j-windows). Verify native DLL contents, Windows x64 load and architecture, checksums and full license notices before taking a Gradle dependency. No provider is declared fully approved before those checks.

## Architecture & safety contract (for implementation when M10 is open)

- New independent Gradle module, e.g. `:downloader:torrent`, with an engine-facing interface `TorrentEngine` and opt-in desktop native implementation `LibtorrentTorrentEngine`; **do not reuse HTTP DownloadJob or JSON polymorphic discriminator** to disguise Torrent tasks as HTTP.
- Persistent `TorrentTask` repository isolated from `IDownloadListDb` and HTTP/HLS/FTP queues; migration-free initial rollout with its own `torrent-state` data directory. Atomic backup/recovery/rollback before edits. Use a distinct UI category `Torrent` and task detail tabs: Content, Peers, Trackers, Files, Speed and Seeding.
- Parse magnet/.torrent safely; reject malformed, huge metadata; canonicalize output paths and block `../`, absolute paths, symlink escapes, drive-root traversal, unauthorized file overwrites.
- Configurable download/upload bandwidth, maximum peers/active torrents, port/listen/TCP+UDP settings, DHT/PEX/LSD/private-torrent flag adherence, selective file priority, recheck/reannounce, auto/manual seeding policy, seed ratio/time limits, privacy options.
- Stop/Pause/Exit must flush resume metadata and stop the native session safely. Independent lifecycle, no leaked native threads, no shared mutable HTTP state. No auto-open or auto-execute downloaded files.
- Integrate via a protocol router that explicitly dispatches `http://`, `https://`, `ftp://`, `magnet:?`, `*.torrent`, HLS and DASH to engines **only after** each protocol's real adapter and tests are complete. Accepting file extension is not proof of supported stream type.

## Concrete acceptance gates

T01. Windows x64 native library load, version/ABI + native shutdown test in clean GitHub Windows; DLL hash, license notices, and transitive bill of materials.

T02. Offline-local test fixture .torrent with trusted piece hashes and two loopback peers: start -> pieces written -> SHA/piece verification -> complete; no public trackers needed.

T03. Magnet metadata from loopback only, then choose a subset of files before download; verify selective download and no path traversal.

T04. Pause -> process kill/restart -> resume from persisted native fastresume/bitfield; verify no corrupt data and no duplicate jobs.

T05. Peers/DHT/PEX/Trackers visible with accurate counters; private torrents must honor disabled DHT/PEX/LSD; respect user seed ratio/time and per-torrent/global speed limits.

T06. UI/Tray/Settings/backup operations separate from HTTP/HLS/FTP; a BitTorrent error may not stop ordinary downloads; CPU/RAM and native handle counts measured.

T07. EXE packaging still one installer with bundled required native runtime **only after** attribution and Windows clean-install tests. External process not spawned without a verified reason and permissions.

## Where this sits in the master plan

1. **Now:** M04 FTP transport real loopback fixture and engine integration. Concurrently this RFC documents the already-requested P7-11 so it isn't forgotten.
2. **Then:** complete M02–M09 remaining gates; stabilize ordinary multi-protocol downloads, media, browser integration and real-world tests.
3. **At M10/P7-11:** implement native smoke+isolated engine, persistence and UI, followed by T01–T07; do not pretend that merely importing JNI delivers a Torrent feature.
4. **M11:** package verified release; no untested Torrent binary shipped by default.

Update `docs/Download_Manager_Execution_Plan_v1.md` evidence after each tested step and the checkpoint when appropriate.
