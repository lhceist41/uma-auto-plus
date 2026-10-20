package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The support type count badges: five empty reads (what every launch produced while the read ran on the Final Confirmation
 * screen, which has no badges) are null, never five zeros; a read off the Support Formation screen gives the counts.
 */
@DisplayName("Support deck type counts")
class DeckTypeCountsTest {
    @Test
    @DisplayName("five empty reads are null, not five zeros")
    fun allEmptyIsNull() {
        assertNull(parseDeckTypeCounts(listOf("", "", "", "", "")))
        assertNull(parseDeckTypeCounts(listOf("x", " ", "", "xx", "")))
    }

    @Test
    @DisplayName("a read with absent badges keeps those types at 0 beside the read counts")
    fun absentBadgesAreZero() {
        // Deck 7 on the Support Formation fixture: Speed x4, Power x1, Wit x1, no Stamina or Guts badge.
        assertArrayEquals(intArrayOf(4, 0, 1, 0, 1), parseDeckTypeCounts(listOf("x4", "", "x1", "", "x1")))
        assertArrayEquals(intArrayOf(1, 0, 0, 0, 5), parseDeckTypeCounts(listOf("11", "", "", "", "x5")))
        assertArrayEquals(intArrayOf(6, 0, 0, 0, 0), parseDeckTypeCounts(listOf("x7", "", "", "", "")))
    }

    @Test
    @DisplayName("the read happens at the Support Formation Start Career taps only, and the record takes that read")
    fun wiring() {
        val src = source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
        val formation = src.substringAfter("private fun handleSupportDeckScreen(").substringBefore("private fun handleBuildAwareLaunch(")
        val legacyTap = formation.indexOf("readDeckTypeCountsOnce { bitmap }")
        assertTrue(legacyTap > 0 && legacyTap < formation.indexOf("ButtonStartCareer.click(iu, sourceBitmap = bitmap)", legacyTap), "read before the legacy Start Career tap")
        val buildAware = src.substringAfter("private fun handleBuildAwareLaunch(").substringBefore("private fun runBorrowStep(")
        val buildTap = buildAware.indexOf("readDeckTypeCountsOnce { iu.getSourceBitmap() }")
        assertTrue(buildTap > 0 && buildTap < buildAware.indexOf("ButtonStartCareer.click(iu)", buildTap), "read before the build-aware Start Career tap")
        val preRun = src.substringAfter("private fun handlePreRunConfirmation(): TransitionResult {").substringBefore("private fun verifyNormalCareerMode(")
        assertFalse(preRun.contains("checkDeckConcentration(") || preRun.contains("readDeckTypeCounts"), "the Final Confirmation screen has no badges to read")
        assertTrue(preRun.contains("buildLaunchDeckObservation(deckTypeCountsAtStart)"))
        assertEquals(2, Regex("readDeckTypeCountsOnce \\{").findAll(src).count())
        val once = src.substringAfter("private fun readDeckTypeCountsOnce(").substringBefore("private fun checkDeckConcentration(")
        assertTrue(once.contains("if (deckTypeCountsAttempted || !SettingsHelper.getBooleanSetting(\"training\", \"enableDeckConcentrationCheck\", false)) return"), "one read per launch, none when the check is off")
        val check = src.substringAfter("private fun checkDeckConcentration(bitmap: Bitmap): IntArray? {").substringBefore("val breakdown")
        assertTrue(check.contains("if (counts == null) {") && check.contains("return null"), "an unread badge row skips the warning")
        assertTrue(src.contains("deckTypeCountsAttempted -> \"read failed: no count badge read on the Support Formation screen\""))
        assertEquals(2, Regex("deckTypeCountsAttempted = false").findAll(src).count(), "reset per navigate() and per borrow replacement, nowhere else")
        val reset = src.indexOf("deckTypeCountsAttempted = false")
        // The per-launch latches are reset in navigate(), beside the deck verification latches.
        assertTrue(reset > src.indexOf("fun navigate(") && reset < src.indexOf("lastBuildAwareLaunchResult = null"), "reset per navigate()")
    }

    @Test
    @DisplayName("a borrow replacement after a refused Start Career drops the read of the replaced deck, so the next tap reads again")
    fun replacementRereads() {
        val src = source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
        val replacement = src.substringAfter("private fun performBorrowReplacement(").substringBefore("private fun ")
        val invalidate = replacement.indexOf("deckTypeCountsAttempted = false")
        assertTrue(invalidate > 0 && replacement.indexOf("deckTypeCountsAtStart = null", invalidate) in invalidate until invalidate + 120, "both latches drop together")
        // Dropped on every replacement, before any picker reopen, after the bounded-attempts return.
        assertTrue(invalidate < replacement.indexOf("trySmartBorrowPick("), "before the replacement pick")
        assertTrue(replacement.lastIndexOf("return TransitionResult.Failed(", invalidate) in 0 until invalidate, "after the replacement budget check")
        // The toast-detected trainee conflict routes into the same replacement, so it is covered too.
        val toast = src.substringAfter("if (isTraineeConflictMessage(toast)) {").substringBefore("}")
        assertTrue(toast.contains("forceBorrowReplacement = true"))
        assertTrue(src.substringAfter("private fun runBorrowStep(").substringBefore("private fun ").contains("performBorrowReplacement(duplicateDeckPill, traineeDeckPill, viaMessage)"))
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val file = File(dir, relative)
            if (file.isFile) return file.readText().replace("\r\n", "\n")
            dir = dir.parentFile
        }
        error("source file not found: $relative")
    }
}
