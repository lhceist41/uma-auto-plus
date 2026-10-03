package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonFollow
import com.steve1316.uma_android_automation.components.ButtonInterface
import com.steve1316.uma_android_automation.components.DialogFollowTrainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** The in-career "Follow Trainer" dialog, including the Close-only variant shown when that trainer is at maximum followers. */
@DisplayName("Follow Trainer in a career")
class FollowTrainerDialogTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val path = "android/app/src/main/java/com/steve1316/uma_android_automation/$relative"
        repeat(8) {
            listOf(File(dir, path), File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")).firstOrNull { it.isFile }?.let { return it.readText().replace("\r\n", "\n") }
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }

    private val branch: String by lazy {
        val handler = source("bot/DialogHandler.kt")
        val start = handler.indexOf("\"follow_trainer\" -> {")
        assertTrue(start >= 0, "missing follow_trainer branch")
        handler.substring(start, handler.indexOf("\n            }\n", start))
    }

    /** The branch taps the first dismiss button that is on screen (firstOrNull { it.click(...) }). */
    private fun tapped(onScreen: Set<ButtonInterface>) = DialogFollowTrainer.dismissButtons.firstOrNull { it in onScreen }

    @Test
    fun `Cancel and Follow taps Cancel, Close only taps Close, Follow only taps nothing`() {
        assertEquals(ButtonCancel, tapped(setOf(ButtonCancel, ButtonFollow)))
        assertEquals(ButtonClose, tapped(setOf(ButtonClose)))
        assertNull(tapped(setOf(ButtonFollow)))
        assertNull(tapped(emptySet()))
    }

    @Test
    fun `the branch taps only the dismiss buttons, first match wins, and never Follow`() {
        assertEquals(listOf(ButtonCancel, ButtonClose), DialogFollowTrainer.dismissButtons)
        assertTrue(branch.contains("DialogFollowTrainer.dismissButtons.firstOrNull { it.click(game.imageUtils) }"), branch)
        for (banned in listOf("ButtonFollow", ".ok(", "okButton", "dialog.close(")) assertFalse(branch.contains(banned), banned)
    }

    @Test
    fun `between runs the same dialog is dismissed with the same buttons in the same order`() {
        assertEquals(DialogFollowTrainer.dismissButtons, BetweenRunDialogStep.DismissFollowTrainer.taps)
    }
}
