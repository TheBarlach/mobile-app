package dk.sdu.expenseshare.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dk.sdu.expenseshare.data.notifications.NotificationsNotice
import dk.sdu.expenseshare.data.notifications.NotificationsRepository
import dk.sdu.expenseshare.data.notifications.ReadSyncState
import dk.sdu.expenseshare.domain.AppNotification
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUiState(
    /** Null until notifications have been loaded from the server or the device. */
    val notifications: List<AppNotification>? = null,
    /** When the shown notifications were fetched from the server. */
    val lastUpdatedMillis: Long? = null,
    /** True while the notifications come from the device because nothing newer was fetched yet. */
    val isShowingSavedData: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    /** Null when no read marks are waiting to be sent. */
    val readSync: ReadSyncState? = null,
    /** A background success to show once, e.g. as a snackbar. */
    val notice: NotificationsNotice? = null
) {
    val unreadCount: Int get() = notifications?.count { !it.isRead } ?: 0

    /** Nothing to show yet, not even saved notifications. */
    val isLoading: Boolean get() = notifications == null && isRefreshing
}

private data class RefreshState(val isRefreshing: Boolean = false, val failed: Boolean = false)

/**
 * Shared by the Alerts tab and its badge in the bottom bar, so it lives as long as the signed-in
 * part of the app and loads the notifications once at start.
 */
class NotificationsViewModel(private val repository: NotificationsRepository) : ViewModel() {
    private val refreshState = MutableStateFlow(RefreshState())

    val state: StateFlow<NotificationsUiState> =
        combine(repository.notifications, repository.readSync, repository.notices, refreshState) {
                snapshot,
                readSync,
                notices,
                refresh
            ->
            NotificationsUiState(
                notifications = snapshot?.notifications,
                lastUpdatedMillis = snapshot?.fetchedAtMillis,
                isShowingSavedData = snapshot?.isFromCache == true,
                isRefreshing = refresh.isRefreshing,
                refreshFailed = refresh.failed,
                readSync = readSync,
                notice = notices.firstOrNull()
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationsUiState(isRefreshing = true))

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
                    // Offline or server trouble must not crash the app; saved notifications stay visible.
                    true
                }
            refreshState.value = RefreshState(isRefreshing = false, failed = failed)
        }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch { repository.markRead(listOf(notificationId)) }
    }

    fun markAllRead() {
        val unread = state.value.notifications.orEmpty().filterNot { it.isRead }.map { it.id }
        if (unread.isEmpty()) return
        viewModelScope.launch { repository.markRead(unread) }
    }

    fun retryReadSync() = repository.retryReadSync()

    fun onNoticeShown(notice: NotificationsNotice) = repository.dismissNotice(notice)
}
