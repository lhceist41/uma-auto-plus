package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.SPARK_PAGER_GEOMETRY
import com.steve1316.uma_android_automation.utils.gameY

/**
 * Pure, Android-free decision layer for the career-end Spark Selection flow (original vs rerolled after a 30 TP
 * reroll). Pixel probing lives in [com.steve1316.uma_android_automation.utils.SparkScreenProbes]; screen handling in the navigator.
 */

const val SPARK_UNREADABLE_NAME = "unreadable"

/** [wire] is the corpus string and must never change: existing records use stat/aptitude/unique/skill. */
enum class SparkRowKind(val wire: String) {
    STAT("stat"),
    APTITUDE("aptitude"),
    UNIQUE("unique"),
    WHITE("skill"),
}

/**
 * Race sparks are always relevant (about 20% regenerate per distinct G1 won, and a specific 3-star race spark rarely
 * survives a redraw); skill whites only when planned; an unreadable name is uncertainty, not neutral value.
 */
enum class SparkWhiteClass { SKILL, RACE, UNKNOWN }

data class SparkRowFact(
    val name: String,
    val stars: Int,
    val kind: SparkRowKind,
    val whiteClass: SparkWhiteClass? = null,
) {
    val unreadable: Boolean get() = name == SPARK_UNREADABLE_NAME || name.isBlank()
}

/**
 * Only the two COMPLETE values prove the whole set was read; anything else is a partial read that must never authorize
 * a 30 TP spend or an automatic choice. A scrollbar thumb at the bottom is deliberately NOT a termination signal.
 */
enum class SparkScanTermination {
    COMPLETE_END_MARKER,

    COMPLETE_NO_PROGRESS,

    TIMED_OUT_PARTIAL,

    ALIGNMENT_FAILED,

    FAILED,
    ;

    val complete: Boolean get() = this == COMPLETE_END_MARKER || this == COMPLETE_NO_PROGRESS
}

data class SparkSetReading(
    val rows: List<SparkRowFact>,
    val termination: SparkScanTermination,
    val scrollsUsed: Int = 0,
    val sameFrameRetries: Int = 0,
    val sameFrameRecoveries: Int = 0,
) {
    val complete: Boolean get() = termination.complete && rows.isNotEmpty()
    val unreadableRowCount: Int get() = rows.count { it.unreadable }
}

/** [wire] is the corpus phase string; "original" and "kept" predate this file and must not change. */
enum class SparkSetSide(val wire: String) {
    ORIGINAL("original"),
    REROLLED("rerolled"),
}

/**
 * Three variants exist live: the post-reroll confirmation names the side ("Original Sparks" / "Rerolled Sparks"), while
 * the ordinary keep confirmation every no-reroll career ends on carries a plain "Sparks" pill. [PLAIN] exists so that
 * dialog is recognised positively instead of being mistaken for an unreadable side name.
 */
enum class SparkConfirmationPill {
    PLAIN,

    ORIGINAL,

    REROLLED,

    /** No text recovered: the caller must not infer which dialog this is. */
    UNREADABLE,
}

