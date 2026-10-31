package ir.amirab.downloader

import ir.amirab.downloader.downloaditem.DownloadItemContext
import ir.amirab.downloader.downloaditem.EmptyContext
import ir.amirab.downloader.downloaditem.contexts.Queue
import ir.amirab.downloader.downloaditem.contexts.RemovedBy
import ir.amirab.downloader.downloaditem.contexts.ResumedBy
import ir.amirab.downloader.downloaditem.contexts.StoppedBy
import ir.amirab.downloader.downloaditem.contexts.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadManagerLifecycleContextTest {
    @Test fun resumeReplacesPreviousStopAndResumeActors() {
        val previous: DownloadItemContext = StoppedBy(User) + ResumedBy(User)
        val next = contextForLifecycleResume(previous, ResumedBy(Queue(17L)))
        assertNull(next[StoppedBy])
        assertEquals(Queue(17L), next[ResumedBy]?.by)
    }
    @Test fun pauseReplacesPreviousResumeAndStopActors() {
        val previous: DownloadItemContext = ResumedBy(User) + StoppedBy(User)
        val next = contextForLifecyclePause(previous, StoppedBy(Queue(23L)))
        assertNull(next[ResumedBy])
        assertEquals(Queue(23L), next[StoppedBy]?.by)
    }
    @Test fun sixteenPauseResumeCyclesNeverCarryOppositeActor() {
        var state: DownloadItemContext = EmptyContext
        repeat(16) {
            state = contextForLifecycleResume(state, ResumedBy(User))
            assertNull(state[StoppedBy])
            assertEquals(User, state[ResumedBy]?.by)
            state = contextForLifecyclePause(state, StoppedBy(User))
            assertNull(state[ResumedBy])
            assertEquals(User, state[StoppedBy]?.by)
        }
    }
    @Test fun unrelatedContextSurvivesEmptyResumeCommand() {
        val previous: DownloadItemContext = RemovedBy(User) + StoppedBy(User)
        val next = contextForLifecycleResume(previous, EmptyContext)
        assertEquals(User, next[RemovedBy]?.by)
        assertNull(next[StoppedBy])
        assertNull(next[ResumedBy])
    }
}
