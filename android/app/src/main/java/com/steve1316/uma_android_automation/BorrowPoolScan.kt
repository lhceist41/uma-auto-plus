package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.GlyphBox
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure, Android-free model + decoders + serialization for the read-only Borrow Card pool census
 * (DeckLab Phase 2B). The on-device orchestration lives in [CareerLaunchNavigator.scanBorrowPoolReadOnly];
 * everything here takes already-sampled pixels or already-OCR'd strings, so it runs unchanged in a
 * JVM unit test through [SparkPixelSampler] and carries no OpenCV, OCR, or gesture dependency.
 *
 * The division of labour is deliberate and matches the offline resolver's contract:
 *   - Kotlin observes the picker faithfully and fails each field CLOSED (to UNKNOWN) the moment it
 *     is unsure, because guessing a limit break or a card identity silently would be a lie;
 *   - the TypeScript `resolveBorrowPool` does the canonical support-card resolution and DeckLab
 *     semantics from these observations.
 *
 * Owner identity is treated as private evidence. A row carries the raw owner name only in the local,
 * gitignored JSONL (for offline diagnosis); the snapshot fed to resolution carries a stable
 * [ownerAlias] derived from it, never the name itself.
 */

/** Schema tag + version for the emitted records, kept in step with the JSONL consumers. */
const val BORROW_POOL_SCAN_SCHEMA = "borrow_pool_scan"
const val BORROW_POOL_SCAN_SCHEMA_VERSION = 1

/**
 * Why a pool scan stopped. These map onto the TypeScript `BorrowTermination` completeness semantics:
 * only [UI_END_REACHED] and [VISIBLE_WINDOW_COMPLETE] can ever assert that an absent card is really
 * not borrowable; every other code means "this is what was seen, not all there is".
 */
enum class BorrowPoolTermination {
    /** The bounded visible window was read fully and the list stopped producing new rows. */
    VISIBLE_WINDOW_COMPLETE,

    /** The list stopped moving under the page gesture: the real bottom of the borrowable pool. */
    UI_END_REACHED,

    /** The page-gesture budget ran out with the list still moving: a real partial. */
    SCROLL_LIMIT_REACHED,

    /** The requested entry limit was hit before the list ended: a bounded validation partial. */
    ENTRY_LIMIT_REACHED,

    /** The picker was never open (first screen carried no row), or a later frame was not the picker. */
    UNEXPECTED_SCREEN,

    /** The bot was stopped mid-scan. */
    ABORTED,

    /** A precondition failed before any gesture: not on Support Formation, or the slot was not empty. */
    PRECONDITION_FAILED,
}

/** The TypeScript `BorrowTermination` string this Kotlin termination maps to. */
fun BorrowPoolTermination.toSnapshotTermination(): String =
    when (this) {
        BorrowPoolTermination.VISIBLE_WINDOW_COMPLETE -> "COMPLETE_VISIBLE_POOL"
        BorrowPoolTermination.UI_END_REACHED -> "UI_END_REACHED"
        BorrowPoolTermination.SCROLL_LIMIT_REACHED -> "SCROLL_LIMIT_REACHED"
        BorrowPoolTermination.ENTRY_LIMIT_REACHED -> "BOUNDED_PARTIAL"
        BorrowPoolTermination.UNEXPECTED_SCREEN -> "UNEXPECTED_SCREEN"
        BorrowPoolTermination.ABORTED -> "BOUNDED_PARTIAL"
        BorrowPoolTermination.PRECONDITION_FAILED -> "BOUNDED_PARTIAL"
    }

/**
 * Maps a completed [BorrowWalkEnd] to a pool termination. [entryLimitHit] distinguishes a walk that
 * stopped because the caller's entry limit was reached (a bounded partial) from one that reached a
 * real list end. A walk that never opened the picker is [BorrowWalkEnd.EMPTY_PICKER].
 */