/**
 * Cross-frame merge for a scrolled spark list, aligned by content (swipes are not pixel-exact): kind and stars exactly,
 * names tolerantly. Runs of rows sharing (kind, stars) can make several overlap lengths align at once, so any single
 * guess could silently drop or duplicate a row; an ambiguous merge returns null (ALIGNMENT_FAILED, keeps the original).
 * Over-reporting incompleteness costs a keep; under-reporting it discards a spark the choice depended on.
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

    /**
     * True when [partial] agrees with the leading rows of [known]. Diagnostic only: as an authorization gate it refused
     * for the same transient misread that made the read partial, because it compares through the exact-match [rowsAlign].
     * It feeds [SparkPrefixEvidence.prefixAgreed] so the log states whether the old rule would have blocked.
     */
    fun rowsAreConsistentPrefix(known: List<SparkRowFact>, partial: List<SparkRowFact>): Boolean {
        if (partial.isEmpty() || partial.size > known.size) return false
        return rowsAlign(known.subList(0, partial.size), partial)
    }

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

    /** Diagnostic prefix comparison only; separate from [rowsAlign] so loosening it cannot loosen a safety rule. */
    private fun foldName(name: String): String =
        name.lowercase().replace('0', 'o').replace('1', 'i').replace('l', 'i').filter { it.isLetterOrDigit() }

    /** Observability only: the fallback decision does not depend on it. */
    fun describePrefix(known: List<SparkRowFact>, partial: List<SparkRowFact>): SparkPrefixEvidence {
        val compared = minOf(known.size, partial.size)
        var firstDiffering: Int? = null
        var detail: String? = null
        for (i in 0 until compared) {
            val k = known[i]
            val p = partial[i]
            val nameAgrees = k.unreadable || p.unreadable || foldName(k.name) == foldName(p.name)
            if (k.kind == p.kind && k.stars == p.stars && nameAgrees) continue
            firstDiffering = i
            detail =
                "row ${i + 1}: known \"${k.name}\" ${k.stars}* ${k.kind} vs read \"${p.name}\" ${p.stars}* ${p.kind}"
            break
        }
        return SparkPrefixEvidence(
            knownRowCount = known.size,
            partialRowCount = partial.size,
            comparedRowCount = compared,
            missingRowCount = maxOf(0, known.size - partial.size),
            extraRowCount = maxOf(0, partial.size - known.size),
            // Tolerant: names a row that genuinely disagrees, not one that lost a glyph.
            firstDifferingRow = firstDiffering,
            firstDifference = detail,
            // Strict (the old gate's predicate): says whether that gate would have blocked.
            prefixAgreed = rowsAreConsistentPrefix(known, partial),
        )
    }
}

/** Logged with the fallback decision; never an input to whether the fallback is allowed. */
data class SparkPrefixEvidence(
    val knownRowCount: Int,
    val partialRowCount: Int,
    val comparedRowCount: Int,
    val missingRowCount: Int,
    val extraRowCount: Int,
    val firstDifferingRow: Int?,
    val firstDifference: String?,
    val prefixAgreed: Boolean,
) {
    fun summarize(): String =
        "prefixAgreed=$prefixAgreed known=$knownRowCount read=$partialRowCount compared=$comparedRowCount " +
            "missing=$missingRowCount extra=$extraRowCount" +
            (firstDifference?.let { " firstDiff=[$it]" } ?: "")
}

/**
 * Blue (stat) and pink (aptitude) rows are the only kinds with a name space known up front, which is what makes a
 * repair provable. White and unique names are open vocabulary and are never repaired.
 */
val SPARK_STAT_NAMES: List<String> = listOf("Speed", "Stamina", "Power", "Guts", "Wit")

val SPARK_APTITUDE_NAMES: List<String> =
    listOf("Sprint", "Mile", "Medium", "Long", "Turf", "Dirt", "Front Runner", "Pace Chaser", "Late Surger", "End Closer")

enum class SparkReadAuthority {
    PAGER_UNCHANGED,

    PAGER_WITH_STRUCTURAL_NAME_REPAIR,

    PAGER_ONLY,
}

enum class SparkReconcileRefusal(val wire: String) {
    NONE("none"),
    NO_EARLIER_CAPTURE("no_earlier_capture"),
    EARLIER_INCOMPLETE("earlier_incomplete"),
    PAGER_INCOMPLETE("pager_incomplete"),
    DIFFERENT_TRANSACTION("different_transaction"),
    DIFFERENT_SIDE("different_side"),
    ROW_COUNT_MISMATCH("row_count_mismatch"),
    KIND_MISMATCH("kind_mismatch"),
    STAR_MISMATCH("star_mismatch"),
    VALID_NAME_CONTRADICTION("valid_name_contradiction"),
}

/** Keeps both spellings so the corpus never loses what the pager actually read. */
data class SparkNameRepair(
    val rowIndex: Int,
    val kind: SparkRowKind,
    val pagerName: String,
    val repairedName: String,
)

/**
 * Tagged with the transaction and side it belongs to. Production guarantees this structurally, so the mismatch
 * refusals below are defense in depth.
 */
data class SparkSideCapture(
    val reading: SparkSetReading,
    val side: SparkSetSide,
    val transactionId: String,
)

