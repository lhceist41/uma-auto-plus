package com.steve1316.uma_android_automation.utils

/**
 * Canonical trainee and outfit names for the Veteran roster reader, plus the fuzzy matcher that
 * snaps a noisy OCR read onto one of them.
 *
 * The character name and outfit title on the `Umamusume Details` dialog are brown text over a busy
 * building illustration, which generic OCR reads approximately ("Taiki Shuttle" -> "Taikishuttle",
 * "Wild Frontier" -> "Wild Fronttai"). Because the game only ever shows one of a known, finite set,
 * snapping the OCR onto the nearest canonical name recovers the exact string the fingerprint needs.
 *
 * The two lists are a maintained snapshot of the sources of truth - character keys of
 * `src/data/characters.json`, base-card outfit titles of `characterBaseOutfits` in
 * `src/data/presetMeta.ts` - mirrored here because no compiled character/outfit list reaches the
 * Kotlin side yet. Refresh them when those files change (the same snapshot discipline presetMeta.ts
 * already documents for its own outfit titles). A trainee outside these lists reads as unresolved
 * rather than a wrong guess, which fail-closed blocks the fingerprint.
 */
object VeteranIdentityNames {
    /** Character keys of src/data/characters.json. */
    val CHARACTERS: List<String> =
        listOf(
            "Admire Vega",
            "Agnes Digital",
            "Agnes Tachyon",
            "Air Groove",
            "Air Shakur",
            "Bamboo Memory",
            "Biwa Hayahide",
            "Copano Rickey",
            "Curren Chan",
            "Daiwa Scarlet",
            "Eishin Flash",
            "El Condor Pasa",
            "Fine Motion",
            "Fuji Kiseki",
            "Gold City",
            "Gold Ship",
            "Grass Wonder",
            "Haru Urara",
            "Hishi Akebono",
            "Hishi Amazon",
            "Inari One",
            "Ines Fujin",
            "Kawakami Princess",
            "King Halo",
            "Kitasan Black",
            "Manhattan Cafe",
            "Maruzensky",
            "Matikanefukukitaru",
            "Matikanetannhauser",
            "Mayano Top Gun",
            "Meisho Doto",
            "Mejiro Ardan",
            "Mejiro Bright",
            "Mejiro Dober",
            "Mejiro McQueen",
            "Mejiro Palmer",
            "Mejiro Ryan",
            "Mihono Bourbon",
            "Narita Brian",
            "Narita Taishin",
            "Nice Nature",
            "Nishino Flower",
            "Oguri Cap",
            "Rice Shower",
            "Sakura Bakushin O",
            "Sakura Chiyono O",
            "Satono Diamond",
            "Seeking the Pearl",
            "Seiun Sky",
            "Silence Suzuka",
            "Smart Falcon",
            "Special Week",
            "Super Creek",
            "Sweep Tosho",
            "Symboli Rudolf",
            "TM Opera O",
            "Taiki Shuttle",
            "Tamamo Cross",
            "Tokai Teio",
            "Tosen Jordan",
            "Vodka",
            "Winning Ticket",
            "Yaeno Muteki",
            "Yukino Bijin",
        )

    /** Base-card outfit titles from characterBaseOutfits in src/data/presetMeta.ts. Alternate
     * costumes are not covered here; a Veteran in one reads as unresolved rather than mismatched. */
    val OUTFITS: List<String> =
        listOf(
            "Azure Amazon",
            "Bestest Prize ♪",
            "Blossom in Learning",
            "Clippety-Tippety-Clop",
            "Creeping Shadow",
            "Down the Line",
            "Eightfold☆Fortune",
            "El☆Número 1",
            "Empress Road",
            "Fast as Lightning",
            "Gilded Shrine to Glory",
            "Innocent Silence",
            "Jokester ☆ Vibes",
            "King of Emeralds",
            "LOVE☆4EVER",
            "Layered Petals",
            "Line Breakthrough",
            "MB-19890425",
            "Murmuring Stream",
            "Nevertheless",
            "Off the Line",
            "Peak Blue",
            "Peak Joy",
            "Platanus Witch",
            "Poinsettia Ribbon",
            "Red Strife",
            "Reeling in the Big One",
            "Rising☆Fortune",
            "Scramble☆Zone",
            "Special Dreamer",
            "Starlight Beat",
            "Stone-Piercing Blue",
            "Turbulent Blue",
            "Wild Frontier",
            "Wild Top Gear",
            "pf. Winning Equation...",
            "tach-nology",
        )
}

