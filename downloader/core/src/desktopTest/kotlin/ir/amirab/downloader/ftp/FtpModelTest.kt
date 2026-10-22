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
    fun ftpLiteralSpacesBracketsAndEscapedNamesShareOneSafeParser() {
        val raw = "ftp://127.0.0.1:2121/pub/report [final] (2).zip"
        val encoded = "ftp://127.0.0.1:2121/pub/report%20%5Bfinal%5D%20(2).zip"
        assertEquals(
            parseFtpFileUri(encoded).path,
            parseFtpFileUri(raw).path,
        )
        assertEquals("/pub/report [final] (2).zip", parseFtpFileUri(raw).path)
        FtpDownloadCredentials(raw).validateCredentials()
        FtpDownloadCredentials(encoded).validateCredentials()
        assertEquals("127.0.0.1", parseFtpFileUri(raw).host)
        assertFailsWith<IllegalArgumentException> {
            FtpDownloadCredentials("ftp://host/pub/%0Aevil.bin").validateCredentials()
        }
        assertFailsWith<IllegalArgumentException> {
            FtpDownloadCredentials("ftp://user:secret@host/pub/a%20b.bin").validateCredentials()
        }
        assertFailsWith<IllegalArgumentException> {
            FtpDownloadCredentials("ftp://ho st/pub/a.bin").validateCredentials()
        }
    }

    @Test
    fun oldFtpRecordWithoutCredentialRefStillDecodesAndNewReferenceDoesNotContainPassword() {
        val legacy = Json.encodeToString(FtpDownloadItem.serializer(), sample())
        assertFalse(legacy.contains("credentialRef"))
        val oldLoaded = Json.decodeFromString(FtpDownloadItem.serializer(), legacy)
        assertEquals(null, oldLoaded.credentialRef)
        val withReference = sample().copy(
            username = "synthetic-user",
            credentialRef = "01234567-89ab-4cde-8fab-0123456789ab",
        )
        val json = Json.encodeToString(FtpDownloadItem.serializer(), withReference)
        assertTrue(json.contains(withReference.credentialRef!!))
        assertFalse(json.contains("password"))
        assertEquals(withReference, Json.decodeFromString(FtpDownloadItem.serializer(), json))
        assertFailsWith<IllegalArgumentException> {
            sample().copy(credentialRef = "../../credential-target").validateItem()
        }
    }

    @Test
    fun itemRejectsUnsupportedMultiConnectionMode() {
        val item = sample().copy(preferredConnectionCount = 3)
        assertFailsWith<IllegalArgumentException> { item.validateItem() }
        sample().validateItem()
    }
}
