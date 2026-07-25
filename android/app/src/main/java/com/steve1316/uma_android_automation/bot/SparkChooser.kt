package com.steve1316.uma_android_automation.bot

/**
 * Pure decision layer for the career-end Spark Selection flow (the original-vs-rerolled choice
 * that follows a 30 TP reroll spend).
 *
 * Why this exists: the spend half of the reroll feature was live-proven (10 spends), but every
 * screen after the spend fell into the generic POST_RUN_RESULTS handler, which blind-clicked
 * Next/Confirm on whatever the game showed first. The pager's first page is the rerolled set,
 * so "keeps the redrawn set" was an accident of button position, not a decision, and no kept
 * record was written for spend careers at all (69 original / 55 kept / 0 rerolled corpus
 * records at the time of the fix).
 *
 * Everything in this file is total, Android-free, and JUnit-pinned: the set model, the
 * cross-frame scroll merge, the OCR text normalization, the page resolution, and the keep
 * policy. Pixel probing lives in [com.steve1316.uma_android_automation.utils.SparkScreenProbes];
 * the screen handling lives in the navigator.
 */

/** Sentinel name for a row whose OCR produced nothing. Matches the corpus convention. */
const val SPARK_UNREADABLE_NAME = "unreadable"

/** Row kind as the bar-color probe classifies it. [wire] is the corpus string and must never
 * change: existing records use stat/aptitude/unique/skill. */
enum class SparkRowKind(val wire: String) {
    STAT("stat"),
    APTITUDE("aptitude"),
    UNIQUE("unique"),
    WHITE("skill"),
}

/** Refined classification of a WHITE row for the chooser. Race sparks are always relevant
 * (they regenerate at ~20% per distinct G1 won, and a specific 3-star race spark rarely
 * survives a redraw); skill whites are relevant only when planned; an unreadable name is
 * uncertainty, not neutral value. */
enum class SparkWhiteClass { SKILL, RACE, UNKNOWN }

/** One spark row as read off a live list: OCR name, gold stars, bar kind, and (for whites)
 * the refined class the caller resolved via the skill catalog. */
data class SparkRowFact(
    val name: String,
    val stars: Int,
    val kind: SparkRowKind,
    val whiteClass: SparkWhiteClass? = null,
) {
    val unreadable: Boolean get() = name == SPARK_UNREADABLE_NAME || name.isBlank()
}

/**
 * How a complete-list scan ended. Only the two COMPLETE values prove the whole set was read;
 * everything else is a partial read that must never authorize a 30 TP spend or an automatic
 * choice. A scrollbar thumb at the bottom is deliberately NOT a termination signal (same
 * principle as the skill-scan terminations in ScrollList).
 */
enum class SparkScanTermination {
    /** The end-of-list marker was observed inside the visible window (white break or a
     * starless, textless slot past the last real row). */
    COMPLETE_END_MARKER,

    /** A scroll attempt produced a frame identical to the previous one: the list cannot move,
     * so the last merged row is the last spark. */
    COMPLETE_NO_PROGRESS,

    /** The scan budget (iterations or wall clock) ran out before either completion proof. */
    TIMED_OUT_PARTIAL,

    /** A scrolled frame shared no consistent overlap with the merged rows: the scroll went
     * past the window and rows may have been skipped. */
    ALIGNMENT_FAILED,

    /** No spark rows could be read at all (wrong screen, mid-transition frame). */
    FAILED,
    ;

    val complete: Boolean get() = this == COMPLETE_END_MARKER || this == COMPLETE_NO_PROGRESS
}

/** A full set read: ordered rows, how the scan terminated, and how many scrolls it took. */
data class SparkSetReading(
    val rows: List<SparkRowFact>,
    val termination: SparkScanTermination,
    val scrollsUsed: Int = 0,
) {
    val complete: Boolean get() = termination.complete && rows.isNotEmpty()
    val unreadableRowCount: Int get() = rows.count { it.unreadable }
}

/** Which side of the Spark Selection pager a set belongs to. [wire] is the corpus phase
 * string; "original" and "kept" predate this file and must not change. */
enum class SparkSetSide(val wire: String) {
    ORIGINAL("original"),
    REROLLED("rerolled"),
}

/**
 * The green set-name pill on a "Confirmation" dialog. THREE variants exist live, not two:
 * the post-reroll Spark Selection confirmation names the chosen side ("Original Sparks" /
 * "Rerolled Sparks"), while the ordinary keep confirmation raised by Confirm on the SPARKS
 * screen - the one every no-reroll career ends on - carries a plain "Sparks" pill.
 *
 * The plain variant was absent from the 2026-07-08 capture set (all post-spend), so the first
 * hardened build classified it as "not provably Original" and blocked a completed no-spend
 * career on 2026-07-19. [PLAIN] exists so that dialog is recognised positively instead of
 * being mistaken for an unreadable side name.
 */
