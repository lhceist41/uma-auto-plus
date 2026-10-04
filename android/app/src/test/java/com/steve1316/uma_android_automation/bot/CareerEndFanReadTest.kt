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
    fun `a read that lost a character is unknown, not a shorter number`() {
        // Rice Shower's 225,250 was logged as fans=22525 over a held in-career 101394.
        assertNull(parseCareerEndFans("225,25o", heldFans = 101394))
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
