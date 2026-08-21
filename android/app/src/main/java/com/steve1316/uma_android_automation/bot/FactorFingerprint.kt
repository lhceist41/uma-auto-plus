package com.steve1316.uma_android_automation.bot

/**
 * The deterministic factor-identity tokens shared by the Veteran Inspiration reader and the PL-4
 * lineage reader, so a factor read from either screen hashes to the same value.
 *
 * There are two levels of identity, and they exist for different reasons:
 *  - a CANONICAL token (`kind:CANONNAME:stars`) is the semantic identity. It is built from a resolved
 *    canonical name (see [com.steve1316.uma_android_automation.utils.VeteranFactorDomain]), never from
 *    raw OCR, so two reads of the same card produce the same token even when the OCR text jitters. It
 *    is null when the name did not resolve: an unknown name must not mint a trusted identity.
 *  - a STRUCTURAL token (`kind:stars`) is the name-free fallback. `kind` and `stars` are
 *    pixel-classified and already agree across re-reads, so this is always available and always stable,
 *    and it is what a consumer joins on when the name itself could not be resolved.
 */

/** Canonical per-factor semantic token: `kind:CANONNAME:stars`, or null when [canonicalName] is null.
 * The name is upper-cased/whitespace-normalized through the same helper PL-4 uses, so a resolved
 * Inspiration factor and a resolved lineage factor for the same canonical name produce equal tokens. */
fun canonicalFactorToken(kind: SparkRowKind, canonicalName: String?, stars: Int): String? =
    canonicalName?.let { "${kind.name.lowercase()}:${normalizeLineageFactorName(it)}:$stars" }

/** Name-free structural token: `kind:stars`. Always available, stable even when the name is unread. */
fun structuralFactorToken(kind: SparkRowKind, stars: Int): String = "${kind.name.lowercase()}:$stars"

/**
 * Trusted canonical set fingerprint: the per-factor canonical tokens, sorted and `|`-joined, or null
 * when the set is empty OR any factor is unresolved. A partly-unknown factor set must not present a
 * trusted identity as if it were fully known - the caller falls back to [structuralFactorSetFingerprint]
 * there. Order-independent so read order never changes the value.
 */
fun canonicalFactorSetFingerprint(tokens: List<String?>): String? {
    if (tokens.isEmpty()) return null
    val out = ArrayList<String>(tokens.size)
    for (t in tokens) out.add(t ?: return null)
    return out.sorted().joinToString("|")
}

/** Structural set fingerprint: the `kind:stars` tokens, sorted and `|`-joined. Always available and
 * name-free, so it is a stable identity for a factor set even when some names did not resolve. */
fun structuralFactorSetFingerprint(tokens: List<String>): String = tokens.sorted().joinToString("|")
