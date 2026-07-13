package com.steve1316.uma_android_automation

/**
 * Ordered borrow priorities for the queue's friend-slot fill: the highest entry found anywhere
 * in the Borrow Card list wins. Entries are the full "[Outfit] Character" names exactly as the
 * picker rows render them; matching runs through [borrowRowMatchesPreference], so case,
 * brackets, line breaks, and decorations are ignored. An entry that never appears in a player's
 * pool simply never matches, which makes extending this list free.
 *
 * Ordering is the community meta for a general-purpose borrow: the speed staple first, then the
 * stamina/wit/friend staples, then solid alternatives, with the Group cards last (they mainly
 * matter to dating-schedule users, who can pin one via runQueue.preferredBorrowName - a
 * non-empty value is treated as priority zero ahead of this list).
 */
internal object SmartBorrowList {
    val priority = listOf(
        "[Fire at My Heels] Kitasan Black",
        "[Sounds of Earth] Super Creek",
        "[Wave of Gratitude] Fine Motion",
        "[Tracen Reception] Tazuna Hayakawa",
        "[Beyond This Shining Moment] Silence Suzuka",
        "[Touching Sleeves Is Good Luck! ♪] Matikanefukukitaru",
        "[The Ghost Finds Halloween Magic] Mihono Bourbon",
        "[Passing the Dream On] Team Sirius",
        "[Esteemed and Adored] Heirs to the Throne",
    )
}

/**
 * The highest-priority entry present among [rowTexts]: returns (entry index, row index) with the
 * lowest entry index winning, or null when nothing matches. Pure so it is unit-testable.
 */
internal fun smartBorrowBestMatch(rowTexts: List<String>, priority: List<String> = SmartBorrowList.priority): Pair<Int, Int>? {
    for ((entryIdx, entry) in priority.withIndex()) {
        val rowIdx = rowTexts.indexOfFirst { borrowRowMatchesPreference(it, entry) }
        if (rowIdx >= 0) return entryIdx to rowIdx
    }
    return null
}

/**
 * The character part of a borrow entry or row text: whatever follows the outfit's closing
 * bracket, falling back to the last line for bracketless OCR reads. The game's duplicate rule
 * is per CHARACTER, not per card, so exclusions must strip the outfit.
 */
internal fun borrowEntryCharacter(entry: String): String {
    val afterBracket = entry.substringAfterLast(']', "").trim()
    if (afterBracket.isNotEmpty()) return afterBracket
    return entry.trim().lines().last().trim()
}

/**
 * [priorities] minus every entry whose character is in [excludedCharacters] (any outfit of an
 * excluded character goes - the deck clash is character-wide). Exclusion names match through
 * [borrowRowMatchesPreference], so OCR-derived names work too.
 */
internal fun filterBorrowPriorities(priorities: List<String>, excludedCharacters: Collection<String>): List<String> =
    priorities.filter { entry -> excludedCharacters.none { borrowRowMatchesPreference(entry, it) } }
