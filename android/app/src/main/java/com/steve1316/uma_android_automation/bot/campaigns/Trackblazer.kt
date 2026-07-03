package com.steve1316.uma_android_automation.bot.campaigns

import android.graphics.Bitmap
import android.util.Log
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.DialogHandlerResult
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.MainScreenAction
import com.steve1316.uma_android_automation.bot.SelectionSource
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonConfirmUse
import com.steve1316.uma_android_automation.components.ButtonOk
import com.steve1316.uma_android_automation.components.ButtonRaceDayRace
import com.steve1316.uma_android_automation.components.ButtonRaceListFullStats
import com.steve1316.uma_android_automation.components.ButtonRaces
import com.steve1316.uma_android_automation.components.ButtonShopTrackblazer
import com.steve1316.uma_android_automation.components.ButtonSkillUp
import com.steve1316.uma_android_automation.components.ButtonTraining
import com.steve1316.uma_android_automation.components.ButtonHomeFullStats
import com.steve1316.uma_android_automation.components.ButtonTrainingItems
import com.steve1316.uma_android_automation.components.ButtonUseTrainingItems
import com.steve1316.uma_android_automation.components.DialogConfirmUse
import com.steve1316.uma_android_automation.components.DialogExchangeComplete
import com.steve1316.uma_android_automation.components.DialogInterface
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.IconGoalRibbon
import com.steve1316.uma_android_automation.components.IconRaceDayRibbon
import com.steve1316.uma_android_automation.components.IconRaceListPredictionDoubleStar
import com.steve1316.uma_android_automation.components.IconTrainingEventHorseshoe
import com.steve1316.uma_android_automation.components.IconUnityCupTutorialHeader
import com.steve1316.uma_android_automation.components.LabelScheduledRace
import com.steve1316.uma_android_automation.types.DateMonth
import com.steve1316.uma_android_automation.types.DatePhase
import com.steve1316.uma_android_automation.types.DateYear
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.NegativeStatus
import com.steve1316.uma_android_automation.types.PositiveStatus
import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.types.ScannedItem
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.types.TrackblazerShopList
import com.steve1316.uma_android_automation.types.Trainee
import com.steve1316.uma_android_automation.utils.ScrollListEntry
import org.json.JSONArray
import org.opencv.core.Point

/**
 * Handles the Trackblazer scenario with scenario-specific logic and handling.
 *
 * @property game The [Game] instance for interacting with the game state.
 */
class Trackblazer(game: Game) : Campaign(game) {
    /** Flag indicating if the tutorial has been disabled. */
    private var tutorialDisabled = false

    /** Representation of the item shop list along with the mapping of items to their price and effect. */
    private val shopList: TrackblazerShopList = TrackblazerShopList(game)

    init {
        shopList.getInventorySummaryCallback = { getInventorySummary() }
    }

    /** Current number of coins available to spend in the shop. */
    var shopCoins: Int = 0

    /** Map representing the current inventory of items. */
    var currentInventory: Map<String, Int> = mapOf()

    /** Map representing the mapping of bad condition items to their enums. */
    val badConditionMap =
        mapOf(
            "Fluffy Pillow" to NegativeStatus.NIGHT_OWL.statusName,
            "Pocket Planner" to NegativeStatus.SLACKER.statusName,
            "Rich Hand Cream" to NegativeStatus.SKIN_OUTBREAK.statusName,
            "Smart Scale" to NegativeStatus.SLOW_METABOLISM.statusName,
            "Aroma Diffuser" to NegativeStatus.MIGRAINE.statusName,
            "Practice Drills DVD" to NegativeStatus.PRACTICE_POOR.statusName,
        )

    /** Map representing the mapping of good condition items to their enums. */
    val goodConditionMap =
        mapOf(
            "Pretty Mirror" to PositiveStatus.CHARMING.statusName,
            "Reporter's Binoculars" to PositiveStatus.HOT_TOPIC.statusName,
            "Master Practice Guide" to PositiveStatus.PRACTICE_PERFECT.statusName,
            "Scholar's Hat" to PositiveStatus.FAST_LEARNER.statusName,
        )

    /** The limit for consecutive races before the bot should stop and recover. */
    private val consecutiveRacesLimit: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerConsecutiveRacesLimit", 2)

