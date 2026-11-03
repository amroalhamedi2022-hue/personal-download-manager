package ir.amirab.downloader.destination

/** Conservative validation for a completed segmented download before assembly. */
internal fun isStagedSegmentValid(
    isRegularFile: Boolean,
    actualSize: Long,
    expectedSize: Long?,
): Boolean {
    if (!isRegularFile || actualSize <= 0L) return false
    // Some servers do not report a usable Content-Length for HLS segments.
    return expectedSize == null || expectedSize <= 0L || actualSize == expectedSize
}
