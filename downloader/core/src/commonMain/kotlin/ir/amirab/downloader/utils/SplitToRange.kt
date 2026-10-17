
package ir.amirab.downloader.utils

fun splitToRange(size: Long, minPartSize: Long, maxPartCount: Long): List<LongRange> {
    require(size >= 1) {
        "size must be >=1 passed :$size"
    }
    require(minPartSize >= 1) {
        "minPartSize must be >=1 passed :$minPartSize"
    }
    require(maxPartCount >= 1) {
        "maxPartCount must be >=1 passed :$maxPartCount"
    }

    // A single small file is allowed, but once split, every initial part must
    // remain at least minPartSize. Using ceil(size / minPartSize) here created
    // undersized parts (for example, 700 KiB became two 350 KiB parts when
    // the configured minimum was 512 KiB).
    val maxPartsRespectingMinimum = (size / minPartSize).coerceAtLeast(1)
    val actualPartCount = minOf(maxPartCount, maxPartsRespectingMinimum)
    val idealPartSize = size / actualPartCount
    val ranges = mutableListOf<LongRange>()
    var start = 0L
    var end = 0L
    for (i in 1..actualPartCount) {
        end = start + idealPartSize - 1
        if (i <= size % actualPartCount) {
            end++
        }
        ranges.add(start..end)
        start = end + 1
    }
    return ranges
}
