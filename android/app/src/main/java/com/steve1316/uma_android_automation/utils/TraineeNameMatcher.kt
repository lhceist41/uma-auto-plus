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
     * Per-aligned-word Jaro-Winkler match (0..1) where [target] is the wanted name and [read] is the
     * OCR'd Trainee Select banner ("[Outfit] Name").
     *
     * The character name sits at the END of the banner, so target words are aligned to the TRAILING
     * read words: a bare-name target ("Sweep Tosho") aligns to just the name part and ignores the
     * outfit prefix, while an outfit-prefixed target aligns outfit + name and so stays outfit-sensitive.
     *
     * Crucially this is per-word, NOT whole-string. Every target word must match its aligned read word
     * and the WEAKEST word gates the result. Whole-string Jaro-Winkler over-weights a shared prefix, so
     * two distinct trainees that share a leading word ("Gold Ship" vs "Gold City") would otherwise ride
     * that prefix over the threshold. A merely OCR-noisy word still scores high per-word ([TOKEN_FLOOR]);
     * a genuinely different word (Ship vs City) scores far below it and fails the match. Once every word
     * clears the floor, the joined name region is scored whole-string so ordinary fuzziness is tolerated.
     */
    fun score(target: String, read: String): Double {
        val targetTokens = normalize(target).split(" ").filter { it.isNotEmpty() }
        val readTokens = normalize(read).split(" ").filter { it.isNotEmpty() }
        if (targetTokens.isEmpty() || readTokens.isEmpty()) {
            return service.score(normalize(target), normalize(read))
        }
        // The banner can't be the target if it has fewer words than the target name itself.
        if (readTokens.size < targetTokens.size) return 0.0

        val window = readTokens.takeLast(targetTokens.size)
        val perToken = targetTokens.indices.map { service.score(targetTokens[it], window[it]) }
        // A clearly-different aligned word means a different trainee — fail hard so a shared prefix word
        // can't carry the match (Gold Ship vs Gold City). Returning the min keeps bestMatch ranking sane.
        if (perToken.any { it < TOKEN_FLOOR }) return perToken.minOrNull() ?: 0.0
        // Every word is at least OCR-plausible; score the joined name region whole-string.
        return service.score(targetTokens.joinToString(" "), window.joinToString(" "))
    }

    /**
     * Per-word floor below which an aligned word is treated as a DIFFERENT word rather than OCR noise.
     * Sits between "Ship" vs "City" (~0.50, reject) and a single-character OCR slip like "Ship" vs
     * "Shlp" (~0.87, accept).
     */
    private const val TOKEN_FLOOR = 0.75

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