data class SparkReadAuthorityResult(
    val side: SparkSetSide,
    val earlier: SparkSetReading?,
    val pager: SparkSetReading,
    val effective: SparkSetReading,
    val authority: SparkReadAuthority,
    val repairs: List<SparkNameRepair>,
    val structurallyAgreed: Boolean,
    val refusal: SparkReconcileRefusal,
) {
    val repairedRowIndexes: List<Int> get() = repairs.map { it.rowIndex }

    fun summarize(): String =
        when (authority) {
            SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR ->
                "structural name repair on the ${side.wire} page: ${pager.rows.size} rows, identical kinds and stars; " +
                    repairs.joinToString("; ") { "row ${it.rowIndex + 1} \"${it.pagerName}\" -> \"${it.repairedName}\"" } +
                    "; authority=${authority.name}"
            SparkReadAuthority.PAGER_ONLY ->
                "the ${side.wire} page was scored from the pager alone (${refusal.wire}); authority=${authority.name}"
            SparkReadAuthority.PAGER_UNCHANGED ->
                "the ${side.wire} page reads corroborate and needed no repair; authority=${authority.name}"
        }
}

/**
 * The pager's name OCR is systematically worse than the earlier result-screen capture of the same immutable set: it
 * loses leading glyphs (`Speed` -> `peed`), which demoted a configured blue target to rank -1 under exact-name matching.
 * The pager stays the authority for side identity and navigation. The earlier capture may repair CONTENT NAMES ONLY, on
 * stat and aptitude rows (closed vocabularies), only when both reads match structurally (same transaction, side, row
 * count, order, kind and stars), and only where the earlier name resolves and the pager name does not. Two different
 * valid names are a contradiction, so the whole side falls back to the pager read.
 */
object SparkReadReconcile {
    fun canonicalNameFor(kind: SparkRowKind, name: String): String? =
        when (kind) {
            SparkRowKind.STAT -> SPARK_STAT_NAMES.firstOrNull { SparkTextNorm.namesEqual(it, name) }
            SparkRowKind.APTITUDE -> SPARK_APTITUDE_NAMES.firstOrNull { SparkTextNorm.namesEqual(it, name) }
            else -> null
        }

    fun reconcile(
        side: SparkSetSide,
        transactionId: String,
        earlier: SparkSideCapture?,
        pager: SparkSetReading,
    ): SparkReadAuthorityResult {
        fun pagerOnly(refusal: SparkReconcileRefusal, structurallyAgreed: Boolean = false) =
            SparkReadAuthorityResult(
                side = side,
                earlier = earlier?.reading,
                pager = pager,
                effective = pager,
                authority = SparkReadAuthority.PAGER_ONLY,
                repairs = emptyList(),
                structurallyAgreed = structurallyAgreed,
                refusal = refusal,
            )

        if (earlier == null) return pagerOnly(SparkReconcileRefusal.NO_EARLIER_CAPTURE)
        if (earlier.transactionId != transactionId) return pagerOnly(SparkReconcileRefusal.DIFFERENT_TRANSACTION)
        if (earlier.side != side) return pagerOnly(SparkReconcileRefusal.DIFFERENT_SIDE)
        if (!earlier.reading.complete) return pagerOnly(SparkReconcileRefusal.EARLIER_INCOMPLETE)
        if (!pager.complete) return pagerOnly(SparkReconcileRefusal.PAGER_INCOMPLETE)

        val earlierRows = earlier.reading.rows
        val pagerRows = pager.rows
        if (earlierRows.size != pagerRows.size) return pagerOnly(SparkReconcileRefusal.ROW_COUNT_MISMATCH)
        // Index comparison does not prove order (swapping two rows with equal kind and stars would pass), but that is safe: a
        // set has one stat and one aptitude row, the list reader and scroll merge preserve order, whites are never repaired,
        // and scoring is permutation-invariant.
        for (i in pagerRows.indices) {
            if (earlierRows[i].kind != pagerRows[i].kind) return pagerOnly(SparkReconcileRefusal.KIND_MISMATCH)
            if (earlierRows[i].stars != pagerRows[i].stars) return pagerOnly(SparkReconcileRefusal.STAR_MISMATCH)
        }

        val repairs = mutableListOf<SparkNameRepair>()
        for (i in pagerRows.indices) {
            val kind = pagerRows[i].kind
            if (kind != SparkRowKind.STAT && kind != SparkRowKind.APTITUDE) continue
            if (SparkTextNorm.namesEqual(earlierRows[i].name, pagerRows[i].name)) continue
            val earlierCanonical = canonicalNameFor(kind, earlierRows[i].name)
            val pagerCanonical = canonicalNameFor(kind, pagerRows[i].name)
            if (earlierCanonical != null && pagerCanonical != null && earlierCanonical != pagerCanonical) {
                // Different valid names cannot be the same row of the same set: nothing is repairable and no partial repair leaks out.
                return pagerOnly(SparkReconcileRefusal.VALID_NAME_CONTRADICTION, structurallyAgreed = true)
            }
            if (earlierCanonical != null && pagerCanonical == null) {
                repairs.add(SparkNameRepair(i, kind, pagerRows[i].name, earlierCanonical))
            }
        }

        if (repairs.isEmpty()) {
            return SparkReadAuthorityResult(
                side = side,
                earlier = earlier.reading,
                pager = pager,
                effective = pager,
                authority = SparkReadAuthority.PAGER_UNCHANGED,
                repairs = emptyList(),
                structurallyAgreed = true,
                refusal = SparkReconcileRefusal.NONE,
            )
        }

        // Only names are substituted; count, order, kind, stars, white class and scan counters stay the pager's.
        val repairedRows = pagerRows.toMutableList()
        for (repair in repairs) {
            repairedRows[repair.rowIndex] = repairedRows[repair.rowIndex].copy(name = repair.repairedName)
        }
        return SparkReadAuthorityResult(
            side = side,
            earlier = earlier.reading,
            pager = pager,
            effective = pager.copy(rows = repairedRows),
            authority = SparkReadAuthority.PAGER_WITH_STRUCTURAL_NAME_REPAIR,
            repairs = repairs,
            structurallyAgreed = true,
            refusal = SparkReconcileRefusal.NONE,
        )
    }
}

