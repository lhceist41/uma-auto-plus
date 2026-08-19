package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the post-career event REWARDS advance (2026-08-19, "Days Flying By" event): the summary
 * advances via a "Next" button at the bottom, not a body tap. The original centre-body tap never
 * advanced it and the queue stuck for 15 iterations after run 2's completed career. The handler
 * must click Next (template first, fixed bottom-centre fallback), and must not reach for a centre
 * (0.5, 0.5) tap.
 */
@DisplayName("Event rewards summary advance")
class EventRewardsSummaryAdvanceTest {
    private val nav = sourceFile("CareerLaunchNavigator.kt").readText()

    private fun rewardsBlock(): String {
        val start = nav.indexOf("if (isEventPointsRewardsScreen(bitmap)) {")
        require(start >= 0) { "event rewards summary handler not found" }
        val end = nav.indexOf("ButtonSkillListFullStats.check", start)
        require(end > start) { "handler end marker not found" }
        return nav.substring(start, end)
    }

    @Test
    fun `the event rewards summary advances via Next, not a centre body tap`() {
        val block = rewardsBlock()
        assertTrue(block.contains("ButtonNext.click"), "the handler must click the Next button")
        assertTrue(block.contains("eventRewardsNextFraction"), "the coordinate fallback must target the bottom Next position")
        assertFalse(
            block.contains("bitmap.height * 0.5"),
            "the handler must not tap the centre body (0.5 height) - that never advanced the event REWARDS screen",
        )
    }

    @Test
    fun `the Next fallback position is the bottom button row`() {
        val match = Regex("eventRewardsNextFraction\\s*=\\s*floatArrayOf\\(([^)]*)\\)").find(nav)
        require(match != null) { "eventRewardsNextFraction declaration not found" }
        val values = match.groupValues[1].split(",").map { it.trim().removeSuffix("f").toFloat() }
        assertTrue(values.size == 2, "expected [x, y] fractions")
        assertTrue(values[0] in 0.4f..0.6f, "Next button is horizontally centred, got ${values[0]}")
        assertTrue(values[1] > 0.85f, "Next button sits in the bottom button row, got ${values[1]}")
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        error("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
