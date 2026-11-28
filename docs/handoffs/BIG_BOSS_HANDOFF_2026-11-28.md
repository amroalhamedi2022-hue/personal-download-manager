# HyperGet — Big Boss handoff

Snapshot: 2026-11-28. Documentation only; README/open PR metadata reviewed, not full code audit or fresh CI/device certification.

## Product and constraints
Kotlin/Compose Windows downloader; retain legacy package/data/native-messaging IDs. README describes HTTP(S), queues, segmented recovery, FTP/HLS; aria2 experimental, not production or bundled.

## Observed development checkpoint
PR #66 9e44bfb5e5663b7fdfe7fcf49fe8fe5f01efd9f0 (installed Windows paired performance gate), #65 07f97215f7544d1872de1d09ba2131b17986851a (integrated HTTP/HLS/file safety). Compare ancestry and all open fixes; do not restart from main blindly.

## Authoritative plans and evidence
docs/handoffs/HYPERGET_MAIN_INTEGRATION_CURRENT.md; docs/roadmaps/HYPERGET_FUNCTIONALITY_MASTER_PLAN.md; docs/Download_Manager_Execution_Plan_v1.md; docs/ARIA2_CORE_MIGRATION_PLAN_2026-10-27.md (development branch).

## Execution sequence and acceptance
1. Reconcile #65/#66 and other open PRs; preserve safe destination, partial data, retry/queue state. 2. Run differential throughput and recovery tests vs baseline on Windows; exact installer SHA, DPI/RTL, Explorer multi-file drops and browser integration. 3. Evaluate aria2 behind opt-in adapter/rollback, keep approved eventual torrent scope separate from currently available functions. 4. Ship installer only after measured acceptance.

## Build and test
Windows: .\gradlew.bat --no-daemon :downloader:core:desktopTest :shared:app:desktopTest :desktop:app:compileKotlin ; .\gradlew.bat --no-daemon createReleaseFolderForCi. Actions artifact hyperget-windows-x64 is not a final signed release.

## Resume procedure
Read AGENTS.md if present, original plans, current default branch, open PR heads/base/ancestry, exact SHA Actions and artifacts; compare against this snapshot. Choose the newest compatible cumulative implementation, never overwrite newer changes. Record completed / remaining / blocked with exact commit and test evidence. Work in substantial nonduplicate batches; build at meaningful milestones, not each change. Before publishing, check package/version/signing continuity, licenses, offline/privacy policy and real-device acceptance where applicable. Do not infer completion from PR title or CI alone.

## Private handoff boundary
Owner-only material (keys, passwords, private data, ROMs, personal documents, original restricted assets) stays outside GitHub in access-restricted owner storage. Consult the private Big Boss Drive register. No secret values or private links are included here. If missing, record blocker; do not generate replacement keys for installed apps.

## Cross-project law
A lot of work to reach our goal as perfect as possible and as masterpiece as possible at best time. Master functionality in a masterpiece app. Evidence over claims; optimize time/cost; no duplicated work.
