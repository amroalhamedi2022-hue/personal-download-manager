package ir.amirab.downloader.ftp

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
    fun anonymousAndInvalidCredentialsNeverWriteSecrets() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return
        val vault = WindowsFtpCredentialVault()
        assertFailsWith<IllegalArgumentException> { vault.save("", "synthetic") }
        assertFailsWith<IllegalArgumentException> { vault.save("synthetic", "") }
        assertFailsWith<IllegalArgumentException> { vault.save("synthetic", "s".repeat(2000)) }
        assertFailsWith<IllegalArgumentException> { vault.delete("not-an-opaque-id") }
    }
}
