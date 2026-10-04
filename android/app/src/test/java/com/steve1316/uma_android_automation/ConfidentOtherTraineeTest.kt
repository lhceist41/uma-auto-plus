package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.closingOnStop
import com.steve1316.uma_android_automation.bot.confidentOtherTrainee
import com.steve1316.uma_android_automation.bot.deOutfitTraineeName
import com.steve1316.uma_android_automation.utils.VeteranIdentityNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A run once finished another trainee's leftover career under its own slot's preset because her name, read cleanly, was not in the
 * rotation and so was taken for an OCR misread. A read that names a real character other than the target's own now stops the run.
 */
@DisplayName("A confidently read trainee outside the rotation stops the run")
class ConfidentOtherTraineeTest {
    private val characters = VeteranIdentityNames.CHARACTERS
    private val threshold = 0.85

    @Nested
    @DisplayName("confidentOtherTrainee")
    inner class Helper {
        @Test
        fun `a clean read of another character names her`() {
            assertEquals("Rice Shower", confidentOtherTrainee("Rice Shower", "[Autumn Cosmos] Gold City", characters, threshold))
        }

        @Test
        fun `a garbled or unknown read names nobody`() {
            for (read in listOf("R?c3 Sh0w", "Rlc", "", "Trainer", "Career Complete")) {
                assertNull(confidentOtherTrainee(read, "[Autumn Cosmos] Gold City", characters, threshold), read)
            }
        }

        @Test
        fun `the target's own character never counts, in any outfit form`() {
            for (target in listOf("Gold City", "[Autumn Cosmos] Gold City", " [Autumn Cosmos]  Gold City ")) {
                assertNull(confidentOtherTrainee("Gold City", target, characters, threshold), target)
                assertNull(confidentOtherTrainee("[Autumn Cosmos] Gold City", target, characters, threshold), target)
            }
        }

        @Test
        fun `a shared first word does not make two trainees one`() {
            assertEquals("Gold Ship", confidentOtherTrainee("Gold Ship", "[Autumn Cosmos] Gold City", characters, threshold))
        }

        @Test
        fun `an unknown character list never stops`() {
            assertNull(confidentOtherTrainee("Rice Shower", "Gold City", emptyList(), threshold))
        }

        @Test
        fun `outfit titles come off only at the front`() {
            assertEquals("Gold City", deOutfitTraineeName("[Autumn Cosmos] Gold City"))
            assertEquals("Gold City", deOutfitTraineeName("Gold City"))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }

        private val verify by lazy {
            campaign.substring(campaign.indexOf("private fun verifyRotationTrainee() {"), campaign.indexOf("private fun deOutfit(name: String)"))
        }

        @Test
        fun `the inconclusive branch stops first when the read names another character`() {
            val check = verify.indexOf("confidentOtherTrainee(inCareer, target, VeteranIdentityNames.CHARACTERS, rotationVerifyMatchThreshold)?.let { other ->")
            val inconclusive = verify.indexOf("Trainee verify inconclusive")
            assertTrue(check >= 0, "the helper is consulted")
            assertTrue(check < inconclusive, "before the continue-anyway warning")
            assertTrue(check > verify.indexOf("if (matched != null && bestScore >= rotationVerifyMatchThreshold"), "after the rotation resync path, which is unchanged")
            val stop = verify.substring(check, inconclusive)
            assertTrue(stop.contains("StartModule.queueStopKey = \"TRAINEE_MISMATCH\""), "the existing key and player words")
            assertTrue(stop.contains("StartModule.queueStopRequested = true"))
            assertTrue(stop.contains("throw InterruptedException(reason)"), "nothing else runs under the wrong preset")
            assertTrue(stop.contains("career was '\$other' but the queue loaded the preset for '\$target'"), "the reason names both trainees")
            assertFalse(Regex("\\.tap|click\\(").containsMatchIn(stop), "no tap on the way out")
        }

        @Test
        fun `the stop keeps the career and uses the rotation's own threshold`() {
            assertTrue(verify.contains("The career is kept in the game."))
            assertFalse(verify.contains("confidentOtherTrainee(inCareer, target, VeteranIdentityNames.CHARACTERS, 0."), "no separate bar")
        }
    }

    /** A stop left the Umamusume Details dialog open over the career-end screen, so the next Start could not see that screen and refused. */
    @Nested
    @DisplayName("the details dialog on a stop")
    inner class DialogOnStop {
        @Test
        fun `a stop closes the dialog once and still stops`() {
            var closes = 0
            val stop = InterruptedException("Stopped on trainee mismatch")
            val thrown = assertThrows(InterruptedException::class.java) { closingOnStop({ closes++ }) { throw stop } }
            assertSame(stop, thrown, "the same stop goes on")
            assertEquals(1, closes)
        }

        @Test
        fun `a passing check leaves the dialog to the handler's own close`() {
            var closes = 0
            var ran = false
            closingOnStop({ closes++ }) { ran = true }
            assertTrue(ran)
            assertEquals(0, closes)
        }

        @Test
        fun `other errors are not treated as a stop`() {
            var closes = 0
            assertThrows(IllegalStateException::class.java) { closingOnStop({ closes++ }) { throw IllegalStateException("unhandled dialog") } }
            assertEquals(0, closes)
        }

        @Test
        fun `the details dialog handler wraps the trainee check`() {
            val campaign = source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt")
            val handler = campaign.substring(campaign.indexOf("\"umamusume_details\" -> {"), campaign.indexOf("\"choose_recreation_partner\" -> {"))
            assertTrue(handler.contains("closingOnStop({ result.dialog.close(game.imageUtils) }) { verifyRotationTrainee() }"))
            assertEquals(1, Regex("verifyRotationTrainee\\(\\)").findAll(handler).count(), "no unwrapped call")
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }
}
