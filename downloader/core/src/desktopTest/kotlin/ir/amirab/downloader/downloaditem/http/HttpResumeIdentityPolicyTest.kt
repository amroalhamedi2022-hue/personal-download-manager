package ir.amirab.downloader.downloaditem.http

import ir.amirab.downloader.exception.FileChangedException
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
    fun strongEtagIsRequiredAndMustRemainPresentOnResponse() {
        assertTrue(isStrongHttpETag("\"abc\""))
        assertFalse(isStrongHttpETag("W/\"abc\""))
        assertFalse(isStrongHttpETag("\"\""))
        assertFalse(isStrongHttpETag(null))

        validateRemoteFileIdentity(42, "\"abc\"", 42, "\"abc\"")
        assertFailsWith<FileChangedException.ETagChangedException> {
            validateRemoteFileIdentity(42, "\"abc\"", 42, null)
        }
        assertFailsWith<FileChangedException.ETagChangedException> {
            validateRemoteFileIdentity(42, "\"abc\"", 42, "W/\"abc\"")
        }
        assertFailsWith<FileChangedException.ETagChangedException> {
            validateRemoteFileIdentity(42, "\"abc\"", 42, "\"different\"")
        }
    }

    @Test
    fun legacyMissingOrWeakEtagDoesNotIntroduceNewResumeFailures() {
        validateRemoteFileIdentity(42, null, 42, null)
        validateRemoteFileIdentity(42, "W/\"old\"", 42, null)
        assertFailsWith<FileChangedException.LengthChangedException> {
            validateRemoteFileIdentity(42, null, 41, null)
        }
    }
}
