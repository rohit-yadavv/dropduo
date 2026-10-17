package app.dropduo.core

import org.junit.Assert.*
import org.junit.Test

class TransferProgressTest {
    @Test fun waitsForSamplesAndExcludesTheResumeOffset() {
        var stats = TransferProgress(1000, 800, 100.0)
        assertNull(stats.bytesPerSecond(100.0))
        stats = stats.advance(825, 100.5)
        assertNull(stats.remainingSeconds(100.5))
        stats = stats.advance(900, 102.0)
        assertEquals(50.0, stats.bytesPerSecond(102.0)!!, 0.001)
        assertEquals(2.0, stats.remainingSeconds(102.0)!!, 0.001)
    }

    @Test fun slowsTheEstimateAndHidesItOnAStalledConnection() {
        val stats = TransferProgress(1000, 0, 0.0).advance(200, 2.0)
        assertEquals(100.0, stats.bytesPerSecond(2.0)!!, 0.001)
        assertEquals(50.0, stats.bytesPerSecond(4.0)!!, 0.001)
        assertEquals(16.0, stats.remainingSeconds(4.0)!!, 0.001)
        assertNull(stats.bytesPerSecond(7.0))
        assertNull(stats.remainingSeconds(7.0))
        assertEquals("Waiting for data…", stats.rateDescription(7.0))
    }

    @Test fun usesRecentSpeedAndResetsWhenProgressStartsAgain() {
        var stats = TransferProgress(100_000, 0, 0.0)
        for (second in 1..5) stats = stats.advance(second * 100L, second.toDouble())
        for (second in 6..11) stats = stats.advance(500 + (second - 5) * 1000L, second.toDouble())
        assertEquals(1000.0, stats.bytesPerSecond(11.0)!!, 0.001)
        stats = stats.advance(10, 12.0)
        assertNull(stats.bytesPerSecond(12.0))
        stats = stats.advance(20, 13.0)
        assertEquals(10.0, stats.bytesPerSecond(13.0)!!, 0.001)
    }

    @Test fun handlesEmptyFinishedAndLargeFilesWithoutBogusEstimates() {
        assertNull(TransferProgress(0, 0, 0.0).remainingSeconds(1.0))
        assertNull(TransferProgress(1000, 0, 0.0).advance(1000, 2.0).remainingSeconds(2.0))
        val gib = 1024L * 1024 * 1024
        val stats = TransferProgress(4 * gib, gib, 0.0).advance(2 * gib, 2.0)
        assertEquals(gib / 2.0, stats.bytesPerSecond(2.0)!!, 0.001)
        assertEquals(4.0, stats.remainingSeconds(2.0)!!, 0.001)
        assertTrue(stats.rateDescription(2.0).endsWith("less than 10 sec left"))
        assertEquals("Calculating time left…", TransferProgress(1000, 0, 0.0).rateDescription(0.0))
    }
}
