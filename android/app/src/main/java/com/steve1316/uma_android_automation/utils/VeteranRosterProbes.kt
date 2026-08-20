package com.steve1316.uma_android_automation.utils

/**
 * Region geometry and pure parsers for the Veteran Roster list status bar and the read-only
 * `Umamusume Details` dialog (Skills tab header + Career Info block).
 *
 * Every coordinate here was measured directly on the live 2026-08-20 PL-R1 capture set (1080x1920,
 * `validation/parentlab-plr1-roster/`, gitignored), not copied from an unrelated screen: the header
 * band (name/outfit/rank/rating/stats/aptitudes) sits above the Skills/Inspiration/Career Info tab
 * strip and stays on screen regardless of which tab is active, while the Career block fields only
 * resolve once the operator has manually opened Career Info and scrolled it into view - a manual
 * precondition [VeteranRosterReader] cannot arrange itself, since PL-R1a is zero-gesture by design.
 *
 * Android-free on purpose: the runtime feeds these parsers raw OCR strings from
 * `CustomImageUtils.performOCROnRegion`, the JUnit fixtures feed literal strings copied from the
 * fixture captures. The one pixel-level read ([classifyFavoriteMarker]) takes a [SparkPixelSampler],
 * the same minimal abstraction the Legacy/Spark probes already use.
 */

// -- Roster list status bar (1080x1920) -----------------------------------------------------------

const val ROSTER_REGISTERED_X = 30
const val ROSTER_REGISTERED_Y = 1460
const val ROSTER_REGISTERED_W = 300
const val ROSTER_REGISTERED_H = 40

const val ROSTER_FILTERS_X = 400
const val ROSTER_FILTERS_Y = 1460
const val ROSTER_FILTERS_W = 210
const val ROSTER_FILTERS_H = 40

const val ROSTER_SORT_X = 610
const val ROSTER_SORT_Y = 1455
const val ROSTER_SORT_W = 270
const val ROSTER_SORT_H = 45

const val ROSTER_ASCDESC_X = 890
const val ROSTER_ASCDESC_Y = 1455
const val ROSTER_ASCDESC_W = 140
const val ROSTER_ASCDESC_H = 45

/** Every value the live Display Settings > Sort By list offers (PL-R1 investigation, 2026-08-20). */
val ROSTER_KNOWN_SORT_KEYS: Set<String> =
    setOf("Rating", "Sparks", "Skills", "Track", "Distance", "Style", "Date Acquired", "Name", "Memo", "Favorites Icons")

// -- Umamusume Details dialog: title + header (visible on every tab) -----------------------------

const val DETAIL_TITLE_X = 0
const val DETAIL_TITLE_Y = 60
const val DETAIL_TITLE_W = 1080
const val DETAIL_TITLE_H = 80

const val DETAIL_NAME_OUTFIT_X = 480
const val DETAIL_NAME_OUTFIT_Y = 175
const val DETAIL_NAME_OUTFIT_W = 500
const val DETAIL_NAME_OUTFIT_H = 90

const val DETAIL_RATING_X = 130
const val DETAIL_RATING_Y = 385
const val DETAIL_RATING_W = 180
const val DETAIL_RATING_H = 60

/** Favorite marker glyph center - a DENY zone for taps (toggles favorite state), read-only here. */
const val DETAIL_FAVORITE_CX = 75
const val DETAIL_FAVORITE_CY = 205

/** Grayscale-vs-colour sample spread below which the glyph is the "Not Set" outline icon. Measured
 * on the live fixture: every sampled pixel of the Not Set glyph reads R=G=B within 2 (pure gray),
 * while the 16 real favorite icons (carrot orange, heart red, etc., per Display Settings > Filter)
 * are saturated. 20 sits well above the observed noise floor and well below real colour. */
const val FAVORITE_SATURATION_THRESHOLD = 20

// -- Stat row: 5 equal-pitch columns, Speed/Stamina/Power/Guts/Wit ---------------------------------

/** Stat labels in header order, for logging. The grade badges and numeric values are read by the
 * pixel classifiers / digit OCR in [VeteranBadgeClassifier], not by generic full-cell OCR. */
val STAT_LABELS: List<String> = listOf("Speed", "Stamina", "Power", "Guts", "Wit")

/** Standard Uma aptitude grade domain - one letter, no plus (unlike stat grades). */
val APTITUDE_GRADES: Set<String> = setOf("S", "A", "B", "C", "D", "E", "F", "G")

// -- Career Info block, once the operator has scrolled it into view (matches the PL-R1 fixture
// `06-details-careerinfo-career-block.png` framing exactly) ---------------------------------------

const val CAREER_RECORD_X = 60
const val CAREER_RECORD_Y = 1040
const val CAREER_RECORD_W = 700
const val CAREER_RECORD_H = 55

const val CAREER_FANS_X = 60
const val CAREER_FANS_Y = 1100
const val CAREER_FANS_W = 700
const val CAREER_FANS_H = 55

const val CAREER_SCENARIO_X = 60
const val CAREER_SCENARIO_Y = 1220
const val CAREER_SCENARIO_W = 900
const val CAREER_SCENARIO_H = 55