enum class SparkSelectionAction {
    CHOOSE_ORIGINAL,

    CHOOSE_REROLLED,

    RESCAN_CURRENT_PAGE,

    HALT,
}

data class SparkSelectionInputs(
    val originalTermination: SparkScanTermination,
    val rerolledTermination: SparkScanTermination,
    val currentPageSide: SparkSetSide?,
    val originalPageVerifiedByNavigation: Boolean,
    val originalControlAvailable: Boolean,
    val currentPageRescanAvailable: Boolean,
    val prefix: SparkPrefixEvidence? = null,
) {
    /** Both routes need two signals: ORIGINAL resolved from heading AND dots this pass, or a verified pager swipe to ORIGINAL. */
    val originalIdentityTrusted: Boolean
        get() = currentPageSide == SparkSetSide.ORIGINAL || originalPageVerifiedByNavigation

    val currentPageIncomplete: Boolean
        get() =
            when (currentPageSide) {
                SparkSetSide.ORIGINAL -> !originalTermination.complete
                SparkSetSide.REROLLED -> !rerolledTermination.complete
                null -> false
            }
}

data class SparkSelectionDecision(
    val action: SparkSelectionAction,
    val side: SparkSetSide?,
    val reason: String,
    val certain: Boolean,
    val decidedBy: String,
)

/**
 * Safety invariant: an incomplete EVALUATION is not a reason to stop. Keeping the Original set is safe however little
 * was read (the career already earned it and the 30 TP is spent either way); what is unsafe is committing a page whose
 * IDENTITY is not proven, which commits the rerolled set irreversibly. Content uncertainty degrades the choice; only
 * identity or control uncertainty halts.
 */
object SparkSelectionPolicy {
    fun decide(choice: SparkChoice, inputs: SparkSelectionInputs): SparkSelectionDecision {
        if (choice.certain) {
            return SparkSelectionDecision(
                action = if (choice.side == SparkSetSide.ORIGINAL) SparkSelectionAction.CHOOSE_ORIGINAL else SparkSelectionAction.CHOOSE_REROLLED,
                side = choice.side,
                reason = choice.reason,
                certain = true,
                decidedBy = choice.decidedBy,
            )
        }

        val terminations = "original ${inputs.originalTermination.name}, rerolled ${inputs.rerolledTermination.name}"

        if (inputs.currentPageIncomplete && inputs.currentPageRescanAvailable) {
            return SparkSelectionDecision(
                action = SparkSelectionAction.RESCAN_CURRENT_PAGE,
                side = inputs.currentPageSide,
                reason = "the ${inputs.currentPageSide?.wire} page read short ($terminations); re-reading it once before deciding",
                certain = false,
                decidedBy = "rescan",
            )
        }

        if (!inputs.originalIdentityTrusted) {
            return SparkSelectionDecision(
                action = SparkSelectionAction.HALT,
                side = null,
                reason =
                    "the evaluation is incomplete ($terminations) and the Original page identity is not established " +
                        "(current page ${inputs.currentPageSide?.wire ?: "unresolved"}, navigation-verified=${inputs.originalPageVerifiedByNavigation}); " +
                        "never committing a page the bot cannot name",
                certain = false,
                decidedBy = "identity_unverified",
            )
        }

        if (!inputs.originalControlAvailable) {
            return SparkSelectionDecision(
                action = SparkSelectionAction.HALT,
                side = null,
                reason =
                    "the evaluation is incomplete ($terminations) and the Confirm control could not be located on the " +
                        "verified Original page; never committing on a guessed coordinate",
                certain = false,
                decidedBy = "control_unavailable",
            )
        }

        return SparkSelectionDecision(
            action = SparkSelectionAction.CHOOSE_ORIGINAL,
            side = SparkSetSide.ORIGINAL,
            reason =
                "Original chosen without reroll comparison: the evaluation is degraded ($terminations) but the Original " +
                    "page is identity-verified, so the set the career earned is kept" +
                    (inputs.prefix?.let { " [${it.summarize()}]" } ?: ""),
            certain = false,
            decidedBy = "incomplete_read",
        )
    }
}

