package dk.sdu.expenseshare.data.sync

import dk.sdu.expenseshare.data.InMemoryTextStore
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxTest {
    private val store = InMemoryTextStore()
    private val policy = RetryPolicy(maxAttempts = 4, initialDelay = 2.seconds, maxDelay = 5.seconds)

    private val reminder = PendingWrite.SendReminder(id = "r1", groupId = "trip", toUserId = "alex")
    private val payment = PendingWrite.RecordPayment(id = "p1", groupId = "trip", toUserId = "john", amount = 10_000)

    /** Backend stand-in that records the virtual time of every attempt. */
    private inner class Backend(private val scheduler: TestScope) {
        var online = true
        var reject = false
        val attemptTimes = mutableListOf<Long>()
        val delivered = mutableListOf<PendingWrite>()

        suspend fun send(write: PendingWrite) {
            attemptTimes += scheduler.testScheduler.currentTime
            if (reject) throw RemoteRequestException(retryable = false, message = "rejected")
            if (!online) throw RemoteRequestException(retryable = true, message = "offline")
            delivered += write
        }
    }

    private fun TestScope.outbox(backend: Backend, scope: CoroutineScope = backgroundScope) =
        Outbox(store, backend::send, scope, policy)

    private fun Outbox.status(id: String) = entries.value.single { it.write.id == id }.status

    @Test
    fun `a write is sent straight away when online`() = runTest {
        val backend = Backend(this)
        val outbox = outbox(backend)
        val completed = mutableListOf<CompletedWrite>()
        backgroundScope.launch { outbox.completed.toList(completed) }
        runCurrent()

        outbox.enqueue(reminder)
        runCurrent()

        assertEquals(listOf(reminder), backend.delivered)
        assertEquals(RequestStatus.Successful, outbox.status("r1"))
        assertEquals(listOf(CompletedWrite(reminder, afterFailure = false)), completed)
        assertEquals("[]", store.text)
    }

    @Test
    fun `a temporary failure is retried in the background with backoff`() = runTest {
        val backend = Backend(this).apply { online = false }
        val outbox = outbox(backend)
        val completed = mutableListOf<CompletedWrite>()
        backgroundScope.launch { outbox.completed.toList(completed) }
        runCurrent()

        outbox.enqueue(payment)
        runCurrent()

        // Pending while it waits to retry, not failed.
        val waiting = outbox.entries.value.single()
        assertEquals(RequestStatus.Pending, waiting.status)
        assertTrue(waiting.isWaitingToRetry)

        advanceTimeBy(2_001) // first retry after 2 s, still offline
        backend.online = true
        advanceTimeBy(4_001) // second retry after another 4 s

        assertEquals(listOf(0L, 2_000L, 6_000L), backend.attemptTimes)
        assertEquals(RequestStatus.Successful, outbox.status("p1"))
        assertEquals(listOf(CompletedWrite(payment, afterFailure = true)), completed)
    }

    @Test
    fun `retries are bounded and the delay is capped`() = runTest {
        val backend = Backend(this).apply { online = false }
        val outbox = outbox(backend)
        runCurrent()

        outbox.enqueue(reminder)
        advanceTimeBy(60_000)

        // 4 attempts: at 0, +2 s, +4 s and +5 s (capped at maxDelay), then it gives up.
        assertEquals(listOf(0L, 2_000L, 6_000L, 11_000L), backend.attemptTimes)
        val entry = outbox.entries.value.single()
        assertEquals(RequestStatus.Failed, entry.status)
        assertTrue(entry.retryable)
    }

    @Test
    fun `writes are sent once connectivity returns`() = runTest {
        val backend = Backend(this).apply { online = false }
        val outbox = outbox(backend)
        runCurrent()
        outbox.enqueue(reminder)
        outbox.enqueue(payment)
        advanceTimeBy(60_000)
        assertEquals(RequestStatus.Failed, outbox.status("r1"))
        assertEquals(RequestStatus.Failed, outbox.status("p1"))
        // Each write got its own bounded attempts; queuing the payment did not restart the reminder's.
        assertEquals(8, backend.attemptTimes.size)

        backend.online = true
        outbox.syncNow()
        runCurrent()

        assertEquals(listOf(reminder, payment), backend.delivered)
        assertEquals(RequestStatus.Successful, outbox.status("r1"))
        assertEquals(RequestStatus.Successful, outbox.status("p1"))
    }

    @Test
    fun `connectivity returning cuts the backoff short`() = runTest {
        val backend = Backend(this).apply { online = false }
        val outbox = outbox(backend)
        runCurrent()
        outbox.enqueue(reminder)
        runCurrent()

        advanceTimeBy(500)
        backend.online = true
        outbox.syncNow()
        runCurrent()

        assertEquals(listOf(0L, 500L), backend.attemptTimes)
        assertEquals(RequestStatus.Successful, outbox.status("r1"))
    }

    @Test
    fun `a rejected write fails at once and does not block later writes`() = runTest {
        val backend = Backend(this).apply { reject = true }
        val outbox = outbox(backend)
        runCurrent()

        outbox.enqueue(reminder)
        runCurrent()
        backend.reject = false
        outbox.enqueue(payment)
        runCurrent()

        assertEquals(2, backend.attemptTimes.size) // one attempt each, no retries
        val rejected = outbox.entries.value.first()
        assertEquals(RequestStatus.Failed, rejected.status)
        assertFalse(rejected.retryable)
        assertEquals(listOf(payment), backend.delivered)

        // Not retried automatically, even when connectivity "returns".
        outbox.syncNow()
        runCurrent()
        assertEquals(RequestStatus.Failed, outbox.status("r1"))
    }

    @Test
    fun `the user can retry a failed write`() = runTest {
        val backend = Backend(this).apply { reject = true }
        val outbox = outbox(backend)
        runCurrent()
        outbox.enqueue(reminder)
        runCurrent()

        backend.reject = false
        outbox.retry("r1")
        runCurrent()

        assertEquals(RequestStatus.Successful, outbox.status("r1"))
        assertEquals(listOf(reminder), backend.delivered)
    }

    @Test
    fun `unsent writes survive an app restart and are sent with the same id`() = runTest {
        val offlineBackend = Backend(this).apply { online = false }
        val firstRun = CoroutineScope(coroutineContext + Job())
        val first = outbox(offlineBackend, firstRun)
        runCurrent()
        first.enqueue(payment)
        runCurrent()
        firstRun.cancel() // the app is killed

        val backend = Backend(this)
        val second = outbox(backend)
        runCurrent()

        assertEquals(listOf(payment), backend.delivered)
        assertEquals(RequestStatus.Successful, second.status("p1"))
        assertEquals("[]", store.text)
    }

    @Test
    fun `after a restart offline the queue pauses after the first write runs out of attempts`() = runTest {
        val firstRun = CoroutineScope(coroutineContext + Job())
        val first = outbox(Backend(this).apply { reject = true }, firstRun)
        runCurrent()
        // Saved as pending: the app is killed before they are sent.
        firstRun.cancel()
        store.text = Json.encodeToString(listOf(OutboxEntry(reminder), OutboxEntry(payment)))

        val backend = Backend(this).apply { online = false }
        val outbox = outbox(backend)
        advanceTimeBy(60_000)

        assertEquals(4, backend.attemptTimes.size)
        assertEquals(RequestStatus.Failed, outbox.status("r1"))
        assertEquals(RequestStatus.Pending, outbox.status("p1"))
        assertEquals(0, first.entries.value.size)
    }

    @Test
    fun `a broken saved queue is ignored`() = runTest {
        store.text = "{ not json"
        val backend = Backend(this)
        val outbox = outbox(backend)
        runCurrent()

        assertEquals(emptyList<OutboxEntry>(), outbox.entries.value)
        outbox.enqueue(reminder)
        runCurrent()
        assertEquals(listOf(reminder), backend.delivered)
    }

    @Test
    fun `a failing disk does not stop writes from being sent`() = runTest {
        store.failWrites = true
        val backend = Backend(this)
        val outbox = outbox(backend)
        runCurrent()

        outbox.enqueue(reminder)
        runCurrent()

        assertEquals(listOf(reminder), backend.delivered)
    }

    @Test
    fun `backoff doubles up to the maximum delay`() {
        val retry = RetryPolicy(maxAttempts = 10, initialDelay = 1.seconds, maxDelay = 10.seconds)

        assertEquals(listOf(1, 2, 4, 8, 10, 10).map { it.seconds }, (1..6).map(retry::delayAfter))
        assertEquals(10.seconds, retry.delayAfter(1_000))
    }
}
