package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.FixturePng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Tutorial event: the Grand Concert confirm")
class TutorialConfirmTest {
    @Nested
    @DisplayName("the 2-option choice")
    inner class Choice {
        @Test
        @DisplayName("the Grand Concert \"Are you sure?\" takes Yep!, also on partial reads")
        fun grandConcertConfirm() {
            assertEquals(0, TrainingEvent.tutorialTwoOptionChoice("Yep!", "On second thought...", false))
            assertEquals(0, TrainingEvent.tutorialTwoOptionChoice("Yep", "", false))
            assertEquals(0, TrainingEvent.tutorialTwoOptionChoice("", "On second thought", false))
            assertEquals(0, TrainingEvent.tutorialTwoOptionChoice("Yep!", "On second thought...", true))
        }

        @Test
        @DisplayName("any other or unreadable pair keeps the last option, as before")
        fun otherTutorialsUnchanged() {
            for ((first, last) in listOf("" to "", "Got it!" to "Tell me more", "Yes" to "No", "That's all, thank you." to "Lessons")) {
                assertEquals(1, TrainingEvent.tutorialTwoOptionChoice(first, last, false), "$first / $last")
            }
        }

        @Test
        @DisplayName("a 2-option Tutorial answered with its last option already this turn takes the first, whatever the read")
        fun repeatedThisTurn() {
            assertEquals(0, TrainingEvent.tutorialTwoOptionChoice("", "", true))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val source by lazy {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            var file: File? = null
            repeat(8) {
                val f = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/bot/TrainingEvent.kt").takeIf { it.isFile }
                    ?: File(dir, "src/main/java/com/steve1316/uma_android_automation/bot/TrainingEvent.kt").takeIf { it.isFile }
                if (file == null && f != null) file = f
                dir = dir?.parentFile
            }
            requireNotNull(file).readText().replace("\r\n", "\n")
        }

        private val branches by lazy {
            val start = source.indexOf("            when (tutorialOptionCount) {")
            source.substring(start, source.indexOf("\n            specialEventHandled = true", start))
        }

        @Test
        @DisplayName("only the 2-option branch changed; the repeat guard is Grand Concert only")
        fun branches() {
            val two = branches.substringAfter("                2 -> {").substringBefore("\n                5 -> {")
            assertTrue(two.contains("optionSelected = tutorialTwoOptionChoice(texts[0], texts[1], repeated)"))
            assertTrue(two.contains("val repeated = GrandConcertScenario.matches(game.scenario) && tutorialLastOptionTurn == turn"))
            assertTrue(two.contains("if (optionSelected == 1) {\n                        tutorialLastOptionTurn = turn"))
            val five = branches.substringAfter("                5 -> {").substringBefore("\n                else -> {")
            assertTrue(five.contains("optionSelected = 4"))
            val other = branches.substringAfter("                else -> {")
            assertTrue(other.contains("optionSelected = if (tutorialOptionCount > 0) tutorialOptionCount - 1 else 0"))
        }
    }

    @Nested
    @DisplayName("the option-text crop on the phone frames")
    inner class PhoneCrop {
        private fun image(name: String): FixturePng =
            requireNotNull(javaClass.getResourceAsStream("/fixtures/tallphone/$name.png")) { "missing $name.png" }.use { FixturePng.read(it) }

        /** Brown option-label ink inside the crop the bot reads beside an icon at ([x], [y]): relX(x, 45), relY(y, -30) = -36 on 2316, 800x55. */
        private fun inkColumns(
            img: FixturePng,
            x: Int,
            y: Int,
        ): Int {
            val top = y + (-30 * 2316 / 1920.0).toInt()
            return (x + 45 until x + 45 + 800).count { cx ->
                (top until top + 55).any { cy ->
                    val p = img.getRGB(cx, cy)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    r in 90..170 && g in 40..110 && b < 70 && r - b >= 60
                }
            }
        }

        @Test
        @DisplayName("the confirm's two crops hold \"Yep!\" (short) and \"On second thought...\" (long)")
        fun confirmRows() {
            // Icon centres the live matcher reported on this screen.
            val img = image("tutorial_confirm")
            val yep = inkColumns(img, 80, 1389)
            val secondThought = inkColumns(img, 80, 1587)
            assertTrue(yep in 40..140, "Yep! ink columns $yep")
            assertTrue(secondThought in 200..400, "On second thought... ink columns $secondThought")
        }

        @Test
        @DisplayName("the menu's last crop holds \"That's all, thank you.\"")
        fun menuLastRow() {
            val ink = inkColumns(image("tutorial_menu"), 80, 1587)
            assertTrue(ink in 200..400, "That's all, thank you. ink columns $ink")
        }
    }
}