internal fun borrowWalkEndToTermination(end: BorrowWalkEnd, entryLimitHit: Boolean): BorrowPoolTermination =
    when {
        entryLimitHit -> BorrowPoolTermination.ENTRY_LIMIT_REACHED
        end == BorrowWalkEnd.END_OF_LIST -> BorrowPoolTermination.UI_END_REACHED
        end == BorrowWalkEnd.MAX_PAGES -> BorrowPoolTermination.SCROLL_LIMIT_REACHED
        end == BorrowWalkEnd.EMPTY_PICKER -> BorrowPoolTermination.UNEXPECTED_SCREEN
        end == BorrowWalkEnd.ABORTED -> BorrowPoolTermination.ABORTED
        end == BorrowWalkEnd.PICKED -> BorrowPoolTermination.VISIBLE_WINDOW_COMPLETE
        else -> BorrowPoolTermination.SCROLL_LIMIT_REACHED
    }

/** Where a borrow row can be borrowed from, as the provenance pill distinguishes it. */
enum class BorrowSourceType { FRIEND, FOLLOW, GUEST, UNKNOWN }

/** The game's own tag on a row that cannot be borrowed into the current deck this launch. */
enum class BorrowBlockedTag { DUPLICATE, TRAINEE }

/**
 * One observed row of the Borrow Card picker: the faithful UI observation, before any catalogue
 * resolution. Every decoded field is nullable and null means "not read", never a default guess;
 * [unresolvedFields] names each one so the offline side can see exactly what was missed.
 */
data class BorrowRowObservation(
    val pageIndex: Int,
    /** Character name as split out of the two-line name band. */
    val character: String?,
    /** Outfit / title from the name band's bracket. */
    val outfit: String?,
    val rarity: String?,
    /** Advisory support-type from the icon hue; null for the ambiguous (red) types. Never used for resolution. */
    val supportType: String?,
    val level: Int?,
    /** Filled limit-break pips, 0..4, or null when the pip band could not be read. */
    val limitBreakIndex: Int?,
    val sourceType: BorrowSourceType,
    /** A stable local alias for the owner (never the raw name), for de-duplication and source counts. */
    val ownerAlias: String?,
    /** The raw owner name: LOCAL evidence only, dropped before the snapshot. */
    val ownerNameRaw: String?,
    val blockedTag: BorrowBlockedTag?,
    /** Stable identity of this row across scrolls, so an overlapping page does not double-count it. */
    val rowFingerprint: String,
    val confidence: String,
    val unresolvedFields: List<String>,
    /** Free-form raw reads for the log and offline audit. Carries no owner name. */
    val evidence: String,
)

/** The summary of one pool scan, written once after all the rows. */
data class BorrowPoolScanHeader(
    val scanId: String,
    val startedAt: Long,
    val completedAt: Long,
    val appVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val screensInspected: Int,
    val pageGestures: Int,
    val rowsObserved: Int,
    val distinctRows: Int,
    val entryLimit: Int,
    val termination: BorrowPoolTermination,
    /** True only when a real list end was reached with no unreadable rows: the pool is complete. */
    val trustedAsCompletePool: Boolean,
    val evidenceCropCount: Int,
)

// -- Decoders (pure) ------------------------------------------------------------------------------

/** Lowercase alphanumerics only, for stable identity comparisons that ignore glyph noise. */
fun borrowPoolNormalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/**
 * Splits the two-line name band into (character, outfit). The band renders the outfit in brackets on
 * one line and the character on the other; the bracket line is the outfit whichever line it is on.
 * Either half may be null when the band did not carry it.
 */
fun splitBorrowName(raw: String): Pair<String?, String?> {
    val lines = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return null to null
    val bracket = Regex("[\\[(]([^\\])]*)[\\])]")
    var outfit: String? = null
    val nonBracket = mutableListOf<String>()
    for (line in lines) {
        val m = bracket.find(line)
        if (m != null && outfit == null) {
            outfit = m.groupValues[1].trim().ifEmpty { null }
            val remainder = line.replace(m.value, "").trim()
            if (remainder.isNotEmpty()) nonBracket.add(remainder)
        } else {
            nonBracket.add(line)
        }
    }
    val character = nonBracket.joinToString(" ").trim().ifEmpty { null }
    return character to outfit
}

