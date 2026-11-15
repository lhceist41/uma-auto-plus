package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Trainee
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A career end re-entered after a process restart or a lost Finish plays the same career's end screen again. Its record was
 * already written, so the second pass must not add another one, while a career this run launched is always recorded.
 */
class CareerEndRecordOnceTest {
    private val now = 1_800_000_000_000L
    private val recorded = CareerEndKey("Tamamo_Cross", "Unity_Cup", 280692, now - 13 * 60 * 1000L)

    private fun matches(
        saved: CareerEndKey? = recorded,
        trainee: String = "Tamamo_Cross",
        scenario: String = "Unity_Cup",
        fans: Int = 280692,
        careerLaunched: Boolean = false,
        completed: Boolean = true,
        nowMs: Long = now,
    ) = careerEndAlreadyRecorded(saved, trainee, scenario, fans, careerLaunched, completed, nowMs)

    @Nested
    inner class Match {
        @Test
        fun `the same career resumed at its end screen is already recorded`() {
            assertTrue(matches())
        }

        @Test
        fun `a career this run launched is always recorded`() {
            assertFalse(matches(careerLaunched = true))
        }

        @Test
        fun `an unreadable final fan count never matches`() {
            assertFalse(matches(fans = -1))
            assertFalse(matches(saved = recorded.copy(fans = -1), fans = -1))
        }

        @Test
        fun `a different fan count is another career`() {
            assertFalse(matches(fans = 280693))
        }

        @Test
        fun `another trainee or scenario is another career`() {
            assertFalse(matches(trainee = "King_Halo"))
            assertFalse(matches(scenario = "URA_Finale"))
        }

        @Test
        fun `a record older than a day no longer matches`() {
            assertTrue(matches(nowMs = recorded.atMs + CAREER_END_KEY_MAX_AGE_MS))
            assertFalse(matches(nowMs = recorded.atMs + CAREER_END_KEY_MAX_AGE_MS + 1))
            assertFalse(matches(nowMs = recorded.atMs - 1), "a record dated in the future proves nothing")
        }

        @Test
        fun `nothing recorded or a stopped run writes`() {
            assertFalse(matches(saved = null))
            assertFalse(matches(completed = false))
        }
    }

    @Nested
    inner class Stored {
        @Test
        fun `the stored key reads back as written`() {
            assertEquals(recorded, parseCareerEndKey(careerEndKeyValue(recorded)))
        }

        @Test
        fun `a missing or damaged value reads as nothing recorded`() {
            assertNull(parseCareerEndKey(null))
            assertNull(parseCareerEndKey(""))
            assertNull(parseCareerEndKey("{not json"))
            assertNull(parseCareerEndKey("""{"trainee":"Tamamo_Cross","scenario":"Unity_Cup"}"""))
        }
    }

    @Nested
    inner class SkillPoints {
        @Test
        fun `skill points never read are reported unknown, not as the starting 120`() {
            val trainee = Trainee()
            assertEquals(-1, reportedSkillPoints(trainee.skillPoints, trainee.skillPointsReads))
            assertEquals(-1, reportedSkillPoints(350, 0))
        }

        @Test
        fun `a read value is reported, zero included`() {
            assertEquals(2, reportedSkillPoints(2, 1))
            assertEquals(0, reportedSkillPoints(0, 3))
        }
    }

    @Nested
    inner class Wiring {
        private val campaign = source("bot/Campaign.kt")
        private val ledgerLine = campaign.substringAfter("override fun careerEndLedgerLine(result: TaskResult): String {").substringBefore("\n    }\n")

        @Test
        fun `the check runs before both corpus appends and the ledger line`() {
            val check = ledgerLine.indexOf("if (careerEndAlreadyRecorded(")
            assertTrue(check > 0)
            assertTrue(check < ledgerLine.indexOf("OutcomeCorpus.append(game.myContext, record)"))
            assertTrue(check < ledgerLine.indexOf("scenarioCareerEndRecord(careerEndFp"))
            assertTrue(check < ledgerLine.indexOf("append(\"[CAREER_END] result=\")"))
            assertTrue(ledgerLine.contains("careerEndAlreadyRecorded(recordedKey, resolvedName, scenarioToken, trainee.fans, game.careerLaunched, completed, nowMs)"))
        }

        @Test
        fun `the re-entered pass keeps the hand-off and the sequence`() {
            val check = ledgerLine.indexOf("if (careerEndAlreadyRecorded(")
            for (kept in listOf("StartModule.lastCareerEndStats =", "StartModule.lastCareerEndResult =", "StartModule.lastCareerEndSeq++")) {
                val at = ledgerLine.indexOf(kept)
                assertTrue(at in 0 until check, "$kept must still run on a re-entered pass")
            }
        }

        @Test
        fun `the already recorded line carries the re-read values`() {
            val skipped = ledgerLine.substringAfter("if (careerEndAlreadyRecorded(").substringBefore("\n        }\n")
            assertTrue(skipped.contains("return \"[CAREER_END] Already recorded for this career"))
            for (field in listOf("fans=\${trainee.fans}", "spd=\${st.speed}", "sta=\${st.stamina}", "pwr=\${st.power}", "grt=\${st.guts}", "wit=\${st.wit}", "skillPts=\$skillPts")) {
                assertTrue(skipped.contains(field), "missing $field")
            }
            assertFalse(skipped.contains("result="), "the skipped line must not read as a second outcome line")
        }

        @Test
        fun `only a completed career with a readable fan count is stored, after its record`() {
            val store = ledgerLine.indexOf("StartModule.setQueueStateValue(game.myContext, CAREER_END_KEY_SETTING,")
            assertTrue(store > ledgerLine.indexOf("OutcomeCorpus.append(game.myContext, record)"))
            assertTrue(ledgerLine.contains("if (completed && trainee.fans > 0) StartModule.setQueueStateValue("))
        }

        @Test
        fun `the record and the line report skill points only when they were read`() {
            assertTrue(ledgerLine.contains("val skillPts = reportedSkillPoints(trainee.skillPoints, trainee.skillPointsReads)"))
            assertTrue(ledgerLine.contains("put(\"skillPts\", skillPts)"))
            assertTrue(ledgerLine.contains("append(\" skillPts=\").append(skillPts)"))
            assertFalse(ledgerLine.contains("trainee.skillPoints)"), "no path may print the raw starting value")
        }

        private fun source(relative: String): String = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

        private fun kotlinRoot(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
                if (a.isDirectory) return a
                val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
                if (b.isDirectory) return b
                dir = dir?.parentFile
            }
            error("Kotlin source root not found")
        }
    }
}
