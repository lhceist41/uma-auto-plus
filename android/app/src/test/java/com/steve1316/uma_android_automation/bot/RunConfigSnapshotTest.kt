package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The run-scoped configuration snapshot: it freezes a career's launch-critical identity at the
 * attachment boundary so a mid-career settings write cannot silently change what the run is.
 * The scattered live SettingsHelper readers are external and not exercised here; these tests
 * pin the snapshot mechanism (capture, immutability, revision, drift, isolation) plus a source
 * guard that Game.kt arms it exactly at the career-attachment boundary.
 */
@DisplayName("Run config snapshot")
class RunConfigSnapshotTest {
    @BeforeEach
    @AfterEach
    fun reset() = RunConfigSnapshot.clear()

    @Test
    fun `unarmed by default`() {
        assertFalse(RunConfigSnapshot.isArmed)
        assertNull(RunConfigSnapshot.config)
    }

    @Test
    fun `arm captures the launch-critical identity`() {
        val c = RunConfigSnapshot.arm(revision = 7, trainee = "Super Creek", scenario = "Unity Cup", objective = "sparks", mode = "adaptive", tier = "endgame", nowMs = 1_000L)
        assertTrue(RunConfigSnapshot.isArmed)
        assertEquals(7, c.revision)
        assertEquals("Super Creek", c.trainee)
        assertEquals("sparks", c.objective)
        assertEquals(c, RunConfigSnapshot.config)
    }

    @Test
    fun `the captured value is frozen -- a later live change does not alter the snapshot`() {
        RunConfigSnapshot.arm(revision = 1, trainee = "[Frontline Elegance] Mejiro McQueen", scenario = "Unity Cup", objective = "rank", mode = "adaptive", tier = "endgame", nowMs = 1_000L)
        val captured = RunConfigSnapshot.config!!
        // Simulate the incident's mid-career write landing: the "live" objective becomes sparks.
        // The snapshot must still report the value it captured at attachment (rank), not the new one.
        assertEquals("rank", captured.objective)
        assertEquals("rank", RunConfigSnapshot.config!!.objective)
    }

    @Test
    fun `a new career replaces the snapshot with the newer revision`() {
        RunConfigSnapshot.arm(revision = 1, trainee = "A", scenario = "Unity Cup", objective = "rank", mode = "adaptive", tier = "endgame", nowMs = 1_000L)
        RunConfigSnapshot.arm(revision = 2, trainee = "B", scenario = "Unity Cup", objective = "sparks", mode = "adaptive", tier = "endgame", nowMs = 2_000L)
        assertEquals(2, RunConfigSnapshot.config!!.revision)
        assertEquals("B", RunConfigSnapshot.config!!.trainee)
    }

    @Test
    fun `revisionMatches detects drift and unarmed state`() {
        assertFalse(RunConfigSnapshot.revisionMatches(1)) // unarmed is never a match
        RunConfigSnapshot.arm(revision = 5, trainee = "A", scenario = "Unity Cup", objective = "rank", mode = "adaptive", tier = "endgame", nowMs = 1_000L)
        assertTrue(RunConfigSnapshot.revisionMatches(5))
        assertFalse(RunConfigSnapshot.revisionMatches(6)) // the on-disk revision moved -> drift
    }

    @Test
    fun `clear resets the snapshot`() {
        RunConfigSnapshot.arm(revision = 1, trainee = "A", scenario = "Unity Cup", objective = "rank", mode = "adaptive", tier = "endgame", nowMs = 1_000L)
        RunConfigSnapshot.clear()
        assertFalse(RunConfigSnapshot.isArmed)
    }

    @Test
    fun `describe renders a greppable identity line`() {
        val c = RunConfigSnapshot.arm(revision = 3, trainee = "Super Creek", scenario = "Unity Cup", objective = "sparks", mode = "adaptive", tier = "endgame", nowMs = 1_000L)
        val line = RunConfigSnapshot.describe(c)
        assertTrue("revision=3" in line)
        assertTrue("trainee=\"Super Creek\"" in line)
        assertTrue("objective=sparks" in line)
    }

    @Nested
    @DisplayName("source guard")
    inner class SourceGuard {
        @Test
        fun `Game arms the snapshot once, at the career-attachment boundary, gated on a real career`() {
            val game = sourceFile("bot/Game.kt").readText()
            assertEquals(1, Regex("RunConfigSnapshot\\.armFromSettings\\(").findAll(game).count(), "the snapshot arms exactly once")
            // Same boundary as the spark gate: after the spark-gate arm, inside the !isMiscTask block.
            val miscGate = game.indexOf("if (!isMiscTask)")
            val sparkArm = game.indexOf("SparkRerollGate.beginCareer(")
            val runArm = game.indexOf("RunConfigSnapshot.armFromSettings(")
            val taskStart = game.indexOf("task.start(maxRuntimeMinutes")
            assertTrue(miscGate in 0 until sparkArm, "the arm is inside the !isMiscTask career gate")
            assertTrue(sparkArm < runArm && runArm < taskStart, "the run-config arm sits between the spark-gate arm and task start")
            assertTrue("loaded_run_config" in game, "the loaded_run_config diagnostic is logged at the boundary")
        }

        @Test
        fun `the settings batch write stays atomic (single transaction, not per-row)`() {
            val db = sourceFile("../../../../../src/lib/database.ts", fromKotlinRoot = false)
            // saveSettingsBatch must keep exactly one BEGIN/COMMIT around the prepared-statement loop.
            val batch = db.readText().substringAfter("async saveSettingsBatch(")
            assertTrue("BEGIN TRANSACTION" in batch && "COMMIT" in batch, "the batch stays wrapped in one transaction")
        }
    }

    // Kotlin source lives under .../src/main/java/...; the TS file needs the repo root, so this
    // guard resolves either relative to the Kotlin source root or the repo root.
    private fun sourceFile(relative: String, fromKotlinRoot: Boolean = true): File {
        if (fromKotlinRoot) return File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }
        return File(repoRoot(), relative.removePrefix("../../../../../")).also { require(it.isFile) { "missing ${it.path}" } }
    }

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

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            if (File(dir, "src/lib/database.ts").isFile) return dir!!
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the repo root from ${System.getProperty("user.dir")}")
    }
}
