#!/usr/bin/env python3
"""Regressions for safe uninstall of HyperGet's per-user Windows shortcut."""
from pathlib import Path
import re

text = Path("desktop/app/resources/installer/nsis-script-template.nsi").read_text(encoding="utf-8")
def section(start, end):
    assert text.count(start) == 1 and end in text.split(start, 1)[1], start
    return text.split(start, 1)[1].split(end, 1)[0]

menu = section("!macro RemoveStartMenu", "!macroend")
assert re.search(r'Delete\s+"\$SMPROGRAMS', menu, re.I)
assert "${APP_DISPLAY_NAME}.lnk" in menu
assert re.search(r'RMDir\s+"\$SMPROGRAMS', menu, re.I)
assert not re.search(r'RMDir\s+/r', menu, re.I), "Do not delete user files recursively"
init = section("Function .onInit", "FunctionEnd")
uninstall = section('Section "Uninstall"', "SectionEnd")
assert "SetShellVarContext current" in init
assert "SetShellVarContext current" in uninstall
ordered = [
    "!insertmacro closeApp", "!insertmacro RemoveStartMenu",
    "!insertmacro RemoveDesktopShortCut", 'DeleteRegKey SHCTX "${REG_UNINSTALL_KEY}"',
    'DeleteRegKey SHCTX "${REG_APP_KEY}"',
    'DeleteRegValue SHCTX "${REG_RUN_KEY}" "${APP_NAME}"',
    "!insertmacro clearFiles",
]
locations = [uninstall.find(item) for item in ordered]
assert all(p >= 0 for p in locations), f"Missing uninstall step(s): {locations}"
assert locations == sorted(locations), "Uninstaller must be removed last"
print("PASS: per-user context, targeted shortcut cleanup, and uninstall ordering")
