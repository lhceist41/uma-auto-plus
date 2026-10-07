package com.steve1316.uma_android_automation.utils

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every preset target and every outfit in the shipped data against every outfit banner, clean and
 * with one OCR slip, through the real matcher decision (TraineeNameMatcher.isTargetBanner at the
 * navigator's threshold). The data is read at test time, so new trainees and outfits are covered
 * without editing this file.
 */
@DisplayName("Trainee name confusion (real data, real matcher)")
class TraineeNameConfusionTest {
    private data class Outfit(val character: String, val title: String, val cardId: Int, val released: Boolean) {
        val banner get() = "[$title] $character"
    }

    private data class Target(val inGameName: String, val excludes: List<String>) {
        val character get() = inGameName.substringAfter("] ", inGameName)
        val isBare get() = !inGameName.startsWith("[")
    }

    private val threshold by lazy { navigatorThreshold() }
    private val outfits by lazy { loadOutfits() }
    private val presetTargets by lazy { loadTargets() }
    private val outfitTargets by lazy { outfits.map { Target(it.banner, emptyList()) } }
    private val allTargets by lazy { presetTargets + outfitTargets }

    private fun accepts(t: Target, banner: String) = TraineeNameMatcher.isTargetBanner(t.inGameName, banner, t.excludes, threshold)

    private fun baseOutfit(character: String) = outfits.filter { it.character == character }.minByOrNull { it.cardId }

    /** The banner a target is meant to pick: its own outfit, or the character's base card for a bare name. */
    private fun ownBanner(t: Target) = if (t.isBare) baseOutfit(t.character)?.banner ?: error("no outfit data for '${t.inGameName}'") else t.inGameName

    /** Other outfits close enough to a target to matter: same character, or similar text. Clean accepts have their own tests. */
    private val rejectionPairs by lazy {
        allTargets.flatMap { t ->
            outfits.filter { o ->
                o.banner != ownBanner(t) && !accepts(t, o.banner) &&
                    (o.character == t.character || TraineeNameMatcher.similarity(t.inGameName, o.banner) >= 0.60)
            }.map { t to it }
        }
    }

    private enum class Wrong { OTHER_CHARACTER, SIBLING_OF_OUTFIT_TARGET, SIBLING_OF_BARE_TARGET }

    private fun wrongClass(t: Target, o: Outfit) =
        when {
            o.character != t.character -> Wrong.OTHER_CHARACTER
            t.isBare -> Wrong.SIBLING_OF_BARE_TARGET
            else -> Wrong.SIBLING_OF_OUTFIT_TARGET
        }

    private fun ownMisses(kinds: Set<String>?): List<String> =
        allTargets.flatMap { t ->
            slips(ownBanner(t)).filter { kinds == null || it.first in kinds }.filter { !accepts(t, it.second) }
                .map { "${it.first}: '${t.inGameName}' misses '${it.second}' (${fmt(TraineeNameMatcher.score(t.inGameName, it.second))})" }
        }

    private fun wrongAccepts(cls: Wrong, kinds: Set<String>?): List<String> =
        rejectionPairs.filter { wrongClass(it.first, it.second) == cls }.flatMap { (t, o) ->
            slips(o.banner).filter { kinds == null || it.first in kinds }.filter { accepts(t, it.second) }
                .map { "${it.first}: '${t.inGameName}' accepts '${it.second}' from '${o.banner}' (${fmt(TraineeNameMatcher.score(t.inGameName, it.second))})" }
        }

    private fun assertNone(failures: List<String>) = assertTrue(failures.isEmpty(), "${failures.size} case(s), first 25:\n" + failures.take(25).joinToString("\n"))

    @Test
    fun `every target accepts its own banner and no other character's`() {
        val failures = mutableListOf<String>()
        for (t in allTargets) {
            val own = ownBanner(t)
            if (!accepts(t, own)) failures += "MISS '${t.inGameName}' x '$own' (${fmt(TraineeNameMatcher.score(t.inGameName, own))}) excludes=${t.excludes}"
            outfits.filter { it.character != t.character && accepts(t, it.banner) }.forEach { failures += "CROSS '${t.inGameName}' accepts '${it.banner}'" }
        }
        assertNone(failures)
    }

    @Test
    fun `an outfit target accepts only its own outfit`() {
        assertNone(outfitTargets.flatMap { t -> outfits.filter { it.banner != t.inGameName && accepts(t, it.banner) }.map { "'${t.inGameName}' accepts '${it.banner}'" } })
    }

    @Test
    fun `a bare target skips every released sibling outfit`() {
        val failures = presetTargets.filter { it.isBare }.flatMap { t ->
            outfits.filter { it.character == t.character && it.released && it != baseOutfit(t.character) && accepts(t, it.banner) }
                .map { "'${t.inGameName}' (excludes ${t.excludes}) also accepts '${it.banner}'" }
        }
        assertNone(failures)
    }

    @Test
    fun `every preset character has outfit data`() {
        val presetCharacters = presetNames().map { parsePresetName(it).first }.toSet()
        val dataCharacters = outfits.map { it.character }.toSet()
        assertTrue(presetCharacters.all { it in dataCharacters }, "preset characters without outfit data: ${presetCharacters - dataCharacters}")
    }

    @Test
    fun `slips that already hold`() {
        assertNone(ownMisses(setOf("badge digit appended", "first character clipped", "space doubled", "leading junk token")))
        assertNone(wrongAccepts(Wrong.OTHER_CHARACTER, slipKinds - setOf("edge cut 5", "one character dropped", "space inserted")))
    }

    @Test
    fun `a bare target skips every sibling outfit including unreleased ones`() {
        val failures = presetTargets.filter { it.isBare }.flatMap { t ->
            outfits.filter { it.character == t.character && it != baseOutfit(t.character) && accepts(t, it.banner) }.map { "'${t.inGameName}' accepts '${it.banner}' released=${it.released}" }
        }
        assertNone(failures)
    }

    @Test
    fun `a bare target still skips a listed sibling under one slip`() = assertNone(wrongAccepts(Wrong.SIBLING_OF_BARE_TARGET, null))

    private val mihono get() = presetTargets.first { it.inGameName == "Mihono Bourbon" }

    @Test
    fun `a bare target rejects a clean sibling banner`() {
        assertTrue(mihono.excludes.contains("CODE: ICING"), "expected the plain Mihono preset to list CODE: ICING, got ${mihono.excludes}")
        assertTrue(!accepts(mihono, "[CODE: ICING] Mihono Bourbon"))
    }

    @Test
    fun `the 2026-09-28 live read does not select the excluded outfit for bare Mihono Bourbon`() {
        assertTrue(!accepts(mihono, "J [CODE: ICINGJ Mihono Bourbon"), "bare 'Mihono Bourbon' accepted the CODE: ICING banner as read live")
    }

    @Test
    fun `another character is never accepted under one slip`() = assertNone(wrongAccepts(Wrong.OTHER_CHARACTER, null))

    @Test
    fun `an outfit banner survives the slips seen in live reads`() {
        val kinds = setOf("star dropped", "star read as other glyph", "closing bracket misread", "opening bracket misread", "O/0 swap", "I/l/1 swap", "first letter clipped", "junk letter before name", liveCombination)
        assertNone(ownMisses(kinds))
    }

    private val liveCombination = "live combination"

    private val glyphs = "☆★♡♪"

    private val slipKinds =
        setOf(
            "star dropped", "star read as other glyph", "closing bracket misread", "opening bracket misread", "I/l/1 swap", "O/0 swap", "space inserted", "space doubled",
            "space dropped", "one character dropped", "first letter clipped", "first character clipped", "badge digit appended", "edge cut 1", "edge cut 2", "edge cut 3", "edge cut 5",
            "leading junk token", "junk letter before name", liveCombination,
        )

    /** One OCR slip per variant, labeled by kind. */
    private fun slips(b: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()

        fun add(kind: String, v: String) {
            if (v != b) out += kind to v
        }
        if (b.any { it in glyphs }) {
            add("star dropped", b.filter { it !in glyphs })
            for (r in listOf("x", "t", "e", "*", "8", "-", "<", "&", "D", "!")) add("star read as other glyph", b.map { if (it in glyphs) r else it.toString() }.joinToString(""))
        }
        for (r in listOf("l", "1", "I", "t", "j", "|", "", ")", "J", "!", "<", "&", "D")) add("closing bracket misread", b.replaceFirst("]", r))
        for (r in listOf("(", "", "l", "1", "{", "I")) add("opening bracket misread", b.replaceFirst("[", r))
        for ((i, c) in b.withIndex()) {
            if (c in "Il1i|") for (r in "Il1i") if (r != c) add("I/l/1 swap", b.replaceRange(i, i + 1, r.toString()))
            if (c in "Oo0") add("O/0 swap", b.replaceRange(i, i + 1, if (c == '0') "O" else "0"))
            if (i > 0 && i < b.length - 1 && c.isLetter() && b[i - 1].isLetter()) add("space inserted", b.substring(0, i) + " " + b.substring(i))
            if (c == ' ') {
                add("space doubled", b.substring(0, i) + " " + b.substring(i))
                add("space dropped", b.removeRange(i, i + 1))
            }
            if (i > 0 && i < b.length - 1) add("one character dropped", b.removeRange(i, i + 1))
        }
        val firstLetter = b.indexOfFirst { it.isLetter() }
        if (firstLetter >= 0) add("first letter clipped", b.removeRange(firstLetter, firstLetter + 1))
        add("first character clipped", b.drop(1))
        add("badge digit appended", "$b 1")
        add("badge digit appended", "$b 2")
        for (k in listOf(1, 2, 3, 5)) add("edge cut $k", b.dropLast(k))
        for (j in listOf("J ", "K ", "D ")) add("leading junk token", j + b)
        for (j in listOf("K ", "J ")) add("junk letter before name", b.replaceFirst("] ", "] $j"))
        out += liveCombinations(b)
        return out
    }

    /**
     * Shapes measured in live reads, applied together because most live misreads carry several:
     * a leading junk token ("J "), "(" for "[", a lost or glued "]", a lost or misread glyph.
     * Only variants that change at least two parts are listed; single parts have their own kinds.
     */
    private fun liveCombinations(b: String): List<Pair<String, String>> {
        if (!b.startsWith("[") || !b.contains("] ")) return emptyList()
        val title = b.substringAfter("[").substringBefore("] ")
        val name = b.substringAfter("] ")
        val titles = listOf(title) + if (title.any { it in glyphs }) listOf("", "t", "<").map { r -> title.map { if (it in glyphs) r else it.toString() }.joinToString("") } else emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (lead in listOf("", "J ")) for (open in listOf("[", "(", "")) for (close in listOf("]", "", "J", "l", "!")) for (t in titles) {
            val changed = listOf(lead != "", open != "[", close != "]", t != title).count { it }
            if (changed >= 2) out += liveCombination to "$lead$open$t$close $name"
        }
        return out
    }

    @Test
    fun `survey`() {
        assumeTrue(System.getenv("NAMECHK_SURVEY") != null, "set NAMECHK_SURVEY to write build/namechk-survey.txt")
        val r = StringBuilder()
        r.appendLine("threshold=$threshold outfits=${outfits.size} (unreleased ${outfits.count { !it.released }}) characters=${outfits.map { it.character }.toSet().size} presetTargets=${presetTargets.size} (bare ${presetTargets.count { it.isBare }}) outfitTargets=${outfitTargets.size}")
        r.appendLine("lowest clean own score: " + fmt(allTargets.minOf { TraineeNameMatcher.score(it.inGameName, ownBanner(it)) }))
        r.appendLine("highest clean other-character score: " + fmt(allTargets.maxOf { t -> outfits.filter { it.character != t.character }.maxOf { TraineeNameMatcher.score(t.inGameName, it.banner) } }))
        val scored = allTargets.flatMap { t -> outfits.filter { it.character != t.character }.map { Triple(t.inGameName, it.banner, TraineeNameMatcher.score(t.inGameName, it.banner)) } }
        r.appendLine("closest other-character banners by accept score (accepted at >= $threshold):")
        scored.sortedByDescending { it.third }.take(10).forEach { r.appendLine("  ${fmt(it.third)} '${it.first}' vs '${it.second}'") }
        val sims = outfitTargets.flatMap { t -> outfits.filter { it.character != t.character }.map { Triple(t.inGameName, it.banner, TraineeNameMatcher.similarity(t.inGameName, it.banner)) } }
        r.appendLine("closest other-character banners by plain similarity (no per-word gate):")
        sims.sortedByDescending { it.third }.take(12).forEach { r.appendLine("  ${fmt(it.third)} '${it.first}' vs '${it.second}'") }

        val own = ownMisses(null)
        r.appendLine("own banner under one slip: kind, tried, missed")
        val tried = allTargets.flatMap { slips(ownBanner(it)) }.groupingBy { it.first }.eachCount().toSortedMap()
        val missed = own.groupingBy { it.substringBefore(":") }.eachCount()
        tried.forEach { (k, n) -> r.appendLine("  $k: $n tried, ${missed[k] ?: 0} missed") }
        r.appendLine("rejection pairs: ${rejectionPairs.size}")
        for (cls in Wrong.values()) {
            val w = wrongAccepts(cls, null)
            r.appendLine("wrong accepts $cls: ${w.size} " + w.groupingBy { it.substringBefore(":") }.eachCount().toSortedMap())
            w.groupBy { it.substringBefore(":") }.toSortedMap().forEach { (k, l) -> r.appendLine("  [$k] " + l.take(3).joinToString(" | ")) }
        }
        r.appendLine("own misses, distinct targets per kind:")
        own.groupBy { it.substringBefore(":") }.toSortedMap().forEach { (k, l) ->
            r.appendLine("  [$k] ${l.size}: " + l.map { it.substringAfter("'").substringBefore("'") }.distinct().take(40))
        }
        r.appendLine("trailing characters that can be cut before the own banner is lost (preset targets):")
        presetTargets.groupBy { t -> (0 until 20).takeWhile { accepts(t, ownBanner(t).dropLast(it + 1)) }.size }.toSortedMap()
            .forEach { (k, l) -> r.appendLine("  survives $k cut(s): ${l.size} ${if (k <= 3) l.map { it.inGameName } else ""}") }
        File("build/namechk-survey.txt").also { it.parentFile.mkdirs() }.writeText(r.toString(), Charsets.UTF_8)
    }

    private fun fmt(d: Double) = "%.3f".format(d)

    private fun navigatorThreshold(): Double {
        val src = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText()
        return Regex("""private val traineeMatchThreshold = ([0-9.]+)""").find(src)?.groupValues?.get(1)?.toDouble() ?: error("traineeMatchThreshold not found")
    }

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private val presetSource by lazy { repoFile("src/data/characterPresets.ts").readLines(Charsets.UTF_8) }

    private fun presetNames() = presetSource.mapNotNull { Regex("""^ {8}name: "(.*)",$""").find(it)?.groupValues?.get(1) }.distinct()

    /** The preset spelling wins over the data spelling ("T.M. Opera O" vs "TM Opera O"), as the game shows the preset one. */
    private fun loadOutfits(): List<Outfit> {
        val spelling = presetNames().map { parsePresetName(it).first }.distinct().associateBy { squash(it) }
        val root = JSONObject(repoFile("src/data/character_outfits.json").readText(Charsets.UTF_8))
        return root.keys().asSequence().flatMap { key ->
            val arr = root.getJSONObject(key).getJSONArray("outfits")
            (0 until arr.length()).map { arr.getJSONObject(it) }.map { Outfit(spelling[squash(key)] ?: key, it.getString("title"), it.getInt("cardId"), !it.isNull("releasedEn")) }
        }.toList()
    }

    private val nonOutfitSuffixes by lazy {
        val src = repoFile("src/lib/presetNames.ts").readText(Charsets.UTF_8)
        val set = Regex("""NON_OUTFIT_SUFFIXES = new Set\(\[(.*?)]\)""").find(src)?.groupValues?.get(1) ?: error("NON_OUTFIT_SUFFIXES not found")
        Regex("\"(.*?)\"").findAll(set).map { it.groupValues[1] }.toSet()
    }

    /** Mirrors parsePresetName in src/lib/presetNames.ts. */
    private fun parsePresetName(name: String): Pair<String, String?> {
        val m = Regex("""^(.*?)\s*\(([^)]*)\)\s*$""").find(name) ?: return name.trim() to null
        val suffix = m.groupValues[2].trim()
        return if (suffix in nonOutfitSuffixes) m.groupValues[1].trim() to null else m.groupValues[1].trim() to suffix
    }

    private fun outfitTitles(character: String) = outfits.filter { it.character == character }.map { it.title }

    /** Mirrors deriveInGameName and deriveExcludeOutfits in src/lib/rotationSnapshots.ts, including the `traineeName` override. */
    private fun loadTargets(): List<Target> {
        val overrides = mutableMapOf<String, String>()
        var last: String? = null
        for (line in presetSource) {
            Regex("""^ {8}name: "(.*)",$""").find(line)?.let { last = it.groupValues[1] }
            Regex("""^ {8}traineeName: "(.*)",$""").find(line)?.let { m -> last?.let { overrides[it] = m.groupValues[1] } }
        }
        val parsed = presetNames().map { parsePresetName(overrides[it] ?: it) }
        return parsed.map { (base, outfit) ->
            Target(
                if (outfit != null) "[$outfit] $base" else base,
                if (outfit != null) emptyList() else (outfitTitles(base) + parsed.filter { it.second != null && it.first == base }.map { it.second!! }).distinct().filter { it != baseOutfit(base)?.title },
            )
        }.distinct()
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${System.getProperty("user.dir")}")
    }
}
