package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** The career-end fan total is the number the result screen shows, or unknown; never a mangled or pre-finale number. */
@DisplayName("Career-end fan read")
class CareerEndFanReadTest {
    @Test
    fun `the result screen's grouped totals parse exactly`() {
        // The six end-screen totals of one Grand Concert queue, as the game printed them.
        listOf("211,654", "260,409", "310,759", "185,415", "256,069", "225,250").forEach {
            assertEquals(it.replace(",", "").toInt(), parseCareerEndFans(it, heldFans = 101394), it)
        }
        assertEquals(225250, parseCareerEndFans(" +225,250\n", heldFans = 1))
        assertEquals(225250, parseCareerEndFans("225.250", heldFans = 1))
        assertEquals(850, parseCareerEndFans("850", heldFans = 1))
        assertEquals(1, parseCareerEndFans("1", heldFans = 1))
    }

    @Test
    fun `a read that lost its separator counts only between a real in-career count and five times it`() {
        assertEquals(225250, parseCareerEndFans("225250", heldFans = 101394))
        assertEquals(225250, parseCareerEndFans("225 250", heldFans = 101394))
        assertEquals(506969, parseCareerEndFans("506969", heldFans = 101394), "just under 5x")
        assertNull(parseCareerEndFans("506970", heldFans = 101394), "5x is out")
        assertNull(parseCareerEndFans("225250", heldFans = 1), "the default 1 is no floor")
        assertNull(parseCareerEndFans("225 250", heldFans = 1))
        assertNull(parseCareerEndFans("225250", heldFans = -1), "an unknown count is no floor")
        // C8's floor: the last in-career read was 101394.
        assertNull(parseCareerEndFans("22525", heldFans = 101394))
    }

    @Test
    fun `an inserted digit is unknown, not a total ten times too large`() {
        assertNull(parseCareerEndFans("9 225,250", heldFans = 1), "a stray digit before the total")
        assertNull(parseCareerEndFans("9 225,250", heldFans = 101394))
        assertNull(parseCareerEndFans("2251250", heldFans = 101394), "the comma read as 1")
        assertNull(parseCareerEndFans("9225250", heldFans = 101394))
        assertNull(parseCareerEndFans("9225,250", heldFans = 1))
    }

    @Test
    fun `a misread glyph inside a group reads as the digit the group pins`() {
        // Every unusable read logged on MuMu so far, with its in-career count. Frames showed 265,171, 270,711 and 295,418 (a stray mark after the 8).
        listOf(
            Triple("265,17 1", 174210, 265171),
            Triple("270,71 1", 179665, 270711),
            Triple("295,418,", 142662, 295418),
            Triple("236,29 ]", 138313, 236291),
            Triple("318,02 1", 191112, 318021),
            Triple("280,27 1", 147395, 280271),
            Triple("330,47O", 202735, 330470),
            Triple("209,77O", 117704, 209770),
            Triple("162,46O", 83877, 162460),
            Triple("278,558 -", 206712, 278558),
        ).forEach { (read, held, shown) -> assertEquals(shown, parseCareerEndFans(read, held), read) }
        assertEquals(265171, parseCareerEndFans("265,17 1", heldFans = 1))
        // An example string for Rice Shower's frame, which showed 225,250 (her raw read was not logged).
        assertEquals(225250, parseCareerEndFans("225,25o", heldFans = 101394))
        // A live read whose frame shows 175,719: the split falls after a group's first digit.
        assertEquals(175719, parseCareerEndFans("175,7 19", heldFans = 80023))
        assertEquals(265171, parseCareerEndFans("265,1 7 1", heldFans = 1))
        assertEquals(330470, parseCareerEndFans("330.47O", heldFans = 1), "a lookalike after a dot separator")
        assertEquals(1225250, parseCareerEndFans("1,225,250.", heldFans = 1))
    }

