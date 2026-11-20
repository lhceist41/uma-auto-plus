package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The failure chance read path needs a live Game, so its wiring to the tested pure pieces (the facility column, the "%" rule,
 * the unread skip) is pinned with source guards. Each guard fails when that one piece is taken out of the read path.
 */
class FailureChanceWiringTest {
    private val training by lazy { kotlinFile("bot/Training.kt") }
    private val imageUtils by lazy { kotlinFile("utils/CustomImageUtils.kt") }

    private fun between(source: String, from: String, to: String): String {
        val start = source.indexOf(from)
        require(start >= 0) { "missing anchor: $from" }
        val end = source.indexOf(to, start)
        require(end > start) { "missing anchor: $to" }
        return source.substring(start, end)
    }

    @Test
    fun `each tile's label is searched only in that facility's column`() {
        val capture = between(training, "captureSettledFailureChanceFrame(", "val settleMs")
        assertTrue(
            capture.contains("LabelTrainingFailureChance.findImageWithBitmap(game.imageUtils, it, failureBubbleColumn(statName, CanonicalScreen.width, CanonicalScreen.height))"),
            "the per-tile locate must search the selected facility's column",
        )
    }

    @Test
    fun `the first read and its recovery retry search Speed's column`() {
        assertTrue(
            training.contains("val gateRegion = if (singleTraining) null else failureBubbleColumn(StatName.SPEED, CanonicalScreen.width, CanonicalScreen.height)"),
            "the first read must search Speed's column outside single-training mode",
        )
        assertTrue(training.contains("var failureChance: Int = game.imageUtils.findTrainingFailureChance(tries = 3, region = gateRegion)"), "the first read must use that region")
        val retry = between(training, "fun recoverAndRetryFailureChance(): Int {", "return retried")
        assertTrue(
            retry.contains("game.imageUtils.findTrainingFailureChance(tries = 3, region = failureBubbleColumn(StatName.SPEED, CanonicalScreen.width, CanonicalScreen.height))"),
            "the recovery retry must search Speed's column",
        )
        assertEquals(3, Regex("""findTrainingFailureChance\(""").findAll(training).count(), "a new read site needs its own region decision")
    }

    @Test
    fun `every OCR read of the bubble requires the percent sign`() {
        val detect = between(imageUtils, "fun detectTrainingFailureChance(", "val tries: Int = maxOf(1, tries)")
        assertTrue(detect.contains("val value = parseFailureChance(detectedText, requirePercent = true)"), "the read must reject text without a % sign")
        assertTrue(detect.contains("return value ?: INVALID_FAILURE_CHANCE"), "the value returned is the one that required the sign")
    }

    @Test
    fun `a single-training tile with an unread failure chance is left out and goes no further`() {
        val skip = between(training, "                    if (result.failureChance == -1) {", "                    // For Risky Training")
        val record = skip.indexOf("skippedTrainingMap[statName] = result.toSkippedOption(SKIP_UNREADABLE_FAILURE_CHANCE)")
        val leave = skip.indexOf("continue", record)
        assertTrue(record >= 0 && leave > record, "the unread tile must be recorded as skipped and then left")
        assertTrue(skip.substring(leave).trimStart().removePrefix("continue").trim().startsWith("}"), "nothing may run between the skip and the end of its block")
    }

    private fun kotlinFile(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            for (root in listOf("src/main/java", "app/src/main/java")) {
                val candidate = File(dir, "$root/com/steve1316/uma_android_automation/$relative")
                if (candidate.isFile) return candidate.readText().replace("\r\n", "\n")
            }
            dir = dir?.parentFile
        }
        error("missing $relative")
    }
}
