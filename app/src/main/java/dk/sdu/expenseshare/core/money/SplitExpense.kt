package dk.sdu.expenseshare.core.money

/**
 * Splits [amount] (in the smallest currency unit, e.g. øre) between [participants].
 *
 * The total is always preserved. When the amount does not divide evenly, the
 * remainder is distributed one unit at a time to the first participants, so the
 * result is deterministic: 10000 øre between 3 people gives [3334, 3333, 3333].
 *
 * @throws IllegalArgumentException if [amount] is negative or [participants] is not positive.
 */
fun splitExpense(amount: Long, participants: Int): List<Long> {
    require(amount >= 0) { "Amount must not be negative, was $amount" }
    require(participants > 0) { "Participants must be positive, was $participants" }

    val base = amount / participants
    val remainder = amount % participants

    return List(participants) { index -> base + if (index < remainder) 1 else 0 }
}
