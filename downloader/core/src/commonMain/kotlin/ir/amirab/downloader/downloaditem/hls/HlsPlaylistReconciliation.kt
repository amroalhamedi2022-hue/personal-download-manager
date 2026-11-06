package ir.amirab.downloader.downloaditem.hls

/** A dependency-free view of a segment, used to reconcile refreshed playlists. */
internal data class HlsSegmentSnapshot(
    val sequence: Long,
    val uri: String,
    val duration: Double,
    val completed: Boolean = false,
    val length: Long? = null,
    val current: Long = 0L,
)

/**
 * A media sequence alone does not identify bytes: playlists may reuse sequence
 * numbers with different URIs or segment durations. Never reuse a completed
 * segment when either changes, and always adopt the refreshed playlist order.
 */
internal fun reconcileHlsSegments(
    previous: List<HlsSegmentSnapshot>,
    incoming: List<HlsSegmentSnapshot>,
): List<HlsSegmentSnapshot> {
    // Repeated sequence IDs cannot identify a unique on-disk segment. Never
    // reuse bytes from an ambiguous persisted sequence after a playlist refresh.
    val previousBySequence = HashMap<Long, HlsSegmentSnapshot?>()
    for (saved in previous) {
        if (previousBySequence.containsKey(saved.sequence)) {
            previousBySequence[saved.sequence] = null
        } else {
            previousBySequence[saved.sequence] = saved
        }
    }
    val incomingSequences = HashSet<Long>(incoming.size)
    return incoming.map { fresh ->
        require(incomingSequences.add(fresh.sequence)) {
            "HLS playlist repeats media sequence ${fresh.sequence}"
        }
        val old = previousBySequence[fresh.sequence]
        if (old != null && old.uri == fresh.uri && old.duration == fresh.duration) {
            fresh.copy(completed = old.completed, length = old.length, current = old.current)
        } else {
            fresh
        }
    }
}