const val CAREER_RATING_X = 60
const val CAREER_RATING_Y = 1280
const val CAREER_RATING_W = 500
const val CAREER_RATING_H = 55

const val CAREER_DATE_ACQUIRED_X = 60
const val CAREER_DATE_ACQUIRED_Y = 1340
const val CAREER_DATE_ACQUIRED_W = 700
const val CAREER_DATE_ACQUIRED_H = 55

private val MONTH_ABBREVIATIONS: Map<String, Int> =
    mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

// -- Parsers: every one returns null (not a guess) on anything it cannot read confidently ---------

/** "Registered 257/260" -> (257, 260). Rejects an impossible used>capacity read outright. */
fun parseRegistered(raw: String): Pair<Int, Int>? {
    val m = Regex("""(\d+)\s*/\s*(\d+)""").find(raw) ?: return null
    val used = m.groupValues[1].toIntOrNull() ?: return null
    val capacity = m.groupValues[2].toIntOrNull() ?: return null
    if (capacity <= 0 || used < 0 || used > capacity) return null
    return used to capacity
}

/** Which Veteran screen a captured frame is on. */
enum class RosterScreenKind { ROSTER_LIST, UMAMUSUME_DETAILS, UNKNOWN }

/**
 * Classifies a frame from the two cheap probes: a non-null [registered] read ("Registered X/Y") is
 * the roster LIST; otherwise a title containing "DETAIL" (the "Umamusume Details" dialog header) is
 * the DETAILS dialog; anything else is UNKNOWN.
 *
 * UNKNOWN is deliberately conservative. At session start the bot's own "Automation is now running"
 * foreground-service notification peeks as a heads-up banner over the title band, so a valid Details
 * dialog momentarily reads its title as "Status ... Automation is now running" (no "DETAIL"). The
 * reader retries a few captures on UNKNOWN to outlast that banner rather than trusting the first frame.
 */
fun classifyRosterScreen(registered: Pair<Int, Int>?, titleRaw: String): RosterScreenKind =
    when {
        registered != null -> RosterScreenKind.ROSTER_LIST
        titleRaw.uppercase().contains("DETAIL") -> RosterScreenKind.UMAMUSUME_DETAILS
        else -> RosterScreenKind.UNKNOWN
    }

/** "Filters: OFF" -> true, "Filters: ON" -> false, anything else -> null (never assume OFF). */
fun parseFiltersOff(raw: String): Boolean? {
    val upper = raw.uppercase()
    return when {
        upper.contains("OFF") -> true
        upper.contains("ON") -> false
        else -> null
    }
}

/** Matches the OCR text against the known Sort By vocabulary; anything else is unresolved. */
fun parseSortKey(raw: String): String? {
    val cleaned = raw.replace("\n", " ").trim()
    return ROSTER_KNOWN_SORT_KEYS.firstOrNull { cleaned.contains(it, ignoreCase = true) }
}

/** "Asc" / "Desc" toggle text -> "Asc" / "Desc", else null. */
fun parseSortDirection(raw: String): String? {
    val upper = raw.uppercase()
    return when {
        upper.contains("ASC") -> "Asc"
        upper.contains("DESC") -> "Desc"
        else -> null
    }
}

/** Exact integer Rating, e.g. "10,192" -> 10192. Rejects an implausibly large misread. */
fun parseRating(raw: String): Int? {
    val digits = raw.filter { it.isDigit() }
    val value = digits.toIntOrNull() ?: return null
    return value.takeIf { it in 0..999_999 }
}

/** "Career Record   Races: 18  Wins: 13" -> (18, 13). Rejects wins>races as an impossible read. */
fun parseCareerRecord(raw: String): Pair<Int, Int>? {
    val m = Regex("""Races:?\s*(\d+)\s+Wins:?\s*(\d+)""", RegexOption.IGNORE_CASE).find(raw.replace("\n", " ")) ?: return null
    val races = m.groupValues[1].toIntOrNull() ?: return null
    val wins = m.groupValues[2].toIntOrNull() ?: return null
    return (races to wins).takeIf { wins <= races }
}

/** "Fans Earned   191,730" -> 191730. */
fun parseFansEarned(raw: String): Int? {
    val m = Regex("""Fans Earned\D*(\d[\d,]*)""", RegexOption.IGNORE_CASE).find(raw) ?: return null
    return m.groupValues[1].replace(",", "").toIntOrNull()
}

/** "Career Scenario   The Beginning: URA Finale" -> "The Beginning: URA Finale". The scenario text
 * itself may contain a colon, so this takes everything after the label, not a colon split. */
fun parseCareerScenario(raw: String): String? {
    val m = Regex("""Career Scenario\s+(.+)""", RegexOption.IGNORE_CASE).find(raw.replace("\n", " ")) ?: return null
    return m.groupValues[1].trim().takeIf { it.isNotEmpty() }
}

/** "Rating   10,192" in the Career Info block -> 10192. Separate crop from the header rating pill,
 * kept as its own parser so a mismatch between the two reads is visible rather than assumed equal. */
