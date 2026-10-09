package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.types.isTruncatedDetailsRead
import com.steve1316.uma_android_automation.types.stripTrailingGlyphNoise
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** The Details skill cell reads every ML Kit block inside the crop in reading order, so a wrapped name's short second line is no longer read alone. */
@DisplayName("Details skill cell text from all OCR blocks")
class DetailsCellTextTest {
    private val cropHeight = 100

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val f = File(dir, relative)
            if (f.isFile) return f
            dir = dir?.parentFile
        }
        error("$relative not found")
    }

    private val skillNames by lazy { JSONObject(repoFile("src/data/skills.json").readText()).keys().asSequence().toList() }

    /** The cell reader's chain after OCR: glyphs to spaces, the fuzzy match at SkillDatabase's 0.7 floor, then the truncation guard. */
    private fun cellSkill(blocks: List<OcrTextBlock>): String? {
        val text = detailsCellText(blocks, cropHeight).replace(Regex("\\s+"), " ")
        val base = stripTrailingGlyphNoise(text.replace(Regex("[○◎×]"), " ").trim())
        if (base.length < 2) return null
        val name = TextUtils.matchStringInList(base, skillNames, 0.7) ?: return null
        val matchedBase = stripTrailingGlyphNoise(name.replace(Regex("[○◎×]"), " ").trim())
        return if (isTruncatedDetailsRead(base, matchedBase)) null else name
    }

    @Nested
    @DisplayName("through the real matcher")
    inner class Match {
        private val dutyLine1 = OcrTextBlock(top = 11, bottom = 38, left = 2, text = "The Duty of Dignity")
        private val dutyLine2 = OcrTextBlock(top = 42, bottom = 61, left = 2, text = "Calls")

        @Test
        fun `the Duty name's two blocks read as the whole name, whatever order ML Kit returns them in`() {
            assertEquals("The Duty of Dignity Calls", cellSkill(listOf(dutyLine2, dutyLine1)))
            assertEquals("The Duty of Dignity Calls", cellSkill(listOf(dutyLine1, dutyLine2)))
        }

        @Test
        fun `Calls read alone still matches as it did before the join`() {
            assertEquals("Call Me King", cellSkill(listOf(dutyLine2)))
            // The cell's own first line cut at the crop top is dropped like a neighbour's, which leaves the old single-block read.
            assertEquals("Call Me King", cellSkill(listOf(OcrTextBlock(0, 20, 2, "The Duty of Dignity"), dutyLine2)))
        }

        @Test
        fun `a neighbour line clipped at the crop top no longer leads the match`() {
            val neighbour = OcrTextBlock(top = 0, bottom = 9, left = 2, text = "Straightaways")
            val own = OcrTextBlock(top = 60, bottom = 88, left = 2, text = "Medium")
            assertEquals("Straightaway Adept", TextUtils.matchStringInList(readingOrderText(listOf(neighbour, own)), skillNames, 0.7), "the joined read that the edge drop prevents")
            assertEquals("Medium", detailsCellText(listOf(neighbour, own), cropHeight))
            assertNull(cellSkill(listOf(neighbour, own)), "a line-1-only read is left to a later pass by the truncation guard")
        }

        @Test
        fun `a neighbour line clipped at the crop bottom is dropped too`() {
            assertEquals("The Duty of Dignity Calls", cellSkill(listOf(dutyLine1, dutyLine2, OcrTextBlock(top = 92, bottom = 100, left = 2, text = "Paddock Fright"))))
        }

        @Test
        fun `blocks on one line with slightly different tops read left to right`() {
            val the = OcrTextBlock(top = 14, bottom = 38, left = 2, text = "The")
            val rest = OcrTextBlock(top = 11, bottom = 38, left = 60, text = "Duty of Dignity")
            assertEquals("The Duty of Dignity Calls", readingOrderText(listOf(rest, dutyLine2, the)))
            assertEquals("The Duty of Dignity Calls", cellSkill(listOf(rest, dutyLine2, the)))
        }
    }

    @Nested
    @DisplayName("the join")
    inner class Join {
        @Test
        fun `a glyph block on the name's line reads after it`() {
            assertEquals("Pace Chaser Straightaways ○", readingOrderText(listOf(OcrTextBlock(40, 64, 300, "○"), OcrTextBlock(40, 64, 10, "Pace Chaser Straightaways"))))
        }

        @Test
        fun `a single block, even at an edge, padding and no blocks`() {
            assertEquals("Murmur", detailsCellText(listOf(OcrTextBlock(0, 100, 8, " Murmur\n")), cropHeight))
            assertEquals("", detailsCellText(emptyList(), cropHeight))
        }
    }

    @Nested
    @DisplayName("waiting for the OCR result")
    inner class Await {
        @Test
        fun `a published result comes back whole, a failure as empty`() {
            assertEquals(listOf("a", "b"), awaitWholeResult<String>(1_000L) { publish, _ -> publish(listOf("a", "b")) })
            assertEquals(emptyList<String>(), awaitWholeResult<String>(1_000L) { _, fail -> fail() })
        }

        @Test
        fun `a result that arrives after the timeout is ignored`() {
            var late: Thread? = null
            val read =
                awaitWholeResult<String>(20L) { publish, _ ->
                    late =
                        Thread {
                            Thread.sleep(200L)
                            publish(listOf("late"))
                        }.also { it.start() }
                }
            assertNull(read)
            late?.join()
        }

        @Test
        fun `an interrupt propagates instead of reading on`() {
            Thread.currentThread().interrupt()
            try {
                assertThrows(InterruptedException::class.java) { awaitWholeResult<String>(1_000L) { _, _ -> } }
            } finally {
                Thread.interrupted()
            }
        }
    }

    @Test
    fun `the Details cell reads all blocks, rethrows an interrupt and catches the rest, the Learn list keeps its own read`() {
        val code = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/types/SkillList.kt").readText().replace("\r\n", "\n")
        val cell = code.substring(code.indexOf("private fun readDetailsSkillCell("), code.indexOf("private fun isNegativeSkillCell("))
        val read = cell.indexOf("game.imageUtils.readAllTextBlocks(crop)")
        val rethrow = cell.indexOf("catch (e: InterruptedException) {\n                throw e")
        val caught = cell.indexOf("catch (e: Exception) {")
        assertTrue(read in 0 until rethrow && rethrow < caught, "the interrupt is rethrown before the general catch")
        assertTrue(cell.contains("detailsCellText(blocks, crop.height)"))
        assertTrue(cell.contains("isTruncatedDetailsRead(base, matchedBase)"))
        assertFalse(cell.contains("extractText("))
        val learnTitle = code.substring(code.indexOf("fun getSkillListEntryTitle("), code.indexOf("fun getSkillListEntryTitle(") + 3000)
        assertTrue(learnTitle.contains("extractText(croppedTitle)"))
    }
}
