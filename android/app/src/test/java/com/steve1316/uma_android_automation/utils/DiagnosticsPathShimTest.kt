package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Diagnostics-path compatibility shim for the Automation Library 2.5.9 bump.
 *
 * Library 2.5.7 moved the inherited `matchFilePath` (and its private temp) from external app storage to
 * internal `filesDir/temp`, which non-root adb cannot pull. UMA Auto+ writes its fixture corpus, stuck
 * screenshots, and debug bitmaps under `matchFilePath`, and `LogStreamServer` serves that directory, so
 * the bump would silently break `/triage` pulls and the Remote Log Viewer. `CustomImageUtils` re-owns
 * `matchFilePath` back to the external temp directory in its init block. That reassignment reads an Android
 * Context (`getExternalFilesDir`), so it is pinned here with source guards rather than a mocked framework;
 * runtime pullability is proven in the device smoke.
 */
@DisplayName("Diagnostics-path shim (2.5.9 bump)")
class DiagnosticsPathShimTest {
    private val customImageUtils by lazy { sourceFile("utils/CustomImageUtils.kt").readText().replace("\r\n", "\n") }
    private val logStreamServer by lazy { sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n") }

    /** Body of the `init { ... }` block. */
    private val initBlock by lazy {
        val start = customImageUtils.indexOf("\n    init {")
        require(start >= 0) { "missing init block" }
        customImageUtils.substring(start, customImageUtils.indexOf("\n    }\n", start) + 1)
    }

    @Test
    fun `the init block restores matchFilePath to the external app-specific temp directory`() {
        assertTrue(
            initBlock.contains("val externalTemp = File(context.getExternalFilesDir(null), \"temp\")"),
            "the external temp dir is built from getExternalFilesDir(null)/temp",
        )
        assertTrue(initBlock.contains("matchFilePath = externalTemp.absolutePath"), "the inherited matchFilePath is reassigned to that external path")
    }

    @Test
    fun `the override runs in the init block, after the superclass constructor set the library default`() {
        // The reassignment lives in init (which runs after the ImageUtils(context) super constructor),
        // not in a property initializer that could race the library's own default.
        assertTrue(initBlock.contains("matchFilePath = externalTemp.absolutePath"), "the reassignment is inside init {}")
        val superDefault = customImageUtils.indexOf(") : ImageUtils(context) {")
        val assign = customImageUtils.indexOf("matchFilePath = externalTemp.absolutePath")
        assertTrue(superDefault in 0 until assign, "the class extends ImageUtils(context); the reassignment follows it")
    }

    @Test
    fun `the external temp directory is created so direct imwrite saves do not silently fail`() {
        assertTrue(initBlock.contains("externalTemp.mkdirs()"), "the restored directory is created (saveBitmap/imwrite do not mkdir it themselves)")
    }

    @Test
    fun `the shim reuses the inherited matchFilePath rather than introducing a parallel fixture-path constant`() {
        // The explanatory comment names filesDir; the code must not point the path there.
        val code = initBlock.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
        assertFalse(code.contains("filesDir"), "the override code never points matchFilePath back at internal filesDir")
        // Exactly one assignment to the inherited property; no second independent path field shadowing it.
        assertTrue(Regex("matchFilePath = ").findAll(customImageUtils).count() == 1, "matchFilePath is assigned exactly once (the shim), keeping the inherited property authoritative")
    }

    @Test
    fun `the restored path matches the directory LogStreamServer serves`() {
        // Both must resolve to File(getExternalFilesDir(null), "temp") so the Remote Log Viewer reads the
        // same directory the debug/fixture saves write to.
        assertTrue(
            logStreamServer.contains("File(context.getExternalFilesDir(null), \"temp\")"),
            "LogStreamServer still reads the external temp directory; the shim realigns matchFilePath to it",
        )
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
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