/** Lowercase alphanumeric skeleton of a name, dropping spaces, punctuation, brackets, and the
 * decorative symbols outfit titles carry (star, music note, accents), so OCR that mangles those
 * still matches. "[Wild Frontier]" and "Eightfold☆Fortune" reduce to "wildfrontier"/"eightfoldfortune". */
fun normalizeIdentityText(raw: String): String =
    buildString {
        for (c in raw.lowercase()) {
            if (c in 'a'..'z' || c in '0'..'9') append(c)
        }
    }

/** Levenshtein edit distance between two strings. */
private fun editDistance(a: String, b: String): Int {
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[b.length]
}

/** Similarity in [0, 1] of two normalized strings: 1 - editDistance / longerLength. */
private fun similarity(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    val longer = maxOf(a.length, b.length)
    return 1.0 - editDistance(a, b).toDouble() / longer
}

/** Default minimum normalized similarity for a canonical match to be trusted. */
const val CANONICAL_MIN_SIMILARITY = 0.68

/** Default minimum lead the best candidate must hold over the runner-up (rejects ambiguous reads). */
const val CANONICAL_MIN_MARGIN = 0.08

/** Best [candidates] entry for a normalized needle and the winning + runner-up scores. */
private class CandidateScore(val best: String?, val bestScore: Double, val secondScore: Double)

private fun scoreCandidates(needle: String, candidates: List<String>): CandidateScore {
    var best: String? = null
    var bestScore = -1.0
    var secondScore = -1.0
    for (candidate in candidates) {
        val score = similarity(needle, normalizeIdentityText(candidate))
        if (score > bestScore) {
            secondScore = bestScore
            bestScore = score
            best = candidate
        } else if (score > secondScore) {
            secondScore = score
        }
    }
    return CandidateScore(best, bestScore, secondScore)
}

/**
 * Snaps a noisy OCR string onto the nearest [candidates] entry, or null when nothing is close enough
 * or two candidates are too close to separate. Returns the original candidate string (not the
 * normalized form) so the caller gets the exact canonical value.
 */
fun canonicalMatch(
    rawOcr: String,
    candidates: List<String>,
    minSimilarity: Double = CANONICAL_MIN_SIMILARITY,
    minMargin: Double = CANONICAL_MIN_MARGIN,
): String? {
    val needle = normalizeIdentityText(rawOcr)
    if (needle.isEmpty()) return null
    val scored = scoreCandidates(needle, candidates)
    if (scored.bestScore < minSimilarity) return null
    if (scored.secondScore >= 0.0 && scored.bestScore - scored.secondScore < minMargin) return null
    return scored.best
}

/** A resolved character name and outfit title from the two-line dialog header. */
data class NameOutfitMatch(val outfit: String?, val name: String?)

/**
 * Resolves the character and outfit from the raw name/outfit OCR. Each OCR line is scored against
 * both the character and outfit domains and assigned to the domain it fits best, so the read is
 * robust to the outfit brackets being dropped or the two lines arriving out of order. Falls back to
 * matching the whole blob when the OCR did not split into lines.
 */
fun resolveNameOutfit(
    rawOcr: String,
    characters: List<String> = VeteranIdentityNames.CHARACTERS,
    outfits: List<String> = VeteranIdentityNames.OUTFITS,
): NameOutfitMatch {
    val lines = rawOcr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(rawOcr) }
    var name: String? = null
    var nameScore = -1.0
    var outfit: String? = null
    var outfitScore = -1.0
    for (line in lines) {
        val n = normalizeIdentityText(line)
        if (n.isEmpty()) continue
        val cs = scoreCandidates(n, characters)
        if (cs.bestScore >= CANONICAL_MIN_SIMILARITY && cs.bestScore - cs.secondScore >= CANONICAL_MIN_MARGIN && cs.bestScore > nameScore) {
            nameScore = cs.bestScore
            name = cs.best
        }
        val os = scoreCandidates(n, outfits)
        if (os.bestScore >= CANONICAL_MIN_SIMILARITY && os.bestScore - os.secondScore >= CANONICAL_MIN_MARGIN && os.bestScore > outfitScore) {
            outfitScore = os.bestScore
            outfit = os.best
        }
    }
    return NameOutfitMatch(outfit, name)
}
