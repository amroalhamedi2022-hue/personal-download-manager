package ir.amirab.downloader.connection.response.headers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContentDispositionFileNameTest {
    @Test
    fun utf8ExtendedNameWithArabicLanguageTagWinsOverAsciiFallback() {
        assertEquals(
            "اختبار.pdf",
            extractFileNameFromContentDisposition(
                "attachment; filename=\"fallback.pdf\"; filename*=UTF-8'ar'%D8%A7%D8%AE%D8%AA%D8%A8%D8%A7%D8%B1.pdf"
            ),
        )
    }

    @Test
    fun spacesAroundEqualsAndParametersAreAccepted() {
        assertEquals(
            "annual report 2026.pdf",
            extractFileNameFromContentDisposition(
                "attachment; filename = \"old.txt\"; filename* = UTF-8'en'annual%20report%202026.pdf"
            ),
        )
        assertEquals(
            "File 2026.txt",
            extractFileNameFromContentDisposition("attachment; filename = \"File 2026.txt\""),
        )
    }

    @Test
    fun unrelatedSuffixParametersCannotSpoofFilename() {
        assertEquals(
            "actual.txt",
            extractFileNameFromContentDisposition(
                "attachment; x-filename=\"ignored.txt\"; filename=\"actual.txt\""
            ),
        )
        assertNull(extractFileNameFromContentDisposition("attachment; x-filename=\"only-fake.txt\""))
    }

    @Test
    fun invalidExtendedNameFallsBackToAsciiFilename() {
        assertEquals(
            "fallback.txt",
            extractFileNameFromContentDisposition(
                "attachment; filename=\"fallback.txt\"; filename*=UTF-8'en'%GGbad"
            ),
        )
    }

    @Test
    fun noLanguageTagAndEmbeddedQuotedSemicolonRemainSupported() {
        assertEquals(
            "ملف.txt",
            extractFileNameFromContentDisposition(
                "attachment; filename*=UTF-8''%D9%85%D9%84%D9%81.txt"
            ),
        )
        assertEquals(
            "archive; final.txt",
            extractFileNameFromContentDisposition("attachment; filename=\"archive; final.txt\"; size=10"),
        )
    }
}
