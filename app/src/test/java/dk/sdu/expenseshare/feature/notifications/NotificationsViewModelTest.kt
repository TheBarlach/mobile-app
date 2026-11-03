package dk.sdu.expenseshare.feature.notifications

import dk.sdu.expenseshare.MainDispatcherRule
import dk.sdu.expenseshare.data.FakeNotificationsApi
import dk.sdu.expenseshare.data.InMemoryTextStore
import dk.sdu.expenseshare.data.dinnerNotification
import dk.sdu.expenseshare.data.joinedNotification
import dk.sdu.expenseshare.data.local.NotificationsCache
import dk.sdu.expenseshare.data.notifications.NotificationsNotice
import dk.sdu.expenseshare.data.notifications.NotificationsRepository
import dk.sdu.expenseshare.data.notifications.ReadSyncState
import dk.sdu.expenseshare.data.reminderNotification
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.data.sync.RetryPolicy
import dk.sdu.expenseshare.domain.NotificationType
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val api = FakeNotificationsApi(listOf(dinnerNotification, reminderNotification, joinedNotification))
    private val cacheStore = InMemoryTextStore()
    private val network = MutableStateFlow(true)

    private fun TestScope.viewModel(scope: CoroutineScope = backgroundScope) = NotificationsViewModel(
        NotificationsRepository(
            api,
            NotificationsCache(cacheStore),
            scope,
            networkAvailable = network,
            retryPolicy = RetryPolicy(maxAttempts = 2, initialDelay = 1.seconds)
        )
    )

    @Test
    fun `shows the notifications and the unread count after loading`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isShowingSavedData)
        assertEquals(
            listOf(NotificationType.ExpenseAdded, NotificationType.PaymentReminder, NotificationType.MemberJoined),
            state.notifications?.map { it.type }
        )
        assertEquals(2, state.unreadCount)
    }

    @Test
    fun `opening a notification marks it as read`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        runCurrent()

        viewModel.markRead("n-dinner")
        runCurrent()

        assertEquals(1, viewModel.state.value.unreadCount)
        assertEquals(listOf(setOf("n-dinner")), api.markReadCalls)
    }

    @Test
    fun `mark all as read sends every unread notification in one request`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        runCurrent()

        viewModel.markAllRead()
        runCurrent()

        assertEquals(0, viewModel.state.value.unreadCount)
        assertEquals(listOf(setOf("n-dinner", "n-reminder")), api.markReadCalls)
    }

    @Test
    fun `offline the saved notifications are shown with the failed refresh`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel(CoroutineScope(backgroundScope.coroutineContext + Job()))
        runCurrent()
        api.online = false

        val offline = viewModel()
        runCurrent()

        val state = offline.state.value
        assertTrue(state.isShowingSavedData)
        assertTrue(state.refreshFailed)
        assertEquals(3, state.notifications?.size)
    }

    @Test
    fun `without saved data a failed refresh shows an error, and retrying recovers`() =
        runTest(mainDispatcherRule.dispatcher) {
            api.online = false
            val viewModel = viewModel()
            runCurrent()

            assertNull(viewModel.state.value.notifications)
            assertTrue(viewModel.state.value.refreshFailed)
            assertFalse(viewModel.state.value.isLoading)

            api.online = true
            viewModel.refresh()
            runCurrent()

            assertFalse(viewModel.state.value.refreshFailed)
            assertEquals(3, viewModel.state.value.notifications?.size)
        }

    @Test
    fun `read status goes from pending to synced, and the user is told once`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            api.online = false

            viewModel.markRead("n-dinner")
            runCurrent()
            assertEquals(
                ReadSyncState(RequestStatus.Pending, isWaitingToRetry = true),
                viewModel.state.value.readSync
            )

            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(ReadSyncState(RequestStatus.Failed), viewModel.state.value.readSync)

            api.online = true
            viewModel.retryReadSync()
            runCurrent()

            assertNull(viewModel.state.value.readSync)
            assertEquals(NotificationsNotice.ReadStatusSynced, viewModel.state.value.notice)

            viewModel.onNoticeShown(NotificationsNotice.ReadStatusSynced)
            runCurrent()
            assertNull(viewModel.state.value.notice)
        }
}
