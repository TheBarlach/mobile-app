package dk.sdu.expenseshare.data.notifications

import dk.sdu.expenseshare.data.local.CachedNotifications
import dk.sdu.expenseshare.data.local.NotificationsCache
import dk.sdu.expenseshare.data.remote.NotificationRow
import dk.sdu.expenseshare.data.remote.NotificationsApi
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import dk.sdu.expenseshare.data.remote.toDomainOrNull
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.data.sync.RetryPolicy
import dk.sdu.expenseshare.domain.AppNotification
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The user's notifications and where they came from. [isFromCache] is true while the app shows
 * notifications saved on the device because nothing newer has been fetched in this session.
 */
data class NotificationsSnapshot(
    val notifications: List<AppNotification>,
    val fetchedAtMillis: Long,
    val isFromCache: Boolean
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
}

/**
 * Progress of sending read marks to the backend. [isWaitingToRetry] is true after a failed
 * attempt while another one is planned (usually because the device is offline).
 */
data class ReadSyncState(val status: RequestStatus, val isWaitingToRetry: Boolean = false)

/** Something that succeeded in the background after failing, which the user should be told. */
enum class NotificationsNotice {
    /** Notifications were fetched again after connectivity returned. */
    UpdatedAfterReconnect,

    /** Read marks reached the backend after earlier attempts failed. */
    ReadStatusSynced
}

/**
 * Notifications for one signed-in user.
 *
 * - Fetched notifications are cached, so the last ones stay visible offline.
 * - Marking as read is shown immediately. The ids are saved as pending and sent in the background,
 *   with [retryPolicy]'s bounded backoff for temporary failures. They are sent again when
 *   connectivity returns, after a restart, or on [retryReadSync]. The backend function ignores
 *   ids that are already read, so a repeated request does no harm.
 *
 * Unlike the balances `Outbox`, read marks are a set, not a queue: marking more notifications
 * while a request is failing just adds ids to the next request.
 */
