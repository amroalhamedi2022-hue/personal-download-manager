package com.abdownloadmanager.shared.storage.appsettings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MaxConcurrentDownloadsTest {
    @Test
    fun zeroKeepsUnlimitedSemantics() {
        assertEquals(0, requireSupportedMaxConcurrentDownloads(0))
    }

    @Test
    fun positiveLimitIsAcceptedWithoutCoercion() {
        assertEquals(16, requireSupportedMaxConcurrentDownloads(16))
        assertEquals(Int.MAX_VALUE, requireSupportedMaxConcurrentDownloads(Int.MAX_VALUE))
    }

    @Test
    fun negativeLimitIsRejectedInsteadOfBecomingUnlimited() {
        assertFailsWith<IllegalArgumentException> {
            requireSupportedMaxConcurrentDownloads(-1)
        }
    }
}