/** Substring matching after folding usual OCR damage (casing, 0-for-o, 1/l-for-i): ML Kit keeps word cores but mangles glyphs. */
object SparkTextNorm {
    private fun fold(text: String): String = text.lowercase().replace('0', 'o').replace('1', 'i').replace('l', 'i')

    /** "rero" and "rigina" survive the fold distinctly ("reroiied"/"originai"), so the two sides cannot be confused. */
    fun headingSide(text: String?): SparkSetSide? {
        if (text == null) return null
        val folded = fold(text)
        return when {
            folded.contains("rero") -> SparkSetSide.REROLLED
            folded.contains("rigina") -> SparkSetSide.ORIGINAL
            else -> null
        }
    }

    fun isSparksRerolledTitle(text: String?): Boolean = text != null && fold(text).contains("rero")

    fun isSparkSelectionTitle(text: String?): Boolean {
        if (text == null) return false
        val folded = fold(text)
        return folded.contains("seiect") || folded.contains("select")
    }

    /**
     * A side name wins over the plain form (side variants also contain "Sparks"); text with neither is UNREADABLE, so a
     * mangled read is never mistaken for the ordinary dialog.
     */
    fun confirmationPill(text: String?): SparkConfirmationPill {
        if (text.isNullOrBlank()) return SparkConfirmationPill.UNREADABLE
        return when (headingSide(text)) {
            SparkSetSide.REROLLED -> SparkConfirmationPill.REROLLED
            SparkSetSide.ORIGINAL -> SparkConfirmationPill.ORIGINAL
            null -> if (fold(text).contains("spark")) SparkConfirmationPill.PLAIN else SparkConfirmationPill.UNREADABLE
        }
    }

    /** Pink aptitude sparks use the full style names ("Front", "Pace Chaser"). */
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

    fun namesEqual(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val fa = fold(a).filter { it.isLetterOrDigit() }
        val fb = fold(b).filter { it.isLetterOrDigit() }
        return fa.isNotEmpty() && fa == fb
    }

    fun nameInList(name: String?, list: Collection<String>): Boolean = list.any { namesEqual(name, it) }

    /**
     * Same-row comparison for two reads of the same list. Tolerant one step past [namesEqual]: an unreadable side matches
     * anything, and one folded form containing the other matches when the shorter is long enough to be distinctive
     * (absorbs "Unity CupP" vs "Unity Cup").
     */
    fun namesCompatible(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        if (a == SPARK_UNREADABLE_NAME || b == SPARK_UNREADABLE_NAME) return true
        return foldedNamesCompatible(a, b) || foldedNamesCompatible(a.replace('|', 'i'), b.replace('|', 'i'))
    }

    // OCR reads a Roman numeral "I" as "|" but also adds "|" as a border artifact, so both readings are tried.
    private fun foldedNamesCompatible(a: String, b: String): Boolean {
        val fa = fold(a).filter { it.isLetterOrDigit() }
        val fb = fold(b).filter { it.isLetterOrDigit() }
        if (fa.isEmpty() || fb.isEmpty()) return true
        if (fa == fb) return true
        val shorter = if (fa.length <= fb.length) fa else fb
        val longer = if (fa.length <= fb.length) fb else fa
        return shorter.length >= 5 && longer.contains(shorter)
    }
}