enum class SparkConfirmationPill {
    /** "Sparks" - the ordinary keep confirmation; no reroll selection is in play. */
    PLAIN,

    /** "Original Sparks" - post-reroll selection, original chosen. */
    ORIGINAL,

    /** "Rerolled Sparks" - post-reroll selection, rerolled chosen. */
    REROLLED,

    /** No text recovered: the caller must not infer which dialog this is. */
    UNREADABLE,
}

/**
 * Cross-frame merge for a scrolled spark list. Frames overlap by an unknown number of rows
 * (swipe distance is not pixel-exact and the list rubber-bands), so alignment is by content:
 * a suffix of the merged rows that matches a prefix of the new frame, comparing kind and stars
 * exactly and names tolerantly (an unreadable name matches anything).
 *
 * The merge is COLLISION-SAFE: it appends the new tail only when EXACTLY ONE overlap length
 * aligns. When runs of rows that share (kind, stars) straddle the frame boundary, several
 * overlap lengths can align at once - and because the two frames are then genuinely consistent
 * with lists of different lengths, any single guess (largest or smallest) can silently drop or
 * duplicate a row. Rather than guess, an ambiguous merge returns null, which the caller records
 * as ALIGNMENT_FAILED - an incomplete read that keeps the original set instead of comparing a
 * corrupt one. This is the conservative direction: over-reporting incompleteness costs a keep;
 * under-reporting it discards a spark the choice depended on.
 */
object SparkScrollMerge {
    fun rowsAlign(a: List<SparkRowFact>, b: List<SparkRowFact>): Boolean {
        if (a.size != b.size) return false
        return a.zip(b).all { (x, y) ->
            x.kind == y.kind &&
                x.stars == y.stars &&
                (x.unreadable || y.unreadable || x.name.equals(y.name, ignoreCase = true))
        }
    }

    /** Merge [next] onto [merged], or null when no overlap aligns (no shared content) or more
     * than one aligns (ambiguous - the frames fit lists of different lengths). */
    fun merge(merged: List<SparkRowFact>, next: List<SparkRowFact>): List<SparkRowFact>? {
        if (merged.isEmpty()) return next
        if (next.isEmpty()) return merged
        var chosenOverlap = -1
        for (overlap in 1..minOf(merged.size, next.size)) {
            val tail = merged.subList(merged.size - overlap, merged.size)
            val head = next.subList(0, overlap)
            if (rowsAlign(tail, head)) {
                if (chosenOverlap != -1) return null // a second aligning overlap: ambiguous.
                chosenOverlap = overlap
            }
        }
        if (chosenOverlap == -1) return null
        return merged + next.subList(chosenOverlap, next.size)
    }
}

/**
 * OCR text normalization for the chooser's screen text. All matching is substring-based after
 * folding the usual OCR damage (casing, 0-for-o, 1/l-for-i), because ML Kit reliably delivers
 * the word cores while mangling individual glyphs.
 */
object SparkTextNorm {
    private fun fold(text: String): String = text.lowercase().replace('0', 'o').replace('1', 'i').replace('l', 'i')

    /** Pager heading ("Rerolled Sparks" / "Original Sparks") to a side, or null. Checked on
     * the pager and on the Confirmation dialog's set-name band. "rero" and "rigina" survive
     * the fold distinctly ("reroiied"/"originai"), so the two sides cannot be confused. */
    fun headingSide(text: String?): SparkSetSide? {
        if (text == null) return null
        val folded = fold(text)
        return when {
            folded.contains("rero") -> SparkSetSide.REROLLED
            folded.contains("rigina") -> SparkSetSide.ORIGINAL
            else -> null
        }
    }

    /** Whether a title read matches the "Sparks Rerolled" result screen. */
    fun isSparksRerolledTitle(text: String?): Boolean = text != null && fold(text).contains("rero")

    /** Whether a title read matches the "Spark Selection" intro dialog. */
    fun isSparkSelectionTitle(text: String?): Boolean {
        if (text == null) return false
        val folded = fold(text)
        return folded.contains("seiect") || folded.contains("select")
    }

    /**
     * Classify a Confirmation dialog's green set-name pill. A side name wins over the plain
     * form (the side variants also contain the word "Sparks"); text that contains neither a
     * side name nor the word "spark" is [SparkConfirmationPill.UNREADABLE] rather than being
     * forced into a variant, so a mangled read can never be mistaken for the ordinary dialog.
     */
    fun confirmationPill(text: String?): SparkConfirmationPill {
        if (text.isNullOrBlank()) return SparkConfirmationPill.UNREADABLE
        return when (headingSide(text)) {
            SparkSetSide.REROLLED -> SparkConfirmationPill.REROLLED
            SparkSetSide.ORIGINAL -> SparkConfirmationPill.ORIGINAL
            null -> if (fold(text).contains("spark")) SparkConfirmationPill.PLAIN else SparkConfirmationPill.UNREADABLE
        }
    }

