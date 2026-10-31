package com.abdownloadmanager.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpHeaderOptionTest {
    @Test
    fun authorizationBearerWithColonAndEqualsIsNotTruncated() {
        assertEquals(
            "Authorization" to "Bearer abc:def=ghi",
            parseDownloadHeaderOption("Authorization: Bearer abc:def=ghi"),
        )
    }

    @Test
    fun cookieWithSeveralEqualsAndColonsIsPreserved() {
        assertEquals(
            "Cookie" to "session=a=b; expires=Wed, 11:00:00 GMT",
            parseDownloadHeaderOption("Cookie: session=a=b; expires=Wed, 11:00:00 GMT"),
        )
    }

    @Test
    fun legacyEqualsSyntaxPreservesEntireSignedLinkValue() {
        val url = "https://cdn.example.org/video?token=abc=123&expires=12:00"
        assertEquals(
            "Referer" to url,
            parseDownloadHeaderOption("Referer=$url"),
        )
    }

    @Test
    fun valuesMayBeEmptyButHeaderNameMustBeValidToken() {
        assertEquals("If-None-Match" to "", parseDownloadHeaderOption("If-None-Match:"))
        assertFailsWith<IllegalArgumentException> { parseDownloadHeaderOption(": value") }
        assertFailsWith<IllegalArgumentException> { parseDownloadHeaderOption("not-a-header") }
        assertFailsWith<IllegalArgumentException> { parseDownloadHeaderOption("Bad Name: value") }
    }

    @Test
    fun acceptsStandardLegalTokenPunctuation() {
        assertEquals("X-Custom_Name" to "ok", parseDownloadHeaderOption("X-Custom_Name=ok"))
        assertEquals("X-Trace" to "a=b:c", parseDownloadHeaderOption(" X-Trace : a=b:c"))
    }

    @Test
    fun rejectsCrLfAndControlHeaderInjection() {
        for (value in listOf(
            "X-Test: first\r\nAuthorization: stolen",
            "X-Test=first\nHost: evil.example",
            "X-Test: a\u0000b",
            "X-Test: a\u0001b",
            "X-Test: a\u007fb",
        )) {
            assertFailsWith<IllegalArgumentException>(value) { parseDownloadHeaderOption(value) }
        }
    }

    @Test
    fun delimiterAndEntireValueArePreserved() {
        assertEquals("A" to "value=1:2", parseDownloadHeaderOption("A=value=1:2"))
        assertEquals("B" to "value:1=2", parseDownloadHeaderOption("B: value:1=2"))
        assertEquals("Authorization" to "Basic dXNlcjpwYXNz==", parseDownloadHeaderOption("Authorization: Basic dXNlcjpwYXNz=="))
    }
}
