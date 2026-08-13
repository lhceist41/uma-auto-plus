package com.steve1316.uma_android_automation

/**
 * Pure launch-safety policy for explicit saved-support-formation selection.
 *
 * Why this exists: with Auto-Fill off the navigator leaves whatever support formation the
 * career-start Support Formation screen currently shows, and the game defaults that screen to the
 * last deck actually used in a career. On 2026-08-13 a mission retry that required the hand-built
 * mission formation (Deck 5) launched on the game's default (Deck 2) instead -- Auto-Fill off and
 * Smart Borrow both worked, but nothing selected or positively verified the OWNED five-card
 * formation, and leaving Deck 5 active only in the separate Edit Support Deck editor does not make
 * it the career-start default. The wrong-deck career had to be caught by eye after it started.
 *
 * This object holds the pure decisions that make an explicit "require Deck N" contract enforceable
 * and JUnit-testable without a device: parsing the visible "Deck N" label into an exact identity,
 * the shortest-path step toward the requested deck, and the bounded read -> navigate -> re-read loop
 * that either reaches the requested deck or fails closed. Reads and taps are injected, so the loop
 * is exercised with fakes; the navigator wires the real OCR + arrow taps. An unknown or wrong deck
 * is always an [Outcome.Blocked], never a best-effort pass: Start Career must be unreachable unless
 * the active deck is exactly the requested one.
 */
internal object SupportDeckSelector {
    /** Saved support-formation slots the game exposes on the deck selector. */
    const val MIN_DECK = 1
    const val MAX_DECK = 10

    /**
     * Bound on deck-arrow steps. Ten decks with no wraparound means the farthest legal move is
     * [MAX_DECK] - [MIN_DECK] = 9 taps; the margin covers a stray re-read before the run fails closed
     * rather than looping. Exceeding it is a [Outcome.Blocked], never a silent give-up-and-proceed.
     */
    const val MAX_STEPS = 12

    /** Which deck-selector arrow to tap to move one step toward the requested deck. */
    enum class Direction { LEFT, RIGHT }

    /** The terminal result of trying to make the requested deck the active one. */
    sealed class Outcome {
        /** The active deck is exactly the requested deck (a fresh read confirmed it). */
        object Verified : Outcome()

        /** The requested deck could not be positively verified; the caller must refuse Start Career. */
        data class Blocked(val reason: String) : Outcome()
    }

    /**
     * The deck this launch must select, or null when no explicit deck is configured. The setting uses
     * 0 as its off sentinel (valid deck slots are 1..10), so 0 -> null (legacy behavior). A non-zero
     * value is returned as-is -- even an out-of-range one -- so the caller runs [run], which fails
     * closed on the invalid request instead of silently clamping it to a real deck or ignoring it.
     */
    fun requestedIndexOrNull(rawSetting: Int): Int? = rawSetting.takeIf { it != 0 }

    private val deckLabelRegex = Regex("""deck\s*(\d{1,2})""", RegexOption.IGNORE_CASE)

    /**
     * Parse the OCR of the deck-selector label into an exact 1..10 identity. Requires a recognizable
     * "Deck" token immediately before the number, so a stray count elsewhere in the crop cannot be
     * read as the deck; a number outside 1..10, a missing token, or an empty read is UNKNOWN (null).
     * No fuzzy nearest-deck: an unreadable label fails closed upstream, it never guesses.
     */
    fun parseDeckLabel(ocrText: String): Int? {
        val match = deckLabelRegex.find(ocrText) ?: return null
        val parsed = match.groupValues[1].toIntOrNull() ?: return null
        return if (parsed in MIN_DECK..MAX_DECK) parsed else null
    }

    /** The arrow to tap to move one step from [current] toward [requested], or null when already there. */
    fun stepToward(current: Int, requested: Int): Direction? =
        when {
            current == requested -> null
            current < requested -> Direction.RIGHT
            else -> Direction.LEFT
        }

    /**
     * Read the active deck, navigate toward [requested] with bounded arrow taps -- re-reading fresh
     * after every tap -- and return [Outcome.Verified] only when a fresh read equals [requested].
     * Fails closed on: an out-of-range request, an unreadable deck (initial or after a tap), a tap
     * that does not change the number (a stalled or boundary arrow), or exceeding [maxSteps].
     *
     * @param readDeck reads the currently-visible deck number; null when unreadable or ambiguous.
     * @param tapArrow taps the given selector arrow and lets the UI settle before the next read.
     */
    fun run(
        requested: Int,
        readDeck: () -> Int?,
        tapArrow: (Direction) -> Unit,
        maxSteps: Int = MAX_STEPS,
    ): Outcome {
        if (requested !in MIN_DECK..MAX_DECK) {
            return Outcome.Blocked("requested deck $requested is outside the valid range $MIN_DECK..$MAX_DECK")
        }
        var current = readDeck() ?: return Outcome.Blocked("the active deck number could not be read")
        var steps = 0
        while (current != requested) {
            if (steps >= maxSteps) {
                return Outcome.Blocked("did not reach Deck $requested within $maxSteps arrow steps (stuck reading Deck $current)")
            }
            val direction = stepToward(current, requested) ?: break
            tapArrow(direction)
            steps++
            val next = readDeck() ?: return Outcome.Blocked("the active deck number could not be read after navigation")
            if (next == current) {
                return Outcome.Blocked("deck navigation stalled at Deck $current (the arrow did not change the deck)")
            }
            current = next
        }
        return Outcome.Verified
    }
}