    /** Canonical pink-spark style name for a settings value ("Front", "Pace Chaser", ...), or
     * null when the value maps to no style. Pink aptitude sparks use the full style names. */
    fun canonicalStyleName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val folded = fold(raw)
        return when {
            folded.contains("front") -> "Front Runner"
            folded.contains("pace") -> "Pace Chaser"
            folded.contains("iate") || folded.contains("late") -> "Late Surger"
            folded.contains("end") -> "End Closer"
            else -> null
        }
    }

    /** Loose equality for spark/skill/stat names across OCR fuzz. */
    fun namesEqual(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val fa = fold(a).filter { it.isLetterOrDigit() }
        val fb = fold(b).filter { it.isLetterOrDigit() }
        return fa.isNotEmpty() && fa == fb
    }

    fun nameInList(name: String?, list: Collection<String>): Boolean = list.any { namesEqual(name, it) }

    /**
     * Same-row name comparison for two reads OF THE SAME LIST (SPARKS screen vs the keep
     * dialog). Tolerant one step past [namesEqual]: an unreadable side matches anything (the
     * merge's rule), and one folded form containing the other counts as the same name when the
     * shorter form is long enough to be distinctive. That absorbs single-glyph OCR fuzz such
     * as the live "Unity CupP" vs "Unity Cup" (2026-07-21) without letting a genuinely
     * different name pass: two different sparks in the same row position never differ by a
     * contained prefix alone.
     */
    fun namesCompatible(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        if (a == SPARK_UNREADABLE_NAME || b == SPARK_UNREADABLE_NAME) return true
        val fa = fold(a).filter { it.isLetterOrDigit() }
        val fb = fold(b).filter { it.isLetterOrDigit() }
        if (fa.isEmpty() || fb.isEmpty()) return true
        if (fa == fb) return true
        val shorter = if (fa.length <= fb.length) fa else fb
        val longer = if (fa.length <= fb.length) fb else fa
        return shorter.length >= 5 && longer.contains(shorter)
    }
}

/** Per-row star evidence the keep-dialog read carries alongside its facts: the counted stars
 * plus how many slots were too ambiguous to classify. Kept probe-free so the verdict stays a
 * pure function. */
data class SparkStarEvidence(val stars: Int, val ambiguousSlots: Int)

/** Outcome of one keep-dialog verification pass. */
sealed class SparkKeepVerdict {
    /** Every row matches the original read exactly: confirm. */
    object Confirm : SparkKeepVerdict()

    /** Names, kinds, order, and count all match and the only unresolved differences are star
     * counts on rows whose evidence was ambiguous after every retry: confirm, logging the star
     * check as corroborative. [rows] are the 1-based rows confirmed on semantics. */
    data class ConfirmCorroborative(val rows: List<Int>) : SparkKeepVerdict()

    /** A star mismatch with retry budget left: rescan a fresh frame before judging. */
    object Retry : SparkKeepVerdict()

    /** A proven contradiction: never confirm. [reason] is the exact block message. */
    data class Block(val reason: String) : SparkKeepVerdict()
}

/**
 * Evidence-fusion verdict for the ordinary keep confirmation (SPARKS_KEEP_CONFIRMATION only:
 * the plain-"Sparks" dialog cannot switch sides, and the original set was already read
 * completely on the SPARKS screen, so row names, kinds, order, and count are the primary
 * confirmation evidence and star counts corroborate).
 *
 * Semantic evidence is judged first and blocks outright: a different row count, a kind
 * mismatch, or a readable-name mismatch means the dialog is not showing the set this career
 * rolled. Star mismatches alone are retried on fresh frames ([SparkKeepVerdict.Retry]) while
 * budget remains - a single frame's star read has a proven transient failure mode (the
 * 2026-07-21 Medium 3*-as-2* block) - and only a mismatch that REPRODUCES after every retry
 * with unambiguous slot evidence blocks. A mismatch whose rows still carry ambiguous slots
 * after the retries confirms corroboratively instead: names, order, kinds, and count match a
 * set this career provably rolled, and the game offers no second set a keep could lose.
 *
 * The selected-side Original-vs-Rerolled confirmation deliberately does NOT use this rule:
 * there a star misread can select the wrong side, so its strict verification stays.
 */
