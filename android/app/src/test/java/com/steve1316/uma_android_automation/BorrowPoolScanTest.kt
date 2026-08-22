package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.GlyphBox
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit tests for the pure Borrow Card pool decoders. Every fixture is synthetic: the decoders have no
 * opinion about which cards exist, so real card names never appear here (a test that needed one would
 * be testing the catalogue, not the reader).
 */
@DisplayName("Borrow pool pure decoders")
class BorrowPoolScanTest {
    private val cyan = (0xFF shl 24) or (20 shl 16) or (200 shl 8) or 210
    private val grey = (0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100

    /** A sampler that returns cyan inside any of [cyanBoxes] and grey everywhere else. */
    private fun cyanSampler(vararg cyanBoxes: GlyphBox): SparkPixelSampler =
        SparkPixelSampler { x, y ->
            if (cyanBoxes.any { x >= it.x0 && x < it.x1 && y >= it.y0 && y < it.y1 }) cyan else grey
        }

    private fun solidSampler(color: Int): SparkPixelSampler = SparkPixelSampler { _, _ -> color }

    @Test
    @DisplayName("The name band splits into character and bracketed outfit whichever line the bracket is on")
    fun testSplitBorrowName() {
        assertEquals("Delta Dawn" to "Fire Trail", splitBorrowName("[Fire Trail]\nDelta Dawn"))
        assertEquals("Echo Edge" to "Cold Snap", splitBorrowName("Echo Edge\n(Cold Snap)"))
        assertEquals("Foxtrot" to null, splitBorrowName("Foxtrot"))
        assertEquals(null to null, splitBorrowName("   \n "))
    }

    @Test
    @DisplayName("Level parses only a plausible 1..50 and rejects noise")
    fun testParseBorrowLevel() {
        assertEquals(45, parseBorrowLevel("Lvl 45"))
        assertEquals(50, parseBorrowLevel("Lv50"))
        assertEquals(1, parseBorrowLevel("Lvl 1"))
        assertNull(parseBorrowLevel("Lvl"))
        assertNull(parseBorrowLevel("Lvl 99"))
        assertNull(parseBorrowLevel(""))
    }

    @Test
    @DisplayName("Rarity normalizes to R / SR / SSR, longest match first, else null")
    fun testParseBorrowRarity() {
        assertEquals("SSR", parseBorrowRarity("SSR"))
        assertEquals("SR", parseBorrowRarity("SR"))
        assertEquals("R", parseBorrowRarity("R"))
        assertEquals("SSR", parseBorrowRarity("sSr"))
        assertNull(parseBorrowRarity("???"))
    }

    @Test
    @DisplayName("Source type maps the provenance pill, never guessing on an unreadable one")
    fun testParseBorrowSourceType() {
        assertEquals(BorrowSourceType.FOLLOW, parseBorrowSourceType("Following"))
        assertEquals(BorrowSourceType.FRIEND, parseBorrowSourceType("Mutual Follow"))
        assertEquals(BorrowSourceType.GUEST, parseBorrowSourceType("Guest"))
        assertEquals(BorrowSourceType.UNKNOWN, parseBorrowSourceType(""))
        assertEquals(BorrowSourceType.UNKNOWN, parseBorrowSourceType("acquaintance"))
    }

    @Test
    @DisplayName("Owner name is redacted to a stable alias, never the raw name, and blanks collapse to null")
    fun testRedactOwnerAlias() {
        val a = redactOwnerAlias("SomePlayer")
        val b = redactOwnerAlias("someplayer")
        assertEquals(a, b, "the alias is stable across case/spacing noise")
        assertNotEquals(a, redactOwnerAlias("OtherPlayer"), "different owners get different aliases")
        assertTrue(a!!.startsWith("owner-"), "the alias carries no part of the raw name")
        assertFalse(a.contains("someplayer", ignoreCase = true))
        assertNull(redactOwnerAlias(null))
        assertNull(redactOwnerAlias(" "))
        assertNull(redactOwnerAlias("a"))
    }

    @Test
    @DisplayName("A fully lit pip band reads MLB; a partial band reads its filled prefix")
    fun testReadLimitBreakPipsFilled() {
        val box = GlyphBox(0, 0, 100, 30)
        assertEquals(4, readLimitBreakPips(cyanSampler(box), box), "the whole band lit is MLB")
        // Slots 0 and 1 lit, 2 and 3 dark.
        assertEquals(2, readLimitBreakPips(cyanSampler(GlyphBox(0, 0, 50, 30)), box))
        // Only slot 0 lit.
        assertEquals(1, readLimitBreakPips(cyanSampler(GlyphBox(0, 0, 25, 30)), box))
    }

    @Test
    @DisplayName("A gap ends the filled count: a stray cyan speck on the right cannot inflate a low limit break")
    fun testReadLimitBreakPipsGapStopsCount() {
        val box = GlyphBox(0, 0, 100, 30)
        // Slots 0,1 lit, slot 2 dark, slot 3 lit: the gap at slot 2 caps the count at 2.
        val sampler = cyanSampler(GlyphBox(0, 0, 50, 30), GlyphBox(75, 0, 100, 30))
        assertEquals(2, readLimitBreakPips(sampler, box))
    }

    @Test
    @DisplayName("A band with too little cyan reads UNKNOWN, never a confident zero")
    fun testReadLimitBreakPipsFailClosed() {
        val box = GlyphBox(0, 0, 100, 30)
        assertNull(readLimitBreakPips(solidSampler(grey), box), "a dark band is unread, not LB0")
    }

    @Test
    @DisplayName("Support type reads only the unambiguous families and drops the red (Stamina/Guts) ambiguity")
    fun testClassifyBorrowSupportType() {
        val box = GlyphBox(0, 0, 40, 40)
        val blue = (0xFF shl 24) or (40 shl 16) or (90 shl 8) or 220
        val orange = (0xFF shl 24) or (200 shl 16) or (120 shl 8) or 30
        val red = (0xFF shl 24) or (220 shl 16) or (40 shl 8) or 40
        assertEquals("Speed", classifyBorrowSupportType(solidSampler(blue), box))
        assertEquals("Power", classifyBorrowSupportType(solidSampler(orange), box))
        assertNull(classifyBorrowSupportType(solidSampler(red), box), "red cannot be split into Stamina vs Guts")
        assertNull(classifyBorrowSupportType(solidSampler(grey), box), "an achromatic icon is not a type")
    }

    @Test
    @DisplayName("The row fingerprint ignores level but separates the same card by owner")
    fun testRowFingerprint() {
        val a = borrowPoolRowFingerprint("Delta Dawn", "Fire Trail", "SSR", 4, "owner-1")
        val b = borrowPoolRowFingerprint("delta  dawn", "fire trail", "SSR", 4, "owner-1")
        assertEquals(a, b, "case and spacing noise do not change identity")
        val differentOwner = borrowPoolRowFingerprint("Delta Dawn", "Fire Trail", "SSR", 4, "owner-2")
        assertNotEquals(a, differentOwner, "the same card from two owners is two rows")
    }

    @Test
    @DisplayName("Walk-end and termination map onto the offline completeness semantics")
    fun testTerminationMapping() {
        assertEquals(BorrowPoolTermination.UI_END_REACHED, borrowWalkEndToTermination(BorrowWalkEnd.END_OF_LIST, false))
        assertEquals(BorrowPoolTermination.SCROLL_LIMIT_REACHED, borrowWalkEndToTermination(BorrowWalkEnd.MAX_PAGES, false))
        assertEquals(BorrowPoolTermination.UNEXPECTED_SCREEN, borrowWalkEndToTermination(BorrowWalkEnd.EMPTY_PICKER, false))
        assertEquals(BorrowPoolTermination.ABORTED, borrowWalkEndToTermination(BorrowWalkEnd.ABORTED, false))
        // The entry limit overrides a real end: a bounded run is a partial no matter how the walk ended.
        assertEquals(BorrowPoolTermination.ENTRY_LIMIT_REACHED, borrowWalkEndToTermination(BorrowWalkEnd.END_OF_LIST, true))

        assertEquals("UI_END_REACHED", BorrowPoolTermination.UI_END_REACHED.toSnapshotTermination())
        assertEquals("COMPLETE_VISIBLE_POOL", BorrowPoolTermination.VISIBLE_WINDOW_COMPLETE.toSnapshotTermination())
        assertEquals("SCROLL_LIMIT_REACHED", BorrowPoolTermination.SCROLL_LIMIT_REACHED.toSnapshotTermination())
        assertEquals("BOUNDED_PARTIAL", BorrowPoolTermination.ENTRY_LIMIT_REACHED.toSnapshotTermination())
        assertEquals("UNEXPECTED_SCREEN", BorrowPoolTermination.UNEXPECTED_SCREEN.toSnapshotTermination())
    }

    @Test
    @DisplayName("A serialized row carries the alias for the snapshot and the raw name only for local diagnosis")
    fun testSerializeRowPrivacyAndKeys() {
        val obs =
            BorrowRowObservation(
                pageIndex = 1,
                character = "Delta Dawn",
                outfit = "Fire Trail",
                rarity = "SSR",
                supportType = "Speed",
                level = 45,
                limitBreakIndex = 4,
                sourceType = BorrowSourceType.FOLLOW,
                ownerAlias = "owner-000abc",
                ownerNameRaw = "SomePlayer",
                blockedTag = null,
                rowFingerprint = "fp",
                confidence = "high",
                unresolvedFields = listOf("supportType"),
                evidence = "name='[Fire Trail] Delta Dawn' lvl='Lvl 45'",
            )
        val json = serializeBorrowRow("scan-1", obs)
        assertEquals("owner-000abc", json.getString("owner_alias"))
        assertEquals("SomePlayer", json.getString("owner_name_raw"), "the raw name stays only in the local JSONL")
        assertEquals(45, json.getInt("level"))
        assertEquals(4, json.getInt("limit_break_index"))
        assertEquals("FOLLOW", json.getString("source_type"))
        assertEquals("Fire Trail", json.getString("title"))
        assertEquals(1, json.getJSONArray("unresolved_fields").length())
        // The evidence string must never carry the raw owner name.
        assertFalse(json.getString("evidence").contains("SomePlayer"), "evidence carries no owner name")
    }

    @Test
    @DisplayName("The pure reader carries no card, trainer, deck, or account specifics")
    fun testReaderIsAgnostic() {
        val source = findRepoFile("src/main/java/com/steve1316/uma_android_automation/BorrowPoolScan.kt")
        assertTrue(source != null, "the reader source must be locatable for this check")
        val text = source!!.readText()
        for (forbidden in listOf("Kitasan", "Maruzensky", "Tazuna", "Copano", "Super Creek")) {
            assertFalse(text.contains(forbidden, ignoreCase = true), "the reader must not mention \"$forbidden\"")
        }
    }

    /** Walks up from the test working directory to find a repository file, independent of where Gradle ran. */
    private fun findRepoFile(relative: String): File? {
        var dir: File? = File(".").absoluteFile
        repeat(8) {
            val direct = File(dir, relative)
            if (direct.isFile) return direct
            val underApp = File(dir, "app/$relative")
            if (underApp.isFile) return underApp
            dir = dir?.parentFile
        }
        return null
    }
}
