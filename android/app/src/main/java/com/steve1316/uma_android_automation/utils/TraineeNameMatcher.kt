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

    /** Lowercase, de-accent, drop bracket/star/punctuation noise, fold OCR-identical i/l/I, collapse whitespace. */
    fun normalize(raw: String): String {
        val deaccented =
            Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "") // strip combining accent marks (Número -> Numero)
        return deaccented
            .lowercase()
            .replace(Regex("\\b([a-z])\\.(?=[a-z]\\b)"), "$1") // "T.M. Opera O" and "TM Opera O" read alike
            .replace(Regex("[\\[\\](){}【】]"), " ") // bracket variants around the outfit
            .replace(Regex("[^a-z0-9 ]"), " ") // stars, colons, and other symbol noise
            // Capital-I, lowercase-l and lowercase-i are the same glyph in the game font, so OCR swaps
            // them freely ("El Condor Pasa" reads as "EI Condor Pasa"). On a 2-char token like "el" that
            // single-glyph slip drops Jaro-Winkler below TOKEN_FLOOR and fails the whole match. Fold the
            // three together so the comparison is blind to the confusion (applied to both sides equally).
            .replace(Regex("[il1]"), "l") // the digit 1 too
            .replace('0', 'o') // the lone "O" of "Sakura Bakushin O" reads as "0"
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
     * An outfit target's title is scored apart, without spaces: a lost glyph or glued bracket merges its words.
     */
    fun score(target: String, read: String): Double {
        val title = outfitTitleOf(target)
        val targetTokens = tokens(if (title != null) target.substringAfter("]") else target)
        val readTokens = tokens(read)
        if (targetTokens.isEmpty() || readTokens.isEmpty()) {
            return service.score(normalize(target), normalize(read))
        }
        // The banner can't be the target if it has fewer words than the target name itself.
        if (readTokens.size < targetTokens.size) return 0.0

        // Best window whose every word passes sameWord (keeps "Gold Ship" off "Gold City"), or 0.0.
        var best = 0.0
        for (end in nameEnds(readTokens, targetTokens.size)) {
            val window = readTokens.subList(end - targetTokens.size, end)
            if (targetTokens.indices.any { !sameWord(targetTokens[it], window[it]) }) continue
            var whole = service.score(targetTokens.joinToString(" "), window.joinToString(" "))
            if (title != null) whole = minOf(whole, titleScore(title, readTokens.subList(0, end - targetTokens.size)))
            if (whole > best) best = whole
        }
        return best
    }

    private fun tokens(raw: String) = normalize(raw).split(" ").filter { it.isNotEmpty() }

    private fun outfitTitleOf(target: String): String? {
        val t = target.trim()
        return if (t.startsWith("[") && t.contains("]")) t.substring(1, t.indexOf("]")) else null
    }

    /** Jaro-Winkler alone passes a one-letter change ("Ryan" vs Ardan read as "rdan"), so edits are capped too. */
    private fun sameWord(target: String, read: String): Boolean =
        service.score(target, read) >= TOKEN_FLOOR &&
            (edits(target, read, anywhere = false) <= target.length / 5 || (read.length >= 2 && target.length - read.length <= 3 && target.startsWith(read)))

    /** Edit distance too: this Jaro-Winkler drops to 0.85 when a glued junk letter ("J [", "]K") shifts the title. */
    private fun titleScore(title: String, before: List<String>): Double {
        val t = normalize(title).replace(" ", "")
        val read = before.joinToString("")
        if (t.isEmpty() || read.isEmpty()) return 0.0
        var best = 0.0
        for (h in 0..1) {
            for (k in 0..1) {
                if (h + k >= read.length) continue
                val c = read.substring(h, read.length - k)
                best = maxOf(best, service.score(t, c), 1.0 - edits(t, c, anywhere = false).toDouble() / maxOf(t.length, c.length))
            }
        }
        return best
    }

    private fun edits(a: String, b: String, anywhere: Boolean): Int {
        var prev = IntArray(b.length + 1) { if (anywhere) 0 else it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return if (anywhere) prev.min() else prev[b.length]
    }

    /**
     * How close [read] comes to [target] regardless of whether it matches: the best whole-string
     * Jaro-Winkler over the same windows, with no per-word gate.
     *
     * Purely diagnostic. [score] answers "is this her", and answers 0.0 when it is not; this answers
     * "what was the nearest thing on screen", so a failure can name the closest cell instead of
     * telling the operator to go check ownership while the trainee is visible.
     */
    fun similarity(target: String, read: String): Double {
        val targetTokens = normalize(target).split(" ").filter { it.isNotEmpty() }
        val readTokens = normalize(read).split(" ").filter { it.isNotEmpty() }
        if (targetTokens.isEmpty() || readTokens.isEmpty()) return service.score(normalize(target), normalize(read))
        if (readTokens.size < targetTokens.size) return service.score(normalize(target), normalize(read))
        var best = 0.0
        for (window in windows(readTokens, targetTokens.size)) {
            val whole = service.score(targetTokens.joinToString(" "), window.joinToString(" "))
            if (whole > best) best = whole
        }
        return best
    }

    /**
     * Every contiguous [size]-token window of [tokens].
     *
     * The old code took only the LAST window, on the assumption that the character name ends the
     * banner. OCR breaks that assumption routinely: a trailing badge digit on "[Azure Amazon] Hishi
     * Amazon 1" pushed the real name out of the window, aligned "amazon" against "1", and scored the
     * correct trainee 0.000 while an unrelated 18-character name scored 0.546 on coincidental letter
     * overlap (live 2026-07-28, halted the queue). [score] now slides only past badge tokens ([nameEnds]).
     */
    private fun windows(tokens: List<String>, size: Int): List<List<String>> =
        if (size > tokens.size) emptyList() else (0..tokens.size - size).map { tokens.subList(it, it + size) }

    /** Sliding into title words let "Narita Brian" match "[Natural Brlliance] Satono Diamond". */
    private fun nameEnds(tokens: List<String>, size: Int): List<Int> {
        val ends = mutableListOf<Int>()
        var end = tokens.size
        while (end >= size) {
            ends += end
            val last = tokens[end - 1]
            if (last.length > 2 && last.any { !it.isDigit() }) break
            end--
        }
        return ends
    }

    /**
     * Per-word floor below which an aligned word is treated as a DIFFERENT word rather than OCR noise.
     * Sits between "Ship" vs "City" (~0.50, reject) and a single-character OCR slip like "Ship" vs
     * "Shlp" (~0.87, accept).
     */
    private const val TOKEN_FLOOR = 0.75

    /** Shorter titles need an exact hit: one edit finds them in random banner letters. */
    private const val MIN_FUZZY_TITLE = 5

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

    /**
     * True when [banner] (a "[Outfit] Name" preview) carries the given [outfit] words.
     *
     * Used to skip a sibling-outfit banner: a bare base-name rotation target ("El Condor Pasa") is
     * outfit-insensitive and would otherwise match an owned outfit's banner ("[Kukulkan Warrior] El
     * Condor Pasa"). Spaces ignored and one edit allowed, so a slipped title ("J [CODE: ICINGJ") still counts.
     */
    fun hasOutfit(banner: String, outfit: String): Boolean {
        val t = normalize(outfit).replace(" ", "")
        if (t.isEmpty()) return false
        val s = normalize(banner).replace(" ", "")
        return s.contains(t) || t.length >= MIN_FUZZY_TITLE && edits(t, s, anywhere = true) <= 1
    }

    /** Returns the list's own title, never the OCR text, so it is safe to show players. The name check keeps another character sharing an outfit title from counting. */
    fun excludedOutfitOf(target: String, banner: String, excludeOutfits: List<String>, threshold: Double): String? =
        excludeOutfits.firstOrNull { hasOutfit(banner, it) }?.takeIf { score(target, banner) >= threshold }

    /** Drops a bracket opened at the banner's end and cut off by the crop. */
    fun bannerForLog(banner: String): String = banner.trim().replace(Regex("""\s*[(\[]$"""), "")

    fun isTargetBanner(target: String, banner: String, excludeOutfits: List<String>, threshold: Double): Boolean =
        banner.isNotBlank() && excludeOutfits.none { hasOutfit(banner, it) } && score(target, banner) >= threshold
}
