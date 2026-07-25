package com.steve1316.uma_android_automation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.bot.CareerFinalizeGate
import com.steve1316.uma_android_automation.bot.FinalizeVerdict
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.SPARK_UNREADABLE_NAME
import com.steve1316.uma_android_automation.bot.SparkChooserProfile
import com.steve1316.uma_android_automation.bot.SparkConfirmationPill
import com.steve1316.uma_android_automation.bot.SparkSpendDiagnostics
import com.steve1316.uma_android_automation.bot.SparkKeepPolicy
import com.steve1316.uma_android_automation.bot.SparkKeepVerdict
import com.steve1316.uma_android_automation.bot.SparkPagerResolution
import com.steve1316.uma_android_automation.bot.SparkRerollGate
import com.steve1316.uma_android_automation.bot.SparkRerollPolicy
import com.steve1316.uma_android_automation.bot.SparkRerollTransaction
import com.steve1316.uma_android_automation.bot.SparkRowFact
import com.steve1316.uma_android_automation.bot.SparkRowKind
import com.steve1316.uma_android_automation.bot.SparkScanTermination
import com.steve1316.uma_android_automation.bot.SparkScrollMerge
import com.steve1316.uma_android_automation.bot.SparkSetReading
import com.steve1316.uma_android_automation.bot.SparkSetSide
import com.steve1316.uma_android_automation.bot.SparkSideBreakdown
import com.steve1316.uma_android_automation.bot.SparkStarEvidence
import com.steve1316.uma_android_automation.bot.SparkTextNorm
import com.steve1316.uma_android_automation.bot.SparkTxState
import com.steve1316.uma_android_automation.bot.SparkWhiteClass
import com.steve1316.uma_android_automation.bot.finalizeVerdictUsable
import com.steve1316.uma_android_automation.bot.keepDialogVerdict
import com.steve1316.uma_android_automation.bot.parseRemainingSkillPoints
import com.steve1316.uma_android_automation.bot.popupContradictsVerifiedBalance
import com.steve1316.uma_android_automation.bot.SparkPagerAction
import com.steve1316.uma_android_automation.bot.SparkPagerNav
import com.steve1316.uma_android_automation.bot.SparkPagerRepaint
import com.steve1316.uma_android_automation.bot.classifySparkPagerRepaint
import com.steve1316.uma_android_automation.bot.resolvePagerSide
import com.steve1316.uma_android_automation.bot.sparkPagerDotSide
import com.steve1316.uma_android_automation.bot.sparkSelectionDrivable
import com.steve1316.uma_android_automation.components.*
import com.steve1316.uma_android_automation.bot.QuickModeAction
import com.steve1316.uma_android_automation.bot.QuickModePlanner
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import com.steve1316.uma_android_automation.utils.QuickModeGeometry
import com.steve1316.uma_android_automation.utils.QuickModeOption
import com.steve1316.uma_android_automation.utils.grandConcertCareerCompleteScreenPresent
import com.steve1316.uma_android_automation.utils.grandConcertConcertPendingScreenPresent
import com.steve1316.uma_android_automation.utils.quickModeDialogPresent
import com.steve1316.uma_android_automation.utils.quickModeSelectedIndex
import com.steve1316.uma_android_automation.utils.SPARKS_CONFIRM_GEOMETRY
import com.steve1316.uma_android_automation.utils.SPARKS_SCREEN_GEOMETRY
import com.steve1316.uma_android_automation.utils.SPARK_CONFIRMATION_CANCEL_X
import com.steve1316.uma_android_automation.utils.SPARK_CONFIRMATION_CANCEL_Y
import com.steve1316.uma_android_automation.utils.SPARK_CONFIRMATION_CONFIRM_X
import com.steve1316.uma_android_automation.utils.SPARK_CONFIRMATION_CONFIRM_Y
import com.steve1316.uma_android_automation.utils.SPARK_CONFIRMATION_SET_NAME_OCR_REGION
import com.steve1316.uma_android_automation.utils.SPARK_INTRO_BUTTON_X
import com.steve1316.uma_android_automation.utils.SPARK_INTRO_BUTTON_Y
import com.steve1316.uma_android_automation.utils.SPARK_INTRO_TITLE_OCR_REGION
import com.steve1316.uma_android_automation.utils.SPARK_PAGER_CONFIRM_X
import com.steve1316.uma_android_automation.utils.SPARK_PAGER_CONFIRM_Y
import com.steve1316.uma_android_automation.utils.SPARK_PAGER_GEOMETRY
import com.steve1316.uma_android_automation.utils.SPARK_PAGER_HEADING_OCR_REGION
import com.steve1316.uma_android_automation.utils.SPARK_REROLLED_TITLE_OCR_REGION
import com.steve1316.uma_android_automation.utils.SparkListGeometry
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.SparkRowCell
import com.steve1316.uma_android_automation.utils.TraineeNameMatcher
import com.steve1316.uma_android_automation.utils.TraineePositionStore
import com.steve1316.uma_android_automation.utils.parseSparkRowCells
import com.steve1316.uma_android_automation.utils.parseSparkRowCellsAligned
import com.steve1316.uma_android_automation.utils.parseSparkRowCellsWithEvidence
import com.steve1316.uma_android_automation.utils.sparkConfirmationStructurePresent
import com.steve1316.uma_android_automation.utils.sparkIntroStructurePresent
import com.steve1316.uma_android_automation.utils.sparkPagerActiveDotIndex
import com.steve1316.uma_android_automation.utils.sparkPagerStructurePresent
import com.steve1316.uma_android_automation.utils.sparkRerolledStructurePresent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Whether a Borrow Card row's OCR text contains the user's preferred card/character name.
 * Both sides are lowercased and stripped to alphanumerics, so OCR line breaks, brackets, and
 * punctuation ("[Fire at My Heels]\nKitasan Black") cannot break a plain "kitasan black" match.
 * Pure so it is unit-testable without OpenCV.
 */
