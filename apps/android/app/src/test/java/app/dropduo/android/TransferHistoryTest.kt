package app.dropduo.android

import app.dropduo.core.TransferProgress
import app.dropduo.core.Wire
import org.junit.Assert.*
import org.junit.Test

class TransferHistoryTest {
    @Test fun liveEstimatesAreNeverSavedOrRestoredFromHistory() {
        val transfer = Transfer("id", "video.mp4", "Sent", "Sending", 0.5,
            stats = TransferProgress(1000, 0, 0.0).advance(500, 2.0))
        val json = Wire.gson.toJson(transfer)
        assertFalse(json.contains("stats"))
        assertFalse(json.contains("updatedAt"))
        val restored = Wire.gson.fromJson(json, Transfer::class.java)
        assertNull(restored.stats)
        assertEquals(0.5, restored.progress, 0.0)
    }
}
