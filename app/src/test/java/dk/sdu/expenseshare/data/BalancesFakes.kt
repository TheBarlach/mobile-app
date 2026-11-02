package dk.sdu.expenseshare.data

import dk.sdu.expenseshare.data.local.TextStore
import dk.sdu.expenseshare.data.remote.BalancesApi
import dk.sdu.expenseshare.data.remote.ExpenseRow
import dk.sdu.expenseshare.data.remote.ExpenseShareRow
import dk.sdu.expenseshare.data.remote.GroupMemberRow
import dk.sdu.expenseshare.data.remote.GroupRow
import dk.sdu.expenseshare.data.remote.MemberProfileRow
import dk.sdu.expenseshare.data.remote.RemoteRequestException
import dk.sdu.expenseshare.data.sync.PendingWrite
import java.io.IOException

/** [TextStore] in memory. Set [failWrites] to simulate a full disk. */
class InMemoryTextStore(var text: String? = null) : TextStore {
    var failWrites = false

    override suspend fun read(): String? = text

    override suspend fun write(text: String) {
        if (failWrites) throw IOException("disk full")
        this.text = text
    }
}

/** Backend stand-in. Set [online] to false to simulate no connection. */
class FakeBalancesApi(var groups: List<GroupRow> = emptyList()) : BalancesApi {
    var online = true

    /** Overrides the result of [send], e.g. to simulate the server rejecting a write. */
    var sendResult: (suspend (PendingWrite) -> Unit)? = null

    val sent = mutableListOf<PendingWrite>()
    var fetchCount = 0

    override suspend fun fetchGroups(): List<GroupRow> {
        fetchCount++
        if (!online) throw offline()
        return groups
    }

    override suspend fun send(write: PendingWrite) {
        if (!online) throw offline()
        sendResult?.invoke(write)
        sent += write
    }

    private fun offline() = RemoteRequestException(retryable = true, message = "offline", cause = IOException())
}

/** "Summer Trip": the user (`me`) paid 200 DKK for dinner with Alex, so Alex owes 100 DKK. */
val summerTripRow =
    GroupRow(
        id = "trip",
        name = "Summer Trip",
        members =
        listOf(
            GroupMemberRow("me", MemberProfileRow(fullName = "Me")),
            GroupMemberRow("alex", MemberProfileRow(fullName = "Alex"))
        ),
        expenses =
        listOf(
            ExpenseRow(
                id = "dinner",
                description = "Dinner",
                paidBy = "me",
                amount = 20_000,
                shares = listOf(ExpenseShareRow("me", 10_000), ExpenseShareRow("alex", 10_000))
            )
        )
    )
