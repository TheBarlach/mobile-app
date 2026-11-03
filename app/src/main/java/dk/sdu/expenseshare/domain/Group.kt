package dk.sdu.expenseshare.domain

/** A person taking part in a group. [id] is the user's profile id; [name] is null if they have not set one. */
data class Member(val id: String, val name: String?)

/**
 * An expense paid by [paidByMemberId]. [shares] maps each participant's member id to the part of
 * [amount] (in øre) they are responsible for, so uneven splits are stored explicitly.
 */
data class Expense(
    val id: String,
    val description: String,
    val paidByMemberId: String,
    val amount: Long,
    val shares: Map<String, Long>
) {
    init {
        require(amount >= 0) { "Expense amount must not be negative, was $amount" }
        require(shares.isNotEmpty()) { "Expense $id must have at least one participant" }
        require(shares.values.all { it >= 0 }) { "Expense shares must not be negative" }
        require(shares.values.sum() == amount) {
            "Shares of expense $id add up to ${shares.values.sum()}, expected $amount"
        }
    }
}

/** Money paid directly from one member to another to settle a debt. */
data class Payment(val id: String, val fromMemberId: String, val toMemberId: String, val amount: Long) {
    init {
        require(amount > 0) { "Payment amount must be positive, was $amount" }
        require(fromMemberId != toMemberId) { "A member cannot pay themselves" }
    }
}

data class Group(
    val id: String,
    val name: String,
    val members: List<Member>,
    val expenses: List<Expense> = emptyList(),
    val payments: List<Payment> = emptyList()
) {
    init {
        val memberIds = members.map { it.id }.toSet()
        require(memberIds.size == members.size) { "Group $id has duplicate members" }
        expenses.forEach { expense ->
            require(expense.paidByMemberId in memberIds) { "Payer of expense ${expense.id} is not in group $id" }
            require(memberIds.containsAll(expense.shares.keys)) {
                "A participant of expense ${expense.id} is not in group $id"
            }
        }
        payments.forEach { payment ->
            require(payment.fromMemberId in memberIds && payment.toMemberId in memberIds) {
                "Payment ${payment.id} involves someone who is not in group $id"
            }
        }
    }
}
