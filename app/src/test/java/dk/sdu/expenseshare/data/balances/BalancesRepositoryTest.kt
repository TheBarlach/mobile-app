package dk.sdu.expenseshare.data.balances

import dk.sdu.expenseshare.data.FakeBalancesApi
import dk.sdu.expenseshare.data.InMemoryTextStore
import dk.sdu.expenseshare.data.local.BalancesCache
import dk.sdu.expenseshare.data.remote.PaymentRow
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import dk.sdu.expenseshare.data.summerTripRow
import dk.sdu.expenseshare.data.sync.CompletedWrite
import dk.sdu.expenseshare.data.sync.Outbox
import dk.sdu.expenseshare.data.sync.PendingWrite
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.data.sync.RetryPolicy
import dk.sdu.expenseshare.domain.balances.userBalanceSummary
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BalancesRepositoryTest {
    private val api = FakeBalancesApi(groups = listOf(summerTripRow))
    private val cacheStore = InMemoryTextStore()
    private val outboxStore = InMemoryTextStore()
    private val network = MutableStateFlow(true)
    private var now = 1_000L
    private var nextId = 0

    private fun TestScope.repository(): BalancesRepository {
        val outbox =
            Outbox(outboxStore, api::send, backgroundScope, RetryPolicy(maxAttempts = 2, initialDelay = 1.seconds))
        return BalancesRepository(
            api = api,
            cache = BalancesCache(cacheStore),
            outbox = outbox,
            scope = backgroundScope,
            networkAvailable = network,
            clock = { now },
            newId = { "write-${nextId++}" }
        )
    }

    private suspend fun refreshError(repository: BalancesRepository): RemoteRequestException = try {
        repository.refresh()
        throw AssertionError("Expected the refresh to fail")
    } catch (e: RemoteRequestException) {
        e
    }

    private fun BalancesRepository.myBalance() = groups.value?.let { userBalanceSummary(it.groups, "me").totalBalance }

    @Test
    fun `refresh fetches the groups and caches them`() = runTest {
        val repository = repository()
        runCurrent()

        repository.refresh()

        val snapshot = repository.groups.value
        assertEquals(listOf("trip"), snapshot?.groups?.map { it.id })
        assertFalse(snapshot?.isFromCache ?: true)
        assertEquals(1_000L, snapshot?.fetchedAtMillis)
        assertTrue(cacheStore.text.orEmpty().contains("Summer Trip"))
    }

    @Test
    fun `previously fetched balances are available offline`() = runTest {
        repository().refresh()
        runCurrent()

        // Next app start, without a connection.
        api.online = false
        val offlineRepository = repository()
        runCurrent()

        val snapshot = offlineRepository.groups.value
        assertTrue(snapshot?.isFromCache ?: false)
        assertEquals(1_000L, snapshot?.fetchedAtMillis)
        assertEquals(10_000L, offlineRepository.myBalance())

        assertTrue(refreshError(offlineRepository).retryable)
        // A failed refresh keeps the cached balances.
        assertEquals(10_000L, offlineRepository.myBalance())
    }

    @Test
    fun `fresh data replaces cached data`() = runTest {
        repository().refresh()
        val repository = repository()
        runCurrent()
        assertTrue(repository.groups.value?.isFromCache ?: false)

        now = 5_000
        repository.refresh()

        assertFalse(repository.groups.value?.isFromCache ?: true)
        assertEquals(5_000L, repository.groups.value?.fetchedAtMillis)
    }

    @Test
    fun `nothing is shown before the first fetch when there is no cache`() = runTest {
        api.online = false
        val repository = repository()
        runCurrent()

        assertNull(repository.groups.value)
    }

    @Test
    fun `invalid data from the server is reported instead of crashing`() = runTest {
        val broken = summerTripRow.copy(expenses = summerTripRow.expenses.map { it.copy(amount = 1) })
        api.groups = listOf(broken)
        val repository = repository()
        runCurrent()

        assertFalse(refreshError(repository).retryable)
    }

    @Test
    fun `a recorded payment is sent and the balances are refreshed`() = runTest {
        val repository = repository()
        runCurrent()
        repository.refresh()
        // The server will include the payment in the next fetch.
        api.sendResult =
            { api.groups = listOf(summerTripRow.copy(payments = listOf(PaymentRow("p", "alex", "me", 10_000)))) }

        repository.recordPayment("trip", "alex", 10_000)
        runCurrent()

        assertEquals(listOf(PendingWrite.RecordPayment("write-0", "trip", "alex", 10_000)), api.sent)
        assertEquals(0L, repository.myBalance())
    }

    @Test
    fun `the same reminder is not queued twice`() = runTest {
        val repository = repository()
        runCurrent()

        repository.sendReminder("trip", "alex")
        repository.sendReminder("trip", "alex")
        runCurrent()
        repository.sendReminder("trip", "alex")
        runCurrent()

        assertEquals(1, api.sent.size)
    }

    @Test
    fun `a payment is not queued twice while it is being sent`() = runTest {
        api.online = false
        val repository = repository()
        runCurrent()

        repository.recordPayment("trip", "alex", 10_000)
        repository.recordPayment("trip", "alex", 10_000)
        runCurrent()

        assertEquals(1, repository.writes.value.size)
    }

    @Test
    fun `writes are synchronized when connectivity returns and the user is told`() = runTest {
        api.online = false
        network.value = false
        val repository = repository()
        runCurrent()

        repository.sendReminder("trip", "alex")
        advanceTimeBy(10_000)
        assertEquals(RequestStatus.Failed, repository.writes.value.single().status)
        assertEquals(emptyList<CompletedWrite>(), repository.syncNotices.value)

        api.online = true
        network.value = true
        runCurrent()

        assertEquals(RequestStatus.Successful, repository.writes.value.single().status)
        val notice = repository.syncNotices.value.single()
        assertTrue(notice.afterFailure)
        // The groups are fetched again as well.
        assertEquals(10_000L, repository.myBalance())

        repository.dismissNotice(notice)
        assertEquals(emptyList<CompletedWrite>(), repository.syncNotices.value)
    }

    @Test
    fun `a write that succeeds at once gives no notice`() = runTest {
        val repository = repository()
        runCurrent()

        repository.sendReminder("trip", "alex")
        runCurrent()

        assertEquals(RequestStatus.Successful, repository.writes.value.single().status)
        assertEquals(emptyList<CompletedWrite>(), repository.syncNotices.value)
    }
}
