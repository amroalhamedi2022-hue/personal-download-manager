package ir.amirab.downloader.ftp

import arrow.core.Some
import ir.amirab.downloader.downloaditem.DownloadStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtpModelTest {
    private fun sample() = FtpDownloadItem(
        id = 7L, folder = "C:/Downloads", name = "sample.bin",
        link = "ftp://example.com/pub/sample.bin", status = DownloadStatus.Paused,
    )

    @Test
    fun persistedModelRoundTripsWithoutPasswordOrSecret() {
        val serialized = Json.encodeToString(FtpDownloadItem.serializer(), sample())
        assertTrue(serialized.contains("ftp://example.com/pub/sample.bin"))
        assertFalse(serialized.contains("password", ignoreCase = true))
        assertFalse(serialized.contains("secret", ignoreCase = true))
        assertEquals(sample(), Json.decodeFromString(FtpDownloadItem.serializer(), serialized))
    }

    @Test
    fun copyKeepsIdentityAndStatusAndCanChangeName() {
        val source = sample()
        val updated = source.copy(name = Some("new.bin"))
        assertEquals("new.bin", updated.name)
        assertEquals(7L, updated.id)
        assertEquals(DownloadStatus.Paused, updated.status)
        assertEquals("sample.bin", source.name)
    }

    @Test
    fun validateFtpSchemesPortsAndCredentialsWithoutEmbeddingSecrets() {
        val valid = FtpDownloadCredentials("ftp://example.com:2121/a%20b.zip")
        valid.validateCredentials()
        assertFailsWith<IllegalArgumentException> { FtpDownloadCredentials("https://example.com/x").validateCredentials() }
        assertFailsWith<IllegalArgumentException> { FtpDownloadCredentials("ftp://admin:pass@example.com/a.zip").validateCredentials() }
        assertFailsWith<IllegalArgumentException> { FtpDownloadCredentials("ftp://example.com/").validateCredentials() }
        assertFailsWith<IllegalArgumentException> { FtpDownloadCredentials("ftp://example.com:65536/a").validateCredentials() }
    }

    @Test
    fun itemRejectsUnsupportedMultiConnectionMode() {
        val item = sample().copy(preferredConnectionCount = 3)
        assertFailsWith<IllegalArgumentException> { item.validateItem() }
        sample().validateItem()
    }
}
