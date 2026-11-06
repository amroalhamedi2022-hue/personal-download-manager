package ir.amirab.downloader.part

import ir.amirab.downloader.utils.calcPercent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
        get() = to?.let {
            (it - current) + 1
        }
    val partLength
        get() = to?.let {
            (it - from) + 1
        }

    override val percent: Int?
        get() = partLength?.let { calcPercent(howMuchProceed(), it) }

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
