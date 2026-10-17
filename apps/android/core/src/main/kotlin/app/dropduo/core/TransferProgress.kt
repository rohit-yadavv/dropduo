package app.dropduo.core

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Per-attempt progress. The initial offset is a baseline, so resumed bytes never count as speed. */
class TransferProgress private constructor(
    val totalBytes: Long,
    val transferredBytes: Long,
    val updatedAt: Double,
    private val samples: List<Sample>,
) {
    private data class Sample(val time: Double, val bytes: Long)

    constructor(totalBytes: Long, transferredBytes: Long, at: Double = now()) :
        this(totalBytes, transferredBytes, at, listOf(Sample(at, transferredBytes)))

    fun advance(bytes: Long, at: Double = now()): TransferProgress {
        if (bytes < transferredBytes || at < updatedAt) return TransferProgress(totalBytes, bytes, at)
        var first = 0
        while (first + 1 < samples.size && samples[first + 1].time <= at - 5) first++
        val recent = samples.drop(first)
        // Bound the history to about 20 samples, even on very fast links.
        val next = if (at - recent.last().time >= 0.25) recent + Sample(at, bytes) else recent
        return TransferProgress(totalBytes, bytes, at, next)
    }

    fun isStalled(at: Double = now()) = at - updatedAt >= 5

    fun bytesPerSecond(at: Double = now()): Double? {
        val first = samples.first()
        if (isStalled(at) || updatedAt - first.time < 1 || transferredBytes <= first.bytes) return null
        return (transferredBytes - first.bytes) / (max(at, updatedAt) - first.time)
    }

    fun remainingSeconds(at: Double = now()): Double? = bytesPerSecond(at)?.let {
        (totalBytes - transferredBytes).takeIf { left -> left > 0 }?.div(it)
    }

    val sizeDescription get() = "${formatBytes(transferredBytes.toDouble())} / ${formatBytes(totalBytes.toDouble())}"

    fun rateDescription(at: Double = now()): String {
        if (isStalled(at)) return "Waiting for data…"
        val rate = bytesPerSecond(at) ?: return "Calculating time left…"
        val remaining = remainingSeconds(at) ?: return "Finishing…"
        return "${formatBytes(rate)}/s · ${formatRemaining(remaining)}"
    }

    companion object {
        fun now(): Double = System.nanoTime() / 1_000_000_000.0

        private fun formatBytes(bytes: Double): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes; var unit = 0
            while (value >= 1000 && unit < units.lastIndex) { value /= 1000; unit++ }
            val number = String.format(Locale.getDefault(), if (unit == 0 || value >= 10) "%.0f" else "%.1f", value)
            return "$number ${units[unit]}"
        }

        private fun formatRemaining(seconds: Double): String = when {
            seconds < 10 -> "less than 10 sec left"
            seconds < 60 -> "about ${(ceil(seconds / 5) * 5).toInt()} sec left"
            seconds < 3600 -> "about ${ceil(seconds / 60).toInt()} min left"
            else -> "about ${ceil(seconds / 3600).toInt()} hr left"
        }
    }
}
