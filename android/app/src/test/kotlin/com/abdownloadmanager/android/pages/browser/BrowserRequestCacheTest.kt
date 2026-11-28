package com.abdownloadmanager.android.pages.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserRequestCacheTest {
    private var time = 0L
    private val cache = BrowserRequestCache<String>(2, 20) { time }

    @Test fun differentTabsNeverShareMetadata() {
        cache.put("a", "url", "private-a")
        cache.put("b", "url", "private-b")
        assertEquals("private-a", cache.take("a", "url"))
        assertEquals("private-b", cache.take("b", "url"))
        assertNull(cache.take("a", "url"))
    }

    @Test fun refreshedEntrySurvivesOldExpiry() {
        cache.put("a", "url", "old")
        time = 15
        cache.put("a", "url", "new")
        time = 21
        assertEquals("new", cache.take("a", "url"))
    }

    @Test fun expiryBoundaryRejectsStaleCredentials() {
        cache.put("a", "url", "old")
        time = 20
        assertNull(cache.take("a", "url"))
    }

    @Test fun boundedCacheEvictsOldest() {
        cache.put("a", "1", "first")
        cache.put("a", "2", "second")
        cache.put("a", "3", "third")
        assertNull(cache.take("a", "1"))
        assertEquals("second", cache.take("a", "2"))
        assertEquals("third", cache.take("a", "3"))
    }

    @Test fun closingTabClearsOnlyItsEntries() {
        cache.put("a", "url", "first")
        cache.put("b", "url", "second")
        cache.clearTab("a")
        assertNull(cache.take("a", "url"))
        assertEquals("second", cache.take("b", "url"))
    }

    @Test fun exactUrlAndSingleConsumption() {
        cache.put("a", "url?token=one", "first")
        assertNull(cache.take("a", "url?token=two"))
        assertEquals("first", cache.take("a", "url?token=one"))
        assertNull(cache.take("a", "url?token=one"))
    }
}
