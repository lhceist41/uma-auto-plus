package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.CareerLaunchNavigator.Companion.TpRestoreItem
import com.steve1316.uma_android_automation.CareerLaunchNavigator.Companion.TpRestoreRowChoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Once Toughness 30 and Star Fruit run out, TP restore Max-fills with Carats by default, and
 * `runQueue.allowCaratsForTpRestore` is the switch that turns Carats off. Off must mean no Carat
 * is spent on any path and the queue stops with its own reason, not the generic out-of-TP text.
 */
@DisplayName("TP restore Carats setting")
class TpRestoreCaratOptInTest {
    private fun choose(toughness30: Boolean, starFruit: Boolean, allowCarats: Boolean, carats: Boolean = true) =
        CareerLaunchNavigator.chooseTpRestoreRow(toughness30, starFruit, allowCarats, carats)

    private fun mayFinish(pending: TpRestoreItem?, allowCarats: Boolean, enabled: Boolean = true, capReached: Boolean = false) =
        CareerLaunchNavigator.mayFinishRecoverTpQuantity(enabled, capReached, allowCarats, pending)

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

    private fun readRepoFile(relative: String): String {
        val file = findRepoFile(relative)
        assertNotNull(file, "$relative not found from the test working directory")
        return file!!.readText()
    }

    private val navigatorSource: String by lazy { readRepoFile("src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt") }

    private fun navigatorBody(signature: String): String {
        val start = navigatorSource.indexOf(signature)
        assertTrue(start >= 0, "missing: $signature")
        val end = navigatorSource.indexOf("\n    private fun ", start + signature.length)
        return navigatorSource.substring(start, if (end < 0) navigatorSource.length else end)
    }

    @Nested
    @DisplayName("Default")
    inner class Default {
        @Test
        fun `the app default allows Carats`() {
            val settings = readRepoFile("src/context/BotStateContext.tsx")
            val defaults = settings.substring(settings.indexOf("export const defaultSettings"))
            assertTrue(Regex("\\ballowCaratsForTpRestore: true,").containsMatchIn(defaults), "defaultSettings must set allowCaratsForTpRestore: true")
        }

        @Test
        fun `every Kotlin read falls back to allowing Carats`() {
            val reads = Regex("getBooleanSetting\\(\"runQueue\", \"allowCaratsForTpRestore\", (\\w+)\\)").findAll(navigatorSource).map { it.groupValues[1] }.toList()
            assertEquals(listOf("true", "true"), reads, "the picker and the quantity-popup handler must both default to true")
        }
    }

    @Nested
    @DisplayName("Carats allowed")
    inner class CaratsAllowed {
        @Test
        fun `Carats are the last rung once both items are gone`() {
            assertEquals(TpRestoreRowChoice.Use(TpRestoreItem.CARATS), choose(toughness30 = false, starFruit = false, allowCarats = true))
        }

        @Test
        fun `no row at all is still reported as no row`() {
            assertEquals(TpRestoreRowChoice.NoRow, choose(toughness30 = false, starFruit = false, allowCarats = true, carats = false))
        }

        @Test
        fun `the quantity handler finishes any Recover TP popup`() {
            assertTrue(mayFinish(TpRestoreItem.CARATS, allowCarats = true))
            assertTrue(mayFinish(null, allowCarats = true))
            assertTrue(mayFinish(TpRestoreItem.STAR_FRUIT, allowCarats = true))
        }

        @Test
        fun `every rung Max-fills, with the plus tap only as the Max-missing fallback`() {
            val picker = navigatorBody("private fun driveTpRestorePicker(")
            val plusTaps = Regex("\"tp_plus_one\"").findAll(picker).map { it.range.first }.toList()
            assertEquals(1, plusTaps.size, "only the Max-missing fallback taps plus")
            val fallback = picker.indexOf("if (!ButtonMax.click(iu)) {")
            assertTrue(fallback in 0 until plusTaps[0], "the plus tap must sit behind the Max-missing check")
            assertTrue(picker.indexOf("}", fallback) > plusTaps[0], "the plus tap must sit inside the Max-missing block")
            val retry = picker.indexOf("if (ButtonMax.find(iu).first != null) {")
            assertTrue(retry > fallback)
            val retryBlock = picker.substring(picker.indexOf('\n', retry), picker.indexOf("ButtonOk.click(iu)", retry))
            assertTrue(retryBlock.contains("ButtonMax.click(iu)"), "the still-open retry re-taps Max for every rung")
            assertFalse(retryBlock.contains("if ("), "the retry's Max tap is unconditional")
        }
    }

