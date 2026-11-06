package ir.amirab.downloader.part

import ir.amirab.downloader.utils.calcPercent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Inclusive ranges can span Long.MAX_VALUE + 1 bytes (0..Long.MAX_VALUE),
// which is not representable as a Long. Saturate instead of wrapping negative.
internal fun boundedInclusiveLength(start: Long, end: Long): Long {
    if (start < 0L || end < start) return 0L
    val span = end - start
    return if (span == Long.MAX_VALUE) Long.MAX_VALUE else span + 1L
}

@Serializable
data class RangedPart(
    var from: Long,
    @Volatile
    var to: Long?,
    @Volatile
    override var current: Long = from,
) : DownloadPart {

    override fun howMuchProceed(): Long {
        return current - from
    }

    override fun resetCurrent() {
        current = from
    }

    @Transient
    override val statusFlow = MutableStateFlow<PartDownloadStatus>(PartDownloadStatus.IDLE)

    val remainingLength
        get() = to?.let { boundedInclusiveLength(current, it) }
    val partLength
        get() = to?.let { boundedInclusiveLength(from, it) }

    override val percent: Int?
        get() = partLength?.let { length ->
            // A corrupted or stale cursor below the start can overflow subtraction.
            if (current < from) 0 else {
                val percent = calcPercent(howMuchProceed(), length)
                // Saturating an unrepresentably long range must not imply
                // the last byte has been transferred.
                if (!isCompleted && percent == 100) 99 else percent
            }
        }

    // The next byte index cannot be represented after Long.MAX_VALUE.
    // In particular, wrapping MAX_VALUE + 1 must never report completion.
    override val isCompleted: Boolean
        get() = to?.let { end ->
            end != Long.MAX_VALUE && current == end + 1
        } ?: false

    fun setBlindAsCompleted() {
        to = current - 1
    }

    val range
        get() = to?.let {
            from..it
        }

    val isBlind get() = to == null
    override fun getID(): Long {
        return from
    }
    companion object {
    }
}
