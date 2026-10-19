package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.borrowRowMatchesPreference
import org.json.JSONObject

/**
 * The borrow the launch tapped into the Friends slot, as a card identity only. The picker row's OCR band also carries
 * the owning trainer's name, which must never reach the corpus, so the raw row text is parsed here and dropped.
 */
internal data class LaunchBorrow(
    /** The Smart Borrow entry (pinned or curated) the tapped row matched, as the list spells it. */
    val entry: String?,
    val character: String?,
    val outfit: String?,
    val limitBreak: Int?,
    /** smart, default, preferred_template or build_aware. */
    val source: String,
) {
    /** "[Outfit] Character" when the row was read, else the matched entry. */
    val display: String?
        get() = character?.let { c -> outfit?.let { "[$it] $c" } ?: c } ?: entry
}

/**
 * What the launch read of the deck a career starts on, carried into that career's `[CAREER_END]` record. Only values the
 * launch already reads are recorded; a field it did not read is null with its reason, never a guess. In memory on purpose,
 * like the launch-transaction id: a career claims the observation only by matching id, so a resumed, restarted or
 * hand-started career records "not observed" rather than the previous launch's deck.
 */
internal data class LaunchDeckObservation(
    val launchTransactionId: String,
    val slot: Int?,
    val slotReason: String?,
    /** Speed, Stamina, Power, Guts, Wit card counts off the deck badges. */
    val typeCounts: List<Int>?,
    val typeCountsReason: String?,
    val borrow: LaunchBorrow?,
    val borrowReason: String?,
) {
    companion object {
        /** The last launch this process drove to its Start Career tap. */
        @Volatile
        var latest: LaunchDeckObservation? = null

        fun forCareer(launchTransactionId: String?): LaunchDeckObservation? =
            latest?.takeIf { launchTransactionId != null && it.launchTransactionId == launchTransactionId }
    }
}

internal const val LAUNCH_DECK_NOT_OBSERVED = "launch not observed by this process (resumed, restarted or hand-started career)"
internal const val LAUNCH_DECK_CARDS_NOT_READ = "not read: the launch reads no per-card identity"

private val BORROW_BRACKET = Regex("[\\[(]([^\\])]*)[\\])]")

/**
 * (character, outfit) from a picker row's OCR text. The band's top line can be the owning trainer's name, so the outfit
 * line is the last line that opens with a bracket (an owner's own brackets sit inside the name), never the first of
 * three lines, and the character is taken only from a line below it or from that line's own remainder; a row with no
 * such line gives (null, null).
 */
internal fun borrowRowIdentity(rowText: String): Pair<String?, String?> {
    val lines = rowText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val outfitIndex = lines.indexOfLast { it.startsWith('[') || it.startsWith('(') }
    if (outfitIndex < 0 || (lines.size >= 3 && outfitIndex == 0)) return null to null
    val outfitLine = lines[outfitIndex]
    val match = BORROW_BRACKET.find(outfitLine)
    val outfit = match?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    val character =
        when {
            outfitIndex < lines.lastIndex -> lines.last().takeIf { !it.contains('[') && !it.contains('(') }
            match != null -> outfitLine.substring(match.range.last + 1).trim().ifEmpty { null }
            else -> null
        }
    return character to outfit
}

/**
 * The borrow record for a row tap at the production tap boundary. [identity] is the row's OCR text for the smart and
 * default picks, the "Character [Outfit] lb=N" line for the build-aware launch, and a placeholder for the preferred
 * template row (no name is read there). [curated] is the pinned name followed by the Smart Borrow list.
 */
