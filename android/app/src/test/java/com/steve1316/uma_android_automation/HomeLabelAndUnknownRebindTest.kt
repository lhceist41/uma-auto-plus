package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.OwnInputProbeResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Missions screens keep the Home tab selected and read as Home; their "Event Missions" button must not pass as the CAREER label,
 * so the navigator taps the Home tab instead of the CAREER spot. An unrecognised screen gets a forced accessibility rebind only when
 * the own-input probe did not see the bot's tap arrive.
 */
@DisplayName("Home CAREER label and the unknown-screen rebind")
class HomeLabelAndUnknownRebindTest {
    @Test
    fun `the Event Missions button is not the CAREER label`() {
        assertNull(careerLabelWord("Event\nMissions"), "the live read on the Missions screen")
        assertNull(careerLabelWord("Event Missions"))
        assertNull(careerLabelWord("EVENT MISSIONS"))
    }

    @Test
    fun `CAREER and the plain Event label still confirm the button`() {
        assertEquals("CAREER", careerLabelWord("CAREER"))
        assertEquals("CAREER", careerLabelWord("career"))
        assertEquals("CAREER", careerLabelWord("CAREER\nEvent Missions"), "CAREER wins when both show")
        assertEquals("Event", careerLabelWord("Event"))
        assertEquals("Event", careerLabelWord("\nEvent\n"))
    }

    @Test
    fun `a read with neither word is not the button`() {
        assertNull(careerLabelWord(""))
        assertNull(careerLabelWord("Transfer"))
        assertNull(careerLabelWord("Eveńt"), "an accented misread stays unmatched, as before")
    }

    @Test
    fun `only a tap that did not arrive gets the rebind`() {
        assertFalse(unknownScreenNeedsRebind(OwnInputProbeResult.ARRIVED), "the bot's taps reach the screen; the screen is only unknown")
        assertTrue(unknownScreenNeedsRebind(OwnInputProbeResult.LOST))
        assertTrue(unknownScreenNeedsRebind(OwnInputProbeResult.INCONCLUSIVE), "no probe result keeps the rebind")
    }

    private val navigator by lazy { source("app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }

    @Test
    fun `the grant branch probes on every unknown tick and keeps no result`() {
        val start = navigator.indexOf("Unknown screen state (\$consecutiveUnknowns/\$unknownLimit)")
        val branch = navigator.substring(start, navigator.indexOf("waitSafe(2.0)", start))
        assertTrue(branch.contains("if (unknownScreenNeedsRebind(ownInputProbe())) rebindAccessibility() else checkAccessibility()"), branch)
        assertFalse(Regex("""^\s*rebindAccessibility\(\)\s*$""", RegexOption.MULTILINE).containsMatchIn(branch), "no unconditional rebind is left")
        assertFalse(Regex("""va[lr]\s+\w+\s*(:\s*OwnInputProbeResult\??)?\s*=\s*ownInputProbe\(\)""").containsMatchIn(navigator), "a probe result is never stored")
    }

    @Test
    fun `the OCR probe at the CAREER position uses the word rule`() {
        val start = navigator.indexOf("private fun ocrCareerLabelAtHomePosition(")
        val body = navigator.substring(start, navigator.indexOf("\n    }\n", start))
        assertTrue(body.contains("careerLabelWord(ocrText)"), body)
        assertFalse(body.contains("contains(\"EVENT\")"), "the old inline rule is gone")
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
