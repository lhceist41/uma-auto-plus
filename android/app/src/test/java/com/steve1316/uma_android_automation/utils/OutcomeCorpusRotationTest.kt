package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Capped per-turn trace files rotate to `<name>.1` instead of going silent; the uncapped corpus never does. */
@DisplayName("Outcome trace rotation")
class OutcomeCorpusRotationTest {
    private val cap = 10L

    @Test
    fun `under the cap the record is appended and nothing rotates`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "decisions.jsonl")
        var rotations = 0
        assertTrue(OutcomeCorpus.appendCapped(file, "a\n", cap) { rotations++ })
        assertTrue(OutcomeCorpus.appendCapped(file, "b\n", cap) { rotations++ })
        assertEquals("a\nb\n", file.readText())
        assertFalse(File(dir, "decisions.jsonl.1").exists())
        assertEquals(0, rotations)
    }

    @Test
    fun `at the cap the file becomes the backup and the next record lands in a fresh file`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "decisions.jsonl")
        file.writeText("0123456789")
        var rotations = 0
        assertTrue(OutcomeCorpus.appendCapped(file, "new\n", cap) { rotations++ })
        assertEquals("0123456789", File(dir, "decisions.jsonl.1").readText())
        assertEquals("new\n", file.readText())
        assertEquals(1, rotations)
    }

    @Test
    fun `a second rotation replaces the backup so only two files stay on disk`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "decisions.jsonl")
        file.writeText("first-file!")
        assertTrue(OutcomeCorpus.appendCapped(file, "second-file\n", cap) {})
        assertTrue(OutcomeCorpus.appendCapped(file, "third\n", cap) {})
        assertEquals("second-file\n", File(dir, "decisions.jsonl.1").readText())
        assertEquals("third\n", file.readText())
        assertEquals(setOf("decisions.jsonl", "decisions.jsonl.1"), dir.list()!!.toSet())
    }

    @Test
    fun `a null cap never rotates however large the file grows`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "careers.jsonl")
        file.writeText("x".repeat(1000))
        assertTrue(OutcomeCorpus.appendCapped(file, "y\n", null) { throw AssertionError("must not rotate") })
        assertEquals(1002, file.length())
        assertFalse(File(dir, "careers.jsonl.1").exists())
    }

    @Test
    fun `a failed rotation drops the record and leaves the full file untouched`(
        @TempDir dir: File,
    ) {
        val file = File(dir, "decisions.jsonl")
        file.writeText("0123456789")
        // A non-empty directory in the backup slot makes both the rename and the delete fail.
        File(dir, "decisions.jsonl.1").apply {
            mkdir()
            File(this, "blocker").writeText("x")
        }
        var rotations = 0
        assertFalse(OutcomeCorpus.appendCapped(file, "new\n", cap) { rotations++ })
        assertEquals("0123456789", file.readText())
        assertEquals(0, rotations)
    }
}
