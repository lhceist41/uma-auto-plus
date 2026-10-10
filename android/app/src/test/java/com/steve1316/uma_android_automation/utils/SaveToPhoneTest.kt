package com.steve1316.uma_android_automation.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.Promise
import com.steve1316.uma_android_automation.DebugTestGate
import com.steve1316.uma_android_automation.StartModule
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * The save-to-phone file rules (only the app's own regular files leave its folder, the copy is byte for byte)
 * and the save flow driven through StartModule.onActivityResult the way the app drives it: cancelled, saved,
 * copy failure, a lost pending save, and the screen-capture request code staying untouched.
 */
@DisplayName("Save to phone")
class SaveToPhoneTest {
    @TempDir
    lateinit var dir: File

    private val appDir get() = File(dir, "files").apply { mkdirs() }

    private val unsafeType = Class.forName("sun.misc.Unsafe")
    private val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
    private val activity = unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Activity::class.java) as Activity
    private val module =
        StartModule(
            object : BridgeReactContext(
                object : ContextWrapper(null) {
                    override fun getApplicationContext(): Context = this
                },
            ) {
                override fun getApplicationContext(): Context = this
            },
        ).also { it.saveRunner = { work -> work() } }

    @AfterEach
    fun clear() {
        DebugTestGate.cancel()
        DebugTestGate.finish()
    }

    /** A promise that records how it ended. A proxy, because the real one builds native maps on reject. */
    private class Outcome {
        var resolved: Any? = null
        var rejected: String? = null
        val promise: Promise =
            java.lang.reflect.Proxy.newProxyInstance(Promise::class.java.classLoader, arrayOf(Promise::class.java)) { _, method, args ->
                when (method.name) {
                    "resolve" -> resolved = args?.firstOrNull()
                    "reject" -> rejected = args.orEmpty().joinToString { it.toString() }
                }
                null
            } as Promise
        val settled get() = resolved != null || rejected != null
    }

    private class Target(val output: OutputStream?, val failOpen: Boolean = false) : StartModule.SaveTarget {
        var deleted = 0

        override fun open(): OutputStream? = if (failOpen) throw IOException("denied") else output

        override fun delete() {
            deleted++
        }
    }

    private fun source(bytes: ByteArray = byteArrayOf(1, 2, 3)) = File(appDir, "report.zip").also { it.writeBytes(bytes) }

    private fun rows() =
        DebugTestGate.ALL_KEYS.associate { "debug/$it" to "false" }.toMutableMap().apply {
            put("general/scenario", "URA Finale")
            put("general/settingsRevision", "10")
            put("debug/veteranRosterScanLimit", "5")
            put("debug/veteranRosterScanEvidence", "true")
            put("debug/veteranInspirationScanLimit", "3")
            put("debug/veteranInspirationScanStartIndex", "2")
        }

    private fun armNormalStart() {
        val payload =
            JSONObject().put("key", JSONObject.NULL).put("scenario", "URA Finale")
                .put("veteranRosterScanLimit", 5).put("veteranRosterScanEvidence", true)
                .put("veteranInspirationScanLimit", 3).put("veteranInspirationScanStartIndex", 2).toString()
        DebugTestGate.start(DebugTestGate.prepare(payload))
    }

    @Test
    fun `a file inside the app folder may be saved, also in a subfolder`() {
        val report = File(appDir, "bug-report/report.zip").apply { parentFile.mkdirs() }.also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val export = File(appDir, "UAA-settings.json").also { it.writeText("{}") }
        assertTrue(SaveToPhone.isAppFile(report, appDir))
        assertTrue(SaveToPhone.isAppFile(export, appDir))
    }

    @Test
    fun `files outside the app folder, parent-folder tricks, folders, look-alike folders and missing files are refused`() {
        val outside = File(dir, "secret.txt").also { it.writeText("x") }
        val lookAlike = File(dir, "files-other/report.zip").apply { parentFile.mkdirs() }.also { it.writeText("x") }
        assertFalse(SaveToPhone.isAppFile(outside, appDir))
        assertFalse(SaveToPhone.isAppFile(File(appDir, "../secret.txt"), appDir))
        assertFalse(SaveToPhone.isAppFile(lookAlike, appDir))
        assertFalse(SaveToPhone.isAppFile(File(appDir, "bug-report").apply { mkdirs() }, appDir))
        assertFalse(SaveToPhone.isAppFile(File(appDir, "missing.zip"), appDir))
    }

    @Test
    fun `the copy equals the source byte for byte, including binary content`() {
        val bytes = ByteArray(300_000) { (it * 31).toByte() }
        val out = ByteArrayOutputStream()
        assertEquals(bytes.size.toLong(), SaveToPhone.copy(source(bytes), out))
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test
    fun `the save code uses no call newer than API 24`() {
        val main = "android/app/src/main/java/com/steve1316/uma_android_automation"
        for (file in listOf("$main/utils/SaveToPhone.kt", "$main/utils/BugReport.kt")) {
            val text = repoFile(file).readText()
            for (newer in listOf("toPath(", "java.nio.file", "Files.", "Path.of(", "Paths.get(")) {
                assertFalse(text.contains(newer), "$file uses $newer (API 26)")
            }
        }
    }

    @Test
    fun `request 4207 with a closed dialog resolves cancelled`() {
        val outcome = Outcome()
        assertTrue(module.beginSave(source(), outcome.promise))
        module.onActivityResult(activity, 4207, Activity.RESULT_CANCELED, null)
        assertEquals("cancelled", outcome.resolved)
        assertNull(outcome.rejected)
    }

    @Test
    fun `a chosen place gets the file and the promise says saved`() {
        val bytes = ByteArray(5000) { it.toByte() }
        val out = ByteArrayOutputStream()
        val outcome = Outcome()
        module.beginSave(source(bytes), outcome.promise)
        module.finishSaveToPhone(Activity.RESULT_OK, Target(out))
        assertEquals("saved", outcome.resolved)
        assertArrayEquals(bytes, out.toByteArray())
    }

    @Test
    fun `a copy that fails rejects, and removes the empty file the dialog made`() {
        val failing =
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")
            }
        val outcome = Outcome()
        val target = Target(failing)
        module.beginSave(source(), outcome.promise)
        module.finishSaveToPhone(Activity.RESULT_OK, target)
        assertTrue(outcome.rejected!!.contains("SAVE_FAILED") || outcome.rejected!!.contains("disk full"))
        assertNull(outcome.resolved)
        assertEquals(1, target.deleted)
    }

    @Test
    fun `a place that cannot be opened rejects and is cleaned up`() {
        val outcome = Outcome()
        val target = Target(null, failOpen = true)
        module.beginSave(source(), outcome.promise)
        module.finishSaveToPhone(Activity.RESULT_OK, target)
        assertNull(outcome.resolved)
        assertTrue(outcome.settled)
        assertEquals(1, target.deleted)
    }

    @Test
    fun `a second save while a dialog is open is refused, and the first still finishes`() {
        val first = Outcome()
        assertTrue(module.beginSave(source(), first.promise))
        assertFalse(module.beginSave(source(), Outcome().promise))
        module.onActivityResult(activity, 4207, Activity.RESULT_CANCELED, null)
        assertEquals("cancelled", first.resolved)
        assertTrue(module.beginSave(source(), Outcome().promise))
    }

    @Test
    fun `a document made for a save that was lost is deleted, a closed dialog is not touched`() {
        val made = Target(ByteArrayOutputStream())
        module.finishSaveToPhone(Activity.RESULT_OK, made)
        assertEquals(1, made.deleted)
        val closed = Target(ByteArrayOutputStream())
        module.finishSaveToPhone(Activity.RESULT_CANCELED, closed)
        module.finishSaveToPhone(Activity.RESULT_OK, null)
        assertEquals(0, closed.deleted)
    }

    @Test
    fun `the save request code never reaches the screen capture handling`() {
        armNormalStart()
        module.onActivityResult(activity, 4207, Activity.RESULT_CANCELED, null)
        assertDoesNotThrow { DebugTestGate.consume { rows() } }
    }

    @Test
    fun `the screen capture request is still handled while a save is pending`() {
        val outcome = Outcome()
        module.beginSave(source(), outcome.promise)
        armNormalStart()
        module.onActivityResult(activity, 100, Activity.RESULT_CANCELED, null)
        assertThrows(Exception::class.java) { DebugTestGate.consume { rows() } }
        assertFalse(outcome.settled, "the capture result must not settle the save")
        module.onActivityResult(activity, 4207, Activity.RESULT_CANCELED, null)
        assertEquals("cancelled", outcome.resolved)
    }

    @Test
    fun `saveFileToPhone rejects instead of crashing when there is no activity or no file`() {
        val outcome = Outcome()
        module.saveFileToPhone(source().absolutePath, "report.zip", "application/zip", outcome.promise)
        assertTrue(outcome.rejected!!.contains("There is no file to save."))
    }

    private fun repoFile(relative: String): File {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(d, relative).isFile) return File(d, relative)
            d = d?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }
}