/** Parses the "Lvl NN" level text into a level, or null when no plausible level is present. */
fun parseBorrowLevel(raw: String): Int? {
    val digits = Regex("(\\d{1,2})").find(raw.replace(" ", ""))?.groupValues?.get(1) ?: return null
    val value = digits.toIntOrNull() ?: return null
    return if (value in 1..50) value else null
}

/** Normalizes the rarity badge OCR to one of R / SR / SSR, or null when it is not one of those. */
fun parseBorrowRarity(raw: String): String? {
    val t = raw.uppercase().filter { it.isLetter() }
    return when {
        t.contains("SSR") -> "SSR"
        t.contains("SR") -> "SR"
        t == "R" -> "R"
        else -> null
    }
}

/** Maps the provenance pill text to a source type. "Following" is a one-way follow; a mutual/friend
 * pill is a friend; an explicit guest pill is a guest. Anything unreadable is UNKNOWN, never guessed. */
fun parseBorrowSourceType(raw: String): BorrowSourceType {
    val t = borrowPoolNormalize(raw)
    return when {
        t.contains("guest") -> BorrowSourceType.GUEST
        // "Mutual" wins over a bare "follow": a mutual follow is a friend, and "Mutual Follow" carries
        // both words. Only a one-way "Following" (no mutual) is a FOLLOW.
        t.contains("mutual") || t.contains("friend") -> BorrowSourceType.FRIEND
        t.contains("following") || t.contains("follow") -> BorrowSourceType.FOLLOW
        else -> BorrowSourceType.UNKNOWN
    }
}

/**
 * A stable, non-reversible alias for an owner, so source counts and de-duplication work without ever
 * carrying the raw name into the snapshot. Blank / unreadable owners collapse to null (no owner
 * distinguished) rather than to a shared bogus alias.
 */
fun redactOwnerAlias(ownerNameRaw: String?): String? {
    val key = ownerNameRaw?.let { borrowPoolNormalize(it) } ?: return null
    if (key.length < 2) return null
    return "owner-%08x".format(key.hashCode() and 0x7fffffff)
}

private fun isLimitBreakCyan(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    // The filled pip is a saturated cyan diamond: high green and blue, low red, green close to blue.
    return b > 120 && g > 120 && r < 150 && (b - r) > 40 && kotlin.math.abs(g - b) < 80
}

/** How many cyan pixels sit in [box], sampling every other pixel. Exposed so a calibration run can
 * log the population next to the verdict. */
fun countLimitBreakCyan(sampler: SparkPixelSampler, box: GlyphBox): Int {
    var count = 0
    var y = box.y0
    while (y < box.y1) {
        var x = box.x0
        while (x < box.x1) {
            if (isLimitBreakCyan(sampler.argb(x, y))) count++
            x += 2
        }
        y += 2
    }
    return count
}

/** Minimum cyan population across the whole pip band before a limit break is read at all. Below it
 * the band is treated as UNREAD (null) rather than as a genuine LB0, because a mis-placed box that
 * lands on background would otherwise mint a confident zero. Recalibrated in Stage A live proof. */
const val BORROW_LB_CYAN_FLOOR = 8

/** Fraction of a pip cell's sampled pixels that must be cyan for the pip to count as filled. */
private const val BORROW_LB_CELL_FILL_FRACTION = 0.10

/** The number of pip slots the picker draws for a support card (MLB fills all four). */
const val BORROW_LB_PIP_SLOTS = 4

/**
 * Reads the filled limit-break pip count (0..4) from the pip band, or null when the band carried too
 * little cyan to trust any reading. The band is split into [BORROW_LB_PIP_SLOTS] equal cells and a
 * cell counts as filled when its cyan fraction clears [BORROW_LB_CELL_FILL_FRACTION]; filled cells
 * are counted from the left and a gap ends the count, so a stray cyan speck on the right cannot
 * inflate a low limit break.
 */
