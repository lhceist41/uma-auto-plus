package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.DialogObjects
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.utils.FixturePng
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertActiveBonusesPanelPresent
import com.steve1316.uma_android_automation.utils.grandConcertBonusesUpdatedPresent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Grand Concert bonus popups, replayed on the live frames that stalled a run (see
 * src/test/resources/fixtures/concertbonuses/PROVENANCE.md): "Bonuses Updated!" was read as an event
 * cutscene and advanced with blind taps onto "Active Concert Bonuses", which no dialog matched.
 */
@DisplayName("Grand Concert bonus popups")
class ConcertBonusPopupsTest {
    private fun sampler(path: String): SparkPixelSampler {
        val img = requireNotNull(javaClass.getResourceAsStream("/fixtures/$path.png")) { "missing fixture $path" }.use { FixturePng.read(it) }
        return SparkPixelSampler { x, y -> img.getRGB(x, y) }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val f = File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")
            if (f.isFile) return f.readText().replace("\r\n", "\n")
            val g = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/$relative")
            if (g.isFile) return g.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        error("could not locate $relative")
    }

    @Test
    fun `both popups are registered dialogs that close with Close, never Confirm`() {
        val active = requireNotNull(DialogObjects.map["active_concert_bonuses"])
        assertEquals("Active Concert Bonuses", active.title)
        assertSame(ButtonClose, active.buttons.first())

        val updated = requireNotNull(DialogObjects.map["bonuses_updated"])
        assertEquals("Bonuses Updated", updated.title)
        // Confirm opens the Active Concert Bonuses panel and gains nothing.
        assertEquals(listOf(ButtonClose), updated.buttons)
        assertNull(updated.okButton)
    }

    @Test
    fun `the script-title popup is named from its pixels when OCR found no title`() {
        for (name in listOf("concertbonuses/bonuses_updated_live", "concertbonuses/bonuses_updated_second_live", "grandconcert/bonuses_updated")) {
            assertEquals("Bonuses Updated", DialogUtils.titleOrPixelFallback(null, sampler(name)), name)
        }
    }

    @Test
    fun `an OCR title is never replaced by the pixel read`() {
        // The popup's layout (green band, green right-hand button) is the ordinary dialog's: these frames
        // clear the escort probe's thresholds, so only the order protects their own names and handlers.
        val ordinary =
            mapOf(
                "concertbonuses/warning_live" to "consecutive_race_warning",
                "concertbonuses/auto_select_live" to "auto_select",
                "concertbonuses/restore_tp_live" to "confirm_restore_rp",
            )
        for ((name, dialogName) in ordinary) {
            val dialog = requireNotNull(DialogObjects.map[dialogName])
            assertEquals(dialog.title, DialogUtils.titleOrPixelFallback(dialog.title, sampler(name)), name)
        }
        // Even the real popup keeps a title OCR did read.
        assertEquals("Active Concert Bonuses", DialogUtils.titleOrPixelFallback("Active Concert Bonuses", sampler("concertbonuses/bonuses_updated_live")))
    }

    @Test
    fun `an ordinary green dialog is not named from pixels even when OCR found nothing`() {
        for (name in listOf("concertbonuses/warning_live", "concertbonuses/auto_select_live", "concertbonuses/restore_tp_live")) {
            assertNull(DialogUtils.titleOrPixelFallback(null, sampler(name)), name)
        }
    }

    @Test
    fun `no other live frame is named from pixels`() {
        // The panel has a title-bar gradient of its own and is named from OCR; an event with choices is no dialog.
        for (name in listOf("concertbonuses/active_bonuses_live", "concertbonuses/event_choices_live", "grandconcert/active_bonuses_panel", "grandconcert/career_main_turn1")) {
            assertNull(DialogUtils.titleFromPixels(sampler(name)), name)
        }
    }

    @Test
    fun `the escort probes agree with the live frames`() {
        assertTrue(grandConcertBonusesUpdatedPresent(sampler("concertbonuses/bonuses_updated_live")))
        assertTrue(grandConcertActiveBonusesPanelPresent(sampler("concertbonuses/active_bonuses_live")))
        assertFalse(grandConcertBonusesUpdatedPresent(sampler("concertbonuses/event_choices_live")))
        assertFalse(grandConcertActiveBonusesPanelPresent(sampler("concertbonuses/event_choices_live")))
    }

    @Test
    fun `the cutscene read and the dialog handler use the registered popups`() {
        val campaign = source("bot/Campaign.kt")
        val read = campaign.substringAfter("private fun isEventCutsceneSkipPillVisible(): Boolean {").substringBefore("\n    }\n")
        val guard = read.indexOf("DialogUtils.check(game.imageUtils, sourceBitmap = sourceBitmap)")
        assertTrue(guard >= 0, "the cutscene read no longer excludes a dialog banner")
        assertTrue(guard < read.indexOf("readSkipPill(sourceBitmap)"), "the dialog exclusion must come before the pill is read")

        val dialogs = source("components/Dialog.kt")
        val getTitle = dialogs.substringAfter("fun getTitle(").substringBefore("\n    }\n")
        assertTrue(getTitle.contains("readTitle(imageUtils, bitmap, logOnMiss) ?: titleFromPixels(bitmap)"), "getTitle must read the OCR title first and use the pixel read only as a fallback")

        val handler = source("bot/DialogHandler.kt")
        assertTrue(campaign.contains("\"consecutive_race_warning\" -> {"), "the consecutive race warning lost its handler")
        assertTrue(handler.contains("\"active_concert_bonuses\", \"bonuses_updated\" -> {\n                dialog.close(game.imageUtils)"), "the popups are not closed by the dialog handler")
    }
}
