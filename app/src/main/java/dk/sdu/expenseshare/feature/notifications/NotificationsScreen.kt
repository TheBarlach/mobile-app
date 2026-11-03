package dk.sdu.expenseshare.feature.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.core.money.formatDkkShort
import dk.sdu.expenseshare.data.notifications.NotificationsNotice
import dk.sdu.expenseshare.data.notifications.ReadSyncState
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.domain.AppNotification
import dk.sdu.expenseshare.domain.NotificationType
import dk.sdu.expenseshare.ui.components.PrimaryButton
import dk.sdu.expenseshare.ui.components.ScreenHeader
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

/** Callbacks from the notifications screen to its ViewModel. */
data class NotificationsActions(
    val onRefresh: () -> Unit = {},
    val onOpen: (notificationId: String) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onRetryReadSync: () -> Unit = {},
    val onNoticeShown: (NotificationsNotice) -> Unit = {}
)

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel, userInitials: String?, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            NotificationsActions(
                onRefresh = viewModel::refresh,
                onOpen = viewModel::markRead,
                onMarkAllRead = viewModel::markAllRead,
                onRetryReadSync = viewModel::retryReadSync,
                onNoticeShown = viewModel::onNoticeShown
            )
        }
    // Fetch the newest notifications whenever the tab is opened.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    NotificationsContent(state = state, userInitials = userInitials, actions = actions, modifier = modifier)
}

@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    userInitials: String?,
    actions: NotificationsActions,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val notice = state.notice
    val noticeText =
        when (notice) {
            NotificationsNotice.UpdatedAfterReconnect -> stringResource(R.string.notifications_updated_after_reconnect)
            NotificationsNotice.ReadStatusSynced -> stringResource(R.string.notifications_read_synced)
            null -> null
        }
    LaunchedEffect(notice) {
        if (notice != null && noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            actions.onNoticeShown(notice)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { ScreenHeader(stringResource(R.string.notifications_title), userInitials) }
            item { StatusArea(state, actions, nowMillis) }

            val notifications = state.notifications
            when {
                notifications != null && notifications.isEmpty() -> item { Empty() }
                notifications != null ->
                    items(notifications, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            nowMillis = nowMillis,
                            onClick = { actions.onOpen(notification.id) }
                        )
                    }
                state.refreshFailed -> item { LoadFailed(actions.onRefresh) }
                state.isLoading -> item { Loading() }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** The space between the title and the list: offline and sync messages, and "Mark all as read". */
@Composable
private fun StatusArea(state: NotificationsUiState, actions: NotificationsActions, nowMillis: Long) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        if (state.notifications != null && state.isShowingSavedData && state.refreshFailed) {
            SavedDataBanner(state.lastUpdatedMillis, nowMillis, actions.onRefresh)
        }
        state.readSync?.let { ReadSyncStatus(it, actions.onRetryReadSync) }
        if (state.unreadCount > 0) {
            TextButton(onClick = actions.onMarkAllRead, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.notifications_mark_all_read))
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unreadDescription = stringResource(R.string.notifications_unread)
    Surface(
        modifier = modifier.fillMaxWidth().clickable(enabled = !notification.isRead, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    notificationTitle(notification.type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    notificationBody(notification),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    notificationMeta(notification, nowMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!notification.isRead) {
                Box(
                    Modifier
                        .padding(top = 6.dp, start = 8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .semantics { contentDescription = unreadDescription }
                )
            }
        }
    }
}

@Composable
private fun notificationTitle(type: NotificationType): String = stringResource(
    when (type) {
        NotificationType.ExpenseAdded -> R.string.notifications_type_expense_added
        NotificationType.PaymentReminder -> R.string.notifications_type_payment_reminder
        NotificationType.PaymentReceived -> R.string.notifications_type_payment_received
        NotificationType.MemberJoined, NotificationType.AddedToGroup -> R.string.notifications_type_group_updated
    }
)

@Composable
private fun notificationBody(notification: AppNotification): String {
    val actor = notification.actorName ?: stringResource(R.string.notifications_someone)
    val group = notification.groupName ?: stringResource(R.string.notifications_a_group)
    val amount = notification.amount?.let(::formatDkkShort)
    return when (notification.type) {
        NotificationType.ExpenseAdded -> {
            val description = notification.description ?: stringResource(R.string.notifications_an_expense)
            if (amount != null) {
                stringResource(R.string.notifications_expense_added, actor, description, amount)
            } else {
                stringResource(R.string.notifications_expense_added_no_amount, actor, description)
            }
        }
        NotificationType.PaymentReminder ->
            if (amount != null) {
                stringResource(R.string.notifications_payment_reminder, actor, amount)
            } else {
                stringResource(R.string.notifications_payment_reminder_no_amount, actor)
            }
        NotificationType.PaymentReceived ->
            stringResource(R.string.notifications_payment_received, actor, amount ?: formatDkkShort(0))
        NotificationType.MemberJoined -> stringResource(R.string.notifications_member_joined, actor, group)
        NotificationType.AddedToGroup -> stringResource(R.string.notifications_added_to_group, actor, group)
    }
}

/** "Summer Trip · 10 minutes ago", or just the time when the group is unknown. */
private fun notificationMeta(notification: AppNotification, nowMillis: Long): String {
    val time =
        DateUtils.getRelativeTimeSpanString(notification.createdAtMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS)
    return listOfNotNull(notification.groupName, time).joinToString(" · ")
}

@Composable
private fun SavedDataBanner(lastUpdatedMillis: Long?, nowMillis: Long, onRefresh: () -> Unit) {
    val updated =
        lastUpdatedMillis?.let {
            DateUtils.getRelativeTimeSpanString(it, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString()
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (updated != null) {
                    stringResource(R.string.notifications_showing_saved_since, updated)
                } else {
                    stringResource(R.string.notifications_showing_saved)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.action_try_again)) }
        }
    }
}

@Composable
private fun ReadSyncStatus(readSync: ReadSyncState, onRetry: () -> Unit) {
    when {
        readSync.status == RequestStatus.Failed ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.notifications_read_sync_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.balances_retry)) }
            }
        readSync.isWaitingToRetry ->
            Text(
                stringResource(R.string.notifications_read_sync_waiting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

@Composable
private fun Empty(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.notifications_empty),
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp)
    )
}

