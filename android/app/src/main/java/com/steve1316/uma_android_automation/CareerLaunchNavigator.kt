package com.steve1316.uma_android_automation

import android.content.Context
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.components.*
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of a navigation attempt between runs.
 *
 * @property success Whether navigation completed successfully.
 * @property lastDetectedState The last screen state detected before success or failure.
 * @property failureReason A human-readable explanation of why navigation failed, if applicable.
 * @property failedTransition A string describing the transition that failed (e.g. "HOME_SCREEN -> CAREER_ENTRY").
 * @property isRecoverable Whether the failure might be resolved by user intervention.
 * @property recommendedAction A suggested manual action the user could take to recover.
 * @property screenshotPath Path to a screenshot saved at the time of failure, if available.
 */
data class NavigationResult(
    val success: Boolean,
    val lastDetectedState: String = "UNKNOWN",
    val failureReason: String = "",
    val failedTransition: String = "",
    val isRecoverable: Boolean = false,
    val recommendedAction: String = "",
    val screenshotPath: String = "",
)

/**
 * Finite-state navigator that handles all outer-menu automation between queued runs.
 *
 * After a career run completes, this class navigates from the post-run results screen
 * back through the game menus to start a new career run. It uses explicit screen state
 * detection via template matching and transitions via known button components.
 *
 * States that cannot be reliably detected or transitioned (because the required template
 * images have not been provided yet) will fail cleanly with structured diagnostics
 * instead of guessing with approximate tap coordinates.
 *
 * @property context The Android application context.
 */
class CareerLaunchNavigator(private val context: Context) {
    companion object {
        private val TAG = "[${MainActivity.loggerTag}]CareerLaunchNavigator"

        /** Maximum number of detection-and-transition iterations before timing out. */
        private const val MAX_DETECTION_ATTEMPTS = 100

        /** Maximum consecutive UNKNOWN detections before failing. */
        private const val MAX_CONSECUTIVE_UNKNOWNS = 5

        /** TP-restore flow: center-to-center tap offsets on 1080-wide captures. Restore sits
         * right of No on the confirm dialog; Use sits right of the Toughness 30 tile in the
         * picker; the plus button sits above OK on the quantity dialog. All anchors are template
         * matches, so the offsets ride dialog position. */
        private const val TP_RESTORE_FROM_NO_DX = 475.0
        private const val TP_USE_FROM_DRINK_DX = 719.0
        private const val TP_USE_FROM_DRINK_DY = -4.0
        private const val TP_PLUS_FROM_OK_DX = 7.0
        private const val TP_PLUS_FROM_OK_DY = -372.0

        /** Hard cap on item-based TP restores per queue session - bounds item spend even if
         * something loops. One restore covers one career, so 10 outruns any queue length the
         * UI offers. */
        private const val MAX_TP_RESTORES_PER_SESSION = 10

        /** Maximum consecutive iterations stuck in the same non-goal state before failing. */
        private const val MAX_STUCK_ITERATIONS = 15

        /** Item-based TP restores performed this bot session. Lives on the companion because
         * each between-run handoff constructs a fresh navigator - an instance field would reset
         * every handoff, making the per-session cap really a per-handoff cap. Reset from
         * StartModule at session start. */
        @Volatile
        private var tpRestoresThisSession = 0

        /** Resets the session-scoped TP restore counter. Called when a new bot session starts. */
        fun resetTpRestoresForSession() {
            tpRestoresThisSession = 0
        }
    }

    /** Screen states in the between-run navigation flow. */
    enum class LaunchScreenState {
        /** Career summary screen showing final stats, with "Complete Career" button. */
        CAREER_SUMMARY,

        /** Career-end "Learn" skill purchase screen - the skill list without the in-career Log button. */
        CAREER_END_SKILL_SCREEN,

        /** "Complete Career" confirmation dialog with "Cancel" and "Finish" buttons. */
        COMPLETE_CAREER_CONFIRMATION,

        /** Post-career result screens with Next/OK/Close/Confirm buttons. */
        POST_RUN_RESULTS,

        /** "Career Complete" dialog with "To Home" / "Edit Team" buttons. */
        CAREER_COMPLETE_DIALOG,

        /** "Continue Career" dialog with "Cancel" and "Resume" buttons. */
        CONTINUE_CAREER_DIALOG,

        /** Game main menu with the bottom menu bar. */
        HOME_SCREEN,

        /** Career entry / mode selection. Requires CAREER button template (not yet provided). */
        CAREER_ENTRY,

        /** Scenario selection screen. Requires template (not yet provided). */
        SCENARIO_SELECT,

        /** Trainee/character selection or reuse. Requires template (not yet provided). */
        TRAINEE_SETUP,

        /** Inheritance selection popup. */
        INHERITANCE_SCREEN,

        /** Legacy / Inheritance selection screen with Auto-Select button (and Carnival event banner during the Racing Carnival event). The Next button is disabled until Auto-Select fills both legacy slots. */
        LEGACY_SELECT_SCREEN,

        /** Support card deck with Auto-Select / Reset buttons. */
        SUPPORT_DECK_SCREEN,

        /** "Start Career!" button visible for final confirmation. */
        PRE_RUN_CONFIRMATION,

        /** Quick-mode / shorten-events prompt. Requires template (not yet provided). */
        QUICK_MODE_PROMPT,

        /** Opening cinematic / intro. Detected via Skip button or requires Pause template. */
        CINEMATIC_INTRO,

        /** Bot's normal start point - the in-career training menu. Navigation is complete. */
        ACTIVE_TRAINING_MENU,

        /** "Restore TP?" confirmation - the account lacks Training Points for another career. */
        TP_RESTORE_DIALOG,

        /** Screen could not be identified by any detector. */
        UNKNOWN,
    }

    // Create a temporary Game instance to access CustomImageUtils for template matching.
    // Lightweight - reads settings from SQLite and instantiates CV utils, but starts no automation.
    // The scenario has already been validated by the first successful run, so the Game
    // constructor should not throw. If it does, navigate() catches it and returns a failure.
    private var tempGame: Game? = null
    private var imageUtils: CustomImageUtils? = null

    // Session-scoped flag: Auto-Fill has already been clicked in this navigation session.
    // Prevents infinite loops when the Auto-Fill button remains visible after clicking it.
    private var autoFillAlreadyDone: Boolean = false

    // Session-scoped flag: Auto-Select on the Legacy Select screen has already been clicked.
    // The Auto-Select button stays visible after a successful run - without this guard the
    // navigator would re-enter Legacy Select handling each iteration instead of clicking Next.
    private var legacyAutoSelectAlreadyDone: Boolean = false

    // Session-scoped flag: Skip toggle has already been maxed (Skip >>) in this session.
    private var skipToggleAlreadyDone: Boolean = false

    // Session-scoped counters for the support deck screen. The borrowed friend card never
    // persists between careers, and the game silently ignores Start Career while that slot
    // is empty, so both paths need bounded retries instead of an open-ended click loop.
    private var friendSlotFillAttempts: Int = 0
    private var startCareerClickAttempts: Int = 0

    // Vertical offset from the Borrow Card list's "Remove" bar to the center of the first
    // card row. Both supported screen configs render game content at 1080px width, so this
    // dialog-internal offset is stable across them.
    private val borrowListFirstRowOffsetPx: Int = 220

    private val gestureUtils: MyAccessibilityService get() = MyAccessibilityService.getInstance()

    /** Non-null accessor for imageUtils. Only call after ensureInitialised() returns true. */
    private val iu: CustomImageUtils get() = imageUtils!!