data class SparkStarEvidence(val stars: Int, val ambiguousSlots: Int)

sealed class SparkKeepVerdict {
    object Confirm : SparkKeepVerdict()

    /** Only star counts on ambiguous rows differ after every retry: confirm, logging the star check as corroborative. [rows] are the 1-based rows confirmed on semantics. */
    data class ConfirmCorroborative(val rows: List<Int>) : SparkKeepVerdict()

    object Retry : SparkKeepVerdict()

    data class Block(val reason: String) : SparkKeepVerdict()
}

/**
 * Verdict for the ordinary keep confirmation only: the plain-"Sparks" dialog cannot switch sides and the original was
 * read completely, so names, kinds, order and count are primary evidence and stars corroborate. A semantic mismatch
 * blocks outright. Star mismatches are retried on fresh frames (a single frame can misread 3* as 2*) and block only if
 * they reproduce with unambiguous slot evidence; with ambiguous slots they confirm corroboratively. The side-selected
 * Original-vs-Rerolled confirmation does NOT use this rule: there a star misread can select the wrong side.
 *
 * Two name reads that fold apart still agree when [resolveName] snaps both onto the same catalog name for the row's kind
 * (a "☆" read as "*" on one frame and "t" on the next); a read the catalog cannot place, or two different catalog names,
 * still block.
 */
fun keepDialogVerdict(
    original: List<SparkRowFact>,
    dialog: List<SparkRowFact>,
    evidence: List<SparkStarEvidence>?,
    retriesUsed: Int,
    maxRetries: Int,
    resolveName: (String, SparkRowKind) -> String? = { _, _ -> null },
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
        if (!SparkTextNorm.namesCompatible(original[i].name, dialog[i].name) && !sameCatalogName(original[i], dialog[i], resolveName)) {
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

private fun sameCatalogName(a: SparkRowFact, b: SparkRowFact, resolveName: (String, SparkRowKind) -> String?): Boolean {
    val canonical = resolveName(a.name, a.kind) ?: return false
    return canonical == resolveName(b.name, b.kind)
}

sealed class SparkPagerResolution {
    data class Resolved(val side: SparkSetSide) : SparkPagerResolution()

    object Contradictory : SparkPagerResolution()

    object Unreadable : SparkPagerResolution()
}

/**
 * The heading names the CONTENT, the lit page dot the POSITION (page 1 = Rerolled, page 2 = Original). Both must agree:
 * a disagreement blocks instead of confirming a set the bot cannot prove it is looking at.
 */
fun resolvePagerSide(headingSide: SparkSetSide?, activeDotIndex: Int?): SparkPagerResolution {
    val dotSide = sparkPagerDotSide(activeDotIndex)
    return when {
        headingSide == null || dotSide == null -> SparkPagerResolution.Unreadable
        headingSide == dotSide -> SparkPagerResolution.Resolved(headingSide)
        else -> SparkPagerResolution.Contradictory
    }
}

fun sparkPagerDotSide(activeDotIndex: Int?): SparkSetSide? =
    when (activeDotIndex) {
        1 -> SparkSetSide.REROLLED
        2 -> SparkSetSide.ORIGINAL
        else -> null
    }

enum class SparkPagerAction { NONE, SWIPE_LEFT, SWIPE_RIGHT }

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
 * The pager is paged with a horizontal central drag, never an edge chevron tap: the thin chevron outline is a poor
 * dispatch target (two taps at its measured pixels never moved the page) and the floating overlay bubble rides the same
 * screen edge and can swallow edge taps. Rerolled -> Original drags leftward. A wrong direction cannot mis-confirm: the
 * pager has no page 0/3 and the repaint check reads UNCHANGED.
 */
object SparkPagerNav {
    /** Measured layout the coordinates are anchored to (see SparkScreenProbes); the lanes move with the list's band on taller 1080-wide screens and scale linearly elsewhere. */
    const val REFERENCE_WIDTH = 1080
    const val REFERENCE_HEIGHT = 1920

    // Swipe endpoints must clear both full-height edge strips (the overlay bubble snaps to either edge and can be dragged
    // along it; the list scrollbar rides the right edge), the bottom band (the Confirm at 540,1769 irreversibly commits the
    // page) and the top band (chevrons y=228, heading, page dots y=272).
    const val EDGE_OVERLAY_WIDTH = 100
    const val SCROLLBAR_MIN_X = 1015
    const val CONFIRM_ZONE_MIN_Y = 1650
    const val HEADER_ZONE_MAX_Y = 300

    /** A vertical component would scroll the list instead of paging; a delta at or above this is a planning bug. */
    const val LIST_SCROLL_DY_LIMIT = 24

    // Safe lanes across the row region. Lane 1 mirrors the carousel's proven 80% -> 20% drag; the retry uses a different
    // band and a longer, slower drag in case the first release point was inert.
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
        val y = gameY(laneY.toDouble(), SPARK_PAGER_GEOMETRY.band, screenWidth, screenHeight).toFloat()
        return SparkPagerSwipePlan(action, startX * sx, y, endX * sx, y, duration, target)
    }
}

enum class SparkPagerRepaint {
    VERIFIED,

    /** Both signals still name the starting page: the gesture did not take and the screen is in a known state, so one more attempt is safe. */
    UNCHANGED,

    HEADING_UNREADABLE,

    DOTS_UNREADABLE,

    CONTRADICTION,
}

/**
 * Success is deliberately narrow: heading OCR and lit dot must BOTH name the target page. A settle timer or a single
 * signal is never proof; anything unreadable or contradictory blocks instead of swiping again toward a blind Confirm.
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

/** Why a 30 TP redraw was not priced, in precedence order. Only the first genuine blocker is named, so a log never claims a stage failed that was never reached. */
enum class SparkSpendBlocker(val wire: String) {
    NONE("none"),

    TRANSACTION_MISSING("transaction_missing"),

    STATS_SNAPSHOT_MISSING("stats_snapshot_missing"),

    ORIGINAL_READ_SKIPPED("original_read_skipped"),

    ORIGINAL_READ_INCOMPLETE("original_read_incomplete"),

    LAYOUT_UNEXPECTED("layout_unexpected"),
}

/** [blocker] names the FIRST genuine problem; every field states its own fact independently. */
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

    fun format(): String {
        val scan = scanTermination?.name ?: "not attempted"
        val stats = statsSnapshotSize?.toString() ?: "missing"
        return "blocker=${blocker.wire} transaction=${if (transactionPresent) "present" else "missing"} " +
            "stats_snapshot=$stats original_read=${if (scanTermination == null) "skipped" else if (readComplete) "complete" else "incomplete"} " +
            "scan=$scan rows=$rowCount layout=${if (leadsCorrectly) "ok" else "unexpected"}"
    }
}

