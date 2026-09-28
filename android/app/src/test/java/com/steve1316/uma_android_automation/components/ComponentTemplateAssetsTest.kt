package com.steve1316.uma_android_automation.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every component template must have its PNG in the assets: the library opens template assets
 * unguarded, so a missing file throws the first time a check or click reaches that component,
 * for example a dialog's OK button. Templates are always string literals, so this reads the source
 * rather than loading the component objects.
 */
@DisplayName("Component template assets")
class ComponentTemplateAssetsTest {
    /** Components declared without a capture (none now); exempt from the PNG check. */
    private val unreferencedWithoutAsset = emptySet<String>()

    private val appRoot by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            if (File(dir, "src/main/assets/images").isDirectory) return@lazy dir!!
            if (File(dir, "android/app/src/main/assets/images").isDirectory) return@lazy File(dir, "android/app")
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the app module from ${System.getProperty("user.dir")}")
    }

    private val sources by lazy {
        File(appRoot, "src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.associateWith { it.readText().replace("\r\n", "\n") }
    }

    /** Each component template path with the object that declares it. */
    private val templates by lazy {
        sources.flatMap { (file, text) ->
            Regex("""Template\(\s*"(components/[^"]+)"""").findAll(text).map { match ->
                val owner = Regex("""\bobject (\w+)""").findAll(text.substring(0, match.range.first)).lastOrNull()?.groupValues?.get(1) ?: file.name
                owner to match.groupValues[1]
            }.toList()
        }
    }

    @Test
    fun `finds the component templates`() {
        assertTrue(templates.size > 200, "found ${templates.size}")
        assertTrue(templates.any { it.second == "components/button/cancel" })
    }

    @Test
    fun `every component template has its PNG`() {
        val missing = templates.filter { (owner, path) -> owner !in unreferencedWithoutAsset && !File(appRoot, "src/main/assets/images/$path.png").isFile }
        assertEquals(emptyList<Pair<String, String>>(), missing)
    }
}
