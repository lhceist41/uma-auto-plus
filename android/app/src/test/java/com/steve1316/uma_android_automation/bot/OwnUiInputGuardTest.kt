package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class OwnUiInputGuardTest {
    @Nested
    @DisplayName("waitWhileOwnUi")
    inner class WaitWhileOwnUi {
        @Test
        fun `waits until our screen leaves the front, then lets the input through`() {
            val inFront = ArrayDeque(listOf(true, true, false))
            var steps = 0
            assertTrue(waitWhileOwnUi({ inFront.removeFirst() }, { false }) { steps++ })
            assertEquals(2, steps)
            assertTrue(inFront.isEmpty())
        }

        @Test
        fun `a stop while held ends the wait instead of tapping`() {
            var steps = 0
            assertThrows(InterruptedException::class.java) {
                waitWhileOwnUi({ true }, { steps == 3 }) { steps++ }
            }
            assertEquals(3, steps)
        }

        @Test
        fun `with the game in front nothing waits`() {
            var steps = 0
            assertFalse(waitWhileOwnUi({ false }, { error("not consulted") }) { steps++ })
            assertEquals(0, steps)
        }
    }

    @Nested
    @DisplayName("every bot tap and swipe is guarded")
    inner class SourceScan {
        @Test
        fun `the tap chokepoints guard before dispatching`() {
            val game = sourceFile("bot/Game.kt").readText().replace("\r\n", "\n")
            val gameTap = game.substringAfter("fun tap(x: Double, y: Double, imageName: String? = null, taps: Int = 1, ignoreWaiting: Boolean = false) {\n")
            assertTrue(gameTap.trimStart().startsWith("OwnUiForeground.waitForGame()"), "Game.tap guards before its run check and dispatch")

            val coordinate = sourceFile("bot/CoordinateTap.kt").readText().replace("\r\n", "\n").substringAfter("fun tap(service: MyAccessibilityService,")
            assertTrue(coordinate.contains("val live = if (OwnUiForeground.waitForGame()) MyAccessibilityService.getInstance() else service"))
            assertTrue(coordinate.indexOf("val live =") < coordinate.indexOf("live.tap("))

            val components = sourceFile("components/Components.kt").readText().replace("\r\n", "\n")
            assertTrue(components.contains("OwnUiForeground.waitForGame()\n        MyAccessibilityService.getInstance().tap(x, y, imageName, taps = taps)"))
        }

        @Test
        fun `no direct tap, swipe or gesture dispatch skips the guard`() {
            val found = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
                unguardedInputCalls(file.relativeTo(sourceRoot()).invariantSeparatorsPath, file.readText())
            }.toList()
            assertEquals(emptyList<String>(), found, "guard these with OwnUiForeground.waitForGame() or allowlist them with a reason")
        }

        @Test
        fun `the scan catches an unguarded call and accepts a guarded one`() {
            val unguarded = "fun f() {\n    game.wait(1.0)\n    game.gestureUtils.swipe(0f, 0f, 0f, 1f)\n}\n"
            assertEquals(listOf("bot/Example.kt:3 game.gestureUtils.swipe"), unguardedInputCalls("bot/Example.kt", unguarded))
            val service = "fun f() {\n    service.tap(1.0, 1.0, null)\n}\n"
            assertEquals(listOf("bot/Example.kt:2 service.tap"), unguardedInputCalls("bot/Example.kt", service))
            val guarded = "fun f() {\n    OwnUiForeground.waitForGame()\n    // Scroll up.\n    game.gestureUtils.swipe(0f, 0f, 0f, 1f)\n    game.tap(1.0, 1.0)\n}\n"
            assertEquals(emptyList<String>(), unguardedInputCalls("bot/Example.kt", guarded))
        }
    }

    private companion object {
        /** Receivers whose tap is a chokepoint guarded inside: [Game.tap] and [CoordinateTap.tap]. */
        val ROUTED = setOf("game", "CoordinateTap")

        /** (file, receiver.method) pairs that dispatch without the guard on purpose. */
        val ALLOWED =
            mapOf(
                "bot/Game.kt gestureUtils.tap" to "Game.tap's own dispatch; the guard opens the function",
                "bot/CoordinateTap.kt live.tap" to "the service is re-read after the guard in CoordinateTap.tap",
                "utils/OwnInputProbe.kt service.dispatchGesture" to "taps the probe's own overlay window by design; recovery code that acts first",
                "CareerLaunchNavigator.kt transport.swipe" to "host ADB swipe, sent only after the host reports the game in front",
                "ProductionHostScrollRecovery.kt activeTransport.swipe" to "host ADB swipe, sent only after the host reports the game in front",
            )

        val CALL = Regex("""([A-Za-z_][\w.]*?(?:\(\))?)\.(tap|swipe|dispatchGesture)\(""")

        /** Input calls in [text] that neither route through a chokepoint nor follow the guard (comment lines between are fine). */
        fun unguardedInputCalls(path: String, text: String): List<String> {
            val lines = text.replace("\r\n", "\n").split("\n")
            val found = mutableListOf<String>()
            lines.forEachIndexed { index, line ->
                val code = line.trim()
                if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                CALL.findAll(line).forEach { match ->
                    val receiver = match.groupValues[1]
                    val call = "$receiver.${match.groupValues[2]}"
                    if (receiver in ROUTED || "$path $call" in ALLOWED) return@forEach
                    var previous = index - 1
                    while (previous >= 0 && lines[previous].trim().startsWith("//")) previous--
                    if (previous < 0 || lines[previous].trim() != "OwnUiForeground.waitForGame()") found += "$path:${index + 1} $call"
                }
            }
            return found
        }

        fun sourceFile(relative: String): File = File(sourceRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

        fun sourceRoot(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation")
                if (candidate.isDirectory) return candidate
                val fromRepoRoot = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
                if (fromRepoRoot.isDirectory) return fromRepoRoot
                dir = dir?.parentFile
            }
            throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
        }
    }
}
