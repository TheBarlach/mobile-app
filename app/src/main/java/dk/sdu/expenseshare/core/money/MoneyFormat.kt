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