    /**
     * Initialises the temporary Game instance for image utilities.
     * Returns false if initialisation failed.
     */
    private fun ensureInitialised(): Boolean {
        if (imageUtils != null) return true
        return try {
            val game = Game(context)
            tempGame = game
            imageUtils = game.imageUtils
            true
        } catch (e: InterruptedException) {
            // The queue-side navigation deadline interrupts this thread when a call below us
            // wedges (e.g. Game construction never returning after a mid-navigation
            // accessibility-service kill). Let it unwind instead of reporting a constructor error.
            throw e
        } catch (e: Exception) {
            MessageLog.e(TAG, "[NAV] Failed to create Game instance for image utils: ${e.message}")
            false
        }
    }

    /**
     * One-shot probe: is the game parked on the main home screen?
     *
     * Used by the queue before run 1. The navigator otherwise only runs BETWEEN careers,
     * so a queue started while the game sits at home (e.g. a previous queue failed out
     * between runs) used to burn the first run on failed screen detection inside the
     * career loop. Returns false on any initialisation or detection problem - the queue
     * then behaves exactly as it did before this probe existed.
     */
    fun isOnHomeScreen(): Boolean {
        if (!ensureInitialised()) return false
        return try {
            detectScreenState() == LaunchScreenState.HOME_SCREEN
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Navigates from the post-run state to the active training menu for the next queued run.
     *
     * @param reuseLastLaunchSetup If true, attempt to reuse the previous trainee/deck setup.
     * @return A [NavigationResult] indicating success or failure with diagnostics.
     */
    fun navigate(reuseLastLaunchSetup: Boolean): NavigationResult {
        val autoFillSupports = SettingsHelper.getBooleanSetting("runQueue", "autoFillSupports", false)
        MessageLog.i(TAG, "[NAV] Starting between-run navigation. reuseLastLaunchSetup=$reuseLastLaunchSetup, autoFillSupports=$autoFillSupports")

        // Reset session-scoped flags for this navigation run.
        autoFillAlreadyDone = false
        skipToggleAlreadyDone = false
        legacyAutoSelectAlreadyDone = false

        if (!ensureInitialised()) {
            return NavigationResult(
                success = false,
                lastDetectedState = "INIT",
                failureReason = "Failed to initialise image utilities for navigation. Game constructor may have thrown.",
                failedTransition = "INIT -> POST_RUN_RESULTS",
                isRecoverable = true,
                recommendedAction = "Check that a valid scenario is selected and restart the queue.",
            )
        }

        var currentState = LaunchScreenState.POST_RUN_RESULTS
        var consecutiveUnknowns = 0
        var stuckInStateCount = 0
        // Secondary bail-out: total iterations since the last time we made meaningful progress
        // (i.e. reached a state we hadn't seen yet OR actually advanced to a new state). The
        // per-state `stuckInStateCount` resets whenever the detected state changes, so a screen
        // that oscillates between two misdetected states (e.g. POST_RUN_RESULTS ↔ UNKNOWN, or
        // Confirm dialog flickering on/off) would reset it every cycle and never trigger.
        // This counter only resets when we actually enter a NEW state, so oscillation still
        // fails cleanly instead of wasting MAX_DETECTION_ATTEMPTS iterations.
        var iterationsWithoutProgress = 0
        val seenStates = mutableSetOf<LaunchScreenState>()
        val progressBailThreshold = MAX_STUCK_ITERATIONS * 2

        for (attempt in 0 until MAX_DETECTION_ATTEMPTS) {
            if (!BotService.isRunning || StartModule.queueStopRequested) {
                return NavigationResult(
                    success = false,
                    lastDetectedState = currentState.name,
                    failureReason = "Queue was stopped during navigation.",
                    isRecoverable = false,
                    recommendedAction = "Restart the queue when ready.",
                )
            }

            // Detect current screen state.
            val detectedState = detectScreenState()
            MessageLog.i(TAG, "[NAV] Attempt $attempt: Detected state = $detectedState (previous = $currentState)")

            if (detectedState != LaunchScreenState.UNKNOWN) {
                // Track if we're stuck in the same state (e.g. POST_RUN_RESULTS clicking but not advancing).
                // This is different from normal progress where POST_RUN_RESULTS may repeat across many screens.
                if (detectedState == currentState && detectedState != LaunchScreenState.ACTIVE_TRAINING_MENU) {
                    stuckInStateCount++
                    if (stuckInStateCount >= MAX_STUCK_ITERATIONS) {
                        val screenshotPath = captureFailureScreenshot("stuck_in_${detectedState.name}")
                        return NavigationResult(
                            success = false,
                            lastDetectedState = detectedState.name,
                            failureReason = "Stuck in $detectedState for $MAX_STUCK_ITERATIONS iterations without advancing. The click is not producing a screen transition.",
                            failedTransition = "${detectedState.name} -> next screen",
                            isRecoverable = true,
                            recommendedAction = "Manually advance past the current screen and restart the queue.",
                            screenshotPath = screenshotPath,
                        )
                    }
                } else {
                    stuckInStateCount = 0
                }
                // Progress counter: resets only when we enter a state we haven't seen this
                // navigation session yet. Just oscillating between known states does NOT reset.
                if (seenStates.add(detectedState)) {
                    iterationsWithoutProgress = 0
                } else if (detectedState != LaunchScreenState.ACTIVE_TRAINING_MENU) {
                    iterationsWithoutProgress++
                    if (iterationsWithoutProgress >= progressBailThreshold) {
                        val screenshotPath = captureFailureScreenshot("no_progress_${detectedState.name}")
                        return NavigationResult(
                            success = false,
                            lastDetectedState = detectedState.name,
                            failureReason = "Navigation made no forward progress for $progressBailThreshold iterations (oscillating between ${seenStates.joinToString()}). Bailing before MAX_DETECTION_ATTEMPTS timeout.",
                            failedTransition = "${currentState.name} -> ${detectedState.name}",
                            isRecoverable = true,
                            recommendedAction = "Manually navigate past the current screen loop and restart the queue.",
                            screenshotPath = screenshotPath,
                        )
                    }
                }
                currentState = detectedState
                consecutiveUnknowns = 0
            } else {
                consecutiveUnknowns++
                if (consecutiveUnknowns >= MAX_CONSECUTIVE_UNKNOWNS) {
                    val screenshotPath = captureFailureScreenshot("unknown_state")
                    return NavigationResult(
                        success = false,
                        lastDetectedState = currentState.name,
                        failureReason = "Could not identify the current screen after $MAX_CONSECUTIVE_UNKNOWNS consecutive attempts. No known buttons or UI elements matched.",
                        failedTransition = "${currentState.name} -> UNKNOWN",
                        isRecoverable = true,
                        recommendedAction = "Manually navigate to the in-career training screen and restart the queue.",
                        screenshotPath = screenshotPath,
                    )
                }
                // Wait and retry detection - do NOT tap blindly.
                MessageLog.w(TAG, "[NAV] Unknown screen state ($consecutiveUnknowns/$MAX_CONSECUTIVE_UNKNOWNS). Waiting before retry...")
                waitSafe(2.0)
                continue
            }

            // Handle the detected state.
            val transitionResult = handleState(currentState, reuseLastLaunchSetup, autoFillSupports)

            when (transitionResult) {
                is TransitionResult.Success -> {
                    return NavigationResult(success = true, lastDetectedState = LaunchScreenState.ACTIVE_TRAINING_MENU.name)
                }
                is TransitionResult.Continue -> {
                    waitSafe(1.5)
                }
                is TransitionResult.Failed -> {
                    val screenshotPath = captureFailureScreenshot("failed_${currentState.name}")
                    return NavigationResult(
                        success = false,
                        lastDetectedState = currentState.name,
                        failureReason = transitionResult.reason,
                        failedTransition = transitionResult.transition,
                        isRecoverable = transitionResult.isRecoverable,
                        recommendedAction = transitionResult.recommendedAction,
                        screenshotPath = screenshotPath,
                    )
                }
            }
        }

        val screenshotPath = captureFailureScreenshot("timeout")
        return NavigationResult(
            success = false,
            lastDetectedState = currentState.name,
            failureReason = "Navigation timed out after $MAX_DETECTION_ATTEMPTS attempts without reaching the training menu.",
            isRecoverable = true,
            recommendedAction = "Manually navigate to the in-career training screen and restart the queue.",
            screenshotPath = screenshotPath,
        )
    }

    // ////////////////////////////////////////////////////////////////////////////
    // Screen Detection
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * Detects the current screen state by checking for known UI elements via template matching.
     *
     * States are checked in priority order - the first match wins. Every detection here
     * uses real template matching against existing button/component assets.
     */
    private fun detectScreenState(): LaunchScreenState {
        val bitmap = iu.getSourceBitmap()

        // Detection order is performance-sensitive: each check on a non-matching screen still
        // costs one OpenCV TM_CCOEFF_NORMED scan. We frontload the goal state, then the most
        // common transition states (which dominate iteration count during between-run nav),
        // then the discriminating-but-unique buttons that could otherwise hide a generic
        // POST_RUN_RESULTS Next/Ok/Confirm/Close match underneath them.
        //
        // Hard ordering constraints:
        //   - ACTIVE_TRAINING_MENU first: it's the success/exit state.
        //   - LEGACY_SELECT_SCREEN must precede POST_RUN_RESULTS Next: Legacy Select shows a
        //     greyed Next that template-matches but is non-clickable; misclassifying as
        //     POST_RUN_RESULTS would trap the navigator in a stuck-iteration loop.
        //   - HOME_SCREEN (CareerHome) must precede POST_RUN_RESULTS: the home screen has no
        //     Next/Ok/Confirm/Close but other screens with those buttons should not be
        //     misclassified as HOME by the weaker MenuBarHomeSelected fallback at the bottom.

        // GOAL STATE: Active training menu - Training or Rest button visible.
        if (ButtonTraining.check(iu, sourceBitmap = bitmap) || ButtonRest.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.ACTIVE_TRAINING_MENU
        }

        // Career Complete dialog - "To Home" button is uniquely present on this screen.
        // Common immediately after a career run completes.
        if (ButtonToHome.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.CAREER_COMPLETE_DIALOG
        }

        // Home screen - CAREER button visible (the strongest home-screen signal).
        // We do NOT use ButtonMenuBarHomeSelected alone because that menu bar appears on
        // many intermediate screens (Scenario Select, Trainee Select, etc.) that are reached
        // from the home screen but still have the bottom nav visible.
        if (ButtonCareerHome.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.HOME_SCREEN
        }

        // Legacy Select screen - Auto-Select button (green pill, optionally under a pink
        // Racing Carnival Underway banner). Checked BEFORE the generic POST_RUN_RESULTS Next
        // check because Next on Legacy Select is greyed out / non-clickable until Auto-Select
        // populates both legacy slots; clicking Next there is a no-op and would trap the
        // navigator in a 15-iteration stuck loop.
        if (ButtonAutoSelect.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.LEGACY_SELECT_SCREEN
        }

        // Career-end "Learn" (skill purchase) screen. MUST be checked before the generic
        // POST_RUN_RESULTS chain and before CAREER_SUMMARY: this screen carries a Confirm
        // button (matching the POST_RUN_RESULTS chain, whose handler closes the skill list)
        // and the Complete Career button can also match here (CAREER_SUMMARY, whose handler
        // would end the career with the skill points unspent). Misordering it once completed a
        // career with ~1150 SP unspent.
        val skillLabelConfidence = 0.60
        if (ButtonSkillListFullStats.check(iu, sourceBitmap = bitmap) &&
            !ButtonLog.check(iu, sourceBitmap = bitmap) &&
            (
                LabelSkillListScreenSkillPoints.check(iu, sourceBitmap = bitmap, confidence = skillLabelConfidence) ||
                    LabelSkillListScreenSkillPointsV2.check(iu, sourceBitmap = bitmap, confidence = skillLabelConfidence)
            )
        ) {
            return LaunchScreenState.CAREER_END_SKILL_SCREEN
        }

        // POST_RUN_RESULTS - generic post-run / between-screens dialog with Next, OK, Confirm,
        // or Close (wide or compact-pill style) as the primary advance button. This is the most
        // common state during between-run navigation (10-20 iterations per career), so we check it early.
        // Consolidated into a single short-circuit `||` chain so a Next match avoids running
        // the other three template scans. Order within the chain is most-common-first.
        if (ButtonNext.check(iu, sourceBitmap = bitmap) ||
            ButtonOk.check(iu, sourceBitmap = bitmap) ||
            ButtonConfirm.check(iu, sourceBitmap = bitmap) ||
            ButtonClose.check(iu, sourceBitmap = bitmap) ||
            ButtonCloseDialog.check(iu, sourceBitmap = bitmap)
        ) {
            return LaunchScreenState.POST_RUN_RESULTS
        }

        // "Restore TP?" confirmation - the account lacks Training Points for another career
        // playthrough (back-to-back completed runs can drain TP). The dialog offers No / Restore
        // and matches none of the generic advance buttons. ButtonNo alone is ambiguous across
        // dialogs, so confirm via body OCR.
        if (ButtonNo.check(iu, sourceBitmap = bitmap)) {
            try {
                val body =
                    iu.performOCROnRegion(
                        bitmap,
                        (bitmap.width * 0.10).toInt(),
                        (bitmap.height * 0.35).toInt(),
                        (bitmap.width * 0.80).toInt(),
                        (bitmap.height * 0.25).toInt(),
                        useThreshold = false,
                        useGrayscale = true,
                        scale = 2.0,
                        debugName = "nav_tp_dialog_ocr",
                    )
                if (Regex("\\bTP\\b").containsMatchIn(body.uppercase())) {
                    MessageLog.i(TAG, "[NAV] Restore-TP confirmation detected: \"${body.replace("\n", " ").take(70)}\"")
                    return LaunchScreenState.TP_RESTORE_DIALOG
                }
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // "Continue Career" dialog - Resume button takes us straight back into an active career.
        // Rare but discriminating.
        if (ButtonResume.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.CONTINUE_CAREER_DIALOG
        }

        // Deck screen has the unique "Support Formation" purple banner at the top.
        // This is the most reliable deck-screen discriminator.
        if (LabelSupportFormation.check(iu, sourceBitmap = bitmap)) {
            MessageLog.i(TAG, "[NAV] LabelSupportFormation matched → SUPPORT_DECK_SCREEN")
            return LaunchScreenState.SUPPORT_DECK_SCREEN
        }

        // Pre-run confirmation - "Start Career!" button visible without the Support
        // Formation banner. This is the Final Confirmation popup.
        if (ButtonStartCareer.check(iu, sourceBitmap = bitmap) || ButtonStartCareerOffset.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.PRE_RUN_CONFIRMATION
        }

        // Quick Mode / Skip toggle - Skip Off, Skip >, or Skip >> button visible at the
        // bottom-left. Detection uses template match first, then falls back to OCR for
        // "Skip" text in the bottom-left area (handles template variations across game
        // versions and event cinematics).
        if (ButtonSkipOff.check(iu, sourceBitmap = bitmap) || ButtonSkipOn.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.QUICK_MODE_PROMPT
        }
        try {
            // Scan 22%-53% width, 94%-98% height - centered on the Skip Off pill button.
            val skipOcr =
                iu.performOCROnRegion(
                    bitmap,
                    (bitmap.width * 0.22).toInt(),
                    (bitmap.height * 0.94).toInt(),
                    (bitmap.width * 0.31).toInt(),
                    (bitmap.height * 0.04).toInt(),
                    useThreshold = false,
                    useGrayscale = false,
                    scale = 2.0,
                    debugName = "nav_skip_button_ocr",
                )
            val skipUpper = skipOcr.uppercase()
            if (skipUpper.contains("SKIP")) {
                MessageLog.i(TAG, "[NAV] Skip button OCR: '$skipOcr' → QUICK_MODE_PROMPT")
                return LaunchScreenState.QUICK_MODE_PROMPT
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
        }

        // Cinematic - Skip or fast-forward button visible.
        if (ButtonSkipCinematic.check(iu, sourceBitmap = bitmap) || ButtonSkip.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.CINEMATIC_INTRO
        }

        // Career summary screen - "Complete Career" button visible (the button to initiate completion).
        if (ButtonCompleteCareer.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.CAREER_SUMMARY
        }

        // "Complete Career" confirmation dialog - Finish button is unique to this screen.
        if (ButtonFinish.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.COMPLETE_CAREER_CONFIRMATION
        }

        // Last-resort home screen detection - menu bar Home tab is selected but no other
        // specific buttons matched. This is a weaker signal because the menu bar appears
        // on many screens, but if nothing else matched we're likely on the home screen
        // with the CAREER button obscured by decorative banners.
        if (ButtonMenuBarHomeSelected.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.HOME_SCREEN
        }

        return LaunchScreenState.UNKNOWN
    }

    // ////////////////////////////////////////////////////////////////////////////
    // State Transitions
    // ////////////////////////////////////////////////////////////////////////////

    private sealed class TransitionResult {
        /** Navigation is complete - we reached the training menu. */
        object Success : TransitionResult()

        /** Transition was performed. Re-detect to find the next state. */
        object Continue : TransitionResult()

        /** Navigation failed and cannot continue. */
        data class Failed(
            val reason: String,
            val transition: String,
            val isRecoverable: Boolean = true,
            val recommendedAction: String = "Manually navigate to the in-career training screen and restart the queue.",
        ) : TransitionResult()
    }

    /**
     * Dispatches to the appropriate handler for the detected screen state.
     */
    private fun handleState(state: LaunchScreenState, reuseLastLaunchSetup: Boolean, autoFillSupports: Boolean = false): TransitionResult {
        return when (state) {
            LaunchScreenState.ACTIVE_TRAINING_MENU -> {
                MessageLog.i(TAG, "[NAV] Reached active training menu. Navigation complete!")
                TransitionResult.Success
            }

            LaunchScreenState.CONTINUE_CAREER_DIALOG -> handleContinueCareerDialog()
            LaunchScreenState.CAREER_SUMMARY -> handleCareerSummary()
            LaunchScreenState.CAREER_END_SKILL_SCREEN ->
                TransitionResult.Failed(
                    reason = "Career-end skill purchase (Learn) screen detected - the careerComplete skill plan has not run for this career.",
                    transition = "CAREER_END_SKILL_SCREEN -> (refusing to navigate)",
                    isRecoverable = true,
                    recommendedAction = "Start the bot while on this screen - the startup career-end guard hands it to the campaign, which buys skills and then completes the career.",
                )
            LaunchScreenState.COMPLETE_CAREER_CONFIRMATION -> handleCompleteCareerConfirmation()
            LaunchScreenState.POST_RUN_RESULTS -> handlePostRunResults()
            LaunchScreenState.CAREER_COMPLETE_DIALOG -> handleCareerCompleteDialog()
            LaunchScreenState.LEGACY_SELECT_SCREEN -> handleLegacySelectScreen()
            LaunchScreenState.PRE_RUN_CONFIRMATION -> handlePreRunConfirmation()
            LaunchScreenState.TP_RESTORE_DIALOG -> handleTpRestoreDialog()
            LaunchScreenState.SUPPORT_DECK_SCREEN -> handleSupportDeckScreen(reuseLastLaunchSetup, autoFillSupports)
            LaunchScreenState.CINEMATIC_INTRO -> handleCinematicIntro()
            LaunchScreenState.HOME_SCREEN -> handleHomeScreen()
            LaunchScreenState.QUICK_MODE_PROMPT -> handleQuickModePrompt()

            // --- States that require templates not yet provided ---

            LaunchScreenState.CAREER_ENTRY ->
                TransitionResult.Failed(
                    reason = "Reached CAREER_ENTRY state but no template exists for the Career mode button. Cannot navigate further.",
                    transition = "CAREER_ENTRY -> SCENARIO_SELECT",
                    isRecoverable = true,
                    recommendedAction = "Provide the 'career_home' template image, or manually enter Career mode and restart the queue.",
                )

            LaunchScreenState.SCENARIO_SELECT ->
                TransitionResult.Failed(
                    reason = "Reached SCENARIO_SELECT state but no template exists for scenario selection UI. Cannot navigate further.",
                    transition = "SCENARIO_SELECT -> TRAINEE_SETUP",
                    isRecoverable = true,
                    recommendedAction = "Provide scenario select templates, or manually select the scenario and restart the queue.",
                )

            LaunchScreenState.TRAINEE_SETUP ->
                TransitionResult.Failed(
                    reason = "Reached TRAINEE_SETUP state. Trainee selection requires manual input or a reuse-setup template.",
                    transition = "TRAINEE_SETUP -> SUPPORT_DECK_SCREEN",
                    isRecoverable = true,
                    recommendedAction = "Manually select the trainee and restart the queue.",
                )

            LaunchScreenState.INHERITANCE_SCREEN ->
                TransitionResult.Failed(
                    reason = "Reached INHERITANCE_SCREEN state. Inheritance selection requires manual input.",
                    transition = "INHERITANCE_SCREEN -> next screen",
                    isRecoverable = true,
                    recommendedAction = "Manually handle the inheritance selection and restart the queue.",
                )

            LaunchScreenState.UNKNOWN -> {
                // Handled in the main loop - should not be dispatched here.
                TransitionResult.Continue
            }
        }
    }

    // ////////////////////////////////////////////////////////////////////////////
    // State Handlers - implemented with real template matching
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * CONTINUE_CAREER_DIALOG: The "Continue Career" dialog that appears when entering Career
     * mode while a previous career is still in progress. Clicking "Resume" goes straight
     * back into the active career's training menu.
     *
     * Detection: ButtonResume template match (unique to this dialog).
     * Transition: ButtonResume.click() → ACTIVE_TRAINING_MENU.
     */
    private fun handleContinueCareerDialog(): TransitionResult {
        MessageLog.i(TAG, "[NAV] Continue Career dialog detected. Clicking 'Resume'...")

        if (ButtonResume.click(iu)) {
            waitSafe(3.0)
            return TransitionResult.Continue
        }

        return TransitionResult.Failed(
            reason = "CONTINUE_CAREER_DIALOG detected (ButtonResume matched) but click failed.",
            transition = "CONTINUE_CAREER_DIALOG -> ACTIVE_TRAINING_MENU",
            recommendedAction = "Manually click 'Resume' to continue your career.",
        )
    }

    /**
     * CAREER_SUMMARY: The career end summary screen showing final stats.
     * Has a "Complete Career" button that initiates the completion flow.
     *
     * Detection: ButtonCompleteCareer template match.
     * Transition: ButtonCompleteCareer.click() → leads to COMPLETE_CAREER_CONFIRMATION dialog.
     */
    private fun handleCareerSummary(): TransitionResult {
        MessageLog.i(TAG, "[NAV] Career summary screen detected. Clicking 'Complete Career'...")

        if (ButtonCompleteCareer.click(iu)) {
            waitSafe(2.0)
            return TransitionResult.Continue
        }

        return TransitionResult.Failed(
            reason = "CAREER_SUMMARY detected (ButtonCompleteCareer matched) but click failed.",
            transition = "CAREER_SUMMARY -> COMPLETE_CAREER_CONFIRMATION",
            recommendedAction = "Manually click 'Complete Career' and restart the queue.",
        )
    }

    /**
     * COMPLETE_CAREER_CONFIRMATION: The "Complete Career" dialog with Cancel and Finish buttons.
     * This appears at the very end of a run before the post-run result screens.
     *
     * Detection: ButtonFinish template match (unique to this dialog).
     * Transition: ButtonFinish.click() (template-matched).
     */
    private fun handleCompleteCareerConfirmation(): TransitionResult {
        MessageLog.i(TAG, "[NAV] Complete Career confirmation dialog detected. Clicking 'Finish'...")

        if (ButtonFinish.click(iu)) {
            waitSafe(3.0)
            return TransitionResult.Continue
        }

        return TransitionResult.Failed(
            reason = "COMPLETE_CAREER_CONFIRMATION detected (ButtonFinish matched) but click failed.",
            transition = "COMPLETE_CAREER_CONFIRMATION -> POST_RUN_RESULTS",
            recommendedAction = "Manually click 'Finish' and restart the queue.",
        )
    }

    /**
     * POST_RUN_RESULTS: Clicks the first matching advancement button.
     *
     * Detection: template-matched (Next, OK, Confirm, Close, CompleteCareer).
     * Transition: template-matched button click.
     */

    /**
     * Handles the "Restore TP?" confirmation.
     *
     * Default: decline and end the queue gracefully - restoring spends resources, and that
     * decision belongs to the user. With the opt-in `runQueue.enableTpRestoreWithItems`
     * setting, the flow the user specified runs instead: Restore -> pick the Toughness 30 row
     * (never Carats - if the drink template is absent the bot declines) -> plus once (exactly
     * one drink = 30 TP = one career) -> OK -> Close -> resume the career start.
     */
    private fun handleTpRestoreDialog(): TransitionResult {
        val restoreWithItems = SettingsHelper.getBooleanSetting("runQueue", "enableTpRestoreWithItems", false)
        val declineResult = {
            ButtonNo.click(iu)
            waitSafe(1.0)
            TransitionResult.Failed(
                reason = "Out of TP: the game needs more Training Points to start another career playthrough. The restore prompt was declined.",
                transition = "PRE_RUN_CONFIRMATION -> TP_RESTORE_DIALOG",
                isRecoverable = false,
                recommendedAction = "TP regenerates over time - restart the queue later, restore TP manually, or enable \"Restore TP with items\" in the Run Queue settings. All completed runs are saved.",
            )
        }

        if (!restoreWithItems) {
            MessageLog.w(TAG, "[NAV] Out of TP for another career playthrough. Declining the restore prompt and ending the queue (item restore is disabled).")
            return declineResult()
        }
        if (tpRestoresThisSession >= MAX_TP_RESTORES_PER_SESSION) {
            MessageLog.w(TAG, "[NAV] TP restore cap reached ($MAX_TP_RESTORES_PER_SESSION this session). Declining and ending the queue.")
            return declineResult()
        }

        val noLocation = ButtonNo.find(iu).first
        if (noLocation == null) {
            // Dialog state shifted between detection and handling - re-detect next tick.
            return TransitionResult.Continue
        }

        MessageLog.i(TAG, "[NAV] Out of TP. Restoring with one Toughness 30 per the enabled setting...")
        gestureUtils.tap(noLocation.x + TP_RESTORE_FROM_NO_DX, noLocation.y, "tp_restore_button")
        waitSafe(1.5)

        val drinkLocation = IconTpDrink.find(iu).first
        if (drinkLocation == null) {
            MessageLog.w(TAG, "[NAV] Toughness 30 row not found in the Recover TP picker - likely out of drinks. Carats are never spent; closing and ending the queue.")
            // The picker's Close style is unverified - try both close variants.
            if (!ButtonClose.click(iu)) ButtonCloseDialog.click(iu)
            waitSafe(1.0)
            return TransitionResult.Failed(
                reason = "TP restore was enabled but no Toughness 30 drinks were found in the Recover TP picker. Carats are never spent automatically.",
                transition = "TP_RESTORE_DIALOG -> RECOVER_TP_PICKER",
                isRecoverable = false,
                recommendedAction = "Stock Toughness 30 drinks or restore TP manually, then restart the queue. All completed runs are saved.",
            )
        }

        gestureUtils.tap(drinkLocation.x + TP_USE_FROM_DRINK_DX, drinkLocation.y + TP_USE_FROM_DRINK_DY, "tp_use_button")
        waitSafe(1.2)

        val okLocation = ButtonOk.find(iu).first
        if (okLocation == null) {
            MessageLog.w(TAG, "[NAV] Quantity dialog OK button not found after Use. Re-detecting...")
            return TransitionResult.Continue
        }
        // The quantity dialog opens with zero drinks selected; one plus press selects exactly
        // one drink (30 TP), which always covers the 30 TP career cost.
        gestureUtils.tap(okLocation.x + TP_PLUS_FROM_OK_DX, okLocation.y + TP_PLUS_FROM_OK_DY, "tp_plus_one")
        waitSafe(0.6)
        ButtonOk.click(iu)
        waitSafe(1.2)
        if (!ButtonClose.click(iu)) ButtonCloseDialog.click(iu)
        waitSafe(1.0)

        tpRestoresThisSession++
        MessageLog.i(
            TAG,
            "[NAV] Restored 30 TP with one Toughness 30 (restore $tpRestoresThisSession/$MAX_TP_RESTORES_PER_SESSION this session). Resuming the career start.",
        )
        return TransitionResult.Continue
    }

    private fun handlePostRunResults(): TransitionResult {
        val bitmap = iu.getSourceBitmap()

        // Defense: if the post-career SkillList screen lingered (e.g. SkillPlan failed to detect
        // the screen and bailed without exiting), the navigator would loop forever clicking the
        // green Confirm button (which does nothing when no skills are selected). Detect the
        // SkillList screen via its unique Full Stats button and back out instead.
        if (ButtonSkillListFullStats.check(iu, sourceBitmap = bitmap)) {
            MessageLog.w(TAG, "[NAV] SkillList screen detected during POST_RUN_RESULTS. Backing out via ButtonBack to escape Confirm-loop.")
            if (ButtonBack.click(iu, sourceBitmap = bitmap)) {
                waitSafe(1.5)
                return TransitionResult.Continue
            }
            MessageLog.w(TAG, "[NAV] ButtonBack click failed on SkillList screen. Falling through to standard post-run handling.")
        }

        var clickedButton = ""
        val clicked =
            when {
                ButtonNext.check(iu, sourceBitmap = bitmap) -> {
                    clickedButton = "Next"
                    ButtonNext.click(iu, sourceBitmap = bitmap)
                }
                ButtonOk.check(iu, sourceBitmap = bitmap) -> {
                    clickedButton = "OK"
                    ButtonOk.click(iu, sourceBitmap = bitmap)
                }
                ButtonConfirm.check(iu, sourceBitmap = bitmap) -> {
                    clickedButton = "Confirm"
                    ButtonConfirm.click(iu, sourceBitmap = bitmap)
                }
                ButtonClose.check(iu, sourceBitmap = bitmap) -> {
                    clickedButton = "Close"
                    ButtonClose.click(iu, sourceBitmap = bitmap)
                }
                ButtonCloseDialog.check(iu, sourceBitmap = bitmap) -> {
                    clickedButton = "Close (dialog pill)"
                    ButtonCloseDialog.click(iu, sourceBitmap = bitmap)
                }
                ButtonCompleteCareer.check(iu, sourceBitmap = bitmap) -> {
                    clickedButton = "Complete Career"
                    ButtonCompleteCareer.click(iu, sourceBitmap = bitmap)
                }
                else -> false
            }
        if (!clicked) {
            return TransitionResult.Failed(
                reason = "POST_RUN_RESULTS state detected but could not click any advancement button.",
                transition = "POST_RUN_RESULTS -> next screen",
                recommendedAction = "Check if the post-run screen has an unexpected button layout.",
            )
        }
        MessageLog.i(TAG, "[NAV] Post-run results screen: clicked '$clickedButton' to advance.")
        return TransitionResult.Continue
    }

    /**
     * CAREER_COMPLETE_DIALOG: Clicks "To Home" button.
     *
     * Detection: ButtonToHome template match.
     * Transition: ButtonToHome.click() or ButtonClose.click() (both template-matched).
     */
    private fun handleCareerCompleteDialog(): TransitionResult {
        MessageLog.i(TAG, "[NAV] Career Complete dialog detected. Clicking 'To Home'...")
        if (ButtonToHome.click(iu)) {
            waitSafe(3.0)
            return TransitionResult.Continue
        }

        // ButtonClose is also a known template on this dialog.
        if (ButtonClose.click(iu)) {
            waitSafe(2.0)
            return TransitionResult.Continue
        }

        return TransitionResult.Failed(
            reason = "CAREER_COMPLETE_DIALOG detected (ButtonToHome matched) but click failed.",
            transition = "CAREER_COMPLETE_DIALOG -> HOME_SCREEN",
            recommendedAction = "Manually click 'To Home' and restart the queue.",
        )
    }

    /**
     * HOME_SCREEN: Detected via ButtonMenuBarHomeSelected or ButtonCareerHome.
     * Uses a multi-detector strategy to click the CAREER button:
     * 1. Primary: ButtonCareerHome (full button, lowered confidence 0.6)
     * 2. Secondary: ButtonCareerHomeText (text-only crop, confidence 0.55)
     * 3. Tertiary: OCR scan for "CAREER" in the bottom-half of the screen
     *
     * Detection: ButtonMenuBarHomeSelected or ButtonCareerHome template match.
     * Transition: Multi-detector click.
     */
    private fun handleHomeScreen(): TransitionResult {
        MessageLog.i(TAG, "[NAV] On home screen. Attempting to click CAREER button (multi-detector)...")
        val bitmap = iu.getSourceBitmap()

        // Detector 1: Full career_home template (lowered confidence 0.6)
        MessageLog.i(TAG, "[NAV] [HOME] Trying detector 1: ButtonCareerHome (full template, confidence 0.6)...")
        if (ButtonCareerHome.check(iu, sourceBitmap = bitmap)) {
            MessageLog.i(TAG, "[NAV] [HOME] Detector 1 matched. Clicking...")
            if (ButtonCareerHome.click(iu, sourceBitmap = bitmap)) {
                MessageLog.i(TAG, "[NAV] [HOME] Detector 1 click succeeded.")
                waitSafe(3.0)
                return TransitionResult.Continue
            }
            MessageLog.w(TAG, "[NAV] [HOME] Detector 1 matched but click failed.")
        } else {
            MessageLog.i(TAG, "[NAV] [HOME] Detector 1 did not match.")
        }

        // Detector 2: Text-only crop template (confidence 0.55)
        MessageLog.i(TAG, "[NAV] [HOME] Trying detector 2: ButtonCareerHomeText (text crop, confidence 0.55)...")
        if (ButtonCareerHomeText.check(iu, sourceBitmap = bitmap)) {
            MessageLog.i(TAG, "[NAV] [HOME] Detector 2 matched. Clicking...")
            if (ButtonCareerHomeText.click(iu, sourceBitmap = bitmap)) {
                MessageLog.i(TAG, "[NAV] [HOME] Detector 2 click succeeded.")
                waitSafe(3.0)
                return TransitionResult.Continue
            }
            MessageLog.w(TAG, "[NAV] [HOME] Detector 2 matched but click failed.")
        } else {
            MessageLog.i(TAG, "[NAV] [HOME] Detector 2 did not match.")
        }

        // Detector 3: OCR scan for "CAREER" text near the CAREER button's known position.
        // The CAREER button area changes decorations (event banners, character art) but
        // the "CAREER" text itself is always present.
        MessageLog.i(TAG, "[NAV] [HOME] Trying detector 3: OCR scan for 'CAREER' text...")
        try {
            val screenWidth = bitmap.width
            val screenHeight = bitmap.height
            // The CAREER button sits at ~75% width. Limit the OCR region to 55%-95% width so
            // decorative "Event" banners on the left side of the screen (common during
            // seasonal events / campaign promos) cannot false-trigger the Event fallback
            // below - which would otherwise fire a blind tap at the fixed CAREER button
            // coordinates and navigate the bot to the wrong screen.
            // Vertically, CAREER text is at 85-88% height. Scan 80-92% to give OCR room.
            val ocrX = (screenWidth * 0.55).toInt()
            val ocrY = (screenHeight * 0.80).toInt()
            val ocrW = (screenWidth * 0.40).toInt()
            val ocrH = (screenHeight * 0.12).toInt()

            val ocrText =
                iu.performOCROnRegion(
                    bitmap,
                    ocrX,
                    ocrY,
                    ocrW,
                    ocrH,
                    useThreshold = false,
                    useGrayscale = false,
                    scale = 2.0,
                    debugName = "nav_career_ocr",
                )
            MessageLog.i(TAG, "[NAV] [HOME] OCR result: '$ocrText'")

            // The "CAREER" text uses a decorative gold font that Tesseract often can't read.
            // However, the "Event" label directly below it IS readable. If OCR finds either
            // "CAREER" or "Event" in the bottom region, we know the CAREER button is present.
            val ocrUpper = ocrText.uppercase()
            if (ocrUpper.contains("CAREER") || ocrUpper.contains("EVENT")) {
                val matchedWord = if (ocrUpper.contains("CAREER")) "CAREER" else "Event"
                MessageLog.i(TAG, "[NAV] [HOME] OCR found '$matchedWord'. Tapping CAREER button area...")
                // Tap at the center of the CAREER button. This is OCR-guided: we only
                // reach here after confirming the CAREER/Event text is on screen AND
                // we already verified HOME_SCREEN via ButtonMenuBarHomeSelected.
                val tapX = (screenWidth * 0.75).toDouble()
                val tapY = (screenHeight * 0.86).toDouble()
                gestureUtils.tap(tapX, tapY, "career_ocr_tap")
                waitSafe(3.0)
                return TransitionResult.Continue
            } else {
                MessageLog.i(TAG, "[NAV] [HOME] OCR did not find 'CAREER' or 'Event'.")
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[NAV] [HOME] OCR detector failed: ${e.message}")
        }

        // All detectors failed
        val screenshotPath = captureFailureScreenshot("HOME_SCREEN_career_click")
        return TransitionResult.Failed(
            reason = "HOME_SCREEN detected but all CAREER button detectors failed (template x2, OCR x1). Screenshot: $screenshotPath",
            transition = "HOME_SCREEN -> CAREER_ENTRY",
            isRecoverable = true,
            recommendedAction = "The CAREER button template may not match your game version. Provide a fresh screenshot of the home screen CAREER button area.",
        )
    }

    /**
     * SUPPORT_DECK_SCREEN: Detected via ButtonAutoSelect/ButtonReset.
     * With reuseLastLaunchSetup, the previous deck should be pre-populated and
     * StartCareer should be visible somewhere on the screen.
     *
     * Detection: ButtonAutoSelect or ButtonReset template match.
     * Transition: ButtonStartCareer template match.
     */
    private fun handleSupportDeckScreen(reuseLastLaunchSetup: Boolean, autoFillSupports: Boolean = false): TransitionResult {
        MessageLog.i(TAG, "[NAV] On support deck screen. autoFillSupports=$autoFillSupports")

        if (!reuseLastLaunchSetup) {
            return TransitionResult.Failed(
                reason = "SUPPORT_DECK_SCREEN reached but reuseLastLaunchSetup is disabled. Deck configuration requires manual input.",
                transition = "SUPPORT_DECK_SCREEN -> PRE_RUN_CONFIRMATION",
                recommendedAction = "Enable 'Reuse Last Launch Setup' or manually configure the deck.",
            )
        }

        // If autoFillSupports is enabled AND we haven't already clicked Auto-Fill in this
        // navigation session, click the Auto-Fill button to fill empty slots.
        // The flag prevents infinite loops since Auto-Fill stays visible after clicking.
        if (autoFillSupports && !autoFillAlreadyDone) {
            MessageLog.i(TAG, "[NAV] Auto-Fill enabled. Looking for Auto-Fill button...")
            val clicked =
                when {
                    ButtonAutoFill.check(iu) -> {
                        MessageLog.i(TAG, "[NAV] ButtonAutoFill matched. Clicking...")
                        ButtonAutoFill.click(iu)
                    }
                    ButtonAutoSelect.check(iu) -> {
                        MessageLog.i(TAG, "[NAV] ButtonAutoSelect matched (fallback). Clicking...")
                        ButtonAutoSelect.click(iu)
                    }
                    else -> {
                        MessageLog.w(TAG, "[NAV] Auto-Fill enabled but neither ButtonAutoFill nor ButtonAutoSelect matched.")
                        false
                    }
                }
            if (clicked) {
                autoFillAlreadyDone = true // Mark as done even if the subsequent flow fails.
                waitSafe(2.0)
                if (ButtonOk.check(iu)) {
                    MessageLog.i(TAG, "[NAV] Clicking OK on Auto-Fill confirmation dialog...")
                    ButtonOk.click(iu)
                    waitSafe(3.0)
                }
                MessageLog.i(TAG, "[NAV] Auto-Fill sequence complete. Letting loop re-detect next screen.")
                return TransitionResult.Continue
            }
        } else if (autoFillAlreadyDone) {
            MessageLog.i(TAG, "[NAV] Auto-Fill already done this session, skipping to Start Career.")
        }

        val bitmap = iu.getSourceBitmap()

        // The game renders Start Career as enabled but silently ignores it while the friend
        // slot is empty, and the borrowed card never carries over between careers - so with
        // reuseLastLaunchSetup the deck always arrives here one card short. Borrow the first card
        // in the Borrow Card list to complete the deck. A single row tap selects the card and
        // closes the picker.
        if (IconFriendSlotEmpty.check(iu, sourceBitmap = bitmap)) {
            if (friendSlotFillAttempts >= 2) {
                return TransitionResult.Failed(
                    reason = "Friend slot is empty and the Borrow Card flow failed to fill it after $friendSlotFillAttempts attempts.",
                    transition = "SUPPORT_DECK_SCREEN -> PRE_RUN_CONFIRMATION",
                    recommendedAction = "Select a friend support card manually, then restart the queue.",
                )
            }
            friendSlotFillAttempts++
            MessageLog.i(TAG, "[NAV] Friend slot is empty. Opening the Borrow Card picker (attempt $friendSlotFillAttempts)...")
            IconFriendSlotEmpty.click(iu, sourceBitmap = bitmap)
            waitSafe(2.0)
            val (removeLocation, _) = ButtonBorrowCardRemove.find(iu)
            if (removeLocation != null) {
                // Prefer the user's strong friend card when it is visible (template:
                // borrow_preferred_card.png). Blind first-row borrows produced measurably
                // weaker careers. Tap at the row's center X, not on the card art, which opens
                // card details.
                val (preferredLocation, _) = IconBorrowPreferredCard.find(iu)
                if (preferredLocation != null) {
                    MessageLog.i(TAG, "[NAV] Borrow Card list open. Preferred card found - selecting its row at (540, ${preferredLocation.y.toInt()})...")
                    gestureUtils.tap(540.0, preferredLocation.y, "borrow_preferred_row")
                } else {
                    val tapY = removeLocation.y + borrowListFirstRowOffsetPx
                    MessageLog.i(TAG, "[NAV] Borrow Card list open. Preferred card not visible - selecting the first card at (${removeLocation.x.toInt()}, ${tapY.toInt()})...")
                    gestureUtils.tap(removeLocation.x, tapY, "borrow_card_first_row")
                }
                waitSafe(2.0)
            } else {
                MessageLog.w(TAG, "[NAV] Tapped the friend slot but the Borrow Card list did not appear. Re-detecting...")
            }
            return TransitionResult.Continue
        }

        // Deck is already complete OR autoFillSupports is off. Click Start Career.
        if (ButtonStartCareer.check(iu, sourceBitmap = bitmap) || ButtonStartCareerOffset.check(iu, sourceBitmap = bitmap)) {
            if (startCareerClickAttempts >= 5) {
                return TransitionResult.Failed(
                    reason = "Start Career was clicked $startCareerClickAttempts times with no screen transition. An empty or invalid deck slot is the usual cause.",
                    transition = "SUPPORT_DECK_SCREEN -> PRE_RUN_CONFIRMATION",
                    recommendedAction = "Complete the support deck manually, then restart the queue.",
                )
            }
            startCareerClickAttempts++
            MessageLog.i(TAG, "[NAV] Deck complete or auto-fill off. Clicking Start Career! (attempt $startCareerClickAttempts)...")
            if (!ButtonStartCareer.click(iu, sourceBitmap = bitmap)) {
                ButtonStartCareerOffset.click(iu, sourceBitmap = bitmap)
            }
            waitSafe(3.0)
            return TransitionResult.Continue
        }

        MessageLog.i(TAG, "[NAV] Deck screen detected but 'Start Career!' not visible yet. Waiting...")
        waitSafe(2.0)
        return TransitionResult.Continue
    }

    /**
     * PRE_RUN_CONFIRMATION: Clicks "Start Career!" button (and handles a potential second confirmation).
     *
     * Detection: ButtonStartCareer / ButtonStartCareerOffset template match.
     * Transition: ButtonStartCareer.click() (template-matched).
     */

    /**
     * LEGACY_SELECT_SCREEN: Click Auto-Select to populate both legacy slots, tick all unchecked
     * options on the resulting Confirm Auto-Select dialog (Include Guests, plus Prioritize
     * Carnival Bonus Sparks during the Racing Carnival event), then click OK. After the legacies
     * are populated the Next button becomes active and the regular POST_RUN_RESULTS handler
     * advances the flow on the next iteration.
     *
     * Detection: ButtonAutoSelect template match.
     * Transition: ButtonAutoSelect.click() -> Checkbox.click() x N -> ButtonOk.click() -> ButtonNext (next iteration).
     */
    private fun handleLegacySelectScreen(): TransitionResult {
        if (legacyAutoSelectAlreadyDone) {
            MessageLog.i(TAG, "[NAV] Legacy Auto-Select already done this session. Clicking Next to advance...")
            if (ButtonNext.click(iu)) {
                waitSafe(2.0)
                return TransitionResult.Continue
            }
            return TransitionResult.Failed(
                reason = "LEGACY_SELECT_SCREEN detected after Auto-Select but ButtonNext click failed.",
                transition = "LEGACY_SELECT_SCREEN -> SUPPORT_DECK_SCREEN",
                recommendedAction = "Manually click Next on the Legacy Select screen and restart the queue.",
            )
        }

        MessageLog.i(TAG, "[NAV] On Legacy Select screen. Clicking Auto-Select...")
        if (!ButtonAutoSelect.click(iu)) {
            return TransitionResult.Failed(
                reason = "LEGACY_SELECT_SCREEN detected (ButtonAutoSelect matched) but click failed.",
                transition = "LEGACY_SELECT_SCREEN -> Confirm Auto-Select dialog",
                recommendedAction = "Manually click Auto-Select and restart the queue.",
            )
        }
        // Mark as done even if subsequent steps fail - the button stays visible after click,
        // so without this guard the next iteration would re-enter this handler.
        legacyAutoSelectAlreadyDone = true
        waitSafe(2.0)

        // Tick all unchecked checkboxes on the Confirm Auto-Select dialog. The dialog has 1 or
        // 2 checkboxes depending on whether the Racing Carnival event is active. Iterate while
        // an unchecked Checkbox can be clicked, with a safety cap of 3 iterations.
        for (i in 0 until 3) {
            if (!Checkbox.click(iu, tries = 1)) break
            waitSafe(0.4)
        }

        if (ButtonOk.click(iu)) {
            waitSafe(2.0)
            return TransitionResult.Continue
        }

        return TransitionResult.Failed(
            reason = "Confirm Auto-Select dialog OK click failed after ticking checkboxes.",
            transition = "LEGACY_SELECT_SCREEN -> next screen",
            recommendedAction = "Manually click OK on the Confirm Auto-Select dialog and restart the queue.",
        )
    }

    private fun handlePreRunConfirmation(): TransitionResult {
        MessageLog.i(TAG, "[NAV] Clicking 'Start Career!'...")

        if (ButtonStartCareer.click(iu) || ButtonStartCareerOffset.click(iu)) {
            waitSafe(3.0)
            // Check for a second "Start Career!" confirmation screen.
            if (ButtonStartCareer.check(iu) || ButtonStartCareerOffset.check(iu)) {
                MessageLog.i(TAG, "[NAV] Second 'Start Career!' confirmation detected. Clicking...")
                // Short-circuit: if the primary template clicks the button successfully, do NOT
                // also fire the offset variant. Without this guard, the offset click lands on
                // whatever screen appeared after the first click (cinematic, dialog) and produces
                // an unintended early tap.
                if (!ButtonStartCareer.click(iu)) {
                    ButtonStartCareerOffset.click(iu)
                }
                waitSafe(3.0)
            }
            return TransitionResult.Continue
        }

        return TransitionResult.Failed(
            reason = "PRE_RUN_CONFIRMATION detected (StartCareer matched) but click failed.",
            transition = "PRE_RUN_CONFIRMATION -> CINEMATIC_INTRO",
            recommendedAction = "Manually click 'Start Career!' and restart the queue.",
        )
    }

    /**
     * CINEMATIC_INTRO: Detected via ButtonSkipCinematic (>> fast-forward) or ButtonSkip.
     * Clicking the >> button skips the cinematic directly.
     *
     * Detection: ButtonSkipCinematic or ButtonSkip template match.
     * Transition: ButtonSkipCinematic.click() or ButtonSkip.click(), plus optional confirmation dialog.
     */
    private fun handleCinematicIntro(): TransitionResult {
        MessageLog.i(TAG, "[NAV] Cinematic detected. Attempting to skip...")

        // Try the >> fast-forward button first (directly skips the cinematic).
        if (ButtonSkipCinematic.click(iu)) {
            MessageLog.i(TAG, "[NAV] Clicked cinematic skip (>>) button.")
            waitSafe(3.0)
            // Handle any confirmation dialog that may appear.
            if (ButtonConfirm.check(iu)) {
                ButtonConfirm.click(iu)
                waitSafe(2.0)
            } else if (ButtonOk.check(iu)) {
                ButtonOk.click(iu)
                waitSafe(2.0)
            }
            return TransitionResult.Continue
        }

        // Fall back to the text-based Skip button if the >> button wasn't found.
        if (ButtonSkip.click(iu)) {
            MessageLog.i(TAG, "[NAV] Clicked text Skip button.")
            waitSafe(2.0)
            if (ButtonConfirm.check(iu)) {
                ButtonConfirm.click(iu)
                waitSafe(2.0)
            } else if (ButtonOk.check(iu)) {
                ButtonOk.click(iu)
                waitSafe(2.0)
            }
            return TransitionResult.Continue
        }

        return TransitionResult.Failed(
            reason = "CINEMATIC_INTRO detected but neither skip button could be clicked.",
            transition = "CINEMATIC_INTRO -> ACTIVE_TRAINING_MENU",
            isRecoverable = true,
            recommendedAction = "Manually skip the cinematic and restart the queue.",
        )
    }

    /**
     * QUICK_MODE_PROMPT: The Quick Mode Settings dialog appears after starting a career.
     * Handles two things:
     * 1. Click Confirm on the Quick Mode Settings dialog (if visible).
     * 2. Click "Skip Off" button twice to enable "Skip >>" (fast-forward all events).
     *
     * Detection: ButtonSkipOff or ButtonSkipOn template match.
     * Transition: ButtonConfirm.click() + ButtonSkipOff.click() x2, all template-matched.
     */
    private fun handleQuickModePrompt(): TransitionResult {
        MessageLog.i(TAG, "[NAV] Quick Mode / Skip toggle screen detected.")

        if (skipToggleAlreadyDone) {
            MessageLog.i(TAG, "[NAV] Skip toggle already maxed this session. Checking for Confirm...")
            if (ButtonConfirm.check(iu)) {
                ButtonConfirm.click(iu)
                waitSafe(2.0)
            }
            return TransitionResult.Continue
        }

        // Tap the Skip button position twice to cycle Skip Off → Skip > → Skip >>.
        // Position calibrated from actual game screen: white pill button center at
        // x ≈ 386/1080 = 35.7%, y ≈ 1847/1920 = 96.2% (measured via pixel sampling).
        val bitmap = iu.getSourceBitmap()
        val tapX = (bitmap.width * 0.357).toDouble()
        val tapY = (bitmap.height * 0.962).toDouble()

        MessageLog.i(TAG, "[NAV] Tapping Skip button (1st click) at ($tapX, $tapY)...")
        gestureUtils.tap(tapX, tapY, "skip_toggle_tap_1")
        waitSafe(0.6)

        MessageLog.i(TAG, "[NAV] Tapping Skip button (2nd click) at ($tapX, $tapY)...")
        gestureUtils.tap(tapX, tapY, "skip_toggle_tap_2")
        waitSafe(0.6)

        skipToggleAlreadyDone = true

        // Click Confirm on the Quick Mode Settings dialog if present.
        if (ButtonConfirm.check(iu)) {
            MessageLog.i(TAG, "[NAV] Clicking Confirm on Quick Mode Settings dialog...")
            ButtonConfirm.click(iu)
            waitSafe(2.0)
        }

        return TransitionResult.Continue
    }

    // ////////////////////////////////////////////////////////////////////////////
    // Diagnostics
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * Captures a screenshot and saves it to the debug output directory.
     * Returns a descriptive filename string, or an empty string if capture failed.
     */
    private fun captureFailureScreenshot(tag: String): String {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "nav_failure_${tag}_$timestamp"
            iu.saveBitmap(filename = filename, fullRes = true)
            MessageLog.i(TAG, "[NAV] Failure screenshot saved: $filename.png")
            "$filename.png"
        } catch (e: Exception) {
            MessageLog.w(TAG, "[NAV] Failed to save failure screenshot: ${e.message}")
            ""
        }
    }

    // ////////////////////////////////////////////////////////////////////////////
    // Utilities
    // ////////////////////////////////////////////////////////////////////////////

    /**
     * Waits for the specified number of seconds, checking if the bot is still running.
     * Ticks [Game.heartbeat] each iteration so the stall watchdog doesn't false-trigger
     * during navigator retry loops (which use their own sleep instead of [Game.wait]).
     */
    private fun waitSafe(seconds: Double) {
        val totalMs = (seconds * 1000).toLong()
        val checkInterval = 100L
        var remaining = totalMs
        while (remaining > 0) {
            if (!BotService.isRunning || StartModule.queueStopRequested) return
            Game.heartbeat()
            val sleep = minOf(checkInterval, remaining)
            Thread.sleep(sleep)
            remaining -= sleep
        }
    }
}