fun parseCareerRatingValue(raw: String): Int? {
    val m = Regex("""Rating\D*(\d[\d,]*)""", RegexOption.IGNORE_CASE).find(raw) ?: return null
    return m.groupValues[1].replace(",", "").toIntOrNull()
}

/** "Date Acquired   Aug 10, 2026" -> "2026-08-10". Null on an unrecognised month abbreviation rather
 * than guessing a number. */
fun parseDateAcquired(raw: String): String? {
    val m =
        Regex("""Date Acquired\D*([A-Za-z]{3})[a-z]*\s+(\d{1,2}),\s*(\d{4})""", RegexOption.IGNORE_CASE)
            .find(raw.replace("\n", " "))
            ?: return null
    val month = MONTH_ABBREVIATIONS[m.groupValues[1].lowercase()] ?: return null
    val day = m.groupValues[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    val year = m.groupValues[3].toIntOrNull()?.takeIf { it in 2000..2100 } ?: return null
    return "%04d-%02d-%02d".format(year, month, day)
}

// -- Favorite marker: classified by colour saturation, never by icon shape ------------------------

enum class FavoriteMarkerRead { NOT_SET, UNKNOWN }

/** Samples a small grid around the favorite marker glyph and classifies it by saturation, not by
 * recognising which of the 16 favorite icons it might be (see PL-R1 design doc Part 6: icon
 * recognition is avoidable work, and a wrong icon guess is worse than reporting unknown). A pure
 * grayscale glyph (every channel within [FAVORITE_SATURATION_THRESHOLD]) is the "Not Set" outline;
 * anything more saturated is a real favorite icon whose specific identity this does not resolve. */
fun classifyFavoriteMarker(sampler: SparkPixelSampler, cx: Int = DETAIL_FAVORITE_CX, cy: Int = DETAIL_FAVORITE_CY): FavoriteMarkerRead {
    var maxSpread = 0
    for (dx in -15..15 step 5) {
        for (dy in -15..15 step 5) {
            val argb = sampler.argb(cx + dx, cy + dy)
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val spread = maxOf(r, g, b) - minOf(r, g, b)
            if (spread > maxSpread) maxSpread = spread
        }
    }
    return if (maxSpread <= FAVORITE_SATURATION_THRESHOLD) FavoriteMarkerRead.NOT_SET else FavoriteMarkerRead.UNKNOWN
}

// -- Roster fingerprint: content-addressed identity, ported from src/lib/parentLab/identity.ts -----

/**
 * FNV-1a over two 64-bit lanes with different offset bases, concatenated to a 128-bit hex digest.
 * Bit-for-bit the same algorithm as `contentHash128` in `src/lib/parentLab/identity.ts` (same prime,
 * same two offset bases, same UTF-8 byte walk): Kotlin's [ULong] wraps on overflow exactly like the
 * TS side's BigInt masked to 64 bits, so no arbitrary-precision arithmetic is needed here.
 */
private const val FNV_PRIME: ULong = 0x100000001b3uL
private const val OFFSET_A: ULong = 0xcbf29ce484222325uL
private const val OFFSET_B: ULong = 0x84222325cbf29ce4uL

private fun fnv1a64(bytes: ByteArray, offset: ULong): ULong {
    var h = offset
    for (byte in bytes) {
        h = h xor (byte.toUByte().toULong())
        h *= FNV_PRIME
    }
    return h
}

fun contentHash128(s: String): String {
    val bytes = s.toByteArray(Charsets.UTF_8)
    val a = fnv1a64(bytes, OFFSET_A)
    val b = fnv1a64(bytes, OFFSET_B)
    return a.toString(16).padStart(16, '0') + b.toString(16).padStart(16, '0')
}

/** One fully-read roster entry's immutable identity evidence (PL-R1 design doc Part 4): everything
 * that never changes once a Veteran is registered. Favorite/memo/epithet are deliberately excluded -
 * they are user-mutable and must never enter identity. */
data class RosterIdentityEvidence(
    val character: String,
    val outfit: String,
    val rank: String,
    val rating: Int,
    val stats: List<Int>,
    val aptitudes: List<String>,
)

/** Canonical serialization matching the PL-3 `rosterFingerprint` shape verbatim (field order fixed,
 * compact JSON with no inserted whitespace, matching `JSON.stringify`'s default output). */
fun canonicalRosterEvidence(e: RosterIdentityEvidence): String {
    val statsJson = e.stats.joinToString(",")
    val aptitudesJson = e.aptitudes.joinToString(",") { "\"$it\"" }
    return "{\"v\":1,\"character\":\"${e.character}\",\"outfit\":\"${e.outfit}\",\"rank\":\"${e.rank}\"," +
        "\"rating\":${e.rating},\"stats\":[$statsJson],\"aptitudes\":[$aptitudesJson]}"
}

/** The Veteran's content-addressed roster identity: same input always hashes identical, distinct
 * evidence (almost) never collides. See PL-R1 design doc Part 4 for the tiering this feeds. */
fun rosterFingerprint(e: RosterIdentityEvidence): String = contentHash128(canonicalRosterEvidence(e))
