package com.steve1316.uma_android_automation

import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.CareerLaunchNavigator.LaunchScreenState
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.DialogDataDownload
import com.steve1316.uma_android_automation.components.DialogObjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The game's "Data Download" prompt after a patch: OK, never Cancel, then the download is waited out
 * like the loading screen. Text-level only: the dialog's title and body come from a crop of the
 * dialog seen on 2026-09-28; no full-frame capture exists, so the live path is unobserved.
 */
@DisplayName("Data Download prompt")
class DataDownloadTest {
    private val titles = DialogObjects.items.map { it.title }

    @Nested
    @DisplayName("recognising the dialog")
    inner class Recognising {
        @Test
        fun `its title reads as its own dialog, including near misses, and never as a neighbour`() {
            for (read in listOf("Data Download", "Data Downlaod", "DataDownload", "Data Download.", "Data Dovvnload")) {
                assertEquals(DialogDataDownload.title, TextUtils.matchStringInList(read, titles), read)
            }
            assertEquals("Date Changed", TextUtils.matchStringInList("Date Changed", titles))
            assertEquals("Download Error", TextUtils.matchStringInList("Download Error", titles))
            assertEquals(DialogDataDownload, DialogObjects.map["data_download"])
        }

        @Test
        fun `between runs it is its own dialog, answered with OK only`() {
            assertEquals(BetweenRunDialog.DATA_DOWNLOAD, readBetweenRunDialog(false, { true }, { DialogDataDownload.title }))
            val step = planBetweenRunDialog(BetweenRunDialog.DATA_DOWNLOAD, { throw AssertionError("no outage budget for a download prompt") }, StartModule.NAV_DEADLINE_MS)
            assertEquals(BetweenRunDialogStep.AcceptDataDownload, step)
            assertEquals(listOf(ButtonOk), step.taps)
        }

        @Test
        fun `Cancel is never chosen, by any path`() {
            assertFalse(ButtonCancel in BetweenRunDialogStep.AcceptDataDownload.taps)
            assertEquals(ButtonOk, DialogDataDownload.okButton)
            val handler = block(source("bot/DialogHandler.kt"), "private fun handleDataDownload(", "\n    }\n")
            assertTrue(handler.contains("ButtonOk.click(game.imageUtils)"))
            for (never in listOf("ButtonCancel", ".close(", ".cancel(", "Cancel.click")) assertFalse(handler.contains(never), never)
            assertTrue(source("bot/DialogHandler.kt").contains("\"data_download\" -> {\n                handleDataDownload(dialog)\n            }"))
        }
    }

