package com.steve1316.uma_android_automation.bot

import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The bot recomputes the app's settings identity hash from its own read of the settings and refuses
 * a launch whose hash differs from the one the app verified at Start. That only works if both sides
 * hash identically, so this reads the same golden vectors as launchIdentityVectors.test.ts.
 */
@DisplayName("Launch identity hash")
class LaunchIdentityHashTest {
    private val vectors: JSONObject by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val file = File(dir, "src/lib/__fixtures__/launchIdentity.json")
            if (file.isFile) return@lazy JSONObject(file.readText())
            dir = dir?.parentFile
        }
        throw AssertionError("src/lib/__fixtures__/launchIdentity.json not found from the test working directory")
    }

    private fun strings(key: String) = vectors.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }

    private fun rows(v: JSONObject): Map<String, String> = v.getJSONObject("rows").let { r -> r.keys().asSequence().associateWith { r.getString(it) } }

    private fun identity(name: String): JSONObject {
        val a = vectors.getJSONArray("identity")
        return (0 until a.length()).map { a.getJSONObject(it) }.single { it.getString("name") == name }
    }

    @AfterEach
    fun clear() = LaunchIdentityGate.clear()

    @Nested
    @DisplayName("golden vectors shared with the app")
    inner class Vectors {
        @Test
        fun `the hashed categories and excluded keys are the app's`() {
            assertEquals(strings("categories").toSet(), LaunchIdentityGate.HASHED_CATEGORIES)
            assertEquals(strings("excludedKeys").toSet(), LaunchIdentityGate.EXCLUDED_KEYS)
        }

        @Test
        fun `stableHash reproduces every vector`() {
            val a = vectors.getJSONArray("stableHash")
            assertTrue(a.length() >= 6)
            for (i in 0 until a.length()) {
                val v = a.getJSONObject(i)
                assertEquals(v.getString("hash"), LaunchIdentityGate.stableHash(v.getString("input")), v.getString("name"))
            }
        }

        @Test
        fun `identityHash reproduces every vector`() {
            val a = vectors.getJSONArray("identity")
            assertTrue(a.length() >= 8)
            for (i in 0 until a.length()) {
                val v = a.getJSONObject(i)
                assertEquals(v.getString("hash"), LaunchIdentityGate.identityHash(rows(v)), v.getString("name"))
            }
        }

        @Test
        fun `the bot's slash-keyed snapshot hashes the same as the app's dot-keyed rows`() {
            val v = identity("a key containing a dot belongs to the category before the first dot")
            val snapshot = rows(v).mapKeys { (key, _) -> key.replaceFirst('.', '/') }
            assertEquals(v.getString("hash"), LaunchIdentityGate.snapshotIdentityHash(snapshot))
        }
    }

    @Nested
    @DisplayName("the vectors can tell a wrong port apart")
    inner class Discrimination {
        @Test
        fun `sorting by code point instead of UTF-16 code unit changes the surrogate vector's hash`() {
            val v = identity("non-ASCII values and a surrogate pair sorted by UTF-16 code unit")
            val lines = rows(v).filterKeys { it.substringBefore('.') in LaunchIdentityGate.HASHED_CATEGORIES }.map { (k, value) -> "$k=$value" }
            val byCodePoint = lines.sortedWith { a, b -> compareCodePoints(a, b) }
            assertNotEquals(lines.sorted(), byCodePoint, "the vector must order differently by code point")
            assertNotEquals(v.getString("hash"), LaunchIdentityGate.stableHash(byCodePoint.joinToString("\n")))
        }

        @Test
        fun `unsorted lines and a different category set both change the hash`() {
            val v = identity("launch-critical rows in shuffled order, with a JSON-looking value")
            val lines = rows(v).map { (k, value) -> "$k=$value" }
            assertNotEquals(v.getString("hash"), LaunchIdentityGate.stableHash(lines.joinToString("\n")), "insertion order must not be the hashed order")
            val withoutRunQueue = lines.filterNot { it.startsWith("runQueue.") }.sorted()
            assertNotEquals(v.getString("hash"), LaunchIdentityGate.stableHash(withoutRunQueue.joinToString("\n")))
        }

        private fun compareCodePoints(a: String, b: String): Int {
            val x = a.codePoints().toArray()
            val y = b.codePoints().toArray()
            for (i in 0 until minOf(x.size, y.size)) if (x[i] != y[i]) return x[i].compareTo(y[i])
            return x.size.compareTo(y.size)
        }
    }

    @Nested
    @DisplayName("verdict")
    inner class Verdict {
        @Test
        fun `the same revision with a different settings hash is a mismatch that latches`() {
            LaunchIdentityGate.setExpected(10, identity("launch-critical rows in shuffled order, with a JSON-looking value").getString("hash"))
            val read = identity("the same rows with reuse flipped to true").getString("hash")
            assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(10, read))
            assertTrue(LaunchIdentityGate.isBlockedAfterMismatch())
        }

        @Test
        fun `a matching revision and hash pass`() {
            val hash = identity("launch-critical rows in shuffled order, with a JSON-looking value").getString("hash")
            LaunchIdentityGate.setExpected(10, hash)
            assertEquals(LaunchIdentityGate.Verdict.PASS, LaunchIdentityGate.verdict(10, hash))
        }

        @Test
        fun `a different revision with the same hash is still a mismatch`() {
            LaunchIdentityGate.setExpected(10, "6e379b0c")
            assertEquals(LaunchIdentityGate.Verdict.MISMATCH, LaunchIdentityGate.verdict(9, "6e379b0c"))
        }
    }
}