class NotificationsRepository(
    private val api: NotificationsApi,
    private val cache: NotificationsCache,
    private val scope: CoroutineScope,
    networkAvailable: Flow<Boolean> = emptyFlow(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Fetched(val rows: List<NotificationRow>, val fetchedAtMillis: Long, val isFromCache: Boolean)

    private val mutex = Mutex()
    private val restored = CompletableDeferred<Unit>()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val skipBackoff = Channel<Unit>(Channel.CONFLATED)
    private val lastRefreshFailed = AtomicBoolean(false)

    /**
     * True once sending read marks failed, until they are sent. Kept across send runs, so the
     * user is also told when marks that failed earlier are sent after a reconnect or a retry.
     * Only used by the coroutine that sends read marks.
     */
    private var readSyncHadFailure = false

    private val fetched = MutableStateFlow<Fetched?>(null)
    private val pendingReads = MutableStateFlow<Set<String>>(emptySet())

    /** The notifications to show, with unsent read marks already applied. Null until loaded. */
    val notifications: Flow<NotificationsSnapshot?> =
        combine(fetched, pendingReads) { fetched, pending -> fetched?.toSnapshot(pending) }

    private val _readSync = MutableStateFlow<ReadSyncState?>(null)

    /** Null when there is nothing to send. */
    val readSync: StateFlow<ReadSyncState?> = _readSync.asStateFlow()

    private val _notices = MutableStateFlow<List<NotificationsNotice>>(emptyList())

    /** Background successes the user has not been told about yet. Remove shown ones with [dismissNotice]. */
    val notices: StateFlow<List<NotificationsNotice>> = _notices.asStateFlow()

    init {
        scope.launch {
            restore()
            for (signal in wakeUp) sendPendingReads()
        }
        scope.launch {
            // Only react to the network coming back, not to the first value.
            networkAvailable.distinctUntilChanged().drop(1).filter { it }.collect {
                skipBackoff.trySend(Unit)
                wakeUp.trySend(Unit)
                refreshInBackground()
            }
        }
    }

    /**
     * Fetches the notifications from the backend and caches them.
     *
     * @throws RemoteRequestException if the request fails. Cached notifications stay available.
     */
    suspend fun refresh() {
        fetch()
    }

    /** Marks [ids] as read straight away and sends the change in the background. */
    suspend fun markRead(ids: Collection<String>) {
        restored.await()
        val added =
            mutex.withLock {
                val unread =
                    fetched.value?.rows.orEmpty()
                        .filter { it.id in ids && it.readAt == null && it.id !in pendingReads.value }
                        .map { it.id }
                if (unread.isNotEmpty()) {
                    pendingReads.update { it + unread }
                    persist()
                }
                unread.isNotEmpty()
            }
        if (added) wakeUp.trySend(Unit)
    }

    /** Sends pending read marks again now, e.g. after they failed. */
    fun retryReadSync() {
        skipBackoff.trySend(Unit)
        wakeUp.trySend(Unit)
    }

    fun dismissNotice(notice: NotificationsNotice) {
        _notices.update { it - notice }
    }

    /** Returns true if the previous refresh had failed. */
    private suspend fun fetch(): Boolean {
        val rows =
            try {
                api.fetchNotifications()
            } catch (e: RemoteRequestException) {
                lastRefreshFailed.set(true)
                throw e
            }
        restored.await()
        mutex.withLock {
            val readOnServer = rows.filter { it.readAt != null }.map { it.id }.toSet()
            pendingReads.update { it - readOnServer }
            fetched.value = Fetched(rows, clock(), isFromCache = false)
            persist()
        }
        return lastRefreshFailed.getAndSet(false)
    }

    private suspend fun refreshInBackground() {
        try {
            if (fetch()) addNotice(NotificationsNotice.UpdatedAfterReconnect)
        } catch (_: RemoteRequestException) {
            // Still offline or the server is down; the screen shows the saved notifications.
        }
    }

    private suspend fun restore() {
        val cached = cache.load()
        mutex.withLock {
            if (cached != null) {
                // Never replace notifications that were fetched while the cache was being read.
                fetched.compareAndSet(null, Fetched(cached.notifications, cached.fetchedAtMillis, isFromCache = true))
                pendingReads.update { it + cached.pendingReadIds }
            }
        }
        restored.complete(Unit)
        wakeUp.trySend(Unit)
    }

    private suspend fun sendPendingReads() {
        // A stale "skip" signal must not cut the first backoff of this run short.
        skipBackoff.tryReceive()
        var failedAttempts = 0
        while (true) {
            val batch = pendingReads.value
            if (batch.isEmpty()) {
                _readSync.value = null
                return
            }
            _readSync.value = ReadSyncState(RequestStatus.Pending, isWaitingToRetry = failedAttempts > 0)
            try {
                api.markRead(batch)
                onReadsSent(batch)
                if (readSyncHadFailure) addNotice(NotificationsNotice.ReadStatusSynced)
                readSyncHadFailure = false
                failedAttempts = 0
                // Loop: ids marked while the request was running are sent next.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedAttempts++
                readSyncHadFailure = true
                val retryable = (e as? RemoteRequestException)?.retryable ?: false
                if (!retryable || failedAttempts >= retryPolicy.maxAttempts) {
                    // Kept as pending; sent again when connectivity returns, on restart or on retry.
                    _readSync.value = ReadSyncState(RequestStatus.Failed)
                    return
                }
                _readSync.value = ReadSyncState(RequestStatus.Pending, isWaitingToRetry = true)
                withTimeoutOrNull(retryPolicy.delayAfter(failedAttempts)) { skipBackoff.receive() }
            }
        }
    }

    private suspend fun onReadsSent(sent: Set<String>) = mutex.withLock {
        // Record the read state on the rows themselves, so they stay read once no longer pending.
        val readAt = Instant.ofEpochMilli(clock()).toString()
        fetched.update { current ->
            current?.copy(
                rows = current.rows.map {
                    if (it.id in sent &&
                        it.readAt == null
                    ) {
                        it.copy(readAt = readAt)
                    } else {
                        it
                    }
                }
            )
        }
        pendingReads.update { it - sent }
        persist()
    }

    /** Must be called with [mutex] held. */
    private suspend fun persist() {
        val current = fetched.value ?: return
        cache.save(CachedNotifications(current.rows, current.fetchedAtMillis, pendingReads.value))
    }

    private fun addNotice(notice: NotificationsNotice) {
        _notices.update { if (notice in it) it else it + notice }
    }

    private fun Fetched.toSnapshot(pending: Set<String>): NotificationsSnapshot {
        val notifications =
            rows.mapNotNull { row ->
                val notification =
                    try {
                        row.toDomainOrNull()
                    } catch (_: IllegalArgumentException) {
                        // One broken row must not hide the others.
                        null
                    }
                if (notification != null &&
                    notification.id in pending
                ) {
                    notification.copy(isRead = true)
                } else {
                    notification
                }
            }
        return NotificationsSnapshot(notifications, fetchedAtMillis, isFromCache)
    }
}
