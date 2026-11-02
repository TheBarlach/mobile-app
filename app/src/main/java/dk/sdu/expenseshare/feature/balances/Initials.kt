package dk.sdu.expenseshare.feature.balances

/** Up to two initials for an avatar, e.g. "Anna Hansen" -> "AH". Returns null for a blank name. */
fun initialsOf(name: String?): String? {
    val words = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    if (words.isEmpty()) return null
    val letters = if (words.size == 1) words.first().take(1) else words.first().take(1) + words.last().take(1)
    return letters.uppercase()
}
