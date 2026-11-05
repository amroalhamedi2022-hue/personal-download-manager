# HyperGet F01-08 — Windows filename safety (2026-11-05)

- Source: `shared/utils/src/commonMain/kotlin/ir/amirab/util/FileNameValidator.kt`. Reject DOS device aliases (CON/NUL/PRN/AUX/COM/LPT/CONIN$/CONOUT$), including disguised extensions and superscript port digits; reject forbidden Windows chars, controls, terminal dot/space and name >255 UTF-16 units. Preserve legitimate Arabic/RTL Unicode filenames and names with leading periods. Linux/Android behaviour unchanged.
- Test: `downloader/core/src/desktopTest/kotlin/ir/amirab/downloader/WindowsFilenameValidationTest.kt`: three Windows-specific regression tests. No changes to user data, rename policy, database, UI, themes or icons.
- [PR #25](https://github.com/amroalhamedi2022-hue/personal-download-manager/pull/25). Tested production+test SHA `b98824075f2c782ebb933cf2daf5ba17e86f3d43`.
- [Windows run 42084999515](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/42084999515) **COMPLETED/SUCCESS**: downloader tests and desktop compile/tests, Windows build, real silent NSIS installation/uninstall, artifacts. Source/test blob SHAs remained unchanged after trigger cleanup.
- Temporary CI trigger restored in workflow-only commit `4507be2f0cdf8a3f4496c7136075d7cfe99667af`; workflow Git blob `0fd348e612abc1760662998f618887ae6e05070a` equal main.
- Before merge: confirm fresh GET /branches/main, clean mergeability and expected head. Post-merge Windows CI is separate evidence. Manual installed GUI acceptance remains PENDING.
- F01-02 16-active memory benchmark remains historically FAIL; threshold 15% unchanged, PR #3 separate.
