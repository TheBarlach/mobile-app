package dk.sdu.expenseshare.domain.balances

import dk.sdu.expenseshare.domain.Group

/** [fromMemberId] should pay [amount] øre to [toMemberId]. */
data class Transfer(val fromMemberId: String, val toMemberId: String, val amount: Long)

enum class DebtDirection {
    /** The other member owes the current user. */
    OwesYou,

    /** The current user owes the other member. */
    YouOwe
}

/** One line on the balances screen, seen from the current user's side. [memberName] is null when unknown. */
data class UserDebt(val memberId: String, val memberName: String?, val amount: Long, val direction: DebtDirection)

data class GroupBalance(val groupId: String, val groupName: String, val debts: List<UserDebt>)

/** [totalBalance] is positive when the user is owed money overall and negative when they owe. */
data class BalanceSummary(val totalBalance: Long, val groups: List<GroupBalance>)

/**
 * Net balance of every member in [group], in øre: what they paid minus what they owe.
 * Positive means the member should get money back. The balances always add up to zero.
 */
fun netBalances(group: Group): Map<String, Long> {
    val balances = group.members.associate { it.id to 0L }.toMutableMap()
    group.expenses.forEach { expense ->
        balances.addTo(expense.paidByMemberId, expense.amount)
        expense.shares.forEach { (memberId, share) -> balances.addTo(memberId, -share) }
    }
    group.payments.forEach { payment ->
        balances.addTo(payment.fromMemberId, payment.amount)
        balances.addTo(payment.toMemberId, -payment.amount)
    }
    return balances
}

/**
 * Turns net balances into a short list of transfers that settles everyone.
 *
 * Greedy: the largest debtor pays the largest creditor until one of them is settled. This gives
 * at most `n - 1` transfers. Ties are broken by member id, so the result is deterministic.
 *
 * @throws IllegalArgumentException if the balances do not add up to zero.
 */
fun simplifyDebts(netBalances: Map<String, Long>): List<Transfer> {
    require(netBalances.values.sum() == 0L) { "Balances must add up to zero, was ${netBalances.values.sum()}" }

    val byAmountThenId = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
    val creditors = netBalances.filterValues { it > 0 }.toList().sortedWith(byAmountThenId)
    val debtors =
        netBalances.filterValues { it < 0 }.map { it.key to -it.value }.sortedWith(byAmountThenId)

    val transfers = mutableListOf<Transfer>()
    var creditorIndex = 0
    var debtorIndex = 0
    var creditLeft = creditors.firstOrNull()?.second ?: 0L
    var debtLeft = debtors.firstOrNull()?.second ?: 0L

    while (creditorIndex < creditors.size && debtorIndex < debtors.size) {
        val amount = minOf(creditLeft, debtLeft)
        transfers += Transfer(debtors[debtorIndex].first, creditors[creditorIndex].first, amount)
        creditLeft -= amount
        debtLeft -= amount
        if (creditLeft == 0L && ++creditorIndex < creditors.size) creditLeft = creditors[creditorIndex].second
        if (debtLeft == 0L && ++debtorIndex < debtors.size) debtLeft = debtors[debtorIndex].second
    }
    return transfers
}

/**
 * The balances screen's view of [groups] for [userId]: who owes the user and whom the user owes,
 * per group, plus the user's overall balance. Groups the user is not a member of are skipped.
 */
fun userBalanceSummary(groups: List<Group>, userId: String): BalanceSummary {
    val groupBalances =
        groups
            .filter { group -> group.members.any { it.id == userId } }
            .map { group -> groupBalanceFor(group, userId) }
    val total =
        groupBalances.sumOf { group ->
            group.debts.sumOf { if (it.direction == DebtDirection.OwesYou) it.amount else -it.amount }
        }
    return BalanceSummary(totalBalance = total, groups = groupBalances)
}

private fun groupBalanceFor(group: Group, userId: String): GroupBalance {
    val names = group.members.associate { it.id to it.name }
    val debts =
        simplifyDebts(netBalances(group)).mapNotNull { transfer ->
            when (userId) {
                transfer.toMemberId -> transfer.fromMemberId to DebtDirection.OwesYou
                transfer.fromMemberId -> transfer.toMemberId to DebtDirection.YouOwe
                else -> null
            }?.let { (otherId, direction) ->
                UserDebt(otherId, names[otherId], transfer.amount, direction)
            }
        }
    return GroupBalance(groupId = group.id, groupName = group.name, debts = debts)
}

private fun MutableMap<String, Long>.addTo(memberId: String, amount: Long) {
    this[memberId] = Math.addExact(getValue(memberId), amount)
}