fun keepDialogVerdict(
    original: List<SparkRowFact>,
    dialog: List<SparkRowFact>,
    evidence: List<SparkStarEvidence>?,
    retriesUsed: Int,
    maxRetries: Int,
): SparkKeepVerdict {
    if (dialog.size != original.size) {
        return SparkKeepVerdict.Block(
            "the keep dialog lists ${dialog.size} row(s) but the complete SPARKS screen read had ${original.size}; not confirming",
        )
    }
    for (i in original.indices) {
        if (original[i].kind != dialog[i].kind) {
            return SparkKeepVerdict.Block(
                "keep-dialog row ${i + 1} (${dialog[i].kind.wire}) contradicts the original set read on the SPARKS screen " +
                    "(${original[i].kind.wire}); not confirming",
            )
        }
        if (!SparkTextNorm.namesCompatible(original[i].name, dialog[i].name)) {
            return SparkKeepVerdict.Block(
                "keep-dialog row ${i + 1} (\"${dialog[i].name}\") contradicts the original set read on the SPARKS screen " +
                    "(\"${original[i].name}\"); not confirming",
            )
        }
    }
    val starMismatches = original.indices.filter { original[it].stars != dialog[it].stars }
    if (starMismatches.isEmpty()) return SparkKeepVerdict.Confirm
    if (retriesUsed < maxRetries) return SparkKeepVerdict.Retry
    val allAmbiguous = starMismatches.all { (evidence?.getOrNull(it)?.ambiguousSlots ?: 0) > 0 }
    if (allAmbiguous) return SparkKeepVerdict.ConfirmCorroborative(starMismatches.map { it + 1 })
    val i = starMismatches.first { (evidence?.getOrNull(it)?.ambiguousSlots ?: 0) == 0 }
    return SparkKeepVerdict.Block(
        "keep-dialog row ${i + 1} (${dialog[i].kind.wire}/${dialog[i].stars}*) contradicts the original set read on the SPARKS screen " +
            "(${original[i].kind.wire}/${original[i].stars}*) with unambiguous star evidence on every retry; not confirming",
    )
}

/** The pager page the bot is actually looking at, resolved from BOTH signals. */
sealed class SparkPagerResolution {
    /** Heading and page indicator agree. */
    data class Resolved(val side: SparkSetSide) : SparkPagerResolution()

    /** Heading and page indicator disagree: never act on a contradictory page. */
    object Contradictory : SparkPagerResolution()

    /** One or both signals were unreadable. */
    object Unreadable : SparkPagerResolution()
}

/**
 * Resolve the current pager page. The heading names the CONTENT; the active page dot gives the
 * POSITION, mapped through the observed page order (page 1 = Rerolled Sparks, page 2 =
 * Original Sparks on the live 2026-07-08 captures). Both signals are required and must agree:
 * the page is never assumed, and a disagreement (layout change, misread) blocks instead of
 * confirming a set the bot cannot prove it is looking at.
 */
fun resolvePagerSide(headingSide: SparkSetSide?, activeDotIndex: Int?): SparkPagerResolution {
    val dotSide = sparkPagerDotSide(activeDotIndex)
    return when {
        headingSide == null || dotSide == null -> SparkPagerResolution.Unreadable
        headingSide == dotSide -> SparkPagerResolution.Resolved(headingSide)
        else -> SparkPagerResolution.Contradictory
    }
}

/** Map a lit page-dot index to its side under the live-proven page order (dot 1 = Rerolled,
 * dot 2 = Original). Anything else -- no lit dot, both lit -- is unreadable. */
fun sparkPagerDotSide(activeDotIndex: Int?): SparkSetSide? =
    when (activeDotIndex) {
        1 -> SparkSetSide.REROLLED
        2 -> SparkSetSide.ORIGINAL
        else -> null
    }

/** The gesture the pager planner asks the navigator to dispatch. */
enum class SparkPagerAction { NONE, SWIPE_LEFT, SWIPE_RIGHT }

/** One planned pager gesture: endpoints in screen pixels plus the page it must land on. */
data class SparkPagerSwipePlan(
    val action: SparkPagerAction,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
    val expectedPage: SparkSetSide,
)

/**
 * Plans the Spark Selection pager's page-change gesture.
 *
 * The pager is paged with a horizontal drag across its central content, never with a tap on
 * the edge chevrons. The 2026-07-20 supervised run aimed two taps at the right chevron's own
 * measured pixels (990, 228) and the page never moved while every mid-screen tap in the same
 * minute landed: the thin chevron outline is a poor dispatch target, and the floating overlay
 * bubble rides the same screen edge and can swallow edge taps outright. The Scenario Select
 * carousel has paged reliably with a central drag since it shipped; the pager follows that
 * precedent.
 *
 * Page order is live-proven (page 1 = Rerolled Sparks, page 2 = Original Sparks), so moving
 * Rerolled -> Original drags the content leftward (finger travels right-to-left, the same
 * forward drag the carousel uses) and Original -> Rerolled drags back. A wrong direction
 * cannot mis-confirm anything: the pager has no page 0/3 to land on, the drag rubber-bands,
 * and the repaint verification reads UNCHANGED.
 */
