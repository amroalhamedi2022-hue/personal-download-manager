package com.abdownloadmanager.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeUtilRelativeRefreshTest {
    @Test
    fun secondLabelsWakeAtTheNextSecondBoundary() {
        assertEquals(1_000L, relativeTimeRefreshDelayMillis(0L))
        assertEquals(2_000L, relativeTimeRefreshDelayMillis(58_000L))
        assertEquals(1L, relativeTimeRefreshDelayMillis(59_999L))
        assertEquals(1_000L, relativeTimeRefreshDelayMillis(-10L))
    }

    @Test
    fun minuteLabelsWakeAtTheNextMinuteBoundary() {
        assertEquals(60_000L, relativeTimeRefreshDelayMillis(60_000L))
        assertEquals(15_000L, relativeTimeRefreshDelayMillis(105_000L))
        assertEquals(1L, relativeTimeRefreshDelayMillis(119_999L))
        assertEquals(60_000L, relativeTimeRefreshDelayMillis(3_000_000L))
    }

    @Test
    fun hourLabelsWakeAtTheNextHourBoundary() {
        assertEquals(3_600_000L, relativeTimeRefreshDelayMillis(3_600_000L))
        assertEquals(3_600_000L, relativeTimeRefreshDelayMillis(7_200_000L))
        assertEquals(1L, relativeTimeRefreshDelayMillis(86_399_999L))
    }

    @Test
    fun dayAndOlderLabelsUseAtMostDailyRefreshes() {
        assertEquals(86_400_000L, relativeTimeRefreshDelayMillis(86_400_000L))
        assertEquals(43_200_000L, relativeTimeRefreshDelayMillis(129_600_000L))
    }
}
