package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * On the last page of a paged help dialog the misc Back only turns the page back, and the Next branch turns it forward again. The misc
 * checks close such a dialog instead, and a Next/Back ping-pong on any other screen stops in bounded time.
 */
@DisplayName("Paged help dialog and the Next/Back ping-pong")
class PagedHelpDialogTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).exists()) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private fun sampler(png: FixturePng) = SparkPixelSampler { x, y -> png.getRGB(x, y) }

    private fun fixture(resource: String): FixturePng = requireNotNull(javaClass.getResourceAsStream(resource)) { "missing fixture $resource" }.use { FixturePng.read(it) }

    private val lastPage by lazy { fixture("/fixtures/grandconcert/paged_help_last_page.png") }

    @Nested
    @DisplayName("the probe")
    inner class Probe {
        @Test
        fun `the last page of the Lessons help is a paged help dialog`() {
            assertTrue(pagedHelpDialogPresent(sampler(lastPage)))
        }

        @Test
        fun `the Umamusume Details dialog is not`() {
            assertFalse(pagedHelpDialogPresent(sampler(fixture("/fixtures/sparks/umamusume_details.png"))))
        }

        @Test
        fun `no other full-screen fixture is`() {
            val root = repoFile("android/app/src/test/resources/fixtures")
            var checked = 0
            root.walkTopDown().filter { it.isFile && it.extension == "png" && it.name != "paged_help_last_page.png" }.forEach { file ->
                val png = runCatching { file.inputStream().use { FixturePng.read(it) } }.getOrNull() ?: return@forEach
                if (png.width != 1080 || png.height != 1920) return@forEach
                checked++
                assertFalse(pagedHelpDialogPresent(sampler(png)), file.relativeTo(root).path)
            }
            assertTrue(checked >= 50, "only $checked full-screen fixtures decoded")
        }

        @Test
        fun `the Close tap lands on the white Close button`() {
            val p = lastPage.getRGB(PagedHelpGeometry.CLOSE_X - 150, PagedHelpGeometry.CLOSE_Y)
            assertTrue(minOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF) >= 215)
        }
    }

    @Nested
    @DisplayName("the Next/Back streak")
    inner class Streak {
        private fun run(steps: List<Boolean>): List<Int> {
            var streak = 0
            var previous: Boolean? = null
            return steps.map { next ->
                streak = miscNextBackSwapStreak(streak, previous, next)
                previous = next
                streak
            }
        }

        @Test
        fun `alternating Next and Back counts every turn`() {
            val streaks = run(List(13) { it % 2 == 0 })
            assertEquals((0..12).toList(), streaks)
        }

        @Test
        fun `a long Next-only chain never counts`() {
            assertTrue(run(List(100) { true }).all { it == 0 })
        }

        @Test
        fun `a repeated step ends the streak`() {
            assertEquals(listOf(0, 1, 2, 0, 1), run(listOf(true, false, true, true, false)))
        }
    }

    @Nested
    @DisplayName("misc-check wiring")
    inner class Wiring {
        private val campaign by lazy {
            repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt").readText().replace("\r\n", "\n")
        }

        private val misc by lazy {
            val start = campaign.indexOf("open fun performMiscChecks(): Boolean {")
            campaign.substring(start, campaign.indexOf("open fun handleMainScreen(): Boolean {", start))
        }

        @Test
        fun `a paged help dialog is closed before the Back branch can turn its page`() {
            val help = misc.indexOf("pagedHelpDialogPresent(SparkPixelSampler { x, y -> sourceBitmap.getPixel(x, y) }.onScreen(ScreenBand.DIALOG, sourceBitmap.width, sourceBitmap.height))")
            val close = misc.indexOf("gameY(PagedHelpGeometry.CLOSE_Y.toDouble(), ScreenBand.DIALOG, sourceBitmap.width, sourceBitmap.height),\n                \"paged_help_close\",")
            val back = misc.indexOf("} else if (ButtonBack.click(game.imageUtils, sourceBitmap = sourceBitmap)) {")
            assertTrue(help >= 0 && close > help && back > close, "probe, then Close, then the Back branch")
            assertTrue(misc.contains("if (pagedHelpCloseTaps > maxPagedHelpCloseTaps) {"), "repeated Close taps are bounded")
        }

        @Test
        fun `both Next and Back feed the streak, which stops at its bound`() {
            assertEquals(2, Regex("recordMiscNextOrBack\\(nextNow = (true|false)\\)").findAll(misc).count())
            assertTrue(misc.contains("if (miscNextBackSwaps >= maxMiscNextBackSwaps) {"))
            assertTrue(misc.contains("throw InterruptedException(\n                \"Bot alternated Next and Back"))
        }

        @Test
        fun `a tick that took no misc step resets the streaks before the early-returning dialog and main-screen ticks`() {
            val process = campaign.substring(campaign.indexOf("override fun process(): TaskResult? {"))
            val reset = process.indexOf("if (!bMiscStepTakenLastTick) {")
            assertTrue(reset >= 0 && reset < process.indexOf("if (tryHandleAllDialogs()) {") && reset < process.indexOf("if (handleMainScreen()) {"))
        }
    }
}
