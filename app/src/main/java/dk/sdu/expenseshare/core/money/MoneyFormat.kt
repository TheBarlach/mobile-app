package dk.sdu.expenseshare.core.money

import kotlin.math.absoluteValue

private const val MINOR_UNITS_PER_MAJOR = 100

/**
 * Formats an amount in øre as a DKK string, e.g. 3334 -> "33.34 DKK".
 * Uses integer arithmetic only, so no floating-point rounding can occur.
 */
fun formatDkk(amountInOre: Long): String {
    val sign = if (amountInOre < 0) "-" else ""
    val absolute = amountInOre.absoluteValue
    val kroner = absolute / MINOR_UNITS_PER_MAJOR
    val ore = (absolute % MINOR_UNITS_PER_MAJOR).toString().padStart(2, '0')
    return "$sign$kroner.$ore DKK"
}

/**
 * Like [formatDkk], but leaves out the øre when the amount is whole kroner,
 * e.g. 15000 -> "150 DKK" and 15050 -> "150.50 DKK".
 */
fun formatDkkShort(amountInOre: Long): String {
    if (amountInOre % MINOR_UNITS_PER_MAJOR != 0L) return formatDkk(amountInOre)
    return "${amountInOre / MINOR_UNITS_PER_MAJOR} DKK"
}

/** [formatDkkShort] with an explicit sign for non-zero amounts, e.g. "+ 150 DKK" or "- 100 DKK". */
fun formatSignedDkk(amountInOre: Long): String = when {
    amountInOre > 0 -> "+ ${formatDkkShort(amountInOre)}"
    amountInOre < 0 -> "- ${formatDkkShort(amountInOre.absoluteValue)}"
    else -> formatDkkShort(0)
}