object SparkPagerNav {
    /** The measured layout every coordinate below is anchored to (see SparkScreenProbes).
     * Plans scale linearly to the actual capture size. */
    const val REFERENCE_WIDTH = 1080
    const val REFERENCE_HEIGHT = 1920

    // Exclusion zones, in reference pixels. Swipe endpoints must clear every one of them.
    // The floating overlay bubble snaps to EITHER screen edge and the user can drag it along
    // that edge, so both full-height edge strips are out of bounds, not just the bubble's
    // last seen position. The spark list's scrollbar rides the list's right edge inside the
    // right strip. The bottom band holds the wide Confirm (center 540,1769) that irreversibly
    // commits the visible page; the top band holds the chevrons (y=228), heading, and page
    // dots (y=272).
    const val EDGE_OVERLAY_WIDTH = 100
    const val SCROLLBAR_MIN_X = 1015
    const val CONFIRM_ZONE_MIN_Y = 1650
    const val HEADER_ZONE_MAX_Y = 300

    /** A vertical component would scroll the spark list instead of paging; planned swipes
     * are flat, and anything at or above this delta is a planning bug. */
    const val LIST_SCROLL_DY_LIMIT = 24

    // Safe lanes: horizontal bands across the row region, far from every zone above. Lane 1
    // mirrors the carousel's proven 80% -> 20% drag at mid-list height; the retry uses a
    // different band and a longer, slower drag in case the first release point sat somewhere
    // inert.
    const val LANE1_Y = 900
    const val LANE1_NEAR_X = 864
    const val LANE1_FAR_X = 216
    const val LANE1_DURATION_MS = 450L
    const val LANE2_Y = 620
    const val LANE2_NEAR_X = 918
    const val LANE2_FAR_X = 162
    const val LANE2_DURATION_MS = 600L

    fun plan(
        current: SparkSetSide,
        target: SparkSetSide,
        attempt: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): SparkPagerSwipePlan {
        if (current == target) {
            return SparkPagerSwipePlan(SparkPagerAction.NONE, 0f, 0f, 0f, 0f, 0L, target)
        }
        val retry = attempt >= 2
        val laneY = if (retry) LANE2_Y else LANE1_Y
        val nearX = if (retry) LANE2_NEAR_X else LANE1_NEAR_X
        val farX = if (retry) LANE2_FAR_X else LANE1_FAR_X
        val duration = if (retry) LANE2_DURATION_MS else LANE1_DURATION_MS
        val action = if (target == SparkSetSide.ORIGINAL) SparkPagerAction.SWIPE_LEFT else SparkPagerAction.SWIPE_RIGHT
        val startX = if (action == SparkPagerAction.SWIPE_LEFT) nearX else farX
        val endX = if (action == SparkPagerAction.SWIPE_LEFT) farX else nearX
        val sx = screenWidth / REFERENCE_WIDTH.toFloat()
        val sy = screenHeight / REFERENCE_HEIGHT.toFloat()
        return SparkPagerSwipePlan(action, startX * sx, laneY * sy, endX * sx, laneY * sy, duration, target)
    }
}

/** Post-gesture verdict on the pager page, from a FRESH capture's heading and page dot. */
enum class SparkPagerRepaint {
    /** Both signals independently name the target page: the repaint is proven. */
    VERIFIED,

    /** Both signals still name the starting page: the gesture did not take. The screen is
     * provably in a known state, so exactly one more attempt is safe. */
    UNCHANGED,

    /** The heading OCR yielded no side. */
    HEADING_UNREADABLE,

    /** The page-dot probe yielded no single lit dot. */
    DOTS_UNREADABLE,

    /** The signals disagree with each other. Never act on a contradictory page. */
    CONTRADICTION,
}

/**
 * Classify what a fresh post-gesture capture proves. Success is deliberately narrow: the
 * heading OCR and the lit page dot must BOTH name the target page. A settle timer is never
 * proof, a single signal is never proof, and anything unreadable or contradictory blocks
 * upstream instead of being swiped again toward a blind Confirm.
 */
fun classifySparkPagerRepaint(
    headingSide: SparkSetSide?,
    activeDotIndex: Int?,
    current: SparkSetSide,
    target: SparkSetSide,
): SparkPagerRepaint {
    val dotSide = sparkPagerDotSide(activeDotIndex)
    return when {
        headingSide == null -> SparkPagerRepaint.HEADING_UNREADABLE
        dotSide == null -> SparkPagerRepaint.DOTS_UNREADABLE
        headingSide != dotSide -> SparkPagerRepaint.CONTRADICTION
        headingSide == target -> SparkPagerRepaint.VERIFIED
        headingSide == current -> SparkPagerRepaint.UNCHANGED
        else -> SparkPagerRepaint.CONTRADICTION
    }
}

