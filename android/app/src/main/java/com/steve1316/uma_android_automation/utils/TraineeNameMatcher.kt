package com.steve1316.uma_android_automation.utils

import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl
import java.text.Normalizer

/**
 * Fuzzy matching for in-game trainee names read off the Trainee Select preview banner.
 *
 * The banner reads as "[Outfit] Name" (e.g. "[Kukulkan Warrior] El Condor Pasa"). The outfit
 * prefix is load-bearing — the same character can own several outfits, and rotation targets the
 * exact one — so [normalize] keeps the outfit words and only strips the bracket / star / accent /
 * punctuation noise OCR renders inconsistently. Matching is Jaro-Winkler over the normalized text.
 */
object TraineeNameMatcher {
    private val service = StringSimilarityServiceImpl(JaroWinklerStrategy())

    /** Lowercase, de-accent, drop bracket/star/punctuation noise, collapse whitespace. */
    fun normalize(raw: String): String {
        val deaccented =
            Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "") // strip combining accent marks (Número -> Numero)
        return deaccented
            .lowercase()
            .replace(Regex("[\\[\\](){}【】]"), " ") // bracket variants around the outfit
            .replace(Regex("[^a-z0-9 ]"), " ") // stars, colons, and other symbol noise
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Jaro-Winkler similarity (0..1) where [target] is the wanted name and [read] is the OCR'd
     * Trainee Select banner ("[Outfit] Name").
     *
     * When [target] specifies an outfit (it has a leading bracket), the match is outfit-sensitive:
     * the full normalized strings are compared so the wrong outfit of the same character stays below
     * the threshold. When [target] is a bare character name (no outfit — the common case, since most
     * presets are named without one), the name sits at the END of the banner while Jaro-Winkler
     * weights the START, so the outfit prefix would tank an otherwise perfect match. There we also
     * score [target] against the trailing words of [read] (the name part) and take the better, so a
     * plain "Sweep Tosho" matches "[Platanus Witch] Sweep Tosho".
     */
    fun score(target: String, read: String): Double {
        val targetNorm = normalize(target)
        val readNorm = normalize(read)
        val full = service.score(targetNorm, readNorm)
        if (hasOutfitPrefix(target)) return full
        val readWords = readNorm.split(" ").filter { it.isNotEmpty() }
        val targetWords = targetNorm.split(" ").filter { it.isNotEmpty() }.size.coerceAtLeast(1)
        if (readWords.size <= targetWords) return full
        val tail = readWords.takeLast(targetWords).joinToString(" ")
        return maxOf(full, service.score(targetNorm, tail))
    }

    /** True when [raw] carries a leading "[Outfit]" / "(Outfit)" prefix (vs a bare character name). */
    private fun hasOutfitPrefix(raw: String): Boolean = Regex("^\\s*[\\[(【]").containsMatchIn(raw)

    /**
     * Best candidate for [target] among [candidates] by [score] (same outfit-aware logic).
     *
     * @return the best candidate paired with its score, or null when [candidates] is empty.
     */
    fun bestMatch(target: String, candidates: List<String>): Pair<String, Double>? {
        var best: String? = null
        var bestScore = 0.0
        for (candidate in candidates) {
            val s = score(target, candidate)
            if (best == null || s > bestScore) {
                best = candidate
                bestScore = s
            }
        }
        return best?.let { it to bestScore }
    }
}