internal fun launchBorrowFromTap(identity: String, action: String, curated: List<String>): LaunchBorrow =
    when (action) {
        "borrow_preferred_row" -> LaunchBorrow(entry = null, character = null, outfit = null, limitBreak = null, source = "preferred_template")
        "a3r2_launch_borrow_select" -> {
            val limitBreak = Regex("\\blb=(\\d+)").find(identity)?.groupValues?.get(1)?.toIntOrNull()
            val outfit = BORROW_BRACKET.find(identity)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
            val character = identity.substringBefore('[').trim().takeIf { it.isNotEmpty() && it != "-" }
            LaunchBorrow(entry = null, character = character, outfit = outfit, limitBreak = limitBreak, source = "build_aware")
        }
        else -> {
            val (character, outfit) = borrowRowIdentity(identity)
            LaunchBorrow(
                entry = curated.firstOrNull { borrowRowMatchesPreference(identity, it) },
                character = character,
                outfit = outfit,
                limitBreak = null,
                source = if (action == "borrow_smart_row") "smart" else "default",
            )
        }
    }

private val TYPE_COUNT_KEYS = listOf("spd", "sta", "pwr", "grt", "wit")

/** The `deck` object of a career record; every unread field is null beside its reason. */
internal fun launchDeckJson(observation: LaunchDeckObservation?): JSONObject {
    val json = JSONObject()
    json.put("observed", observation != null)
    if (observation == null) {
        json.put("reason", LAUNCH_DECK_NOT_OBSERVED)
        json.put("slot", JSONObject.NULL)
        json.put("types", JSONObject.NULL)
        json.put("borrow", JSONObject.NULL)
        json.put("cards", JSONObject.NULL)
        return json
    }
    json.put("slot", observation.slot ?: JSONObject.NULL)
    observation.slotReason?.let { json.put("slotReason", it) }
    val counts = observation.typeCounts
    if (counts != null && counts.size == TYPE_COUNT_KEYS.size) {
        json.put("types", JSONObject().apply { TYPE_COUNT_KEYS.forEachIndexed { i, key -> put(key, counts[i]) } })
    } else {
        json.put("types", JSONObject.NULL)
        json.put("typesReason", observation.typeCountsReason ?: "read failed")
    }
    val borrow = observation.borrow
    if (borrow != null) {
        json.put(
            "borrow",
            JSONObject().apply {
                put("entry", borrow.entry ?: JSONObject.NULL)
                put("character", borrow.character ?: JSONObject.NULL)
                put("outfit", borrow.outfit ?: JSONObject.NULL)
                put("limitBreak", borrow.limitBreak ?: JSONObject.NULL)
                put("source", borrow.source)
            },
        )
    } else {
        json.put("borrow", JSONObject.NULL)
        json.put("borrowReason", observation.borrowReason ?: "not tapped")
    }
    json.put("cards", JSONObject.NULL)
    json.put("cardsReason", LAUNCH_DECK_CARDS_NOT_READ)
    return json
}

/** ` deck=N` plus ` borrow="..."` when known, for the `[CAREER_END]` line; `deck=unknown` when nothing was observed. */
internal fun launchDeckLedgerTokens(observation: LaunchDeckObservation?): String =
    buildString {
        append(" deck=").append(observation?.slot?.toString() ?: "unknown")
        observation?.borrow?.display?.let { append(" borrow=\"").append(it.replace('"', '\'')).append('"') }
    }

/** One `[DECK]` line at the Start Career tap, so a live launch shows what its career will record. */
internal fun launchDeckLogLine(observation: LaunchDeckObservation): String =
    buildString {
        append("[DECK] Launch deck record: slot=").append(observation.slot ?: "unknown (${observation.slotReason})")
        append(" types=")
        val counts = observation.typeCounts
        if (counts != null) {
            append(TYPE_COUNT_KEYS.indices.joinToString(",") { "${TYPE_COUNT_KEYS[it]}=${counts.getOrNull(it) ?: "?"}" })
        } else {
            append("unknown (${observation.typeCountsReason})")
        }
        append(" borrow=")
        val borrow = observation.borrow
        if (borrow != null) append('"').append(borrow.display ?: "unread").append("\" source=").append(borrow.source) else append("none (${observation.borrowReason})")
        append(" launchTx=").append(observation.launchTransactionId)
    }
