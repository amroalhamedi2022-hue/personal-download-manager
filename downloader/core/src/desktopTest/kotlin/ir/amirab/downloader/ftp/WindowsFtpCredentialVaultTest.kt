package ir.amirab.downloader.ftp

import ir.amirab.downloader.downloaditem.DownloadStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

/**
 * Uses the real per-user Windows Credential Manager on a clean ephemeral
 * GitHub Actions Windows runner. All names and secrets here are synthetic.
 */
class WindowsFtpCredentialVaultTest {
    @Test
    fun unicodeGenericCredentialRoundTripsAndDeletesOnlyItsOwnReference() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val vault = WindowsFtpCredentialVault()
        val username = "synthetic-ftp-مستخدم"
        val password = "Synthetic🧪Only-Δ-13579"
        val ref = vault.save(username, password)
        try {
            assertFalse(ref.contains(username))
            assertFalse(ref.contains(password))
            val result = assertNotNull(vault.read(ref))
            assertEquals(username, result.username)
            assertEquals(password, result.password)
            assertFalse(result.toString().contains(password))
            assertFailsWith<IllegalArgumentException> { vault.read("../../Windows") }
        } finally {
            vault.delete(ref)
        }
        assertNull(vault.read(ref), "Native credential must not survive deletion")
        assertFalse(vault.delete(ref), "Deleting missing credential must be idempotent")
    }

    @Test
    fun providerRestoresSavedWindowsCredentialsWithoutPuttingPasswordInTask() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val vault = WindowsFtpCredentialVault()
        val username = "synthetic-account"
        val password = "Synthetic🧪FTP-Only-2468"
        val reference = vault.save(username, password)
        try {
            val item = FtpDownloadItem(
                id = 91L,
                folder = "C:/Temp",
                name = "synthetic.bin",
                link = "ftp://example.com/files/synthetic.bin",
                username = username,
                credentialRef = reference,
            )
            val provider = WindowsCredentialFtpProvider(vault)
            assertEquals(password, provider.resolve(item).password)
            assertFalse(item.toString().contains(password))
            assertFailsWith<java.io.IOException> {
                provider.resolve(item.copy(username = "wrong-account"))
            }
        } finally {
            vault.delete(reference)
        }
    }


    @Test
    fun serializedFtpTaskReopensWithFreshWindowsVaultWithoutPersistingItsPassword() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val initialVault = WindowsFtpCredentialVault()
        val username = "synthetic-restart-account"
        val password = "Synthetic-Restart-Only-Δ-4682"
        val reference = initialVault.save(username, password)
        val pausedTask = FtpDownloadItem(
            id = 923L,
            folder = "C:/Downloads",
            name = "restart-test.bin",
            link = "ftp://example.com/pub/restart-test.bin",
            username = username,
            credentialRef = reference,
            status = DownloadStatus.Paused,
        )
        val persistedTaskJson = Json.encodeToString(FtpDownloadItem.serializer(), pausedTask)
        assertFalse(persistedTaskJson.contains(password), "FTP item must never persist secrets")
        assertFalse(persistedTaskJson.contains("\"password\"", ignoreCase = true))
        try {
            // A freshly constructed vault/provider must reopen the already-persisted
            // credential, exactly as the app would after restarting on Windows.
            val restoredTask = Json.decodeFromString(FtpDownloadItem.serializer(), persistedTaskJson)
            assertEquals(DownloadStatus.Paused, restoredTask.status)
            assertEquals(reference, restoredTask.credentialRef)
            assertEquals(username, restoredTask.username)
            val freshProvider = WindowsCredentialFtpProvider(WindowsFtpCredentialVault())
            assertEquals(username, freshProvider.resolve(restoredTask).username)
            assertEquals(password, freshProvider.resolve(restoredTask).password)
        } finally {
            initialVault.delete(reference)
        }
        // Delete must invalidate a persisted reference rather than falling back
        // to anonymous credentials or embedding a substitute password.
        val restoredTask = Json.decodeFromString(FtpDownloadItem.serializer(), persistedTaskJson)
        assertFailsWith<java.io.IOException> {
            WindowsCredentialFtpProvider(WindowsFtpCredentialVault()).resolve(restoredTask)
        }
    }

    @Test
    fun anonymousAndInvalidCredentialsNeverWriteSecrets() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val vault = WindowsFtpCredentialVault()
        assertFailsWith<IllegalArgumentException> { vault.save("", "synthetic") }
        assertFailsWith<IllegalArgumentException> { vault.save("synthetic", "") }
        assertFailsWith<IllegalArgumentException> { vault.save("synthetic", "s".repeat(2000)) }
        assertFailsWith<IllegalArgumentException> { vault.delete("not-an-opaque-id") }
    }
}
