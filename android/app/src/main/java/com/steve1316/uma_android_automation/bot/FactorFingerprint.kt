package com.steve1316.uma_android_automation.bot

/**
 * Factor-identity tokens shared by the Inspiration and lineage readers. Canonical tokens come from a resolved
 * name (never raw OCR) and are null when unresolved; structural tokens (`kind:stars`) are name-free and always stable.
 */

/** Equal for an Inspiration and a lineage factor with the same canonical name (shared normalization). */
fun canonicalFactorToken(kind: SparkRowKind, canonicalName: String?, stars: Int): String? =
    canonicalName?.let { "${kind.name.lowercase()}:${normalizeLineageFactorName(it)}:$stars" }

fun structuralFactorToken(kind: SparkRowKind, stars: Int): String = "${kind.name.lowercase()}:$stars"

/**
 * Null when the set is empty or any factor is unresolved: a partly-unknown set must not present a trusted identity.
 * Callers fall back to [structuralFactorSetFingerprint]. Order-independent.
 */
fun canonicalFactorSetFingerprint(tokens: List<String?>): String? {
    if (tokens.isEmpty()) return null
    val out = ArrayList<String>(tokens.size)
    for (t in tokens) out.add(t ?: return null)
    return out.sorted().joinToString("|")
}

fun structuralFactorSetFingerprint(tokens: List<String>): String = tokens.sorted().joinToString("|")
