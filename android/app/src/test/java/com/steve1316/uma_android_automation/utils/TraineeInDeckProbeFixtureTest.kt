package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins [TraineeInDeckProbe] against live Support Formation captures where a deck card is of the
 * trainee's own character (src/test/resources/fixtures/traineeindeck, see PROVENANCE.md), and every
 * other screen fixture as a negative, decoded by [FixturePng] and read through the same
 * [SparkPixelSampler] the runtime wraps a Bitmap in.
 */
@DisplayName("Trainee-in-deck pixel probe")
class TraineeInDeckProbeFixtureTest {
    private val positives = mapOf("trainee_in_deck_slot2" to listOf(2), "trainee_in_deck_slot4" to listOf(4), "trainee_in_deck_before_borrow" to listOf(2))

    private fun image(name: String): FixturePng {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/traineeindeck/$name.png")) { "missing fixture $name.png" }
        return stream.use { FixturePng.read(it) }
    }

    private fun slots(img: FixturePng) = TraineeInDeckProbe.taggedSlots(SparkPixelSampler { x, y -> img.getRGB(x, y) }, img.width, img.height)

    @Test
    @DisplayName("finds the tagged card's slot in both captured conflicts, before and after the borrow")
    fun findsTheTaggedSlot() {
        for ((name, expected) in positives) assertEquals(expected, slots(image(name)), name)
    }

    @Test
    @DisplayName("reads the third column, where no conflict was captured, from the real tag moved one column right")
    fun readsTheThirdColumn() {
        // The three columns sit symmetrically about the screen centre (tag centres 222, 540, 858), measured
        // on columns one and two; the third is that layout, not a live capture.
        val screen = image("trainee_in_deck_slot2")
        val moved = SparkPixelSampler { x, y -> if (x >= 700 && y in 400..460) screen.getRGB(x - 318, y) else screen.getRGB(x, y) }
        assertEquals(listOf(2, 3), TraineeInDeckProbe.taggedSlots(moved, 1080, 1920))
    }

    @Test
    @DisplayName("reads nothing on the same deck with another trainee, or on the Borrow Card picker's red tags")
    fun ignoresAValidDeckAndThePicker() {
        assertEquals(emptyList<Int>(), slots(image("support_formation_valid")))
        assertEquals(emptyList<Int>(), slots(image("borrow_picker_duplicate_support")))
    }

    @Test
    @DisplayName("does not fire on any other screen fixture")
    fun ignoresEveryOtherFixture() {
        val root = File(requireNotNull(javaClass.getResource("/fixtures")) { "missing fixtures" }.toURI())
        val positiveFiles = positives.keys.map { "traineeindeck/$it.png" }.toSet()
        val others = root.walkTopDown().filter { it.isFile && it.extension == "png" }.filter { "${it.parentFile.name}/${it.name}" !in positiveFiles }.toList()
        assertTrue(others.size >= 70, "found ${others.size} fixtures")
        for (file in others) {
            val img = file.inputStream().use { FixturePng.read(it) }
            assertEquals(emptyList<Int>(), slots(img), file.path)
        }
    }

    @Test
    @DisplayName("needs both red edges and the white lettering")
    fun needsTheWholeTag() {
        val tagRed = 0xFFFF4F31.toInt()
        assertEquals(emptyList<Int>(), TraineeInDeckProbe.taggedSlots(SparkPixelSampler { _, _ -> tagRed }, 1080, 1920), "solid red, no lettering")
        assertEquals(emptyList<Int>(), TraineeInDeckProbe.taggedSlots(SparkPixelSampler { _, _ -> 0xFFFFFFFF.toInt() }, 1080, 1920), "solid white")
        val screen = image("trainee_in_deck_slot2")
        // Slot 2's tag spans y 408 to 446: blank its lower edge and the tag no longer reads.
        val noLowerEdge = SparkPixelSampler { x, y -> if (y in 438..444) 0xFFFFFFFF.toInt() else screen.getRGB(x, y) }
        assertEquals(emptyList<Int>(), TraineeInDeckProbe.taggedSlots(noLowerEdge, 1080, 1920))
    }

    @Test
    @DisplayName("reads nothing on any other surface size")
    fun ignoresOtherSizes() {
        val screen = image("trainee_in_deck_slot2")
        val sampler = SparkPixelSampler { x, y -> screen.getRGB(x, y) }
        assertEquals(emptyList<Int>(), TraineeInDeckProbe.taggedSlots(sampler, 1080, 2340))
        assertEquals(emptyList<Int>(), TraineeInDeckProbe.taggedSlots(sampler, 720, 1280))
    }
}
