package com.abdownloadmanager.desktop.utils

import java.io.File
import java.nio.file.Files

/**
 * Dependency-free source smoke test. Compile ONLY this file and the production
 * MainExecutableResolver.kt with kotlinc; never build/package the application.
 *
 * Example:
 * kotlinc desktop/app/src/main/kotlin/com/abdownloadmanager/desktop/utils/MainExecutableResolver.kt \
 *   scripts/source-tests/HyperGetExecutableResolverSmoke.kt -d /tmp/hyperget-resolver-classes
 * kotlin -classpath /tmp/hyperget-resolver-classes \
 *   com.abdownloadmanager.desktop.utils.HyperGetExecutableResolverSmokeKt
 */
fun main() {
    val root = Files.createTempDirectory("HyperGet resolver with spaces ").toFile()
    try {
        val helper = File(root, "DownloadManagerNativeMessagingHost.bin")
        val legacy = File(root, "DownloadManager.bin")
        val branded = File(root, "HyperGet.bin")
        fun resolve(path: String?) = MainExecutableResolver.resolve(
            runningExecutablePath = path,
            legacyBaseName = "DownloadManager",
            brandedBaseName = "HyperGet",
        )
        fun expect(expected: String?, actual: String?) {
            check(expected == actual) { "Expected '$expected', got '$actual'" }
        }

        expect(null, resolve(null))
        expect(null, resolve("NativeMessagingHost.bin")) // no parent directory
        expect(legacy.path, resolve(helper.path)) // historical fallback
        legacy.writeText("legacy test fixture")
        expect(legacy.path, resolve(helper.path))
        branded.writeText("branded test fixture")
        expect(legacy.path, resolve(helper.path)) // safe side-by-side preference
        expect(legacy.path, resolve(legacy.path)) // legacy GUI never redirects
        expect(branded.path, resolve(branded.path)) // new GUI never redirects
        check(legacy.delete())
        expect(branded.path, resolve(helper.path)) // new-only install
        check(branded.delete())
        check(branded.mkdir())
        expect(legacy.path, resolve(helper.path)) // directory is not executable
        check(branded.delete())
        expect(File(root, "HYPERGET.BIN").path, resolve(File(root, "HYPERGET.BIN").path))
        println("PASS HyperGet executable path source smoke: 10 cases")
    } finally {
        root.deleteRecursively()
    }
}
