package dk.sdu.expenseshare.data.balances

import dk.sdu.expenseshare.data.local.BalancesCache
import dk.sdu.expenseshare.data.local.CachedGroups
import dk.sdu.expenseshare.data.remote.BalancesApi
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import dk.sdu.expenseshare.data.remote.toDomain
import dk.sdu.expenseshare.data.sync.CompletedWrite
import dk.sdu.expenseshare.data.sync.Outbox
import dk.sdu.expenseshare.data.sync.OutboxEntry
import dk.sdu.expenseshare.data.sync.PendingWrite
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.domain.Group
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The user's groups and where they came from. [isFromCache] is true while the app shows groups
 * saved on the device because nothing newer has been fetched in this session.
 */
data class GroupsSnapshot(val groups: List<Group>, val fetchedAtMillis: Long, val isFromCache: Boolean)

/**
 * Groups and balance-related writes for one signed-in user.
 *
 * Reads go to the backend and are cached, so the last fetched groups stay available offline.
 * Writes are queued in [outbox] and sent in the background with retries. [syncNotices] collects
 * writes that only succeeded after failing, so the user can be told about them.
 */
class BalancesRepository(
    private val api: BalancesApi,
    private val cache: BalancesCache,
    private val outbox: Outbox,
    private val scope: CoroutineScope,
    networkAvailable: Flow<Boolean> = emptyFlow(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private val _groups = MutableStateFlow<GroupsSnapshot?>(null)
    val groups: StateFlow<GroupsSnapshot?> = _groups.asStateFlow()

    val writes: StateFlow<List<OutboxEntry>> = outbox.entries

    private val _syncNotices = MutableStateFlow<List<CompletedWrite>>(emptyList())

    /** Writes that reached the backend after failing first. Remove shown ones with [dismissNotice]. */
    val syncNotices: StateFlow<List<CompletedWrite>> = _syncNotices.asStateFlow()

    init {
        scope.launch { loadCache() }
        scope.launch {
            outbox.completed.collect { completed ->
                if (completed.afterFailure) _syncNotices.update { it + completed }
                // A payment changes the balances, so fetch them again.
                if (completed.write is PendingWrite.RecordPayment) refreshQuietly()
            }
        }
        scope.launch {
            // Only react to the network coming back, not to the first value.
            networkAvailable.distinctUntilChanged().drop(1).filter { it }.collect {
                outbox.syncNow()
                refreshQuietly()
            }
        }
    }

    /**
     * Fetches the groups from the backend and caches them.
     *
     * @throws RemoteRequestException if the request fails. Cached groups stay in [groups].
     */
    suspend fun refresh() {
        val rows = api.fetchGroups()
        val groups =
            try {
                rows.map { it.toDomain() }
            } catch (e: IllegalArgumentException) {
                throw RemoteRequestException(retryable = false, message = "Invalid group data", cause = e)
            }
        val fetched = CachedGroups(rows, fetchedAtMillis = clock())
        _groups.value = GroupsSnapshot(groups, fetched.fetchedAtMillis, isFromCache = false)
        cache.save(fetched)
    }

    /** Queues a payment from the user to [toUserId], unless the same payment is already being sent. */
    suspend fun recordPayment(groupId: String, toUserId: String, amount: Long) {
        if (hasUnfinishedWrite<PendingWrite.RecordPayment>(groupId, toUserId)) return
        outbox.enqueue(PendingWrite.RecordPayment(newId(), groupId, toUserId, amount))
    }

    /**
     * Queues a reminder to [toUserId] about [amount] øre, unless one is being sent or was sent in
     * this session.
     */
    suspend fun sendReminder(groupId: String, toUserId: String, amount: Long? = null) {
        val alreadySent =
            writes.value.any {
                it.write is PendingWrite.SendReminder &&
                    it.write.groupId == groupId &&
                    it.write.toUserId == toUserId &&
                    it.status == RequestStatus.Successful
            }
        if (alreadySent || hasUnfinishedWrite<PendingWrite.SendReminder>(groupId, toUserId)) return
        outbox.enqueue(PendingWrite.SendReminder(newId(), groupId, toUserId, amount?.takeIf { it > 0 }))
    }

    suspend fun retryWrite(writeId: String) = outbox.retry(writeId)

    fun dismissNotice(notice: CompletedWrite) {
        _syncNotices.update { notices -> notices - notice }
    }

    private inline fun <reified T : PendingWrite> hasUnfinishedWrite(groupId: String, toUserId: String): Boolean =
        writes.value.any {
            it.write is T &&
                it.write.groupId == groupId &&
                it.write.toUserId == toUserId &&
                it.status != RequestStatus.Successful
        }

    private suspend fun loadCache() {
        val cached = cache.load() ?: return
        val groups =
            try {
                cached.groups.map { it.toDomain() }
            } catch (_: IllegalArgumentException) {
                return
            }
        // Never replace groups that were fetched while the cache was being read.
        _groups.compareAndSet(null, GroupsSnapshot(groups, cached.fetchedAtMillis, isFromCache = true))
    }

    private suspend fun refreshQuietly() {
        try {
            refresh()
        } catch (_: RemoteRequestException) {
            // Background refresh; the screen shows its own state for refreshes it starts.
        }
    }
}
