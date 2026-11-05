package com.abdownloadmanager.shared.pages.batchdownload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatchImportSafetyTest {
    private fun valid(url: String) = url.startsWith("https://") && !url.contains(' ')

    @Test fun hugeInclusiveRangeCannotOverflowOrEnumerate() {
        val range = WildcardString(
            string = "https://example.test/part-*.zip",
            range = 0..Int.MAX_VALUE,
            wildcardLength = WildcardLength.Auto,
        )
        assertEquals(2_147_483_648L, range.size())
        val neverIterate = object : Iterable<String> {
            override fun iterator(): Iterator<String> = error("unsafe enumeration")
        }
        assertNull(validatedBatchUrls(neverIterate, range.size(), 1000, ::valid))
    }

    @Test fun inclusiveBoundsRejectInvalidOrInvertedRanges() {
        assertEquals(0L, batchRangeCount(-1, 100))
        assertEquals(0L, batchRangeCount(5, 4))
        assertEquals(1L, batchRangeCount(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(1000L, batchRangeCount(1, 1000))
    }

    @Test fun validBatchKeepsOriginalOrderAndNumericZeroPadding() {
        val range = WildcardString(
            string = "https://example.test/part-*.zip",
            range = 1..3,
            wildcardLength = WildcardLength.Custom(4),
        )
        val urls = validatedBatchUrls(range, range.size(), 1000, ::valid)
        assertEquals(
            listOf(
                "https://example.test/part-0001.zip",
                "https://example.test/part-0002.zip",
                "https://example.test/part-0003.zip",
            ),
            urls,
        )
    }

    @Test fun duplicateLinksAreRejectedRatherThanQueuedThousandTimes() {
        val range = WildcardString(
            string = "https://example.test/same.zip",
            range = 1..1000,
            wildcardLength = WildcardLength.Auto,
        )
        assertNull(validatedBatchUrls(range, range.size(), 1000, ::valid))
    }

    @Test fun middleMalformedUrlIsRejectedEvenWhenEndpointsPass() {
        val links = listOf(
            "https://ok.example/one.zip",
            "https://bad.example/contains space.zip",
            "https://ok.example/three.zip",
        )
        assertNull(validatedBatchUrls(links, 3, 1000, ::valid))
    }

    @Test fun mismatchedCountCannotImportPartialOrExtraEnumeration() {
        assertNull(validatedBatchUrls(listOf("https://a.test"), 2, 1000, ::valid))
        assertNull(validatedBatchUrls(listOf("https://a.test", "https://b.test"), 1, 1000, ::valid))
        assertNull(validatedBatchUrls(emptyList(), 0, 1000, ::valid))
        assertNull(validatedBatchUrls(listOf("https://a.test"), 1, 0, ::valid))
    }

    @Test fun tenThousandItemsAreRejectedBeforeAllocationOrIteration() {
        val items = object : Iterable<String> {
            override fun iterator(): Iterator<String> = error("must not enumerate")
        }
        assertNull(validatedBatchUrls(items, 10_000L, 1000, ::valid))
    }

    @Test fun oversizedCustomPaddingIsBoundedToTenDigits() {
        val range = WildcardString(
            string = "https://example.test/file-*.zip",
            range = 1..1,
            wildcardLength = WildcardLength.Custom(Int.MAX_VALUE),
        )
        val result = range.first()
        assertEquals("https://example.test/file-0000000001.zip", result)
        assertTrue(result.length < 100)
    }
}
