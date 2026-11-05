package ir.amirab.util

import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.isWindows
import java.io.File
import java.util.Locale

object FileNameValidator {
    private val reservedWindowsDeviceNames = setOf(
        "CON", "PRN", "AUX", "NUL", "CLOCK$", "CONIN$", "CONOUT$",
    )
    private val reservedWindowsPorts = Regex("""(COM|LPT)[1-9¹²³]""")
    private const val WINDOWS_MAX_FILENAME_LENGTH = 255

    /**
     * Windows' DOS device names are reserved even with extensions (NUL.txt);
     * File.canonicalFile does not reliably reject them before file creation.
     * Do not silently rename user-selected output or change validation on Android.
     */
    internal fun isValidWindowsFileNameSyntax(name: String): Boolean {
        if (name.isEmpty() || name.length > WINDOWS_MAX_FILENAME_LENGTH) return false
        if (name == "." || name == ".." || name.endsWith(' ') || name.endsWith('.')) return false
        if (name.any { it.code < 0x20 || it in "<>:\"/\\|?*" }) return false
        val deviceStem = name.substringBefore('.').trimEnd(' ').uppercase(Locale.ROOT)
        if (deviceStem in reservedWindowsDeviceNames || reservedWindowsPorts.matches(deviceStem)) {
            return false
        }
        return true
    }

    fun isValidFileName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (Platform.isWindows() && !isValidWindowsFileNameSyntax(name)) return false
        return runCatching {
            File(name).canonicalFile
        }.getOrNull()?.let {
            it.name == name
        } ?: false
    }
}