    @Nested
    @DisplayName("an OK button that is not found")
    inner class OkMissed {
        private val limit = Game.DATA_DOWNLOAD_OK_MISS_LIMIT

        private fun plan(misses: Int) =
            planBetweenRunDialog(BetweenRunDialog.DATA_DOWNLOAD, { throw AssertionError("no outage budget") }, StartModule.NAV_DEADLINE_MS, dataDownloadOkMisses = misses)

        @Test
        fun `between runs a few misses are retried, then the queue stops with the prompt's own key and taps nothing`() {
            assertEquals(3, limit)
            for (misses in 0 until limit) assertEquals(BetweenRunDialogStep.AcceptDataDownload, plan(misses), "$misses")
            val stop = plan(limit)
            assertEquals(BetweenRunDialogStep.Fail("DATA_DOWNLOAD_PROMPT"), stop)
            assertTrue(stop.taps.isEmpty())
            assertTrue("DATA_DOWNLOAD_PROMPT" in REPORT_REASON_KEYS, "the stop has its own words")
        }

        @Test
        fun `between runs the misses count only in a row, and an OK or any other look starts them over`() {
            val recovery = BetweenRunRecovery(coldStartOnHome = true, previousCareerComplete = false, finalizeToHome = false, campaignOwnsCareer = false)
            repeat(2) { recovery.missedDataDownloadOk() }
            assertEquals(2, recovery.dataDownloadOkMisses)
            recovery.dataDownloadPromptGone()
            assertEquals(0, recovery.dataDownloadOkMisses)
            repeat(2) { recovery.missedDataDownloadOk() }
            recovery.acceptedDataDownload(1_000L, 60_000L)
            assertEquals(0, recovery.dataDownloadOkMisses, "an OK that landed")
            val navigator = source("CareerLaunchNavigator.kt")
            assertTrue(
                navigator.contains(
                    "if (detectedState != LaunchScreenState.DIALOG_HANDLED || pendingBetweenRunDialog != BetweenRunDialog.DATA_DOWNLOAD) betweenRunRecovery.dataDownloadPromptGone()",
                ),
            )
            val handler = block(navigator, "private fun handleBetweenRunDialog()", "\n    }\n")
            assertTrue(handler.contains("betweenRunRecovery.dataDownloadOkMisses,\n"), "the plan sees the misses")
            assertTrue(handler.contains("if (!tapped) {\n            if (step is BetweenRunDialogStep.AcceptDataDownload) betweenRunRecovery.missedDataDownloadOk()"))
        }

        @Test
        fun `mid-career a few misses leave the prompt up, then the run stops with the prompt's own reason before the dead-gesture ladder`() {
            val dialogHandler = source("bot/DialogHandler.kt")
            val handler = block(dialogHandler, "private fun handleDataDownload(", "\n    }\n")
            assertTrue(handler.contains("dataDownloadOkMisses++\n            if (dataDownloadOkMisses >= Game.DATA_DOWNLOAD_OK_MISS_LIMIT) stopForDataDownloadPrompt(dialog)"))
            assertTrue(handler.contains("dataDownloadOkMisses = 0\n        game.dataDownloadAcceptedAtMs"), "an OK that landed starts over")
            val stop = block(dialogHandler, "private fun stopForDataDownloadPrompt(", "\n    }\n")
            val stopLines =
                listOf("StartModule.queueStopKey = \"DATA_DOWNLOAD_PROMPT\"", "StartModule.queueStopReason = reason", "StartModule.queueStopRequested = true", "throw InterruptedException(reason)")
            for (line in stopLines) {
                assertTrue(stop.contains(line), line)
            }
            for (never in listOf(".click(", "tap(", "Cancel")) assertFalse(stop.contains(never), never)
            assertTrue(dialogHandler.contains("dataDownloadOkMisses = 0\n            return DialogHandlerResult.NoDialogDetected"), "no dialog starts over")
            assertTrue(dialogHandler.contains("if (dialog.name != \"data_download\") dataDownloadOkMisses = 0"), "another dialog starts over")
            val firstRebind = Regex("consecutiveDialogTicks == (\\d+) \\|\\|").find(source("bot/Campaign.kt"))!!.groupValues[1].toInt()
            assertTrue(Game.DATA_DOWNLOAD_OK_MISS_LIMIT < firstRebind, "stops before the dialog streak rebinds at $firstRebind and blames dead gestures")
        }
    }