/**
 * Why a 30 TP redraw was not priced, in precedence order. The live 2026-07-19 decline logged
 * "spark rows: unexpected layout, scan: missing, transaction: missing" - three clauses for one
 * cause, two of them misleading: no scan had been SKIPPED rather than failed, and the row
 * layout was never actually inspected because the transaction was already gone. Each
 * prerequisite is now reported independently and only the first genuine blocker is named.
 */
enum class SparkSpendBlocker(val wire: String) {
    /** All prerequisites met; the EV policy decides. */
    NONE("none"),

    /** No live career transaction, so nothing may be read, priced, or spent. */
    TRANSACTION_MISSING("transaction_missing"),

    /** The career-end stats snapshot is absent or short; the redraw cannot be priced. */
    STATS_SNAPSHOT_MISSING("stats_snapshot_missing"),

    /** The complete-list scan never ran (a prior blocker short-circuited it). */
    ORIGINAL_READ_SKIPPED("original_read_skipped"),

    /** The scan ran but terminated without proving it saw the whole set. */
    ORIGINAL_READ_INCOMPLETE("original_read_incomplete"),

    /** The set was read completely but does not lead stat / aptitude / unique. */
    LAYOUT_UNEXPECTED("layout_unexpected"),
}

/**
 * One spend-decision prerequisite report. Pure and total: [blocker] names the FIRST genuine
 * problem in precedence order, and every field states its own fact independently so a log
 * line can never claim a downstream stage failed when it was simply never reached.
 */
data class SparkSpendDiagnostics(
    val transactionPresent: Boolean,
    val statsSnapshotSize: Int?,
    /** Null when no scan was attempted at all (as opposed to one that ran and fell short). */
    val scanTermination: SparkScanTermination?,
    val readComplete: Boolean,
    val rowCount: Int,
    val leadsCorrectly: Boolean,
) {
    val blocker: SparkSpendBlocker =
        when {
            !transactionPresent -> SparkSpendBlocker.TRANSACTION_MISSING
            statsSnapshotSize == null || statsSnapshotSize < 5 -> SparkSpendBlocker.STATS_SNAPSHOT_MISSING
            scanTermination == null -> SparkSpendBlocker.ORIGINAL_READ_SKIPPED
            !readComplete -> SparkSpendBlocker.ORIGINAL_READ_INCOMPLETE
            !leadsCorrectly -> SparkSpendBlocker.LAYOUT_UNEXPECTED
            else -> SparkSpendBlocker.NONE
        }

    val spendAllowed: Boolean get() = blocker == SparkSpendBlocker.NONE

    /** Greppable one-liner: the blocker first, then every prerequisite's own honest state. */
    fun format(): String {
        val scan = scanTermination?.name ?: "not attempted"
        val stats = statsSnapshotSize?.toString() ?: "missing"
        return "blocker=${blocker.wire} transaction=${if (transactionPresent) "present" else "missing"} " +
            "stats_snapshot=$stats original_read=${if (scanTermination == null) "skipped" else if (readComplete) "complete" else "incomplete"} " +
            "scan=$scan rows=$rowCount layout=${if (leadsCorrectly) "ok" else "unexpected"}"
    }
}

/** Everything the keep policy knows about the career's build. Missing fields degrade
 * gracefully (a null axis matches nothing; empty targets score every blue as non-target). */
data class SparkChooserProfile(
    val traineeIdentity: String?,
    val objective: String?,
    /** Ordered blue-spark target stats, highest priority first (training.focusOnSparkStatTarget). */
    val blueTargetsOrdered: List<String>,
    val preferredDistance: String?,
    val preferredStyle: String?,
    val preferredSurface: String?,
    val plannedSkillNames: List<String>,
)

/** Per-side score breakdown, kept structured so the choice record can persist it verbatim. */
data class SparkSideBreakdown(
    val targetBlueStars: Int,
    /** Index of the blue's stat in the ordered targets, or -1 when not a target / unreadable. */
    val blueTargetRank: Int,
    val rawBlueStars: Int,
    val matchedPinkStars: Int,
    val rawPinkStars: Int,
    val uniqueStars: Int,
    val relevantWhiteStars: Int,
    val protectedTargetBlue3: Int,
    val protectedDesiredPink3: Int,
    val protectedRelevantWhite3: Int,
    val unknownWhite3: Int,
    val totalStars: Int,
    val rowCount: Int,
    val unreadableRows: Int,
    val complete: Boolean,
) {
    /** Flat map for the telemetry record. Key names are part of the corpus schema. */
    fun toRecordMap(): Map<String, Any> =
        linkedMapOf(
            "target_blue_stars" to targetBlueStars,
            "blue_target_rank" to blueTargetRank,
            "raw_blue_stars" to rawBlueStars,
            "matched_pink_stars" to matchedPinkStars,
            "raw_pink_stars" to rawPinkStars,
            "unique_stars" to uniqueStars,
            "relevant_white_stars" to relevantWhiteStars,
            "protected_three_star" to (protectedTargetBlue3 + protectedDesiredPink3 + protectedRelevantWhite3),
            "unknown_white_three_star" to unknownWhite3,
            "total_stars" to totalStars,
            "rows" to rowCount,
            "unreadable_rows" to unreadableRows,
            "complete" to complete,
        )
}

