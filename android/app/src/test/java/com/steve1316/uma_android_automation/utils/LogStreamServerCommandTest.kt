package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The launch-mismatch validation command on the Remote Log Viewer's WebSocket channel.
 *
 * A one-shot `CMD:ARM_LAUNCH_MISMATCH_TEST` frame arms [LaunchIdentityGate]'s forced-mismatch hook so
 * the live overlay can deterministically drive the sticky-guard path. The handler runs inside a Ktor
 * WebSocket session, so the command wiring is pinned here with source guards while the gate's
 * forced-mismatch STATE is covered behaviorally in `LaunchIdentityGateTest`. The safety properties
 * that matter: the command is exact-match, arms the gate and acks, writes no settings and starts no
 * service, and is reachable only through the already-default-off Remote Log Viewer.
 */
@DisplayName("LogStreamServer launch-mismatch command")
class LogStreamServerCommandTest {
    private val server by lazy { sourceFile("utils/LogStreamServer.kt").readText().replace("\r\n", "\n") }

    /** The WebSocket frame-dispatch loop inside handleWebSocketSession (between the loop and its catch). */
    private val dispatch by lazy {
        val start = server.indexOf("for (frame in session.incoming)")
        require(start >= 0) { "missing frame dispatch loop" }
        server.substring(start, server.indexOf("} catch (e: Exception)", start))
    }

    @Test
    fun `the exact ARM command dispatches, arms the gate, and acks`() {
        assertTrue(dispatch.contains("text == \"CMD:ARM_LAUNCH_MISMATCH_TEST\""), "the exact command string is matched")
        assertTrue(dispatch.contains("LaunchIdentityGate.armForcedMismatchForTest()"), "it arms the launch-identity gate")
        assertTrue(dispatch.contains("ACK:ARM_LAUNCH_MISMATCH_TEST"), "it acknowledges the arm over the socket")
    }

    @Test
    fun `the arm command is a sibling branch of the existing REFRESH_IMAGES command, which is unchanged`() {
        val refresh = dispatch.indexOf("text == \"CMD:REFRESH_IMAGES\"")
        val arm = dispatch.indexOf("text == \"CMD:ARM_LAUNCH_MISMATCH_TEST\"")
        assertTrue(refresh in 0 until arm, "REFRESH_IMAGES is still handled, before the new branch")
        assertTrue(dispatch.contains("sendDebugImages(session)"), "REFRESH_IMAGES behavior is unchanged")
    }

    @Test
    fun `the arm branch touches no settings, service, or game -- only the gate and the ack`() {
        val branch = dispatch.substring(dispatch.indexOf("text == \"CMD:ARM_LAUNCH_MISMATCH_TEST\""))
        for (forbidden in listOf("SettingsHelper", "getBooleanSetting", "putSetting", "LogStreamServer.start(", "MediaProjection", "dispatchGesture", "BotService")) {
            assertFalse(branch.contains(forbidden), "the arm branch must not reference $forbidden")
        }
    }

    @Test
    fun `the command channel stays gated behind the default-off Remote Log Viewer`() {
        // The server hosting this command only starts when debug.enableRemoteLogViewer is true (default
        // false), and StartModule is the sole start() caller -- so the command cannot be delivered while
        // the viewer is disabled.
        val start = sourceFile("StartModule.kt").readText().replace("\r\n", "\n")
        assertTrue(start.contains("getBooleanSetting(\"debug\", \"enableRemoteLogViewer\", false)"), "the viewer flag defaults off")
        val flagAt = start.indexOf("enableRemoteLogViewer = SettingsHelper.getBooleanSetting")
        val guard = start.indexOf("if (enableRemoteLogViewer)", flagAt)
        val startCall = start.indexOf("LogStreamServer.start(", flagAt)
        assertTrue(flagAt >= 0 && guard in flagAt until startCall, "LogStreamServer.start is gated on the flag")
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