fun readLimitBreakPips(sampler: SparkPixelSampler, box: GlyphBox): Int? {
    if (countLimitBreakCyan(sampler, box) < BORROW_LB_CYAN_FLOOR) return null
    val width = box.x1 - box.x0
    if (width < BORROW_LB_PIP_SLOTS) return null
    var filledPrefix = 0
    var brokenByGap = false
    for (slot in 0 until BORROW_LB_PIP_SLOTS) {
        val cellX0 = box.x0 + width * slot / BORROW_LB_PIP_SLOTS
        val cellX1 = box.x0 + width * (slot + 1) / BORROW_LB_PIP_SLOTS
        var sampled = 0
        var cyan = 0
        var y = box.y0
        while (y < box.y1) {
            var x = cellX0
            while (x < cellX1) {
                sampled++
                if (isLimitBreakCyan(sampler.argb(x, y))) cyan++
                x += 2
            }
            y += 2
        }
        val filled = sampled > 0 && cyan.toDouble() / sampled >= BORROW_LB_CELL_FILL_FRACTION
        if (filled && !brokenByGap) {
            filledPrefix++
        } else if (!filled) {
            brokenByGap = true
        }
    }
    return filledPrefix
}

private fun hue(argb: Int): Double {
    val r = ((argb shr 16) and 0xFF) / 255.0
    val g = ((argb shr 8) and 0xFF) / 255.0
    val b = (argb and 0xFF) / 255.0
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d < 1e-6) return -1.0
    val h =
        when (max) {
            r -> 60.0 * (((g - b) / d) % 6.0)
            g -> 60.0 * (((b - r) / d) + 2.0)
            else -> 60.0 * (((r - g) / d) + 4.0)
        }
    return if (h < 0) h + 360.0 else h
}

/**
 * Advisory support-type from the icon hue. Only the unambiguous families are returned: Speed (blue),
 * Power (brown/orange), and Wit (green). Stamina and Guts both render red and cannot be separated by
 * hue, so they - and everything else - come back null. This field is never used for catalogue
 * resolution (the catalogue already knows a card's type); it is advisory evidence only, which is why
 * an ambiguous read is dropped rather than guessed.
 */
fun classifyBorrowSupportType(sampler: SparkPixelSampler, box: GlyphBox): String? {
    val buckets = HashMap<String, Int>()
    var y = box.y0
    while (y < box.y1) {
        var x = box.x0
        while (x < box.x1) {
            val h = hue(sampler.argb(x, y))
            val label =
                when {
                    h < 0 -> null
                    h in 190.0..250.0 -> "Speed"
                    h in 20.0..45.0 -> "Power"
                    h in 90.0..160.0 -> "Wit"
                    else -> null
                }
            if (label != null) buckets[label] = (buckets[label] ?: 0) + 1
            x += 2
        }
        y += 2
    }
    val best = buckets.maxByOrNull { it.value } ?: return null
    return if (best.value >= 20) best.key else null
}

/**
 * Stable identity of a row across scrolls and reopens. Built only from the card's readable identity
 * and the owner alias, so two genuinely identical observations collapse while the same card offered
 * by two different owners stays two rows (the offline side turns those into one candidate with two
 * sources). Level is excluded deliberately: a friend training a card drifts its level between reads.
 */
fun borrowPoolRowFingerprint(
    character: String?,
    outfit: String?,
    rarity: String?,
    limitBreakIndex: Int?,
    ownerAlias: String?,
): String =
    listOf(
        borrowPoolNormalize(character ?: ""),
        borrowPoolNormalize(outfit ?: ""),
        rarity ?: "",
        limitBreakIndex?.toString() ?: "",
        ownerAlias ?: "",
    ).joinToString("|")

// -- Geometry (recon estimates, recalibrated in Stage A) ------------------------------------------

/**
 * Sub-field boxes of a borrow row, positioned relative to the row's Last Login pill center on
 * 1080-wide captures. These are the previous session's live-recon estimates and MUST be recalibrated
 * against the real screen in Stage A before the decoded fields are trusted; the read-only debug mode
 * dumps the raw reads and crops for exactly that.
 */
