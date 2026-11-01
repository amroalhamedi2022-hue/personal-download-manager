package com.abdownloadmanager.desktop.utils

import java.io.File

/**
 * Locate the main GUI beside a CLI/native-messaging helper during the gradual
 * HyperGet rename. The existing executable wins when both old and new files
 * exist: an unrelated or stale HyperGet file must not silently hijack launches
 * from a still-installed legacy helper.
 *
 * This only resolves a path. It never renames executables, moves user data,
 * rewrites IPC/registry keys or changes the installer identity.
 */
internal object MainExecutableResolver {
    fun resolve(
        runningExecutablePath: String?,
        legacyBaseName: String,
        brandedBaseName: String,
    ): String? {
        val running = runningExecutablePath?.let(::File) ?: return null
        val suffix = running.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        val legacyName = "$legacyBaseName$suffix"
        val brandedName = "$brandedBaseName$suffix"

        // A GUI process must keep referring to itself, even during a
        // side-by-side upgrade when both launchers are present.
        if (running.name.equals(legacyName, ignoreCase = true) ||
            running.name.equals(brandedName, ignoreCase = true)
        ) {
            return running.path
        }

        val installDir = running.parentFile ?: return null
        val legacy = installDir.resolve(legacyName)
        val branded = installDir.resolve(brandedName)

        // Preserve the historical target while it exists. The branded
        // launcher is a fallback only after the old GUI is absent.
        return when {
            legacy.isFile -> legacy.path
            branded.isFile -> branded.path
            else -> legacy.path // retain pre-rename path behavior
        }
    }
}