    /** List of race grades that trigger a shop check afterward. */
    private val shopCheckGrades: List<RaceGrade> =
        try {
            val gradesString = SettingsHelper.getStringSetting("scenarioOverrides", "trackblazerShopCheckGrades", "[\"G1\",\"G2\",\"G3\"]")
            val jsonArray = JSONArray(gradesString)
            val grades = mutableListOf<RaceGrade>()
            for (i in 0 until jsonArray.length()) {
                val gradeName = jsonArray.getString(i)
                val grade = RaceGrade.fromName(gradeName)
                if (grade != null) {
                    grades.add(grade)
                }
            }
            grades
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] shopCheckGrades:: Failed to parse shopCheckGrades setting: ${e.message}")
            listOf(RaceGrade.G1, RaceGrade.G2, RaceGrade.G3)
        }

    /** Tracks the number of consecutive races performed. */
    private var consecutiveRaceCount: Int = 0

    /** Flag to prevent double incrementing the counter when OCR already updated it. */
    private var counterUpdatedByOCR: Boolean = false

    /** Whether the Reset Whistle has been used this turn. */
    private var bUsedWhistleToday: Boolean = false

    /** Whether the Good-Luck Charm has been used this turn. */
    private var bUsedCharmToday: Boolean = false

    /** Whether Royal Kale Juice was queued during the current inventory management pass. Reset at
     * the start of each pass. Used to fire a cupcake in the same pass to offset the -1 mood penalty. */
    private var bKaleJuiceQueuedThisPass: Boolean = false

    /** Whether a race hammer has been used this turn. */
    private var bUsedHammerToday: Boolean = false

    /** Flag indicating that the bot decided to train instead of running extra races due to high stat gains. */
    private var bIsIrregularTraining: Boolean = false

    /** Tracks whether the inventory has been synced at least once during this session. */
    private var bInventorySynced: Boolean = false

    /** Flag to track when a shop check should be performed after a race. */
    private var bShouldCheckShop: Boolean = false

    /** Flag to track if the first-time Shop check for the session has been performed. */
    private var bInitialShopCheckPerformed: Boolean = false

    /** Flag indicating if the bot has checked for Irregular Training during the current turn. */
    private var bHasCheckedIrregularTrainingThisTurn: Boolean = false

    /**
     * True once a training has executed this turn. The Summer and Finale branches of
     * [decideNextAction] return TRAIN unconditionally; without this guard, re-landing on the main
     * screen before the date advances re-enters training and double-trains, burning energy before
     * the race. Cleared each turn in [resetDailyFlags].
     */
    private var bCompletedTrainingThisTurn: Boolean = false

    /** Mapping of energy-restoring items to their gain values. */
    private val energyGains =
        mapOf(
            "Royal Kale Juice" to 100,
            "Vita 65" to 65,
            "Vita 40" to 40,
            "Vita 20" to 20,
            "Energy Drink MAX" to 5,
        )

    /** Threshold for energy level to use energy items. */
    private var energyThresholdToUseEnergyItems: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerEnergyThreshold", 40)

    /** Whether the Reset Whistle forces training. */
    private val whistleForcesTraining: Boolean = SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerWhistleForcesTraining", true)

    /** Whether to enable Irregular Training in between races during Trackblazer. */
    private val enableIrregularTraining: Boolean = SettingsHelper.getBooleanSetting("scenarioOverrides", "trackblazerEnableIrregularTraining", false)

    /** Ordered list of energy items from lowest to highest gain, used for conservation priority. */
    private val energyItemConservationOrder = listOf("Energy Drink MAX", "Vita 20", "Vita 40", "Vita 65")

    /** Flag to bypass conservation and force-use the reserved energy item. */
    private var bForceUseReservedItem: Boolean = false

    /**
     * When mood is below NORMAL, training resources (Reset Whistle reshuffle, Good-Luck Charm,
     * Megaphones) refuse to fire if main-stat gain is below this floor. Avoids wasting items on
     * low-return turns where the mood multiplier caps the gain.
     */
    private val lowMainStatGainItemFloor: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerLowMainStatGainItemFloor", 20)

    /**
     * Per-tier minimum selected-training main-stat gain required before each megaphone is spent. Unlike
     * [lowMainStatGainItemFloor] this is mood-independent and stacks on top of it: a tier is held whenever the
     * selected training's main gain is below its threshold, regardless of mood. 0 = no threshold (always allowed).
     * Higher-effect tiers (Empowering +60%/2t, Motivating +40%/3t) reward being saved for high-gain turns such as
     * Classic/Senior summer camp; raising their thresholds stops the bot from burning them on low-value turns.
     */
    private val megaphoneThresholds: Map<String, Int> =
        mapOf(
            "Empowering Megaphone" to SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerSkipEmpoweringMegaphoneBelowGain", 0),
            "Motivating Megaphone" to SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerSkipMotivatingMegaphoneBelowGain", 0),
            "Coaching Megaphone" to SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerSkipCoachingMegaphoneBelowGain", 0),
        )

    /** The frequency to check the shop after a race. */
    private val shopCheckFrequency: Int = SettingsHelper.getIntSetting("scenarioOverrides", "trackblazerShopCheckFrequency", 3)

    /** Tracks the number of days since the last race for shop check frequency. */
    private var shopCheckCounter: Int = 0

    /**
     * Number of recreation dates consumed across this entire career. Rio Kashimoto decks get ~5
     * recs and should save one for Senior-year mood instead of burning them all on streak breaks;
     * non-Rio decks with fewer recs never reach the cap, so behavior is unchanged.
     */
    private var recreationUsedCount: Int = 0

    /**
     * Soft cap on recreation usages in non-Senior years, applied only to energy-recovery calls
     * (streak breaking). Past the cap, energy-path recs defer to Senior and the caller falls back
     * to Rest. Mood-recovery calls are never gated - saving one rec for mood is the point.
     */
    private val recreationUsageCapBeforeSenior: Int = 4

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Debug Tests

    /**
     * Starts debug tests for the Trackblazer campaign.
     *
     * @return True if any tests were run, false otherwise.
     */
    override fun startTests(): Boolean {
        var bDidAnyTestsRun = super.startTests()

        val fnMap: Map<String, () -> Unit> =
            mapOf(
                "debugMode_startTrackblazerRaceSelectionTest" to ::startTrackblazerRaceSelectionTest,
                "debugMode_startTrackblazerInventorySyncTest" to ::startTrackblazerInventorySyncTest,
                "debugMode_startTrackblazerBuyItemsTest" to ::startTrackblazerBuyItemsTest,
            )

        for ((settingName, fn) in fnMap) {
            if (SettingsHelper.getBooleanSetting("debug", settingName)) {
                fn()
                bDidAnyTestsRun = true
            }
        }

        return bDidAnyTestsRun
    }

    /**
     * Debug test for Trackblazer's race selection logic.
     */
    fun startTrackblazerRaceSelectionTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning Trackblazer race selection test.")

        val sourceBitmap = game.imageUtils.getSourceBitmap()

        // If on Main Screen, navigate to the Race List screen first.
        if (checkMainScreen()) {
            MessageLog.i(TAG, "[TEST] Currently on Main Screen. Navigating to Race List...")
            if (!ButtonRaces.click(game.imageUtils, sourceBitmap = sourceBitmap) && !ButtonRaceDayRace.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                MessageLog.e(TAG, "[ERROR] startTrackblazerRaceSelectionTest:: Failed to click Races button.")
                return
            }
            game.wait(1.0)

            // Handle any consecutive race warning dialogs that might pop up.
            handleDialogs(args = mapOf("overrideIgnoreConsecutiveRaceWarning" to true))
        }

        // Now check if we are on the Race List screen.
        if (ButtonRaceListFullStats.check(game.imageUtils)) {
            // Update the date first for racing logic.
            updateDate(isOnMainScreen = false)

            MessageLog.i(TAG, "[TEST] Currently on Race List screen. Calling findSuitableTrackblazerRace($consecutiveRaceCount)...")
            val result = racing.findSuitableTrackblazerRace(consecutiveRaceCount)

            if (result != null) {
                val (point, raceData) = result
                MessageLog.i(TAG, "[TEST] Selection Finalized: ${raceData.name} (${raceData.grade}) at (${point.x}, ${point.y}).")
            } else {
                MessageLog.i(TAG, "[TEST] findSuitableTrackblazerRace returned null. No suitable races found.")
            }
        } else {
            MessageLog.e(TAG, "[ERROR] startTrackblazerRaceSelectionTest:: Not on Main Screen or Race List screen. Ending test.")
        }
    }

    /**
     * Debug test for Trackblazer's inventory sync logic.
     */
    fun startTrackblazerInventorySyncTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning Trackblazer inventory sync test.")

        // If on Main Screen, open Training Items.
        if (checkMainScreen()) {
            MessageLog.i(TAG, "[TEST] Currently on Main Screen. Opening Training Items...")
            if (shopList.openTrainingItemsDialog()) {
                MessageLog.i(TAG, "[TEST] Training Items dialog opened. Calling manageInventoryItems with bDryRun = true and bQuickUseOnly = true...")
                manageInventoryItems(bQuickUseOnly = true, bDryRun = true)
            } else {
                MessageLog.e(TAG, "[ERROR] startTrackblazerInventorySyncTest:: Failed to open Training Items dialog.")
            }
        } else if (ButtonClose.check(game.imageUtils)) {
            // Assume we are already in some dialog, possibly training items.
            MessageLog.i(TAG, "[TEST] Close button detected. Assuming Training Items dialog is open. Calling manageInventoryItems...")
            manageInventoryItems(bQuickUseOnly = true, bDryRun = true)
        } else {
            MessageLog.e(TAG, "[ERROR] startTrackblazerInventorySyncTest:: Not on Main Screen or in a dialog. Ending test.")
        }
    }

    /**
     * Debug test for Trackblazer's buying process logic.
     */
    fun startTrackblazerBuyItemsTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning Trackblazer buy items test.")

        // If on Main Screen, open the Shop.
        if (checkMainScreen()) {
            MessageLog.i(TAG, "[TEST] Currently on Main Screen. Opening Shop...")
            openShop()
            game.wait(1.0)
        }

        // Check if we are in the Shop.
        if (ButtonTrainingItems.check(game.imageUtils)) {
            MessageLog.i(TAG, "[TEST] Shop detected. Calling buyItems with bDryRun = true...")
            buyItems(bDryRun = true)
        } else {
            MessageLog.e(TAG, "[ERROR] startTrackblazerBuyItemsTest:: Shop not detected. Ending test.")
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    override fun handleDialogs(dialog: DialogInterface?, args: Map<String, Any>): DialogHandlerResult {
        val result: DialogHandlerResult = super.handleDialogs(dialog, args)
        if (result !is DialogHandlerResult.Unhandled) {
            return result
        }

        when (result.dialog.name) {
            "exchange_complete" -> {
                val boughtItems = args["itemsBought"] as? List<String> ?: emptyList()
                val quickUseItemsOnly = boughtItems.filter { shopList.shopItems[it]?.isQuickUsage == true }

                if (quickUseItemsOnly.isNotEmpty()) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Quick-use items were purchased. Navigating and queuing for usage...")
                    val usedItems = shopList.useSpecificItems(quickUseItemsOnly, bUseAll = true, reason = "Quick-use after purchase.")
                    usedItems.forEach { useInventoryItem(it.first) }

                    // This clicks the "Confirm Use" button on the "Exchange Complete" dialog.
                    if (result.dialog.ok(game.imageUtils)) {
                        game.wait(0.5)
                        // This clicks the "Use Training Items" button on the "Confirm Use" dialog.
                        handleDialogs(DialogConfirmUse)
                        // This clicks the "Close" button on the "Exchange Complete" dialog after handling quick-use.
                        result.dialog.close(game.imageUtils)
                    } else {
                        // Fallback to closing the dialog if "Confirm Use" button was not found.
                        MessageLog.i(TAG, "[TRACKBLAZER] Quick-use items were identified but the \"Confirm Use\" button was not found. Closing dialog...")
                        result.dialog.close(game.imageUtils)
                    }
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] No quick-use items were purchased. Closing dialog...")
                    result.dialog.close(game.imageUtils)
                }
            }

            "confirm_use" -> {
                result.dialog.ok(game.imageUtils)
            }

            "shop" -> {
                // Once it gets to Junior Year Early July, the shop will be unlocked for use.
                // But the date update has not happened yet, so we need to check for the previous date instead.
                if (date.year == DateYear.JUNIOR && date.month == DateMonth.JUNE && date.phase == DatePhase.LATE) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop unlocked! Initiating the first time buying process.")
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop discount detected! Initiating buying process.")
                }

                if (result.dialog.ok(game.imageUtils)) {
                    game.wait(game.dialogWaitDelay)

                    // Clear the shop check flag and counter as the shop is already being handled.
                    bShouldCheckShop = false
                    shopCheckCounter = 0
                    bInitialShopCheckPerformed = true

                    game.wait(0.5)
                    buyItems()
                    return DialogHandlerResult.Handled(result.dialog)
                } else {
                    MessageLog.e(TAG, "[ERROR] handleDialogs:: Failed to click the OK button on the Shop dialog.")
                    return DialogHandlerResult.Unhandled(result.dialog)
                }
            }

            "training_items" -> {
                MessageLog.i(TAG, "[TRACKBLAZER] Training Items dialog detected. Closing it as it is not currently being handled by a specific process.")
                result.dialog.close(game.imageUtils)
            }

            else -> {
                Log.w(TAG, "[WARN] handleDialogs:: Unknown dialog \"${result.dialog.name}\" detected so it will not be handled.")
                return DialogHandlerResult.Unhandled(result.dialog)
            }
        }

        game.wait(0.5)
        return DialogHandlerResult.Handled(result.dialog)
    }

    override fun handleTrainingEvent() {
        if (!tutorialDisabled) {
            tutorialDisabled =
                if (IconUnityCupTutorialHeader.check(game.imageUtils)) {
                    // If the tutorial is detected, select the second option to close it.
                    MessageLog.i(TAG, "[TRACKBLAZER] Detected tutorial for Trackblazer. Closing it now.")
                    val trainingOptionLocations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
                    if (trainingOptionLocations.size >= 2) {
                        game.tap(trainingOptionLocations[1].x, trainingOptionLocations[1].y, IconTrainingEventHorseshoe.template.path)
                        true
                    } else {
                        MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Could not find training options to dismiss tutorial.")
                        false
                    }
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Tutorial must have already been dismissed.")
                    super.handleTrainingEvent()
                    true
                }
        } else {
            super.handleTrainingEvent()
        }
    }

    override fun recoverEnergy(sourceBitmap: Bitmap?): Boolean {
        MessageLog.i(TAG, "[TRACKBLAZER] Resetting $consecutiveRaceCount consecutive race counts due to energy recovery.")
        consecutiveRaceCount = 0
        return super.recoverEnergy(sourceBitmap)
    }

    override fun recoverMood(sourceBitmap: Bitmap?, targetMood: Mood): Boolean {
        MessageLog.i(TAG, "[TRACKBLAZER] Resetting $consecutiveRaceCount consecutive race counts due to mood recovery.")
        consecutiveRaceCount = 0
        return super.recoverMood(sourceBitmap, targetMood)
    }

    /**
     * Enforces the recreation budget: save at least one rec for Senior-year mood instead of burning
     * them all on streak breaks in Junior/Classic. Gates only energy-recovery calls
     * (`recoverMoodIfCompleted` false); mood-recovery calls pass through, since the reserve exists
     * for them. A blocked energy-path rec falls back to [recoverEnergy]'s Rest.
     *
     * @param recoverMoodIfCompleted True for mood-recovery (always allowed); false for energy /
     * streak-break calls (gated by the budget).
     * @return True if a recreation was consumed, false if the budget was enforced.
     */
    override fun handleRecreationDate(recoverMoodIfCompleted: Boolean): Boolean {
        val isMoodRecovery = recoverMoodIfCompleted
        val isSenior = date.year == DateYear.SENIOR
        if (!isMoodRecovery && !isSenior && recreationUsedCount >= recreationUsageCapBeforeSenior) {
            MessageLog.i(
                TAG,
                "[TRACKBLAZER] Recreation budget exhausted ($recreationUsedCount/$recreationUsageCapBeforeSenior) in ${date.year} on energy-recovery call. Reserving remaining rec(s) for Senior-year mood management. Falling back to rest.",
            )
            return false
        }

        val success = super.handleRecreationDate(recoverMoodIfCompleted)
        if (success) {
            recreationUsedCount++
            MessageLog.i(TAG, "[TRACKBLAZER] Recreation used. Total this career: $recreationUsedCount.")
        }
        return success
    }

    override fun onConsecutiveRaceWarningDetected(dialog: DialogInterface, args: Map<String, Any>) {
        val okButtonLocation: Point? = ButtonOk.find(game.imageUtils).first

        if (okButtonLocation != null) {
            val ocrText =
                game.imageUtils.performOCRFromReference(
                    okButtonLocation,
                    offsetX = -560,
                    offsetY = -525,
                    width = game.imageUtils.relWidth(690),
                    height = game.imageUtils.relHeight(50),
                    useThreshold = true,
                    useGrayscale = true,
                    scale = 2.0,
                    ocrEngine = "mlkit",
                    debugName = "TrackblazerConsecutiveRaceOCR",
                )

            Log.d(TAG, "[DEBUG] onConsecutiveRaceWarningDetected:: OCR text from consecutive warning: \"$ocrText\"")

            // Matches the count in "This will put you at N consecutive races."
            // toIntOrNull (not toInt): a >10-digit OCR misread overflows Int and toInt() throws;
            // toIntOrNull returns null on overflow too, so garbage falls through to -1 ("no count").
            val match = Regex("""([0-9]+)""").find(ocrText)
            val ocrCount = match?.groups?.get(1)?.value?.toIntOrNull() ?: -1

            if (ocrCount != -1) {
                Log.d(TAG, "[DEBUG] onConsecutiveRaceWarningDetected:: OCR detected a count of $ocrCount consecutive races.")

                // Trust OCR as the primary source of truth if it successfully parses a number.
                consecutiveRaceCount = ocrCount
                counterUpdatedByOCR = true
            } else {
                MessageLog.w(TAG, "[WARN] onConsecutiveRaceWarningDetected:: Failed to parse consecutive race count from OCR. Counter will be incremented after race.")
            }
        } else {
            MessageLog.e(TAG, "[ERROR] onConsecutiveRaceWarningDetected:: Failed to find ButtonOk on consecutive race warning screen. Counter will be incremented after race.")
        }

        MessageLog.i(TAG, "[TRACKBLAZER] Current consecutive race count: $consecutiveRaceCount.")
    }

    override fun shouldAllowConsecutiveRace(args: Map<String, Any>): Boolean {
        // Block racing at 0-1 energy with 3+ consecutive races to avoid -30 stat penalty.
        // User can opt out via racing.ignoreLowEnergyRacingBlock setting if they accept the risk.
        if (trainee.energy <= 1 && consecutiveRaceCount >= 3) {
            if (racing.ignoreLowEnergyRacingBlock) {
                MessageLog.w(
                    TAG,
                    "[WARN] shouldAllowConsecutiveRace:: Energy critically low (${trainee.energy}%) with $consecutiveRaceCount consecutive races, but ignoreLowEnergyRacingBlock is enabled. Allowing race.",
                )
            } else {
                val conserveItem = energyItemConservationOrder.firstOrNull { (currentInventory[it] ?: 0) > 0 }
                if (conserveItem != null) {
                    MessageLog.w(
                        TAG,
                        "[WARN] shouldAllowConsecutiveRace:: Energy critically low but $conserveItem exists in inventory. This should have been used in decideNextAction(). Blocking race as safety net.",
                    )
                } else {
                    MessageLog.w(
                        TAG,
                        "[WARN] shouldAllowConsecutiveRace:: Energy is critically low (${trainee.energy}%) with $consecutiveRaceCount consecutive races. Blocking to avoid possible -30 stat penalty.",
                    )
                }
                racing.encounteredRacingPopup = false
                return false
            }
        }

        // A -30 stat penalty can apply starting from 3 consecutive races.
        if (consecutiveRaceCount >= 3) {
            MessageLog.w(TAG, "[WARN] shouldAllowConsecutiveRace:: Current consecutive race count is $consecutiveRaceCount. Note that a -30 stat penalty can apply starting from 3 consecutive races!")
        }

        // Edge case: if there is only 1 turn left before a mandatory race, we can safely race
        // even if it would exceed the limit.
        val turnsRemaining = game.imageUtils.determineTurnsRemainingBeforeNextGoal()
        val onlyOneTurnLeft = turnsRemaining == 1

        // Late December is the last racing opportunity before a mandatory goal race, so ignore the limit.
        val isLateDecember = date.month == DateMonth.DECEMBER && date.phase == DatePhase.LATE

        if (consecutiveRaceCount < (consecutiveRacesLimit + 1) || onlyOneTurnLeft || isLateDecember) {
            if (isLateDecember && consecutiveRaceCount >= (consecutiveRacesLimit + 1)) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Consecutive race count $consecutiveRaceCount >= ${consecutiveRacesLimit + 1}, but it is Late December. Ignoring limit to maximize races before mandatory goal race.",
                )
            } else if (onlyOneTurnLeft && consecutiveRaceCount >= (consecutiveRacesLimit + 1)) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Consecutive race count $consecutiveRaceCount >= ${consecutiveRacesLimit + 1}, but only 1 turn remains before mandatory race. Racing is safe. Continuing.",
                )
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] Consecutive race count $consecutiveRaceCount < ${consecutiveRacesLimit + 1}. Continuing.")
            }
            return true
        } else {
            MessageLog.w(TAG, "[WARN] shouldAllowConsecutiveRace:: Consecutive race count $consecutiveRaceCount >= ${consecutiveRacesLimit + 1}. Aborting racing.")
            racing.encounteredRacingPopup = false
            return false
        }
    }

    override fun shouldRetryRace(dialog: DialogInterface, args: Map<String, Any>): Boolean {
        // Once all free Alarm Clocks are used up, the only retry option is the carat-purchase popup.
        // If alarmClockPolicy already declined that popup this race, accepting the retry just
        // re-triggers the same declined popup, so bail out and proceed to results.
        if (racing.bAlarmClockPolicySkippedThisRace) {
            MessageLog.i(TAG, "[TRACKBLAZER] Alarm clock policy already declined the carat-retry option this race. Skipping further retry attempts and proceeding to race results.")
            return false
        }
        if (racing.lastRaceGrade != null &&
            racing.trackblazerRetryGrades.contains(racing.lastRaceGrade) &&
            racing.raceRetries > 0 &&
            racing.retriesThisRace < racing.maxRetriesPerRace
        ) {
            if (racing.lastRaceIsRival && !racing.bRetriedCurrentRace) {
                MessageLog.i(TAG, "[TRACKBLAZER] ${racing.lastRaceGrade} Rival Race retry button is available. Retrying once.")
                racing.bRetriedCurrentRace = true
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] ${racing.lastRaceGrade} race retry button is available. Retrying.")
            }

            racing.raceRetries--
            racing.retriesThisRace++
            if (dialog.ok(game.imageUtils)) {
                game.wait(1.0)
            }
            return true
        }

        MessageLog.w(TAG, "[WARN] shouldRetryRace:: No retries remaining or G1/G2/G3/Rival race conditions not met.")
        return false
    }

    override fun shouldRecoverMoodFromItems(sourceBitmap: Bitmap): Boolean? {
        val hasMoodItems =
            currentInventory.any { (name, count) ->
                count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake")
            }

        if (trainee.energy >= 70) {
            // If energy is high, we prefer to rest/recover mood naturally to save items.
            MessageLog.i(TAG, "[TRACKBLAZER] Mood is ${trainee.mood} and energy is ${trainee.energy}% (>= 70%). Attempting to recover mood via rest/recreation (saving items).")
            return true
        } else if (!hasMoodItems) {
            // If energy is low, we prefer to use items. If no items are available, we must rest/recover mood manually as a fallback.
            MessageLog.i(TAG, "[TRACKBLAZER] Mood is ${trainee.mood} and energy is ${trainee.energy}% (< 70%). No mood items are available. Attempting to recover mood via rest/recreation...")
            return true
        }

        // Has mood items and energy is low - skip recovery, items will handle mood in useItems().
        return false
    }

    override fun handleRaceEventFallback(): Boolean {
        if (racing.detectedMandatoryRaceCheck) {
            return super.handleRaceEventFallback()
        }
        ButtonBack.click(game.imageUtils)
        ButtonCancel.click(game.imageUtils)
        ButtonClose.click(game.imageUtils)
        game.wait(1.0)
        handleTrackblazerTraining()
        return false
    }

    override fun handleRaceEvents(isScheduledRace: Boolean): Boolean {
        counterUpdatedByOCR = false

        // If it's not a scheduled race, we need to apply Trackblazer-specific filtering.
        if (!isScheduledRace) {
            val sourceBitmap = game.imageUtils.getSourceBitmap()

            // Check if we're at a mandatory race screen first (IconRaceDayRibbon or IconGoalRibbon).
            // If we are, we should treat it as a mandatory race and NOT an extra race.
            if (IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap) || IconGoalRibbon.check(game.imageUtils, sourceBitmap = sourceBitmap)) {
                MessageLog.i(TAG, "[TRACKBLAZER] Mandatory race ribbon detected. Processing as mandatory race.")
                val result = super.handleRaceEvents(true)
                // Mandatory races bypass executeAction(), so decrement the megaphone counter here
                // to match the per-turn decrement other actions get. Otherwise it stays inflated
                // and the bot thinks a megaphone is still active after its effect expired in-game.
                if (result && trainee.megaphoneTurnCounter > 0) {
                    trainee.megaphoneTurnCounter--
                    MessageLog.i(TAG, "[TRACKBLAZER] Megaphone duration reduced. Turns remaining: ${trainee.megaphoneTurnCounter}.")
                }
                return result
            }

            MessageLog.i(TAG, "[TRACKBLAZER] Checking for suitable races.")
            // We need to enter the race list to check for predictions and grades.
            // Try both standard Races button and the Race Day variant.
            if (!ButtonRaces.click(game.imageUtils, sourceBitmap = sourceBitmap) && !ButtonRaceDayRace.click(game.imageUtils, sourceBitmap = sourceBitmap)) {
                MessageLog.e(TAG, "[ERROR] handleRaceEvents:: Failed to click Races button.")
                return false
            }
            game.wait(1.0)

            // Handle any consecutive race warning dialogs that might pop up after clicking "Races".
            val dialogResult = handleDialogs()
            if (dialogResult is DialogHandlerResult.Handled &&
                dialogResult.dialog.name == "consecutive_race_warning" &&
                !(racing.enableForceRacing || racing.ignoreConsecutiveRaceWarning) &&
                consecutiveRaceCount > consecutiveRacesLimit &&
                game.imageUtils.determineTurnsRemainingBeforeNextGoal() != 1
            ) {
                MessageLog.i(TAG, "[TRACKBLAZER] Consecutive race warning obeyed. Aborting racing.")
                // Back off the race list onto a detectable screen (matches the "no suitable races"
                // abort below). Without it the bot is stranded on the race list and the next
                // handleMainScreen fails checkMainScreen, wasting cycles until something recovers.
                ButtonBack.click(game.imageUtils)
                game.wait(0.5)
                return false
            }

            val suitableRaceResult = racing.findSuitableTrackblazerRace(consecutiveRaceCount)
            if (suitableRaceResult != null) {
                val suitableRaceLocation = suitableRaceResult.first
                val raceData = suitableRaceResult.second
                MessageLog.i(TAG, "[TRACKBLAZER] Found suitable race: ${raceData.name} (${raceData.grade}). Processing items.")

                // Use race-related items (Hammers, Glow Sticks).
                // Skip OP, Pre-debut, and Maiden races as hammers provide no benefit for those grades.
                if (raceData.grade == RaceGrade.G1 || raceData.grade == RaceGrade.G2 || raceData.grade == RaceGrade.G3) {
                    useRaceItems(raceData.grade, raceData.fans)
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Non-G1/G2/G3 race detected (${raceData.grade}). Skipping race item usage.")
                }

                racing.lastRaceGrade = raceData.grade
                racing.lastRaceIsRival = raceData.isRival
                game.tap(suitableRaceLocation.x, suitableRaceLocation.y, IconRaceListPredictionDoubleStar.template.path, ignoreWaiting = true)
                game.wait(0.5)
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] No suitable races found. Backing out and training.")
                ButtonBack.click(game.imageUtils)
                game.wait(0.5)
                return false
            }
        }

        val result = super.handleRaceEvents(isScheduledRace)
        if (result) {
            if (!counterUpdatedByOCR) {
                consecutiveRaceCount++
                MessageLog.i(TAG, "[TRACKBLAZER] Incremented consecutive race count to $consecutiveRaceCount.")
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] Consecutive race count was already updated by OCR: $consecutiveRaceCount.")
            }

            // Check if we should perform a shop check after this race.
            // Any graded race defined in the settings or any scheduled race should trigger a shop check.
            if (isScheduledRace || shopCheckGrades.contains(racing.lastRaceGrade)) {
                if (shopCheckFrequency <= 1) {
                    if (isScheduledRace) {
                        MessageLog.i(TAG, "[TRACKBLAZER] Scheduled race completed. Shop check will be performed on main screen.")
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] Graded race detected (${racing.lastRaceGrade}). Shop check will be performed on main screen.")
                    }
                    bShouldCheckShop = true
                } else if (shopCheckCounter == 0) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Race completed. Starting shop check counter at 1. Frequency: $shopCheckFrequency.")
                    shopCheckCounter = 1
                }
            }
        }
        return result
    }

    override fun resetDailyFlags() {
        bUsedWhistleToday = false
        bUsedCharmToday = false
        bUsedHammerToday = false
        bIsIrregularTraining = false
        bHasCheckedIrregularTrainingThisTurn = false
        bCompletedTrainingThisTurn = false
        training.clearAnalysisCache()
    }

    override fun onBeforeMainScreenUpdate() {
        // Buy items if a shop check is pending after a race.
        if (bShouldCheckShop) {
            MessageLog.i(TAG, "[TRACKBLAZER] Pending shop check detected! Checking Shop for new items...")
            game.wait(0.5)
            if (openShop()) {
                bShouldCheckShop = false
                buyItems(bAfterRacePurchase = true)
            } else {
                MessageLog.w(TAG, "[WARN] onBeforeMainScreenUpdate:: Failed to open the shop despite pending shop check.")
                // A misfired shop entry can leave us on a partial shop dialog or greeting screen.
                // Tap Back/Cancel/Close to claw back to the main screen so the next handleMainScreen
                // re-check doesn't see non-main UI and the performOCROnRegion bounds check doesn't
                // fire. Clear the pending flag either way so we don't loop on the same failure.
                bShouldCheckShop = false
                ButtonBack.click(game.imageUtils)
                game.wait(0.5)
                ButtonCancel.click(game.imageUtils)
                game.wait(0.5)
                ButtonClose.click(game.imageUtils)
                game.wait(0.5)
            }
        }
    }

    override fun onMainScreenEntry() {
        // Before taking any action, check for items to use.
        // This handles Stats, Energy, Mood, and Bad Conditions.
        // Training items are only available starting Turn 13 (Junior Year Early July).
        if (date.day >= 13) {
            if (!bInitialShopCheckPerformed) {
                MessageLog.i(TAG, "[TRACKBLAZER] Performing first-time Shop check for the session...")
                if (openShop()) {
                    buyItems()
                    bInitialShopCheckPerformed = true
                }
            }

            useItems(trainee)
        }
    }

    override fun performMoodRecovery(sourceBitmap: Bitmap, targetMood: Mood): Boolean {
        // If we don't have Cupcakes, we fall back to the standard recovery method.
        return recoverMood(sourceBitmap, targetMood = targetMood)
    }

    /** True when this turn carries a scheduled agenda race or a mandatory/goal race. Live template
     * checks OR the turn-start cached flags count - a single missed read must not skip a race
     * (same belt-and-braces rationale as the irregular-training gate). */
    private fun isRaceCommitmentTurn(): Boolean {
        return cachedMandatoryRaceDay || cachedScheduledRaceDay || cachedGoalRibbonDay ||
            LabelScheduledRace.check(game.imageUtils) ||
            IconRaceDayRibbon.check(game.imageUtils) ||
            IconGoalRibbon.check(game.imageUtils)
    }

    override fun decideNextAction(): MainScreenAction {
        // Summer Training: Train during July and August in Classic/Senior.
        if (date.isSummer() && !(racing.skipSummerTrainingForAgenda && racing.enableUserInGameRaceAgenda)) {
            if (bCompletedTrainingThisTurn) {
                MessageLog.i(TAG, "[TRACKBLAZER] Summer training already completed this turn. Deferring to the race/rest flow.")
                return super.decideNextAction()
            }
            // A scheduled or mandatory race this turn outranks the summer-training hijack -
            // a skipped agenda race costs more than one camp training.
            if (isRaceCommitmentTurn()) {
                MessageLog.i(TAG, "[TRACKBLAZER] Scheduled/mandatory race this summer turn. Deferring to the race flow instead of camp training.")
                return super.decideNextAction()
            }
            MessageLog.i(TAG, "[TRACKBLAZER] It is Summer. Prioritizing training.")
            decisionTracer?.recordActionChoice(MainScreenAction.TRAIN, "Trackblazer: Summer training (July/August)")
            return MainScreenAction.TRAIN
        }

        // Finale: Train during the final 3 turns (Qualifier, Semifinal, Finals).
        if (date.bIsFinaleSeason && date.day >= 73) {
            if (bCompletedTrainingThisTurn) {
                MessageLog.i(TAG, "[TRACKBLAZER] Finale training already completed this turn. Deferring to the race/rest flow.")
                return super.decideNextAction()
            }
            MessageLog.i(TAG, "[TRACKBLAZER] It is the Finale. Prioritizing training.")
            decisionTracer?.recordActionChoice(MainScreenAction.TRAIN, "Trackblazer: Finale training (turns 73-75)")
            return MainScreenAction.TRAIN
        }

        // Post-debut bond-building window (Junior July, turns 13-14): Rival Races unlock Junior
        // Early August (turn 15), so the two turns after the June debut have only OP-grade races.
        // Train instead to push support bonds toward orange before the graded calendar starts.
        // Turn 15 is excluded (rival races available). A scheduled in-game Agenda race wins over this.
        val isPostDebutBondWindow =
            date.year == DateYear.JUNIOR && date.month == DateMonth.JULY
        if (isPostDebutBondWindow && !LabelScheduledRace.check(game.imageUtils)) {
            MessageLog.i(TAG, "[TRACKBLAZER] Post-debut bond-building window (Junior July, no rival races yet). Prioritizing training.")
            decisionTracer?.recordActionChoice(MainScreenAction.TRAIN, "Trackblazer: post-debut bond-building window (Junior July)")
            return MainScreenAction.TRAIN
        }

        // Avoid racing and training analysis at low energy with 3+ consecutive races to prevent
        // -30 stat penalty. Energy items were already attempted in onMainScreenEntry().
        // However, if a Good-Luck Charm is available, allow training analysis since the charm
        // can bypass high failure chances that come with low energy.
        val hasCharmAvailable = !bUsedCharmToday && (currentInventory["Good-Luck Charm"] ?: 0) > 0
        if (trainee.energy <= 10 && consecutiveRaceCount >= 3 && !hasCharmAvailable) {
            // Scheduled and mandatory races always run - a skipped agenda race costs more than
            // the -30 low-energy penalty this guard exists to avoid.
            if (isRaceCommitmentTurn()) {
                MessageLog.i(TAG, "[TRACKBLAZER] Scheduled/mandatory race detected at low energy. Racing anyway - the race outranks the consecutive-race energy guard.")
                decisionTracer?.recordActionChoice(MainScreenAction.RACE, "Trackblazer: scheduled/mandatory race overrides the low-energy rest guard")
                return MainScreenAction.RACE
            }
            // Before resting, attempt to use a conserved energy item for emergency race recovery.
            val conserveItem = energyItemConservationOrder.firstOrNull { (currentInventory[it] ?: 0) > 0 }
            if (conserveItem != null) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Energy is low (${trainee.energy}%) with $consecutiveRaceCount consecutive races. Using conserved $conserveItem for emergency recovery.",
                )
                if (shopList.openTrainingItemsDialog()) {
                    bForceUseReservedItem = true
                    val itemsUsed = shopList.useSpecificItems(listOf(conserveItem), reason = "Emergency race recovery to avoid -30 stat penalty.")
                    bForceUseReservedItem = false
                    itemsUsed.forEach { (name, _) ->
                        val gain = energyGains[name] ?: 0
                        val oldEnergy = trainee.energy
                        trainee.energy = (trainee.energy + gain).coerceAtMost(100)
                        useInventoryItem(name)
                        MessageLog.i(TAG, "[TRACKBLAZER] Emergency recovery: $oldEnergy% -> ${trainee.energy}%.")
                    }
                    if (itemsUsed.isNotEmpty()) {
                        confirmAndCloseItemDialog(itemsUsed.size)
                    } else {
                        ButtonClose.click(game.imageUtils)
                        game.wait(game.dialogWaitDelay)
                    }
                }

                if (trainee.energy > 10) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Energy recovered to ${trainee.energy}%. Resuming normal decision flow.")
                    // Fall through to normal racing/training logic below.
                } else {
                    MessageLog.w(TAG, "[WARN] decideNextAction:: Energy still low (${trainee.energy}%) after emergency recovery. Resting.")
                    decisionTracer?.recordActionChoice(MainScreenAction.REST, "Trackblazer: energy still low (${trainee.energy}%) after emergency recovery")
                    return MainScreenAction.REST
                }
            } else {
                MessageLog.w(
                    TAG,
                    "[WARN] decideNextAction:: Energy is low (${trainee.energy}%) with $consecutiveRaceCount consecutive races and no energy items available. Resting to avoid -30 stat penalty.",
                )
                decisionTracer?.recordActionChoice(
                    MainScreenAction.REST,
                    "Trackblazer: energy ${trainee.energy}% with $consecutiveRaceCount consecutive races, no energy items",
                )
                return MainScreenAction.REST
            }
        }

        if (enableIrregularTraining && date.year > DateYear.JUNIOR && !bHasCheckedIrregularTrainingThisTurn) {
            val isScheduledRace = LabelScheduledRace.check(game.imageUtils)
            val isMandatoryRace = IconRaceDayRibbon.check(game.imageUtils) || IconGoalRibbon.check(game.imageUtils)

            // Also gate on the turn-start cached flags: the live template checks above can miss, and an
            // irregular-training hijack must never override a mandatory/scheduled race. Requiring both
            // the live AND cached detections to be clear means a single missed read cannot skip a race.
            // cachedGoalRibbonDay mirrors the goal-ribbon arm of the live isMandatoryRace check, so a
            // goal-only mandatory day (no race-day ribbon) still has a cache backup if its live read misses.
            if (!isScheduledRace && !isMandatoryRace && !cachedMandatoryRaceDay && !cachedScheduledRaceDay && !cachedGoalRibbonDay) {
                // Skip irregular training evaluation when energy is depleted and no charm can offset the failure chance.
                if (trainee.energy <= 0 && !hasCharmAvailable) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Skipping Irregular Training evaluation as energy is ${trainee.energy}% with no Good-Luck Charm available.")
                    bHasCheckedIrregularTrainingThisTurn = true
                } else if (ButtonTraining.click(game.imageUtils)) {
                    game.wait(game.dialogWaitDelay)

                    val isIrregularEvaluation = true
                    val hasCharm = !bUsedCharmToday && (currentInventory["Good-Luck Charm"] ?: 0) > 0
                    training.analyzeTrainings(mapOf("ignoreFailureChance" to hasCharm, "isIrregularEvaluation" to isIrregularEvaluation))

                    val bestTraining = training.recommendTraining(isIrregularEvaluation = isIrregularEvaluation)
                    if (bestTraining != null && training.lastSelectionSource != SelectionSource.ANALYSIS) {
                        MessageLog.i(TAG, "[TRACKBLAZER] Pre-screen evaluation used fallback (${training.lastSelectionSource}): $bestTraining.")
                    }

                    if (bestTraining != null) {
                        // Stay on the training screen in order to perform the training.
                        MessageLog.i(TAG, "[TRACKBLAZER] Valid Irregular Training found ($bestTraining). Hijacking turn.")

                        bIsIrregularTraining = true
                        decisionTracer?.recordActionChoice(MainScreenAction.TRAIN, "Trackblazer: irregular training hijack ($bestTraining)")
                        return MainScreenAction.TRAIN
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] No valid Irregular Training found. Backing out to resume racing logic.")
                        ButtonBack.click(game.imageUtils)
                        game.wait(game.dialogWaitDelay)

                        // Mark that we've checked for Irregular Training this turn to avoid looping.
                        bHasCheckedIrregularTrainingThisTurn = true
                    }
                } else {
                    // The Training button could not be found/clicked. Mark the check as done for
                    // this turn to prevent a tight retry loop and fall through to normal logic.
                    MessageLog.w(
                        TAG,
                        "[WARN] decideNextAction:: Irregular Training evaluation could not click ButtonTraining. Skipping this turn's check.",
                    )
                    bHasCheckedIrregularTrainingThisTurn = true
                }
            }
        }

        // Otherwise, use base class decision logic.
        return super.decideNextAction()
    }

    override fun executeAction(action: MainScreenAction, bIsScheduledRaceDay: Boolean): Boolean {
        val result =
            when (action) {
                MainScreenAction.TRAIN -> {
                    if (bForcedWitTraining) {
                        super.executeAction(action, bIsScheduledRaceDay)
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] Decision made to train.")
                        handleTrackblazerTraining()
                        bHasCheckedDateThisTurn = false
                        true
                    }
                }

                else -> {
                    super.executeAction(action, bIsScheduledRaceDay)
                }
            }

        if (result && action != MainScreenAction.NONE) {
            // Turn is over, decrement megaphone counter.
            if (trainee.megaphoneTurnCounter > 0) {
                trainee.megaphoneTurnCounter--
                MessageLog.i(TAG, "[TRACKBLAZER] Megaphone duration reduced. Turns remaining: ${trainee.megaphoneTurnCounter}.")
            }

            // Increment the shop check counter if it is active.
            if (shopCheckCounter > 0) {
                shopCheckCounter++
                if (shopCheckCounter >= shopCheckFrequency) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop check frequency reached ($shopCheckCounter / $shopCheckFrequency). Shop check will be performed on main screen.")
                    bShouldCheckShop = true
                    shopCheckCounter = 0
                } else {
                    MessageLog.i(TAG, "[TRACKBLAZER] Shop check counter: $shopCheckCounter / $shopCheckFrequency. Next check in ${shopCheckFrequency - shopCheckCounter} day(s).")
                }
            }
        }

        return result
    }

    override fun onRaceWin() {
        MessageLog.i(TAG, "[TRACKBLAZER] Rival Race win detected via post-race popup.")
        if (shopCheckFrequency <= 1) {
            bShouldCheckShop = true
        } else if (shopCheckCounter == 0) {
            MessageLog.i(TAG, "[TRACKBLAZER] Rival Race win detected. Starting shop check counter at 1. Frequency: $shopCheckFrequency.")
            shopCheckCounter = 1
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Opens the Shop UI.
     *
     * @param tries The number of scan attempts to perform to find the shop button.
     * @return True if the shop was opened successfully, false otherwise.
     */
    fun openShop(tries: Int = 5): Boolean {
        // Already on the Training Items screen; nothing to open. ButtonTrainingItems also matches
        // the Main screen's round quick-access button on some devices (99%+ confidence in a device
        // capture), and the false "already open" made updateShopCoins read the Main screen's stat
        // HUD. ButtonHomeFullStats only appears on the Main screen, so its absence disambiguates.
        if (ButtonTrainingItems.check(game.imageUtils) && !ButtonHomeFullStats.check(game.imageUtils)) {
            return true
        }

        if (ButtonShopTrackblazer.click(game.imageUtils, tries = tries)) {
            game.wait(game.dialogWaitDelay)

            // An unlock/discount dialog can intercept the tap, so the click "succeeds" but the items
            // screen never opened. Dismiss any shop dialog and re-verify before reporting success.
            val detectedDialog = DialogUtils.getDialog(game.imageUtils)
            if (detectedDialog != null && detectedDialog.name == "shop") {
                MessageLog.i(TAG, "[TRACKBLAZER] Shop dialog intercepted the shop button. Entering via dialog...")
                if (detectedDialog.ok(game.imageUtils)) {
                    game.wait(game.dialogWaitDelay)
                }
            }

            if (ButtonTrainingItems.check(game.imageUtils, tries = 5) && !ButtonHomeFullStats.check(game.imageUtils)) {
                return true
            }

            MessageLog.e(TAG, "[ERROR] openShop:: Clicked the shop button but the Training Items screen never opened.")
            return false
        }

        val detectedDialog = DialogUtils.getDialog(game.imageUtils)
        if (detectedDialog != null && detectedDialog.name == "shop") {
            MessageLog.i(TAG, "[TRACKBLAZER] Shop dialog detected while trying to open the shop. Entering via dialog...")
            if (detectedDialog.ok(game.imageUtils)) {
                game.wait(game.dialogWaitDelay)
                return ButtonTrainingItems.check(game.imageUtils, tries = 5) && !ButtonHomeFullStats.check(game.imageUtils)
            }
        }

        MessageLog.e(TAG, "[ERROR] openShop:: Unable to open the Shop due to failing to find its button.")
        return false
    }

    /**
     * Reads the Shop Coins amount via OCR and updates our internal count.
     *
     * @return True if the Shop Coins amount was updated successfully, false otherwise.
     */
    fun updateShopCoins(): Boolean {
        MessageLog.i(TAG, "[TRACKBLAZER] Updating current amount of Shop Coins...")
        // Brief settle wait for popup-dismiss animations; the find(tries=30) below already polls
        // with its own per-try delay, so a longer pre-wait would just be dead air.
        game.wait(1.0)
        val (trainingItemsButtonLocation, sourceBitmap) = ButtonTrainingItems.find(game.imageUtils, tries = 30)
        if (trainingItemsButtonLocation == null) {
            MessageLog.e(TAG, "[ERROR] updateShopCoins:: Failed to find Training Items button.")
            return false
        }
        val coinText =
            game.imageUtils.performOCROnRegion(
                sourceBitmap,
                game.imageUtils.relX(trainingItemsButtonLocation.x, -35),
                game.imageUtils.relY(trainingItemsButtonLocation.y, 80),
                game.imageUtils.relWidth(180),
                game.imageUtils.relHeight(65),
                useThreshold = false,
                useGrayscale = true,
                scale = 2.0,
                ocrEngine = "mlkit",
                debugName = "ShopCoins",
            )

        try {
            val cleanedText = coinText.replace(Regex("[^0-9]"), "")
            if (cleanedText.isEmpty()) {
                MessageLog.w(TAG, "[WARN] updateShopCoins:: Parsed empty string for Shop Coins from raw text: \"$coinText\".")
            } else {
                shopCoins = cleanedText.toInt()
                MessageLog.i(TAG, "[INFO] Current Shop Coins: $shopCoins (Raw OCR text: \"$coinText\")")
            }
        } catch (_: NumberFormatException) {
            MessageLog.e(TAG, "[ERROR] updateShopCoins:: Failed to parse Shop Coins from OCR text: \"$coinText\".")
        }

        return true
    }

    /**
     * Starts the process to buy items from the Shop.
     *
     * @param priorityList An ordered list of item names to buy. Defaults to an empty list.
     * @param bDryRun If true, only logs intentions without performing any clicks.
     * @param bAfterRacePurchase If true, indicates this process was triggered by a post-race shop check.
     */
    fun buyItems(priorityList: List<String> = listOf(), bDryRun: Boolean = false, bAfterRacePurchase: Boolean = false) {
        val finalPriorityList = priorityList.ifEmpty { getPriorityList() }

        if (bAfterRacePurchase) {
            MessageLog.i(TAG, "[TRACKBLAZER] Buying extra items after participating in a race...")
        }
        MessageLog.i(TAG, "[TRACKBLAZER] Initiating buying process.")

        // Update current coins via OCR before buying.
        if (!updateShopCoins()) {
            MessageLog.w(TAG, "[TRACKBLAZER] Aborting buying process due to failed Shop Coins update.")
            return
        }
        MessageLog.i(TAG, "[TRACKBLAZER] Initial Shop Coins: $shopCoins")

        // If the shop coins are 0, it is possible that the OCR failed to read them correctly.
        // In this case, we will initiate a "Force Purchase" process to attempt to buy items until we can't anymore.
        val bForcePurchase = shopCoins == 0
        if (bForcePurchase) {
            MessageLog.i(TAG, "[TRACKBLAZER] Shop coins read as 0. This may be an OCR failure. Initiating Force Purchase mode.")
        }

        val inventoryLimits =
            finalPriorityList.associateWith { itemName ->
                val itemCount = currentInventory[itemName] ?: 0
                val isBadConditionItem = badConditionMap.containsKey(itemName) || itemName == "Miracle Cure"
                val isGoodConditionItem = goodConditionMap.containsKey(itemName)

                val maxLimit =
                    if (isBadConditionItem || isGoodConditionItem) {
                        // Stockpile items go FIRST, before the "already have one" guard. Miracle Cure and
                        // Rich Hand Cream get burned through during high-frequency racing, so target up to 5.
                        // Otherwise the `itemCount >= 1` guard short-circuits to 0 once you own one and the
                        // stockpile never builds.
                        if (isBadConditionItem && (itemName == "Miracle Cure" || itemName == "Rich Hand Cream")) {
                            5
                        } else if (itemCount >= 1) {
                            // Already have one of this single-use condition item - don't buy more.
                            0
                        } else {
                            // Check if the condition is active/inactive.
                            if (isBadConditionItem) {
                                val condition = badConditionMap[itemName]
                                if (condition != null && trainee.currentNegativeStatuses.contains(condition)) {
                                    1
                                } else {
                                    0
                                }
                            } else {
                                val condition = goodConditionMap[itemName]
                                if (condition != null && !trainee.currentPositiveStatuses.contains(condition)) {
                                    1
                                } else {
                                    0
                                }
                            }
                        }
                    } else {
                        5
                    }

                (maxLimit - itemCount).coerceAtLeast(0)
            }

        val filteredPriorityList = finalPriorityList.filter { (inventoryLimits[it] ?: 0) > 0 }

        if (filteredPriorityList.isEmpty()) {
            MessageLog.v(TAG, getInventorySummary(withDividers = true))
        } else if (bDryRun) {
            shopList.buyItems(filteredPriorityList, shopCoins, inventoryLimits, bDryRun = true, bForcePurchase = bForcePurchase)
            return
        }

        val itemsBought = shopList.buyItems(filteredPriorityList, shopCoins, inventoryLimits, bForcePurchase = bForcePurchase)
        if (itemsBought.isNotEmpty()) {
            // Update internal inventory.
            val nextInventory = currentInventory.toMutableMap()
            itemsBought.forEach { itemName ->
                nextInventory[itemName] = (nextInventory[itemName] ?: 0) + 1
            }
            currentInventory = nextInventory.toMap()

            // Handle "Exchange Complete" dialog.
            if (handleDialogs(DialogExchangeComplete, args = mapOf("itemsBought" to itemsBought)) is DialogHandlerResult.Handled) {
                MessageLog.i(TAG, "[TRACKBLAZER] Successfully handled \"Exchange Complete\" dialog.")

                // Update internal coins count via OCR after purchase.
                updateShopCoins()
                MessageLog.i(TAG, "[TRACKBLAZER] Remaining Shop Coins: $shopCoins")

                ButtonBack.click(game.imageUtils)
                game.wait(2.0)
            }
        }

        // Exit the Shop to return to the Main screen.
        MessageLog.i(TAG, "[TRACKBLAZER] Shop process complete. Returning up to the previous screen.")
        ButtonBack.click(game.imageUtils)
        game.wait(1.0)
    }

    /**
     * Generates a priority list of items to buy based on current state and rules.
     *
     * @return An ordered list of item names.
     */
    private fun getPriorityList(): List<String> {
        val topStats = training.statPrioritization.take(3)
        val priorityList = mutableListOf<String>()

        // 1. Top Tier Priorities (Good-Luck Charms, Hammers, Glow Sticks, Priority heals, Priority Energy/Bond).
        priorityList.add("Good-Luck Charm")
        priorityList.add("Master Cleat Hammer")
        priorityList.add("Artisan Cleat Hammer")
        priorityList.add("Glow Sticks")

        // 1b. Summer Camp Prep Window.
        // Before Classic/Senior summer camp (starts Early July), promote training-effect items
        // (Megaphones, top-stat Ankle Weights, Reset Whistles) and the energy combo (Royal Kale
        // Juice + Plain Cupcake) above stat scrolls, to stock up for the 4 all-Level-5 camp turns.
        // Duplicate later entries are idempotent: calculatePurchases() removes each bought unit from
        // the pool, so a later occurrence is a no-op once the limit is claimed at the promoted spot.
        val isPreSummerCampWindow = isInPreSummerCampPrepWindow()
        if (isPreSummerCampWindow) {
            MessageLog.i(TAG, "[TRACKBLAZER] Pre-summer-camp prep window active. Promoting training-effect and energy-combo items to top priority.")
            priorityList.add("Empowering Megaphone")
            priorityList.add("Motivating Megaphone")
            topStats.forEach { stat ->
                val ankleWeight =
                    when (stat) {
                        StatName.SPEED -> "Speed Ankle Weights"
                        StatName.STAMINA -> "Stamina Ankle Weights"
                        StatName.POWER -> "Power Ankle Weights"
                        StatName.GUTS -> "Guts Ankle Weights"
                        else -> null
                    }
                if (ankleWeight != null) priorityList.add(ankleWeight)
            }
            priorityList.add("Reset Whistle")
            priorityList.add("Royal Kale Juice")
            priorityList.add("Plain Cupcake")
        }

        priorityList.add("Royal Kale Juice")
        priorityList.add("Grilled Carrots")
        priorityList.add("Rich Hand Cream")
        priorityList.add("Miracle Cure")

        // 2. Stats (Excluding Notepads).
        val statsOrdered = listOf("Scroll", "Manual")
        val statNamesOrdered = listOf("Speed", "Stamina", "Power", "Guts", "Wit")
        statsOrdered.forEach { type ->
            statNamesOrdered.forEach { name ->
                priorityList.add("$name $type")
            }
        }

        // 3. Energy + Mood.
        priorityList.add("Vita 65")
        priorityList.add("Vita 40")
        priorityList.add("Vita 20")
        priorityList.add("Berry Sweet Cupcake")
        priorityList.add("Plain Cupcake")

        // 4. Training Effects (Megaphones and specific Ankle Weights).
        priorityList.add("Empowering Megaphone")
        priorityList.add("Motivating Megaphone")
        topStats.forEach { stat ->
            val ankleWeight =
                when (stat) {
                    StatName.SPEED -> "Speed Ankle Weights"
                    StatName.STAMINA -> "Stamina Ankle Weights"
                    StatName.POWER -> "Power Ankle Weights"
                    StatName.GUTS -> "Guts Ankle Weights"
                    else -> null
                }
            if (ankleWeight != null) priorityList.add(ankleWeight)
        }
        priorityList.add("Coaching Megaphone")
        priorityList.add("Reset Whistle")

        // 5. Heal Bad Conditions (Non-priority ones, limit 1 logic is handled in buyItems()).
        priorityList.add("Fluffy Pillow")
        priorityList.add("Pocket Planner")
        priorityList.add("Smart Scale")
        priorityList.add("Aroma Diffuser")
        priorityList.add("Practice Drills DVD")

        // 6. Training Facilities (Top 3 stats only).
        topStats.forEach { stat ->
            val trainingApp =
                when (stat) {
                    StatName.SPEED -> "Speed Training Application"
                    StatName.STAMINA -> "Stamina Training Application"
                    StatName.POWER -> "Power Training Application"
                    StatName.GUTS -> "Guts Training Application"
                    StatName.WIT -> "Wit Training Application"
                }
            priorityList.add(trainingApp)
        }

        // 7. Other Energy Items.
        priorityList.add("Energy Drink MAX")
        priorityList.add("Energy Drink MAX EX")

        // 8. Good Condition Items
        priorityList.add("Pretty Mirror")
        priorityList.add("Reporter's Binoculars")
        priorityList.add("Master Practice Guide")
        priorityList.add("Scholar's Hat")

        return priorityList
    }

    /**
     * Pre-summer-camp prep window: Classic/Senior June, the month before camp starts (Early July).
     * Inventory must be stocked with Megaphones, Ankle Weights, Reset Whistles, and the Royal Kale +
     * Plain Cupcake combo before camp, or the run's highest-value training turns are forfeited.
     *
     * @return True when the current turn is in the prep window (Classic or Senior, month is June).
     */
    private fun isInPreSummerCampPrepWindow(): Boolean {
        val isTargetYear = date.year == DateYear.CLASSIC || date.year == DateYear.SENIOR
        val isPrepMonth = date.month == DateMonth.JUNE
        return isTargetYear && isPrepMonth
    }

    /**
     * Decrements an item's count in the internal inventory.
     *
     * @param itemName The name of the item used.
     */
    private fun useInventoryItem(itemName: String) {
        val nextInventory = currentInventory.toMutableMap()
        val count = nextInventory[itemName] ?: 0
        if (count > 0) {
            nextInventory[itemName] = count - 1
            MessageLog.i(TAG, "[TRACKBLAZER] Decremented $itemName. Remaining: ${nextInventory[itemName]}.")
        }
        currentInventory = nextInventory.toMap()
    }

    /**
     * Confirms the usage of items and closes the Training Items dialog.
     *
     * @param itemsUsedCount The number of items used during this pass to determine the animation delay.
     */
    private fun confirmAndCloseItemDialog(itemsUsedCount: Int = 1) {
        MessageLog.i(TAG, "[TRACKBLAZER] Confirming usage of $itemsUsedCount items.")
        ButtonConfirmUse.click(game.imageUtils)
        game.wait(game.dialogWaitDelay)
        ButtonUseTrainingItems.click(game.imageUtils)

        // Lengthy delay here for the animation to finish.
        // We increase the delay by a second for each additional item to be used after 3 items.
        val animationDelay = if (itemsUsedCount > 3) 4.0 + (itemsUsedCount - 3) else 4.0
        MessageLog.i(TAG, "[TRACKBLAZER] Waiting for animation to finish (Delay: $animationDelay seconds).")
        game.wait(animationDelay)

        // Finalize by closing the dialog.
        MessageLog.i(TAG, "[TRACKBLAZER] Closing training items dialog.")
        if (ButtonClose.check(game.imageUtils, tries = 50)) {
            game.wait(1.0)
            ButtonClose.click(game.imageUtils)
            game.wait(1.0)
        }

        // Clear the training analysis cache so that the bot re-evaluates the training options if it re-enters the training screen.
        training.clearAnalysisCache()
    }

    /**
     * Clicks the plus button for an item in the item list and updates inventory.
     *
     * @param itemName The name of the item.
     * @param entry The ScrollListEntry of the item.
     * @param logMessage The message to log when clicking.
     * @param nextInventory The current inventory map being updated during this pass.
     * @param recheck If true, captures a fresh crop of the entry to re-verify the button state.
     * @param reason Optional reason for using the item.
     * @return True if the button was clicked, false otherwise.
     */
    private fun clickItemPlusButton(itemName: String, entry: ScrollListEntry, logMessage: String, nextInventory: MutableMap<String, Int>, recheck: Boolean = false, reason: String? = null): Boolean {
        val bitmapToUse: Bitmap =
            if (recheck) {
                // Let the dialog finish updating button states (e.g. cupcakes enabling right
                // after Royal Kale Juice is queued) before capturing the fresh crop.
                game.wait(0.3)
                val source = game.imageUtils.getSourceBitmap()
                game.imageUtils.createSafeBitmap(source, entry.bbox.x, entry.bbox.y, entry.bbox.w, entry.bbox.h, "recheck item")
            } else {
                entry.bitmap
            } ?: return false

        if (ButtonSkillUp.checkDisabled(game.imageUtils, bitmapToUse) == true) {
            if (recheck) MessageLog.w(TAG, "[TRACKBLAZER] \"$itemName\" still reads disabled on recheck. Skipping it.")
            return false
        }

        val plusPoint = ButtonSkillUp.findImageWithBitmap(game.imageUtils, bitmapToUse)
        if (plusPoint != null) {
            MessageLog.i(TAG, logMessage)
            game.tap(entry.bbox.x + plusPoint.x, entry.bbox.y + plusPoint.y)

            // Update the provided inventory map.
            val count = nextInventory[itemName] ?: 0
            if (count > 0) {
                nextInventory[itemName] = count - 1
                MessageLog.i(TAG, "[TRACKBLAZER] Decremented $itemName. Remaining: ${nextInventory[itemName]}.")
            }

            return true
        }
        return false
    }

    /**
     * Handles the specialized training process for Trackblazer, including item usage.
     */
    private fun handleTrackblazerTraining() {
        MessageLog.i(TAG, "[TRACKBLAZER] Starting specialized Training process.")

        // Fast path: Already on the training screen from irregular training evaluation.
        if (bIsIrregularTraining) {
            MessageLog.i(TAG, "[TRACKBLAZER] Using existing irregular training analysis (already on Training screen).")
            val trainingSelected: StatName? = training.recommendTraining(isIrregularEvaluation = true)
            if (trainingSelected != null && training.lastSelectionSource != SelectionSource.ANALYSIS) {
                MessageLog.i(TAG, "[TRACKBLAZER] On-screen evaluation used fallback (${training.lastSelectionSource}): $trainingSelected.")
            }

            // Still use training items (megaphones, ankle weights, charms, energy, stat items, etc.)
            if (date.day >= 13) {
                useItems(trainee, trainingSelected)
            }

            if (trainingSelected != null) {
                training.executeTraining(trainingSelected)
                bCompletedTrainingThisTurn = true
            } else {
                MessageLog.w(TAG, "[WARN] handleTrackblazerTraining:: Irregular training unexpectedly became null. Backing out.")
                ButtonBack.click(game.imageUtils)
                game.wait(game.dialogWaitDelay)
            }

            bIsIrregularTraining = false
            return
        }

        // Enter the Training screen.
        if (!ButtonTraining.click(game.imageUtils)) {
            MessageLog.e(TAG, "[ERROR] handleTrackblazerTraining:: Failed to enter Training screen.")
            return
        }
        game.wait(0.5)

        // Initial Training Analysis.
        val hasCharm = date.day >= 13 && !bUsedCharmToday && (currentInventory["Good-Luck Charm"] ?: 0) > 0
        training.analyzeTrainings(mapOf("ignoreFailureChance" to hasCharm))
        var trainingSelected: StatName? = training.recommendTraining()
        if (trainingSelected != null && training.lastSelectionSource != SelectionSource.ANALYSIS) {
            MessageLog.i(TAG, "[TRACKBLAZER] Initial training selection used fallback (${training.lastSelectionSource}): $trainingSelected.")
        }

        // Finally, perform a consolidated item usage pass after the training is finalized.
        if (date.day >= 13) {
            useItems(trainee, trainingSelected)
        }

        // Reset Whistle Check: Use if recommendations are poor.
        // We define "poor" as no training being selected or certain other conditions.
        // Block whistling during irregular training evaluations.
        if (date.day >= 13 && !bUsedWhistleToday && trainingSelected == null && !bIsIrregularTraining && !training.needsEnergyRecovery) {
            val hasWhistle = (currentInventory["Reset Whistle"] ?: 0) > 0

            // Whistle viability gate: below NORMAL mood the multiplier caps gains, and reshuffling won't
            // recover from that, so refuse the Whistle if enough non-blacklisted trainings already show
            // low main-stat gain. Required count scales with blacklist size: 0 -> 3-of-5, 1 -> 2-of-4,
            // 2+ -> 1 (clamped).
            val whistleGateBlocks =
                if (trainee.mood < Mood.NORMAL) {
                    val blacklistSize = training.blacklist.filterNotNull().size
                    val requiredLowGainCount = (3 - blacklistSize).coerceAtLeast(1)
                    val results = training.cachedAnalysisResults ?: emptyList()
                    val nonBlacklisted = results.filter { it.name !in training.blacklist }
                    val lowGainCount = nonBlacklisted.count { (it.statGains[it.name] ?: 0) < lowMainStatGainItemFloor }
                    val blocks = lowGainCount >= requiredLowGainCount
                    if (blocks) {
                        MessageLog.i(
                            TAG,
                            "[TRACKBLAZER] Refusing Reset Whistle reshuffle: mood=${trainee.mood}, $lowGainCount of ${nonBlacklisted.size} non-blacklisted trainings have main gain below floor ($lowMainStatGainItemFloor). Reshuffling won't recover from the mood penalty.",
                        )
                    }
                    blocks
                } else {
                    false
                }

            if (whistleGateBlocks) {
                // Whistle usage was skipped such that trainingSelected stays null and the existing recovery branch below fires.
            } else if (hasWhistle) {
                MessageLog.i(TAG, "[TRACKBLAZER] No suitable training found. Using Reset Whistle.")
                if (shopList.openTrainingItemsDialog()) {
                    if (shopList.useSpecificItems(listOf("Reset Whistle"), reason = "No suitable training found.").isNotEmpty()) {
                        confirmAndCloseItemDialog(1)

                        useInventoryItem("Reset Whistle")
                        bUsedWhistleToday = true

                        // Re-analyze after shuffle.
                        MessageLog.i(TAG, "[TRACKBLAZER] Re-analyzing trainings after Reset Whistle.")
                        training.analyzeTrainings(mapOf("ignoreFailureChance" to hasCharm))
                        trainingSelected = training.recommendTraining(forceSelection = whistleForcesTraining)
                        when {
                            trainingSelected == null ->
                                MessageLog.i(TAG, "[TRACKBLAZER] Reset Whistle re-analysis returned no training; nothing to execute.")
                            training.lastSelectionSource == SelectionSource.FORCED_FROM_SKIPPED -> {
                                // The forced pick comes from the rejected pool, so either its main gain is below the
                                // item-conservation floor or its failure chance is too high to clear without a Good-Luck
                                // Charm. If the charm gates would suppress the charm anyway, executing it is a near-certain
                                // failure with no defensive item. Abandon it and let the recovery branch take Rest/Recreation.
                                val forcedCandidate = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }
                                val forcedFail = forcedCandidate?.failureChance ?: 0
                                val forcedMainGain = forcedCandidate?.statGains?.get(trainingSelected) ?: 0
                                val charmAvailable = (currentInventory["Good-Luck Charm"] ?: 0) > 0
                                val charmWouldFire =
                                    charmAvailable && !bUsedCharmToday && forcedFail >= 20 &&
                                        !shouldConserveTrainingEffectItems(trainingSelected, trainee) &&
                                        forcedMainGain >= lowMainStatGainItemFloor
                                if (!charmWouldFire && forcedFail >= 50) {
                                    MessageLog.i(
                                        TAG,
                                        "[TRACKBLAZER] Skipping Whistle force-pick: $trainingSelected at $forcedFail% fail with no Good-Luck Charm. Falling back to recovery.",
                                    )
                                    trainingSelected = null
                                } else {
                                    MessageLog.i(
                                        TAG,
                                        "[TRACKBLAZER] Reset Whistle re-analysis still rejected all trainings; Whistle Forces Training is enabled, " +
                                            "so executing forced pick: $trainingSelected. Megaphone (if available) will be applied to this forced selection.",
                                    )
                                }
                            }
                            training.lastSelectionSource != SelectionSource.ANALYSIS ->
                                MessageLog.i(TAG, "[TRACKBLAZER] Reset Whistle re-analysis used fallback (${training.lastSelectionSource}): $trainingSelected.")
                            else ->
                                MessageLog.i(TAG, "[TRACKBLAZER] Reset Whistle re-analysis selected: $trainingSelected.")
                        }

                        // Perform another consolidated item usage pass if needed after shuffle.
                        useItems(trainee, trainingSelected)
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] No Reset Whistles found in inventory.")
                        ButtonClose.click(game.imageUtils)
                        game.wait(game.dialogWaitDelay, skipWaitingForLoading = true)
                    }
                }
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] No suitable training found and no Reset Whistles in cached inventory or all are disabled.")
            }
        } else if (training.needsEnergyRecovery && trainingSelected == null) {
            MessageLog.i(TAG, "[TRACKBLAZER] Skipping Reset Whistle as energy recovery is needed, not a training re-roll.")
        }

        // Final Training Execution.
        if (trainingSelected != null) {
            training.executeTraining(trainingSelected)
            bCompletedTrainingThisTurn = true
        } else {
            // No suitable training, so take the best recovery action to avoid a wasted turn.
            // Resting is 62.5% chance of +50 energy; Shrine (clears status conditions) is 30% in recreation.
            if (trainee.mood <= Mood.NORMAL || trainee.energy <= 50) {
                MessageLog.i(TAG, "[TRACKBLAZER] Still no suitable training found. Backing out for recovery.")
                // Set to false to avoid possible rest/recreation looping on the next turn.
                training.firstTrainingCheck = false
                ButtonBack.click(game.imageUtils)
                game.wait(1.0)

                if (checkMainScreen()) {
                    if (trainee.mood == Mood.AWFUL || (trainee.mood <= Mood.NORMAL && trainee.energy >= 20)) {
                        MessageLog.i(TAG, "[TRACKBLAZER] Mood is ${trainee.mood}. Attempting to recover mood.")
                        recoverMood()
                    } else {
                        MessageLog.i(TAG, "[TRACKBLAZER] Energy is ${trainee.energy}%. Attempting to recover energy.")
                        recoverEnergy()
                    }
                }
            } else {
                // Force a training (Wit when we'd risk stat reductions, else Speed). 80 Energy is
                // optimal for Wit since there may be post events that provide additional energy.
                val forcedStat =
                    if (trainee.energy >= 80 && trainee.currentNegativeStatuses.isEmpty()) {
                        StatName.SPEED
                    } else {
                        StatName.WIT
                    }

                // Refuse to force-train a stat the analysis rejected (high failure, low gain even with
                // a charm) or that the user blacklisted; mood/energy recovery beats a guaranteed-bad turn.
                val skippedForced = training.skippedTrainingMap[forcedStat]
                val forcedIsBlacklisted = forcedStat in training.blacklist
                if (skippedForced != null || forcedIsBlacklisted) {
                    val reason = skippedForced?.skipReason ?: "blacklisted"
                    MessageLog.w(
                        TAG,
                        "[WARN] handleTrackblazerTraining:: Cannot force $forcedStat training ($reason). Backing out for recovery instead.",
                    )

                    training.firstTrainingCheck = false
                    ButtonBack.click(game.imageUtils)
                    game.wait(1.0)

                    if (checkMainScreen()) {
                        if (trainee.mood == Mood.AWFUL || (trainee.mood <= Mood.NORMAL && trainee.energy >= 20)) {
                            MessageLog.i(TAG, "[TRACKBLAZER] Mood is ${trainee.mood}. Attempting to recover mood.")
                            recoverMood()
                        } else {
                            MessageLog.i(TAG, "[TRACKBLAZER] Energy is ${trainee.energy}%. Attempting to recover energy.")
                            recoverEnergy()
                        }
                    }
                } else {
                    MessageLog.i(
                        TAG,
                        "[TRACKBLAZER] Still no suitable training found. Energy (${trainee.energy}%) and Mood (${trainee.mood}) are sufficient. Forcing $forcedStat training.",
                    )
                    training.executeTraining(forcedStat)
                    bCompletedTrainingThisTurn = true
                    training.firstTrainingCheck = false
                }
            }
        }

        bIsIrregularTraining = false
    }

    /**
     * Executes the logic meant for the Race Prep screen of scheduled races,
     * specifically to use race items if appropriate.
     */
    override fun onScheduledRacePrepScreen() {
        var grade = racing.lastRaceGrade
        var fans = racing.lastRaceFans

        // For Finale races (turns 73, 74, 75), manually set the grade to G1 and appropriate fans.
        // This ensures the racing item logic is triggered for these mandatory races.
        if (date.bIsFinaleSeason && (date.day == 73 || date.day == 74 || date.day == 75)) {
            grade = RaceGrade.G1
            racing.lastRaceGrade = RaceGrade.FINALE
            fans = if (date.day == 75) 30000 else 10000
        }

        if (grade != null && (grade == RaceGrade.G1 || grade == RaceGrade.G2 || grade == RaceGrade.G3)) {
            MessageLog.i(TAG, "[TRACKBLAZER] Executing scheduled race item logic on Race Prep screen.")
            useRaceItems(grade, fans)
        }
    }

    /**
     * Uses race-related items (Hammers, Glow Sticks) based on the race grade and fan count.
     *
     * @param grade The grade of the detected race.
     * @param fans The number of fans awarded by the race.
     */
    private fun useRaceItems(grade: RaceGrade, fans: Int) {
        if (date.day < 13 || bUsedHammerToday) {
            if (bUsedHammerToday) {
                MessageLog.i(TAG, "[TRACKBLAZER] Already used a race item today.")
            }
            return
        }

        val masterHammerCount = currentInventory["Master Cleat Hammer"] ?: 0
        val artisanHammerCount = currentInventory["Artisan Cleat Hammer"] ?: 0
        val glowSticksCount = currentInventory["Glow Sticks"] ?: 0

        val hasMasterHammer =
            if (date.day == 73) {
                // Twinkle Star Climax race 1 of 3. Need ≥3 to chain through all 3 races.
                masterHammerCount >= 3
            } else if (date.day == 74) {
                // Twinkle race 2 of 3. Need ≥2 (one for this and one for the Final).
                masterHammerCount >= 2
            } else if (date.day == 75) {
                // Twinkle Final. Use the last reserved hammer.
                masterHammerCount >= 1
            } else {
                // Pre-climax: reserve 3 Masters for the 3 Twinkle Climax races. The climax
                // races have the highest stat-return per hammer of the entire run, so 3
                // Masters must be hoarded. Excess (4+) can be spent on regular G1s before
                // the climax.
                masterHammerCount > 3
            }
        val hasArtisanHammer =
            if (date.day == 73) {
                // Save the last Artisan Cleat Hammer for the Semi-Final and Final (turns 74-75).
                artisanHammerCount >= 2
            } else {
                artisanHammerCount > 0
            }
        val hasGlowSticks =
            if (date.day in 73..74) {
                // Save the last Glow Stick for the Finals (turn 75).
                glowSticksCount >= 2
            } else {
                glowSticksCount > 0
            }

        val hammerToUse =
            if (grade == RaceGrade.G1) {
                if (hasMasterHammer) {
                    "Master Cleat Hammer"
                } else if (hasArtisanHammer) {
                    "Artisan Cleat Hammer"
                } else {
                    null
                }
            } else if (grade == RaceGrade.G2 || grade == RaceGrade.G3) {
                if (hasArtisanHammer) "Artisan Cleat Hammer" else null
            } else {
                null
            }

        val useGlowSticks =
            if (date.day >= 73) {
                // During Finale races (turns 73-75), ignore the standard 20k fan requirement.
                grade == RaceGrade.G1 && hasGlowSticks
            } else {
                grade == RaceGrade.G1 && fans >= 20000 && hasGlowSticks
            }

        if (hammerToUse != null || useGlowSticks) {
            MessageLog.i(TAG, "[TRACKBLAZER] Suitable race items found in inventory (Hammer: $hammerToUse, Glow Sticks: $useGlowSticks). Opening Training Items dialog.")
            if (shopList.openTrainingItemsDialog()) {
                val itemsToUseList = mutableListOf<String>()
                if (hammerToUse != null) itemsToUseList.add(hammerToUse)
                if (useGlowSticks) itemsToUseList.add("Glow Sticks")

                // Pass the reasoning and trigger a single consolidated usage summary.
                val itemsUsed = shopList.useSpecificItems(itemsToUseList, bUseAll = false, reason = "Race bonus for $grade.")
                itemsUsed.forEach { (name, _) ->
                    useInventoryItem(name)
                }

                if (itemsUsed.isNotEmpty()) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Queued ${itemsUsed.size} race items for $grade ($fans fans). Confirming usage.")
                    confirmAndCloseItemDialog(itemsUsed.size)
                    bUsedHammerToday = true
                } else {
                    if (ButtonClose.click(game.imageUtils)) {
                        game.wait(game.dialogWaitDelay)
                    }
                }
            }
        } else {
            if (date.day == 73 && (masterHammerCount > 0 || artisanHammerCount > 0 || glowSticksCount > 0)) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Conserving race items for Semi-Final/Final (turns 74-75). " +
                        "Hammer: ${masterHammerCount + artisanHammerCount}, Glow Sticks: $glowSticksCount.",
                )
            } else {
                MessageLog.i(TAG, "[TRACKBLAZER] No relevant race items in cached inventory for $grade.")
            }
        }
    }

    /**
     * Orchestrates the usage of items based on dynamic conditions and updates internal inventory.
     * Consolidates synchronization and item usage into a single pass for efficiency.
     *
     * @param trainee Reference to the trainee's state. If provided, conditional items will be used.
     * @param trainingSelected The stat name of the selected training to help with item usage (e.g. Ankle Weights).
     * @param bQuickUseOnly If true, only items marked for quick use will be used.
     * @param bDryRun If true, only logs intentions without performing any clicks.
     */
    fun manageInventoryItems(trainee: Trainee? = null, trainingSelected: StatName? = null, bQuickUseOnly: Boolean = false, bDryRun: Boolean = false) {
        if (date.day < 13 && !bDryRun) return

        MessageLog.i(TAG, "[TRACKBLAZER] Starting inventory management pass.")
        bKaleJuiceQueuedThisPass = false
        val initialEnergy = trainee?.energy ?: 0
        val initialMood = trainee?.mood ?: Mood.NORMAL
        val initialMegaphoneTurnCounter = trainee?.megaphoneTurnCounter ?: 0
        val nextInventory = currentInventory.toMutableMap()
        val scannedItemsList = mutableListOf<ScannedItem>()
        var itemsUsedCount = 0
        var wasEarlyExit = false

        // To improve efficiency, we identify which items we are actually interested in based on our cached inventory.
        // If we have a cached inventory and have seen all items of interest, we can exit the scroll loop early.
        val remainingItemsOfInterest =
            if (currentInventory.isNotEmpty()) {
                val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
                val neededWeight =
                    when (trainingSelected) {
                        StatName.SPEED -> "Speed Ankle Weights"
                        StatName.STAMINA -> "Stamina Ankle Weights"
                        StatName.POWER -> "Power Ankle Weights"
                        StatName.GUTS -> "Guts Ankle Weights"
                        else -> ""
                    }

                currentInventory
                    .filter { (name, count) ->
                        if (count <= 0) return@filter false

                        val info = shopList.shopItems[name]
                        val isStat = info?.category == "Stats"
                        val isBad = info?.category == "Heal Bad Conditions"
                        val isQuick = info?.isQuickUsage == true
                        val isEnergy = shopList.energyItemNames.contains(name) || name == "Royal Kale Juice"
                        val isMood = name == "Berry Sweet Cupcake" || name == "Plain Cupcake"
                        val isMegaphone = name == "Empowering Megaphone" || name == "Motivating Megaphone" || name == "Coaching Megaphone"
                        val isAnkleWeight = name == neededWeight
                        val isCharm = name == "Good-Luck Charm" && failureChance >= 20

                        // Determine if this item is actually useful right now.
                        // isBad items are also isQuick, but they must clear the condition-match gate; let the isBad clause own them.
                        val isUseful =
                            isStat ||
                                (isBad && trainee != null && canHealActiveNegativeStatus(name, trainee)) ||
                                (isQuick && !isBad) ||
                                (isEnergy && trainee != null && trainee.energy <= 100) ||
                                // We might want any energy item if not full.
                                (isMood && trainee != null && trainee.mood < Mood.GREAT) ||
                                (isMegaphone && trainee != null && trainingSelected != null && trainee.megaphoneTurnCounter == 0 && !shouldConserveTrainingEffectItems(trainingSelected, trainee)) ||
                                (isAnkleWeight && trainee != null && trainingSelected != null) ||
                                (isCharm && trainee != null && trainingSelected != null && !shouldConserveTrainingEffectItems(trainingSelected, trainee))

                        isUseful
                    }.keys
                    .toMutableSet()
            } else {
                mutableSetOf()
            }

        if (remainingItemsOfInterest.isEmpty() && bInventorySynced) {
            MessageLog.i(TAG, "[TRACKBLAZER] No items of interest found in cached inventory and already synced. Skipping scan.")
        } else if (remainingItemsOfInterest.isNotEmpty()) {
            MessageLog.i(TAG, "[TRACKBLAZER] Items of interest for this pass: ${remainingItemsOfInterest.joinToString(", ")}.")
        }

        val itemsUsedWithReasons = mutableListOf<Pair<String, String>>()
        val itemNameMapInManage = mutableMapOf<Int, String>()
        // Snapshot energy at pass start so the threshold gate stays open after earlier items in the
        // same pass raise `trainee.energy`. Greedy selection in isBestEnergyItemToUse still picks which.
        val passStartEnergy = trainee?.energy ?: 0
        shopList.processItemsWithFallback(
            keyExtractor = { entry ->
                val name = shopList.getShopItemName(entry, ButtonSkillUp.checkDisabled(game.imageUtils, entry.bitmap) == true)
                if (name != null) itemNameMapInManage[entry.index] = name
                name
            },
        ) { entry ->
            val isDisabled = ButtonSkillUp.checkDisabled(game.imageUtils, entry.bitmap) == true
            val itemName = itemNameMapInManage[entry.index] ?: shopList.getShopItemName(entry, isDisabled)

            if (itemName != null) {
                Log.d(TAG, "[DEBUG] buyItems:: Detected item \"$itemName\" (Disabled: $isDisabled) at index ${entry.index}.")
                scannedItemsList.add(ScannedItem(entry, itemName, isDisabled))

                // Sync Inventory.
                val amount = shopList.getItemAmount(entry, isDisabled)
                nextInventory[itemName] = amount

                // Inline usage logic.
                if (!bDryRun) {
                    val isStat = shopList.statItemNames.contains(itemName)
                    val isBad = shopList.badConditionHealItemNames.contains(itemName)
                    val itemInfo = shopList.shopItems[itemName]
                    val isQuick = itemInfo != null && itemInfo.isQuickUsage

                    if (bQuickUseOnly) {
                        if (isQuick && !isDisabled) {
                            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Using quick-use item: \"$itemName\".", nextInventory)) {
                                itemsUsedCount++
                                val reason =
                                    when {
                                        isStat -> "Marked as quick-use."
                                        itemInfo?.category == "Bond" -> "Marked as quick-use."
                                        itemInfo?.category == "Get Good Conditions" -> "Acquired good condition: ${getStatusEffectName(itemName)}."
                                        else -> "Marked as quick-use."
                                    }
                                itemsUsedWithReasons.add(itemName to reason)
                            }
                        }
                    } else {
                        if (isStat && !isDisabled) {
                            var clicks = 0
                            while (true) {
                                val reason = "Marked as quick-use."
                                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing stat item: \"$itemName\".", nextInventory, recheck = clicks > 0, reason = reason)) {
                                    itemsUsedCount++
                                    clicks++
                                    itemsUsedWithReasons.add(itemName to reason)
                                    if (clicks >= 5) break
                                    game.wait(0.2)
                                } else {
                                    break
                                }
                            }
                        } else if (isBad && !isDisabled && trainee?.currentNegativeStatuses?.isNotEmpty() == true) {
                            val reason = "Healed status effect: ${trainee.currentNegativeStatuses.joinToString(", ")}."
                            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing bad condition item: \"$itemName\".", nextInventory, reason = reason)) {
                                itemsUsedCount++
                                itemsUsedWithReasons.add(itemName to reason)
                            }
                        } else if (isQuick && !isDisabled) {
                            val reason =
                                when {
                                    itemInfo?.category == "Bond" -> "Marked as quick-use."
                                    itemInfo?.category == "Get Good Conditions" -> "Acquired status effect: ${getStatusEffectName(itemName)}."
                                    else -> "Marked as quick-use."
                                }
                            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing quick-use item: \"$itemName\".", nextInventory, reason = reason)) {
                                itemsUsedCount++
                                itemsUsedWithReasons.add(itemName to reason)
                                if (itemName == "Energy Drink MAX") {
                                    trainee?.energy = (trainee?.energy ?: 100) + 5
                                }
                            }
                        } else if (trainee != null) {
                            // Handle Energy, Mood, Ankle Weights, Charm, Megaphones, etc.
                            val reason = handleInlineUsage(trainee, itemName, entry, isDisabled, trainingSelected, nextInventory, remainingItemsOfInterest, passStartEnergy)
                            if (reason != null) {
                                itemsUsedCount++
                                itemsUsedWithReasons.add(itemName to reason)
                            }
                        }
                    }
                }

                if (remainingItemsOfInterest.contains(itemName)) {
                    remainingItemsOfInterest.remove(itemName)
                }
            } else {
                MessageLog.w(TAG, "[WARN] manageInventoryItems:: Failed to detect item name at index ${entry.index}.")
            }

            // Early exit if we've seen all items of interest.
            // We only allow early exit if the inventory has already been fully synced.
            if (remainingItemsOfInterest.isEmpty() && bInventorySynced) {
                MessageLog.i(TAG, "[TRACKBLAZER] All items of interest processed. Exiting scan early.")
                wasEarlyExit = true
                true
            } else {
                false
            }
        }

        // Finalize Sync.
        if (!wasEarlyExit) {
            val scannedItemNames = scannedItemsList.map { it.itemName }.toSet()
            nextInventory.keys.forEach { name ->
                if (!scannedItemNames.contains(name) && (nextInventory[name] ?: 0) > 0) {
                    nextInventory[name] = 0
                }
            }
        }
        currentInventory = nextInventory.toMap()
        bInventorySynced = true

        // Log reasoning for item usage decisions made during this pass, incorporating the inventory summary.
        if (trainee != null || bDryRun) {
            val stateContext =
                if (trainee != null) {
                    val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
                    buildString {
                        val stateList = listOf("Energy=$initialEnergy%", "Mood=$initialMood", "Megaphone Turn=$initialMegaphoneTurnCounter", "Coins=$shopCoins")
                        appendLine("Current State: ${stateList.joinToString(", ")}")
                        if (trainingSelected != null) {
                            val failureInfo = if (failureChance > 0) " (Fail: $failureChance%)" else ""
                            append("Selected Training: $trainingSelected$failureInfo")
                        }
                    }.trimEnd()
                } else {
                    null
                }
            shopList.printItemUsageSummary(itemsUsedWithReasons, stateContext)
        }

        if (itemsUsedCount > 0 && !bDryRun) {
            confirmAndCloseItemDialog(itemsUsedCount)
        } else if (!bDryRun) {
            if (ButtonClose.click(game.imageUtils, tries = 30)) {
                game.wait(game.dialogWaitDelay)
            }
        }
    }

    /**
     * Map item names to their specific good status effect names.
     *
     * @param itemName The name of the item.
     * @return The status effect name.
     */
    private fun getStatusEffectName(itemName: String): String {
        return when (itemName) {
            "Pretty Mirror" -> "Charming ○"
            "Reporter's Binoculars" -> "Hot Topic"
            "Master Practice Guide" -> "Practice Perfect ○"
            "Scholar's Hat" -> "Fast Learner"
            else -> "null"
        }
    }

    /**
     * Handles usage of a specific item discovered during the scan loop.
     *
     * @param trainee Reference to the trainee's state.
     * @param itemName The name of the item detected.
     * @param entry The ScrollListEntry of the item.
     * @param isDisabled Whether the item is disabled in the UI.
     * @param trainingSelected The stat name of the selected training.
     * @param nextInventory The updated inventory map reflecting changes in this pass.
     * @param remainingItemsOfInterest The set of items we are still looking for.
     * @param passStartEnergy Trainee energy snapshotted at the start of the pass; used by the
     *   energy-item threshold gate so it does not close mid-pass after earlier items raise energy.
     * @return The specific reason why the item was used, or null if not used.
     */
    private fun handleInlineUsage(
        trainee: Trainee,
        itemName: String,
        entry: ScrollListEntry,
        isDisabled: Boolean,
        trainingSelected: StatName?,
        nextInventory: MutableMap<String, Int>,
        remainingItemsOfInterest: Set<String>,
        passStartEnergy: Int,
    ): String? {
        // Cupcakes captured before Royal Kale Juice was queued will read as disabled (mood was
        // still GREAT at scan time). Bypass the early-return when the flag is set so the
        // recheck=true bitmap can decide; the game's dialog enables them once Juice is queued.
        val isCupcake = itemName == "Berry Sweet Cupcake" || itemName == "Plain Cupcake"
        if (isDisabled && !(isCupcake && bKaleJuiceQueuedThisPass)) {
            MessageLog.v(TAG, "[TRACKBLAZER] Item \"$itemName\" read as disabled in dialog, so skipping its usage.")
            return null
        }

        // Ankle Weights Check.
        if (date.day >= 13 && trainingSelected != null) {
            val neededWeight =
                when (trainingSelected) {
                    StatName.SPEED -> "Speed Ankle Weights"
                    StatName.STAMINA -> "Stamina Ankle Weights"
                    StatName.POWER -> "Power Ankle Weights"
                    StatName.GUTS -> "Guts Ankle Weights"
                    else -> ""
                }
            if (itemName == neededWeight) {
                val reason = "Boosting $trainingSelected training gains."
                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName via inline pass.", nextInventory, reason = reason)) {
                    return reason
                }
            }
        }

        // Good-Luck Charm Check.
        val failureChance = training.trainingMap[trainingSelected]?.failureChance ?: 0
        if (date.day >= 13 && !bUsedCharmToday && failureChance >= 20 && itemName == "Good-Luck Charm") {
            // Below NORMAL mood the multiplier caps gain; burning Charm on a low-gain training wastes its
            // 0%-failure benefit, so conserve for a higher-gain turn.
            if (shouldConserveTrainingEffectItems(trainingSelected, trainee)) {
                val selectedMainGain = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Skipping Good-Luck Charm: mood=${trainee.mood}, selected $trainingSelected main gain ($selectedMainGain) below floor ($lowMainStatGainItemFloor). Conserving Charm for a higher-gain turn.",
                )
                return null
            }
            val reason = "Setting training failure chance to 0%."
            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing Good-Luck Charm via inline pass.", nextInventory, reason = reason)) {
                bUsedCharmToday = true
                return reason
            }
        }

        // If a Good-Luck Charm is (or will be) queued this turn, skip energy items: the Charm sets failure
        // to 0% regardless of energy and the energy cost is subtracted after training, so they'd be wasted.
        val charmBeingUsedThisTurn =
            bUsedCharmToday ||
                (date.day >= 13 && failureChance >= 20 && (nextInventory["Good-Luck Charm"] ?: 0) > 0)

        // Energy Items Check.
        if (!charmBeingUsedThisTurn && passStartEnergy <= energyThresholdToUseEnergyItems && shopList.energyItemNames.contains(itemName)) {
            // Conservation: always keep the last unit of the lowest-level energy item for emergency race recovery.
            if (!bForceUseReservedItem) {
                val conserveItem = energyItemConservationOrder.firstOrNull { (nextInventory[it] ?: 0) > 0 }
                if (conserveItem == itemName && (nextInventory[itemName] ?: 0) <= 1) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Conserving last $itemName for emergency race recovery.")
                    return null
                }
            }

            if (isBestEnergyItemToUse(trainee, itemName, nextInventory, remainingItemsOfInterest)) {
                val gain = energyGains[itemName] ?: 0
                val reason = "Restored energy (current: ${trainee.energy}%, pass start: $passStartEnergy%) because it fell below the $energyThresholdToUseEnergyItems% threshold."
                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName for use (Energy: ${trainee.energy}%, Gain: +$gain).", nextInventory, reason = reason)) {
                    val oldEnergy = trainee.energy
                    trainee.energy = (trainee.energy + gain).coerceAtMost(100)
                    MessageLog.i(TAG, "[TRACKBLAZER] Trainee energy updated: $oldEnergy% -> ${trainee.energy}%.")
                    return reason
                }
            }
        }

        // Royal Kale Juice Check (also skipped when Charm is being used).
        if (!charmBeingUsedThisTurn && itemName == "Royal Kale Juice") {
            val hasMoodItems = nextInventory.any { (name, count) -> count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake") }
            val moodConditionMet = trainee.energy <= 20 || hasMoodItems || trainee.mood == Mood.AWFUL
            val shouldUse = isBestEnergyItemToUse(trainee, itemName, nextInventory, remainingItemsOfInterest) && moodConditionMet

            if (shouldUse) {
                val oldEnergy = trainee.energy
                val reason =
                    if (oldEnergy <= 20) {
                        "Restored energy (current: $oldEnergy%) as a last resort (below 20%)."
                    } else {
                        "Restored energy (current: $oldEnergy%) while having mood recovery items available to offset the Mood decrease."
                    }
                if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName for use (Energy: ${trainee.energy}%, Mood: ${trainee.mood}).", nextInventory, reason = reason)) {
                    val oldMood = trainee.mood
                    trainee.energy = (trainee.energy + 100).coerceAtMost(100)
                    trainee.mood = trainee.mood.decrement()
                    bKaleJuiceQueuedThisPass = true
                    MessageLog.i(TAG, "[TRACKBLAZER] Trainee energy and mood updated: $oldEnergy% -> ${trainee.energy}%, $oldMood -> ${trainee.mood}.")
                    return reason
                }
            }
        }

        // Mood Items Check.
        // The Kale-Juice-queued clause fires a cupcake in the same pass to offset the -1 mood
        // penalty. Without it the `mood <= NORMAL && energy < 70` gate stays shut after Kale
        // Juice (mood drops to GOOD, energy jumps to 100), wasting the conserved reserve.
        val moodDroppedByKaleJuice = isCupcake && bKaleJuiceQueuedThisPass
        val shouldUseMoodItem = (trainee.mood <= Mood.NORMAL && trainee.energy < 70) || moodDroppedByKaleJuice
        if (shouldUseMoodItem && isCupcake) {
            // Conservation: always keep at least 1 cupcake in case Royal Kale Juice is purchased later.
            // Prefer conserving Plain Cupcake (+1 mood) since Kale Juice is -1 mood and we can avoid waste from Berry Sweet (+2).
            // Bypassed when Kale Juice was queued this pass: the reserved-for event is happening right now, so spend it.
            if (!bKaleJuiceQueuedThisPass) {
                val plainCount = nextInventory["Plain Cupcake"] ?: 0
                val berryCount = nextInventory["Berry Sweet Cupcake"] ?: 0
                val shouldConserve =
                    (itemName == "Plain Cupcake" && plainCount <= 1) ||
                        (itemName == "Berry Sweet Cupcake" && berryCount <= 1 && plainCount == 0)
                if (shouldConserve) {
                    MessageLog.i(TAG, "[TRACKBLAZER] Conserving last $itemName for potential Royal Kale Juice usage.")
                    return null
                }
            }

            // Recheck reads a fresh bitmap when Kale Juice was queued earlier this pass, because
            // the captured bitmap shows the pre-Juice disabled state (mood was still GREAT at
            // scan time). The game's dialog enables cupcakes after Juice is queued.
            val reason =
                if (moodDroppedByKaleJuice) {
                    "Offsetting Royal Kale Juice's -1 mood penalty (mood: ${trainee.mood} post-Juice)."
                } else {
                    "Recovering mood (current: ${trainee.mood}, energy: ${trainee.energy}% < 70%)."
                }
            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing $itemName for mood recovery.", nextInventory, recheck = moodDroppedByKaleJuice, reason = reason)) {
                val oldMood = trainee.mood
                // Cupcakes are additive, not absolute: Plain = +1 mood, Berry Sweet = +2.
                // Mood.increment() caps at GREAT, so over-stacking is safe.
                trainee.mood = trainee.mood.increment()
                if (itemName == "Berry Sweet Cupcake") {
                    trainee.mood = trainee.mood.increment()
                }
                // Clear the flag so a second cupcake in the same pass doesn't double-spend when
                // one is already enough to offset the -1.
                if (moodDroppedByKaleJuice) bKaleJuiceQueuedThisPass = false
                MessageLog.i(TAG, "[TRACKBLAZER] Trainee mood updated: $oldMood -> ${trainee.mood}.")
                return reason
            }
        }

        // Megaphone Check.
        val megaphoneNames = listOf("Empowering Megaphone", "Motivating Megaphone", "Coaching Megaphone")
        if (trainee.megaphoneTurnCounter == 0 && trainingSelected != null && megaphoneNames.contains(itemName)) {
            // Below NORMAL mood the multiplier caps gain. Megaphones multiply gain across several turns,
            // so spending one on a low-gain training is worse than conserving for a better turn.
            if (shouldConserveTrainingEffectItems(trainingSelected, trainee)) {
                val selectedMainGain = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Skipping $itemName: mood=${trainee.mood}, selected $trainingSelected main gain ($selectedMainGain) below floor ($lowMainStatGainItemFloor). Conserving Megaphone for a higher-gain turn.",
                )
                return null
            }

            // Per-tier stat threshold: hold a higher-effect megaphone on a low-gain turn. Mood-independent and
            // stacked on top of the mood-coupled conservation above.
            val selectedMainGain = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
            val threshold = megaphoneThresholds[itemName] ?: 0
            if (selectedMainGain < threshold) {
                MessageLog.i(
                    TAG,
                    "[TRACKBLAZER] Skipping $itemName: selected $trainingSelected main gain ($selectedMainGain) below its threshold ($threshold). A lower-tier megaphone may still be used this turn.",
                )
                return null
            }

            // Fire only the best eligible tier this turn: if a stronger megaphone is on hand and also clears its
            // threshold, hold this one and let the stronger row fire. With all thresholds at 0 this reproduces the
            // prior best-available-tier behavior exactly.
            val bestEligible = MegaphoneSelection.bestEligibleMegaphone(selectedMainGain, nextInventory, megaphoneThresholds)
            if (bestEligible != itemName) {
                return null
            }

            val reason = "Increasing training gains for the next few turns."
            if (clickItemPlusButton(itemName, entry, "[TRACKBLAZER] Queuing best eligible megaphone: \"$itemName\".", nextInventory, reason = reason)) {
                trainee.megaphoneTurnCounter = MegaphoneSelection.durationFor(itemName)
                return reason
            }
        }

        return null
    }

    /**
     * Returns the energy item currently conserved as the last-resort emergency-race-recovery stash.
     * Mirrors the conservation logic in `isBestEnergyItemToUse` so the dialog-open gate predicts the
     * same outcome: if the only energy item is the conserved one, the dialog would pick nothing, so
     * skip it.
     *
     * @param inventory The inventory snapshot to evaluate.
     * @return The conserved item name, or `null` if conservation is bypassed or none is conservable.
     */
    private fun getConservedEnergyItem(inventory: Map<String, Int>): String? {
        if (bForceUseReservedItem) return null
        return energyItemConservationOrder.firstOrNull { (inventory[it] ?: 0) > 0 }
    }

    /**
     * Conserve training-effect items (Megaphones, Good-Luck Charm) this turn when mood is below NORMAL
     * AND the selected training's main gain is below the floor. Mirrors the inline checks in
     * `handleInlineUsage()` so the Training Items dialog can be short-circuited upfront.
     *
     * @param trainingSelected The training about to execute (null = no selection).
     * @param trainee The current trainee snapshot (mood is read).
     * @return True if Megaphone/Charm should be skipped this turn.
     */
    private fun shouldConserveTrainingEffectItems(trainingSelected: StatName?, trainee: Trainee?): Boolean {
        if (trainingSelected == null || trainee == null) return false
        if (trainee.mood >= Mood.NORMAL) return false
        val selectedMainGain = training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0
        return selectedMainGain < lowMainStatGainItemFloor
    }

    /**
     * True when the heal item targets at least one active negative status. Miracle Cure heals every
     * status; every other `badConditionMap` entry heals exactly one. Used to short-circuit the
     * Training Items dialog when no item can clear an active condition.
     *
     * @param itemName The name of the item to check.
     * @param trainee The current trainee snapshot (currentNegativeStatuses is read).
     * @return True if the item can heal an active negative status; false otherwise.
     */
    private fun canHealActiveNegativeStatus(itemName: String, trainee: Trainee): Boolean {
        if (itemName == "Miracle Cure") return true
        val target = badConditionMap[itemName] ?: return false
        return trainee.currentNegativeStatuses.contains(target)
    }

    private fun useItems(trainee: Trainee, trainingSelected: StatName? = null) {
        if (date.day < 13) return

        val needSync = !bInventorySynced
        val conservedEnergyItem = getConservedEnergyItem(currentInventory)
        val hasEnergyItems =
            currentInventory.any { (name, count) ->
                val effectiveCount = if (name == conservedEnergyItem) count - 1 else count
                effectiveCount > 0 && shopList.energyItemNames.contains(name)
            } ||
                ((currentInventory["Royal Kale Juice"] ?: 0) > 0)
        val hasMoodItems = currentInventory.any { (name, count) -> count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake") }
        val hasBadConditionItems = currentInventory.any { (name, count) -> count > 0 && shopList.badConditionHealItemNames.contains(name) && canHealActiveNegativeStatus(name, trainee) }
        val hasStatItems = currentInventory.any { (name, count) -> count > 0 && shopList.statItemNames.contains(name) }

        val skipTrainingEffectItems = shouldConserveTrainingEffectItems(trainingSelected, trainee)
        val selectedMainGainForMegaphone =
            if (trainingSelected != null) training.cachedAnalysisResults?.firstOrNull { it.name == trainingSelected }?.statGains?.get(trainingSelected) ?: 0 else 0
        val hasMegaphones =
            !skipTrainingEffectItems &&
                trainingSelected != null &&
                trainee.megaphoneTurnCounter == 0 &&
                MegaphoneSelection.bestEligibleMegaphone(selectedMainGainForMegaphone, currentInventory, megaphoneThresholds) != null
        val hasAnkleWeights =
            trainingSelected != null &&
                currentInventory.any { (name, count) ->
                    count > 0 &&
                        name ==
                        when (trainingSelected) {
                            StatName.SPEED -> "Speed Ankle Weights"
                            StatName.STAMINA -> "Stamina Ankle Weights"
                            StatName.POWER -> "Power Ankle Weights"
                            StatName.GUTS -> "Guts Ankle Weights"
                            else -> ""
                        }
                }
        val failureChance = if (trainingSelected != null) training.trainingMap[trainingSelected]?.failureChance ?: 0 else 0
        val hasCharm = !skipTrainingEffectItems && trainingSelected != null && !bUsedCharmToday && failureChance >= 20 && (currentInventory["Good-Luck Charm"] ?: 0) > 0

        val potentialUse =
            (trainee.energy <= energyThresholdToUseEnergyItems && hasEnergyItems) ||
                (trainee.mood <= Mood.NORMAL && trainee.energy < 70 && hasMoodItems) ||
                (trainee.currentNegativeStatuses.isNotEmpty() && hasBadConditionItems) ||
                hasStatItems ||
                hasMegaphones ||
                hasAnkleWeights ||
                hasCharm

        if (needSync || potentialUse) {
            val reasons = mutableListOf<String>()
            if (needSync) reasons.add("Sync needed")
            if (trainee.energy <= energyThresholdToUseEnergyItems && hasEnergyItems) reasons.add("Low energy")
            if (trainee.mood <= Mood.NORMAL && trainee.energy < 70 && hasMoodItems) reasons.add("Low mood")
            if (trainee.currentNegativeStatuses.isNotEmpty() && hasBadConditionItems) reasons.add("Bad conditions")
            if (hasStatItems) reasons.add("Stat items available")
            if (hasMegaphones) reasons.add("Megaphone available")
            if (hasAnkleWeights) reasons.add("Ankle weights available")
            if (hasCharm) reasons.add("Good-luck charm available")

            MessageLog.i(TAG, "[TRACKBLAZER] Opening Training Items dialog (${reasons.joinToString(", ")})...")
            if (shopList.openTrainingItemsDialog()) {
                manageInventoryItems(trainee, trainingSelected)
            }
        } else {
            MessageLog.i(TAG, "[TRACKBLAZER] Skipping Training Items dialog as no relevant items are in the cached inventory.")
        }
    }

    /**
     * Returns a formatted summary of the current inventory categorized with item amounts.
     *
     * @param withDividers If true, includes the standard "Current Inventory" dividers and footer.
     * @return Formatted inventory summary string.
     */
    fun getInventorySummary(withDividers: Boolean = false): String {
        // Group items by category from the central shopItems mapping.
        val inventoryByCategory =
            currentInventory.filter { it.value > 0 }.keys.groupBy { itemName ->
                shopList.shopItems[itemName]?.category ?: "Other"
            }

        val summary =
            if (withDividers) {
                StringBuilder("\n============== Current Inventory ==============\n")
            } else {
                StringBuilder("\n[Current Inventory]\n")
            }

        var hasItems = false

        // Sort categories to maintain consistent order (Stats first, then others).
        val categoryOrder = listOf("Stats", "Energy and Motivation", "Bond", "Get Good Conditions", "Heal Bad Conditions", "Training Facilities", "Training Effects", "Races")
        val sortedCategories =
            inventoryByCategory.keys.sortedWith(
                compareBy { category ->
                    val index = categoryOrder.indexOf(category)
                    if (index == -1) categoryOrder.size else index
                },
            )

        sortedCategories.forEach { category ->
            val items = inventoryByCategory[category] ?: emptyList()
            if (items.isNotEmpty()) {
                summary.append("\n$category\n")
                items.sorted().forEach { name ->
                    summary.append("- $name: ${currentInventory[name]}\n")
                }
                hasItems = true
            }
        }

        if (!hasItems) {
            if (bInventorySynced) {
                summary.append("\nInventory is empty.\n")
            } else {
                summary.append("\nInventory has not been scanned yet.\n")
            }
        }

        if (withDividers) {
            summary.append("\n===============================================")
        }

        return summary.toString()
    }

    /**
     * Whether the current energy item belongs in the best combination of available energy items. Greedy
     * maximization with a small overshoot above 100% allowed, so a larger combined gain (Vita 65 + 40 =
     * 105) beats a strictly-under-100 one (65 + 20 = 85).
     *
     * @param trainee The trainee's current state.
     * @param itemName The name of the item being considered.
     * @param nextInventory The current inventory counts reflecting changes in this pass.
     * @param remainingItemsOfInterest The set of items we still expect to encounter in the current pass.
     * @return True if this item should be used, false otherwise.
     */
    private fun isBestEnergyItemToUse(trainee: Trainee, itemName: String, nextInventory: Map<String, Int>, remainingItemsOfInterest: Set<String>): Boolean {
        val currentGain = energyGains[itemName] ?: return false
        val currentEnergy = trainee.energy

        val hasMoodItems = nextInventory.any { (name, count) -> count > 0 && (name == "Berry Sweet Cupcake" || name == "Plain Cupcake") }
        val isKaleJuiceUsable = currentEnergy <= 20 || hasMoodItems || trainee.mood == Mood.AWFUL

        // Royal Kale Juice "Last Resort" logic: If energy is very low, we prioritize Kale Juice over everything.
        // It gives 100, so any other energy item used first would be wasted.
        if (currentEnergy <= 20 && isKaleJuiceUsable) {
            val hasKaleJuice =
                (itemName == "Royal Kale Juice") ||
                    (nextInventory["Royal Kale Juice"] ?: 0) > 0 ||
                    remainingItemsOfInterest.contains("Royal Kale Juice")
            if (hasKaleJuice) {
                return itemName == "Royal Kale Juice"
            }
        }

        // Collect all available energy items from this scan pass.
        // Always reserve one unit of the lowest-tier item for emergency race recovery, unless force-override is active.
        val availableEnergyItems = mutableListOf<Int>()
        val conserveItem = if (!bForceUseReservedItem) energyItemConservationOrder.firstOrNull { (nextInventory[it] ?: 0) > 0 } else null
        remainingItemsOfInterest.forEach { name ->
            val gain = energyGains[name]
            if (gain != null) {
                // If this is Kale Juice, only include it if it's usable.
                if (name == "Royal Kale Juice" && !isKaleJuiceUsable) return@forEach

                var count = (nextInventory[name] ?: 0)

                // Exclude one unit of the conserved item from the greedy pool.
                if (name == conserveItem && count > 0) {
                    count--
                }

                repeat(count) { availableEnergyItems.add(gain) }
            }
        }

        // Safety net: if the current item was not counted via remainingItemsOfInterest (already-removed edge case),
        // make sure the greedy sees it as an available option.
        if (!remainingItemsOfInterest.contains(itemName)) {
            availableEnergyItems.add(currentGain)
        }

        // Sort gains descending for greedy selection.
        availableEnergyItems.sortDescending()

        // Greedy with soft overshoot: prefer combinations that approach 100% even if they exceed it by up to 10.
        // This prefers Vita 65 + Vita 40 (= 105) over Vita 65 + Vita 20 (= 85) so we don't leave ~15% on the table.
        val overshootCap = 110
        var simulatedEnergy = currentEnergy
        val pickedEnergyItems = mutableListOf<Int>()
        for (gain in availableEnergyItems) {
            if (simulatedEnergy + gain <= overshootCap) {
                simulatedEnergy += gain
                pickedEnergyItems.add(gain)
            }
        }

        // If currentGain was one of the picked items, use it.
        return pickedEnergyItems.contains(currentGain)
    }
}
