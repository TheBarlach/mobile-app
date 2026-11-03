package dk.sdu.expenseshare.feature.balances

import dk.sdu.expenseshare.MainDispatcherRule
import dk.sdu.expenseshare.data.FakeBalancesApi
import dk.sdu.expenseshare.data.InMemoryTextStore
import dk.sdu.expenseshare.data.balances.BalancesRepository
import dk.sdu.expenseshare.data.local.BalancesCache
import dk.sdu.expenseshare.data.local.CachedGroups
import dk.sdu.expenseshare.data.remote.BalancesApi
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import dk.sdu.expenseshare.data.summerTripRow
import dk.sdu.expenseshare.data.sync.Outbox
import dk.sdu.expenseshare.data.sync.PendingWrite
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.data.sync.RetryPolicy
import dk.sdu.expenseshare.domain.balances.DebtDirection
import dk.sdu.expenseshare.domain.balances.UserDebt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BalancesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = FakeBalancesApi(groups = listOf(summerTripRow))
    private val cacheStore = InMemoryTextStore()
    private val network = MutableStateFlow(true)

    private fun TestScope.viewModel(): BalancesViewModel {
        val outbox =
            Outbox(
                InMemoryTextStore(),
                api::send,
                backgroundScope,
                RetryPolicy(maxAttempts = 2, initialDelay = 1.seconds)
            )
        val repository =
            BalancesRepository(api, BalancesCache(cacheStore), outbox, backgroundScope, networkAvailable = network)
        return BalancesViewModel(repository, currentUserId = "me")
    }

    private val alexOwesMe = UserDebt("alex", "Alex", 10_000, DebtDirection.OwesYou)
    private val alexKey = DebtKey("trip", "alex")

    @Test
    fun `shows the user's balances after loading`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isShowingSavedData)
        assertEquals(10_000L, state.summary?.totalBalance)
        assertEquals(listOf(alexOwesMe), state.summary?.groups?.single()?.debts)
    }

    @Test
    fun `shows loading until the first response`() = runTest(mainDispatcherRule.dispatcher) {
        val response = CompletableDeferred<Unit>()
        val slowApi =
            object : BalancesApi by api {
                override suspend fun fetchGroups() = response.await().let { api.fetchGroups() }
            }
        val outbox = Outbox(InMemoryTextStore(), slowApi::send, backgroundScope)
        val viewModel =
            BalancesViewModel(BalancesRepository(slowApi, BalancesCache(cacheStore), outbox, backgroundScope), "me")

        assertTrue(viewModel.state.value.isLoading)
        response.complete(Unit)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `offline without saved data shows an error that can be retried`() = runTest(mainDispatcherRule.dispatcher) {
        api.online = false
        val viewModel = viewModel()

        assertTrue(viewModel.state.value.refreshFailed)
        assertNull(viewModel.state.value.summary)

        api.online = true
        viewModel.refresh()

        assertFalse(viewModel.state.value.refreshFailed)
        assertEquals(10_000L, viewModel.state.value.summary?.totalBalance)
    }

    @Test
    fun `offline with saved data shows the saved balances and when they are from`() =
        runTest(mainDispatcherRule.dispatcher) {
            cacheStore.text = Json.encodeToString(CachedGroups(listOf(summerTripRow), fetchedAtMillis = 1234))
            api.online = false

            val viewModel = viewModel()
            runCurrent()

            val state = viewModel.state.value
            assertTrue(state.isShowingSavedData)
            assertTrue(state.refreshFailed)
            assertEquals(1234L, state.lastUpdatedMillis)
            assertEquals(10_000L, state.summary?.totalBalance)
        }

    @Test
    fun `a reminder goes from pending to successful`() = runTest(mainDispatcherRule.dispatcher) {
        val sent = CompletableDeferred<Unit>()
        api.sendResult = { sent.await() }
        val viewModel = viewModel()

        viewModel.remind("trip", "alex")
        assertEquals(RequestStatus.Pending, viewModel.state.value.reminders[alexKey]?.status)

        sent.complete(Unit)
        assertEquals(RequestStatus.Successful, viewModel.state.value.reminders[alexKey]?.status)
    }

    @Test
    fun `a reminder includes the amount shown, for the receiver's notification`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel()

            viewModel.remind("trip", "alex")

            assertEquals(listOf(PendingWrite.SendReminder(api.sent.single().id, "trip", "alex", 10_000)), api.sent)
        }

    @Test
    fun `a reminder sent while offline waits, fails, and succeeds when the user retries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel()
            api.online = false

            viewModel.remind("trip", "alex")
            assertTrue(viewModel.state.value.reminders[alexKey]?.isWaitingToRetry ?: false)

            advanceTimeBy(10_000)
            val failed = viewModel.state.value.reminders[alexKey]
            assertEquals(RequestStatus.Failed, failed?.status)

            api.online = true
            viewModel.retryWrite(failed?.writeId.orEmpty())

            assertEquals(RequestStatus.Successful, viewModel.state.value.reminders[alexKey]?.status)
            // The user is told that the earlier failed request went through.
            val message = viewModel.state.value.message
            assertEquals("Alex", message?.memberName)
            assertFalse(message?.isPayment ?: true)

            viewModel.onMessageShown(message ?: error("no message"))
            assertNull(viewModel.state.value.message)
        }

    @Test
    fun `a payment is shown as pending until it is saved`() = runTest(mainDispatcherRule.dispatcher) {
        val saved = CompletableDeferred<Unit>()
        api.sendResult = { saved.await() }
        val viewModel = viewModel()

        viewModel.recordPayment("trip", "alex", 10_000)
        assertEquals(RequestStatus.Pending, viewModel.state.value.payments[alexKey]?.status)

        saved.complete(Unit)
        // Finished payments are reflected in the refreshed balances, not as a row status.
        assertNull(viewModel.state.value.payments[alexKey])
        assertEquals(2, api.fetchCount)
    }

    @Test
    fun `a rejected payment is shown as failed`() = runTest(mainDispatcherRule.dispatcher) {
        api.sendResult = { throw RemoteRequestException(retryable = false, message = "rejected") }
        val viewModel = viewModel()

        viewModel.recordPayment("trip", "alex", 10_000)

        assertEquals(RequestStatus.Failed, viewModel.state.value.payments[alexKey]?.status)
    }
}