internal fun borrowRowMatchesPreference(rowText: String, preference: String): Boolean {
    val normRow = rowText.lowercase().filter { it.isLetterOrDigit() }
    val normPref = preference.lowercase().filter { it.isLetterOrDigit() }
    return normPref.isNotEmpty() && normRow.contains(normPref)
}

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

        /** Event Boost checkbox offset from the matched bar center (1080-wide capture). The
         * checkbox sits to the bar's left; anchoring off the template match keeps the tap
         * correct on both supported resolutions. */
        private const val EVENT_BOOST_CHECKBOX_DX = -245.0
        private const val EVENT_BOOST_CHECKBOX_DY = -4.0

        /** Spark-list complete-read bounds. Sets top out around a dozen rows (1 blue + 1 pink +
         * 1 unique + whites), each swipe advances ~4 rows, so 6 swipes covers well past any
         * observed set; the wall clock bounds a wedged capture, and hitting either bound is a
         * typed TIMED_OUT_PARTIAL that never authorizes a spend or a choice. */
        private const val SPARK_SCAN_MAX_SCROLLS = 6
        private const val SPARK_SCAN_BUDGET_MS = 45_000L

        /** Horizontal center of the spark lists, the swipe axis for the complete read. */
        private const val SPARK_LIST_SCROLL_X = 540f

        /** Event Boost checkbox state read by colour (template match can't tell OFF from ON - they
         * differ only in colour, which it normalises away). Sample a box of this half-size around
         * the checkbox center and count pixels where green dominates; the green (ticked) checkmark
         * yields hundreds, the grey (un-ticked) one ~zero (measured 565 vs 0). */
        private const val EVENT_BOOST_CHECKBOX_SAMPLE_HALF = 40
        private const val EVENT_BOOST_GREEN_DOMINANCE = 40
        private const val EVENT_BOOST_ON_GREEN_PIXELS = 50

        /** Floor for the session cap on item-based TP restores - bounds item spend even if
         * something loops. The effective cap scales with the configured queue length (see
         * [sessionRestoreCapFor]): one Max-fill funds ~3 normal careers, but an Event Boost
         * career costs 60 TP and the spark reroll draws on the same budget, so a fixed 10
         * starved legitimate long queues the UI allows (up to 20 runs). */
        private const val DEFAULT_MAX_TP_RESTORES = 10

        /** Maximum consecutive iterations stuck in the same non-goal state before failing. */
        private const val MAX_STUCK_ITERATIONS = 15

        /** TAP_TO_CONTINUE is exempt from MAX_STUCK_ITERATIONS (a scenario-event cutscene legitimately
         * spans many body-tap frames under one state label). It gets its own higher cap, with a
         * force-rebind partway since a no-op body tap is the dispatch-death signature. */
        private const val MAX_TAP_TO_CONTINUE_ITERATIONS = 30
        private const val TAP_TO_CONTINUE_REBIND_AT = 18

        /** Force-rebind partway through a stuck-in-KNOWN-state episode too: identical detection
         * every tick while every click changes nothing is the same dead-gesture-dispatch signature
         * the unknown-state and TAP_TO_CONTINUE paths already self-heal (2026-07-11: the Recover TP
         * quantity popup wedged as POST_RUN_RESULTS for all 15 iterations and killed the queue). */
        private const val STUCK_STATE_REBIND_AT = 7

        /** Borrow Card list row pitch on 1080-wide captures, measured across the 2026-07-13
         * picker screenshots (row centers 440/700/965/1225/1490 -> ~262 px). */
        private const val BORROW_ROW_PITCH_PX = 262

        /** Half-height of the card-name OCR band inside a borrow row. The name renders as two
         * lines (outfit, then character) centered in the row, well inside +-70 px. */
        private const val BORROW_NAME_BAND_HALF_HEIGHT = 70

        /** Distance from a row's Last Login pill center up to the row's own center (the card-name
         * band), measured on the same captures. Rows are located by the pill via findAll, which
         * keeps the scan correct at any scroll position. */
        private const val BORROW_PILL_TO_ROW_CENTER_PX = 87

        /** One Borrow Card page advance: three row pitches, so consecutive pages overlap by two
         * rows and the text dedup absorbs any drag imprecision. */
        private const val BORROW_PAGE_SWIPE_PX = 3f * BORROW_ROW_PITCH_PX

        /** Page-advance swipes after the first page. Nine pages cover ~29 unique rows - live
         * followed pools proved deeper than the original five-page estimate and the scan stopped
         * one swipe short of the list end. It still stops early the moment a page adds nothing
         * new, so short lists pay no extra time. */
        private const val MAX_BORROW_SCAN_PAGES = 8

        /** The "! Duplicate Support" pill straddles a borrow row's top edge; its center sits
         * ~113 px above the row center on the measured captures. A pill inside this offset band
         * marks the row's character as already present in the deck. */
        private const val BORROW_DUPLICATE_PILL_MIN_OFFSET = 78
        private const val BORROW_DUPLICATE_PILL_MAX_OFFSET = 148

        /** Bounded replacements of a borrowed card the deck flags as a duplicate. Each retry
         * excludes the refused character, so this only exhausts when the player's own deck
         * clashes with this many distinct picks in a row. */
        private const val MAX_BORROW_DUPLICATE_REPLACEMENTS = 3

        /** Throttle for isOnHomeScreen()'s unknown-screen evidence capture: the daily-reset
         * re-entry path re-probes every unknown tick and each capture is a full-res PNG. */
        private const val HOME_PROBE_CAPTURE_THROTTLE_MS = 10L * 60L * 1000L

        /** Last wall-clock time the unknown-home-probe screenshot was taken. Lives on the
         * companion because callers construct a fresh navigator per probe - an instance field
         * would never throttle anything. */
        @Volatile
        private var lastHomeProbeCaptureAtMs = 0L

        /** Item-based TP restores performed this bot session. Lives on the companion because
         * each between-run handoff constructs a fresh navigator - an instance field would reset
         * every handoff, making the per-session cap really a per-handoff cap. Reset from
         * StartModule at session start. */
        @Volatile
        private var tpRestoresThisSession = 0

        /** Effective restore cap for this bot session, derived from the configured queue length
         * at session start. Companion-held for the same reason as the counter. */
        @Volatile
        private var maxTpRestoresThisSession = DEFAULT_MAX_TP_RESTORES

        /** Session restore budget for a queue of [totalRuns] careers: up to one career-launch
         * restore plus one reroll restore per career, plus startup slack. Never below the
         * [DEFAULT_MAX_TP_RESTORES] floor, and the input is clamped so a corrupt setting row
         * cannot make the spend bound effectively unbounded. */
        internal fun sessionRestoreCapFor(totalRuns: Int): Int = maxOf(DEFAULT_MAX_TP_RESTORES, 2 * totalRuns.coerceIn(0, 100) + 2)

        /** Reason string for the cap-reached career-start failure. A pure seam so the message the
         * queue persists stays pinned by a JVM test - its predecessor reused the generic out-of-TP
         * decline text, which told the user to enable a setting that was already enabled. */
        internal fun tpRestoreCapReachedReason(count: Int, max: Int): String =
            "TP restore session cap reached ($count/$max restores this bot session). Item restore is enabled but the session budget is spent, " +
                "so the restore prompt was declined. All completed runs are saved - pressing Start begins a fresh bot session and re-arms the restore budget."

        /** Resets the session-scoped TP restore counter and derives the session's cap from the
         * configured queue length. Called when a new bot session starts. */
        internal fun resetTpRestoresForSession(totalRuns: Int = 1) {
            tpRestoresThisSession = 0
            maxTpRestoresThisSession = sessionRestoreCapFor(totalRuns)
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

        /** Career-end SPARKS screen: the generated spark set with "Reroll Sparks" + Confirm. */
        SPARKS_SCREEN,

        /** "Confirm Reroll" spend dialog ("Spend 30 TP to reroll Sparks?"). Detected only while
         * this career's transaction holds an approved spend, as a recovery net for a missed
         * inline spend click; its green button is the spend and must never be pressed blind. */
        CONFIRM_REROLL_DIALOG,

        /** "Sparks Rerolled" result screen after a successful spend: the redrawn set + Next. */
        SPARKS_REROLLED_RESULT,

        /** "Spark Selection" intro dialog ("Select which Sparks to keep.") + Next. */
        SPARK_SELECTION_INTRO,

        /** The Spark Selection pager: page 1 "Rerolled Sparks", page 2 "Original Sparks", with
         * chevrons, page dots, and a Confirm that irreversibly commits the visible page. */
        SPARK_SELECTION_PAGER,

        /** The Spark Selection "Confirmation" dialog: a green header naming the chosen set, the
         * full list, and Cancel/Confirm ("You won't be able to change Sparks later."). */
        SPARK_SELECTION_CONFIRMATION,

        /** The ORDINARY keep confirmation raised by Confirm on the SPARKS screen: the same
         * dialog chrome but a plain "Sparks" pill instead of a chosen-side name, and no reroll
         * in play. Every no-reroll career ends on it. Distinct from the post-reroll selection
         * confirmation on purpose - it names no side, so the winner-header logic must never
         * run against it. */
        SPARKS_KEEP_CONFIRMATION,

        /** "Career Complete" dialog with "To Home" / "Edit Team" buttons. */
        CAREER_COMPLETE_DIALOG,

        /** "Continue Career" dialog with "Cancel" and "Resume" buttons. */
        CONTINUE_CAREER_DIALOG,

        /** "Veteran Umamusume Max" popup: the veteran roster is full and no career can start until one
         * is transferred/released. Terminal - the queue stops with a clear reason rather than looping. */
        VETERAN_UMAMUSUME_MAX,

        /** Game main menu with the bottom menu bar. */
        HOME_SCREEN,

        /** Career entry / mode selection. Requires CAREER button template (not yet provided). */
        CAREER_ENTRY,

        /** Scenario Select carousel. Paged to the run's target scenario before advancing. */
        SCENARIO_SELECT,

        /** Trainee/character selection or reuse. Requires template (not yet provided). */
        TRAINEE_SETUP,

        /**
         * Trainee Select roster (the grid of owned units). The game shows it on every launch with a
         * green Next; the non-rotation path taps through it as POST_RUN_RESULTS (keeping the last
         * trainee). Under rotation it is detected via header OCR and handled by selecting the target
         * trainee and verifying her name (match-or-stop).
         */
        TRAINEE_SELECT_SCREEN,

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

        /** In-career "tap to continue" screen that shows a Skip pill (scenario-event cutscene, goal
         * intro, race intro). Distinct from QUICK_MODE_PROMPT: advances on a body tap (not the Skip
         * pill), does not run the skip-maxing handler, and is exempt from the per-state stuck-limit
         * because a cutscene legitimately spans many frames under one state label. */
        TAP_TO_CONTINUE,

        /** Opening cinematic / intro. Detected via Skip button or requires Pause template. */
        CINEMATIC_INTRO,

        /** Bot's normal start point - the in-career training menu. Navigation is complete. */
        ACTIVE_TRAINING_MENU,

        /** "Restore TP?" confirmation - the account lacks Training Points for another career. */
        TP_RESTORE_DIALOG,

        /** Recover TP quantity popup (Min/-/+/Max over Cancel/OK) opened from the Recover TP
         * picker. Gets its own state so its green OK can never fall through to the generic
         * POST_RUN_RESULTS clicker, and so a restore cut short mid-flow (or a queue started
         * cold on this screen) is finished instead of wedging. */
        RECOVER_TP_QUANTITY,

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

    /** Finalize mode for the queue's final run: end navigation successfully at the home lobby
     * instead of launching another career (set per navigate() call). */
    private var finalizeToHomeMode: Boolean = false

    /** Single-run trainee expectation (set per navigate() call by Game.kt's auto-navigation from
     * general.appliedPresetTrainee). When non-blank, the Trainee Select gate arms and the handler
     * verifies/hunts THIS name - never queueState.currentTrainee, whose stale leftover from an
     * interrupted queue is how a Rudolf single run nearly launched El Condor under his settings
     * (2026-07-09, twice). Blank on every queue-managed navigate() call, keeping those paths
     * byte-identical. */
    private var singleRunTraineeTarget: String = ""
    private var singleRunTraineeTargetExcludes: String = ""

    /** True once the Trainee Select handler ran for a single-run expectation this navigate() call.
     * Read by the Legacy Select backstop: expectation armed + roster never handled = a missed
     * detection tapped through the roster and the preselected trainee may be wrong. */
    private var singleRunTraineeSelectHandled: Boolean = false

    // Session-scoped flag: Auto-Select on the Legacy Select screen has already been clicked.
    // The Auto-Select button stays visible after a successful run - without this guard the
    // navigator would re-enter Legacy Select handling each iteration instead of clicking Next.
    private var legacyAutoSelectAlreadyDone: Boolean = false

    // Session-scoped flag: Skip toggle has already been maxed (Skip >>) in this session.
    private var skipToggleAlreadyDone: Boolean = false

    // Session-scoped latch: the launch has provably advanced PAST Start Career (we have reached
    // the final confirmation / cinematic / quick-mode prompt). Once set, Trainee Select is no longer
    // a legal screen, so the rotation detector is disarmed. The game shows an in-career "Umamusume
    // Details" card on turn 1 of every freshly started career, and its character portrait makes the
    // header-region OCR hallucinate "TRAINEE", which made isTraineeSelectScreen classify the card as
    // the roster and stop the queue on the wrong trainee every launch. The card is left to the
    // campaign's umamusume_details handler, which reads the real in-career name and runs
    // verifyRotationTrainee.
    private var careerLaunchInitiated: Boolean = false

    // Session-scoped counters for the support deck screen. The borrowed friend card never
    // persists between careers, and the game silently ignores Start Career while that slot
    // is empty, so both paths need bounded retries instead of an open-ended click loop.
    private var friendSlotFillAttempts: Int = 0
    private var startCareerClickAttempts: Int = 0

    // Duplicate-borrow replacement state, reset per navigate(). A borrow of a character already
    // in the deck COMMITS in the picker but blocks Start Career; the deck screen then marks both
    // cards with a "! Duplicate Support" pill. Each replacement excludes the refused character so
    // the next pick moves down the priority list instead of repeating the clash.
    private var borrowDuplicateReplacements: Int = 0
    private val borrowExcludedCharacters = mutableSetOf<String>()
    private var lastBorrowPickEntry: String? = null

    // The launch's active trainee identity (bare name or "[Outfit] Name"), used to reject borrow
    // candidates of the trainee's own character - the game refuses such a deck with a red
    // "! Trainee" pill and a disabled Start Career, which once burned a whole queued run.
    private var borrowLaunchTraineeTarget: String = ""

    // Set when the trainee-conflict refusal was detected by the transient formation message
    // rather than a pill template, so the next handler pass routes into the borrow replacement
    // flow even though the pill checks missed.
    private var forceBorrowReplacement: Boolean = false

    // The finalization-verdict token captured when this navigation began (null when none was
    // armed). Only a verdict matching it may govern this navigation's Complete Career / Finish
    // clicks - see CareerFinalizeGate for the lifecycle.
    private var expectedFinalizeToken: String? = null

    // Whether the finalization guard applies to this navigation (global Skill Spend Mode is
    // Adaptive). Manual navigations keep the pre-guard behavior bit-for-bit.
    private var finalizeGuardActive: Boolean = false

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
     * Point this navigator at an already-live [Game] instead of letting [ensureInitialised] build a
     * throwaway one. Used when the in-career loop drives the navigator mid-career (re-entering a
     * career after a daily-reset bounce to the Home lobby) so we reuse the running capture pipeline
     * rather than standing up a second Game beside it. Call before isOnHomeScreen()/navigate().
     */
    fun attachLiveGame(game: Game) {
        tempGame = game
        imageUtils = game.imageUtils
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
            val state = detectScreenState(deepHomeProbe = true)
            if (state == LaunchScreenState.UNKNOWN) {
                // Every caller of this probe EXPECTS the lobby (cold start, daily-reset bounce,
                // post-restart recovery), so a screen no detector recognises is exactly what a
                // reskinned lobby looks like - photograph it as recapture material. Throttled via
                // the companion because the daily-reset path re-probes every unknown tick on a
                // fresh navigator instance.
                val now = System.currentTimeMillis()
                if (now - lastHomeProbeCaptureAtMs > HOME_PROBE_CAPTURE_THROTTLE_MS) {
                    lastHomeProbeCaptureAtMs = now
                    captureFailureScreenshot("home_probe_unknown")
                }
            }
            state == LaunchScreenState.HOME_SCREEN
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
     * @param finalizeToHome If true, stop successfully at the home lobby instead of launching
     *   another career - used after a queue's final run so the career-end flow (summary, results,
     *   the sparks screen and its reroll, veteran registration) still completes.
     * @param singleRunTrainee Applied-preset trainee for a SINGLE (non-queue) launch, in the
     *   roster-preview form. When non-blank, Trainee Select is verified against this name instead
     *   of being tapped through on the game's sticky preselection; queue callers leave it blank.
     * @param singleRunTraineeExcludes Sibling-outfit names to skip for [singleRunTrainee],
     *   newline-joined (same convention as queueState.currentTraineeExcludes).
     * @return A [NavigationResult] indicating success or failure with diagnostics.
     */
    fun navigate(reuseLastLaunchSetup: Boolean, finalizeToHome: Boolean = false, singleRunTrainee: String = "", singleRunTraineeExcludes: String = ""): NavigationResult {
        val autoFillSupports = SettingsHelper.getBooleanSetting("runQueue", "autoFillSupports", false)
        MessageLog.i(
            TAG,
            "[NAV] Starting between-run navigation. reuseLastLaunchSetup=$reuseLastLaunchSetup, autoFillSupports=$autoFillSupports, finalizeToHome=$finalizeToHome" +
                if (singleRunTrainee.isNotBlank()) ", singleRunTrainee=$singleRunTrainee" else "",
        )

        // Reset session-scoped flags for this navigation run.
        autoFillAlreadyDone = false
        skipToggleAlreadyDone = false
        legacyAutoSelectAlreadyDone = false
        careerLaunchInitiated = false
        borrowDuplicateReplacements = 0
        borrowExcludedCharacters.clear()
        lastBorrowPickEntry = null
        forceBorrowReplacement = false
        // Finalization-guard context for THIS navigation: the verdict present when the
        // navigation begins is the one career this navigation may finalize - a verdict armed
        // later (a different career) can never match the captured token. Guard activity follows
        // the global Skill Spend Mode: Adaptive navigations refuse to press Finish without a
        // usable verdict, Manual navigations behave exactly as they always did.
        expectedFinalizeToken = CareerFinalizeGate.verdict?.careerToken
        finalizeGuardActive = SettingsHelper.getStringSetting("skills", "skillSpendMode").trim().lowercase() == "adaptive"
        finalizeToHomeMode = finalizeToHome
        singleRunTraineeTarget = singleRunTrainee
        singleRunTraineeTargetExcludes = singleRunTraineeExcludes
        singleRunTraineeSelectHandled = false

        // Smart Borrow must never pick a support of the ACTIVE TRAINEE's character: the game
        // refuses such a deck (red "! Trainee" pill, Start Career disabled, "Includes a
        // character identical to the Trainee."). Seeding the exclusion set with the launch's
        // trainee makes every candidate path reject her cards up front - the curated scan, the
        // preferred-name pick, the validated default pick, and the replacement search all
        // consult this set. Blank when no trainee identity is known (non-rotation launches),
        // which leaves behavior unchanged.
        borrowLaunchTraineeTarget = if (singleRunTrainee.isNotBlank()) singleRunTrainee else SettingsHelper.getStringSetting("queueState", "currentTrainee")
        borrowEntryCharacter(borrowLaunchTraineeTarget).takeIf { it.isNotEmpty() }?.let {
            borrowExcludedCharacters.add(it)
            MessageLog.i(TAG, "[NAV] [BORROW] Active trainee \"$it\" is excluded from borrow candidates for this launch.")
        }

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

        // MuMu wipes the accessibility service at career-end transitions, which the between-run
        // navigator crosses. The in-career loop self-heals every tick but this navigator does not,
        // so a kill here would silently stop every tap from landing. Rebind once before we start
        // driving the FSM; cheap when the service is alive.
        tempGame?.ensureAccessibilityService()

        var currentState = LaunchScreenState.POST_RUN_RESULTS
        var consecutiveUnknowns = 0
        var stuckInStateCount = 0
        // TAP_TO_CONTINUE's own counter (it is exempt from stuckInStateCount). Resets on any other state.
        var tapToContinueCount = 0
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
        // One-shot recovery for exceptions crossing the FSM boundary (see the catch blocks below).
        var exceptionRecoveryUsed = false

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

            // Detect current screen state. The FSM previously caught only InterruptedException, so
            // capture death (getSourceBitmap throws IllegalStateException after failed captures) or
            // a BotService stop/teardown race escaped navigate() unstructured: no NavigationResult,
            // no queueFailed event, stale queueState. Rebind once and retry; recurrence fails
            // structured. InterruptedException must keep propagating - the nav deadline thread
            // relies on it to kill a wedged queue thread.
            val detectedState =
                try {
                    // From the second consecutive unknown on, arm the deep home tier: the primary
                    // lobby anchors may be what the current screen's skin broke, and the rebinds
                    // below have already had their chance to fix a dead capture/dispatch instead.
                    detectScreenState(deepHomeProbe = consecutiveUnknowns >= 2)
                } catch (e: InterruptedException) {
                    throw e
                } catch (e: Exception) {
                    if (!BotService.isRunning || StartModule.queueStopRequested) {
                        // A stop/teardown race makes capture and service calls throw; report the stop
                        // itself, not an infrastructure failure (stop attribution feeds the queue ledger).
                        return NavigationResult(
                            success = false,
                            lastDetectedState = currentState.name,
                            failureReason = "Queue was stopped during navigation.",
                            isRecoverable = false,
                            recommendedAction = "Restart the queue when ready.",
                        )
                    }
                    if (!exceptionRecoveryUsed) {
                        exceptionRecoveryUsed = true
                        MessageLog.w(TAG, "[NAV] ${e.javaClass.simpleName} during screen detection: ${e.message}. Force-rebinding the accessibility service and retrying once.")
                        tempGame?.forceRebindAccessibilityService()
                        // The rebind may have fixed whatever wedged the FSM, so grant fresh attempts -
                        // carrying stale stuck/progress counters into recovery would fail it early.
                        stuckInStateCount = 0
                        tapToContinueCount = 0
                        iterationsWithoutProgress = 0
                        consecutiveUnknowns = 0
                        waitSafe(2.0)
                        continue
                    }
                    return NavigationResult(
                        success = false,
                        lastDetectedState = currentState.name,
                        failureReason = "Screen detection threw ${e.javaClass.simpleName}: ${e.message}",
                        isRecoverable = false,
                        recommendedAction = "Check that screen capture and the accessibility service are alive, then restart the queue. All completed runs are saved.",
                        screenshotPath = captureFailureScreenshot("exception_detect"),
                    )
                }
            MessageLog.i(TAG, "[NAV] Attempt $attempt: Detected state = $detectedState (previous = $currentState)")

            if (detectedState != LaunchScreenState.UNKNOWN) {
                // Track if we're stuck in the same state (e.g. POST_RUN_RESULTS clicking but not advancing).
                // This is different from normal progress where POST_RUN_RESULTS may repeat across many screens.
                if (detectedState == currentState &&
                    detectedState != LaunchScreenState.ACTIVE_TRAINING_MENU &&
                    detectedState != LaunchScreenState.TAP_TO_CONTINUE
                ) {
                    stuckInStateCount++
                    // Dead gesture dispatch wedges a KNOWN state exactly like this: the same
                    // detection every tick while every click silently no-ops (dispatchGesture
                    // still returns true). Rebind once mid-episode - a real transition resets
                    // the counter, so this only fires when clicks demonstrably do nothing.
                    if (stuckInStateCount == STUCK_STATE_REBIND_AT) {
                        MessageLog.w(TAG, "[NAV] $detectedState repeated $STUCK_STATE_REBIND_AT times with no effect from clicks; force-rebinding the accessibility service.")
                        tempGame?.forceRebindAccessibilityService()
                    }
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
                // TAP_TO_CONTINUE legitimately repeats across many cutscene frames under one state
                // label, so it is exempt from stuckInStateCount above. Track it on its own higher cap
                // so a genuinely wedged screen still fails, with a force-rebind partway (a no-op body
                // tap is the dead-gesture-dispatch signature).
                if (detectedState == LaunchScreenState.TAP_TO_CONTINUE) {
                    tapToContinueCount++
                    if (tapToContinueCount == TAP_TO_CONTINUE_REBIND_AT) {
                        MessageLog.w(TAG, "[NAV] TAP_TO_CONTINUE not advancing after $TAP_TO_CONTINUE_REBIND_AT taps; force-rebinding accessibility service.")
                        tempGame?.forceRebindAccessibilityService()
                    }
                    if (tapToContinueCount >= MAX_TAP_TO_CONTINUE_ITERATIONS) {
                        val screenshotPath = captureFailureScreenshot("stuck_in_TAP_TO_CONTINUE")
                        return NavigationResult(
                            success = false,
                            lastDetectedState = detectedState.name,
                            failureReason = "Stuck on a tap-to-continue screen for $MAX_TAP_TO_CONTINUE_ITERATIONS taps without advancing.",
                            failedTransition = "TAP_TO_CONTINUE -> next screen",
                            isRecoverable = true,
                            recommendedAction = "Manually advance past the current screen and restart the queue.",
                            screenshotPath = screenshotPath,
                        )
                    }
                } else {
                    tapToContinueCount = 0
                }
                // Progress counter: resets only when we enter a state we haven't seen this
                // navigation session yet. Just oscillating between known states does NOT reset.
                if (seenStates.add(detectedState)) {
                    iterationsWithoutProgress = 0
                } else if (detectedState != LaunchScreenState.ACTIVE_TRAINING_MENU &&
                    detectedState != LaunchScreenState.TAP_TO_CONTINUE
                ) {
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
                // Latch once the launch has provably passed Start Career. These three states only
                // occur after the Start Career click succeeds, and the in-career "Umamusume Details"
                // card the game shows right after them must NOT be tested for Trainee Select.
                if (detectedState == LaunchScreenState.PRE_RUN_CONFIRMATION ||
                    detectedState == LaunchScreenState.CINEMATIC_INTRO ||
                    detectedState == LaunchScreenState.QUICK_MODE_PROMPT ||
                    detectedState == LaunchScreenState.TAP_TO_CONTINUE
                ) {
                    careerLaunchInitiated = true
                }
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
                // An UNKNOWN screen is the exact signature of a dead accessibility service (taps stop
                // landing, so the game never advances to a recognised screen). Rebind before retrying
                // rather than burning all attempts against a service that will never respond. The
                // first unknown does the cheap string check (catches the grant being wiped); from the
                // second on, MuMu's nastier "enabled-but-dispatch-dead" mode is likely, so force a
                // hard off->on rebind that the string check can't see.
                if (consecutiveUnknowns >= 2) {
                    tempGame?.forceRebindAccessibilityService()
                } else {
                    tempGame?.ensureAccessibilityService()
                }
                waitSafe(2.0)
                continue
            }

            // Handle the detected state. Same exception boundary as detection above: handlers
            // drive taps and captures, so they share the same throwers.
            val transitionResult =
                try {
                    handleState(currentState, reuseLastLaunchSetup, autoFillSupports)
                } catch (e: InterruptedException) {
                    throw e
                } catch (e: Exception) {
                    if (!BotService.isRunning || StartModule.queueStopRequested) {
                        // Same stop/teardown mislabel guard as the detection catch above.
                        return NavigationResult(
                            success = false,
                            lastDetectedState = currentState.name,
                            failureReason = "Queue was stopped during navigation.",
                            isRecoverable = false,
                            recommendedAction = "Restart the queue when ready.",
                        )
                    }
                    if (!exceptionRecoveryUsed) {
                        exceptionRecoveryUsed = true
                        MessageLog.w(TAG, "[NAV] ${e.javaClass.simpleName} while handling $currentState: ${e.message}. Force-rebinding the accessibility service and retrying once.")
                        tempGame?.forceRebindAccessibilityService()
                        // Fresh attempts post-rebind, mirroring the detection catch.
                        stuckInStateCount = 0
                        tapToContinueCount = 0
                        iterationsWithoutProgress = 0
                        consecutiveUnknowns = 0
                        waitSafe(2.0)
                        continue
                    }
                    return NavigationResult(
                        success = false,
                        lastDetectedState = currentState.name,
                        failureReason = "Handling $currentState threw ${e.javaClass.simpleName}: ${e.message}",
                        failedTransition = "${currentState.name} -> next screen",
                        isRecoverable = false,
                        recommendedAction = "Check that screen capture and the accessibility service are alive, then restart the queue. All completed runs are saved.",
                        screenshotPath = captureFailureScreenshot("exception_handle"),
                    )
                }

            when (transitionResult) {
                is TransitionResult.Success -> {
                    return NavigationResult(success = true, lastDetectedState = currentState.name)
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
     *
     * @param deepHomeProbe Also run the looser end-of-chain home-lobby detectors (CAREER text-crop
     *   template, then OCR at the button's fixed position) when nothing else matched. Event skins
     *   restyle the lobby chrome that the primary home anchors match on (the 2026-07-14 anniversary
     *   reskins the home screen outright), so the one-shot home probes (cold start, daily-reset
     *   re-entry, post-restart recovery) and an FSM loop that is already failing detection enable
     *   this; normal navigation stays byte-identical.
     */
    private fun detectScreenState(deepHomeProbe: Boolean = false): LaunchScreenState {
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
        //
        // On a mandatory RACE DAY the in-career main screen collapses to Skills + Race! with a
        // "Race Day" ribbon and NO Training/Rest button, so those two checks miss it. When the
        // navigator resumes a career interrupted on a race day (a mid-career client kick lands here),
        // that screen must still count as reaching the career - the IconRaceDayRibbon is the same
        // signal Campaign.checkMandatoryRacePrepScreen uses, and handing off lets the campaign race
        // it. The ribbon appears only in-career, never on a between-run/launch screen, so treating
        // it as the success/exit state cannot misfire on the launch path.
        //
        // ButtonRace / ButtonRaceExclamation catch the next screen in that flow - the race lineup
        // (entrant roster + green "Race!") a Continue-Career resume lands on when the career was
        // interrupted deeper into a race. It has no ribbon or Training button, so without this a
        // resume onto it failed CONTINUE_CAREER_DIALOG -> UNKNOWN and killed the queue (2026-07-11).
        // The green Race! button exists only in-career, so it cannot misfire on a launch screen.
        if (ButtonTraining.check(iu, sourceBitmap = bitmap) ||
            ButtonRest.check(iu, sourceBitmap = bitmap) ||
            IconRaceDayRibbon.check(iu, sourceBitmap = bitmap) ||
            ButtonRace.check(iu, sourceBitmap = bitmap) ||
            ButtonRaceExclamation.check(iu, sourceBitmap = bitmap)
        ) {
            return LaunchScreenState.ACTIVE_TRAINING_MENU
        }

        // Career Complete dialog - "To Home" button is uniquely present on this screen.
        // Common immediately after a career run completes.
        if (ButtonToHome.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.CAREER_COMPLETE_DIALOG
        }

        // Career-end SPARKS screen - the Reroll Sparks button is unique to it. Must precede the
        // generic POST_RUN_RESULTS match: this screen also shows a green Confirm, and the reroll
        // decision has to happen before anything clicks it.
        if (ButtonRerollSparks.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.SPARKS_SCREEN
        }

        // Home screen - CAREER button visible (the strongest home-screen signal).
        // We do NOT use ButtonMenuBarHomeSelected alone because that menu bar appears on
        // many intermediate screens (Scenario Select, Trainee Select, etc.) that are reached
        // from the home screen but still have the bottom nav visible.
        if (ButtonCareerHome.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.HOME_SCREEN
        }

        // Trainee Select roster (rotation switch or a single run with an applied-preset trainee).
        // MUST precede the generic POST_RUN_RESULTS Next check below: this screen's green "Next"
        // template-matches but advancing it without first selecting + verifying the trainee would
        // start the WRONG career. Gated on rotation being enabled OR a single-run expectation being
        // set, so a launch with neither pays zero OCR cost and stays byte-for-byte unchanged.
        // Also gated on NOT having passed Start Career yet: the roster only appears pre-deck, so once
        // the launch has advanced past Start Career this fragile header-OCR check is skipped entirely,
        // which is what stops the in-career "Umamusume Details" card from being misread as the roster.
        if ((SettingsHelper.getBooleanSetting("runQueue", "enableTraineeRotation", false) || singleRunTraineeTarget.isNotBlank()) &&
            !careerLaunchInitiated &&
            isTraineeSelectScreen(bitmap)
        ) {
            return LaunchScreenState.TRAINEE_SELECT_SCREEN
        }

        // Legacy Select screen - Auto-Select button (green pill, optionally under a pink
        // Racing Carnival Underway banner). Checked BEFORE the generic POST_RUN_RESULTS Next
        // check because Next on Legacy Select is greyed out / non-clickable until Auto-Select
        // populates both legacy slots; clicking Next there is a no-op and would trap the
        // navigator in a 15-iteration stuck loop. The title banner is a second signal because the
        // July 2026 patch restyled Auto-Select below the match threshold: a lone Auto-Select miss
        // used to fall through here to the greyed Next and either wedge (stuck loop) or, when the
        // patch's pre-filled selection enables Next, silently skip legacy selection entirely.
        if (ButtonAutoSelect.check(iu, sourceBitmap = bitmap) || LabelLegacySelectTitle.check(iu, sourceBitmap = bitmap)) {
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

        // Scenario Select carousel - its header banner is unique to this screen. MUST precede the
        // generic POST_RUN_RESULTS Next check: the screen shows a green Next that would otherwise
        // blind-confirm whatever scenario the game last played, which is exactly wrong for a
        // cross-scenario rotation that needs the carousel paged to the target scenario first.
        // Gated on the launch not having advanced past Start Career (mirrors TRAINEE_SELECT_SCREEN):
        // the header only exists pre-deck, so in-career screens never pay this scan.
        if (!careerLaunchInitiated && LabelScenarioSelectHeader.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.SCENARIO_SELECT
        }

        // "Veteran Umamusume Max" popup: the veteran roster is full (e.g. 260/260) and the game blocks
        // starting a career until one is transferred/released. MUST precede POST_RUN_RESULTS - the popup
        // carries a Close that the generic handler would tap, bouncing back to Scenario Select forever.
        if (LabelVeteranUmamusumeMax.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.VETERAN_UMAMUSUME_MAX
        }

        // Recover TP quantity popup - Max exists only on quantity popups, and the TP restore's is
        // the only quantity popup in the between-run flow. MUST precede the generic
        // POST_RUN_RESULTS check: the popup's green OK matched it on 2026-07-11 and the generic
        // handler clicked OK at quantity 0 for 15 straight iterations while the queue died.
        if (ButtonMax.check(iu, sourceBitmap = bitmap) && ButtonOk.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.RECOVER_TP_QUANTITY
        }

        // Post-spend Spark Selection screens. These MUST precede the generic POST_RUN_RESULTS
        // chain below: all four carry a green Next or Confirm that the generic handler would
        // click blind, and that is exactly how ten live reroll spends had their keep-original-
        // vs-rerolled choice resolved by button position instead of a comparison (zero
        // "rerolled" corpus records existed despite ten spends). The pager's Confirm and the
        // Confirmation dialog's Confirm are irreversible ("You won't be able to change Sparks
        // later."), so the generic handler must never own these screens.
        //
        // Cost control: the structural probes are raw pixel samples (no OpenCV scan), gated on
        // the capture being the supported 1080x1920 game surface; OCR runs only after a
        // structural match. The spend dialog check is template-based but gated on this
        // career's transaction holding an approved, unconfirmed spend, so every other
        // navigation pays nothing for it.
        val sparkTransaction = SparkRerollGate.transaction
        if (sparkTransaction?.state == SparkTxState.SPEND_APPROVED && ButtonRerollSparksConfirm.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.CONFIRM_REROLL_DIALOG
        }
        if (bitmap.width >= 1080 && bitmap.height >= 1840) {
            val sparkSampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            val sparkDrivable = sparkSelectionDrivable(sparkTransaction, System.currentTimeMillis())
            if (sparkConfirmationStructurePresent(sparkSampler)) {
                // Three live pill variants share this dialog chrome: "Original Sparks" and
                // "Rerolled Sparks" belong to the post-reroll selection, while a plain "Sparks"
                // pill is the ORDINARY keep confirmation every no-reroll career ends on. The
                // transaction is the stronger signal where it exists - a career that never
                // confirmed a 30 TP spend cannot be looking at a selection dialog - and the pill
                // decides when it does not.
                val pill = SparkTextNorm.confirmationPill(readSparkOcrRegion(bitmap, SPARK_CONFIRMATION_SET_NAME_OCR_REGION, "spark_conf_pill"))
                val noSpendOnThisCareer = sparkTransaction != null && !sparkTransaction.spendEverConfirmed
                if (noSpendOnThisCareer || pill == SparkConfirmationPill.PLAIN) {
                    // Ordinary keep confirmation. Its handler cross-checks the pill against the
                    // transaction and blocks on a contradiction (a plain pill after a confirmed
                    // spend, or a side-named pill on a career that never spent).
                    return LaunchScreenState.SPARKS_KEEP_CONFIRMATION
                }
                if (sparkDrivable) {
                    return LaunchScreenState.SPARK_SELECTION_CONFIRMATION
                }
                // No live transaction (process restart mid-selection, or a hand-played reroll).
                // Only a header that PROVABLY names the Original set may fall through to the
                // generic keep-original flow, which cannot lose the career's own set. A
                // "Rerolled Sparks" header - or an UNREADABLE one, which could itself be
                // Rerolled - claims the state so the handler blocks: the generic Confirm must
                // never commit a rerolled set the bot never chose.
                if (pill != SparkConfirmationPill.ORIGINAL) {
                    return LaunchScreenState.SPARK_SELECTION_CONFIRMATION
                }
            }
            if (sparkIntroStructurePresent(sparkSampler)) {
                if (sparkDrivable) {
                    return LaunchScreenState.SPARK_SELECTION_INTRO
                }
                val title = readSparkOcrRegion(bitmap, SPARK_INTRO_TITLE_OCR_REGION, "spark_intro_title")
                if (SparkTextNorm.isSparkSelectionTitle(title)) {
                    return LaunchScreenState.SPARK_SELECTION_INTRO
                }
            }
            if (sparkPagerStructurePresent(sparkSampler)) {
                // The pager's structure (both chevrons + exactly one lit page dot + a spark
                // list + the wide Confirm) exists nowhere else, so it claims the state even
                // without a transaction - the handler then blocks instead of letting the
                // generic chain confirm whatever page is showing.
                return LaunchScreenState.SPARK_SELECTION_PAGER
            }
            if (sparkRerolledStructurePresent(sparkSampler)) {
                if (sparkTransaction?.state == SparkTxState.SPEND_CONFIRMED) {
                    return LaunchScreenState.SPARKS_REROLLED_RESULT
                }
                val title = readSparkOcrRegion(bitmap, SPARK_REROLLED_TITLE_OCR_REGION, "spark_rerolled_title")
                if (SparkTextNorm.isSparksRerolledTitle(title)) {
                    return LaunchScreenState.SPARKS_REROLLED_RESULT
                }
            }
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
        if (ButtonStartCareer.check(iu, sourceBitmap = bitmap) ||
            ButtonStartCareerOffset.check(iu, sourceBitmap = bitmap) ||
            ButtonStartCareerRight.check(iu, sourceBitmap = bitmap)
        ) {
            return LaunchScreenState.PRE_RUN_CONFIRMATION
        }

        // Opening cinematic - the >> fast-forward / Skip button. Checked BEFORE the in-career Skip
        // pill below so the opening movie is fast-forwarded by its own dedicated button rather than
        // body-tapped as a tap-to-continue screen (its skip_cinematic/skip templates are distinct
        // from the skip_off/skip_on pill).
        if (ButtonSkipCinematic.check(iu, sourceBitmap = bitmap) || ButtonSkip.check(iu, sourceBitmap = bitmap)) {
            return LaunchScreenState.CINEMATIC_INTRO
        }

        // The Grand Concert concert-pending screen also shows the Skip pill and previously fell
        // through to TAP_TO_CONTINUE, body-tapping until the launch failed (observed 2026-07-24 on
        // the 4th Concert: 30 taps then TASK_RESULT_QUEUE_NAVIGATION_FAILED). It is a drivable
        // in-career state: navigation is complete there and the campaign's concert escort owns it.
        if (grandConcertConcertPendingScreenPresent(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) })) {
            MessageLog.i(TAG, "[NAV] Grand Concert concert-pending screen -> ACTIVE_TRAINING_MENU (the campaign's concert escort owns it).")
            return LaunchScreenState.ACTIVE_TRAINING_MENU
        }

        // Bottom-left Skip pill (Skip Off / Skip > / Skip >>): template match, then an OCR fallback
        // for "SKIP" in the pill band. This pill is shared by the career-launch Quick Mode prompt AND
        // every in-career "tap to continue" screen (scenario cutscenes, goal/race intros). The launch
        // prompt is ALWAYS the FIRST skip-pill screen of a launch (it precedes any cutscene), so
        // skipToggleAlreadyDone separates them safely: not-yet-maxed -> the real prompt
        // (QUICK_MODE_PROMPT, which maxes skip + confirms); already-maxed -> an in-career screen
        // (TAP_TO_CONTINUE, body-tapped to advance). This avoids a fragile UI discriminator: the
        // prompt is not a registered titled dialog (no DialogUtils gradient) and reaches this block
        // precisely because POST_RUN_RESULTS above found no matchable Confirm on it.
        var hasSkipPill = ButtonSkipOff.check(iu, sourceBitmap = bitmap) || ButtonSkipOn.check(iu, sourceBitmap = bitmap)
        if (!hasSkipPill) {
            try {
                // Scan 22%-53% width, 94%-98% height - centered on the Skip pill button.
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
                if (skipOcr.uppercase().contains("SKIP")) hasSkipPill = true
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
            }
        }
        if (hasSkipPill) {
            if (!skipToggleAlreadyDone) {
                return LaunchScreenState.QUICK_MODE_PROMPT
            }
            MessageLog.i(TAG, "[NAV] Skip pill with skip already maxed -> TAP_TO_CONTINUE (in-career tap-to-continue screen).")
            return LaunchScreenState.TAP_TO_CONTINUE
        }

        // The Grand Concert Complete Career screen also shows a Complete Career button, but on a
        // run START it still holds unspent value (the Lessons drain) that the campaign must run
        // first, so it routes to the campaign instead of the summary flow (observed 2026-07-24:
        // classifying it as CAREER_SUMMARY ended the run before the drain hook ever got a tick).
        // During the finalize-to-home pass the campaign has already drained and approved Finish,
        // and this screen must fall through to CAREER_SUMMARY so the finalize actually presses
        // Complete Career (the first finalize run declared "navigation complete" here and left
        // the career unfinished).
        if (!finalizeToHomeMode && grandConcertCareerCompleteScreenPresent(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) })) {
            MessageLog.i(TAG, "[NAV] Grand Concert Complete Career screen -> ACTIVE_TRAINING_MENU (the campaign's Lessons drain owns it).")
            return LaunchScreenState.ACTIVE_TRAINING_MENU
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

        // Career has started (we are past Start Career) and an unrecognised game DIALOG is up, after
        // every specific launch dialog (TP restore, etc.) has already been ruled out above. This is the
        // in-career "Umamusume Details" card the game shows on turn 1 - NOT a launch screen. Treat it as
        // the success/exit state and hand off: the campaign's process loop reads the card via its
        // umamusume_details handler (real in-career name + verifyRotationTrainee) and closes it. The
        // DialogUtils gradient-banner match is OCR-free, so it cannot repeat the header-OCR false read.
        if (careerLaunchInitiated && DialogUtils.check(iu, sourceBitmap = bitmap)) {
            MessageLog.i(TAG, "[NAV] Post-Start-Career dialog detected (the career has started); handing off to the campaign to read and close it.")
            return LaunchScreenState.ACTIVE_TRAINING_MENU
        }

        // The "Follow Trainer" prompt (Auto-Fill borrowed a support card from a trainer you hadn't used
        // before) can pop anywhere in the post-run / launch flow with no rule to its timing, and it
        // matches none of the discriminators above. Before careerLaunchInitiated is set it would otherwise
        // fall straight to UNKNOWN and, after MAX_CONSECUTIVE_UNKNOWNS, stall the queue. It is a pure
        // nuisance dialog with no navigational meaning: dismiss it with Cancel (close() clicks the first
        // button, ButtonCancel) and re-detect — the next detection lands on the real underlying screen.
        // (When careerLaunchInitiated is true the block above already hands it to the campaign's
        // DialogHandler, which Cancels it the same way.)
        if (DialogUtils.check(iu, sourceBitmap = bitmap)) {
            val dialogTitle =
                try {
                    DialogUtils.getTitle(iu, bitmap)
                } catch (e: InterruptedException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            if (dialogTitle == DialogFollowTrainer.title) {
                MessageLog.i(TAG, "[NAV] Follow Trainer prompt detected; tapping Cancel to dismiss and continue.")
                DialogFollowTrainer.close(iu)
                return LaunchScreenState.UNKNOWN
            }
        }

        // Reskin-resilient home fallback: every home anchor above is lobby chrome (ButtonCareerHome
        // at the top of the chain, the nav-bar last resort), and event skins restyle chrome - the
        // July 2026 patch already pushed Auto-Select below its match threshold, and the 2026-07-14
        // anniversary reskins the home screen outright. Deep probes only, so normal navigation is
        // byte-identical; these looser detectors (0.55-confidence text crop, then OCR) are safe here
        // precisely because every known screen was already ruled out above.
        if (deepHomeProbe) {
            if (ButtonCareerHomeText.check(iu, sourceBitmap = bitmap)) {
                MessageLog.i(TAG, "[NAV] Deep home probe: CAREER text-crop matched after every other screen check missed -> HOME_SCREEN.")
                return LaunchScreenState.HOME_SCREEN
            }
            if (ocrCareerLabelAtHomePosition(bitmap) != null) {
                MessageLog.i(TAG, "[NAV] Deep home probe: OCR found the CAREER/Event label at the button position after every other screen check missed -> HOME_SCREEN.")
                return LaunchScreenState.HOME_SCREEN
            }
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
            LaunchScreenState.SPARKS_SCREEN -> handleSparksScreen()
            LaunchScreenState.CONFIRM_REROLL_DIALOG -> handleConfirmRerollDialog()
            LaunchScreenState.SPARKS_REROLLED_RESULT -> handleSparksRerolledResult()
            LaunchScreenState.SPARK_SELECTION_INTRO -> handleSparkSelectionIntro()
            LaunchScreenState.SPARK_SELECTION_PAGER -> handleSparkSelectionPager()
            LaunchScreenState.SPARK_SELECTION_CONFIRMATION -> handleSparkSelectionConfirmation()
            LaunchScreenState.SPARKS_KEEP_CONFIRMATION -> handleSparksKeepConfirmation()
            LaunchScreenState.POST_RUN_RESULTS -> handlePostRunResults()
            LaunchScreenState.VETERAN_UMAMUSUME_MAX -> handleVeteranUmamusumeMax()
            LaunchScreenState.CAREER_COMPLETE_DIALOG -> handleCareerCompleteDialog()
            LaunchScreenState.TRAINEE_SELECT_SCREEN -> handleTraineeSelectScreen()
            LaunchScreenState.LEGACY_SELECT_SCREEN -> handleLegacySelectScreen()
            LaunchScreenState.PRE_RUN_CONFIRMATION -> handlePreRunConfirmation()
            LaunchScreenState.TP_RESTORE_DIALOG -> handleTpRestoreDialog()
            LaunchScreenState.RECOVER_TP_QUANTITY -> handleRecoverTpQuantity()
            LaunchScreenState.SUPPORT_DECK_SCREEN -> handleSupportDeckScreen(reuseLastLaunchSetup, autoFillSupports)
            LaunchScreenState.CINEMATIC_INTRO -> handleCinematicIntro()
            LaunchScreenState.HOME_SCREEN ->
                if (finalizeToHomeMode) {
                    // Final-run finalize: reaching the home lobby means the career-end flow
                    // (summary, results, sparks/reroll, dialogs) is done. Stop here instead of
                    // starting another career.
                    MessageLog.i(TAG, "[NAV] Home screen reached - career-end flow finished. Stopping here (finalize mode).")
                    TransitionResult.Success
                } else {
                    handleHomeScreen()
                }
            LaunchScreenState.QUICK_MODE_PROMPT -> handleQuickModePrompt()
            LaunchScreenState.TAP_TO_CONTINUE -> handleTapToContinue()

            // --- States that require templates not yet provided ---

            LaunchScreenState.CAREER_ENTRY ->
                TransitionResult.Failed(
                    reason = "Reached CAREER_ENTRY state but no template exists for the Career mode button. Cannot navigate further.",
                    transition = "CAREER_ENTRY -> SCENARIO_SELECT",
                    isRecoverable = true,
                    recommendedAction = "Provide the 'career_home' template image, or manually enter Career mode and restart the queue.",
                )

            LaunchScreenState.SCENARIO_SELECT -> handleScenarioSelect()

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
        // Finalization guard: a BLOCK verdict from the career task means the remaining
        // skill-point balance could not be explained (an affordable compatible skill remained,
        // or the evidence was incomplete) after the careerComplete plan and its one retry.
        // Completing the career would discard the points, so fail the transition here instead
        // of walking into the Finish dialog. The career stays exactly as it is for the operator.
        finalizeBlockFailure("CAREER_SUMMARY -> COMPLETE_CAREER_CONFIRMATION")?.let { return it }

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

    /** The verdict allowed to govern this navigation's finalization, or null. Uses the token
     * captured at navigate() start plus the freshness bound, so a verdict from a previous
     * career, a previous queue run, or a later arming can never act here. */
    private fun usableFinalizeVerdict(): FinalizeVerdict? =
        CareerFinalizeGate.verdict?.takeIf { finalizeVerdictUsable(it, expectedFinalizeToken, System.currentTimeMillis()) }

    /**
     * The terminal transition failure that stops the completion flow, or null when advancing is
     * allowed. Shared by every handler that could otherwise advance toward Finish. Two ways to
     * fail: a usable BLOCK verdict (the career-side evidence says the balance is not
     * finishable), or - guard active with NO usable verdict - a missing/stale verification
     * (process restart, a verdict from a different career, an expired verdict). Never click
     * Complete Career or Finish on faith in Adaptive mode; Manual mode never fails here.
     */
    private fun finalizeBlockFailure(transition: String): TransitionResult.Failed? {
        val verdict = usableFinalizeVerdict()
        if (verdict != null && verdict.approved) return null
        if (verdict == null && !finalizeGuardActive) return null
        val reason =
            verdict?.reason
                ?: (
                    "UNSPENT_SKILL_POINTS: no usable finalization verification exists for this career " +
                        "(missing, stale, or from a different career). Not pressing Finish."
                )
        MessageLog.e(TAG, "[NAV] [FINALIZE] $reason")
        return TransitionResult.Failed(
            reason = reason,
            transition = transition,
            recommendedAction = "Open the career and spend the remaining skill points (or press Finish yourself if the balance is intentional), then restart the queue.",
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
        // Finalization guard, second gate: never press Finish on a BLOCK verdict or without a
        // usable verdict in Adaptive mode, even when the flow arrived here without passing the
        // summary handler (a POST_RUN_RESULTS misclassification clicks Complete Career too).
        finalizeBlockFailure("COMPLETE_CAREER_CONFIRMATION -> POST_RUN_RESULTS")?.let { return it }

        // Approved verdict: cross-check the dialog's own "Remaining Skill Points" line against
        // the career-side verified balance. The popup is a consistency check, never the sole
        // source of truth: it blocks only when TWO readable fresh captures both contradict the
        // verified balance (the same two-read rule the skill-point trigger uses against OCR
        // ghosts). An unreadable line proceeds - the approved verdict already proves the
        // career-side evidence was complete, and this OCR region has not been through a live
        // career yet, so it corroborates but never overrules silently.
        usableFinalizeVerdict()?.let { verdict ->
            val firstRead = readCompleteCareerRemainingSp()
            if (firstRead == null) {
                MessageLog.w(
                    TAG,
                    "[NAV] [FINALIZE] Could not read the Remaining Skill Points line on the Complete Career dialog. Proceeding on the verified balance of ${verdict.verifiedRemainingSp}.",
                )
            } else if (firstRead != verdict.verifiedRemainingSp) {
                MessageLog.w(TAG, "[NAV] [FINALIZE] Dialog reports $firstRead remaining skill points but the verification says ${verdict.verifiedRemainingSp}. Re-reading once...")
                val secondRead = readCompleteCareerRemainingSp()
                if (popupContradictsVerifiedBalance(verdict.verifiedRemainingSp, firstRead, secondRead)) {
                    val reason =
                        "UNSPENT_SKILL_POINTS: the Complete Career dialog reports $firstRead/$secondRead remaining skill points " +
                            "but the career-end verification recorded ${verdict.verifiedRemainingSp}. The verification is stale. Not pressing Finish."
                    MessageLog.e(TAG, "[NAV] [FINALIZE] $reason")
                    return TransitionResult.Failed(
                        reason = reason,
                        transition = "COMPLETE_CAREER_CONFIRMATION -> POST_RUN_RESULTS",
                        recommendedAction = "Open the career and spend the remaining skill points (or press Finish yourself if the balance is intentional), then restart the queue.",
                    )
                }
            }
        }

        MessageLog.i(TAG, "[NAV] Complete Career confirmation dialog detected. Clicking 'Finish'...")

        if (ButtonFinish.click(iu)) {
            // The finalization verdict is spent the moment Finish is pressed - it must never
            // leak into the next career's completion flow.
            CareerFinalizeGate.clear()
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
     * OCR the "Remaining Skill Points: NNN pts" line off the Complete Career confirmation
     * dialog, or null when the line cannot be located or parsed. The band is anchored to the
     * matched Finish button: the game renders the dialog at fixed size on the 1080-wide game
     * surface, with the skill-point line in the text block directly above the button row.
     */
    private fun readCompleteCareerRemainingSp(): Int? {
        val (finishLocation, _) = ButtonFinish.find(iu)
        if (finishLocation == null) return null
        val bitmap = iu.getSourceBitmap()
        val text =
            try {
                iu.performOCROnRegion(
                    bitmap,
                    (bitmap.width * 0.08).toInt(),
                    (finishLocation.y.toInt() - 330).coerceAtLeast(0),
                    (bitmap.width * 0.84).toInt(),
                    260,
                    useThreshold = false,
                    useGrayscale = true,
                    scale = 1.5,
                    debugName = "nav_complete_career_remaining_sp",
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                return null
            }
        return parseRemainingSkillPoints(text)
    }

    /**
     * POST_RUN_RESULTS: Clicks the first matching advancement button.
     *
     * Detection: template-matched (Next, OK, Confirm, Close, CompleteCareer).
     * Transition: template-matched button click.
     */

    /** One-shot flag: the reroll flow ran (spent or declined at the dialog) this handoff. The
     * second SPARKS_SCREEN entry must Confirm, never reroll again. */
    private var sparksRerollAttempted = false

    /** One-shot flag: the original spark set was already read and recorded this handoff, so a
     * missed Confirm click re-entering the screen cannot append a duplicate corpus record. */
    private var sparksSetRecorded = false

    /**
     * Handles the career-end SPARKS screen.
     *
     * Default (setting off): click Confirm and keep the generated set - the pre-reroll behavior.
     * With the opt-in `runQueue.enableSparkReroll`, the COMPLETE original set is read first
     * (scrolling when the list runs past the 9 visible rows - the old 6-row cap under-counted
     * 3-star whites and biased the gate toward spending), then the redraw is priced by
     * [SparkRerollPolicy] from the verified band odds: a 2/3-star blue is always kept; a 1-star
     * blue rerolls when the expected fresh blue (uniform stat pick over the five final stats)
     * beats the pink/unique/3-star-white holdings a redraw would forfeit. An incomplete read
     * never authorizes the spend. With TP < 30 the game swaps the spend dialog for a Restore-TP
     * prompt; when item restore is enabled the bot restores (same ladder and session cap as the
     * career-start restore) and retries the spend once, because a confirmed set can never be
     * rerolled.
     *
     * After a successful spend the game moves through Sparks Rerolled, the Spark Selection
     * intro, the two-page pager, and the Confirmation dialog - all dedicated states with their
     * own handlers driving [SparkRerollGate]'s transaction; this screen never reappears
     * post-spend (live-proven; an earlier "post-reroll re-entry" branch here was dead code).
     */
    private fun handleSparksScreen(): TransitionResult {
        val bitmap = iu.getSourceBitmap()
        val enableReroll = SettingsHelper.getBooleanSetting("runQueue", "enableSparkReroll", false)
        val transaction = SparkRerollGate.transaction

        if (sparksRerollAttempted) {
            // The spend was declined earlier (or its dialog never opened) - the original set is
            // still up; Confirm it.
            MessageLog.i(TAG, "[REROLL] The spend was declined earlier - the original set is still up. Confirming it.")
            return confirmSparks(bitmap)
        }

        // Read and record the career's rolled spark set exactly once, independent of the reroll
        // setting - this is ledger enrichment (which sparks each career produced), not reroll
        // logic. The read is the COMPLETE scrolled set whenever a career transaction exists,
        // whether or not the reroll is enabled: the spend decision and the chooser both need
        // every row, and the old visible-window read silently truncated longer sets in the
        // ledger too (a live 11-spark career recorded only its first 9). A set that fits the
        // window terminates on its own end marker and costs no extra swipe.
        var originalReading: SparkSetReading? = transaction?.originalRead
        if (!sparksSetRecorded) {
            sparksSetRecorded = true
            if (transaction != null) {
                originalReading = readCompleteSparkSet(SPARKS_SCREEN_GEOMETRY, "original")
                recordSparkRows(originalReading.rows, "original", originalReading)
                val captured = transaction.captureOriginal(originalReading, StartModule.lastCareerEndTrainee, StartModule.lastCareerEndScenario)
                if (!captured.ok) {
                    MessageLog.w(TAG, "[SPARKS] ${captured.reason}")
                }
            } else {
                recordSparkSet(bitmap, "original")
            }
        }

        if (!enableReroll) {
            return confirmSparks(bitmap)
        }

        // Price the redraw with the verified band odds instead of the old ">= 1100 core stat"
        // rule, which predates the corrected spark model and could never pass on URA farm
        // careers. Star counts come from the row color samples, so no OCR is involved; the
        // final stats come from the ledger-time snapshot (the Campaign instance is gone by
        // the time the navigator runs).
        //
        // Every prerequisite is reported independently: an earlier live decline logged
        // "spark rows: unexpected layout, scan: missing" when in truth the transaction was
        // gone and neither the scan nor the layout had ever been looked at.
        val finalStats = StartModule.lastCareerEndStats
        val rows = originalReading?.rows ?: emptyList()
        val diagnostics =
            SparkSpendDiagnostics(
                transactionPresent = transaction != null,
                statsSnapshotSize = finalStats?.size,
                scanTermination = originalReading?.termination,
                readComplete = originalReading?.complete == true,
                rowCount = rows.size,
                leadsCorrectly = rows.size >= 3 && rows[0].kind == SparkRowKind.STAT && rows[1].kind == SparkRowKind.APTITUDE && rows[2].kind == SparkRowKind.UNIQUE,
            )
        if (!diagnostics.spendAllowed) {
            MessageLog.w(TAG, "[REROLL] Not pricing the redraw: ${diagnostics.format()}. Keeping the original sparks.")
            transaction?.declineSpend("not priced (${diagnostics.blocker.wire})")
            return confirmSparks(bitmap)
        }
        // spendAllowed proves both of these are present; the compiler cannot see through the
        // diagnostics data class, so they are re-bound explicitly rather than re-tested.
        val pricedStats = finalStats!!
        val liveTransaction = transaction!!
        val verdict =
            SparkRerollPolicy.decide(
                blueStars = rows[0].stars,
                pinkStars = rows[1].stars,
                uniqueStars = rows[2].stars,
                visibleWhiteThreeStars = rows.drop(3).count { it.kind == SparkRowKind.WHITE && it.stars >= 3 },
                finalStats = pricedStats.values,
            )
        MessageLog.i(TAG, "[REROLL] EV gate: ${verdict.reason}")
        if (!verdict.reroll) {
            liveTransaction.declineSpend(verdict.reason)
            return confirmSparks(bitmap)
        }
        val approved = liveTransaction.approveSpend(verdict.reason)
        if (!approved.ok) {
            // A spend was already confirmed on this career (or the transaction is out of
            // order): never spend twice. Keep whatever is up.
            MessageLog.w(TAG, "[REROLL] ${approved.reason}. Keeping the original set.")
            return confirmSparks(bitmap)
        }

        if (!ButtonRerollSparks.click(iu, sourceBitmap = bitmap)) {
            MessageLog.w(TAG, "[REROLL] Failed to click Reroll Sparks. Keeping the original set.")
            liveTransaction.declineSpend("Reroll Sparks click failed")
            return confirmSparks(bitmap)
        }
        waitSafe(1.5)
        // The dialog's GREEN button is the SPEND action - the one career-end screen where green
        // is not a safe advance. This click is the deliberate 30 TP spend. tries=3 rides out a
        // slow dialog-open animation with fresh captures per attempt.
        if (!ButtonRerollSparksConfirm.click(iu, tries = 3)) {
            // Capture whatever is actually on screen: the spend button's template has never
            // matched a live TP-sufficient confirm dialog (three priced-positive rerolls lost
            // 2026-07-12), and these pixels are the recapture material for fixing that.
            runCatching {
                iu.saveBitmap(filename = "reroll_confirm_fail_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}", fullRes = true)
            }
            // With TP < 30 the game swaps the spend dialog for "You need N more TP to reroll
            // Sparks. Restore TP?" (seen live 2026-07-10). Restore and retry once when item
            // restore is enabled: the next career start draws on the same items anyway, and a
            // confirmed set can never be rerolled, so declining here saves nothing.
            if (tryRestoreTpForReroll() && retryRerollSpend()) {
                MessageLog.i(TAG, "[REROLL] Spent 30 TP to reroll sparks after restoring TP (${verdict.reason}).")
                liveTransaction.confirmSpend(System.currentTimeMillis(), restoreSource = "item_restore")
                sparksRerollAttempted = true
                waitSafe(4.0)
                captureRerollChoiceScreen()
                return TransitionResult.Continue
            }
            MessageLog.w(TAG, "[REROLL] The spend was not available (dialog never opened, or TP is short with item restore off/exhausted). Keeping the original set.")
            ButtonCancel.click(iu)
            liveTransaction.declineSpend("spend unavailable (no dialog, or TP short with restore off/exhausted)")
            sparksRerollAttempted = true
            waitSafe(1.0)
            return TransitionResult.Continue
        }
        MessageLog.i(TAG, "[REROLL] Spent 30 TP to reroll sparks (${verdict.reason}).")
        liveTransaction.confirmSpend(System.currentTimeMillis())
        sparksRerollAttempted = true
        waitSafe(4.0)
        captureRerollChoiceScreen()
        return TransitionResult.Continue
    }

    /**
     * Photographs whatever the game shows right after a successful reroll spend, as forensic
     * material alongside the dedicated selection-screen handling.
     */
    private fun captureRerollChoiceScreen() {
        runCatching {
            iu.saveBitmap(filename = "reroll_choice_screen_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}", fullRes = true)
            MessageLog.i(TAG, "[REROLL] Post-spend screen captured (reroll_choice_screen_*.png).")
        }
    }

    /**
     * CONFIRM_REROLL_DIALOG: the "Spend 30 TP to reroll Sparks?" dialog, reached through
     * detection only when this career's transaction holds an approved spend whose inline click
     * path did not resolve (the state is transaction-gated). Completes the approved spend; in
     * any other situation the dialog is cancelled - its green button is the spend and is never
     * pressed without an approved verdict.
     */
    private fun handleConfirmRerollDialog(): TransitionResult {
        val transaction = SparkRerollGate.transaction
        if (transaction?.state == SparkTxState.SPEND_APPROVED) {
            if (ButtonRerollSparksConfirm.click(iu, tries = 2)) {
                MessageLog.i(TAG, "[REROLL] Spent 30 TP to reroll sparks from the recovered spend dialog (${transaction.spendReason}).")
                transaction.confirmSpend(System.currentTimeMillis())
                sparksRerollAttempted = true
                waitSafe(4.0)
                captureRerollChoiceScreen()
            } else {
                MessageLog.w(TAG, "[REROLL] The spend dialog is up but the spend click failed. Cancelling and keeping the original set.")
                ButtonCancel.click(iu)
                transaction.declineSpend("spend dialog present but the spend click failed")
                sparksRerollAttempted = true
                waitSafe(1.0)
            }
            return TransitionResult.Continue
        }
        // No approved spend: never spend. Cancel out and let detection continue.
        MessageLog.w(TAG, "[REROLL] Spend dialog detected without an approved spend - cancelling it.")
        ButtonCancel.click(iu)
        waitSafe(1.0)
        return TransitionResult.Continue
    }

    /** OCR one fixed spark-screen text region ([x, y, w, h]); null on failure. */
    private fun readSparkOcrRegion(bitmap: Bitmap, region: IntArray, debugName: String): String? =
        try {
            iu.performOCROnRegion(
                bitmap,
                region[0],
                region[1],
                region[2],
                region[3],
                useThreshold = false,
                useGrayscale = true,
                ocrEngine = "mlkit",
                debugName = debugName,
            ).trim().ifEmpty { null }
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /** The terminal failure for a selection screen that cannot be driven safely. The career is
     * left exactly as it is: the game's own fallback keeps the original set, so a blocked
     * selection loses nothing except the operator's attention. */
    private fun sparkSelectionBlocked(transition: String, transaction: SparkRerollTransaction?, why: String): TransitionResult.Failed {
        transaction?.block(why)
        MessageLog.e(TAG, "[SPARKS] [CHOOSER] $why")
        return TransitionResult.Failed(
            reason = "SPARK_SELECTION blocked: $why",
            transition = transition,
            recommendedAction = "Finish the Spark Selection by hand (keeping the original set is always safe), then restart the queue.",
        )
    }

    /**
     * SPARKS_REROLLED_RESULT: the "Sparks Rerolled" screen after a successful spend. Reads and
     * records the COMPLETE rerolled set (the chooser's second input), then presses Next. An
     * incomplete read is captured as-is: the keep policy treats it as uncertainty and the
     * chooser then only accepts the verified keep-original path.
     */
    private fun handleSparksRerolledResult(): TransitionResult {
        val transaction = SparkRerollGate.transaction
        if (!sparkSelectionDrivable(transaction, System.currentTimeMillis())) {
            return sparkSelectionBlocked(
                "SPARKS_REROLLED_RESULT -> SPARK_SELECTION_INTRO",
                transaction,
                "the Sparks Rerolled screen is up without a live reroll transaction (process restart or stale career); not advancing a selection the bot cannot verify",
            )
        }
        transaction!!
        if (transaction.rerolledRead == null) {
            runCatching {
                iu.saveBitmap(filename = "reroll_result_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}", fullRes = true)
            }
            val reading = readCompleteSparkSet(SPARKS_SCREEN_GEOMETRY, "rerolled")
            recordSparkRows(reading.rows, "rerolled", reading)
            val captured = transaction.captureRerolled(reading)
            if (!captured.ok) {
                MessageLog.w(TAG, "[SPARKS] ${captured.reason}")
            }
        }
        val bitmap = iu.getSourceBitmap()
        if (!ButtonNext.click(iu, sourceBitmap = bitmap)) {
            MessageLog.w(TAG, "[SPARKS] Next not clickable on the Sparks Rerolled screen. Re-detecting...")
        }
        waitSafe(2.0)
        return TransitionResult.Continue
    }

    /**
     * SPARK_SELECTION_INTRO: the "Select which Sparks to keep." dialog. Marks the transaction
     * and advances; the pager that follows owns every real decision.
     */
    private fun handleSparkSelectionIntro(): TransitionResult {
        val transaction = SparkRerollGate.transaction
        if (!sparkSelectionDrivable(transaction, System.currentTimeMillis())) {
            return sparkSelectionBlocked(
                "SPARK_SELECTION_INTRO -> SPARK_SELECTION_PAGER",
                transaction,
                "the Spark Selection intro is up without a live reroll transaction (process restart or stale career); leaving the selection for the operator",
            )
        }
        transaction!!.introPassed()
        val bitmap = iu.getSourceBitmap()
        val advanced =
            ButtonNext.click(iu, sourceBitmap = bitmap) ||
                ButtonClose.click(iu, sourceBitmap = bitmap) ||
                ButtonOk.click(iu, sourceBitmap = bitmap)
        if (!advanced) {
            // The intro's single bottom button sits at a fixed position on the card.
            gestureUtils.tap(SPARK_INTRO_BUTTON_X.toDouble(), SPARK_INTRO_BUTTON_Y.toDouble(), "spark_intro_advance")
        }
        waitSafe(2.0)
        return TransitionResult.Continue
    }

    /** The pager page currently on screen, from BOTH signals (heading OCR + lit page dot), with
     * one fresh-capture retry. Null means unreadable or contradictory after the retry. */
    private fun resolveCurrentPagerSide(): SparkSetSide? {
        repeat(2) { attempt ->
            val bitmap = iu.getSourceBitmap()
            val heading = SparkTextNorm.headingSide(readSparkOcrRegion(bitmap, SPARK_PAGER_HEADING_OCR_REGION, "spark_pager_heading"))
            val dot = sparkPagerActiveDotIndex(sparkSampler(bitmap))
            when (val resolution = resolvePagerSide(heading, dot)) {
                is SparkPagerResolution.Resolved -> return resolution.side
                SparkPagerResolution.Contradictory ->
                    MessageLog.w(TAG, "[SPARKS] [CHOOSER] Pager heading ($heading) and page dot ($dot) disagree (attempt ${attempt + 1}).")
                SparkPagerResolution.Unreadable ->
                    MessageLog.w(TAG, "[SPARKS] [CHOOSER] Pager page unreadable: heading=$heading dot=$dot (attempt ${attempt + 1}).")
            }
            waitSafe(1.5)
        }
        return null
    }

    /** Outcome of one pager navigation attempt. [Unchanged] is the only retryable failure:
     * the screen is still provably on the starting page, so one more swipe is safe. Anything
     * the bot cannot read blocks immediately instead of dispatching more gestures at a
     * screen it cannot prove. */
    private sealed class PagerNavOutcome {
        object Verified : PagerNavOutcome()

        object Unchanged : PagerNavOutcome()

        data class Unverifiable(val reason: String) : PagerNavOutcome()
    }

    /**
     * Drives the pager from [current] to [target] with a verified central swipe.
     *
     * Edge-chevron taps are deliberately not dispatched here: the 2026-07-20 supervised run
     * aimed two taps at the right chevron's own measured pixels (990, 228) and the page never
     * moved, while every mid-screen tap in the same minute landed. The thin outline is a poor
     * dispatch target and the floating overlay bubble rides the same screen edge; the
     * Scenario Select carousel already pages with a central drag for the same reason (see
     * selectScenario). Success requires a FRESH post-settle capture whose heading OCR and lit
     * page dot BOTH name the target page -- the settle wait is never proof by itself.
     */
    private fun navigateToPagerPage(current: SparkSetSide, target: SparkSetSide, attempt: Int): PagerNavOutcome {
        if (current == target) return PagerNavOutcome.Verified
        val bitmap = iu.getSourceBitmap()
        val plan = SparkPagerNav.plan(current, target, attempt, bitmap.width, bitmap.height)
        if (plan.action == SparkPagerAction.NONE) return PagerNavOutcome.Verified
        MessageLog.i(
            TAG,
            "[SPARKS] Pager navigation: current=${current.name} target=${target.name} attempt=$attempt " +
                "action=${plan.action.name.lowercase()} start=(${plan.startX.toInt()}, ${plan.startY.toInt()}) " +
                "end=(${plan.endX.toInt()}, ${plan.endY.toInt()})",
        )
        gestureUtils.swipe(plan.startX, plan.startY, plan.endX, plan.endY, duration = plan.durationMs)
        waitSafe(1.5) // animation settle only; the fresh captures below are the proof
        var lastHeading = "unreadable"
        var lastDots = "unreadable"
        repeat(2) { read ->
            val shot = iu.getSourceBitmap()
            val heading = SparkTextNorm.headingSide(readSparkOcrRegion(shot, SPARK_PAGER_HEADING_OCR_REGION, "spark_pager_heading"))
            val dotIndex = sparkPagerActiveDotIndex(sparkSampler(shot))
            lastHeading = heading?.name ?: "unreadable"
            lastDots = sparkPagerDotSide(dotIndex)?.name ?: "unreadable"
            when (classifySparkPagerRepaint(heading, dotIndex, current, target)) {
                SparkPagerRepaint.VERIFIED -> {
                    MessageLog.i(TAG, "[SPARKS] Pager repaint verified: heading=$lastHeading dots=$lastDots")
                    return PagerNavOutcome.Verified
                }
                SparkPagerRepaint.UNCHANGED -> {
                    MessageLog.w(TAG, "[SPARKS] Pager navigation did not repaint after attempt $attempt: heading=$lastHeading dots=$lastDots")
                    return PagerNavOutcome.Unchanged
                }
                SparkPagerRepaint.HEADING_UNREADABLE,
                SparkPagerRepaint.DOTS_UNREADABLE,
                SparkPagerRepaint.CONTRADICTION,
                -> {
                    MessageLog.w(
                        TAG,
                        "[SPARKS] Pager page not provable after attempt $attempt (read ${read + 1}/2): heading=$lastHeading dots=$lastDots",
                    )
                    // One transient re-read: a capture taken mid-frame must not block alone.
                    if (read == 0) waitSafe(1.5)
                }
            }
        }
        return PagerNavOutcome.Unverifiable(
            "the pager page cannot be proven after the swipe (heading=$lastHeading, dots=$lastDots); never confirming an unverified page",
        )
    }

    /**
     * SPARK_SELECTION_PAGER: the two-page keep-original-vs-rerolled chooser.
     *
     * Never assumes the displayed page: every entry re-resolves the page from the heading OCR
     * AND the lit page dot, and a contradiction blocks. Reads BOTH pages in full on the pager
     * itself (the authoritative display), scores them with [SparkKeepPolicy], swipes to the
     * winner, and presses Confirm only on the verified winner page. The FSM loop drives the
     * multi-step flow: each entry does one step and continues, so a missed gesture re-resolves
     * instead of compounding.
     */
    private fun handleSparkSelectionPager(): TransitionResult {
        val transition = "SPARK_SELECTION_PAGER -> SPARK_SELECTION_CONFIRMATION"
        val transaction = SparkRerollGate.transaction
        if (!sparkSelectionDrivable(transaction, System.currentTimeMillis())) {
            return sparkSelectionBlocked(
                transition,
                transaction,
                "the Spark Selection pager is on screen without a live reroll transaction (process restart or stale career); never confirming a page blind",
            )
        }
        transaction!!
        transaction.introPassed()

        val side =
            resolveCurrentPagerSide()
                ?: return sparkSelectionBlocked(transition, transaction, "the pager page cannot be resolved (heading and page dot unreadable or contradictory)")

        // Read the page on screen once, in full.
        if (transaction.pagerRead(side) == null) {
            val reading = readCompleteSparkSet(SPARK_PAGER_GEOMETRY, "pager ${side.wire}")
            transaction.recordPagerRead(side, reading)
            crossCheckPagerRead(side, reading, transaction)
        }

        // Both pages must be read before any decision.
        val missing = SparkSetSide.entries.firstOrNull { transaction.pagerRead(it) == null }
        if (transaction.winner == null && missing != null) {
            val attempt = if (transaction.pagerNavRetryUsed) 2 else 1
            when (val nav = navigateToPagerPage(side, missing, attempt)) {
                PagerNavOutcome.Verified -> {
                    // Heading and dots both agreed we landed on this page. Remember it: the
                    // keep-original fallback below can only trust a partial read of a page whose
                    // identity was established independently of that read.
                    transaction.markPagerPageVerified(missing)
                    return TransitionResult.Continue
                }
                PagerNavOutcome.Unchanged -> {
                    if (transaction.usePagerNavRetry()) {
                        MessageLog.w(TAG, "[SPARKS] [CHOOSER] Retrying the pager swipe once.")
                        return TransitionResult.Continue
                    }
                    return sparkSelectionBlocked(
                        transition,
                        transaction,
                        "the pager swipe to the ${missing.wire} page did not repaint and no retry budget remains; never confirming an unverified page",
                    )
                }
                is PagerNavOutcome.Unverifiable -> return sparkSelectionBlocked(transition, transaction, nav.reason)
            }
        }

        if (transaction.winner == null) {
            transaction.setsVerified()
            val original = transaction.pagerRead(SparkSetSide.ORIGINAL)!!
            val rerolled = transaction.pagerRead(SparkSetSide.REROLLED)!!
            val choice = SparkKeepPolicy.choose(original, rerolled, buildSparkChooserProfile())
            if (!choice.certain && !original.complete) {
                // The pager read of the Original page came up short. That used to end the queue,
                // which is a harsh outcome for a choice that always has a safe answer: keeping the
                // original is never a loss, because it is the set the career already earned.
                //
                // Confirm it anyway when three independent things line up. The pre-pager scan of the
                // Original set was COMPLETE (it has to be, or the 30 TP would never have been spent),
                // so the set's content is already known. The pager's own heading and dots confirmed
                // which page is on screen, independently of any row read. And every row the short
                // pager read did capture agrees with the leading rows of that known set, so nothing
                // observed contradicts it. Known content, confirmed page, no contradiction.
                //
                // Anything less still blocks: an unverified page, a contradicted row, or no complete
                // pre-pager read. The comparison itself never runs on partial data and the rerolled
                // set is never confirmed this way, so this can only ever keep the original.
                val known = transaction.originalRead
                val corroborated =
                    known != null &&
                        known.complete &&
                        transaction.pagerPageVerified(SparkSetSide.ORIGINAL) &&
                        SparkScrollMerge.rowsAreConsistentPrefix(known.rows, original.rows)
                if (!corroborated) {
                    return sparkSelectionBlocked(
                        transition,
                        transaction,
                        "neither set could be read completely (original ${original.termination.name}, rerolled ${rerolled.termination.name}); no choice is safe",
                    )
                }
                MessageLog.w(
                    TAG,
                    "[SPARKS] [CHOOSER] The original pager page read short (${original.termination.name}, " +
                        "${original.rows.size} of ${known!!.rows.size} rows), but it agrees with the complete " +
                        "pre-pager capture on a verified ORIGINAL page. Keeping the original.",
                )
            }
            val selected = transaction.selectWinner(choice)
            if (!selected.ok) {
                return sparkSelectionBlocked(transition, transaction, selected.reason)
            }
            MessageLog.i(TAG, "[SPARKS] [CHOOSER] ${choice.reason}")
            MessageLog.i(
                TAG,
                "[SPARKS] [CHOOSER] Original: ${summarizeBreakdown(choice.original)} | Rerolled: ${summarizeBreakdown(choice.rerolled)}",
            )
        }

        val winner = transaction.winner!!
        if (side != winner) {
            val attempt = if (transaction.pagerNavRetryUsed) 2 else 1
            when (val nav = navigateToPagerPage(side, winner, attempt)) {
                PagerNavOutcome.Verified -> return TransitionResult.Continue
                PagerNavOutcome.Unchanged -> {
                    if (transaction.usePagerNavRetry()) {
                        MessageLog.w(TAG, "[SPARKS] [CHOOSER] Retrying the pager swipe to the winner once.")
                        return TransitionResult.Continue
                    }
                    return sparkSelectionBlocked(
                        transition,
                        transaction,
                        "the pager swipe to the winning ${winner.wire} page did not repaint and no retry budget remains; never confirming the wrong page",
                    )
                }
                is PagerNavOutcome.Unverifiable -> return sparkSelectionBlocked(transition, transaction, nav.reason)
            }
        }

        // On the verified winner page, at the top: commit. The Confirmation dialog that opens
        // re-verifies the chosen side by name before the final Confirm.
        MessageLog.i(TAG, "[SPARKS] [CHOOSER] Confirming the ${winner.wire} page.")
        val bitmap = iu.getSourceBitmap()
        if (!ButtonConfirm.click(iu, sourceBitmap = bitmap)) {
            gestureUtils.tap(SPARK_PAGER_CONFIRM_X.toDouble(), SPARK_PAGER_CONFIRM_Y.toDouble(), "spark_pager_confirm")
        }
        waitSafe(2.0)
        return TransitionResult.Continue
    }

    /** Log-only comparison of a pager page read against the earlier capture of the same set
     * (SPARKS screen for the original, the result screen for the rerolled). The pager is
     * authoritative for the choice; a disagreement is surfaced for the log, not acted on. */
    private fun crossCheckPagerRead(side: SparkSetSide, pagerReading: SparkSetReading, transaction: SparkRerollTransaction) {
        val earlier = if (side == SparkSetSide.ORIGINAL) transaction.originalRead else transaction.rerolledRead
        if (earlier == null || !earlier.complete || !pagerReading.complete) return
        if (!SparkScrollMerge.rowsAlign(earlier.rows, pagerReading.rows)) {
            MessageLog.w(
                TAG,
                "[SPARKS] [CHOOSER] The ${side.wire} pager page (${pagerReading.rows.size} rows) disagrees with its earlier capture (${earlier.rows.size} rows); trusting the pager.",
            )
        }
    }

    private fun summarizeBreakdown(b: SparkSideBreakdown): String =
        "blue ${b.rawBlueStars}* (target rank ${b.blueTargetRank}), pink ${b.rawPinkStars}* (${b.matchedPinkStars} matched), " +
            "unique ${b.uniqueStars}*, relevant whites ${b.relevantWhiteStars}*, total ${b.totalStars}* over ${b.rowCount} rows" +
            if (b.complete) "" else " (INCOMPLETE)"

    /**
     * SPARK_SELECTION_CONFIRMATION: the final "Keep this set of Sparks?" dialog whose green
     * header names the side being kept. The header must agree with the chosen winner and the
     * listed rows must not contradict the winner's read; only then are the kept-set and choice
     * records written and the final Confirm pressed. A mismatch or unreadable header cancels
     * back to the pager once, then blocks - never confirms.
     */
    private fun handleSparkSelectionConfirmation(): TransitionResult {
        val transition = "SPARK_SELECTION_CONFIRMATION -> POST_RUN_RESULTS"
        val transaction = SparkRerollGate.transaction
        // COMPLETE is admitted alongside the drivable states: a missed final Confirm leaves a
        // completed transaction with the dialog still up, and the re-entry below re-verifies
        // the header and re-clicks without re-recording anything.
        if (!sparkSelectionDrivable(transaction, System.currentTimeMillis()) && transaction?.state != SparkTxState.COMPLETE) {
            return sparkSelectionBlocked(
                transition,
                transaction,
                "the Spark Selection confirmation names the rerolled set but no live reroll transaction exists (process restart or stale career); never confirming it blind",
            )
        }
        transaction!!
        val winner =
            transaction.winner
                ?: return sparkSelectionBlocked(transition, transaction, "the confirmation dialog appeared before a winner was chosen; not confirming")

        val bitmap = iu.getSourceBitmap()
        val pillSide = SparkTextNorm.headingSide(readSparkOcrRegion(bitmap, SPARK_CONFIRMATION_SET_NAME_OCR_REGION, "spark_conf_set_name"))
        if (pillSide == null || pillSide != winner) {
            val problem =
                if (pillSide == null) {
                    "the confirmation header is unreadable"
                } else {
                    "the confirmation header names the ${pillSide.wire} set but the chooser selected the ${winner.wire} set"
                }
            if (transaction.useConfirmationRetry()) {
                MessageLog.w(TAG, "[SPARKS] [CHOOSER] $problem - cancelling back to the pager for one retry.")
                clickSparkConfirmationCancel(bitmap)
                waitSafe(2.0)
                return TransitionResult.Continue
            }
            clickSparkConfirmationCancel(bitmap)
            return sparkSelectionBlocked(transition, transaction, "$problem (retry already used)")
        }

        // The dialog lists the kept set; its visible rows must not contradict the winner's read
        // (kind or stars at any shared index). The dialog can clip a long set, so a shorter
        // dialog list is tolerated once the header matched.
        val winnerRead = transaction.pagerRead(winner) ?: if (winner == SparkSetSide.ORIGINAL) transaction.originalRead else transaction.rerolledRead
        val dialogRows = readSparkRows(bitmap, SPARKS_CONFIRM_GEOMETRY)
        val expectedRows = winnerRead?.rows ?: emptyList()
        val comparable = minOf(dialogRows.size, expectedRows.size)
        val contradiction =
            (0 until comparable).firstOrNull { i ->
                dialogRows[i].kind != expectedRows[i].kind || dialogRows[i].stars != expectedRows[i].stars
            }
        if (contradiction != null) {
            clickSparkConfirmationCancel(bitmap)
            return sparkSelectionBlocked(
                transition,
                transaction,
                "confirmation row ${contradiction + 1} (${dialogRows[contradiction].kind.wire}/${dialogRows[contradiction].stars}*) contradicts the chosen ${winner.wire} set (${expectedRows[contradiction].kind.wire}/${expectedRows[contradiction].stars}*)",
            )
        }

        if (transaction.state != SparkTxState.COMPLETE) {
            transaction.verifyFinalConfirmation()
            if (!transaction.keptRecorded) {
                // The winner's complete pager read IS the kept set; the dialog may clip.
                val keptRows = if (expectedRows.size >= dialogRows.size) expectedRows else dialogRows
                recordSparkRows(keptRows, "kept", winnerRead)
                transaction.markKeptRecorded()
            }
            if (!transaction.choiceRecorded) {
                appendSparkChoiceRecord(transaction, pillSide)
                transaction.markChoiceRecorded()
            }
        }
        MessageLog.i(TAG, "[SPARKS] [CHOOSER] Confirmation header verified (${pillSide.wire}); pressing the final Confirm.")
        if (!ButtonConfirm.click(iu, sourceBitmap = bitmap)) {
            gestureUtils.tap(SPARK_CONFIRMATION_CONFIRM_X.toDouble(), SPARK_CONFIRMATION_CONFIRM_Y.toDouble(), "spark_conf_confirm")
        }
        val completed = transaction.complete()
        if (!completed.ok) {
            MessageLog.w(TAG, "[SPARKS] ${completed.reason}")
        }
        waitSafe(2.5)
        return TransitionResult.Continue
    }

    /**
     * SPARKS_KEEP_CONFIRMATION: the ORDINARY "Keep this set of Sparks?" dialog, raised by
     * Confirm on the SPARKS screen when no reroll was performed. Its green pill reads a plain
     * "Sparks" - it names no side - so none of the post-reroll winner-header logic applies.
     *
     * Confirming here is lossless by construction: there is no second set to lose, only the
     * set the career actually rolled. The handler therefore auto-confirms once it can prove
     * the situation is what it looks like: a live transaction that never confirmed a 30 TP
     * spend, plus a complete read of the dialog's own list (which is the authoritative full
     * set and becomes the kept record). A plain pill after a confirmed spend is contradictory
     * and blocks; a missing transaction blocks rather than guessing.
     *
     * This screen was previously owned by the generic POST_RUN_RESULTS Confirm, and then - for
     * one build - misclassified as the post-reroll selection confirmation, which blocked a
     * completed no-spend career with a message claiming the header named the rerolled set
     * (2026-07-19). It names nothing of the sort; it says "Sparks".
     */
    private fun handleSparksKeepConfirmation(): TransitionResult {
        val transition = "SPARKS_KEEP_CONFIRMATION -> POST_RUN_RESULTS"
        val transaction = SparkRerollGate.transaction
        val bitmap = iu.getSourceBitmap()
        val pill = SparkTextNorm.confirmationPill(readSparkOcrRegion(bitmap, SPARK_CONFIRMATION_SET_NAME_OCR_REGION, "spark_keep_pill"))

        if (transaction == null) {
            return sparkSelectionBlocked(
                transition,
                null,
                "the keep confirmation (pill: ${pill.name.lowercase()}) is on screen with no live career transaction " +
                    "(process restart or a hand-played career); not confirming a set the bot cannot account for",
            )
        }
        if (transaction.spendEverConfirmed) {
            return sparkSelectionBlocked(
                transition,
                transaction,
                "this career confirmed a 30 TP reroll, so the selection dialog must name a side - but the pill reads " +
                    "\"${pill.name.lowercase()}\". Contradictory; not confirming",
            )
        }
        if (pill == SparkConfirmationPill.REROLLED) {
            return sparkSelectionBlocked(
                transition,
                transaction,
                "the pill names the rerolled set although this career never confirmed a spend. Contradictory; not confirming",
            )
        }

        // Read the dialog's own list in full: it is the complete kept set (the SPARKS screen
        // list can be longer than its visible window) and the record written from it.
        //
        // Verification is EVIDENCE-FUSED on this dialog and this dialog only: the plain pill
        // cannot switch sides and the original set was already read completely, so row names,
        // kinds, order, and count are the primary evidence and star counts corroborate. A
        // single frame's star read has a proven transient failure mode - the 2026-07-21 block
        // undercounted a filled third star sampled on its glyph edge - so star mismatches are
        // retried on fresh frames and only a reproduced, unambiguous contradiction blocks
        // ([keepDialogVerdict] carries the full rule; the selected-side confirmation keeps its
        // strict check).
        if (transaction.state != SparkTxState.COMPLETE && !transaction.keptRecorded) {
            val original = transaction.originalRead
            val maxStarRetries = 2
            var retries = 0
            var dialogReading: SparkSetReading
            while (true) {
                if (!BotService.isRunning || StartModule.queueStopRequested) {
                    return sparkSelectionBlocked(
                        transition,
                        transaction,
                        "a stop was requested during keep-dialog verification; not confirming",
                    )
                }
                val frame = iu.getSourceBitmap()
                val evidenceCells = parseSparkRowCellsWithEvidence(sparkSampler(frame), SPARKS_CONFIRM_GEOMETRY, frame.height)
                val named = nameSparkCells(frame, evidenceCells.map { it.toCell() }, SPARKS_CONFIRM_GEOMETRY)
                val endMarkerSeen = evidenceCells.size < SPARKS_CONFIRM_GEOMETRY.maxRows || named.size < evidenceCells.size
                val evidence = evidenceCells.take(named.size).map { SparkStarEvidence(it.filledCount, it.ambiguousCount) }
                MessageLog.i(
                    TAG,
                    "[SPARKS] keep_confirmation_scan rows=${named.size} endMarker=$endMarkerSeen " +
                        "ambiguousRows=${evidence.count { it.ambiguousSlots > 0 }} retry=$retries/$maxStarRetries",
                )
                dialogReading =
                    if (endMarkerSeen && named.isNotEmpty()) {
                        SparkSetReading(named, SparkScanTermination.COMPLETE_END_MARKER, 0)
                    } else {
                        // A set past the single-frame window (never observed; the window holds
                        // one slot past the largest live set) or an unparseable frame: the
                        // scrolling reader takes over. It carries no per-slot evidence, so the
                        // verdict below stays strict for it.
                        readCompleteSparkSet(SPARKS_CONFIRM_GEOMETRY, "keep confirmation")
                    }
                if (!dialogReading.complete || dialogReading.rows.isEmpty()) {
                    return sparkSelectionBlocked(
                        transition,
                        transaction,
                        "the keep dialog's set could not be read completely (${dialogReading.termination.name}, ${dialogReading.rows.size} rows); " +
                            "not confirming a set that cannot be recorded",
                    )
                }
                // No complete original read exists (partial SPARKS scan): nothing to fuse
                // against. The dialog read alone is the record, as before.
                if (original == null || !original.complete) break
                val verdict =
                    keepDialogVerdict(
                        original.rows,
                        dialogReading.rows,
                        if (endMarkerSeen) evidence else null,
                        retries,
                        maxStarRetries,
                    )
                when (verdict) {
                    is SparkKeepVerdict.Confirm -> break
                    is SparkKeepVerdict.ConfirmCorroborative -> {
                        MessageLog.w(
                            TAG,
                            "[SPARKS] keep_confirmation_star_ambiguous rows=${verdict.rows} decision=corroborative_confirm " +
                                "(names, kinds, order, and count all match the complete SPARKS read; the star check stayed " +
                                "ambiguous after $retries retries and is corroborative on this dialog)",
                        )
                        break
                    }
                    is SparkKeepVerdict.Retry -> {
                        val mismatches =
                            original.rows.indices.filter { original.rows[it].stars != dialogReading.rows[it].stars }
                        val detail =
                            mismatches.joinToString(" ") { i ->
                                val slots =
                                    evidenceCells.getOrNull(i)?.slots?.joinToString(",") { s -> "${s.read.name.first()}(${s.r},${s.g},${s.b})" }
                                "row=${i + 1} expected=${original.rows[i].stars} observed=${dialogReading.rows[i].stars} slots=[$slots]"
                            }
                        MessageLog.w(TAG, "[SPARKS] keep_confirmation_retry $detail retry=${retries + 1}/$maxStarRetries")
                        retries++
                        waitSafe(0.8)
                        continue
                    }
                    is SparkKeepVerdict.Block -> {
                        MessageLog.e(TAG, "[SPARKS] keep_confirmation_blocked reason=\"${verdict.reason}\"")
                        return sparkSelectionBlocked(transition, transaction, verdict.reason)
                    }
                }
            }
            recordSparkRows(dialogReading.rows, "kept", dialogReading)
            transaction.markKeptRecorded()
            sparksFullSetRecorded = true
            MessageLog.i(
                TAG,
                "[SPARKS] keep_confirmation_verified pill=${pill.name.lowercase()} rows=${dialogReading.rows.size} retriesUsed=$retries",
            )
            MessageLog.i(TAG, "[SPARKS] Keep confirmation verified (pill: ${pill.name.lowercase()}, ${dialogReading.rows.size} rows); confirming the rolled set.")
        }

        if (!ButtonConfirm.click(iu, sourceBitmap = bitmap)) {
            gestureUtils.tap(SPARK_CONFIRMATION_CONFIRM_X.toDouble(), SPARK_CONFIRMATION_CONFIRM_Y.toDouble(), "spark_keep_confirm")
        }
        // No reroll happened on this career: the transaction's work is done. declineSpend is
        // idempotent for an already-terminal transaction, so a re-entry cannot double-record.
        if (!transaction.terminal) {
            transaction.declineSpend("no reroll performed; kept the rolled set")
        }
        waitSafe(2.5)
        return TransitionResult.Continue
    }

    private fun clickSparkConfirmationCancel(bitmap: Bitmap) {
        if (!ButtonCancel.click(iu, sourceBitmap = bitmap)) {
            gestureUtils.tap(SPARK_CONFIRMATION_CANCEL_X.toDouble(), SPARK_CONFIRMATION_CANCEL_Y.toDouble(), "spark_conf_cancel")
        }
    }

    /** The keep policy's profile, from the same settings the skill machinery reads: ordered
     * blue targets, the Style-preference axes, the adaptive objective, and the enabled skill
     * plans' names. Missing values degrade to null/empty and the policy stays conservative. */
    private fun buildSparkChooserProfile(): SparkChooserProfile {
        val blueTargets = runCatching { SettingsHelper.getStringArraySetting("training", "focusOnSparkStatTarget") }.getOrDefault(emptyList())
        val style =
            SettingsHelper.getStringSetting("skills", "preferredRunningStyle").trim().ifEmpty {
                SettingsHelper.getStringSetting("racing", "originalRaceStrategy").trim()
            }
        val plannedNames = mutableListOf<String>()
        runCatching {
            val plansString = SettingsHelper.getStringSetting("skills", "plans")
            if (plansString.isNotEmpty()) {
                val plans = JSONObject(plansString)
                plans.keys().forEach { planName ->
                    val plan = plans.optJSONObject(planName) ?: return@forEach
                    if (!plan.optBoolean("enabled", false)) return@forEach
                    plan.optString("plan", "").split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { id ->
                        tempGame?.skillDatabase?.getSkillName(id)?.let { plannedNames.add(it) }
                    }
                }
            }
        }
        return SparkChooserProfile(
            traineeIdentity = StartModule.lastCareerEndTrainee,
            objective = SettingsHelper.getStringSetting("skills", "skillSpendObjective", "rank"),
            blueTargetsOrdered = blueTargets,
            preferredDistance = SettingsHelper.getStringSetting("skills", "preferredTrackDistance").trim().ifEmpty { null },
            preferredStyle = SparkTextNorm.canonicalStyleName(style),
            preferredSurface = SettingsHelper.getStringSetting("skills", "preferredTrackSurface").trim().ifEmpty { null },
            plannedSkillNames = plannedNames,
        )
    }

    /**
     * Appends the first-class type="spark_choice" record: exact career identity, transaction
     * id, both score breakdowns, the chosen side, the verified confirmation header, read
     * completeness, and the spend facts. The kept set itself is the adjacent type="sparks"
     * phase="kept" record - the choice record is the decision's audit trail.
     */
    private fun appendSparkChoiceRecord(transaction: SparkRerollTransaction, verifiedHeader: SparkSetSide) {
        runCatching {
            val choice = transaction.choice ?: return
            val record = JSONObject()
            record.put("type", "spark_choice")
            record.put("ts", System.currentTimeMillis())
            StartModule.lastCareerEndTrainee?.let { record.put("trainee", it) }
            StartModule.lastCareerEndScenario?.let { record.put("scenario", it) }
            StartModule.lastCareerEndFp?.let { record.put("fp", it) }
            transaction.careerToken?.let { record.put("career", it) }
            record.put("tx", transaction.careerNonce)
            transaction.queueRun?.let { record.put("run", it) }
            record.put("chosen", choice.side.wire)
            record.put("decided_by", choice.decidedBy)
            record.put("reason", choice.reason)
            record.put("certain", choice.certain)
            record.put("confirmed_header", verifiedHeader.wire)
            record.put("original", JSONObject(choice.original.toRecordMap()))
            record.put("rerolled", JSONObject(choice.rerolled.toRecordMap()))
            record.put("tp_spent", 30)
            transaction.tpRestoreSource?.let { record.put("tp_restore_source", it) }
            transaction.spendConfirmedAtMs?.let { record.put("spend_ts", it) }
            OutcomeCorpus.append(context, record)
        }.onFailure {
            MessageLog.w(TAG, "[SPARKS] Failed to append the spark_choice record: $it")
        }
    }

    /**
     * Handles the reroll's TP-short dialog by restoring TP with items. Runs only when the dialog
     * on screen really is the TP prompt (No button present and the body text mentions TP), item
     * restore is enabled, and the per-session restore cap has room. Returns true when TP was
     * restored and the sparks screen is ready for a second spend attempt.
     */
    private fun tryRestoreTpForReroll(): Boolean {
        if (!SettingsHelper.getBooleanSetting("runQueue", "enableTpRestoreWithItems", false)) {
            MessageLog.i(TAG, "[REROLL] The spend dialog is missing and item restore is disabled - not spending items without the opt-in.")
            return false
        }
        if (tpRestoresThisSession >= maxTpRestoresThisSession) {
            MessageLog.w(TAG, "[REROLL] TP restore session cap reached ($tpRestoresThisSession/$maxTpRestoresThisSession this session) - not restoring for the reroll.")
            return false
        }
        val bmp = iu.getSourceBitmap()
        val noLocation = ButtonNo.findImageWithBitmap(iu, bmp)
        if (noLocation == null) {
            // Neither the spend dialog nor the TP prompt matched - unknown screen, save it.
            runCatching {
                iu.saveBitmap(filename = "reroll_no_dialog_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}", fullRes = true)
            }
            return false
        }
        val body =
            try {
                iu.performOCROnRegion(
                    bmp,
                    (bmp.width * 0.10).toInt(),
                    (bmp.height * 0.35).toInt(),
                    (bmp.width * 0.80).toInt(),
                    (bmp.height * 0.25).toInt(),
                    useThreshold = false,
                    useGrayscale = true,
                    scale = 2.0,
                    debugName = "reroll_tp_dialog_ocr",
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                return false
            }
        if (!Regex("\\bTP\\b").containsMatchIn(body.uppercase())) return false
        MessageLog.i(TAG, "[REROLL] Out of TP for the reroll: \"${body.replace("\n", " ").take(70)}\". Restoring with items...")
        return driveTpRestorePicker(noLocation.x, noLocation.y) == TpRestoreOutcome.RESTORED
    }

    /** Re-clicks Reroll Sparks and the spend confirmation after a TP restore. One retry only. */
    private fun retryRerollSpend(): Boolean {
        waitSafe(1.5)
        if (!ButtonRerollSparks.click(iu)) {
            MessageLog.w(TAG, "[REROLL] Reroll Sparks not clickable after the TP restore.")
            return false
        }
        waitSafe(1.5)
        return ButtonRerollSparksConfirm.click(iu, tries = 3)
    }

    /** One-shot flag: the full spark set was read off the keep-set confirmation dialog. */
    private var sparksFullSetRecorded = false

    /** Clicks Confirm on the SPARKS screen; falls back to re-detection when the click misses.
     * The click raises the "Keep this set of Sparks?" confirmation, which lists EVERY spark on
     * one screen (the sparks list itself shows at most 9 unscrolled) - the kept set is recorded
     * in full from it before the generic dialog handling confirms it away. */
    private fun confirmSparks(bitmap: Bitmap): TransitionResult {
        if (!ButtonConfirm.click(iu, sourceBitmap = bitmap)) {
            MessageLog.w(TAG, "[NAV] Confirm not clickable on the SPARKS screen. Re-detecting...")
        }
        waitSafe(1.5)
        // The keep dialog this raises is owned by SPARKS_KEEP_CONFIRMATION, which reads the
        // complete set and writes the kept record. This legacy read stays only as a fallback
        // for a frame that state cannot claim (an unsupported capture size), and shares the
        // transaction's keptRecorded token so the two can never both append.
        val transaction = SparkRerollGate.transaction
        if (!sparksFullSetRecorded && transaction?.keptRecorded != true) {
            runCatching {
                val dialogBitmap = iu.getSourceBitmap()
                val rows = readSparkRows(dialogBitmap, SPARKS_CONFIRM_GEOMETRY)
                // Sanity gate: a real spark list always leads stat/aptitude/unique. Anything else
                // means the dialog is not up (missed click, layout drift) - skip silently and keep
                // the visible-window record as coverage.
                val leads = rows.size >= 3 && rows[0].kind == SparkRowKind.STAT && rows[1].kind == SparkRowKind.APTITUDE && rows[2].kind == SparkRowKind.UNIQUE
                if (leads && transaction == null) {
                    sparksFullSetRecorded = true
                    recordSparkRows(rows, "kept")
                }
            }
        }
        return TransitionResult.Continue
    }

    /** Wraps the capture for the pure pixel probes (kind classification, star counting, screen
     * structure) shared with the fixture tests. */
    private fun sparkSampler(bitmap: Bitmap): SparkPixelSampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }

    /**
     * Reads every visible spark row of a spark list: OCR'd name, gold-star count, and the row
     * kind from its bar color - blue = stat, pink = aptitude, green = unique, grey = white skill.
     * The pixel work (bar classification, star sampling, the pure-white end-of-grid break) lives
     * in the shared probe layer so the fixture tests pin it; the OCR name and the name-based
     * termination stay here. Best-effort: an unreadable name is recorded as such rather than
     * dropped, so the record stays honest about what was on screen.
     */
    private fun readSparkRows(bitmap: Bitmap, geometry: SparkListGeometry = SPARKS_SCREEN_GEOMETRY): List<SparkRowFact> {
        if (bitmap.width < 1000 || bitmap.height < 1000) return emptyList()
        return nameSparkCells(bitmap, parseSparkRowCells(sparkSampler(bitmap), geometry, bitmap.height), geometry)
    }

    /** OCR names onto parsed cells, applying the phantom-tail break. */
    private fun nameSparkCells(bitmap: Bitmap, cells: List<SparkRowCell>, geometry: SparkListGeometry): List<SparkRowFact> {
        val rows = mutableListOf<SparkRowFact>()
        for (cell in cells) {
            val name =
                iu.performOCROnRegion(
                    bitmap,
                    110,
                    cell.rowY - 42,
                    650,
                    84,
                    useThreshold = false,
                    useGrayscale = true,
                    ocrEngine = "mlkit",
                    debugName = "${geometry.debugPrefix}${cell.index}",
                ).trim()
            // Every real spark shows at least 1 gold star. A starless, textless slot is past the
            // end of the list: the keep-set dialog shrink-wraps to the set size and its body is
            // not the pure white the grid break expects, so the slots below the real set used to
            // be recorded as phantom "unreadable" 0-star skill rows (53 of 165 kept-phase rows in
            // the corpus, always contiguous at the tail).
            if (cell.stars == 0 && name.isEmpty()) break
            val resolvedName = name.ifEmpty { SPARK_UNREADABLE_NAME }
            rows.add(SparkRowFact(resolvedName, cell.stars, cell.kind, resolveSparkWhiteClass(cell.kind, resolvedName)))
        }
        return rows
    }

    /** Race-vs-skill refinement for a white row, from the packaged skill catalog: a known skill
     * name is a skill spark; anything else readable is a race spark; unreadable stays unknown
     * (uncertainty, handled conservatively by the keep policy). */
    private fun resolveSparkWhiteClass(kind: SparkRowKind, name: String): SparkWhiteClass? {
        if (kind != SparkRowKind.WHITE) return null
        if (name == SPARK_UNREADABLE_NAME || name.isBlank()) return SparkWhiteClass.UNKNOWN
        val known = runCatching { tempGame?.skillDatabase?.getSkillData(name) != null }.getOrDefault(false)
        return if (known) SparkWhiteClass.SKILL else SparkWhiteClass.RACE
    }

    /** One parsed frame of a scrolled spark list read. */
    private data class SparkFrame(val rows: List<SparkRowFact>, val endMarkerSeen: Boolean)

    private fun readSparkFrame(bitmap: Bitmap, geometry: SparkListGeometry): SparkFrame? {
        if (bitmap.width < 1000 || bitmap.height < 1000) return null
        // A swipe does not settle on pixel-exact row multiples, so scrolled frames re-anchor
        // the grid on the detected band offset before parsing; the unscrolled first frame
        // measures within 3 px of the fixed grid (fixture-pinned), so this is a no-op there.
        val cells = parseSparkRowCellsAligned(sparkSampler(bitmap), geometry, bitmap.height) ?: return null
        val rows = nameSparkCells(bitmap, cells, geometry)
        // The parse stops early on the pure-white grid break; the row read additionally stops on
        // a starless, textless slot. Either one is positive proof the visible window contains
        // the end of the list.
        val endMarkerSeen = cells.size < geometry.maxRows || rows.size < cells.size
        return SparkFrame(rows, endMarkerSeen)
    }

    /**
     * Reads a COMPLETE spark list, scrolling as needed and merging overlapping frames by
     * content ([SparkScrollMerge]). Completion requires positive proof: the end-of-list marker
     * inside a frame, or a scroll attempt that provably moved nothing (two identical parses
     * around a swipe). A scrollbar thumb alone is never proof. Anything else terminates as a
     * typed partial result that must not authorize a spend or an automatic choice. The list is
     * scrolled back to the top afterwards (the pager must only be paged from the top, and a
     * later Confirm must commit the page as the game presents it).
     */
    private fun readCompleteSparkSet(
        geometry: SparkListGeometry,
        label: String,
        maxScrolls: Int = SPARK_SCAN_MAX_SCROLLS,
        budgetMs: Long = SPARK_SCAN_BUDGET_MS,
    ): SparkSetReading {
        val startMs = System.currentTimeMillis()
        var merged = listOf<SparkRowFact>()
        var previousRows: List<SparkRowFact>? = null
        var scrolls = 0
        var result: SparkSetReading? = null
        while (result == null) {
            // A user Stop or queue-stop mid-scan: bail as a partial read (never complete) so the
            // caller keeps the original set. A hard interrupt throws out of waitSafe below and is
            // handled by the FSM; this covers the soft-stop flags waitSafe only returns on.
            if (!BotService.isRunning || StartModule.queueStopRequested) {
                result = SparkSetReading(merged, if (merged.isEmpty()) SparkScanTermination.FAILED else SparkScanTermination.TIMED_OUT_PARTIAL, scrolls)
                break
            }
            var frame = readSparkFrame(iu.getSourceBitmap(), geometry)
            if ((frame == null || frame.rows.isEmpty()) && merged.isNotEmpty()) {
                // A mid-settle capture can miss the grid entirely; one extra settle beat before
                // declaring the scan partial.
                waitSafe(1.0)
                frame = readSparkFrame(iu.getSourceBitmap(), geometry)
            }
            if (frame == null || frame.rows.isEmpty()) {
                result =
                    if (merged.isEmpty()) {
                        SparkSetReading(emptyList(), SparkScanTermination.FAILED, scrolls)
                    } else {
                        SparkSetReading(merged, SparkScanTermination.TIMED_OUT_PARTIAL, scrolls)
                    }
                break
            }
            // No-progress is checked BEFORE the merge: a post-swipe frame identical to the
            // pre-swipe frame proves the list cannot move (it is at its bottom), regardless of
            // whether the frame's rows are repetitive enough to make the content merge ambiguous.
            // Checking it here keeps a bottom-of-list of look-alike whites a COMPLETE read rather
            // than letting the hardened (ambiguity-refusing) merge downgrade it to ALIGNMENT_FAILED.
            if (previousRows != null && previousRows.size == frame.rows.size && SparkScrollMerge.rowsAlign(previousRows, frame.rows)) {
                result = SparkSetReading(merged, SparkScanTermination.COMPLETE_NO_PROGRESS, scrolls)
                break
            }
            val mergedNext = SparkScrollMerge.merge(merged, frame.rows)
            if (mergedNext == null) {
                result = SparkSetReading(merged, SparkScanTermination.ALIGNMENT_FAILED, scrolls)
                break
            }
            merged = mergedNext
            if (frame.endMarkerSeen) {
                result = SparkSetReading(merged, SparkScanTermination.COMPLETE_END_MARKER, scrolls)
                break
            }
            if (scrolls >= maxScrolls || System.currentTimeMillis() - startMs > budgetMs) {
                result = SparkSetReading(merged, SparkScanTermination.TIMED_OUT_PARTIAL, scrolls)
                break
            }
            previousRows = frame.rows
            scrolls++
            gestureUtils.swipe(SPARK_LIST_SCROLL_X, 1100f, SPARK_LIST_SCROLL_X, 1100f - geometry.rowPitch * 4f, duration = 900L)
            waitSafe(1.0)
        }
        if (scrolls > 0) {
            restoreSparkListTop(geometry, merged.firstOrNull(), scrolls)
        }
        MessageLog.i(
            TAG,
            "[SPARKS] Scan ($label): ${result!!.rows.size} rows, ${result.termination.name}, $scrolls scroll(s), ${result.unreadableRowCount} unreadable name(s).",
        )
        return result
    }

    /** Scrolls a spark list back to its top and verifies the first merged row is visible again.
     * Best-effort: a failed restore is logged, and the callers re-resolve the screen before
     * acting anyway. */
    private fun restoreSparkListTop(geometry: SparkListGeometry, expectedFirst: SparkRowFact?, scrollsUsed: Int) {
        repeat(scrollsUsed + 1) {
            gestureUtils.swipe(SPARK_LIST_SCROLL_X, 700f, SPARK_LIST_SCROLL_X, 700f + geometry.rowPitch * 4f, duration = 900L)
            waitSafe(0.8)
        }
        val frame = readSparkFrame(iu.getSourceBitmap(), geometry)
        val first = frame?.rows?.firstOrNull()
        if (expectedFirst == null || first == null || first.kind != expectedFirst.kind || first.stars != expectedFirst.stars) {
            MessageLog.w(TAG, "[SPARKS] List top not verified after restore (saw ${first?.kind}/${first?.stars} vs ${expectedFirst?.kind}/${expectedFirst?.stars}).")
        }
    }

    /**
     * Logs one greppable `[SPARKS]` line for a read spark set and appends a type="sparks"
     * record to the outcome corpus. The career's own outcome record precedes it in the same file
     * and the trainee snapshot makes the record self-contained. [phase] is "original" for the
     * set the career rolled, "rerolled" for the redraw after a 30 TP spend, "kept" for the set
     * that survives. The reroll transaction's identity fields ride along additively (older
     * records without them stay readable, and the base schema is untouched).
     */
    private fun recordSparkRows(rows: List<SparkRowFact>, phase: String, reading: SparkSetReading? = null) {
        if (rows.isEmpty()) {
            MessageLog.w(TAG, "[SPARKS] Could not read any spark rows ($phase set) - geometry drift or a mid-transition frame.")
            return
        }
        MessageLog.i(TAG, "[SPARKS] ${phase.replaceFirstChar { it.uppercase() }} set: " + rows.joinToString(" | ") { "${it.name} ${it.stars}-star (${it.kind.wire})" })
        runCatching {
            val record = JSONObject()
            record.put("type", "sparks")
            record.put("ts", System.currentTimeMillis())
            StartModule.lastCareerEndTrainee?.let { record.put("trainee", it) }
            // Direct arm attribution (same snapshot as this career's outcome record). Omitted when the
            // snapshot is absent (first career of the session / debug-mode direct invocation) so the
            // analyzer falls back to the positional join rather than reading a wrong field.
            StartModule.lastCareerEndScenario?.let { record.put("scenario", it) }
            StartModule.lastCareerEndFp?.let { record.put("fp", it) }
            record.put("phase", phase)
            record.put(
                "rows",
                JSONArray().apply {
                    rows.forEach { row ->
                        put(
                            JSONObject().apply {
                                put("name", row.name)
                                put("stars", row.stars)
                                put("kind", row.kind.wire)
                            },
                        )
                    }
                },
            )
            reading?.let {
                record.put("scan", it.termination.name)
                record.put("scan_complete", it.complete)
            }
            SparkRerollGate.transaction?.let { tx ->
                tx.careerToken?.let { record.put("career", it) }
                record.put("tx", tx.careerNonce)
            }
            OutcomeCorpus.append(context, record)
        }.onFailure {
            MessageLog.w(TAG, "[SPARKS] Failed to append the sparks record: $it")
        }
    }

    /** Reads the rows off [bitmap] and records them; the pre-chooser call shape kept for the
     * visible-window paths (reroll off, keep-set dialog). */
    private fun recordSparkSet(bitmap: Bitmap, phase: String, geometry: SparkListGeometry = SPARKS_SCREEN_GEOMETRY) {
        recordSparkRows(readSparkRows(bitmap, geometry), phase)
    }

    /**
     * Handles the "Restore TP?" confirmation.
     *
     * Default: decline and end the queue gracefully - restoring spends resources, and that
     * decision belongs to the user. With the opt-in `runQueue.enableTpRestoreWithItems`
     * setting, the flow the user specified runs instead: Restore -> pick a row off the item
     * ladder (Toughness 30, then Star Fruit, then Carats as the last resort) -> quantity ->
     * OK -> Close -> resume the career start. Every rung Max-fills to the cap (an Event Boost
     * career costs 60 TP, more than one use covers, and fewer restore round-trips means fewer
     * chances for the flow to break). Total Carat spend tracks TP consumed either way; Max
     * just batches it - per the maintainer's explicit request 2026-07-03.
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
        if (tpRestoresThisSession >= maxTpRestoresThisSession) {
            // Do NOT reuse declineResult() here: its reason recommends enabling item restore,
            // which on this branch is already enabled - the cap is the cause, and the persisted
            // failure has to say so or triage chases the wrong setting.
            MessageLog.w(TAG, "[NAV] TP restore session cap reached ($tpRestoresThisSession/$maxTpRestoresThisSession this session). Declining and ending the queue.")
            ButtonNo.click(iu)
            waitSafe(1.0)
            return TransitionResult.Failed(
                reason = tpRestoreCapReachedReason(tpRestoresThisSession, maxTpRestoresThisSession),
                transition = "PRE_RUN_CONFIRMATION -> TP_RESTORE_DIALOG",
                isRecoverable = false,
                recommendedAction =
                    "Press Start to begin a fresh bot session - the restore budget re-arms and the queue can be run again. " +
                        "If a legitimately configured queue hit this cap, report it: the budget scales with the configured run count and should not run out.",
            )
        }

        val noLocation = ButtonNo.find(iu).first
        if (noLocation == null) {
            // Dialog state shifted between detection and handling - re-detect next tick.
            return TransitionResult.Continue
        }

        MessageLog.i(TAG, "[NAV] Out of TP. Restoring with items per the enabled setting...")
        return when (driveTpRestorePicker(noLocation.x, noLocation.y)) {
            TpRestoreOutcome.RESTORED -> {
                MessageLog.i(TAG, "[NAV] Resuming the career start.")
                TransitionResult.Continue
            }
            TpRestoreOutcome.NO_ROW ->
                TransitionResult.Failed(
                    reason = "TP restore was enabled but no usable row (Toughness 30, Star Fruit, or Carats) was found in the Recover TP picker.",
                    transition = "TP_RESTORE_DIALOG -> RECOVER_TP_PICKER",
                    isRecoverable = false,
                    recommendedAction = "Restore TP manually, then restart the queue. All completed runs are saved.",
                )
            // Quantity popup never presented OK - leave the screen up and re-detect next tick.
            TpRestoreOutcome.NO_QUANTITY_OK -> TransitionResult.Continue
        }
    }

    /** Outcome of driving the Recover TP picker (see [driveTpRestorePicker]). */
    private enum class TpRestoreOutcome { RESTORED, NO_ROW, NO_QUANTITY_OK }

    /**
     * Drives the item-based TP restore from the "Restore TP?" dialog through the Recover TP
     * picker: Restore -> item ladder -> Use -> Max-fill quantity -> OK -> Close. Shared by the
     * career-start restore and the spark reroll's TP-short path - both spend the same items
     * against the same per-session cap. [anchorX]/[anchorY] locate the dialog's No button; the
     * Restore button sits at a fixed offset to its right. NO_ROW closes the picker before
     * returning; NO_QUANTITY_OK leaves the screen as-is for the caller to re-detect.
     */
    private fun driveTpRestorePicker(anchorX: Double, anchorY: Double): TpRestoreOutcome {
        gestureUtils.tap(anchorX + TP_RESTORE_FROM_NO_DX, anchorY, "tp_restore_button")
        waitSafe(1.5)

        // Item ladder: Toughness 30 (farmed) -> Star Fruit (event stock) -> Carats (premium,
        // last resort). Rows shift up as stocks empty - the game hides depleted item rows
        // outright - so each rung is anchored by its own template, never by row position.
        val drinkLocation = IconTpDrink.find(iu).first
        val starFruitLocation = if (drinkLocation == null) IconTpStarFruit.find(iu).first else null
        val caratsLocation = if (drinkLocation == null && starFruitLocation == null) IconTpCarats.find(iu).first else null
        val rowLocation = drinkLocation ?: starFruitLocation ?: caratsLocation
        if (rowLocation == null) {
            MessageLog.w(TAG, "[NAV] No restore row found in the Recover TP picker (Toughness 30, Star Fruit, or Carats). Closing it.")
            // The picker uses the wide list-dialog Close - the standard variants left it open on
            // screen when this branch fired live (2026-07-03).
            if (!ButtonCloseWide.click(iu) && !ButtonClose.click(iu)) ButtonCloseDialog.click(iu)
            waitSafe(1.0)
            return TpRestoreOutcome.NO_ROW
        }
        val useCarats = caratsLocation != null
        val itemName =
            when {
                drinkLocation != null -> "Toughness 30"
                starFruitLocation != null -> "Star Fruit"
                else -> "Carats"
            }
        if (useCarats) {
            MessageLog.w(TAG, "[NAV] No Toughness 30 or Star Fruit stock left. Max-filling TP with Carats (last resort per the enabled setting).")
        }

        gestureUtils.tap(rowLocation.x + TP_USE_FROM_DRINK_DX, rowLocation.y + TP_USE_FROM_DRINK_DY, "tp_use_button")
        waitSafe(1.2)

        val okLocation = ButtonOk.find(iu).first
        if (okLocation == null) {
            MessageLog.w(TAG, "[NAV] Quantity dialog OK button not found after Use. Re-detecting...")
            return TpRestoreOutcome.NO_QUANTITY_OK
        }
        // Fill TP to the cap with Max rather than a single +30. A normal career costs 30 TP,
        // but an Event Boost (TP Usage x2) career costs 60 - more than one use covers - and
        // Max-filling means fewer restore round-trips across a long unattended chain.
        // Max not found: fall back to a single +30 via the plus button above OK.
        if (!ButtonMax.click(iu)) {
            MessageLog.w(TAG, "[NAV] Max button not found on the TP quantity popup; falling back to a single +30 use.")
            gestureUtils.tap(okLocation.x + TP_PLUS_FROM_OK_DX, okLocation.y + TP_PLUS_FROM_OK_DY, "tp_plus_one")
        }
        waitSafe(0.6)
        ButtonOk.click(iu)
        waitSafe(1.2)
        // The Max/OK clicks above can silently no-op (dead gesture dispatch, or the Carats
        // rung's quantity popup rendering slower than the item rungs' - seen live 2026-07-11
        // and again 2026-07-12, both times on the Carats rung's first attempt). Max is unique
        // to the quantity popup: still visible means nothing was spent. Retry the pair once in
        // place - the 07-12 failure completed cleanly when the recovery state re-drove it two
        // minutes later, but by then the reroll window was gone, so the retry has to happen here.
        if (ButtonMax.find(iu).first != null) {
            MessageLog.w(TAG, "[NAV] Recover TP quantity popup still open after Max+OK ($itemName) - retrying the pair once.")
            waitSafe(1.5)
            ButtonMax.click(iu)
            waitSafe(0.6)
            ButtonOk.click(iu)
            waitSafe(1.2)
            if (ButtonMax.find(iu).first != null) {
                MessageLog.w(TAG, "[NAV] Recover TP quantity popup still open after the retry ($itemName) - the restore did not go through. Re-detecting...")
                return TpRestoreOutcome.NO_QUANTITY_OK
            }
        }
        if (!ButtonClose.click(iu)) ButtonCloseDialog.click(iu)
        waitSafe(1.0)

        tpRestoresThisSession++
        MessageLog.i(TAG, "[NAV] Restored TP with $itemName (restore $tpRestoresThisSession/$maxTpRestoresThisSession this session).")
        return TpRestoreOutcome.RESTORED
    }

    /**
     * RECOVER_TP_QUANTITY: the Recover TP quantity popup is up on its own - a dispatch death cut
     * a restore short mid-flow, or the queue was started cold on this screen. Finish the restore:
     * Max-fill, OK, verify the popup actually closed, then close the picker list behind it.
     * Honors the same setting and session cap as the item ladder; when spending is not allowed
     * the popup is cancelled so the next Start Career's TP prompt can decline cleanly.
     */
    private fun handleRecoverTpQuantity(): TransitionResult {
        val restoreWithItems = SettingsHelper.getBooleanSetting("runQueue", "enableTpRestoreWithItems", false)
        if (!restoreWithItems || tpRestoresThisSession >= maxTpRestoresThisSession) {
            MessageLog.w(
                TAG,
                "[NAV] Recover TP quantity popup is up but ${if (!restoreWithItems) "item restore is disabled" else "the session restore cap is reached"}. Cancelling it.",
            )
            ButtonCancel.click(iu)
            waitSafe(1.0)
            if (!ButtonCloseWide.click(iu) && !ButtonClose.click(iu)) ButtonCloseDialog.click(iu)
            waitSafe(1.0)
            return TransitionResult.Continue
        }
        if (!ButtonMax.click(iu)) {
            // Max is this state's own detection cue - a miss means the popup closed under us.
            return TransitionResult.Continue
        }
        waitSafe(0.6)
        ButtonOk.click(iu)
        waitSafe(1.2)
        if (ButtonMax.find(iu).first != null) {
            // Clicks are not landing. Keep re-detecting: the stuck-state counter force-rebinds
            // gesture dispatch partway through and this handler runs again with live taps.
            MessageLog.w(TAG, "[NAV] Recover TP quantity popup still open after Max+OK. Re-detecting...")
            return TransitionResult.Continue
        }
        waitSafe(0.5)
        if (!ButtonCloseWide.click(iu) && !ButtonClose.click(iu)) ButtonCloseDialog.click(iu)
        waitSafe(1.0)
        tpRestoresThisSession++
        MessageLog.i(TAG, "[NAV] Restored TP from the quantity popup (restore $tpRestoresThisSession/$maxTpRestoresThisSession this session).")
        return TransitionResult.Continue
    }

    /**
     * True when the post-career "Umamusume Details" summary card is up. OCR the centered title bar
     * (detailsTitleRegion) for "DETAIL" - the reliable positive signal isTraineeSelectScreen already
     * relies on, precisely because this card's big Close button does NOT dependably template-match.
     */

    /**
     * "Veteran Umamusume Max" popup: the veteran roster is full, so the game refuses to start a
     * career. Nothing the queue can do without deleting the user's completed umamusume (never
     * automated) - stop with a clear, actionable reason instead of looping on the popup's Close.
     */
    private fun handleVeteranUmamusumeMax(): TransitionResult =
        TransitionResult.Failed(
            reason = "Veteran Umamusume roster is full - the game will not start a new career until one is transferred or released.",
            transition = "SCENARIO_SELECT -> VETERAN_UMAMUSUME_MAX",
            isRecoverable = false,
            recommendedAction = "Open the Veteran Umamusume list in-game and transfer or release some, then restart the queue. All completed runs are saved.",
        )

    private fun isUmamusumeDetailsScreen(bitmap: Bitmap): Boolean {
        val title =
            try {
                iu.performOCROnRegion(
                    bitmap,
                    (bitmap.width * detailsTitleRegion[0]).toInt(),
                    (bitmap.height * detailsTitleRegion[1]).toInt(),
                    (bitmap.width * detailsTitleRegion[2]).toInt(),
                    (bitmap.height * detailsTitleRegion[3]).toInt(),
                    useThreshold = false,
                    useGrayscale = true,
                    scale = 2.0,
                    debugName = "nav_postrun_details_title",
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                ""
            }
        return title.uppercase().contains("DETAIL")
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

        // Finalization guard: the career summary screen sometimes lands here instead of
        // CAREER_SUMMARY, and the advancement cascade below would click Complete Career. That
        // click must not happen on a BLOCK verdict or, in Adaptive mode, without a usable one.
        if (ButtonCompleteCareer.check(iu, sourceBitmap = bitmap)) {
            finalizeBlockFailure("POST_RUN_RESULTS -> COMPLETE_CAREER_CONFIRMATION")?.let { return it }
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
            // Last resort before failing: the post-career "Umamusume Details" summary card lands
            // here because its big Close button does not reliably template-match (the cascade above
            // already tried ButtonClose). Detect it by its OCR'd title and dismiss it - try the wide
            // Close template first (it sometimes matches), else tap the card's fixed bottom-center
            // Close position. Without this the queue dies one screen short of the next launch
            // (observed 2026-07-08 on a Palmer -> next-run hand-off).
            if (isUmamusumeDetailsScreen(bitmap)) {
                if (!ButtonCloseWide.click(iu, sourceBitmap = bitmap)) {
                    gestureUtils.tap(bitmap.width * 0.5, bitmap.height * 0.86, "umamusume_details_close")
                }
                MessageLog.i(TAG, "[NAV] Dismissed the post-career \"Umamusume Details\" summary card to continue the between-run hand-off.")
                waitSafe(1.5)
                return TransitionResult.Continue
            }
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
     * OCR probe for the home screen's CAREER button label at its fixed position. Shared by
     * handleHomeScreen()'s detector 3 and detectScreenState()'s deep home probe.
     *
     * The CAREER button sits at ~75% width / ~86% height on both supported resolutions. The
     * region is cropped to 55%-95% width so decorative "Event" banners on the LEFT side of the
     * screen (common during seasonal promos) cannot match; vertically 80%-92% gives OCR room
     * around the 85-88% label band. The "CAREER" text uses a decorative gold font Tesseract
     * often can't read, but the "Event" label directly below it is plain and reads reliably -
     * either word confirms the button is present.
     *
     * @return The matched word ("CAREER" or "Event"), or null when neither was read.
     */
    private fun ocrCareerLabelAtHomePosition(bitmap: Bitmap): String? {
        return try {
            val ocrText =
                iu.performOCROnRegion(
                    bitmap,
                    (bitmap.width * 0.55).toInt(),
                    (bitmap.height * 0.80).toInt(),
                    (bitmap.width * 0.40).toInt(),
                    (bitmap.height * 0.12).toInt(),
                    useThreshold = false,
                    useGrayscale = false,
                    scale = 2.0,
                    debugName = "nav_career_ocr",
                )
            MessageLog.i(TAG, "[NAV] [HOME] OCR result: '$ocrText'")
            val ocrUpper = ocrText.uppercase()
            when {
                ocrUpper.contains("CAREER") -> "CAREER"
                ocrUpper.contains("EVENT") -> "Event"
                else -> null
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[NAV] [HOME] OCR detector failed: ${e.message}")
            null
        }
    }

    /**
     * HOME_SCREEN: Detected via ButtonMenuBarHomeSelected or ButtonCareerHome (plus the deep-probe
     * fallbacks in detectScreenState when armed).
     * Uses a multi-detector strategy to click the CAREER button:
     * 1. Primary: ButtonCareerHome (full button, lowered confidence 0.6)
     * 2. Secondary: ButtonCareerHomeText (text-only crop, confidence 0.55)
     * 3. Tertiary: ButtonCareerHomeTextActive (wordmark as it renders mid-career, confidence 0.6)
     * 4. Quaternary: OCR scan for the CAREER/Event label at the button position
     *
     * Transition: Multi-detector click.
     */
    private fun handleHomeScreen(): TransitionResult {
        // Reaching Home means any pending finalization is behind us (the Finish click consumed
        // its verdict) or was resolved outside the bot. A verdict still armed here is stale by
        // definition and must never leak into a later completion flow.
        CareerFinalizeGate.verdict?.let {
            MessageLog.w(TAG, "[NAV] [FINALIZE] Home reached with a leftover finalization verdict (token ${it.careerToken}); clearing it as stale.")
            CareerFinalizeGate.clear()
        }
        // The spark transaction is cleared here only when it has already committed 30 TP or
        // reached a terminal state - those are the survivors that could wrongly govern a later
        // career's selection screens. A pre-spend transaction deliberately survives: Home is
        // also crossed on the way INTO a career and by the daily-reset bounce back to the
        // lobby mid-career, and clearing it there is exactly what left a live career unable to
        // price its redraw (2026-07-19).
        SparkRerollGate.transaction?.let { tx ->
            val state = tx.state
            if (SparkRerollGate.clearOnHome()) {
                MessageLog.i(TAG, "[SPARKS] Home reached; clearing the finished reroll transaction (state $state).")
            }
        }
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

        // Detector 3: In-progress wordmark. Detectors 1 and 2 were both cut from the button as it
        // looks with NO career running; while one IS running the game swaps in the active trainee's
        // portrait and both miss (0.220 and 0.283 on a measured live frame). Since a mid-career
        // lobby bounce is exactly when re-entry needs this button, that gap is the whole failure.
        MessageLog.i(TAG, "[NAV] [HOME] Trying detector 3: ButtonCareerHomeTextActive (in-progress wordmark, confidence 0.6)...")
        if (ButtonCareerHomeTextActive.check(iu, sourceBitmap = bitmap)) {
            MessageLog.i(TAG, "[NAV] [HOME] Detector 3 matched. Clicking...")
            if (ButtonCareerHomeTextActive.click(iu, sourceBitmap = bitmap)) {
                MessageLog.i(TAG, "[NAV] [HOME] Detector 3 click succeeded.")
                waitSafe(3.0)
                return TransitionResult.Continue
            }
            MessageLog.w(TAG, "[NAV] [HOME] Detector 3 matched but click failed.")
        } else {
            MessageLog.i(TAG, "[NAV] [HOME] Detector 3 did not match.")
        }

        // Detector 4: OCR scan for the CAREER/Event label at the button's known position
        // (region bounds and word rules documented on ocrCareerLabelAtHomePosition, which the
        // deep home probe in detectScreenState shares).
        MessageLog.i(TAG, "[NAV] [HOME] Trying detector 4: OCR scan for 'CAREER' text...")
        val matchedWord = ocrCareerLabelAtHomePosition(bitmap)
        if (matchedWord != null) {
            MessageLog.i(TAG, "[NAV] [HOME] OCR found '$matchedWord'. Tapping CAREER button area...")
            // Tap at the center of the CAREER button. This is OCR-guided: we only reach here
            // after confirming the CAREER/Event text is on screen AND the state detection
            // already established HOME_SCREEN.
            val tapX = (bitmap.width * 0.75).toDouble()
            val tapY = (bitmap.height * 0.86).toDouble()
            gestureUtils.tap(tapX, tapY, "career_ocr_tap")
            waitSafe(3.0)
            return TransitionResult.Continue
        }
        MessageLog.i(TAG, "[NAV] [HOME] OCR did not find 'CAREER' or 'Event'.")

        // All detectors failed
        val screenshotPath = captureFailureScreenshot("HOME_SCREEN_career_click")
        return TransitionResult.Failed(
            reason = "HOME_SCREEN detected but all CAREER button detectors failed (template x3, OCR x1). Screenshot: $screenshotPath",
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
                // Smart Borrow (default ON): scroll down through the Borrow Card list, up to
                // MAX_BORROW_SCAN_PAGES, for the best card on the curated priority list and pick it
                // wherever it sits. The scan is bounded, so a card sitting past the last scanned
                // page is not seen - it takes the best card it reaches. First attempt only - if
                // the tap does not commit, the bounded retry must fall back to the default
                // template/top-row pick instead of repeating the same one. (A pick whose character
                // is already in the deck DOES commit - the duplicate check below handles that.)
                if (SettingsHelper.getBooleanSetting("runQueue", "enableSmartBorrow", true) && friendSlotFillAttempts == 1) {
                    if (trySmartBorrowPick()) {
                        waitSafe(2.0)
                        return TransitionResult.Continue
                    }
                    // The picker was closed on failure; the next iteration reopens it and
                    // attempt 2 runs the default pick.
                    MessageLog.i(TAG, "[NAV] [BORROW] Smart Borrow made no pick - the retry will use the default pick.")
                    waitSafe(1.5)
                    return TransitionResult.Continue
                }
                // Prefer the user's strong friend card when it is visible (template:
                // borrow_preferred_card.png). Blind first-row borrows produced measurably
                // weaker careers. Tap at the row's center X, not on the card art, which opens
                // card details.
                val (preferredLocation, _) = IconBorrowPreferredCard.find(iu)
                if (preferredLocation != null) {
                    MessageLog.i(TAG, "[NAV] Borrow Card list open. Preferred card found - selecting its row at (540, ${preferredLocation.y.toInt()})...")
                    gestureUtils.tap(540.0, preferredLocation.y, "borrow_preferred_row")
                } else {
                    // Validated default pick: read the visible rows and take the first one that is
                    // neither pill-tagged nor an excluded character (the active trainee included).
                    // The old blind first-row tap once borrowed the trainee's own card here - the
                    // game then disables Start Career and the launch is unrecoverable by clicking.
                    val scan = borrowRowsOnScreen(iu.getSourceBitmap())
                    val pick =
                        scan.rows.firstOrNull { (_, text) ->
                            borrowExcludedCharacters.none { borrowRowMatchesPreference(text, it) }
                        }
                    if (pick == null) {
                        ButtonClose.click(iu)
                        return TransitionResult.Failed(
                            reason = "No valid borrowed support available: every visible Borrow Card row is the active trainee's own character, already refused this launch, or blocked by the game.",
                            transition = "SUPPORT_DECK_SCREEN -> PRE_RUN_CONFIRMATION",
                            recommendedAction = "Follow more trainers or borrow a card of a different character manually, then restart the queue.",
                        )
                    }
                    MessageLog.i(
                        TAG,
                        "[NAV] Borrow Card list open. Preferred card not visible - selecting the first valid card \"${pick.second.replace(
                            "\n",
                            " ",
                        ).trim().take(60)}\" at (540, ${pick.first.toInt()})...",
                    )
                    lastBorrowPickEntry = pick.second
                    gestureUtils.tap(540.0, pick.first, "borrow_card_first_valid_row")
                }
                waitSafe(2.0)
            } else {
                MessageLog.w(TAG, "[NAV] Tapped the friend slot but the Borrow Card list did not appear. Re-detecting...")
            }
            return TransitionResult.Continue
        }

        // A borrowed card of a character already in the deck commits in the picker just fine -
        // the game only blocks at Start Career ("Cannot proceed with duplicate Umamusume in the
        // Support Card deck", a transient toast) while marking both clashing cards with a
        // persistent "! Duplicate Support" pill. A borrowed card of the ACTIVE TRAINEE's own
        // character behaves the same way with a red "! Trainee" pill and a DISABLED Start Career
        // ("Includes a character identical to the Trainee."). Clicking Start into either wall
        // wastes the whole launch, so replace the borrow instead: reopen the picker through the
        // slot's Friends banner, exclude the refused character, and take the next-best card.
        val duplicateDeckPill = LabelDuplicateSupportDeck.check(iu, sourceBitmap = bitmap)
        val traineeDeckPill = !duplicateDeckPill && LabelTraineeConflictDeck.check(iu, sourceBitmap = bitmap)
        if (SettingsHelper.getBooleanSetting("runQueue", "enableSmartBorrow", true) &&
            (duplicateDeckPill || traineeDeckPill || forceBorrowReplacement)
        ) {
            val viaMessage = forceBorrowReplacement && !duplicateDeckPill && !traineeDeckPill
            forceBorrowReplacement = false
            if (borrowDuplicateReplacements >= MAX_BORROW_DUPLICATE_REPLACEMENTS) {
                return TransitionResult.Failed(
                    reason =
                        if (traineeDeckPill || viaMessage) {
                            "INVALID_SUPPORT_FORMATION: the borrowed friend card is the active trainee's own character (the game disables Start Career), and $borrowDuplicateReplacements replacements did not resolve it. No valid borrowed support available."
                        } else {
                            "The borrowed friend card duplicates a character already in the support deck, and $borrowDuplicateReplacements replacements did not resolve it."
                        },
                    transition = "SUPPORT_DECK_SCREEN -> PRE_RUN_CONFIRMATION",
                    recommendedAction = "Borrow a card of a different character manually, then restart the queue.",
                )
            }
            borrowDuplicateReplacements++
            val refused = lastBorrowPickEntry?.let { borrowEntryCharacter(it) }
            refused?.let { borrowExcludedCharacters.add(it) }
            lastBorrowPickEntry = null
            if (traineeDeckPill || viaMessage) {
                // Whatever the pick was, the game says it is the trainee's character - make sure
                // the trainee is in the exclusion set even when the pick's OCR text was noisy.
                borrowEntryCharacter(borrowLaunchTraineeTarget).takeIf { it.isNotEmpty() }?.let { borrowExcludedCharacters.add(it) }
                MessageLog.w(
                    TAG,
                    "[NAV] [BORROW] Smart Borrow rejected candidate: game reported trainee conflict (\"! Trainee\"" +
                        (refused?.let { ", \"$it\"" } ?: "") +
                        "). Candidate excluded for this launch; replacing it (attempt $borrowDuplicateReplacements/$MAX_BORROW_DUPLICATE_REPLACEMENTS)...",
                )
            } else {
                MessageLog.w(
                    TAG,
                    "[NAV] [BORROW] Smart Borrow rejected candidate: duplicate support" +
                        (refused?.let { " (\"$it\")" } ?: "") +
                        ". Candidate excluded for this launch; replacing it (attempt $borrowDuplicateReplacements/$MAX_BORROW_DUPLICATE_REPLACEMENTS)...",
                )
            }
            val (bannerLocation, _) = LabelFriendSlotBanner.find(iu)
            if (bannerLocation == null) {
                MessageLog.w(TAG, "[NAV] [BORROW] Friend slot banner not found - re-detecting before retrying the replacement.")
                waitSafe(2.0)
                return TransitionResult.Continue
            }
            // Tap the card body above the banner; the banner strip itself may not be tappable.
            gestureUtils.tap(bannerLocation.x, bannerLocation.y - 180, "borrow_slot_reopen")
            waitSafe(2.0)
            if (ButtonBorrowCardRemove.find(iu).first == null) {
                MessageLog.w(TAG, "[NAV] [BORROW] Tapped the friend slot but the Borrow Card list did not appear. Re-detecting...")
                return TransitionResult.Continue
            }
            if (trySmartBorrowPick(replaceMode = true)) {
                MessageLog.i(TAG, "[NAV] [BORROW] Smart Borrow selected valid replacement.")
                waitSafe(2.0)
            } else {
                MessageLog.w(TAG, "[NAV] [BORROW] No replacement candidate found - the next pass retries until the replacement budget runs out.")
                waitSafe(1.5)
            }
            return TransitionResult.Continue
        }

        // Deck is already complete OR autoFillSupports is off. Click Start Career. The right-crop
        // variant matches when the trainee chibi is idling over the button's left edge, which held
        // the full-button templates below threshold for 15 straight checks on a live run.
        if (ButtonStartCareer.check(iu, sourceBitmap = bitmap) ||
            ButtonStartCareerOffset.check(iu, sourceBitmap = bitmap) ||
            ButtonStartCareerRight.check(iu, sourceBitmap = bitmap)
        ) {
            if (startCareerClickAttempts >= 5) {
                return TransitionResult.Failed(
                    reason = "Start Career was clicked $startCareerClickAttempts times with no screen transition. An empty or invalid deck slot is the usual cause.",
                    transition = "SUPPORT_DECK_SCREEN -> PRE_RUN_CONFIRMATION",
                    recommendedAction = "Complete the support deck manually, then restart the queue.",
                )
            }
            startCareerClickAttempts++
            MessageLog.i(TAG, "[NAV] Deck complete or auto-fill off. Clicking Start Career! (attempt $startCareerClickAttempts)...")
            if (!ButtonStartCareer.click(iu, sourceBitmap = bitmap) && !ButtonStartCareerOffset.click(iu, sourceBitmap = bitmap)) {
                ButtonStartCareerRight.click(iu, sourceBitmap = bitmap)
            }
            if (startCareerClickAttempts >= 2) {
                // A second ineffective click means the button is rendered but the game is not
                // accepting it. The persistent pills are handled before this block on every
                // pass; this catches the refusal TOAST ("Includes a character identical to the
                // Trainee.") in case the pill templates miss, and routes the next pass into the
                // borrow replacement flow instead of hammering a disabled control (five blind
                // taps once misdiagnosed this as dead gesture dispatch and force-rebound the
                // accessibility service for nothing).
                waitSafe(0.6)
                val toast =
                    try {
                        val fresh = iu.getSourceBitmap()
                        iu.performOCROnRegion(
                            fresh,
                            (fresh.width * 0.08).toInt(),
                            (fresh.height * 0.38).toInt(),
                            (fresh.width * 0.84).toInt(),
                            (fresh.height * 0.16).toInt(),
                            useThreshold = false,
                            useGrayscale = true,
                            scale = 1.5,
                            debugName = "nav_start_career_refusal_ocr",
                        )
                    } catch (e: InterruptedException) {
                        throw e
                    } catch (_: Exception) {
                        ""
                    }
                if (isTraineeConflictMessage(toast)) {
                    MessageLog.w(TAG, "[NAV] Support formation invalid; Start Career not pressed again (the game reported a trainee conflict). Routing to borrow replacement.")
                    forceBorrowReplacement = true
                    startCareerClickAttempts = 0
                    return TransitionResult.Continue
                }
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

    // ---- Trainee Select grid (rotation) ----
    // Tap targets are fractions of the captured bitmap so they hold across both supported configs.
    // Measured from 1080x1920 Trainee Select captures; tune live if a tap misses.
    // Only the top two rows are scanned per page (the third sits on the filter bar — a stray tap
    // there would open the sort dropdown); the swipe overlaps by ~half a row so nothing is skipped.
    private val traineeColFractions = floatArrayOf(0.13f, 0.315f, 0.50f, 0.685f, 0.87f)
    private val traineeRow0Fraction = 0.585f
    private val traineeRowStepFraction = 0.099f
    private val traineeGridRows = 2
    private val traineeMaxSwipes = 8
    private val traineeMatchThreshold = 0.86

    // Preview name banner: white "[Outfit] Name" text on a saturated character-colored pill. x,y,w,h.
    private val traineePreviewRegion = floatArrayOf(0.02f, 0.295f, 0.74f, 0.05f)

    // Header band carrying the "Trainee Select" title (top-left). x,y,w,h fractions.
    private val traineeHeaderRegion = floatArrayOf(0.0f, 0.125f, 0.45f, 0.05f)

    // Centered title bar of the "Umamusume Details" dialog (top-center, above the portrait band that
    // fools traineeHeaderRegion into reading "Trainee"). Read for "DETAILS" to reject that dialog
    // reliably: the real roster header never carries "Details", so this rejects only the dialog. x,y,w,h.
    private val detailsTitleRegion = floatArrayOf(0.18f, 0.02f, 0.64f, 0.07f)

    // ---- Support-deck composition (the [DECK] concentration advisory) ----
    // The deck-selection screen (PRE_RUN_CONFIRMATION) shows a row of support-type icons
    // (Speed, Stamina, Power, Guts, Wit, Friend, Group) each with an "xN" count badge; an absent
    // badge means zero of that type. We read the first five (the stat types) to judge how
    // concentrated the deck is for the build. deckCountColFractions are the box-CENTRE x; the box top
    // is deckCountRowYFraction and extends downward by deckCountBoxH, so it lands on the "xN" text row
    // just under each icon (not the icon glyph). Calibrated against a 1080x1920 deck capture: the 5
    // boxes sit on Speed/Stamina/Power/Guts/Wit (centres ~251/349/447/545/643px, evenly 98px apart), with
    // Friend/Group correctly excluded. The badge row is a fixed legend — every type icon is always
    // drawn in the same slot and just muted when its count is 0, so these fractions are deck-agnostic.
    private val deckStatLabels = arrayOf("Speed", "Stamina", "Power", "Guts", "Wit")
    private val deckCountColFractions = floatArrayOf(0.232f, 0.323f, 0.414f, 0.505f, 0.596f)
    private val deckCountRowYFraction = 0.677f
    private val deckCountBoxW = 0.085f
    private val deckCountBoxH = 0.02f

    /** Raw OCR of the header band (carries the "Trainee Select" title). */
    private fun readTraineeHeaderText(bitmap: Bitmap): String {
        return try {
            iu.performOCROnRegion(
                bitmap,
                (bitmap.width * traineeHeaderRegion[0]).toInt(),
                (bitmap.height * traineeHeaderRegion[1]).toInt(),
                (bitmap.width * traineeHeaderRegion[2]).toInt(),
                (bitmap.height * traineeHeaderRegion[3]).toInt(),
                useThreshold = false,
                useGrayscale = true,
                scale = 2.0,
                debugName = "nav_trainee_select_header",
            )
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Header OCR detector for the Trainee Select roster. The "Trainee Select" title sits top-left;
     * we only need the word "Trainee" to discriminate it from every other launch screen.
     */
    private fun isTraineeSelectScreen(bitmap: Bitmap): Boolean {
        if (!readTraineeHeaderText(bitmap).uppercase().contains("TRAINEE")) return false
        // Primary, OCR-free discriminator: any open game dialog (incl. the "Umamusume Details" card)
        // renders the title-gradient banner that DialogUtils template-matches; the full-screen Trainee
        // Select roster never does. This rejects the dialog before the brittle title/Close OCR below
        // can false-positive. The latch in detectScreenState already prevents this method from being
        // called once the career has started, so this is belt-and-suspenders for the pre-deck window.
        if (DialogUtils.check(iu, sourceBitmap = bitmap)) {
            MessageLog.i(TAG, "[NAV] Rejected Trainee Select detection: a dialog title-gradient banner is present (not the full-screen roster).")
            return false
        }
        // Disambiguate the "Umamusume Details" dialog, which shares the [Outfit] Name + stats + Track/
        // Distance/Style layout and whose character PORTRAIT sits in the traineeHeaderRegion band — the
        // portrait makes Tesseract hallucinate "Trainee", false-detecting the dialog as the roster
        // screen. The Close-button template does NOT always match the dialog's big "Close" button, so
        // the Close reject below can sail past it and the scan reads the aptitude row "Track Turf Dirt"
        // as a name, killing the queue under match-or-stop. The reliable positive signal is the
        // dialog's own centered "Umamusume Details" title, which the real roster header never carries;
        // read it first, and keep the Close reject as a secondary net.
        val detailsTitle =
            try {
                iu.performOCROnRegion(
                    bitmap,
                    (bitmap.width * detailsTitleRegion[0]).toInt(),
                    (bitmap.height * detailsTitleRegion[1]).toInt(),
                    (bitmap.width * detailsTitleRegion[2]).toInt(),
                    (bitmap.height * detailsTitleRegion[3]).toInt(),
                    useThreshold = false,
                    useGrayscale = true,
                    scale = 2.0,
                    debugName = "nav_details_title",
                )
            } catch (e: InterruptedException) {
                throw e
            } catch (_: Exception) {
                ""
            }
        if (detailsTitle.uppercase().contains("DETAIL")) {
            MessageLog.i(TAG, "[NAV] Rejected Trainee Select detection: read the \"Umamusume Details\" dialog title (\"${detailsTitle.replace("\n", " ").trim()}\").")
            return false
        }
        if (ButtonClose.check(iu, sourceBitmap = bitmap) || ButtonCloseDialog.check(iu, sourceBitmap = bitmap)) return false
        return true
    }

    /**
     * Read-only diagnostic for calibrating the Trainee Select reads against a live device. Captures
     * the current screen and logs what the header detector and the name-banner color OCR read, the
     * match score against the recorded target (if any), and the computed grid tap targets — WITHOUT
     * tapping anything. Driven by the `debugMode_startTraineeSelectTest` debug toggle: park the game
     * on the Trainee Select screen, enable it, and start the bot.
     *
     * @param injectedUtils reuse the running bot's image utils instead of constructing a fresh Game.
     */
    fun debugTraineeSelectRead(injectedUtils: CustomImageUtils? = null) {
        if (injectedUtils != null) {
            imageUtils = injectedUtils
        } else if (!ensureInitialised()) {
            MessageLog.e(TAG, "[ROTATION-TEST] Failed to initialise image utils.")
            return
        }
        val bitmap = iu.getSourceBitmap()
        MessageLog.i(TAG, "[ROTATION-TEST] ===== Trainee Select read-only diagnostic (${bitmap.width}x${bitmap.height}) =====")

        val headerRaw = readTraineeHeaderText(bitmap).replace("\n", " ").trim()
        MessageLog.i(TAG, "[ROTATION-TEST] Header OCR='$headerRaw' -> detectedTraineeSelect=${headerRaw.uppercase().contains("TRAINEE")}")

        val banner = readTraineePreviewName()
        MessageLog.i(TAG, "[ROTATION-TEST] Name banner OCR='$banner'")

        val target = SettingsHelper.getStringSetting("queueState", "currentTrainee")
        if (target.isNotBlank() && banner.isNotBlank()) {
            val score = TraineeNameMatcher.score(target, banner)
            MessageLog.i(TAG, "[ROTATION-TEST] target='$target' vs banner score=${"%.3f".format(score)} (accept >= $traineeMatchThreshold)")
        } else {
            MessageLog.i(TAG, "[ROTATION-TEST] No currentTrainee target set; check the raw banner read above by eye.")
        }

        val sb = StringBuilder("[ROTATION-TEST] Computed grid tap targets (NOT tapped): ")
        for (row in 0 until traineeGridRows) {
            for (col in traineeColFractions.indices) {
                val x = (bitmap.width * traineeColFractions[col]).toInt()
                val y = (bitmap.height * (traineeRow0Fraction + row * traineeRowStepFraction)).toInt()
                sb.append("[$col,$row]=($x,$y) ")
            }
        }
        MessageLog.i(TAG, sb.toString())
        MessageLog.i(TAG, "[ROTATION-TEST] ===== end =====")
    }

    /**
     * Reads the five stat-type "xN" count badges off the deck-selection screen. An absent badge
     * (zero of that type) reads as empty, which parses to 0. Returns counts indexed to
     * [deckStatLabels] (Speed, Stamina, Power, Guts, Wit). Never throws except on interrupt.
     */
    private fun readDeckTypeCounts(bitmap: Bitmap): IntArray {
        val counts = IntArray(deckCountColFractions.size)
        val boxW = (bitmap.width * deckCountBoxW).toInt()
        val boxH = (bitmap.height * deckCountBoxH).toInt()
        val y = (bitmap.height * deckCountRowYFraction).toInt()
        for (i in deckCountColFractions.indices) {
            val x = (bitmap.width * deckCountColFractions[i]).toInt() - boxW / 2
            val raw =
                try {
                    iu.performOCROnRegion(
                        bitmap,
                        x,
                        y,
                        boxW,
                        boxH,
                        useThreshold = true,
                        useGrayscale = true,
                        scale = 3.0,
                        ocrEngine = "tesseract_digits",
                        debugName = "deck_count_${deckStatLabels[i]}",
                    )
                } catch (e: InterruptedException) {
                    throw e
                } catch (_: Exception) {
                    ""
                }
            // The badge reads "xN". tesseract_digits is non-deterministic on the leading "x" — it
            // usually drops it ("x1" -> "1") but sometimes maps it to a digit ("x1" -> "11"). The
            // count is always the TRAILING glyph and physically single-digit (0-6 cards of one type),
            // so take the last digit, not the whole run, and clamp out any larger misread.
            counts[i] = (raw.lastOrNull { it.isDigit() }?.digitToIntOrNull() ?: 0).coerceIn(0, 6)
        }
        return counts
    }

    /**
     * Reads the support-deck composition off the deck screen and warns when it looks too spread for
     * the build. A spread deck (few same-type cards on the build facility) cannot generate the
     * friendship/rainbow trainings a stacked deck does, so its careers under-perform — this flags
     * that at turn 0 instead of after a wasted hour. Advisory only: every failure path is swallowed
     * so the career launch is never blocked by a bad read.
     *
     * Scenario-aware: Trackblazer decks are intentionally spread and run on Race Bonus, not rainbow
     * concentration, so the count is informational there (the Race-Bonus gate is a separate check).
     * Unity Cup gets extra non-rainbow growth from team mechanics, so its floor is one lower.
     */
    private fun checkDeckConcentration() {
        try {
            if (!SettingsHelper.getBooleanSetting("training", "enableDeckConcentrationCheck", false)) return
            val scenario = SettingsHelper.getStringSetting("general", "scenario")
            if (scenario == "Daily Races" || scenario == "Team Trials") return // No support deck in misc modes.

            val counts = readDeckTypeCounts(iu.getSourceBitmap())
            val breakdown = deckStatLabels.indices.joinToString(", ") { "${deckStatLabels[it]}=${counts[it]}" }

            if (scenario == "Trackblazer") {
                MessageLog.i(
                    TAG,
                    "[DECK] Support deck: $breakdown. Trackblazer is Race-Bonus-driven, not rainbow-concentration-driven, so the count is informational here (the Race Bonus check is separate).",
                )
                return
            }

            // Resolve the build's core stat from the preset (spark focus first, then top priority).
            val core =
                SettingsHelper.getStringArraySetting("training", "focusOnSparkStatTarget").firstOrNull()
                    ?: SettingsHelper.getStringArraySetting("training", "statPrioritization").firstOrNull()
            val coreIndex = core?.let { c -> deckStatLabels.indexOfFirst { it.equals(c, ignoreCase = true) } } ?: -1
            if (coreIndex < 0) {
                MessageLog.i(TAG, "[DECK] Support deck: $breakdown. Build core stat unresolved (no focusOnSparkStatTarget/statPrioritization); skipping the concentration warning.")
                return
            }

            val baseFloor = SettingsHelper.getIntSetting("training", "deckConcentrationCardFloor", 4)
            val floor = if (scenario == "Unity Cup") maxOf(1, baseFloor - 1) else baseFloor
            val coreCount = counts[coreIndex]
            val coreLabel = deckStatLabels[coreIndex]

            if (coreCount < floor) {
                MessageLog.w(
                    TAG,
                    "[DECK] Likely rainbow-starved deck for a $coreLabel build: only $coreCount $coreLabel support card(s), want >= $floor for $scenario. Full deck: $breakdown. " +
                        "A spread deck makes few $coreLabel rainbows, which the training scorer needs for big stat turns. Consider 4-5 $coreLabel cards. (Advisory only — the run continues.)",
                )
            } else {
                MessageLog.i(TAG, "[DECK] Deck concentration OK for a $coreLabel build: $coreCount $coreLabel card(s) (floor $floor for $scenario). Full deck: $breakdown.")
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            MessageLog.w(TAG, "[DECK] Concentration check skipped (read failed: ${e.message}). The run continues normally.")
        }
    }

    /**
     * Read-only diagnostic for calibrating the deck-composition count read against a live device.
     * Park the game on the deck-selection screen (the one with Start Career! / Perks), enable
     * `debugMode_startDeckStatReadTest`, and start the bot. Logs every stat count and its OCR box so
     * the [deckCountColFractions] / [deckCountRowYFraction] / box-size estimates can be tuned.
     */
    fun debugDeckStatRead(injectedUtils: CustomImageUtils? = null) {
        if (injectedUtils != null) {
            imageUtils = injectedUtils
        } else if (!ensureInitialised()) {
            MessageLog.e(TAG, "[DECK-TEST] Failed to initialise image utils.")
            return
        }
        val bitmap = iu.getSourceBitmap()
        MessageLog.i(TAG, "[DECK-TEST] ===== Deck composition read-only diagnostic (${bitmap.width}x${bitmap.height}) =====")
        val counts = readDeckTypeCounts(bitmap)
        val y = (bitmap.height * deckCountRowYFraction).toInt()
        deckStatLabels.forEachIndexed { i, label ->
            val cx = (bitmap.width * deckCountColFractions[i]).toInt()
            MessageLog.i(TAG, "[DECK-TEST] $label: count=${counts[i]} (box centre x=$cx, y=$y)")
        }
        val core =
            SettingsHelper.getStringArraySetting("training", "focusOnSparkStatTarget").firstOrNull()
                ?: SettingsHelper.getStringArraySetting("training", "statPrioritization").firstOrNull()
        MessageLog.i(TAG, "[DECK-TEST] Build core stat (from preset) = ${core ?: "unresolved"}")
        MessageLog.i(TAG, "[DECK-TEST] If any count is wrong, tune deckCountColFractions / deckCountRowYFraction / deckCountBox*. ===== end =====")
    }

    /**
     * TRAINEE_SELECT_SCREEN (rotation only): pick the rotation trainee in-game and verify her name
     * before advancing. Taps each roster thumbnail, reads the preview name banner, and Jaro-Winkler
     * matches it against the target the queue recorded (queueState.currentTrainee). On a confident
     * match the tap has already selected her, so we click Next; if the whole grid scans out with no
     * match it STOPS rather than risk running the wrong trainee under this trainee's settings.
     */
    private fun handleTraineeSelectScreen(): TransitionResult {
        // A single-run launch (Game.kt auto-navigation with an applied-preset expectation) must NOT
        // read queueState: an interrupted queue leaves a stale currentTrainee behind, and hunting
        // that stale target is exactly how a Rudolf single run nearly launched El Condor under his
        // settings (2026-07-09, twice - the rotation flag stays on when only the queue toggle is off).
        val singleRunMode = singleRunTraineeTarget.isNotBlank()
        val target = if (singleRunMode) singleRunTraineeTarget else SettingsHelper.getStringSetting("queueState", "currentTrainee")
        // Sibling outfits to skip: a bare base-name target ("El Condor Pasa") is outfit-insensitive and
        // would otherwise match an owned outfit's banner ("[Kukulkan Warrior] El Condor Pasa"). The
        // frontend supplies the names; empty for outfit-specific or pre-existing entries (old behavior).
        val excludeOutfits =
            (if (singleRunMode) singleRunTraineeTargetExcludes else SettingsHelper.getStringSetting("queueState", "currentTraineeExcludes"))
                .split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (target.isBlank()) {
            return TransitionResult.Failed(
                reason = "On Trainee Select but no target trainee recorded (queueState.currentTrainee empty); cannot choose safely.",
                transition = "TRAINEE_SELECT_SCREEN -> LEGACY_SELECT_SCREEN",
                recommendedAction = "Configure trainee rotation, or manually select the trainee and restart the queue.",
            )
        }
        MessageLog.i(TAG, "[ROTATION] Trainee Select: target '$target'${if (singleRunMode) " (applied preset, single run)" else ""}.")

        // We are handling the trainee switch now — disarm the missed-detection backstop. (A no-match
        // STOP below never reaches Legacy Select, so the flag value is moot in that case.) Single
        // runs are not queue-managed and must not touch the queue's backstop state; they disarm
        // their own mirror backstop instead.
        if (singleRunMode) {
            singleRunTraineeSelectHandled = true
        } else {
            StartModule.setRotationSwitchPending(context, false)
        }

        // Fast path: the game pre-highlights the last trainee, so within a trainee's block (no
        // switch) the target is already selected and the preview already reads her name. Confirm it
        // and advance without disturbing the grid - no scan, no risk of changing the selection.
        val current = readTraineePreviewName()
        val currentExcluded = excludeOutfits.any { TraineeNameMatcher.hasOutfit(current, it) }
        if (!currentExcluded && current.isNotBlank() && TraineeNameMatcher.score(target, current) >= traineeMatchThreshold) {
            MessageLog.i(TAG, "[ROTATION] Target already selected ('$current'). Advancing.")
            if (ButtonNext.click(iu)) {
                waitSafe(2.0)
                return TransitionResult.Continue
            }
            return TransitionResult.Failed(
                reason = "Target trainee '$current' already selected but the Next click failed.",
                transition = "TRAINEE_SELECT_SCREEN -> LEGACY_SELECT_SCREEN",
                recommendedAction = "Manually press Next and restart the queue.",
            )
        }

        var bestScore = 0.0
        var bestLabel = ""
        val seen = HashSet<String>()

        anchorTraineeGridTop()?.let { return it }

        // Remembered-position jump: the roster order is stable between careers and the page swipe is a
        // fixed-distance drag, so the cell where this trainee was found last time is almost always
        // still hers. Swipe straight down to that page and tap only that cell; the same preview-OCR
        // threshold as the scan verifies the selection, so a miss (new pull, re-sort) costs nothing
        // but the jump and falls back to the full scan below. Measured: a full scan runs ~90s, this
        // path ~10s past the anchor.
        // Positions live in TraineePositionStore (a flat file), NOT in settings rows: the settings
        // variant provably saved and committed but was never readable by the next queue - see the
        // store's kdoc for the investigation summary.
        val posKey = target.lowercase().replace(Regex("[^a-z0-9]"), "")
        val remembered =
            TraineePositionStore.parseCell(
                TraineePositionStore.get(context, posKey),
                maxPage = traineeMaxSwipes,
                colCount = traineeColFractions.size,
                rowCount = traineeGridRows,
            )
        if (remembered != null) {
            val (rPage, rCol, rRow) = remembered
            MessageLog.i(TAG, "[ROTATION] Trying remembered position page=$rPage cell=($rCol,$rRow) for '$target'.")
            var jumpBitmap = iu.getSourceBitmap()
            repeat(rPage) {
                swipeTraineeGrid(jumpBitmap, pageDown = true)
                waitSafe(1.2)
                jumpBitmap = iu.getSourceBitmap()
            }
            gestureUtils.tap(
                (jumpBitmap.width * traineeColFractions[rCol]).toDouble(),
                (jumpBitmap.height * (traineeRow0Fraction + rRow * traineeRowStepFraction)).toDouble(),
                "trainee_remembered_c${rCol}_r$rRow",
            )
            waitSafe(1.0)
            val preview = readTraineePreviewName()
            val previewExcluded = excludeOutfits.any { TraineeNameMatcher.hasOutfit(preview, it) }
            if (!previewExcluded && preview.isNotBlank() && TraineeNameMatcher.score(target, preview) >= traineeMatchThreshold) {
                MessageLog.i(TAG, "[ROTATION] Remembered position hit: '$preview'. Selecting and advancing.")
                if (ButtonNext.click(iu)) {
                    waitSafe(2.0)
                    return TransitionResult.Continue
                }
                return TransitionResult.Failed(
                    reason = "Matched trainee '$preview' at the remembered position but the Next click failed.",
                    transition = "TRAINEE_SELECT_SCREEN -> LEGACY_SELECT_SCREEN",
                    recommendedAction = "Manually press Next and restart the queue.",
                )
            }
            MessageLog.i(TAG, "[ROTATION] Remembered position missed (read '$preview'); re-anchoring for the full scan.")
            anchorTraineeGridTop()?.let { return it }
        }

        // Single top-down pass from the anchored top. The page swipe spans less than the scan band
        // (see swipeTraineeGrid), so consecutive pages overlap by a row rather than skip; the name
        // dedup makes the re-scan free. Stop when a page reveals no new trainee (bottom reached) or
        // on a confident match.
        for (page in 0..traineeMaxSwipes) {
            val bitmap = iu.getSourceBitmap()
            var newThisPage = 0
            for (row in 0 until traineeGridRows) {
                for (col in traineeColFractions.indices) {
                    if (!BotService.isRunning || StartModule.queueStopRequested) {
                        return TransitionResult.Failed(
                            reason = "Queue stopped during trainee selection.",
                            transition = "TRAINEE_SELECT_SCREEN -> LEGACY_SELECT_SCREEN",
                            isRecoverable = false,
                        )
                    }
                    val tapX = bitmap.width * traineeColFractions[col]
                    val tapY = bitmap.height * (traineeRow0Fraction + row * traineeRowStepFraction)
                    gestureUtils.tap(tapX.toDouble(), tapY.toDouble(), "trainee_grid_c${col}_r$row")
                    waitSafe(1.0)
                    val preview = readTraineePreviewName()
                    if (preview.isBlank()) continue
                    val norm = preview.lowercase().replace(Regex("[^a-z0-9]"), "")
                    if (norm.isEmpty()) continue
                    if (!seen.add(norm)) continue // already scored on an earlier (overlapping) page.
                    // Skip a sibling-outfit banner: a bare base-name target is outfit-insensitive and
                    // would otherwise match an owned outfit ("[Kukulkan Warrior] El Condor Pasa").
                    if (excludeOutfits.any { TraineeNameMatcher.hasOutfit(preview, it) }) {
                        MessageLog.i(TAG, "[ROTATION] Cell ($col,$row): '$preview' is an excluded sibling outfit; skipping.")
                        continue
                    }
                    newThisPage++
                    val score = TraineeNameMatcher.score(target, preview)
                    MessageLog.i(TAG, "[ROTATION] Cell ($col,$row): '$preview' score=${"%.3f".format(score)}")
                    if (score > bestScore) {
                        bestScore = score
                        bestLabel = preview
                    }
                    if (score >= traineeMatchThreshold) {
                        MessageLog.i(TAG, "[ROTATION] Match: '$preview' (${"%.3f".format(score)}). Selecting and advancing.")
                        // Remember where she was found so the next switch to her can jump here
                        // directly instead of re-scanning the grid cell by cell.
                        if (TraineePositionStore.put(context, posKey, "$page,$col,$row")) {
                            MessageLog.i(TAG, "[ROTATION] Saved position page=$page cell=($col,$row) for '$target'.")
                        } else {
                            MessageLog.w(TAG, "[ROTATION] Could not save the grid position for '$target' (file write failed).")
                        }
                        waitSafe(0.5)
                        if (ButtonNext.click(iu)) {
                            waitSafe(2.0)
                            return TransitionResult.Continue
                        }
                        return TransitionResult.Failed(
                            reason = "Matched trainee '$preview' but the Next click failed.",
                            transition = "TRAINEE_SELECT_SCREEN -> LEGACY_SELECT_SCREEN",
                            recommendedAction = "Manually press Next and restart the queue.",
                        )
                    }
                }
            }
            // No new trainees on this page = bottom reached (or the list won't scroll). Stop paging.
            if (page > 0 && newThisPage == 0) break
            if (page < traineeMaxSwipes) {
                swipeTraineeGrid(bitmap, pageDown = true)
                waitSafe(1.2)
            }
        }

        return TransitionResult.Failed(
            reason = "Trainee '$target' not found after scanning ${seen.size} unique roster trainee(s); best was '$bestLabel' @ ${"%.3f".format(
                bestScore,
            )}. Stopping to avoid running the wrong trainee.",
            transition = "TRAINEE_SELECT_SCREEN -> LEGACY_SELECT_SCREEN",
            isRecoverable = true,
            recommendedAction = "Check that the rotation trainee is one you own and that its inGameName matches the in-game name, or select the trainee manually and restart.",
        )
    }

    /**
     * Anchors the trainee roster to its TOP. The game opens the roster pre-scrolled to the
     * last-played trainee — a variable entry row — so a fixed top anchor is what lets a
     * single-direction scan (and the remembered-position page arithmetic) cover everyone
     * reproducibly. Scrolls UP until the top-left tile stops changing (the list hit its ceiling);
     * over-scrolling up is a harmless no-op. Capped so a flaky read can't loop forever. (Replaced a
     * bidirectional scan whose down-swing overshot and skipped a whole row of trainees between
     * pages — see swipeTraineeGrid.)
     *
     * @return A [TransitionResult.Failed] when the queue was stopped mid-anchor, else null once anchored.
     */
    private fun anchorTraineeGridTop(): TransitionResult? {
        var prevTop = ""
        for (i in 0..traineeMaxSwipes + 2) {
            if (!BotService.isRunning || StartModule.queueStopRequested) {
                return TransitionResult.Failed(
                    reason = "Queue stopped during trainee selection.",
                    transition = "TRAINEE_SELECT_SCREEN -> LEGACY_SELECT_SCREEN",
                    isRecoverable = false,
                )
            }
            val anchorBmp = iu.getSourceBitmap()
            gestureUtils.tap(
                (anchorBmp.width * traineeColFractions[0]).toDouble(),
                (anchorBmp.height * traineeRow0Fraction).toDouble(),
                "trainee_anchor_top",
            )
            waitSafe(0.8)
            val top = readTraineePreviewName().lowercase().replace(Regex("[^a-z0-9]"), "")
            if (top.isNotEmpty() && top == prevTop) break // top-left unchanged after a swipe = ceiling.
            prevTop = top
            swipeTraineeGrid(anchorBmp, pageDown = false)
            waitSafe(1.2)
        }
        return null
    }

    /** Reads the "[Outfit] Name" preview banner (white text on a colored pill) via color OCR. */
    private fun readTraineePreviewName(): String {
        val bitmap = iu.getSourceBitmap()
        return try {
            iu
                .findTextByColor(
                    bitmap,
                    (bitmap.width * traineePreviewRegion[0]).toInt(),
                    (bitmap.height * traineePreviewRegion[1]).toInt(),
                    (bitmap.width * traineePreviewRegion[2]).toInt(),
                    (bitmap.height * traineePreviewRegion[3]).toInt(),
                    targetR = 255,
                    targetG = 255,
                    targetB = 255,
                    tolerance = 90,
                    scale = 2.0,
                    debugName = "trainee_preview_name",
                ).replace("\n", " ")
                // The "Career Info" / "Details" buttons sit to the right of the name at the same
                // height; their white backgrounds get caught by the white-text mask, so truncate at
                // the first button word (no trainee name contains "career" or "details").
                .split(Regex("(?i)career|details"))[0]
                .trim()
        } catch (e: InterruptedException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Slow, fling-free vertical drag over the roster grid for paging through trainees. A fast flick
     * triggers inertial scrolling that overshoots and skips rows; a long-duration drag moves the list
     * closer to 1:1 with the finger. The span is ~1.0 row — well under the 2-row scan band — so
     * consecutive pages overlap by ~a row rather than skip; the caller dedupes by name, so the re-scan
     * is free.
     *
     * Was 1.7 rows @ 650ms, which on 1080x1920 overshot to ~2.7 effective rows and skipped a whole row
     * of trainees between pages, stalling the queue. Device-calibrated: if a row is still skipped,
     * lower the 1.0f; if the list won't scroll at all, raise it.
     *
     * @param pageDown true pages DOWN the roster (finger up, later entries appear); false pages UP
     *                 toward the top (finger down, earlier entries appear).
     */
    private fun swipeTraineeGrid(bitmap: Bitmap, pageDown: Boolean) {
        val x = bitmap.width * 0.5f
        val half = bitmap.height * (traineeRowStepFraction * 1.0f) / 2f
        val mid = bitmap.height * 0.64f
        val top = mid - half
        val bottom = mid + half
        if (pageDown) {
            gestureUtils.swipe(x, bottom, x, top, duration = 850L)
        } else {
            gestureUtils.swipe(x, top, x, bottom, duration = 850L)
        }
    }

    /**
     * LEGACY_SELECT_SCREEN: Click Auto-Select to populate both legacy slots, then click OK. By
     * default the Confirm Auto-Select dialog's checkboxes are left UNticked, so Auto-Select uses
     * only OWNED umas (free). Opt-in (runQueue.enableLegacyIncludeGuests) first ticks all unchecked
     * options - Include Guests (borrowing a guest parent costs monies) plus the Racing Carnival
     * spark bonus when that event is live. After the legacies are populated the Next button becomes
     * active and the regular POST_RUN_RESULTS handler advances the flow on the next iteration.
     *
     * Detection: ButtonAutoSelect template match.
     * Transition: ButtonAutoSelect.click() -> [opt-in: Checkbox.click() x N] -> ButtonOk.click() -> ButtonNext (next iteration).
     */

    /**
     * Scans the open Borrow Card list page by page for the highest-priority Smart Borrow card
     * and taps it. Rows are located by their Last Login pill (findAll), which keeps the scan
     * correct at any scroll position; pages advance by a fixed slow swipe and rows are deduped
     * by normalized text. A non-empty runQueue.preferredBorrowName is treated as priority zero
     * ahead of the curated list. When the best find sits on an earlier page, the picker is
     * closed and reopened (which resets it to the top) and the target page is re-scrolled.
     *
     * @param replaceMode true when swapping out a committed borrow the deck flagged as a
     *   Duplicate Support: the picker reopens through the filled slot's Friends banner instead of
     *   the empty-slot icon, and when no curated card is available any untagged row of a
     *   character not yet refused is taken - an arbitrary legal borrow beats one the game will
     *   not start with.
     * @return true when a card was tapped; on false the picker has been closed so the caller's
     *   retry starts clean with the default pick.
     */
    private fun trySmartBorrowPick(replaceMode: Boolean = false): Boolean {
        val allPriorities = mutableListOf<String>()
        SettingsHelper.getStringSetting("runQueue", "preferredBorrowName").trim().takeIf { it.isNotEmpty() }?.let { allPriorities.add(it) }
        allPriorities.addAll(SmartBorrowList.priority)
        // Characters the deck already refused this launch are out, whatever the outfit.
        val priorities = filterBorrowPriorities(allPriorities, borrowExcludedCharacters)

        var bestEntry = Int.MAX_VALUE
        var bestPage = -1
        val seen = HashSet<String>()
        // Priority entries whose offered rows all carry the "! Duplicate Support" tag can never
        // be borrowed this launch. Treating them as unreachable lets the early tap settle for
        // the best card that is actually legal instead of scanning on for a blocked one.
        val unreachable = mutableSetOf<Int>()
        var page = 0
        while (page <= MAX_BORROW_SCAN_PAGES) {
            if (!BotService.isRunning || StartModule.queueStopRequested) break
            val bitmap = iu.getSourceBitmap()
            val scan = borrowRowsOnScreen(bitmap)
            val rows = scan.rows
            if (page == 0 && rows.isEmpty() && scan.duplicateTexts.isEmpty()) {
                MessageLog.w(TAG, "[NAV] [BORROW] No borrow rows detected on the opened picker.")
                break
            }
            for (dupText in scan.duplicateTexts + scan.traineeConflictTexts) {
                priorities.forEachIndexed { idx, entry ->
                    if (borrowRowMatchesPreference(dupText, entry) && unreachable.add(idx)) {
                        MessageLog.i(TAG, "[NAV] [BORROW] \"$entry\" is offered but blocked for this launch (duplicate or active-trainee conflict) - not a candidate.")
                    }
                }
            }
            var newRows = 0
            for (text in rows.map { it.second } + scan.duplicateTexts) {
                val norm = text.lowercase().filter { it.isLetterOrDigit() }
                if (norm.isEmpty() || !seen.add(norm)) continue
                newRows++
                MessageLog.i(TAG, "[NAV] [BORROW] Page $page: \"${text.replace("\n", " ").trim().take(60)}\"")
            }
            val pageBest = smartBorrowBestMatch(rows.map { it.second }, priorities)
            if (pageBest != null && pageBest.first < bestEntry) {
                bestEntry = pageBest.first
                bestPage = page
            }
            val firstReachable = priorities.indices.firstOrNull { it !in unreachable }
            if (pageBest != null && firstReachable != null && pageBest.first == firstReachable) {
                // The best card that can still legally be borrowed is on screen - take it now.
                val centerY = rows[pageBest.second].first
                MessageLog.i(TAG, "[NAV] [BORROW] \"${priorities[pageBest.first]}\" found. Selecting it at (540, ${centerY.toInt()}).")
                lastBorrowPickEntry = priorities[pageBest.first]
                gestureUtils.tap(540.0, centerY, "borrow_smart_row")
                return true
            }
            if (newRows == 0) break // a page of only already-seen rows = the end of the list
            if (page == MAX_BORROW_SCAN_PAGES) break
            swipeBorrowList(bitmap)
            page++
        }

        if (bestPage < 0) {
            if (replaceMode) {
                // No curated card left to try: any row not tagged Duplicate Support (those never
                // reach this list) and not already refused still beats the committed clash.
                for ((centerY, text) in borrowRowsOnScreen(iu.getSourceBitmap()).rows) {
                    if (borrowExcludedCharacters.any { borrowRowMatchesPreference(text, it) }) continue
                    MessageLog.i(TAG, "[NAV] [BORROW] No curated card available - replacing with \"${text.replace("\n", " ").trim().take(60)}\" at (540, ${centerY.toInt()}).")
                    lastBorrowPickEntry = text
                    gestureUtils.tap(540.0, centerY, "borrow_any_row")
                    return true
                }
            }
            ButtonClose.click(iu)
            return false
        }

        // Reopen to reset the list to the top deterministically, then rescroll to the best page.
        MessageLog.i(TAG, "[NAV] [BORROW] Best available card is \"${priorities[bestEntry]}\" on page $bestPage. Reopening the picker to select it...")
        ButtonClose.click(iu)
        waitSafe(1.5)
        if (!reopenBorrowPicker(replaceMode)) return false
        waitSafe(2.0)
        repeat(bestPage) {
            swipeBorrowList(iu.getSourceBitmap())
        }
        for ((centerY, text) in borrowRowsOnScreen(iu.getSourceBitmap()).rows) {
            if (borrowRowMatchesPreference(text, priorities[bestEntry])) {
                MessageLog.i(TAG, "[NAV] [BORROW] Selecting \"${priorities[bestEntry]}\" at (540, ${centerY.toInt()}).")
                lastBorrowPickEntry = priorities[bestEntry]
                gestureUtils.tap(540.0, centerY, "borrow_smart_row")
                return true
            }
        }
        MessageLog.w(TAG, "[NAV] [BORROW] Could not relocate \"${priorities[bestEntry]}\" after reopening (the list order may have shifted). Falling back.")
        ButtonClose.click(iu)
        return false
    }

    /** Opens the Borrow Card picker from the deck screen: via the empty-slot icon normally, or
     * via the filled slot's Friends banner when replacing a committed borrow. */
    private fun reopenBorrowPicker(replaceMode: Boolean): Boolean {
        if (!replaceMode) return IconFriendSlotEmpty.click(iu)
        val (bannerLocation, _) = LabelFriendSlotBanner.find(iu)
        if (bannerLocation == null) return false
        gestureUtils.tap(bannerLocation.x, bannerLocation.y - 180, "borrow_slot_reopen")
        return true
    }

    /** Visible picker rows split into borrowable [rows] and the texts of rows the game tagged
     * "! Duplicate Support" or "! Trainee" - those cannot be borrowed this launch but still
     * identify which priority entries are blocked. */
    private data class BorrowScan(val rows: List<Pair<Double, String>>, val duplicateTexts: List<String>, val traineeConflictTexts: List<String> = emptyList())

    /**
     * Reads the visible Borrow Card rows: each row is located by its Last Login pill and its
     * two-line card name is OCR'd from the fixed band above the pill. Rows whose name band would
     * clip the dialog header or the Filters/Close chrome are skipped. Rows the game tags
     * "! Duplicate Support" (picking one commits but the deck then blocks Start Career) or
     * "! Trainee" (the active trainee's own character - picking one commits but the game
     * DISABLES Start Career) are excluded from the borrowable rows and reported separately.
     * Rows whose OCR text reads as an excluded character (the active trainee included) are
     * also excluded, so an identity match protects the pick even when a pill template misses.
     */
    private fun borrowRowsOnScreen(bitmap: Bitmap): BorrowScan {
        val duplicatePills = LabelDuplicateSupport.findAll(iu, sourceBitmap = bitmap)
        val traineePills = LabelTraineeConflict.findAll(iu, sourceBitmap = bitmap)
        val duplicateTexts = mutableListOf<String>()
        val traineeConflictTexts = mutableListOf<String>()
        val rows =
            LabelBorrowLastLogin.findAll(iu, sourceBitmap = bitmap)
                .sortedBy { it.y }
                .mapNotNull { pill ->
                    val centerY = pill.y - BORROW_PILL_TO_ROW_CENTER_PX
                    if (centerY - BORROW_NAME_BAND_HALF_HEIGHT < 150 || centerY + BORROW_NAME_BAND_HALF_HEIGHT > bitmap.height - 300) return@mapNotNull null
                    val inPillWindow = { p: org.opencv.core.Point -> p.y >= centerY - BORROW_DUPLICATE_PILL_MAX_OFFSET && p.y <= centerY - BORROW_DUPLICATE_PILL_MIN_OFFSET }
                    val taggedDuplicate = duplicatePills.any(inPillWindow)
                    val taggedTrainee = !taggedDuplicate && traineePills.any(inPillWindow)
                    val text =
                        try {
                            iu.performOCROnRegion(
                                bitmap,
                                (bitmap.width * 0.21).toInt(),
                                (centerY - BORROW_NAME_BAND_HALF_HEIGHT).toInt(),
                                (bitmap.width * 0.52).toInt(),
                                BORROW_NAME_BAND_HALF_HEIGHT * 2,
                                useThreshold = false,
                                useGrayscale = true,
                                scale = 2.0,
                                debugName = "nav_borrow_row_ocr",
                            )
                        } catch (e: InterruptedException) {
                            throw e
                        } catch (_: Exception) {
                            ""
                        }
                    if (taggedDuplicate) {
                        MessageLog.i(TAG, "[NAV] [BORROW] Smart Borrow rejected candidate: duplicate support (\"${text.replace("\n", " ").trim().take(60)}\" is marked \"! Duplicate Support\").")
                        duplicateTexts.add(text)
                        return@mapNotNull null
                    }
                    if (taggedTrainee) {
                        MessageLog.i(TAG, "[NAV] [BORROW] Smart Borrow rejected candidate: same as active trainee (\"${text.replace("\n", " ").trim().take(60)}\" is marked \"! Trainee\").")
                        traineeConflictTexts.add(text)
                        return@mapNotNull null
                    }
                    if (borrowLaunchTraineeTarget.isNotBlank() && borrowCandidateConflictsWithTrainee(text, borrowLaunchTraineeTarget)) {
                        // Identity backstop for a missed pill: the row reads as the active trainee's
                        // own character, which the game will refuse at Start Career.
                        MessageLog.i(TAG, "[NAV] [BORROW] Smart Borrow rejected candidate: same as active trainee (\"${text.replace("\n", " ").trim().take(60)}\").")
                        traineeConflictTexts.add(text)
                        return@mapNotNull null
                    }
                    centerY to text
                }
        return BorrowScan(rows, duplicateTexts, traineeConflictTexts)
    }

    /** One page-advance swipe on the Borrow Card list: slow so the drag lands without inertia. */
    private fun swipeBorrowList(bitmap: Bitmap) {
        val x = bitmap.width * 0.5f
        val from = bitmap.height * 0.72f
        gestureUtils.swipe(x, from, x, from - BORROW_PAGE_SWIPE_PX, duration = 900L)
        waitSafe(1.2)
    }

    private fun handleLegacySelectScreen(): TransitionResult {
        // Rotation backstop: a trainee switch was required this launch but we reached Legacy Select
        // without handling Trainee Select (a missed detection tapped through it keeping the wrong
        // trainee). Stop before the career starts rather than run the wrong trainee. Queue-managed
        // launches only - a single run must not be failed by a stale pending flag an interrupted
        // queue left behind; it has its own mirror check below.
        if (singleRunTraineeTarget.isBlank() &&
            SettingsHelper.getBooleanSetting("runQueue", "enableTraineeRotation", false) &&
            SettingsHelper.getStringSetting("queueState", "rotationSwitchPending") == "true"
        ) {
            return TransitionResult.Failed(
                reason = "Trainee rotation required a switch this launch but Trainee Select was not handled before Legacy Select — the wrong trainee may be selected.",
                transition = "LEGACY_SELECT_SCREEN -> SUPPORT_DECK_SCREEN",
                recommendedAction = "Verify the rotation Trainee Select OCR (header + name banner), or select the trainee manually and restart the queue.",
            )
        }

        // Single-run mirror of the same backstop: an applied-preset expectation was armed but the
        // roster was never handled (a missed Trainee Select detection tapped through it), so the
        // game's sticky preselection may be the wrong trainee. Stop before the career starts.
        if (singleRunTraineeTarget.isNotBlank() && !singleRunTraineeSelectHandled) {
            return TransitionResult.Failed(
                reason = "This launch expected trainee '$singleRunTraineeTarget' (applied preset) but Trainee Select was not handled before Legacy Select — the wrong trainee may be selected.",
                transition = "LEGACY_SELECT_SCREEN -> SUPPORT_DECK_SCREEN",
                recommendedAction = "Select the trainee manually in-game and start the bot from the deck screen, or re-apply the intended preset and start again.",
            )
        }

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
            // Reached here via the title co-signal on a frame where Auto-Select has not rendered yet
            // (mid-transition). Wait and let the loop re-detect rather than failing the whole launch;
            // the stuck-iteration counter is the backstop if the button genuinely never appears.
            MessageLog.i(TAG, "[NAV] Legacy Select title matched but Auto-Select not clickable yet. Waiting...")
            waitSafe(2.0)
            return TransitionResult.Continue
        }
        // Mark as done even if subsequent steps fail - the button stays visible after click,
        // so without this guard the next iteration would re-enter this handler.
        legacyAutoSelectAlreadyDone = true
        waitSafe(2.0)

        // The Confirm Auto-Select dialog has 1-2 checkboxes: "Include Guests" (always; borrowing a
        // guest parent costs monies) and, during the Racing Carnival event, a free spark-bonus tick.
        // Default OFF: leave them unticked so Auto-Select uses only owned umas (free) - which suits
        // farming your own spark parents. Opt-in restores the tick-all behavior for players who'd
        // rather inherit stronger borrowed guests. A single generic Checkbox match can't tell the two
        // boxes apart, so the opt-in also re-enables the Carnival tick.
        if (SettingsHelper.getBooleanSetting("runQueue", "enableLegacyIncludeGuests", false)) {
            // Tick all unchecked checkboxes on the dialog. Safety cap of 3 iterations.
            for (i in 0 until 3) {
                if (!Checkbox.click(iu, tries = 1)) break
                waitSafe(0.4)
            }
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
        // Opt-in: tick "Event Boost (TP Usage x2)" before starting so careers earn double event
        // rewards (worth it during the Trackblazer event; the TP cost doubles, which the Max TP
        // restore covers). Runs before the Start Career tap so the boost is committed with the run.
        if (SettingsHelper.getBooleanSetting("runQueue", "enableEventBoost", false)) {
            tickEventBoostIfOff()
        }

        // Read the support-deck composition off this screen and warn if it looks too spread for the
        // build. Read-only and fully guarded — it never blocks the Start Career tap below.
        checkDeckConcentration()

        MessageLog.i(TAG, "[NAV] Clicking 'Start Career!'...")

        if (ButtonStartCareer.click(iu) || ButtonStartCareerOffset.click(iu) || ButtonStartCareerRight.click(iu)) {
            waitSafe(3.0)
            // Check for a second "Start Career!" confirmation screen.
            if (ButtonStartCareer.check(iu) || ButtonStartCareerOffset.check(iu) || ButtonStartCareerRight.check(iu)) {
                MessageLog.i(TAG, "[NAV] Second 'Start Career!' confirmation detected. Clicking...")
                // Short-circuit: if the primary template clicks the button successfully, do NOT
                // also fire the offset variant. Without this guard, the offset click lands on
                // whatever screen appeared after the first click (cinematic, dialog) and produces
                // an unintended early tap. Same rule down the chain: at most ONE click fires.
                if (!ButtonStartCareer.click(iu) && !ButtonStartCareerOffset.click(iu)) {
                    ButtonStartCareerRight.click(iu)
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
     * Ticks the "Event Boost (TP Usage x2)" checkbox on the Final Confirmation screen if it is OFF.
     * The dim OFF-state bar is the anchor; the checkbox sits a fixed offset to its left. A no-op when
     * the bar isn't matched - already ticked, or the boost isn't offered (e.g. outside the event).
     */
    private fun tickEventBoostIfOff() {
        val (barLocation, _) = LabelEventBoostOff.find(iu)
        if (barLocation == null) {
            MessageLog.i(TAG, "[NAV] Event Boost enabled but the bar was not found (not offered on this screen). Skipping.")
            return
        }
        // The bar template matches BOTH states - OFF (dim maroon) and ON (bright pink) differ only
        // in colour, which template matching normalises away. So read the checkbox colour to decide:
        // the ticked checkmark is green, the un-ticked one is grey.
        val cbX = (barLocation.x + EVENT_BOOST_CHECKBOX_DX).toInt()
        val cbY = (barLocation.y + EVENT_BOOST_CHECKBOX_DY).toInt()
        if (isCheckboxGreen(iu.getSourceBitmap(), cbX, cbY)) {
            MessageLog.i(TAG, "[NAV] Event Boost is already ticked. Leaving it on.")
            return
        }
        MessageLog.i(TAG, "[NAV] Event Boost is OFF. Ticking the checkbox to double event rewards (TP cost also doubles)...")
        gestureUtils.tap(barLocation.x + EVENT_BOOST_CHECKBOX_DX, barLocation.y + EVENT_BOOST_CHECKBOX_DY, "event_boost_checkbox")
        waitSafe(0.8)
        // Verify the tick landed by re-reading the checkbox colour (a fresh capture - the state changed).
        if (isCheckboxGreen(iu.getSourceBitmap(), cbX, cbY)) {
            MessageLog.i(TAG, "[NAV] Event Boost ticked.")
        } else {
            MessageLog.w(TAG, "[NAV] Tapped the Event Boost checkbox but it still reads grey; the tap may have missed.")
        }
    }

    /**
     * Reads the Event Boost checkbox to tell ticked (green checkmark) from un-ticked (grey). Counts
     * pixels in a small box around the checkbox center where green clearly dominates red and blue;
     * the green checkmark yields hundreds, the grey checkmark yields ~zero (measured 565 vs 0).
     */
    private fun isCheckboxGreen(bitmap: android.graphics.Bitmap, cx: Int, cy: Int): Boolean {
        val half = EVENT_BOOST_CHECKBOX_SAMPLE_HALF
        val x0 = (cx - half).coerceAtLeast(0)
        val y0 = (cy - half).coerceAtLeast(0)
        val x1 = (cx + half).coerceAtMost(bitmap.width - 1)
        val y1 = (cy + half).coerceAtMost(bitmap.height - 1)
        var greenDominant = 0
        var y = y0
        while (y <= y1) {
            var x = x0
            while (x <= x1) {
                val p = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(p)
                val g = android.graphics.Color.green(p)
                val b = android.graphics.Color.blue(p)
                if (g - maxOf(r, b) > EVENT_BOOST_GREEN_DOMINANCE) greenDominant++
                x++
            }
            y++
        }
        return greenDominant >= EVENT_BOOST_ON_GREEN_PIXELS
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
     * TAP_TO_CONTINUE: an in-career screen that shows a Skip pill but is NOT the launch Quick Mode
     * prompt - a scenario-event cutscene, goal intro, or race intro ("Start!/TAP"). These advance on
     * a tap to the screen BODY, not the Skip pill (tapping the pill only toggles it).
     * Uses the proven (0.5, 0.677) = (540,1300 on 1080x1920) coordinate from Campaign's in-career
     * recovery. navigate() exempts this state from the per-state stuck-limit and gives it a bounded
     * counter + force-rebind, so a multi-line cutscene advances frame by frame without bailing.
     */
    private fun handleTapToContinue(): TransitionResult {
        val bitmap = iu.getSourceBitmap()
        MessageLog.i(TAG, "[NAV] TAP_TO_CONTINUE: body-tapping to advance the in-career screen.")
        gestureUtils.tap((bitmap.width * 0.5).toDouble(), (bitmap.height * 0.677).toDouble(), "tap_to_continue_advance")
        waitSafe(0.8)
        return TransitionResult.Continue
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

        // Reached only while skip has NOT been maxed yet this session: detectScreenState routes a
        // skip pill to QUICK_MODE_PROMPT only while !skipToggleAlreadyDone (once maxed, later skip
        // pills become TAP_TO_CONTINUE). So this is the genuine launch Quick Mode prompt - max skip,
        // then confirm.

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

        // Apply the configured Quick Mode choice when the settings dialog reads as itself. The
        // old behavior was a blind Confirm, which silently locked in whatever row the game
        // remembered; the planner selects the configured row and verifies the radio moved before
        // confirming. The blind Confirm remains as the fallback for the non-dialog variants of
        // this screen.
        val dialogBitmap = iu.getSourceBitmap()
        val dialogSampler = SparkPixelSampler { x, y -> dialogBitmap.getPixel(x, y) }
        if (quickModeDialogPresent(dialogSampler)) {
            val configured = SettingsHelper.getStringSetting("scenarioOverrides", "grandConcertQuickMode", "dont_use")
            when (val action = QuickModePlanner.plan(configured, quickModeSelectedIndex(dialogSampler))) {
                is QuickModeAction.Select -> {
                    MessageLog.i(TAG, "[NAV] Quick Mode: selecting \"${QuickModeOption.entries[action.rowIndex].label}\".")
                    gestureUtils.tap(QuickModeGeometry.RADIO_X.toDouble(), QuickModeGeometry.ROW_YS[action.rowIndex].toDouble(), "quickmode_select")
                    waitSafe(0.8)
                    val verifyBitmap = iu.getSourceBitmap()
                    if (quickModeSelectedIndex(SparkPixelSampler { x, y -> verifyBitmap.getPixel(x, y) }) == action.rowIndex) {
                        gestureUtils.tap(QuickModeGeometry.CONFIRM_X.toDouble(), QuickModeGeometry.CONFIRM_Y.toDouble(), "quickmode_confirm")
                        waitSafe(2.0)
                    } else {
                        MessageLog.w(TAG, "[NAV] Quick Mode: the selection did not take; not confirming this tick.")
                    }
                }
                QuickModeAction.ConfirmOnly -> {
                    MessageLog.i(TAG, "[NAV] Quick Mode: configured option already selected; confirming.")
                    gestureUtils.tap(QuickModeGeometry.CONFIRM_X.toDouble(), QuickModeGeometry.CONFIRM_Y.toDouble(), "quickmode_confirm")
                    waitSafe(2.0)
                }
                is QuickModeAction.HandOff -> {
                    // Only an unreadable selection lands here now that the setting ships a
                    // default. Confirm nothing; the stuck counter escalates safely.
                    MessageLog.w(TAG, "[NAV] Quick Mode: ${action.handoff.playerMessage()}")
                }
            }
        } else if (ButtonConfirm.check(iu)) {
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

    /**
     * SCENARIO_SELECT: pages the scenario carousel to the run's target scenario, then advances.
     *
     * The game opens this screen on whatever scenario was last played, so a same-scenario queue
     * passes with a single Next tap. A cross-scenario rotation pages the 3-entry cyclic carousel
     * (URA Finale -> Unity Cup -> Trackblazer) with the right arrow until the target scenario's
     * logo shows. Bounded at one full cycle plus a settle re-check so a misread cannot loop.
     */
    private fun handleScenarioSelect(): TransitionResult {
        val target = SettingsHelper.getStringSetting("general", "scenario")
        val targetLogo =
            when (target) {
                "URA Finale" -> LabelScenarioSelectUra
                "Unity Cup" -> LabelScenarioSelectUnityCup
                "Trackblazer" -> LabelScenarioSelectTrackblazer
                else ->
                    return TransitionResult.Failed(
                        reason = "Scenario Select reached with unsupported target scenario \"$target\".",
                        transition = "SCENARIO_SELECT -> TRAINEE_SETUP",
                        isRecoverable = false,
                        recommendedAction = "Select a career scenario (URA Finale, Unity Cup, or Trackblazer) in the app and restart the queue.",
                    )
            }

        repeat(6) { attempt ->
            if (!BotService.isRunning || StartModule.queueStopRequested) {
                return TransitionResult.Failed(
                    reason = "Bot stopped while paging the Scenario Select carousel.",
                    transition = "SCENARIO_SELECT -> TRAINEE_SETUP",
                )
            }
            val bitmap = iu.getSourceBitmap()
            if (targetLogo.check(iu, sourceBitmap = bitmap)) {
                MessageLog.i(TAG, "[NAV] Scenario Select shows \"$target\". Advancing to trainee setup.")
                return if (ButtonNext.click(iu)) {
                    waitSafe(2.0)
                    TransitionResult.Continue
                } else {
                    TransitionResult.Failed(
                        reason = "Scenario Select shows \"$target\" but the Next button could not be clicked.",
                        transition = "SCENARIO_SELECT -> TRAINEE_SETUP",
                    )
                }
            }
            MessageLog.i(TAG, "[NAV] Scenario Select is not showing \"$target\" (page check ${attempt + 1}/6). Swiping the carousel to the next scenario.")
            // Swipe the card rather than tapping the arrow chevron. The chevron is a thin outline over
            // per-scenario background art and template-matches unreliably (it stalled the queue at ~0.55,
            // under the 0.6 gate). A horizontal drag across the card pages regardless of the arrow, and the
            // carousel wraps, so consistently dragging one direction cycles through every scenario within
            // the 6 attempts. Drag right-to-left across the card's upper-mid band (clear of the buttons).
            val swipeY = bitmap.height * 0.42f
            gestureUtils.swipe(bitmap.width * 0.80f, swipeY, bitmap.width * 0.20f, swipeY, duration = 450L)
            waitSafe(1.5)
        }
        return TransitionResult.Failed(
            reason = "Scenario Select never showed \"$target\" after paging the full carousel.",
            transition = "SCENARIO_SELECT -> TRAINEE_SETUP",
            recommendedAction = "Manually select the scenario in-game and restart the queue. If the scenario logos changed in a patch, recapture the scenario_select_* templates.",
        )
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
