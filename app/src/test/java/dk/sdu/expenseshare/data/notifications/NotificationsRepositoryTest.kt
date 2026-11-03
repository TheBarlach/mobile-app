package dk.sdu.expenseshare.data.notifications

import dk.sdu.expenseshare.data.FakeNotificationsApi
import dk.sdu.expenseshare.data.InMemoryTextStore
import dk.sdu.expenseshare.data.dinnerNotification
import dk.sdu.expenseshare.data.joinedNotification
import dk.sdu.expenseshare.data.local.CachedNotifications
import dk.sdu.expenseshare.data.local.NotificationsCache
import dk.sdu.expenseshare.data.reminderNotification
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.data.sync.RetryPolicy
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsRepositoryTest {
    private val api = FakeNotificationsApi(listOf(dinnerNotification, reminderNotification, joinedNotification))
    private val cacheStore = InMemoryTextStore()
    private val network = MutableStateFlow(true)

    // Waits 1 s, then 2 s; gives up after the third failed attempt.
    private val retryPolicy = RetryPolicy(maxAttempts = 3, initialDelay = 1.seconds, maxDelay = 4.seconds)

    private fun TestScope.repository(scope: CoroutineScope = backgroundScope) = NotificationsRepository(
        api = api,
        cache = NotificationsCache(cacheStore),
        scope = scope,
        networkAvailable = network,
        retryPolicy = retryPolicy,
        clock = { 1_000L }
    )

    private suspend fun NotificationsRepository.snapshot() = notifications.first()

    private suspend fun NotificationsRepository.readIds() =
        snapshot()?.notifications.orEmpty().filter { it.isRead }.map { it.id }.toSet()

    private fun savedCache() = cacheStore.text?.let { Json.decodeFromString<CachedNotifications>(it) }

    @Test
    fun `refresh shows the notifications and saves them on the device`() = runTest {
        val repository = repository()

        repository.refresh()

        val snapshot = repository.snapshot()
        assertEquals(listOf("n-dinner", "n-reminder", "n-joined"), snapshot?.notifications?.map { it.id })
        assertEquals(2, snapshot?.unreadCount)
        assertFalse(snapshot?.isFromCache == true)
        assertEquals(api.rows, savedCache()?.notifications)
    }

    @Test
    fun `saved notifications are shown offline and marked as saved`() = runTest {
        repository().refresh()
        api.online = false

        val offline = repository(CoroutineScope(backgroundScope.coroutineContext + Job()))
        runCurrent()

        try {
            offline.refresh()
            fail("Expected the refresh to fail while offline")
        } catch (e: RemoteRequestException) {
            assertTrue(e.retryable)
        }
        val snapshot = offline.snapshot()
        assertTrue(snapshot?.isFromCache == true)
        assertEquals(3, snapshot?.notifications?.size)
        assertEquals(1_000L, snapshot?.fetchedAtMillis)
    }

    @Test
    fun `a broken cache is treated as empty`() = runTest {
        cacheStore.text = "{not json"
        val repository = repository()
        runCurrent()

        assertNull(repository.snapshot())
    }

    @Test
    fun `marking as read shows at once and reaches the backend`() = runTest {
        val repository = repository()
        repository.refresh()

        repository.markRead(listOf("n-dinner"))

        assertEquals(setOf("n-dinner", "n-joined"), repository.readIds())
        runCurrent()
        assertEquals(listOf(setOf("n-dinner")), api.markReadCalls)
        assertNull(repository.readSync.value)
        assertEquals(emptySet<String>(), savedCache()?.pendingReadIds)
        // Still read after the next fetch, now because the server says so.
        repository.refresh()
        assertEquals(setOf("n-dinner", "n-joined"), repository.readIds())
    }

    @Test
    fun `notifications that are already read are not sent again`() = runTest {
        val repository = repository()
        repository.refresh()

        repository.markRead(listOf("n-joined", "unknown-id"))
        runCurrent()

        assertTrue(api.markReadCalls.isEmpty())
    }

    @Test
    fun `a temporary failure is retried with backoff until it succeeds`() = runTest {
        val repository = repository()
        repository.refresh()
        api.online = false

        repository.markRead(listOf("n-dinner"))
        runCurrent()

        // Pending, waiting to retry, but already shown as read.
        assertEquals(ReadSyncState(RequestStatus.Pending, isWaitingToRetry = true), repository.readSync.value)
        assertTrue("n-dinner" in repository.readIds())

        api.online = true
        advanceTimeBy(999)
        runCurrent()
        assertTrue("No retry before the backoff delay", api.markReadCalls.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(setOf("n-dinner")), api.markReadCalls)
        assertNull(repository.readSync.value)
        assertEquals(listOf(NotificationsNotice.ReadStatusSynced), repository.notices.value)
    }

    @Test
    fun `retries are bounded and resume when connectivity returns`() = runTest {
        val repository = repository()
        repository.refresh()
        api.online = false
        network.value = false

        repository.markRead(listOf("n-dinner", "n-reminder"))
        runCurrent()
        advanceTimeBy(1_000 + 2_000)
        runCurrent()

        assertEquals(ReadSyncState(RequestStatus.Failed), repository.readSync.value)
        // No further attempts while offline.
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(api.markReadCalls.isEmpty())
        assertEquals(setOf("n-dinner", "n-reminder"), savedCache()?.pendingReadIds)

        api.online = true
        network.value = true
        runCurrent()

        assertEquals(listOf(setOf("n-dinner", "n-reminder")), api.markReadCalls)
        assertNull(repository.readSync.value)
        assertTrue(NotificationsNotice.ReadStatusSynced in repository.notices.value)
        assertEquals(emptySet<String>(), savedCache()?.pendingReadIds)
    }

    @Test
    fun `unsent read marks survive an app restart`() = runTest {
        val firstRun = CoroutineScope(backgroundScope.coroutineContext + Job())
        val first = repository(firstRun)
        first.refresh()
        api.online = false
        first.markRead(listOf("n-dinner"))
        runCurrent()
        firstRun.cancel()

        // The app starts again with a connection.
        api.online = true
        val second = repository()
        runCurrent()

        assertEquals(listOf(setOf("n-dinner")), api.markReadCalls)
        assertTrue("n-dinner" in second.readIds())
        assertEquals(emptySet<String>(), savedCache()?.pendingReadIds)
    }

    @Test
    fun `a rejected request fails at once and is sent again on retry`() = runTest {
        val repository = repository()
        repository.refresh()
        api.markReadResult = { throw RemoteRequestException(retryable = false, message = "function missing") }

        repository.markRead(listOf("n-dinner"))
        runCurrent()

        assertEquals(ReadSyncState(RequestStatus.Failed), repository.readSync.value)
        assertTrue("Still shown as read", "n-dinner" in repository.readIds())
        advanceTimeBy(60_000)
        runCurrent()

        api.markReadResult = null
        repository.retryReadSync()
        runCurrent()

        assertEquals(listOf(setOf("n-dinner")), api.markReadCalls)
        assertNull(repository.readSync.value)
    }

    @Test
    fun `a refresh while a read mark is unsent keeps it read`() = runTest {
        val repository = repository()
        repository.refresh()
        api.online = false
        repository.markRead(listOf("n-dinner"))
        runCurrent()

        // The server does not know yet, but the user must not see it as unread again.
        api.online = true
        repository.refresh()

        assertTrue("n-dinner" in repository.readIds())
    }

    @Test
    fun `notifications are fetched again when connectivity returns after a failed refresh`() = runTest {
        val repository = repository()
        repository.refresh()
        api.online = false
        network.value = false
        runCurrent()
        try {
            repository.refresh()
        } catch (_: RemoteRequestException) {
        }

        val newNotification = dinnerNotification.copy(id = "n-new", createdAt = "2026-11-03T11:00:00+00:00")
        api.rows = listOf(newNotification) + api.rows
        api.online = true
        network.value = true
        runCurrent()

        assertEquals("n-new", repository.snapshot()?.notifications?.first()?.id)
        assertEquals(listOf(NotificationsNotice.UpdatedAfterReconnect), repository.notices.value)

        repository.dismissNotice(NotificationsNotice.UpdatedAfterReconnect)
        assertTrue(repository.notices.value.isEmpty())
    }

    @Test
    fun `a row with broken data does not hide the others`() = runTest {
        api.rows = listOf(dinnerNotification.copy(createdAt = "broken"), reminderNotification)
        val repository = repository()

        repository.refresh()

        assertEquals(listOf("n-reminder"), repository.snapshot()?.notifications?.map { it.id })
    }
}
