package com.steve1316.uma_android_automation.bot.campaigns

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.ButtonHomeFansInfo
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.types.StatName

/**
 * Handles the URA Finale scenario with scenario-specific logic and handling.
 *
 * @property game The [Game] instance for interacting with the game state.
 */
class UraFinale(game: Game) : Campaign(game) {
    // The URA finale's three climax races show the 1st-place "Congratulations" banner, so the base
    // finalizeRaceResults capture can record a true win/lose signal into the career ledger.
    override val capturesFinaleWins: Boolean = true

    override fun openFansDialog() {
        ButtonHomeFansInfo.click(game.imageUtils, region = game.imageUtils.regionTopHalf, tries = 10)
        bHasTriedCheckingFansToday = true
        game.wait(game.dialogWaitDelay, skipWaitingForLoading = true)
    }

    /**
     * Detects and handles the URA Duel screen (the July 2026 "Happy Meek" stat contest): pages the
     * option carousel to the trainee's highest stat and confirms.
     *
     * CALIBRATION: the detect band, the right-arrow coordinate, and the "contest of" header string
     * were derived from a capture, not measured on our own supported resolutions - so the first live
     * firing on a real URA duel must be supervised. The handler self-gates on the header text and only
     * reports the screen handled once the confirm has actually cleared it: a detection miss OR a confirm
     * that does not clear the duel returns false, so the normal unknown-screen recovery still applies and
     * an uncalibrated tap can never wedge the run.
     *
     * @return True only if the duel screen was detected AND confirmed away, false otherwise.
     */
    private fun handleUraDuel(): Boolean {
        val detectX = (SharedData.displayWidth * 0.10).toInt()
        val detectY = (SharedData.displayHeight * 0.35).toInt()
        val detectW = (SharedData.displayWidth * 0.80).toInt()
        val detectH = (SharedData.displayHeight * 0.15).toInt()

        // Fresh capture + OCR of the header band each call - the confirm-verify at the end re-reads it.
        fun readDuelHeader(debugName: String): String =
            game.imageUtils.performOCROnRegion(
                game.imageUtils.getSourceBitmap(),
                detectX,
                detectY,
                detectW,
                detectH,
                useThreshold = false,
                useGrayscale = true,
                scale = 1.5,
                ocrEngine = "mlkit",
                debugName = debugName,
            ).lowercase()

        val headerText = readDuelHeader("ura_duel_detect")
        if (!headerText.contains("contest of")) return false

        MessageLog.i(TAG, "\n[URA_DUEL] Duel screen detected: \"$headerText\"")

        val targetStat =
            mapOf(
                StatName.SPEED to trainee.stats.speed,
                StatName.STAMINA to trainee.stats.stamina,
                StatName.POWER to trainee.stats.power,
                StatName.GUTS to trainee.stats.guts,
                StatName.WIT to trainee.stats.wit,
            ).filter { it.value > 0 }.maxByOrNull { it.value }?.key ?: StatName.SPEED

        val targetKeyword =
            when (targetStat) {
                StatName.SPEED -> "speed"
                StatName.STAMINA -> "stamina"
                StatName.POWER -> "power"
                StatName.GUTS -> "guts"
                StatName.WIT -> "wits"
            }

        MessageLog.i(
            TAG,
            "[URA_DUEL] Best duel stat: $targetStat (spd=${trainee.stats.speed}, sta=${trainee.stats.stamina}, pow=${trainee.stats.power}, guts=${trainee.stats.guts}, wit=${trainee.stats.wit}). Seeking \"$targetKeyword\".",
        )

        val rightArrowX = SharedData.displayWidth * 0.88
        val rightArrowY = SharedData.displayHeight * 0.48

        // Page the carousel until the option band reads the target stat, capped so a misread arrow or
        // an unexpected layout can never spin forever (the duel offers five stats + energy = six cells).
        for (attempt in 0 until 6) {
            val current = readDuelHeader("ura_duel_option_$attempt")
            MessageLog.i(TAG, "[URA_DUEL] Attempt $attempt option text: \"$current\"")
            if (current.contains(targetKeyword)) break
            game.gestureUtils.tap(rightArrowX, rightArrowY, "ura_duel_right_arrow")
            game.wait(0.5)
        }

        if (!ButtonOk.click(game.imageUtils)) {
            game.gestureUtils.tap(SharedData.displayWidth * 0.5, SharedData.displayHeight * 0.88, "ura_duel_confirm")
        }

        game.wait(1.0)
        game.waitForLoading()

        // Report handled ONLY if the confirm actually cleared the duel. Returning true unconditionally
        // would pin Campaign's consecutiveUnknownScreenCount at 0 and keep the stall-watchdog heartbeat
        // fresh, so an uncalibrated confirm that never lands would spin on this screen to the per-run
        // runtime cap with BOTH safety nets blind. If the header persists, hand back to the unknown-screen
        // recovery so the run stops cleanly with diagnostics instead of hanging.
        if (readDuelHeader("ura_duel_verify").contains("contest of")) {
            MessageLog.w(TAG, "[URA_DUEL] Confirm did not clear the duel screen (uncalibrated tap); handing back to unknown-screen recovery.")
            return false
        }
        return true
    }

    override fun checkCampaignSpecificConditions(): Boolean = handleUraDuel()
}
