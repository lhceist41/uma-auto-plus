package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins [TitleScreenProbe] against live captures of the game's title screen and of the screens around
 * it (src/test/resources/fixtures/titlescreen, see PROVENANCE.md), plus every other screen fixture as
 * a negative. The PNGs are decoded by [FixturePng] and read through the same [SparkPixelSampler] the
 * runtime wraps a Bitmap in.
 */
@DisplayName("Title-screen pixel probe")
class TitleScreenProbeFixtureTest {
    private val titles = listOf("title_screen", "title_screen_other_background", "title_logging_in")

    private fun image(dir: String, name: String): FixturePng {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/$dir/$name.png")) { "missing fixture $dir/$name.png" }
        return stream.use { FixturePng.read(it) }
    }

    private fun isTitle(img: FixturePng) = TitleScreenProbe.isTitleScreen(SparkPixelSampler { x, y -> img.getRGB(x, y) }, img.width, img.height)

    @Test
    @DisplayName("recognizes the title screen over different background frames, and while it logs in")
    fun recognizesTitleScreen() {
        for (name in titles) assertTrue(isTitle(image("titlescreen", name)), name)
    }

    @Test
    @DisplayName("does not fire on the Session Error, the loading screen or the Notices over Home")
    fun ignoresScreensAroundTheTitle() {
        for (name in listOf("session_error", "now_loading", "notices_over_home")) assertFalse(isTitle(image("titlescreen", name)), name)
    }

    @Test
    @DisplayName("does not fire on any other screen fixture")
    fun ignoresEveryOtherFixture() {
        val root = File(requireNotNull(javaClass.getResource("/fixtures")) { "missing fixtures" }.toURI())
        val others = root.walkTopDown().filter { it.isFile && it.extension == "png" && it.parentFile.name != "titlescreen" }.toList()
        assertTrue(others.size >= 20, "found ${others.size} fixtures")
        var read = 0
        for (file in others) {
            val img = runCatching { file.inputStream().use { FixturePng.read(it) } }.getOrNull() ?: continue
            read++
            assertFalse(isTitle(img), file.path)
        }
        assertTrue(read >= 20, "decoded $read fixtures")
    }

    @Test
    @DisplayName("rejects a blank surface and any other surface size")
    fun ignoresBlankAndOtherSizes() {
        assertFalse(TitleScreenProbe.isTitleScreen(SparkPixelSampler { _, _ -> 0xFFFFFFFF.toInt() }, 1080, 1920))
        assertFalse(TitleScreenProbe.isTitleScreen(SparkPixelSampler { _, _ -> 0xFF000000.toInt() }, 1080, 1920))
        val title = image("titlescreen", "title_screen")
        val sampler = SparkPixelSampler { x, y -> title.getRGB(x, y) }
        assertFalse(TitleScreenProbe.isTitleScreen(sampler, 1080, 1840))
        assertFalse(TitleScreenProbe.isTitleScreen(sampler, 1440, 2560))
    }
}
