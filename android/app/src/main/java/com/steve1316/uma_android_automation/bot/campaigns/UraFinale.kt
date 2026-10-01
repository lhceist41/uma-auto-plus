package com.steve1316.uma_android_automation.bot.campaigns

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.CoordinateTap
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
    // The climax races show the 1st-place "Congratulations" banner, so finalizeRaceResults can record a win/lose
    // signal.
    override val capturesFinaleWins: Boolean = true

    override fun openFansDialog() {
        ButtonHomeFansInfo.click(game.imageUtils, region = game.imageUtils.regionTopHalf, tries = 10)
        bHasTriedCheckingFansToday = true
        game.wait(game.dialogWaitDelay, skipWaitingForLoading = true)
    }

    /**
     * Handles the URA Duel screen: pages the option carousel to the trainee's highest stat and confirms.
     * The detect band, arrow coordinate and "contest of" header string came from a capture, not our supported
     * resolutions, so the first live firing needs supervision. Self-gates on the header and returns true only
     * once the confirm cleared the duel; a miss returns false so normal unknown-screen recovery applies.
     */
    private fun handleUraDuel(): Boolean {
        val detectX = (SharedData.displayWidth * 0.10).toInt()
        val detectY = (SharedData.displayHeight * 0.35).toInt()
        val detectW = (SharedData.displayWidth * 0.80).toInt()
        val detectH = (SharedData.displayHeight * 0.15).toInt()

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

        // Capped so a misread arrow or unexpected layout cannot spin forever (five stats + energy = six cells).
        for (attempt in 0 until 6) {
            val current = readDuelHeader("ura_duel_option_$attempt")
            MessageLog.i(TAG, "[URA_DUEL] Attempt $attempt option text: \"$current\"")
            if (current.contains(targetKeyword)) break
            CoordinateTap.tap(game.gestureUtils, rightArrowX, rightArrowY, "ura_duel_right_arrow")
            game.wait(0.5)
        }

        if (!ButtonOk.click(game.imageUtils)) {
            CoordinateTap.tap(game.gestureUtils, SharedData.displayWidth * 0.5, SharedData.displayHeight * 0.88, "ura_duel_confirm")
        }

        game.wait(1.0)
        game.waitForLoading()

        // Returning true unconditionally would hold consecutiveUnknownScreenCount at 0 and keep the stall watchdog
        // fed, blinding both safety nets if the confirm never lands; if the header persists, hand back to recovery.
        if (readDuelHeader("ura_duel_verify").contains("contest of")) {
            MessageLog.w(TAG, "[URA_DUEL] Confirm did not clear the duel screen (uncalibrated tap); handing back to unknown-screen recovery.")
            return false
        }
        return true
    }

    override fun checkCampaignSpecificConditions(): Boolean = handleUraDuel()
}
