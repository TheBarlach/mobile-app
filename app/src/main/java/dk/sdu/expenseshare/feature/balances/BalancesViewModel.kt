package dk.sdu.expenseshare.feature.balances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.sdu.expenseshare.data.balances.BalancesRepository
import dk.sdu.expenseshare.data.sync.CompletedWrite
import dk.sdu.expenseshare.data.sync.OutboxEntry
import dk.sdu.expenseshare.data.sync.PendingWrite
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.domain.balances.BalanceSummary
import dk.sdu.expenseshare.domain.balances.userBalanceSummary
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Identifies one row on the balances screen: the other member in a group. */
data class DebtKey(val groupId: String, val memberId: String)

/** State of a payment or reminder request shown on a row. */
data class WriteState(val writeId: String, val status: RequestStatus, val isWaitingToRetry: Boolean)

/** A write that succeeded in the background after failing; shown once as a snackbar. */
data class SyncMessage(val notice: CompletedWrite, val memberName: String?) {
    val isPayment: Boolean get() = notice.write is PendingWrite.RecordPayment
}

data class BalancesUiState(
    val summary: BalanceSummary? = null,
    /** When the shown balances were fetched from the server. */
    val lastUpdatedMillis: Long? = null,
    /** True while the balances come from the device because nothing newer was fetched yet. */
    val isShowingSavedData: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val reminders: Map<DebtKey, WriteState> = emptyMap(),
    val payments: Map<DebtKey, WriteState> = emptyMap(),
    val message: SyncMessage? = null
) {
    /** Nothing to show yet, not even saved balances. */
    val isLoading: Boolean get() = summary == null && isRefreshing
}

private data class RefreshState(val isRefreshing: Boolean = false, val failed: Boolean = false)

class BalancesViewModel(private val repository: BalancesRepository, private val currentUserId: String) :
    ViewModel() {
    private val refreshState = MutableStateFlow(RefreshState())

    val state: StateFlow<BalancesUiState> =
        combine(repository.groups, repository.writes, repository.syncNotices, refreshState) {
                snapshot,
                writes,
                notices,
                refresh
            ->
            val notice = notices.firstOrNull()
            BalancesUiState(
                summary = snapshot?.let { userBalanceSummary(it.groups, currentUserId) },
                lastUpdatedMillis = snapshot?.fetchedAtMillis,
                isShowingSavedData = snapshot?.isFromCache == true,
                isRefreshing = refresh.isRefreshing,
                refreshFailed = refresh.failed,
                reminders = writeStates<PendingWrite.SendReminder>(writes),
                // A finished payment is shown through the refreshed balances instead.
                payments = writeStates<PendingWrite.RecordPayment>(
                    writes.filter {
                        it.status != RequestStatus.Successful
                    }
                ),
                message = notice?.let { SyncMessage(it, memberName(it.write)) }
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BalancesUiState(isRefreshing = true))

    init {
        refresh()
    }

    fun refresh() {
        if (refreshState.value.isRefreshing) return
        refreshState.value = RefreshState(isRefreshing = true)
        viewModelScope.launch {
            val failed =
                try {
                    repository.refresh()
                    false
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Offline or server trouble must not crash the app; saved balances stay visible.
                    true
                }
            refreshState.update { RefreshState(isRefreshing = false, failed = failed) }
        }
    }

    fun remind(groupId: String, memberId: String) {
        viewModelScope.launch { repository.sendReminder(groupId, memberId) }
    }

    fun recordPayment(groupId: String, memberId: String, amount: Long) {
        viewModelScope.launch { repository.recordPayment(groupId, memberId, amount) }
    }

    fun retryWrite(writeId: String) {
        viewModelScope.launch { repository.retryWrite(writeId) }
    }

    fun onMessageShown(message: SyncMessage) {
        repository.dismissNotice(message.notice)
    }

    private fun memberName(write: PendingWrite): String? = repository.groups.value
        ?.groups
        ?.firstOrNull { it.id == write.groupId }
        ?.members
        ?.firstOrNull { it.id == write.toUserId }
        ?.name

    private inline fun <reified T : PendingWrite> writeStates(writes: List<OutboxEntry>): Map<DebtKey, WriteState> =
        writes
            .filter { it.write is T }
            // The newest write for a row wins.
            .associate {
                DebtKey(it.write.groupId, it.write.toUserId) to
                    WriteState(it.write.id, it.status, it.isWaitingToRetry)
            }
}
