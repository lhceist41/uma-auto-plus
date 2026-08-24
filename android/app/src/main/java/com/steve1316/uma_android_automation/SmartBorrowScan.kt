package com.steve1316.uma_android_automation

/**
 * One visible screen of the Borrow Card picker: the rows that can be borrowed, plus the texts of
 * the rows the game itself tagged "! Duplicate Support" or "! Trainee".
 *
 * The tagged rows are kept even though they can never be picked. They are the only evidence that
 * a screen full of blocked cards is a real screen rather than the end of the list, and losing that
 * evidence is what stopped a re-selection scan one screen in.
 */
internal data class BorrowScan(
    val rows: List<Pair<Double, String>>,
    val duplicateTexts: List<String> = emptyList(),
    val traineeConflictTexts: List<String> = emptyList(),
)

/**
 * Normalized identity of a row's OCR text: lowercase alphanumerics only, so bracket glyphs, line
 * breaks, and spacing noise cannot make one row read as two different ones. Empty when the row
 * carried nothing readable.
 */
internal fun borrowRowKey(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/**
 * Every readable row identity on a screen, borrowable and blocked alike.
 *
 * Counting the blocked rows is what makes paging honest. A screen showing nothing but
 * "! Duplicate Support" rows contributes no borrowable row at all, so a freshness rule that
 * looked only at borrowable rows read such a screen as the tail of the list and stopped there,
 * even with the whole list still below it.
 */
internal fun borrowScreenKeys(screen: BorrowScan): List<String> =
    (screen.rows.map { it.second } + screen.duplicateTexts + screen.traineeConflictTexts)
        .map(::borrowRowKey)
        .filter { it.isNotEmpty() }

/**
 * Ordered fingerprint of a screen, used to tell a page that advanced from one that did not.
 * Identity only: row positions shift by a few pixels between captures, so coordinates would
 * report movement that did not happen.
 */
internal fun borrowScreenSignature(screen: BorrowScan): String = borrowScreenKeys(screen).joinToString("|")

/** Alphanumerics a row must carry before it may be tapped without a name to match against. */
private const val BORROW_MIN_READABLE_CHARS = 3

/**
 * Whether a row read well enough to act on. A row whose OCR failed comes back blank or as a
 * fragment; tapping one commits an unknown card, which is worse than skipping it.
 */
internal fun borrowRowIsReadable(text: String): Boolean = borrowRowKey(text).length >= BORROW_MIN_READABLE_CHARS

/**
 * Final gate immediately before a tap: the row must be readable, must not be a character the deck
 * already refused this launch, must not be the active trainee's own character, and when the tap is
 * aimed at a specific card ([intendedEntry]) the row on screen must still be that card.
 *
 * The pill-based filtering in the screen reader already removes tagged rows. This is the identity
 * re-check on top of it, so a reorder between reading a screen and acting on it cannot commit a
 * card nobody chose.
 */
internal fun borrowTapApproved(
    rowText: String,
    intendedEntry: String?,
    excludedCharacters: Collection<String>,
    traineeTarget: String,
): Boolean {
    if (!borrowRowIsReadable(rowText)) return false
    if (excludedCharacters.any { borrowRowMatchesPreference(rowText, it) }) return false
    if (traineeTarget.isNotBlank() && borrowCandidateConflictsWithTrainee(rowText, traineeTarget)) return false
    return intendedEntry == null || borrowRowMatchesPreference(rowText, intendedEntry)
}

/** Why a list walk ended. */
internal enum class BorrowWalkEnd {
    /** The visitor took its action and stopped the walk. */
    PICKED,

    /** The list stopped producing new rows, or stopped moving under the page gesture. */
    END_OF_LIST,

    /** The page-gesture budget ran out with the list still moving. */
    MAX_PAGES,

    /** The bot was stopped mid-walk. */
    ABORTED,

    /** The first screen carried no readable row at all: the picker is not open. */
    EMPTY_PICKER,
}

/** What the one bounded accessibility-service recovery did on a walk, for the log and for tests.
 * NONE: the recovery was never needed (no gap outlasted the gesture ladder). PERFORMED: a rebind was
 * issued (the walk then took its single post-rebind retry). UNAVAILABLE: a gap outlasted the ladder but
 * no recovery could be performed (no live game attached, or the callback declined), so the walk stalled. */
internal enum class BorrowRecovery { NONE, PERFORMED, UNAVAILABLE }

/** What a walk did, for the log and for the caller's fallback decision. */
internal data class BorrowWalkResult(
    val end: BorrowWalkEnd,
    val screensInspected: Int,
    val pageGestures: Int,
    val swallowedRetries: Int,
    /** True when the walk ended because the page gesture could not move the list even after the recovery
     * ladder AND the one accessibility rebind -- a swallowed drag or dead gesture dispatch the recovery
     * never cleared, not the natural end. The list may hold rows below the last one seen, so a card
     * absent from a stalled walk is NOT proof of absence. */
    val stalled: Boolean = false,
    /** Observability for the bounded accessibility recovery, so a skipped or failed rebind is never silent
     * (the A3-R3 live defect emitted no reason). Distinguishes "recovery not needed" from "performed" from
     * "needed but unavailable". */
    val recovery: BorrowRecovery = BorrowRecovery.NONE,
) {
    val picked: Boolean get() = end == BorrowWalkEnd.PICKED

    /** True only when the walk saw the whole list: it reached the natural end (a screen with no new rows)
     * without stalling. A card absent from a fully-traversed walk is genuinely not in the current pool; a
     * card absent from a non-fully-traversed walk may simply not have been reached. */
    val fullyTraversed: Boolean get() = end == BorrowWalkEnd.END_OF_LIST && !stalled
}

/**
 * The one bounded traversal of the Borrow Card list, shared by discovery and by every
 * re-selection pass so their paging limits and end-of-list rules cannot drift apart.
 *
 * Reading a screen and advancing a page are injected, which keeps the traversal itself free of
 * OpenCV, OCR, and gestures, and therefore unit-testable.
 *
 * Bounds, all hard:
 *  - at most [maxPageGestures] FORWARD advances (so at most that many pages plus the first screen);
 *    this is the traversal budget and the only thing that ends a walk with MAX_PAGES;
 *  - a gesture that does not change the screen is retried at most [maxSwallowedRetries] times per gap.
 *    These retries are recovery, not forward progress: they do NOT spend the forward advance budget, so
 *    a stall can never starve the one accessibility rebind below (the A3-R3 live defect, where the ladder
 *    consumed the whole page budget and the rebind gate `gestures < maxPageGestures` was then unreachable);
 *  - at most ONE accessibility-service rebind per walk, plus its single post-rebind retry;
 *  - a screen whose rows were all seen before ends the walk.
 * Every no-movement gap is bounded by [maxSwallowedRetries] and by the once-per-walk rebind, and the
 * number of gaps is bounded by the forward advances, so no path repeats without consuming a hard budget.
 */
internal class BorrowListWalker(
    private val maxPageGestures: Int,
    private val maxSwallowedRetries: Int,
    private val readScreen: () -> BorrowScan,
    /** Advances the list by one page. [attempt] is 0 for the normal gesture and rises with each
     * swallowed-drag retry, so the implementation can escalate to a stronger recovery gesture. */
    private val advancePage: (attempt: Int) -> Unit,
    private val abort: () -> Boolean = { false },
    /** One bounded, last-resort recovery invoked at most ONCE per walk when the gesture recovery ladder
     * is exhausted, reachable regardless of how much forward budget the traversal already spent: on MuMu
     * the accessibility gesture dispatcher can silently die mid-run (the page gesture no-ops though the
     * service reads "enabled"), and rebinding it revives scrolling. Returns true when a recovery was
     * actually performed (so the walk takes one post-rebind retry), false when none was available (so the
     * walk declares a stall). Default: no recovery. */
    private val recoverService: () -> Boolean = { false },
    private val log: (String) -> Unit = {},
) {
    /**
     * Walks the list, calling [visit] once per unique screen with the screen and its zero-based
     * index. [visit] returns true when it has acted and the walk should stop.
     */
    fun walk(visit: (screen: BorrowScan, pageIndex: Int) -> Boolean): BorrowWalkResult {
        val seen = HashSet<String>()
        var lastSignature: String? = null
        var screens = 0
        var gestures = 0 // FORWARD advances only; the traversal budget. Stall-handling never spends it.
        var retriesThisGap = 0
        var retriesTotal = 0
        var serviceRecovered = false
        var recovery = BorrowRecovery.NONE

        while (true) {
            if (abort()) return BorrowWalkResult(BorrowWalkEnd.ABORTED, screens, gestures, retriesTotal, recovery = recovery)

            val screen = readScreen()
            val keys = borrowScreenKeys(screen)
            val signature = keys.joinToString("|")

            if (lastSignature != null && signature == lastSignature) {
                // The list did not move. Either the drag was swallowed (the picker eats short drags the
                // same way the trainee roster does) or this is the bottom. Retry with an ESCALATING
                // gesture -- the retry index becomes the advance attempt, so a swallowed short drag gets a
                // stronger recovery gesture instead of the identical one that just failed. Bounded per gap
                // by maxSwallowedRetries; these retries are recovery, NOT forward progress, so they do not
                // increment the forward budget (that is what starved the rebind below in A3-R3 live).
                if (retriesThisGap < maxSwallowedRetries) {
                    retriesThisGap++
                    retriesTotal++
                    log("page gesture did not move the list; retrying with a stronger gesture ($retriesThisGap/$maxSwallowedRetries).")
                    advancePage(retriesThisGap)
                    continue
                }
                // The gesture recovery ladder is exhausted. Before declaring a stall, try ONE bounded
                // accessibility-service rebind: on MuMu the gesture dispatcher can silently die, and the
                // stronger gestures above then no-op just the same. Gated ONLY by serviceRecovered, so it
                // stays reachable no matter how much forward budget the traversal already spent.
                if (!serviceRecovered) {
                    serviceRecovered = true
                    if (recoverService()) {
                        recovery = BorrowRecovery.PERFORMED
                        log("gesture recovery ladder exhausted; rebound the accessibility dispatcher and retrying the scroll once.")
                        // The single post-rebind retry. retriesThisGap is left at the cap on purpose: if
                        // this one retry still does not move the list, the next pass falls straight through
                        // to the stall below (recovery already spent) -- exactly one retry, never a loop.
                        advancePage(0)
                        continue
                    }
                    // A gap outlasted the ladder but no rebind could be performed (no live game attached, or
                    // the callback declined). Record it so the stall carries an explicit reason.
                    recovery = BorrowRecovery.UNAVAILABLE
                    log("gesture recovery ladder exhausted; no accessibility recovery available, marking the scroll stalled.")
                } else {
                    log("gesture recovery already spent this walk; marking the scroll stalled.")
                }
                // Recovery unavailable or already spent: mark the walk stalled so a caller can tell "the
                // list stopped moving" apart from "a screen produced no new rows" (the natural end below).
                return BorrowWalkResult(BorrowWalkEnd.END_OF_LIST, screens, gestures, retriesTotal, stalled = true, recovery = recovery)
            }
            retriesThisGap = 0
            lastSignature = signature

            if (screens == 0 && keys.isEmpty()) {
                return BorrowWalkResult(BorrowWalkEnd.EMPTY_PICKER, screens, gestures, retriesTotal, recovery = recovery)
            }

            screens++
            if (visit(screen, screens - 1)) {
                return BorrowWalkResult(BorrowWalkEnd.PICKED, screens, gestures, retriesTotal, recovery = recovery)
            }

            // Every row on this screen was already read on an earlier one: nothing further to find.
            if (keys.none { seen.add(it) }) {
                return BorrowWalkResult(BorrowWalkEnd.END_OF_LIST, screens, gestures, retriesTotal, recovery = recovery)
            }
            if (gestures >= maxPageGestures) {
                return BorrowWalkResult(BorrowWalkEnd.MAX_PAGES, screens, gestures, retriesTotal, recovery = recovery)
            }
            advancePage(0)
            gestures++
        }
    }
}

/** The row a selection walk settled on, with the walk's own statistics. */
internal data class BorrowSelection(val row: Pair<Double, String>?, val walk: BorrowWalkResult)

/**
 * Walks the whole bounded list looking for a row [accept] approves, and reports the first one.
 * [observe] sees every borrowable row on the way, which is how a caller can learn what the list
 * actually holds while it hunts for one specific card.
 */
internal fun selectFromBorrowList(
    walker: BorrowListWalker,
    observe: (String) -> Unit = {},
    accept: (String) -> Boolean,
): BorrowSelection {
    var found: Pair<Double, String>? = null
    val walk =
        walker.walk { screen, _ ->
            for (row in screen.rows) {
                observe(row.second)
                if (found == null && accept(row.second)) found = row
            }
            found != null
        }
    return BorrowSelection(found, walk)
}