    @Test
    fun `a misread glyph that leaves the digit count open is still unknown`() {
        assertNull(parseCareerEndFans("295,418 1", heldFans = 1), "a digit-like mark after the last group")
        assertNull(parseCareerEndFans("278,558 --", heldFans = 1), "two marks")
        assertNull(parseCareerEndFans("295,41 8 1", heldFans = 1), "four digits in a group")
        assertNull(parseCareerEndFans("265,1  7 1", heldFans = 1), "two spaces")
        assertNull(parseCareerEndFans("265,17\n1", heldFans = 1), "a line break")
        assertNull(parseCareerEndFans("295,", heldFans = 1), "a separator with no group before it")
        assertNull(parseCareerEndFans("295,,", heldFans = 1))
        assertNull(parseCareerEndFans("2 65,171", heldFans = 1), "a space in the leading group is a stray mark")
        assertNull(parseCareerEndFans("O65,171", heldFans = 1), "a lookalike in the leading group")
        assertNull(parseCareerEndFans("265,1B1", heldFans = 1), "an unmapped letter")
        listOf("265,17|", "265,17l", "265,17I").forEach { assertNull(parseCareerEndFans(it, heldFans = 1), "an unlogged 1-lookalike: $it") }
        assertNull(parseCareerEndFans("265,17 1", heldFans = 265172), "below the in-career count")
        assertNull(parseCareerEndFans("295,418,", heldFans = 295419))
    }

    @Test
    fun `a read that lost a character is unknown, not a shorter number`() {
        assertNull(parseCareerEndFans("225,25", heldFans = 1))
        assertNull(parseCareerEndFans("22525", heldFans = 1))
        assertNull(parseCareerEndFans("2,25250", heldFans = 1))
        assertNull(parseCareerEndFans("", heldFans = 1))
    }

    @Test
    fun `a well-formed total below the in-career count is unknown`() {
        assertNull(parseCareerEndFans("22,525", heldFans = 101394))
        assertEquals(101394, parseCareerEndFans("101,394", heldFans = 101394))
    }

    @Test
    fun `an overlong read is unknown instead of throwing`() {
        assertNull(parseCareerEndFans("99,999,999,999", heldFans = 1))
    }

    @Test
    fun `the career end reads fans through parseCareerEndFans and reports a failed read as -1`() {
        val campaign = File(kotlinRoot(), "bot/Campaign.kt").readText().replace("\r\n", "\n")
        val end = campaign.substringAfter("// Perform a final update of the fan count.").substringBefore("val notAccepted =")
        assertTrue(end.contains("parseCareerEndFans(fansText, heldFans)"))
        assertFalse(end.contains("replace(Regex(\"[^0-9]\"), \"\")"), "stripping every non-digit hides a lost character")
        assertEquals(2, Regex("""trainee\.fans = -1""").findAll(end).count(), "an unusable read and a missing Details button both report fans=-1")
    }

    @Test
    fun `a RETRY_SPEND re-entry neither re-reads an accepted total nor floors on its own -1`() {
        val campaign = File(kotlinRoot(), "bot/Campaign.kt").readText().replace("\r\n", "\n")
        val end = campaign.substringAfter("// Perform a final update of the fan count.").substringBefore("val notAccepted =")
        assertTrue(end.contains("val heldFans = careerEndHeldFans ?: trainee.fans.also { careerEndHeldFans = it }"), "the floor is captured once")
        assertTrue(end.indexOf("val heldFans =") < end.indexOf("trainee.fans = -1"), "captured before any -1 is written")
        val guarded = end.substringAfter("if (!careerEndFansAccepted) {\n").substringBefore("\n                    }\n")
        assertTrue(guarded.contains("debugName = \"final_fan_count\""), "the OCR runs only until a total is accepted")
        assertTrue(guarded.contains("trainee.observeFanCount(finalFans)\n                            careerEndFansAccepted = true"))
        assertTrue(end.contains("if (!careerEndFansAccepted) trainee.fans = -1"), "a missing Details button on re-entry keeps the accepted total")
        assertTrue(campaign.contains("careerEndSpendRetryUsed = true\n                        bCareerEndSkillsHandled = false"), "the re-entry this guards")
    }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