@Composable
private fun LoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.notifications_load_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        PrimaryButton(text = stringResource(R.string.action_try_again), onClick = onRetry)
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.notifications_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

private const val PREVIEW_NOW = 1_762_160_000_000L
private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE

private val previewNotifications =
    listOf(
        AppNotification(
            id = "1",
            type = NotificationType.ExpenseAdded,
            groupName = "Summer Trip",
            actorName = "Alex",
            description = "Dinner",
            amount = 60_000,
            createdAtMillis = PREVIEW_NOW - 10 * MINUTE,
            isRead = false
        ),
        AppNotification(
            id = "2",
            type = NotificationType.PaymentReminder,
            groupName = "Roommates",
            actorName = "John",
            description = null,
            amount = 10_000,
            createdAtMillis = PREVIEW_NOW - DAY,
            isRead = true
        ),
        AppNotification(
            id = "3",
            type = NotificationType.MemberJoined,
            groupName = "Summer Trip",
            actorName = "Sarah",
            description = null,
            amount = null,
            createdAtMillis = PREVIEW_NOW - 2 * DAY,
            isRead = true
        )
    )

@Preview(showBackground = true)
@Composable
private fun NotificationsScreenPreview() {
    ExpenseShareTheme(dynamicColor = false) {
        NotificationsContent(
            state = NotificationsUiState(notifications = previewNotifications, lastUpdatedMillis = PREVIEW_NOW),
            userInitials = "AH",
            actions = NotificationsActions(),
            nowMillis = PREVIEW_NOW
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsScreenOfflinePreview() {
    ExpenseShareTheme(dynamicColor = false) {
        NotificationsContent(
            state =
            NotificationsUiState(
                notifications = previewNotifications,
                lastUpdatedMillis = PREVIEW_NOW - 60 * MINUTE,
                isShowingSavedData = true,
                refreshFailed = true,
                readSync = ReadSyncState(RequestStatus.Pending, isWaitingToRetry = true)
            ),
            userInitials = "AH",
            actions = NotificationsActions(),
            nowMillis = PREVIEW_NOW
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationsScreenEmptyPreview() {
    ExpenseShareTheme(dynamicColor = false) {
        NotificationsContent(
            state = NotificationsUiState(notifications = emptyList()),
            userInitials = null,
            actions = NotificationsActions(),
            nowMillis = PREVIEW_NOW
        )
    }
}
