package com.abdownloadmanager.shared.pages.batchdownload

/**
 * Compute a user-entered inclusive numeric range without overflowing Int.
 * Invalid ranges have no items; values from 0 through Int.MAX_VALUE are
 * otherwise allowed, but a caller MUST cap the count before enumerating.
 */
internal fun batchRangeCount(first: Int, last: Int): Long =
    if (first < 0 || last < first) 0L else last.toLong() - first.toLong() + 1L

/**
 * Final preflight for batch imports. Never hand duplicates, malformed URLs or
 * more than the approved limit to the downloader. Check bounds before calling
 * iterator() so an untrusted range cannot initiate unbounded enumeration.
 *
 * Validation is deliberately repeated at confirmation time: UI validation is
 * useful feedback, not a security/data-safety boundary.
 */
internal fun validatedBatchUrls(
    urls: Iterable<String>,
    expectedCount: Long,
    maxCount: Int,
    isValidUrl: (String) -> Boolean,
): List<String>? {
    if (maxCount <= 0 || expectedCount <= 0L || expectedCount > maxCount.toLong()) return null
    val items = ArrayList<String>(expectedCount.toInt())
    val seen = HashSet<String>(expectedCount.toInt())
    for (url in urls) {
        if (items.size.toLong() >= expectedCount || !isValidUrl(url) || !seen.add(url)) {
            return null
        }
        items.add(url)
    }
    return items.takeIf { it.size.toLong() == expectedCount }
}
