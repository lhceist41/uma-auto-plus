package com.steve1316.uma_android_automation.utils

/**
 * Canonical trainee names for the Veteran roster reader, plus the fuzzy matcher that snaps a noisy
 * OCR read onto one of them and onto the resolved trainee's own outfit titles.
 *
 * The character name and outfit title on the `Umamusume Details` dialog are brown text over a busy
 * building illustration, which generic OCR reads approximately ("Taiki Shuttle" -> "Taikishuttle",
 * "Wild Frontier" -> "Wild Fronttai"). Because the game only ever shows one of a known, finite set,
 * snapping the OCR onto the nearest canonical name recovers the exact string the fingerprint needs.
 *
 * Outfit titles do NOT live here. They come from [VeteranIdentityCatalog], generated from
 * `src/data/character_outfits.json`, and are always scored inside the resolved character's own
 * costumes - see [resolveNameOutfit]. The character list below is the maintained mirror of the keys
 * of `src/data/characters.json`, kept in code so a trainee name still resolves when the asset does
 * not load; `VeteranIdentityCatalogTest` pins it against the generated asset so the two cannot
 * drift. A trainee outside the list reads as unresolved rather than a wrong guess, which fail-closed
 * blocks the fingerprint.
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
}

/** Lowercase ASCII-alphanumeric skeleton of a name, dropping spaces, punctuation, brackets, and the
 * decorative symbols outfit titles carry (star, music note, heart), so OCR that mangles those still
 * matches. "[Wild Frontier]" and "Eightfold☆Fortune" reduce to "wildfrontier"/"eightfoldfortune".
 *
 * Accented letters are decomposed and folded to their base letter rather than dropped: OCR renders
 * "El☆Número 1" as "El Numero 1" and "Nuit Étoilée de Scarlet" as "Nuit Etoilee de Scarlet",
 * so folding both sides to the same skeleton makes those an exact match instead of an edit away. */
fun normalizeIdentityText(raw: String): String =
    buildString {
        val decomposed = java.text.Normalizer.normalize(raw.lowercase(), java.text.Normalizer.Form.NFD)
        for (c in decomposed) {
            if (c in 'a'..'z' || c in '0'..'9') append(c)
        }
    }

