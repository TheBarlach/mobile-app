package dk.sdu.expenseshare.feature.balances

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.sdu.expenseshare.R
import dk.sdu.expenseshare.core.money.formatDkkShort
import dk.sdu.expenseshare.core.money.formatSignedDkk
import dk.sdu.expenseshare.data.sync.CompletedWrite
import dk.sdu.expenseshare.data.sync.PendingWrite
import dk.sdu.expenseshare.data.sync.RequestStatus
import dk.sdu.expenseshare.domain.balances.BalanceSummary
import dk.sdu.expenseshare.domain.balances.DebtDirection
import dk.sdu.expenseshare.domain.balances.GroupBalance
import dk.sdu.expenseshare.domain.balances.UserDebt
import dk.sdu.expenseshare.ui.components.PrimaryButton
import dk.sdu.expenseshare.ui.theme.ExpenseShareTheme

/** Callbacks from the balances screen to its ViewModel. */
data class BalancesActions(
    val onRefresh: () -> Unit = {},
    val onRemind: (groupId: String, memberId: String) -> Unit = { _, _ -> },
    val onRecordPayment: (groupId: String, memberId: String, amount: Long) -> Unit = { _, _, _ -> },
    val onRetryWrite: (writeId: String) -> Unit = {},
    val onMessageShown: (SyncMessage) -> Unit = {}
)

@Composable
fun BalancesScreen(viewModel: BalancesViewModel, userInitials: String?, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            BalancesActions(
                onRefresh = viewModel::refresh,
                onRemind = viewModel::remind,
                onRecordPayment = viewModel::recordPayment,
                onRetryWrite = viewModel::retryWrite,
                onMessageShown = viewModel::onMessageShown
            )
        }
    BalancesContent(state = state, userInitials = userInitials, actions = actions, modifier = modifier)
}

