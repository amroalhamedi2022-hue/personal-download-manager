package ir.amirab.downloader.downloaditem.http

import ir.amirab.downloader.exception.FileChangedException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpResumeIdentityPolicyTest {
    private val original = "https://cdn.example.test/archive.bin?token=old&part=1"
    private val rotated = "https://cdn.example.test/archive.bin?token=new&part=1"

    @Test
    fun unchangedUrlDoesNotRequireRemoteValidator() {
        assertTrue(canReuseHttpPartialAfterLinkChange(original, original, null, -1))
        assertTrue(canReuseHttpPartialAfterLinkChange(original + "#old", original + "#new", null, -1))
    }

    @Test
    fun signedUrlRotationWithoutStrongValidatorMustRestart() {
        assertFalse(canReuseHttpPartialAfterLinkChange(original, rotated, null, 42))
        assertFalse(canReuseHttpPartialAfterLinkChange(original, rotated, "W/\"same\"", 42))
        assertFalse(canReuseHttpPartialAfterLinkChange(original, rotated, "same", 42))
        assertFalse(canReuseHttpPartialAfterLinkChange(original, rotated, "\"\"", 42))
    }

    @Test
    fun signedUrlRotationRequiresKnownPositiveLength() {
        assertFalse(canReuseHttpPartialAfterLinkChange(original, rotated, "\"same\"", -1))
        assertFalse(canReuseHttpPartialAfterLinkChange(original, rotated, "\"same\"", 0))
        assertTrue(canReuseHttpPartialAfterLinkChange(original, rotated, "\"same\"", 42))
    }

    @Test
    fun changedSelectorOrPathOrHostCannotReuseEvenWithStrongValidator() {
        assertFalse(canReuseHttpPartialAfterLinkChange(
            original, "https://cdn.example.test/archive.bin?token=new&part=2", "\"same\"", 42,
        ))
        assertFalse(canReuseHttpPartialAfterLinkChange(
            original, "https://cdn.example.test/other.bin?token=new&part=1", "\"same\"", 42,
        ))
        assertFalse(canReuseHttpPartialAfterLinkChange(
            original, "https://another.example.test/archive.bin?token=new&part=1", "\"same\"", 42,
        ))
        assertFalse(canReuseHttpPartialAfterLinkChange(
            original, "http://cdn.example.test/archive.bin?token=new&part=1", "\"same\"", 42,
        ))
    }

    @Test
    fun queryOrderingHostCaseAndKnownCredentialRotationsAreCompatible() {
        assertTrue(canReuseHttpPartialAfterLinkChange(
            original,
            "https://CDN.EXAMPLE.TEST/archive.bin?part=1&token=updated",
            "\"same\"",
            42,
        ))
        assertTrue(canReuseHttpPartialAfterLinkChange(
            "https://cdn.example.test/a?id=3&x-amz-signature=old",
            "https://cdn.example.test/a?x-amz-signature=new&id=3",
            "\"strong\"",
            42,
        ))
    }

    @Test
    fun rotatedLinkRequiresMatchingStrongEtagFromServer() {
        assertTrue(isStrongHttpETag("\"abc\""))
        assertFalse(isStrongHttpETag("W/\"abc\""))
        assertFalse(isStrongHttpETag("\"\""))
        assertFalse(isStrongHttpETag(null))

        validateRemoteFileIdentity(
            42, "\"abc\"", 42, "\"abc\"", requireStrongETag = true,
        )
        assertFailsWith<FileChangedException.ETagChangedException> {
            validateRemoteFileIdentity(42, "\"abc\"", 42, null, requireStrongETag = true)
        }
        assertFailsWith<FileChangedException.ETagChangedException> {
            validateRemoteFileIdentity(42, "\"abc\"", 42, "W/\"abc\"", requireStrongETag = true)
        }
        assertFailsWith<FileChangedException.ETagChangedException> {
            validateRemoteFileIdentity(42, "\"abc\"", 42, "\"different\"", requireStrongETag = true)
        }
    }

    @Test
    fun malformedStrongEtagCannotAuthoriseSignedLinkRotation() {
        val invalid = listOf(
            "\"part\" \"other\"", "\"embedded\"quote\"", "\"line\\nfeed\"",
            "\"carriage\\rreturn\"", "\"tab\\tcharacter\"", "\"del\\u007fcharacter\"",
        )
        invalid.forEach { candidate ->
            assertFalse(isStrongHttpETag(candidate), "Rejected malformed ETag: ${candidate.length} chars")
            assertFalse(canReuseHttpPartialAfterLinkChange(original, rotated, candidate, 42))
            assertFailsWith<FileChangedException.ETagChangedException> {
                validateRemoteFileIdentity(42, candidate, 42, candidate, requireStrongETag = true)
            }
        }
        assertTrue(isStrongHttpETag("\"valid-opaque:123\""))
    }

    @Test
    fun rotatingUrlGuardSurvivesSerializationAndLegacyRecordsDefaultOff() {
        val original = HttpDownloadItem(
            link = "https://example.test/file?token=old",
            id = 71,
            folder = "downloads",
            name = "file.bin",
            contentLength = 42,
            serverETag = "\"safe\"",
            requireStrongETagOnResume = true,
        )
        val restored = Json.decodeFromString<HttpDownloadItem>(Json.encodeToString(original))
        assertTrue(restored.requireStrongETagOnResume)

        val mutable = original.copy(requireStrongETagOnResume = false)
        mutable.applyFrom(restored)
        assertTrue(mutable.requireStrongETagOnResume)

        val legacy = Json.decodeFromString<HttpDownloadItem>(
            """{"link":"https://example.test/file","id":72,"folder":"downloads","name":"file.bin"}"""
        )
        assertFalse(legacy.requireStrongETagOnResume)
    }

    @Test
    fun legacyMissingOrWeakEtagDoesNotIntroduceNewResumeFailures() {
        validateRemoteFileIdentity(42, null, 42, null)
        // Historical behaviour on unchanged URLs must still allow absent ETag.
        validateRemoteFileIdentity(42, "\"abc\"", 42, null)
        validateRemoteFileIdentity(42, "W/\"old\"", 42, null)
        assertFailsWith<FileChangedException.LengthChangedException> {
            validateRemoteFileIdentity(42, null, 41, null)
        }
    }
}
