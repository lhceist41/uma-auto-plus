package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.FixturePng
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertLessonConfirmationPresent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A Grand Concert lesson Learn dialog carries the skill list's "Confirmation" title and its Cancel and
 * Learn buttons, so the shared dialog handler resolves it as skill_list_confirmation. Only the lesson
 * spend loop may press its Learn: the shared handler cancels a lesson dialog and still learns on a real
 * skill confirmation. The probe is pinned on the captures; the branch in DialogHandler by source guards.
 */
@DisplayName("A lesson Learn dialog is never learned by the generic dialog handler")
class LessonLearnGuardTest {
    private fun sampler(resource: String): SparkPixelSampler {
        val png = requireNotNull(javaClass.getResourceAsStream(resource)) { "missing fixture $resource" }.use { FixturePng.read(it) }
        return SparkPixelSampler { x, y -> png.getRGB(x, y) }
    }

    private fun grandConcert(name: String) = sampler("/fixtures/grandconcert/$name.png")

    @Nested
    @DisplayName("the lesson-dialog probe")
    inner class Probe {
        @Test
        fun `the Learn and Schedule dialogs read as lesson dialogs`() {
            for (name in listOf("learn_confirm_technique", "schedule_confirm_technique", "schedule_confirm_song")) {
                assertTrue(grandConcertLessonConfirmationPresent(grandConcert(name)), name)
            }
        }

        @Test
        fun `the skill list's Learn confirmation does not, though it has the same header`() {
            assertFalse(grandConcertLessonConfirmationPresent(sampler("/fixtures/skilllist/skill_list_confirmation_top.png")))
        }

        @Test
        fun `no other Grand Concert capture reads as a lesson dialog`() {
            val positives = setOf("learn_confirm_technique", "schedule_confirm_technique", "schedule_confirm_song")
            val folder = File(requireNotNull(javaClass.getResource("/fixtures/grandconcert")) { "missing fixtures/grandconcert" }.toURI())
            val others = folder.listFiles { f -> f.extension == "png" }!!.map { it.nameWithoutExtension }.filter { it !in positives }.sorted()
            assertTrue(others.size >= 29, "the fixture folder was not enumerated: ${others.size} captures")
            for (name in others) {
                assertFalse(grandConcertLessonConfirmationPresent(grandConcert(name)), name)
            }
        }
    }

    @Nested
    @DisplayName("the shared skill_list_confirmation branch")
    inner class Branch {
        private val handler by lazy { source("bot/DialogHandler.kt") }

        private val branch by lazy {
            handler.substringAfter("            \"skill_list_confirmation\" -> {").substringBefore("\n            \"skill_list_confirm_exit\" -> {")
        }

        @Test
        fun `a lesson dialog is cancelled before any Learn press, and a skill dialog still learns`() {
            val guard = branch.indexOf("if (grandConcertLessonConfirmationShowing()) {")
            val learn = branch.indexOf("dialog.ok(game.imageUtils)")
            assertTrue(guard in 0 until learn, "the check comes before the Learn press")
            val guarded = branch.substring(guard, learn)
            assertTrue(guarded.contains("dialog.close(game.imageUtils)"), "close is the dialog's first button, Cancel")
            assertTrue(guarded.contains("return DialogHandlerResult.Handled(dialog)"))
            assertFalse(guarded.contains(".ok("))
            assertTrue(guarded.contains("[GRAND_CONCERT]"))
        }

        @Test
        fun `the check reads only Grand Concert full-size frames`() {
            val check = handler.substringAfter("private fun grandConcertLessonConfirmationShowing(): Boolean {").substringBefore("\n    }\n")
            assertTrue(check.contains("if (!GrandConcert.isGrandConcert(game.scenario)) return false"))
            assertTrue(check.contains("if (bitmap.width != 1080 || bitmap.height != 1920) return false"))
            assertTrue(check.contains("grandConcertLessonConfirmationPresent("))
        }

        @Test
        fun `Cancel is the skill confirmation's close button`() {
            val dialog = source("components/Dialog.kt").substringAfter("object DialogSkillListConfirmation : DialogInterface {").substringBefore("\n}\n")
            assertTrue(dialog.contains("override val closeButton = null"))
            assertTrue(dialog.indexOf("ButtonCancel,") < dialog.indexOf("ButtonLearn,"), "Cancel is the first button, which close() presses")
        }
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
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
