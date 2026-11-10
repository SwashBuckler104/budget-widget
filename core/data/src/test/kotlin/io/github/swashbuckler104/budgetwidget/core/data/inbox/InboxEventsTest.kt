package io.github.swashbuckler104.budgetwidget.core.data.inbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric: the failure path logs through android.util.Log.
@RunWith(AndroidJUnit4::class)
class InboxEventsTest {
    @Test
    fun `a failing listener does not stop the others or the caller`() = runTest {
        val failing = object : InboxListener {
            override suspend fun onSorted(transactionId: Long): Unit = throw SecurityException("no permission")
        }
        val recording = RecordingInboxListener()
        val events = InboxEvents(linkedSetOf(failing, recording))

        events.sorted(7)
        events.pendingAdded(8, IngestOrigin.REALTIME)

        assertThat(recording.events).containsExactly("sorted:7", "added:8:REALTIME").inOrder()
    }
}
