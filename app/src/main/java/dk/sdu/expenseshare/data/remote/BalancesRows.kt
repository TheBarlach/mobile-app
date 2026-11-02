package dk.sdu.expenseshare.data.remote

import dk.sdu.expenseshare.domain.Expense
import dk.sdu.expenseshare.domain.Group
import dk.sdu.expenseshare.domain.Member
import dk.sdu.expenseshare.domain.Payment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Columns requested for each group. The nested parts are PostgREST embeds that follow the
 * foreign keys in supabase/migrations/20261103120000_groups_expenses.sql.
 */
const val GROUP_COLUMNS =
    "id, name, " +
        "group_members(user_id, profiles(full_name, username)), " +
        "expenses(id, description, paid_by, amount, expense_shares(user_id, amount)), " +
        "payments(id, from_user, to_user, amount)"

/** A `groups` row with its members, expenses and payments, as returned by [GROUP_COLUMNS]. */
@Serializable
data class GroupRow(
    val id: String,
    val name: String,
    @SerialName("group_members") val members: List<GroupMemberRow> = emptyList(),
    val expenses: List<ExpenseRow> = emptyList(),
    val payments: List<PaymentRow> = emptyList()
)

@Serializable
data class GroupMemberRow(
    @SerialName("user_id") val userId: String,
    /** Null if the profile cannot be read. */
    @SerialName("profiles") val profile: MemberProfileRow? = null
)

@Serializable
data class MemberProfileRow(@SerialName("full_name") val fullName: String? = null, val username: String? = null)

@Serializable
data class ExpenseRow(
    val id: String,
    val description: String,
    @SerialName("paid_by") val paidBy: String,
    val amount: Long,
    @SerialName("expense_shares") val shares: List<ExpenseShareRow> = emptyList()
)

@Serializable
data class ExpenseShareRow(@SerialName("user_id") val userId: String, val amount: Long)

@Serializable
data class PaymentRow(
    val id: String,
    @SerialName("from_user") val fromUser: String,
    @SerialName("to_user") val toUser: String,
    val amount: Long
)

/**
 * Converts the row into the domain model.
 *
 * @throws IllegalArgumentException if the data breaks a domain rule, e.g. shares that do not add up.
 */
fun GroupRow.toDomain(): Group = Group(
    id = id,
    name = name,
    members =
    members.map { member ->
        val name = member.profile?.fullName?.takeIf { it.isNotBlank() } ?: member.profile?.username
        Member(id = member.userId, name = name)
    },
    expenses =
    expenses.map { expense ->
        Expense(
            id = expense.id,
            description = expense.description,
            paidByMemberId = expense.paidBy,
            amount = expense.amount,
            shares = expense.shares.associate { it.userId to it.amount }
        )
    },
    payments =
    payments.map { payment ->
        Payment(id = payment.id, fromMemberId = payment.fromUser, toMemberId = payment.toUser, amount = payment.amount)
    }
)