/** Levenshtein edit distance between two strings. Shared with the factor canonicalizer. */
internal fun editDistance(a: String, b: String): Int {
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

/** Similarity in [0, 1] of two normalized strings: 1 - editDistance / longerLength. Shared with the
 * factor canonicalizer so both matchers score identically. */
internal fun similarity(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    val longer = maxOf(a.length, b.length)
    return 1.0 - editDistance(a, b).toDouble() / longer
}

/** Default minimum normalized similarity for a canonical match to be trusted. */
const val CANONICAL_MIN_SIMILARITY = 0.68

/** Default minimum lead the best candidate must hold over the runner-up (rejects ambiguous reads). */
const val CANONICAL_MIN_MARGIN = 0.08

/**
 * Relaxed floor for the character-conditioned outfit margin fallback (PL-R1b). A costume that clears
 * this but not [CANONICAL_MIN_SIMILARITY] is accepted only when it is the unique best inside the
 * already-resolved trainee's own costumes AND leads the runner-up by [OUTFIT_RELAXED_MIN_MARGIN].
 *
 * Calibrated on the live 257-scan residuals: the lowest true costume read is 0.625 (Maruzensky
 * "Fomua ®J" -> Formula R) and 0.647 (Grass Wonder "(Saintly ade detd" -> Saintly Jade Cleric), each
 * beating its nearest sibling costume by >= 0.48; the highest a WRONG same-character costume ever
 * scored across all 15 diagnosed entries is 0.25. 0.58 sits above that wrong-costume ceiling and below
 * every observed true read, so it rescues the clipped-but-decisive read without lowering the absolute
 * floor for the common path. A read below it stays unresolved (garbage/empty/unseen fail closed). */
const val OUTFIT_RELAXED_MIN_SIMILARITY = 0.58

/** Minimum lead the best costume must hold over the runner-up on the relaxed path - deliberately far
 * stricter than [CANONICAL_MIN_MARGIN], because a lower-confidence absolute read demands a larger
 * separation to be trusted. Every live residual clears it by >= 0.48; a same-character pair too close
 * to separate (margin < this) stays unresolved rather than guessing which costume it was. */
const val OUTFIT_RELAXED_MIN_MARGIN = 0.15

/** How the outfit was accepted (or not), kept as evidence so a margin accept is auditable offline and
 * distinguishable from a strong one. Never identity - the resolved costume is [NameOutfitMatch.outfit]. */
enum class OutfitAcceptancePath { STRONG, MARGIN, REJECT }

/**
 * The acceptance decision for the best costume inside a resolved trainee, factored out so the
 * STRONG/MARGIN/REJECT boundary is unit-testable directly. [second] is the runner-up costume's score,
 * or null when the trainee has a single candidate (no runner-up to lead, so the relaxed absolute floor
 * decides alone). STRONG reproduces the pre-PL-R1b rule exactly; MARGIN only adds accepts below the
 * absolute floor when the lead over the runner-up is wide.
 */
fun outfitAcceptancePath(best: Double, second: Double?): OutfitAcceptancePath {
    val margin = if (second != null) best - second else Double.POSITIVE_INFINITY
    return when {
        best >= CANONICAL_MIN_SIMILARITY && margin >= CANONICAL_MIN_MARGIN -> OutfitAcceptancePath.STRONG
        best >= OUTFIT_RELAXED_MIN_SIMILARITY && margin >= OUTFIT_RELAXED_MIN_MARGIN -> OutfitAcceptancePath.MARGIN
        else -> OutfitAcceptancePath.REJECT
    }
}

/** Best [candidates] entry for a normalized needle and the winning + runner-up scores. */
private class CandidateScore(val best: String?, val bestScore: Double, val second: String?, val secondScore: Double)

private fun scoreCandidates(needle: String, candidates: List<String>): CandidateScore {
    var best: String? = null
    var bestScore = -1.0
    var second: String? = null
    var secondScore = -1.0
    for (candidate in candidates) {
        val score = similarity(needle, normalizeIdentityText(candidate))
        if (score > bestScore) {
            second = best
            secondScore = bestScore
            bestScore = score
            best = candidate
        } else if (score > secondScore) {
            second = candidate
            secondScore = score
        }
    }
    return CandidateScore(best, bestScore, second, secondScore)
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

/**
 * A resolved character name and outfit title from the two-line dialog header, plus the scoring
 * evidence behind the outfit decision.
 *
 * The score fields are diagnostics, never identity: they exist so an unresolved outfit can be
 * explained offline (was the read close but ambiguous, or nowhere near anything?) without a second
 * blind scan. Nothing downstream may promote a candidate into [outfit] on the strength of them.
 */
data class NameOutfitMatch(
    val outfit: String?,
    val name: String?,
    val nameScore: Double? = null,
    /** Best-scoring outfit candidate within the resolved character, accepted or not. */
    val outfitCandidate: String? = null,
    val outfitScore: Double? = null,
    val outfitSecondCandidate: String? = null,
    val outfitSecondScore: Double? = null,
    /** Which path decided the outfit: STRONG (absolute floor), MARGIN (relaxed floor + clear lead), or
     * REJECT (unresolved). Diagnostic only; [outfit] is null on REJECT. */
    val outfitAcceptancePath: OutfitAcceptancePath = OutfitAcceptancePath.REJECT,
)

/**
 * Resolves the character and then, within that character only, the outfit.
 *
 * The order matters. Every OCR line is first scored against the character domain, so the read stays
 * robust to the outfit brackets being dropped or the two lines arriving out of order. Only once a
 * character has won confidently are the lines scored against [catalog]'s costumes FOR THAT
 * CHARACTER - which is what makes it structurally impossible to label a Veteran with another
 * trainee's costume, and what removes the cross-trainee near-collisions a flat outfit list has to
 * survive ("Down the Line" vs "Off the Line" score 0.73 against each other).
 *
 * Everything stays fail-closed and nothing is inferred:
 *  - no confident character -> the outfit is left unresolved too, rather than guessed from a global
 *    list that could name a costume this trainee cannot wear;
 *  - no catalog (the asset failed to load) -> the outfit is unresolved;
 *  - a character with no known costume, an off-domain read, or two costumes too close to separate ->
 *    unresolved.
 *
 * The line that won the character match is excluded from the outfit pass when there is more than one
 * line, so a trainee name can never be scored as a costume.
 */
fun resolveNameOutfit(
    rawOcr: String,
    catalog: VeteranIdentityCatalog?,
    characters: List<String> = catalog?.characters ?: VeteranIdentityNames.CHARACTERS,
): NameOutfitMatch {
    val lines = rawOcr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(rawOcr) }

    var name: String? = null
    var nameScore = -1.0
    var nameLine = -1
    for ((index, line) in lines.withIndex()) {
        val needle = normalizeIdentityText(line)
        if (needle.isEmpty()) continue
        val scored = scoreCandidates(needle, characters)
        if (scored.bestScore < CANONICAL_MIN_SIMILARITY) continue
        if (scored.secondScore >= 0.0 && scored.bestScore - scored.secondScore < CANONICAL_MIN_MARGIN) continue
        if (scored.bestScore > nameScore) {
            nameScore = scored.bestScore
            name = scored.best
            nameLine = index
        }
    }
    if (name == null) return NameOutfitMatch(outfit = null, name = null)

    val candidates = catalog?.outfitsFor(name).orEmpty()
    if (candidates.isEmpty()) return NameOutfitMatch(outfit = null, name = name, nameScore = nameScore)

    var bestOutfit: CandidateScore? = null
    for ((index, line) in lines.withIndex()) {
        if (lines.size > 1 && index == nameLine) continue
        val needle = normalizeIdentityText(line)
        if (needle.isEmpty()) continue
        val scored = scoreCandidates(needle, candidates)
        if (bestOutfit == null || scored.bestScore > bestOutfit.bestScore) bestOutfit = scored
    }
    if (bestOutfit == null) return NameOutfitMatch(outfit = null, name = name, nameScore = nameScore)

    // Two acceptance paths inside the resolved trainee's own costumes. STRONG is byte-identical to the
    // pre-PL-R1b behavior, so every costume that already resolved still resolves the same way. MARGIN
    // only ADDS accepts below the absolute floor, and only when the best costume is the unique winner
    // by a wide lead - the clipped-but-decisive read (Formula R at 0.625 over a 0.14 runner-up) that
    // failed closed before.
    val path = outfitAcceptancePath(bestOutfit.bestScore, if (bestOutfit.secondScore >= 0.0) bestOutfit.secondScore else null)
    return NameOutfitMatch(
        outfit = if (path == OutfitAcceptancePath.REJECT) null else bestOutfit.best,
        name = name,
        nameScore = nameScore,
        outfitCandidate = bestOutfit.best,
        outfitScore = bestOutfit.bestScore,
        outfitSecondCandidate = bestOutfit.second,
        outfitSecondScore = if (bestOutfit.secondScore >= 0.0) bestOutfit.secondScore else null,
        outfitAcceptancePath = path,
    )
}
