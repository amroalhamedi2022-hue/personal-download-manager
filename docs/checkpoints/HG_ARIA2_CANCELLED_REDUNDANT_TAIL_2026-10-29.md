# aria2 comparison: cancelled redundant tail

Baseline: `46b87076f83fc0f1aeada328ce5212e12af7407e`, PR #3.
Evidence: Windows run 40745555279, job 123026076473, artifact 12842752622.

The comparison rejected task 10 because a cancelled 11–12 MiB Range had no
later completed tail. The artifact shows a completed 8–12 MiB response and
only 65536 socket bytes for the redundant request. All sixteen active-16
output files independently hash to
`e09320c5b00b34bb704802136c599a95b3996332ba84d7c7f21112b6231b6bd0`.

Accept this specific cancelled-tail shape only with a completed same-end
cover and at most 64 KiB written. Count its overlap independently of handler
arrival order. Preserve lifecycle, complete unique coverage, byte-counter
reconciliation, overlap bounds and final file hash gates.

Validation: 10 Python contract/regression tests pass. Replay of the sixteen
real artifact tasks passes interval accounting and matches every task's
socket byte counter. Windows PowerShell execution on this change remains
pending; this is not Stage 2 acceptance or evidence for migration.

## Continuation scope

User requested HyperGet only on 2026-10-29; Lingua Tales is out of this work.
User reports old-account automations stopped. Continue the existing PR;
do not restart the app or discard existing requirements. Requirements
sources include `docs/Download_Manager_Execution_Plan_v1.md`, the existing
repair/acceptance documents, the My-projects HyperGet plan and latest user
decisions. Their full traceability audit is still outstanding.

Evaluate aria2 before changing the production default. Preserve the UI,
browser integration, persistent task identity, scheduling and specialist
media/package functionality. Do not infer that successful benchmark files
prove overall product completion. Torrent policy is contradictory across
historical documents; reconcile before packaging/feature work, not by
silently deleting a requirement. SFTP is not an immediate delivery priority.

Next: run the corrected Windows comparison, inspect results before deciding
on migration, and complete requirement-to-code mapping while CI runs.