data class SparkChooserProfile(
    val traineeIdentity: String?,
    val objective: String?,
    val blueTargetsOrdered: List<String>,
    val preferredDistance: String?,
    val preferredStyle: String?,
    val preferredSurface: String?,
    val plannedSkillNames: List<String>,
)

data class SparkSideBreakdown(
    val targetBlueStars: Int,
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
    /** Key names are part of the corpus schema. */
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

data class SparkChoice(
    val side: SparkSetSide,
    val decidedBy: String,
    val reason: String,
    val certain: Boolean,
    val original: SparkSideBreakdown,
    val rerolled: SparkSideBreakdown,
)

/**
 * Compares two ACTUAL sets after the redraw ([SparkRerollPolicy] prices the spend before it). Lexicographic, no weights:
 *  R1 three-star protection: per-class 3-star holdings [target blue, desired pink, relevant white] compared in order; a
 *     side is never discarded while it uniquely holds a 3-star of a class the other cannot match. An unreadable-name
 *     3-star white protects the ORIGINAL side only: uncertainty protects a holding, it never earns redraw credit.
 *  T1 blue (target-stat stars, earlier target rank, raw stars), T2 pink (profile-matched, raw), T3 unique stars,
 *  T4 summed stars of relevant whites (irrelevant ones are excluded so they never outweigh a better blue),
 *  T5 total stars, then row count. Tie: ORIGINAL (keeping it is free; the rerolled set must be earned).
 *
 * Every race spark counts as relevant, not gated on the career's objective. It sits in T4 and the lowest R1 slot, so it
 * never outranks a target blue or matching pink, but can hold a set together when neither side has one. Known
 * limitation: a spark for a G1 the account will never breed toward is protected like a targeted one; over-protecting
 * is the safe error.
 *
 * An incomplete or empty read on either side short-circuits to keep-original with [SparkChoice.certain] = false: the
 * caller may act on that only when it can verify the Original page and the final confirmation header.
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
