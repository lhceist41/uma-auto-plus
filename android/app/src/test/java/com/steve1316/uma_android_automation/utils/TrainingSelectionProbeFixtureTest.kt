package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins [TrainingSelectionProbe] against live captures of the in-career Training selection screen
 * (src/test/resources/fixtures/trainingselection, see PROVENANCE.md, and the Grand Concert training
 * fixtures) and every other screen fixture as a negative, decoded by [FixturePng] and read through the
 * same [SparkPixelSampler] the runtime wraps a Bitmap in.
 */
@DisplayName("Training selection pixel probe")
class TrainingSelectionProbeFixtureTest {
    private val positives =
        listOf("trainingselection" to "training_selection", "trainingselection" to "training_selection_other_training") +
            listOf("training_guts_before", "training_panel_gain_row3_bg", "training_panel_gain_single_digit", "training_panel_rainbow", "training_panel_vi_gain", "training_panel_hidden")
                .map { "grandconcert" to it }

    private fun image(dir: String, name: String): FixturePng {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/$dir/$name.png")) { "missing fixture $dir/$name.png" }
        return stream.use { FixturePng.read(it) }
    }

    private fun isTraining(img: FixturePng) = TrainingSelectionProbe.isTrainingSelection(SparkPixelSampler { x, y -> img.getRGB(x, y) }, img.width, img.height)

    @Test
    @DisplayName("recognizes the screen in the captured career and across Grand Concert skins")
    fun recognizesTrainingSelection() {
        for ((dir, name) in positives) assertTrue(isTraining(image(dir, name)), "$dir/$name")
    }

    @Test
    @DisplayName("needs both the Training header and the Back pill")
    fun needsHeaderAndBackPill() {
        // The training result keeps the "Training" header but has no Back; the Race List has Back under another header.
        assertFalse(isTraining(image("trainingselection", "training_result_cutscene")))
        assertFalse(isTraining(image("trainingselection", "race_list")))
        assertFalse(isTraining(image("grandconcert", "song_list")), "Grand Concert Lessons: Back under another header")
    }

    @Test
    @DisplayName("does not fire on the training menu, the launch Quick Mode prompt or the concert-pending screen")
    fun ignoresTheScreensItMustNotBackOutOf() {
        assertFalse(isTraining(image("trainingselection", "career_menu_after_back")))
        for (name in listOf("career_main_turn1", "career_after_training", "quickmode_dont_use", "quickmode_shorten_all", "concert_pending")) {
            assertFalse(isTraining(image("grandconcert", name)), name)
        }
    }

    @Test
    @DisplayName("does not fire on any other screen fixture")
    fun ignoresEveryOtherFixture() {
        val root = File(requireNotNull(javaClass.getResource("/fixtures")) { "missing fixtures" }.toURI())
        val positiveFiles = positives.map { (dir, name) -> "$dir/$name.png" }.toSet()
        val others = root.walkTopDown().filter { it.isFile && it.extension == "png" }.filter { "${it.parentFile.name}/${it.name}" !in positiveFiles }.toList()
        assertTrue(others.size >= 70, "found ${others.size} fixtures")
        for (file in others) {
            val img = file.inputStream().use { FixturePng.read(it) }
            assertFalse(isTraining(img), file.path)
        }
    }

    @Test
    @DisplayName("rejects a blank surface and any other surface size")
    fun ignoresBlankAndOtherSizes() {
        assertFalse(TrainingSelectionProbe.isTrainingSelection(SparkPixelSampler { _, _ -> 0xFFFFFFFF.toInt() }, 1080, 1920))
        assertFalse(TrainingSelectionProbe.isTrainingSelection(SparkPixelSampler { _, _ -> 0xFF000000.toInt() }, 1080, 1920))
        val screen = image("trainingselection", "training_selection")
        val sampler = SparkPixelSampler { x, y -> screen.getRGB(x, y) }
        assertFalse(TrainingSelectionProbe.isTrainingSelection(sampler, 1080, 2340))
        assertFalse(TrainingSelectionProbe.isTrainingSelection(sampler, 1080, 1840))
    }
}
