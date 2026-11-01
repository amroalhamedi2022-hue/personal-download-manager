# HyperGet — verified Windows development candidate (2026-11-01)

## Source and installer identity
- Repository: `amroalhamedi2022-hue/personal-download-manager`
- PR: #4, **Open / Draft**, dedicated experiment branch; no main/PR #3 changes.
- Exact Windows-verification **CODE SHA**: `631c51ff70aebbca7b6c58f1f79aff1ecd74a756`
- Windows Action: [41347518763](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41347518763), **SUCCESS**
- EXE: `HyperGet_1.0.0-sha.g631c51f_windows_x64.exe`
- Installer artifact: [13099863682](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41347518763/artifacts/13099863682)
- SHA-256 of EXE: `595869de2ee6eb2468940c6f10e19ff75bea090afefd3e5897a25225b2ea1e08`

## Verified
- Strict UTF-8/UTF-16 BOM decoding, error rejection including unpaired surrogate UTF-16 and invalid UTF-8.
- Aggregate input byte budget charges all attempted file reads, including invalid/binary link lists.
- Windows drag input is read at onDrop (once), not hover+drop, preserving text-only hover preview.
- Unicode inline URL paths, long queries, mixed stand-alone and Windows Shortcut `URL=` links, 10k URL extractor & list selection Kotlin regressions.
- Installed HyperGet GUI, Ctrl+F and real Ctrl+V/A/C text editing, 16MiB loopback HTTP transfer and SHA-256 verified, completion settings persistence and NSIS install/reinstall/uninstall.
- Windows artifacts include GUI [13099923451](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41347518763/artifacts/13099923451) and active transfer [13099913400](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41347518763/artifacts/13099913400).

## IMPORTANT: native two-file mouse drag diagnostic did NOT pass
- Separate hosted-Windows OLE test is `continue-on-error` and **does not gate installer release**.
- Exact artifact [13099903368](https://github.com/amroalhamedi2022-hue/personal-download-manager/actions/runs/41347518763/artifacts/13099903368) says `OpenedNewDialog=false`, `NativeOLEEffect=missing`, `Native file source missing`. This means no positive native mouse-drop proof.
- Test-only `5a5a681` addresses possible child WinForms hidden-window initialization; its CI is pending and is NOT included in this verified EXE.
- Even after native Copy succeeds, independently confirm both URLs are populated in the Add flow; do not claim complete behavior on Copy effect alone.

## Unaccepted / unfinished
Physical Explorer drag and both URLs readback; Windows DPI 125/150/200%, final long RTL image proof, original IDM screenshot P01–P27 comparison, sustained 1k/10k real GUI/download pressure, P3–P8 roadmap, user acceptance, PR merge.