    @Nested
    @DisplayName("waiting for the download")
    inner class Waiting {
        @Test
        fun `the wait runs for its window only, and a clock going back ends it`() {
            assertFalse(Game.dataDownloadActive(null, 5_000L))
            assertTrue(Game.dataDownloadActive(1_000L, 1_000L))
            assertTrue(Game.dataDownloadActive(1_000L, 1_000L + Game.LOADING_HARD_LIMIT_MS - 1))
            assertFalse(Game.dataDownloadActive(1_000L, 1_000L + Game.LOADING_HARD_LIMIT_MS))
            assertFalse(Game.dataDownloadActive(1_000L, 999L))
        }

        @Test
        fun `the loading limit covers about 200 MB on a slow link, so it is not extended`() {
            val bits = 200L * 1_000_000L * 8L
            val seconds = bits / 3_000_000L
            assertTrue(seconds * 1000 < Game.LOADING_HARD_LIMIT_MS, "200 MB at 3 Mbit/s takes ${seconds}s")
        }

        @Test
        fun `between runs the window is the loading limit before the deadline, and it ends on Home`() {
            val recovery = BetweenRunRecovery(coldStartOnHome = true, previousCareerComplete = false, finalizeToHome = false, campaignOwnsCareer = false)
            assertFalse(recovery.downloadingData(0L))
            val limit = betweenRunLoadingLimitMs(StartModule.NAV_DEADLINE_MS)
            assertTrue(limit in 1..Game.LOADING_HARD_LIMIT_MS)
            recovery.acceptedDataDownload(1_000L, limit)
            assertTrue(recovery.gameComingBack, "the title after the download is a login wait, and nothing is rebound")
            assertTrue(recovery.downloadingData(1_000L + limit - 1))
            assertFalse(recovery.downloadingData(1_000L + limit))
            assertNull(recovery.dataDownloadDone(LaunchScreenState.TITLE_SCREEN, 5_000L), "the title is no end")
            assertTrue(recovery.downloadingData(5_000L))
            assertEquals(4_000L, recovery.dataDownloadDone(LaunchScreenState.HOME_SCREEN, 5_000L))
            assertFalse(recovery.downloadingData(5_000L))
            assertNull(recovery.dataDownloadDone(LaunchScreenState.HOME_SCREEN, 6_000L), "logged once")
        }

        @Test
        fun `between runs the download frames count toward nothing and nothing is tapped`() {
            val navigator = source("CareerLaunchNavigator.kt")
            val unknown = navigator.substringAfter("            } else {\n                if (betweenRunRecovery.downloadingData(SystemClock.elapsedRealtime())) {\n")
            val waitFirst = "                    waitOutDataDownload()\n                    continue\n                }\n                consecutiveUnknowns++\n"
            assertTrue(unknown.startsWith(waitFirst), "the wait comes before the unknown count and its relaunch")
            val wait = block(navigator, "private fun waitOutDataDownload()", "\n    }\n")
            assertTrue(wait.contains("waitSafe(2.0)"), "the wait feeds the stall watchdog")
            for (tap in listOf(".click(", "tap(", "swipe(", "restartGame")) assertFalse(wait.contains(tap), tap)
            val handler = block(navigator, "private fun handleBetweenRunDialog()", "\n    }\n")
            assertTrue(
                handler.contains(
                    "} else if (step is BetweenRunDialogStep.AcceptDataDownload) {\n            val limitMs = betweenRunLoadingLimitMs(msBeforeDeadline)\n" +
                        "            betweenRunRecovery.acceptedDataDownload(SystemClock.elapsedRealtime(), limitMs)",
                ),
                "the window starts only after OK landed, bounded by the loading limit before the deadline",
            )
        }

        @Test
        fun `mid-career the download frames are waited out before the unknown-screen recovery, tapping nothing`() {
            val campaign = source("bot/Campaign.kt")
            val unknownBranch = "} else {\n                detectedKnownScreen = false\n                consecutiveUnknownScreenCount++"
            val branch = campaign.substringAfter("} else if (dataDownloadRunning()) {\n").substringBefore(unknownBranch)
            assertEquals(
                "                // The game's download screens after its Data Download OK: waited out, tapping nothing.\n" +
                    "                detectedKnownScreen = false\n                game.wait(2.0, skipWaitingForLoading = true)\n            ",
                branch,
            )
            val running = block(campaign, "private fun dataDownloadRunning()", "\n    }\n")
            assertTrue(running.contains("Game.dataDownloadActive(acceptedAt, SystemClock.elapsedRealtime())"), "the in-career loading limit")
            assertEquals(2, Regex("endDataDownloadWait\\(\\)\n").findAll(campaign).count(), "a main screen or any other recognised screen ends it")
            assertTrue(source("bot/DialogHandler.kt").contains("game.dataDownloadAcceptedAtMs = SystemClock.elapsedRealtime()"))
        }
    }

    private fun block(
        src: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = src.indexOf(startMarker)
        assertTrue(start >= 0, "missing $startMarker")
        val end = src.indexOf(endMarker, start)
        assertTrue(end > start, "no end for $startMarker")
        return src.substring(start, end)
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val path = "android/app/src/main/java/com/steve1316/uma_android_automation/$relative"
        repeat(8) {
            listOf(File(dir, path), File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")).firstOrNull { it.isFile }?.let { return it.readText().replace("\r\n", "\n") }
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }
}