    @Nested
    @DisplayName("Carats off")
    inner class CaratsOff {
        @Test
        fun `a picker with only Carats left is declined, not used`() {
            assertEquals(TpRestoreRowChoice.CaratsNotAllowed, choose(toughness30 = false, starFruit = false, allowCarats = false))
        }

        @Test
        fun `the decline keeps its specific reason even when the Carats row was not looked for`() {
            assertEquals(TpRestoreRowChoice.CaratsNotAllowed, choose(toughness30 = false, starFruit = false, allowCarats = false, carats = false))
        }

        @Test
        fun `the persisted reason names the real cause, not the generic out-of-TP text`() {
            val reason = CareerLaunchNavigator.TP_RESTORE_CARATS_NOT_ALLOWED_REASON
            assertTrue(reason.contains("no Toughness 30 or Star Fruit left, and Carats are not allowed"), reason)
            assertFalse(reason.startsWith("Out of TP"), reason)
            assertFalse(reason.contains("enable \"Restore TP with items\""), reason)
        }

        @Test
        fun `the quantity handler cancels unknown and Carats popups`() {
            assertFalse(mayFinish(null, allowCarats = false))
            assertFalse(mayFinish(TpRestoreItem.CARATS, allowCarats = false))
        }

        @Test
        fun `the Carats row is only ever located while Carats are allowed`() {
            val finds = Regex("IconTpCarats\\.find").findAll(navigatorSource).toList()
            assertEquals(1, finds.size, "expected exactly one IconTpCarats.find call site")
            val at = finds[0].range.first
            val line = navigatorSource.substring(navigatorSource.lastIndexOf('\n', at) + 1, navigatorSource.indexOf('\n', at))
            assertTrue(line.contains("allowCarats &&"), line)
        }

        @Test
        fun `the picker routes the decline through the ladder seam`() {
            val picker = navigatorBody("private fun driveTpRestorePicker(")
            assertTrue(picker.contains("chooseTpRestoreRow("))
            val branch = picker.indexOf("TpRestoreRowChoice.CaratsNotAllowed ->")
            assertTrue(branch >= 0)
            val ret = picker.indexOf("return TpRestoreOutcome.CARATS_NOT_ALLOWED", branch)
            assertTrue(ret > branch && picker.indexOf("tp_use_button") > ret, "the decline must return before the Use tap")
        }

        @Test
        fun `the career-start restore fails with the specific reason`() {
            val dialog = navigatorBody("private fun handleTpRestoreDialog(")
            val branch = dialog.indexOf("TpRestoreOutcome.CARATS_NOT_ALLOWED ->")
            assertTrue(branch >= 0)
            assertTrue(dialog.indexOf("reason = TP_RESTORE_CARATS_NOT_ALLOWED_REASON", branch) > branch)
        }

        @Test
        fun `the spark reroll logs the specific reason and does not restore`() {
            val start = navigatorSource.indexOf("Out of TP for the reroll")
            assertTrue(start >= 0)
            val reroll = navigatorSource.substring(start, navigatorSource.indexOf("\n    }", start))
            val branch = reroll.indexOf("if (outcome == TpRestoreOutcome.CARATS_NOT_ALLOWED)")
            assertTrue(branch >= 0)
            assertTrue(reroll.indexOf("\$TP_RESTORE_CARATS_NOT_ALLOWED_REASON", branch) > branch)
            assertTrue(reroll.contains("return outcome == TpRestoreOutcome.RESTORED"))
        }

        @Test
        fun `the quantity handler consults the seam before any spend`() {
            val handler = navigatorBody("private fun handleRecoverTpQuantity(")
            val gate = handler.indexOf("mayFinishRecoverTpQuantity(restoreWithItems, capReached, allowCarats, pendingItem)")
            val firstSpend = handler.indexOf("ButtonMax.click")
            assertTrue(gate in 0 until firstSpend, "gate at $gate, first Max at $firstSpend")
        }
    }

    @Nested
    @DisplayName("Item behavior is unchanged")
    inner class Items {
        @Test
        fun `Toughness 30 comes first and Star Fruit second, whatever the Carats setting`() {
            for (allow in listOf(false, true)) {
                assertEquals(TpRestoreRowChoice.Use(TpRestoreItem.TOUGHNESS_30), choose(toughness30 = true, starFruit = true, allowCarats = allow))
                assertEquals(TpRestoreRowChoice.Use(TpRestoreItem.TOUGHNESS_30), choose(toughness30 = true, starFruit = false, allowCarats = allow))
                assertEquals(TpRestoreRowChoice.Use(TpRestoreItem.STAR_FRUIT), choose(toughness30 = false, starFruit = true, allowCarats = allow))
            }
        }

        @Test
        fun `a ladder-opened item popup is finished whatever the Carats setting`() {
            for (allow in listOf(false, true)) {
                assertTrue(mayFinish(TpRestoreItem.TOUGHNESS_30, allowCarats = allow))
                assertTrue(mayFinish(TpRestoreItem.STAR_FRUIT, allowCarats = allow))
            }
        }

        @Test
        fun `the item restore switch and the session cap still gate the popup`() {
            for (allow in listOf(false, true)) {
                assertFalse(mayFinish(TpRestoreItem.TOUGHNESS_30, allowCarats = allow, enabled = false))
                assertFalse(mayFinish(TpRestoreItem.TOUGHNESS_30, allowCarats = allow, capReached = true))
            }
        }
    }
}