@Composable
fun BalancesContent(
    state: BalancesUiState,
    userInitials: String?,
    actions: BalancesActions,
    modifier: Modifier = Modifier
) {
    var settleUp by remember { mutableStateOf<Pair<String, UserDebt>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val message = state.message
    val messageText = message?.let { syncMessageText(it) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onMessageShown(message)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { BalancesHeader(userInitials) }

            val summary = state.summary
            if (summary != null) {
                item { TotalBalance(summary.totalBalance, Modifier.padding(vertical = 24.dp)) }
                if (state.isShowingSavedData && state.refreshFailed) {
                    item { SavedDataBanner(state.lastUpdatedMillis, actions.onRefresh) }
                }
                groupSections(summary, state, actions, onSettleUp = { groupId, debt -> settleUp = groupId to debt })
            } else if (state.refreshFailed) {
                item { LoadFailed(actions.onRefresh) }
            } else if (state.isLoading) {
                item { Loading() }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    settleUp?.let { (groupId, debt) ->
        SettleUpDialog(
            debt = debt,
            onConfirm = {
                actions.onRecordPayment(groupId, debt.memberId, debt.amount)
                settleUp = null
            },
            onDismiss = { settleUp = null }
        )
    }
}

private fun LazyListScope.groupSections(
    summary: BalanceSummary,
    state: BalancesUiState,
    actions: BalancesActions,
    onSettleUp: (groupId: String, debt: UserDebt) -> Unit
) {
    if (summary.groups.isEmpty()) {
        item { Text(stringResource(R.string.balances_empty), style = MaterialTheme.typography.bodyMedium) }
    }
    summary.groups.forEach { group ->
        item(key = "group-${group.groupId}") {
            Text(
                group.groupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (group.debts.isEmpty()) {
            item(key = "settled-${group.groupId}") {
                Text(stringResource(R.string.balances_settled_up), style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(group.debts, key = { "debt-${group.groupId}-${it.memberId}" }) { debt ->
            val key = DebtKey(group.groupId, debt.memberId)
            DebtRow(
                debt = debt,
                reminder = state.reminders[key],
                payment = state.payments[key],
                onRemind = { actions.onRemind(group.groupId, debt.memberId) },
                onSettleUp = { onSettleUp(group.groupId, debt) },
                onRetryWrite = actions.onRetryWrite
            )
        }
    }
}

@Composable
private fun syncMessageText(message: SyncMessage): String {
    val name = message.memberName ?: stringResource(R.string.balances_unknown_member)
    return if (message.isPayment) {
        stringResource(R.string.balances_payment_synced, name)
    } else {
        stringResource(R.string.balances_reminder_synced, name)
    }
}

@Composable
private fun BalancesHeader(userInitials: String?, modifier: Modifier = Modifier) {
    val avatarDescription = stringResource(R.string.balances_profile_picture)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.balances_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = avatarDescription },
            contentAlignment = Alignment.Center
        ) {
            if (userInitials != null) {
                Text(
                    userInitials,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TotalBalance(totalBalance: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.balances_your_balance), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            formatSignedDkk(totalBalance),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SavedDataBanner(lastUpdatedMillis: Long?, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val updated =
        lastUpdatedMillis?.let {
            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (updated != null) {
                    stringResource(R.string.balances_showing_saved_since, updated)
                } else {
                    stringResource(R.string.balances_showing_saved)
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
private fun DebtRow(
    debt: UserDebt,
    reminder: WriteState?,
    payment: WriteState?,
    onRemind: () -> Unit,
    onSettleUp: () -> Unit,
    onRetryWrite: (writeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val name = debt.memberName ?: stringResource(R.string.balances_unknown_member)
    val canSettleUp = debt.direction == DebtDirection.YouOwe && payment == null
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .then(if (canSettleUp) Modifier.clickable(onClick = onSettleUp) else Modifier),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when (debt.direction) {
                        DebtDirection.OwesYou -> stringResource(R.string.balances_owes_you, name)
                        DebtDirection.YouOwe -> stringResource(R.string.balances_you_owe, name)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(formatDkkShort(debt.amount), style = MaterialTheme.typography.bodySmall)
                WriteStatusText(
                    debt.direction,
                    if (debt.direction ==
                        DebtDirection.OwesYou
                    ) {
                        reminder
                    } else {
                        payment
                    },
                    name
                )
            }
            when {
                debt.direction == DebtDirection.OwesYou -> RemindButton(reminder, onRemind, onRetryWrite)
                payment?.status == RequestStatus.Failed ->
                    TextButton(onClick = { onRetryWrite(payment.writeId) }) {
                        Text(stringResource(R.string.balances_retry))
                    }
                payment != null -> CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                else ->
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = stringResource(R.string.balances_settle_up),
                        modifier = Modifier.padding(12.dp)
                    )
            }
        }
    }
}

@Composable
private fun WriteStatusText(direction: DebtDirection, write: WriteState?, name: String) {
    val text =
        when {
            write == null -> null
            write.status == RequestStatus.Failed && direction == DebtDirection.OwesYou ->
                stringResource(R.string.balances_remind_failed_message, name)
            write.status == RequestStatus.Failed -> stringResource(R.string.balances_payment_failed)
            write.isWaitingToRetry -> stringResource(R.string.balances_waiting_for_connection)
            write.status == RequestStatus.Pending && direction == DebtDirection.YouOwe ->
                stringResource(R.string.balances_payment_pending)
            else -> null
        }
    if (text != null) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color =
            if (write?.status == RequestStatus.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun RemindButton(
    reminder: WriteState?,
    onRemind: () -> Unit,
    onRetryWrite: (writeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val status = reminder?.status
    TextButton(
        onClick = {
            if (reminder != null &&
                status == RequestStatus.Failed
            ) {
                onRetryWrite(reminder.writeId)
            } else {
                onRemind()
            }
        },
        enabled = status == null || status == RequestStatus.Failed,
        modifier = modifier
    ) {
        Text(
            stringResource(
                when (status) {
                    null -> R.string.balances_remind
                    RequestStatus.Pending -> R.string.balances_remind_pending
                    RequestStatus.Successful -> R.string.balances_remind_sent
                    RequestStatus.Failed -> R.string.balances_retry
                }
            ),
            color = if (status ==
                null
            ) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun SettleUpDialog(debt: UserDebt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = debt.memberName ?: stringResource(R.string.balances_unknown_member)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.balances_settle_up)) },
        text = { Text(stringResource(R.string.balances_settle_up_question, name, formatDkkShort(debt.amount))) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.balances_settle_up_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun LoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.balances_load_failed),
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
        Text(stringResource(R.string.balances_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

private val previewSummary =
    BalanceSummary(
        totalBalance = 15_000,
        groups =
        listOf(
            GroupBalance(
                groupId = "trip",
                groupName = "Summer Trip",
                debts =
                listOf(
                    UserDebt("alex", "Alex", 10_000, DebtDirection.OwesYou),
                    UserDebt("sarah", "Sarah", 15_000, DebtDirection.OwesYou)
                )
            ),
            GroupBalance(
                groupId = "roommates",
                groupName = "Roommates",
                debts = listOf(UserDebt("john", "John", 10_000, DebtDirection.YouOwe))
            )
        )
    )

@Preview(showBackground = true)
@Composable
private fun BalancesScreenPreview() {
    ExpenseShareTheme(dynamicColor = false) {
        BalancesContent(
            state = BalancesUiState(summary = previewSummary, lastUpdatedMillis = 0),
            userInitials = "AH",
            actions = BalancesActions()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BalancesScreenOfflinePreview() {
    ExpenseShareTheme(dynamicColor = false) {
        BalancesContent(
            state =
            BalancesUiState(
                summary = previewSummary,
                lastUpdatedMillis = System.currentTimeMillis() - 3_600_000,
                isShowingSavedData = true,
                refreshFailed = true,
                reminders =
                mapOf(
                    DebtKey("trip", "alex") to WriteState("r1", RequestStatus.Successful, isWaitingToRetry = false),
                    DebtKey("trip", "sarah") to WriteState("r2", RequestStatus.Pending, isWaitingToRetry = true)
                ),
                payments = mapOf(DebtKey("roommates", "john") to WriteState("p1", RequestStatus.Failed, false)),
                message =
                SyncMessage(
                    CompletedWrite(PendingWrite.SendReminder("r3", "trip", "alex"), afterFailure = true),
                    memberName = "Alex"
                )
            ),
            userInitials = "AH",
            actions = BalancesActions()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BalancesScreenErrorPreview() {
    ExpenseShareTheme(dynamicColor = false) {
        BalancesContent(
            state = BalancesUiState(refreshFailed = true),
            userInitials = null,
            actions = BalancesActions()
        )
    }
}