/** The policy's decision: which side to keep, why, and whether it was a real comparison
 * ([certain]) or the conservative keep-original fallback for an uncertain read. */
data class SparkChoice(
    val side: SparkSetSide,
    val decidedBy: String,
    val reason: String,
    val certain: Boolean,
    val original: SparkSideBreakdown,
    val rerolled: SparkSideBreakdown,
)

/**
 * Keep-original-vs-keep-rerolled comparison. Separate from [SparkRerollPolicy] on purpose:
 * that object prices "should 30 TP be spent" from expected redraw odds BEFORE the redraw
 * exists; this one compares two ACTUAL sets row by row AFTER it does.
 *
 * Deliberately a conservative lexicographic policy with no numeric weights. The tier order is
 * the farm program's value order and each tier is a plain integer comparison:
 *
 *  R1 three-star protection: compare the per-class 3-star holdings [target blue, desired
 *     pink, relevant white] lexicographically. A side is never discarded while it uniquely
 *     holds a 3-star of a class the other side cannot match at that class or above. An
 *     unreadable-name 3-star white counts protectively for the ORIGINAL side only:
 *     uncertainty protects a holding, it never earns the redraw credit.
 *  T1 blue: target-stat stars, then earlier target rank, then raw stars. Blues outrank
 *     everything because spark farming exists for blue floors.
 *  T2 pink: profile-matched stars, then raw stars.
 *  T3 unique: stars.
 *  T4 relevant whites: summed stars over race sparks and planned skill sparks. Irrelevant
 *     whites are deliberately excluded here so they can never outweigh a better blue.
 *  T5 total stars, then row count.
 *  Tie: ORIGINAL (keeping the original is free; keeping the rerolled must be earned).
 *
 * On race-spark relevance: every race spark is treated as relevant, not gated on the career's
 * objective or on any target-parent race loop. This is deliberate and conservative. A race
 * spark is inheritance material a farm career exists to collect, and a redraw of the same
 * career cannot lose the original, so the only real cost of protecting one is the 30 TP already
 * spent. Because race protection lives in T4 and in the LOWEST slot of the R1 vector (below both
 * target blue and desired pink), a race spark can never outrank a superior target-blue or
 * matching-pink holding of the other side - a 3-star target blue always beats a 3-star race
 * white, since the R1 vector compares target blue first. It can, however, hold a set together
 * when neither side has a better blue/pink to show (the 2026-07-08 anchor is exactly this: a
 * 3-star race white with no 3-star blue or pink on either side). The known limitation, left for
 * a future parent-loop model rather than guessed at here: a race spark for a G1 the account will
 * never breed toward is protected the same as a targeted one. Over-protecting a rare holding is
 * the safe error; discarding an exceptional one is not.
 *
 * An incomplete or empty reading on either side short-circuits to keep-original with
 * [SparkChoice.certain] = false: the caller may only act on that fallback when it can verify
 * the Original page and the final confirmation header, and must block otherwise.
 */
object SparkKeepPolicy {
    fun breakdown(reading: SparkSetReading, profile: SparkChooserProfile, protectUnknownWhites: Boolean): SparkSideBreakdown {
        val rows = reading.rows
        val blue = rows.firstOrNull { it.kind == SparkRowKind.STAT }
        val blueRank =
            if (blue == null || blue.unreadable) {
                -1
            } else {
                profile.blueTargetsOrdered.indexOfFirst { SparkTextNorm.namesEqual(it, blue.name) }
            }
        val targetBlueStars = if (blueRank >= 0) blue!!.stars else 0
        val pinks = rows.filter { it.kind == SparkRowKind.APTITUDE }
        val matchedPinks =
            pinks.filter { pink ->
                !pink.unreadable &&
                    (
                        SparkTextNorm.namesEqual(pink.name, profile.preferredDistance) ||
                            SparkTextNorm.namesEqual(pink.name, profile.preferredStyle) ||
                            SparkTextNorm.namesEqual(pink.name, profile.preferredSurface)
                    )
            }
        val whites = rows.filter { it.kind == SparkRowKind.WHITE }

        fun whiteRelevant(row: SparkRowFact): Boolean =
            when (row.whiteClass) {
                SparkWhiteClass.RACE -> true
                SparkWhiteClass.SKILL -> SparkTextNorm.nameInList(row.name, profile.plannedSkillNames)
                else -> false
            }
        val relevantWhites = whites.filter { whiteRelevant(it) }
        val unknownWhite3 = whites.count { it.stars >= 3 && (it.whiteClass == SparkWhiteClass.UNKNOWN || it.whiteClass == null) }
        return SparkSideBreakdown(
            targetBlueStars = targetBlueStars,
            blueTargetRank = blueRank,
            rawBlueStars = blue?.stars ?: 0,
            matchedPinkStars = matchedPinks.sumOf { it.stars },
            rawPinkStars = pinks.sumOf { it.stars },
            uniqueStars = rows.filter { it.kind == SparkRowKind.UNIQUE }.sumOf { it.stars },
            relevantWhiteStars = relevantWhites.sumOf { it.stars },
            protectedTargetBlue3 = if (blueRank >= 0 && (blue?.stars ?: 0) >= 3) 1 else 0,
            protectedDesiredPink3 = matchedPinks.count { it.stars >= 3 },
            protectedRelevantWhite3 = relevantWhites.count { it.stars >= 3 } + if (protectUnknownWhites) unknownWhite3 else 0,
            unknownWhite3 = unknownWhite3,
            totalStars = rows.sumOf { it.stars },
            rowCount = rows.size,
            unreadableRows = reading.unreadableRowCount,
            complete = reading.complete,
        )
    }

