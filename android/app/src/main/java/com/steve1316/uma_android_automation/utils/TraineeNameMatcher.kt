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

    /** Jaro-Winkler similarity (0..1) of two raw names after normalization. */
    fun score(a: String, b: String): Double = service.score(normalize(a), normalize(b))

    /**
     * Best candidate for [target] among [candidates] by normalized Jaro-Winkler.
     *
     * @return the best candidate paired with its score, or null when [candidates] is empty.
     */
    fun bestMatch(target: String, candidates: List<String>): Pair<String, Double>? {
        val targetNorm = normalize(target)
        var best: String? = null
        var bestScore = 0.0
        for (candidate in candidates) {
            val s = service.score(targetNorm, normalize(candidate))
            if (best == null || s > bestScore) {
                best = candidate
                bestScore = s
            }
        }
        return best?.let { it to bestScore }
    }
}
