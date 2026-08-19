package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source guards for the intentional-coordinate-tap migration.
 *
 * Genuine coordinate taps now go through [CoordinateTap] / Game.tapCoordinate (jitter locally, tap
 * with imageName=null, no missing-asset error). Real template-driven clicks must stay on the library
 * path that passes a template path so the jitter is sized to the matched template. These text guards
 * pin both: the helper contract (null imageName, label retained) and that representative callers on
 * each side stayed on the correct path.
 */
@DisplayName("Coordinate-tap migration source guards")
class CoordinateTapMigrationTest {
    private val helper by lazy { sourceFile("bot/CoordinateTap.kt").readText().replace("\r\n", "\n") }
    private val game by lazy { sourceFile("bot/Game.kt").readText().replace("\r\n", "\n") }
    private val grandConcert by lazy { sourceFile("bot/campaigns/GrandConcert.kt").readText().replace("\r\n", "\n") }
    private val navigator by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }
    private val components by lazy { sourceFile("components/Components.kt").readText().replace("\r\n", "\n") }
    private val campaign by lazy { sourceFile("bot/Campaign.kt").readText().replace("\r\n", "\n") }

    @Test
    @DisplayName("helper taps with a null imageName so no asset is probed")
    fun `helper dispatch passes null imageName`() {
        // CoordinateTap.tap (raw path) and Game.tapCoordinate (waiting path) both tap imageName=null.
        assertTrue(
            helper.contains(Regex("""service\.tap\(jx\.toDouble\(\), jy\.toDouble\(\), null""")),
            "CoordinateTap.tap must dispatch with a null imageName",
        )
        assertTrue(
            game.contains(Regex("""fun tapCoordinate\(""")),
            "Game.tapCoordinate must exist",
        )
        assertTrue(
            game.contains(Regex("""tap\(jx\.toDouble\(\), jy\.toDouble\(\), null""")),
            "Game.tapCoordinate must tap with a null imageName",
        )
    }

    @Test
    @DisplayName("the descriptive label is retained in the trace")
    fun `label is kept for logging`() {
        assertTrue(
            helper.contains("[COORD_TAP] label="),
            "CoordinateTap must log the descriptive label",
        )
    }

    @Test
    @DisplayName("real template clicks stay on the template path (untouched)")
    fun `template-backed clicks keep passing a template path`() {
        // Component.click still taps at the matched point using the real template path.
        assertTrue(
            components.contains("tap(point.x, point.y, template.path"),
            "Component.click must keep passing template.path",
        )
        // The two Class A taps in Campaign (a matched button + a variable imageName) must NOT be migrated.
        assertTrue(
            campaign.contains("game.gestureUtils.tap(buttonLocation.x, buttonLocation.y, ButtonDetails.template.path)"),
            "the ButtonDetails template tap must stay on the library path",
        )
        assertTrue(
            campaign.contains(Regex("""game\.gestureUtils\.tap\(buttonPoint\.x, buttonPoint\.y, imageName""")),
            "the variable-imageName long-press tap must stay on the library path",
        )
    }

    @Test
    @DisplayName("representative callers on both sides are on the right path")
    fun `representative migrated callers`() {
        // Waiting path: the GC career-complete Skills tap (the label that started this work).
        assertTrue(
            grandConcert.contains(Regex("""game\.tapCoordinate\(.*"gc_career_complete_skills"""")),
            "gc_career_complete_skills must go through game.tapCoordinate",
        )
        // Raw path: a non-GC fixed-coordinate tap.
        assertTrue(
            navigator.contains(Regex("""CoordinateTap\.tap\(gestureUtils,.*"tp_restore_button"""")),
            "tp_restore_button must go through CoordinateTap.tap",
        )
    }

    @Test
    @DisplayName("no intentional coordinate tap still passes a descriptive label to the library tap")
    fun `no raw labeled taps remain in migrated files`() {
        // GrandConcert used game.tap("..."); every such call must now be game.tapCoordinate.
        assertFalse(
            grandConcert.contains(Regex("""[^a-zA-Z]game\.tap\(""")),
            "GrandConcert must not call game.tap directly anymore",
        )
        // CareerLaunchNavigator used gestureUtils.tap("..."); the getter is the only gestureUtils.tap-free line.
        assertFalse(
            navigator.contains(Regex("""[^.a-zA-Z]gestureUtils\.tap\(""")),
            "CareerLaunchNavigator must not call gestureUtils.tap directly anymore",
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