    fun choose(original: SparkSetReading, rerolled: SparkSetReading, profile: SparkChooserProfile): SparkChoice {
        val o = breakdown(original, profile, protectUnknownWhites = true)
        val r = breakdown(rerolled, profile, protectUnknownWhites = false)
        if (!original.complete || !rerolled.complete) {
            val which =
                listOfNotNull(
                    if (!original.complete) "original ${original.termination.name}" else null,
                    if (!rerolled.complete) "rerolled ${rerolled.termination.name}" else null,
                ).joinToString(", ")
            return SparkChoice(
                side = SparkSetSide.ORIGINAL,
                decidedBy = "incomplete_read",
                reason = "Keeping the original: the comparison is not allowed on a partial read ($which).",
                certain = false,
                original = o,
                rerolled = r,
            )
        }

        // R1: three-star protection vectors, highest class first.
        val vecO = listOf(o.protectedTargetBlue3, o.protectedDesiredPink3, o.protectedRelevantWhite3)
        val vecR = listOf(r.protectedTargetBlue3, r.protectedDesiredPink3, r.protectedRelevantWhite3)
        for (i in vecO.indices) {
            if (vecO[i] != vecR[i]) {
                val side = if (vecO[i] > vecR[i]) SparkSetSide.ORIGINAL else SparkSetSide.REROLLED
                val cls = listOf("target blue", "desired pink", "relevant white")[i]
                return decided(side, "three_star_protection", "3-star $cls holdings $vecO vs $vecR", o, r)
            }
        }

        // T1..T5: plain integer tiers.
        val tiers: List<Triple<String, List<Int>, List<Int>>> =
            listOf(
                // Lower rank index is better, so rank enters negated; -1 (non-target) is worst.
                Triple("blue", listOf(o.targetBlueStars, negRank(o.blueTargetRank), o.rawBlueStars), listOf(r.targetBlueStars, negRank(r.blueTargetRank), r.rawBlueStars)),
                Triple("pink", listOf(o.matchedPinkStars, o.rawPinkStars), listOf(r.matchedPinkStars, r.rawPinkStars)),
                Triple("unique", listOf(o.uniqueStars), listOf(r.uniqueStars)),
                Triple("relevant_whites", listOf(o.relevantWhiteStars), listOf(r.relevantWhiteStars)),
                Triple("total", listOf(o.totalStars, o.rowCount), listOf(r.totalStars, r.rowCount)),
            )
        for ((tierName, keyO, keyR) in tiers) {
            for (i in keyO.indices) {
                if (keyO[i] != keyR[i]) {
                    val side = if (keyO[i] > keyR[i]) SparkSetSide.ORIGINAL else SparkSetSide.REROLLED
                    return decided(side, tierName, "$tierName ${keyO.joinToString("/")} vs ${keyR.joinToString("/")}", o, r)
                }
            }
        }
        return decided(SparkSetSide.ORIGINAL, "tie", "every tier equal; a tie keeps the original", o, r)
    }

    private fun negRank(rank: Int): Int = if (rank < 0) Int.MIN_VALUE else -rank

    private fun decided(side: SparkSetSide, decidedBy: String, detail: String, o: SparkSideBreakdown, r: SparkSideBreakdown): SparkChoice =
        SparkChoice(
            side = side,
            decidedBy = decidedBy,
            reason = "Keeping the ${side.wire} set ($decidedBy: $detail).",
            certain = true,
            original = o,
            rerolled = r,
        )
}
