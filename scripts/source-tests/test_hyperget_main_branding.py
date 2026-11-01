#!/usr/bin/env python3
"""HyperGet GUI/installer branding contract; technical identity remains stable."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
def source(path):
    return (ROOT / path).read_text(encoding="utf-8")

version = source("buildSrc/src/main/kotlin/buildlogic/versioning/VersionUtil.kt")
gradle = source("desktop/app/build.gradle.kts")
nsis = source("desktop/app/resources/installer/nsis-script-template.nsi")
app = source("desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/AppInfo.kt")
resolver = source("desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/MainExecutableResolver.kt")
ci = source(".github/workflows/private-windows-validation.yml")
e2e = source("scripts/Run-WindowsSafeUninstallE2E.ps1")
shared = source("shared/app/build.gradle.kts")

assert 'return "HyperGet"' in version
assert 'return ".personal-download-manager"' in version
assert 'return "app.personaldownloadmanager"' in version
assert 'rootProject.name' in source("settings.gradle.kts")
assert 'packageName = if (Platform.isWindows()) getPrettifiedAppName() else getAppName()' in gradle
assert 'outputFileName = "${getPrettifiedAppName()}Setup"' in gradle
assert '"main_binary_name" to getPrettifiedAppName()' in gradle
assert '"app_publisher" to "HyperGet"' in gradle
assert 'appDisplayName = getPrettifiedAppName()' in gradle
assert '!define MAIN_BINARY_NAME "{{ main_binary_name }}"' in nsis
assert '!define LEGACY_MAIN_BINARY_NAME "${APP_NAME}"' in nsis
assert 'InstallDir "$LOCALAPPDATA\\${APP_NAME}"' in nsis
assert '!insertmacro RemoveKnownBrandShortcuts' in nsis
assert '"DisplayName" "${APP_DISPLAY_NAME}"' in nsis
assert 'val mainExeFile: String?' in app and 'MainExecutableResolver.resolve(' in app
assert 'legacy.isFile -> legacy.path' in resolver and 'branded.isFile -> branded.path' in resolver
assert 'legacyBaseName = FileNameConstants.mainApp' in app
assert 'brandedBaseName = "HyperGet"' in app
assert 'HyperGetSetup.exe' in ci and 'hyperget-windows-x64' in ci
assert "Join-Path $installDir 'HyperGet.exe'" in e2e
assert "Join-Path $startMenu 'HyperGet.lnk'" in e2e
assert 'WindowsProductName=HyperGet' in e2e
assert 'DownloadManagerNativeMessagingHost' in app
assert 'DownloadManagerCli' in app
assert '"DATA_DIR_NAME"' in shared and '"INTEGRATION_FIREFOX_EXTENSION_ID"' in shared
print("PASS: branded HyperGet Windows UI, launcher, setup and shortcuts; preserved technical compatibility")