object BorrowRowGeometry {
    fun rarityBadge(pillY: Int): GlyphBox = GlyphBox(50, pillY - 170, 130, pillY - 135)

    fun typeIcon(pillY: Int): GlyphBox = GlyphBox(155, pillY - 170, 205, pillY - 135)

    fun limitBreakPips(pillY: Int): GlyphBox = GlyphBox(55, pillY - 30, 150, pillY - 5)

    fun level(pillY: Int): GlyphBox = GlyphBox(150, pillY - 30, 210, pillY - 5)

    fun ownerName(pillY: Int): GlyphBox = GlyphBox(230, pillY - 165, 760, pillY - 125)

    fun provenancePill(pillY: Int): GlyphBox = GlyphBox(770, pillY - 170, 1000, pillY - 130)

    /** The two-line card-name band. The launch's Smart Borrow reader anchors this at pillY-87 +-70;
     * the recon put the name lower, at pillY-90..pillY+5. Stage A decides which the live screen uses. */
    fun cardNameBand(pillY: Int): GlyphBox = GlyphBox(230, pillY - 90, 760, pillY + 5)
}

// -- Serialization (org.json) ---------------------------------------------------------------------

/**
 * One row as a JSONL record. Uses the snake_case keys the TypeScript `parseBorrowPoolSnapshot`
 * already accepts, so the offline bridge is a thin assemble-and-hand-off with no field remapping.
 * The raw owner name is kept under a separate `owner_name_raw` key that the bridge drops; the
 * snapshot the resolver sees carries `owner_alias` only.
 */
fun serializeBorrowRow(scanId: String, obs: BorrowRowObservation): JSONObject =
    JSONObject().apply {
        put("record", "borrow_pool_row")
        put("scan_id", scanId)
        put("page_index", obs.pageIndex)
        put("character", obs.character ?: JSONObject.NULL)
        put("title", obs.outfit ?: JSONObject.NULL)
        put("rarity", obs.rarity ?: JSONObject.NULL)
        put("support_type", obs.supportType ?: JSONObject.NULL)
        put("level", obs.level ?: JSONObject.NULL)
        put("limit_break_index", obs.limitBreakIndex ?: JSONObject.NULL)
        put("source_type", obs.sourceType.name)
        put("owner_alias", obs.ownerAlias ?: JSONObject.NULL)
        put("owner_name_raw", obs.ownerNameRaw ?: JSONObject.NULL)
        put("blocked_tag", obs.blockedTag?.name ?: JSONObject.NULL)
        put("entry_fingerprint", obs.rowFingerprint)
        put("confidence", obs.confidence)
        put("unresolved_fields", JSONArray(obs.unresolvedFields))
        put("evidence", obs.evidence)
    }

/** The scan header as a JSONL record, written after the rows so a truncated write leaves rows with no
 * header (read offline as a partial) rather than a header promising rows that are not there. */
fun serializeBorrowScanHeader(header: BorrowPoolScanHeader): JSONObject =
    JSONObject().apply {
        put("record", "borrow_pool_scan")
        put("schema", BORROW_POOL_SCAN_SCHEMA)
        put("schema_version", BORROW_POOL_SCAN_SCHEMA_VERSION)
        put("scan_id", header.scanId)
        put("started_at", header.startedAt)
        put("completed_at", header.completedAt)
        put("app_version", header.appVersion)
        put("screen_width", header.screenWidth)
        put("screen_height", header.screenHeight)
        put("screens_inspected", header.screensInspected)
        put("page_gestures", header.pageGestures)
        put("rows_observed", header.rowsObserved)
        put("distinct_rows", header.distinctRows)
        put("entry_limit", header.entryLimit)
        put("termination", header.termination.name)
        put("snapshot_termination", header.termination.toSnapshotTermination())
        put("trusted_as_complete_pool", header.trustedAsCompletePool)
        put("evidence_crop_count", header.evidenceCropCount)
    }
