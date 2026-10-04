package com.steve1316.uma_android_automation.bot.campaigns

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.CampaignBreakpointException
import com.steve1316.uma_android_automation.bot.ConcertSegment
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.LessonCardKind
import com.steve1316.uma_android_automation.bot.GrandConcertFanFacts
import com.steve1316.uma_android_automation.bot.GrandConcertFanPolicy
import com.steve1316.uma_android_automation.bot.GrandConcertFanPressure
import com.steve1316.uma_android_automation.bot.GrandConcertFanRequirement
import com.steve1316.uma_android_automation.bot.GrandConcertHandoff
import com.steve1316.uma_android_automation.bot.GrandConcertHandoffReason
import com.steve1316.uma_android_automation.bot.GrandConcertLessonReader
import com.steve1316.uma_android_automation.bot.GrandConcertPointContext
import com.steve1316.uma_android_automation.bot.GrandConcertPointDemand
import com.steve1316.uma_android_automation.bot.GrandConcertPolicy
import com.steve1316.uma_android_automation.bot.GrandConcertScenario
import com.steve1316.uma_android_automation.bot.GrandConcertSongCatalog
import com.steve1316.uma_android_automation.bot.GrandConcertState
import com.steve1316.uma_android_automation.bot.HypeTier
import com.steve1316.uma_android_automation.bot.LearnVerdict
import com.steve1316.uma_android_automation.bot.LessonList
import com.steve1316.uma_android_automation.bot.LessonListCard
import com.steve1316.uma_android_automation.bot.LessonScoreContext
import com.steve1316.uma_android_automation.bot.PerformancePointType
import com.steve1316.uma_android_automation.bot.PerformancePointVector
import com.steve1316.uma_android_automation.bot.ScenarioState
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.DialogSongAcquired
import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.IconRaceDayRibbon
import com.steve1316.uma_android_automation.types.StatName
import com.steve1316.uma_android_automation.utils.GrandConcertCareerComplete
import com.steve1316.uma_android_automation.utils.GrandConcertEscort
import com.steve1316.uma_android_automation.utils.GrandCutsceneCheckbox
import com.steve1316.uma_android_automation.utils.GrandConcertLessonGeometry
import com.steve1316.uma_android_automation.utils.GrandConcertTheme
import com.steve1316.uma_android_automation.utils.LessonSlotState
import com.steve1316.uma_android_automation.utils.SparkPixelSampler
import com.steve1316.uma_android_automation.utils.grandConcertActiveBonusesPanelPresent
import com.steve1316.uma_android_automation.utils.grandConcertBonusesUpdatedPresent
import com.steve1316.uma_android_automation.utils.grandConcertCareerCompleteScreenPresent
import com.steve1316.uma_android_automation.utils.grandConcertConcertConfirmPresent
import com.steve1316.uma_android_automation.utils.grandConcertConcertPendingScreenPresent
import com.steve1316.uma_android_automation.utils.grandConcertCutsceneCheckboxState
import com.steve1316.uma_android_automation.utils.grandConcertDialogHeaderPresent
import com.steve1316.uma_android_automation.utils.grandConcertLessonSlotState
import com.steve1316.uma_android_automation.utils.grandConcertOnStagePresent
import com.steve1316.uma_android_automation.utils.grandConcertPlaybackMenuButtonPresent
import com.steve1316.uma_android_automation.utils.grandConcertPlaybackMenuSkipPresent
import com.steve1316.uma_android_automation.utils.grandConcertPlaybackSkipPresent
import com.steve1316.uma_android_automation.utils.grandConcertResultNextPresent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grand Concert scenario ("Grand Live"): the shared [Campaign] loop plus the Lesson shop, concert escort, and
 * career-end drain.
 * Unknown screens end in a career-preserving handoff, not a relaunch: the game is alive, and a stray Confirm/Next/OK
 * can spend points or skip a concert irrecoverably.
 */
class GrandConcert(game: Game) : Campaign(game) {
    /** No finale-win banner is claimed: the finale has not been captured, so no win/lose signal is recorded. */
    override val capturesFinaleWins: Boolean = false

    private var announcedSupportLevel = false

    /** Reads the live Lesson list into telemetry. Never taps - navigation stays here in the campaign. */
    private val lessonReader = GrandConcertLessonReader(game)

    /** Read from the Training instance so a rotation resync, which rebuilds Training, updates it too. */
    private val statPriority: List<StatName>
        get() = training.statPrioritization

    /**
     * Committed fan facts, loaded once; null when missing or malformed (fan-pressure telemetry then reports UNKNOWN).
     */
    private val fanFacts: GrandConcertFanFacts? by lazy { GrandConcertFanFacts.loadFromAssets(game.myContext) }

    private var lessonVisitsThisRun = 0

    /**
     * Last turn whose shop visit found nothing to buy: the offer only changes on a new turn or a restock. day<=1 (date
     * not read yet) never blocks.
     */
    private var lastNoBuyVisitDay = -1

    /**
     * Songs bought this concert cycle: a floor on the true count (blind to free or pre-attach songs), so the deadline
     * term can only overestimate urgency.
     */
    private var songsBoughtThisCycle = 0

    /**
     * Songs bought across the career (milestone telemetry); a floor on the true total, like the cycle
     * counter.
     */
    private var songsBoughtThisCareer = 0

    private var lastConcertBoundary = 0

    /**
     * Escort attempts spent on the pending concert. A give-up is a recognition miss, not a timeout (concerts finish in
     * 28-37s of a ~100s budget), and re-entering is safe because the escort only taps identified screens.
     */
    private var concertEscortAttempts = 0

    /**
     * Cheapest unscheduled song with a readable cost, kept across turns so the training scorer can steer point income.
     */
    private var lastSongTargetCost: PerformancePointVector? = null
    private var lastSongTargetTitle: String? = null

    /**
     * Cheapest readable unscheduled technique cost, and whether a readable song was on offer: lets the scorer demand
     * gate-advancing technique colors while the song gate is closed.
     */
    private var lastGateTechniqueCost: PerformancePointVector? = null
    private var lastOfferHadSong: Boolean = false

    /** Titles of songs bought this career (observation only), used for the next-song lookahead. */
    private val purchasedSongTitles = mutableSetOf<String>()

    private var careerEndDrainDone = false

    fun announceSupportLevelOnce() {
        if (announcedSupportLevel) return
        announcedSupportLevel = true
        MessageLog.w(
            TAG,
            "[GRAND_CONCERT] Supported scenario: training, races, events, skills, the Lesson shop " +
                "(with a verify-before-Learn purchase gate), the concerts, and the career-end sequence through to the " +
                "home screen are all automated. A Lesson shop screen the bot does not recognize is backed out of and " +
                "retried later; a concert screen it still does not recognize after a few retries stops the run " +
                "safely with the career preserved, so Start can resume it.",
        )
    }

    /**
     * Typed stop for a screen the bot cannot drive: stop rather than relaunch or tap a generic Confirm that could spend
     * points.
     */
    fun handOffToPlayer(reason: GrandConcertHandoffReason, screenNote: String? = null, evidenceScreenshot: String? = null): GrandConcertHandoff {
        val handoff = GrandConcertHandoff(reason, screenNote, evidenceScreenshot)
        MessageLog.w(TAG, "[GRAND_CONCERT] ${handoff.playerMessage()}")
        return handoff
    }

    /**
     * Includes the scenario's pixel probe beside the shared complete_career template: the probe can hit the fade-in
     * frame before the template clears its 0.8 gate, and the old fallback then handed the career off with skill points
     * unspent.
     */
    override fun checkEndScreen(): Boolean {
        if (super.checkEndScreen()) return true
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        if (grandConcertCareerCompleteScreenPresent(sampler)) {
            MessageLog.i(TAG, "[GRAND_CONCERT] [CAREER_COMPLETE] Complete Career screen recognised by the scenario probe (button template not yet matched).")
            return true
        }
        return false
    }

    /**
     * Owns the concert-pending screen; any unrecognized escort state ends in a career-preserving handoff. Complete
     * Career is handled earlier by [checkEndScreen].
     */
    override fun checkCampaignSpecificConditions(): Boolean {
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }

        if (grandConcertConcertPendingScreenPresent(sampler)) {
            concertEscortAttempts++
            MessageLog.i(
                TAG,
                "[GRAND_CONCERT] [CONCERT] Concert-pending screen detected; running the concert escort " +
                    "(attempt $concertEscortAttempts of $MAX_CONCERT_ESCORT_ATTEMPTS).",
            )
            if (runConcertEscort()) {
                concertEscortAttempts = 0
                return true
            }
            if (concertEscortAttempts < MAX_CONCERT_ESCORT_ATTEMPTS) {
                MessageLog.w(
                    TAG,
                    "[GRAND_CONCERT] [CONCERT] Escort attempt $concertEscortAttempts did not finish the concert. " +
                        "Settling ${CONCERT_ESCORT_RETRY_WAIT}s and re-entering; the career is untouched.",
                )
                game.wait(CONCERT_ESCORT_RETRY_WAIT)
                return true
            }
            val handoff =
                handOffToPlayer(
                    GrandConcertHandoffReason.CONCERT_NOT_AUTOMATED,
                    "the concert flow reached a screen the escort does not know after $concertEscortAttempts attempts; finish it manually",
                )
            throw CampaignBreakpointException(handoff.playerMessage())
        }

        // No concert pending: reset the budget so the next concert does not inherit a spent attempt.
        concertEscortAttempts = 0
        return false
    }

    /**
     * Runs one concert from the pending screen back to a screen the main loop can drive. Every tap is gated on a probe
     * for its screen, unrecognized frames only wait, and an exhausted budget returns false for a career-preserving
     * handoff. A Training Event (New Year after a Late Dec concert) is a successful exit.
     */
    private fun runConcertEscort(): Boolean {
        // Logged for the analysis scripts: this count decides the result tier.
        MessageLog.i(
            TAG,
            "[GRAND_CONCERT] [CONCERT] Entering the concert with $songsBoughtThisCycle new song(s) this cycle " +
                "(Great Success needs ${GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value}; career purchased total " +
                "$songsBoughtThisCareer).",
        )
        game.tapCoordinate(GrandConcertEscort.CONCERT_BUTTON_X.toDouble(), GrandConcertEscort.CONCERT_BUTTON_Y.toDouble(), "gc_concert_open")
        game.wait(1.2)

        var confirmSeen = false
        for (attempt in 1..3) {
            val bitmap = game.imageUtils.getSourceBitmap()
            if (grandConcertConcertConfirmPresent(SparkPixelSampler { x, y -> bitmap.getPixel(x, y) })) {
                confirmSeen = true
                break
            }
            game.wait(1.0)
        }
        if (!confirmSeen) {
            MessageLog.w(TAG, "[GRAND_CONCERT] [CONCERT] The start confirmation dialog did not appear; aborting the escort without further taps.")
            return false
        }
        MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Start confirmation recognised; starting the concert.")
        if (!startConcertFromConfirm()) return false

        var ticks = 0
        var menuOpens = 0
        var menuSkips = 0
        while (ticks++ < MAX_ESCORT_TICKS) {
            val bitmap = game.imageUtils.getSourceBitmap()
            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            when {
                grandConcertPlaybackSkipPresent(sampler) -> {
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Playback detected; skipping the performance.")
                    game.tapCoordinate(GrandConcertEscort.SKIP_GLYPH_X.toDouble(), GrandConcertEscort.SKIP_GLYPH_Y.toDouble(), "gc_concert_skip")
                    game.wait(2.0)
                }
                // With a menu button in the skip disc, a tap there only toggles the menu: tap the Skip entry once the menu shows it, never Rotate.
                grandConcertPlaybackMenuSkipPresent(sampler) && menuSkips < MAX_PLAYBACK_MENU_TAPS -> {
                    menuSkips++
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Performance menu open; tapping its Skip (try $menuSkips of $MAX_PLAYBACK_MENU_TAPS).")
                    game.tapCoordinate(GrandConcertEscort.MENU_SKIP_X.toDouble(), GrandConcertEscort.MENU_SKIP_Y.toDouble(), "gc_concert_menu_skip")
                    game.wait(2.0)
                    if (playbackControlsPresent()) {
                        MessageLog.w(TAG, "[GRAND_CONCERT] [CONCERT] The performance is still showing after its menu Skip.")
                    } else {
                        MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Performance skipped from its menu.")
                    }
                }
                grandConcertPlaybackMenuButtonPresent(sampler) && menuOpens < MAX_PLAYBACK_MENU_TAPS -> {
                    menuOpens++
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Playback shows a menu button, not Skip; opening the menu (try $menuOpens of $MAX_PLAYBACK_MENU_TAPS).")
                    game.tapCoordinate(GrandConcertEscort.PLAYBACK_MENU_X.toDouble(), GrandConcertEscort.PLAYBACK_MENU_Y.toDouble(), "gc_concert_menu")
                    game.wait(1.0)
                }
                grandConcertResultNextPresent(sampler) -> {
                    game.tapCoordinate(GrandConcertEscort.NEXT_BUTTON_X.toDouble(), GrandConcertEscort.NEXT_BUTTON_Y.toDouble(), "gc_concert_next")
                    game.wait(1.5)
                }
                grandConcertBonusesUpdatedPresent(sampler) -> {
                    // Close dismisses the queued-bonus notice; Confirm opens the Active Concert Bonuses panel.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Bonuses Updated acknowledgment; closing.")
                    game.tapCoordinate(GrandConcertEscort.BONUSES_CLOSE_X.toDouble(), GrandConcertEscort.BONUSES_CLOSE_Y.toDouble(), "gc_concert_bonuses_close")
                    game.wait(1.2)
                }
                grandConcertActiveBonusesPanelPresent(sampler) -> {
                    // Defensive: the detail panel behind the Bonuses Updated dialog's Confirm.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Active Concert Bonuses panel; closing.")
                    game.tapCoordinate(GrandConcertEscort.ACTIVE_BONUSES_CLOSE_X.toDouble(), GrandConcertEscort.ACTIVE_BONUSES_CLOSE_Y.toDouble(), "gc_concert_active_bonuses_close")
                    game.wait(1.2)
                }
                grandConcertOnStagePresent(sampler) -> {
                    // The Grand's "ON STAGE!" huddle: one tap on the medallion proceeds.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] ON STAGE huddle; tapping to proceed.")
                    game.tapCoordinate(GrandConcertEscort.ON_STAGE_TAP_X.toDouble(), GrandConcertEscort.ON_STAGE_TAP_Y.toDouble(), "gc_concert_on_stage")
                    game.wait(2.0)
                }
                grandConcertConcertConfirmPresent(sampler) -> {
                    // Start confirmation back mid-flow: a tap was swallowed or an interstitial bounced the game back.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Start confirmation reappeared; driving it again.")
                    if (!startConcertFromConfirm()) return false
                }
                checkMainScreen() -> {
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Concert complete; back on the career screen.")
                    return true
                }
                checkTrainingEventScreen() -> {
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Concert complete; a trainee event follows and the main loop owns it.")
                    return true
                }
                IconRaceDayRibbon.check(game.imageUtils, sourceBitmap = bitmap) -> {
                    // A mandatory race day can land on the turn a concert ends and swaps the Training
                    // layout for the Race Day banner; the main loop owns it, so exit successfully. The icon
                    // check is used directly because checkMandatoryRacePrepScreen can tap Back.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Concert complete; a mandatory race day follows and the main loop owns it.")
                    return true
                }
                // A performance the account had never played unlocks its song after the skip.
                DialogUtils.getTitle(game.imageUtils, bitmap, logOnMiss = false) == DialogSongAcquired.title -> {
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Song Acquired notice; closing.")
                    DialogSongAcquired.close(game.imageUtils)
                    game.wait(1.2)
                }
                else -> game.wait(1.0)
            }
        }
        MessageLog.w(TAG, "[GRAND_CONCERT] [CONCERT] Escort budget exhausted on an unrecognised screen.")
        return false
    }

    private fun playbackControlsPresent(): Boolean {
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        return grandConcertPlaybackSkipPresent(sampler) || grandConcertPlaybackMenuButtonPresent(sampler) || grandConcertPlaybackMenuSkipPresent(sampler)
    }

    /**
     * Drives an open start confirmation to completion: checks the cutscene-skip box (verified by the green
     * glyph) on the Grand finale variant, taps Start, and confirms the dialog is gone. The heavier dialog
     * swallowed a single fire-and-forget Start tap.
     */
    private fun startConcertFromConfirm(): Boolean {
        for (attempt in 1..4) {
            val bitmap = game.imageUtils.getSourceBitmap()
            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            if (!grandConcertConcertConfirmPresent(sampler)) return true
            when (grandConcertCutsceneCheckboxState(sampler)) {
                GrandCutsceneCheckbox.UNCHECKED -> {
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Grand finale confirm: checking the cutscene-skip box.")
                    game.tapCoordinate(GrandConcertEscort.GRAND_CONFIRM_CHECKBOX_X.toDouble(), GrandConcertEscort.GRAND_CONFIRM_CHECKBOX_Y.toDouble(), "gc_grand_cutscene_skip")
                    game.wait(0.8)
                }
                GrandCutsceneCheckbox.CHECKED, GrandCutsceneCheckbox.ABSENT -> {
                    game.tapCoordinate(GrandConcertEscort.CONFIRM_START_X.toDouble(), GrandConcertEscort.CONFIRM_START_Y.toDouble(), "gc_concert_start")
                    game.wait(2.0)
                }
            }
        }
        MessageLog.w(TAG, "[GRAND_CONCERT] [CONCERT] The start confirmation did not leave after repeated taps; aborting the escort.")
        return false
    }

    /**
     * The URA Learn-button template does not exist on the Complete Career screen: drain leftover points through Lessons
     * once, then open Skills via the screen's own button. Other layouts use the shared entry.
     */
    override fun openCareerEndSkillScreen() {
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
        if (!grandConcertCareerCompleteScreenPresent(sampler)) {
            super.openCareerEndSkillScreen()
            return
        }
        if (!careerEndDrainDone) {
            MessageLog.i(TAG, "[GRAND_CONCERT] [CAREER_COMPLETE] Draining leftover performance points before the skill purchase.")
            val spent = drainLessonsAtCareerComplete()
            // Leave the flag unset when the drain never saw the list (the first attempt can run on a fade-in frame) so
            // the bounded retry in Campaign.process gets a real second drain.
            careerEndDrainDone = spent >= 0
            game.wait(1.0)
        }
        MessageLog.i(TAG, "[GRAND_CONCERT] [CAREER_COMPLETE] Opening the skill screen via the Complete Career layout's Skills button.")
        game.tapCoordinate(GrandConcertCareerComplete.SKILLS_X.toDouble(), GrandConcertCareerComplete.SKILLS_Y.toDouble(), "gc_career_complete_skills")
    }

    /**
     * Re-reads while the trio is incomplete: the list paints top-down, so an early read returns one card and blanks,
     * which looks like a short offer and once ended a drain with points unspent. Keeps the best attempt so a genuinely
     * short list still proceeds.
     */
    private fun readLessonListSettled(): LessonList? {
        var best: LessonList? = null
        for (attempt in 1..LESSON_LIST_READ_ATTEMPTS) {
            val list = lessonReader.readLessonList(game.imageUtils.getSourceBitmap())
            if (list != null && (best == null || list.cards.count { it.readable } > best!!.cards.count { it.readable })) {
                best = list
            }
            if (best?.complete == true) break
            if (attempt < LESSON_LIST_READ_ATTEMPTS) {
                MessageLog.i(
                    TAG,
                    "[GRAND_CONCERT] [LESSON_READ] Offer incomplete on read $attempt " +
                        "(${best?.cards?.count { it.readable } ?: 0}/3 cards readable); re-reading after a settle wait.",
                )
                game.wait(1.0)
            }
        }
        best?.let { rememberLessonState(it) }
        return best
    }

    /** Remembers the scorer's point-steering inputs from a settled read. Reads only; changes no purchase decision. */
    private fun rememberLessonState(list: LessonList) {
        val readableUnscheduled = list.cards.filter { it.readable && it.scheduled != true }
        val song = readableUnscheduled.filter { it.kind == LessonCardKind.SONG }.minByOrNull { it.cost.total() ?: Int.MAX_VALUE }
        lastOfferHadSong = song != null
        if (song != null) {
            lastSongTargetCost = song.cost
            lastSongTargetTitle = song.title
            MessageLog.i(
                TAG,
                "[GRAND_CONCERT] [LESSON_READ] Point-steering target: \"${song.title}\" " +
                    "cost=${PerformancePointType.entries.joinToString("/") { "${it.displayName.take(2)}${song.cost[it]}" }}",
            )
        }
        lastGateTechniqueCost =
            readableUnscheduled.filter { it.kind == LessonCardKind.TECHNIQUE }.minByOrNull { it.cost.total() ?: Int.MAX_VALUE }?.cost
    }

    /**
     * End-of-career Lessons drain: the guarded spend loop in career-complete scoring with stop line 1 (expiring points
     * have no opportunity cost), then back to Complete Career. Returns lessons learned, or [DRAIN_LIST_NEVER_SEEN] so
     * the caller keeps the drain retryable.
     */
    private fun drainLessonsAtCareerComplete(): Int {
        game.tapCoordinate(GrandConcertCareerComplete.LESSONS_X.toDouble(), GrandConcertCareerComplete.LESSONS_Y.toDouble(), "gc_career_complete_lessons")
        game.wait(1.5)
        val list = readLessonListSettled()
        if (list == null) {
            // "Did not open" is inferred from an empty read; a missed tap and an unparseable layout need opposite
            // fixes, so capture the frame to tell them apart.
            val shot =
                try {
                    val filename = "gc_drain_no_lessons_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
                    game.imageUtils.saveBitmap(filename = filename, fullRes = true)
                    "$filename.png"
                } catch (e: Exception) {
                    "unsaved (${e.message})"
                }
            MessageLog.w(
                TAG,
                "[GRAND_CONCERT] [CAREER_COMPLETE] The Lessons list did not open from the Complete Career screen " +
                    "(tapped ${GrandConcertCareerComplete.LESSONS_X}, ${GrandConcertCareerComplete.LESSONS_Y}); " +
                    "the drain stays retryable. Frame: $shot",
            )
            exitLessonShop()
            return DRAIN_LIST_NEVER_SEEN
        }
        val context = LessonScoreContext(careerComplete = true, energyPercent = trainee.energy, statPriority = statPriority)
        lessonReader.logLessonList(list)
        logLessonScores(list, context)
        val spent =
            spendVisit(
                list,
                context,
                MAX_PURCHASES_CAREER_COMPLETE,
                GrandConcertPolicy.SPEND_MIN_SCORE_CAREER_COMPLETE,
            ).purchases
        exitLessonShop()
        return spent
    }

    /**
     * Performance-point balances are omitted: they are only read during training-screen analysis, so surfacing them
     * here would fabricate state.
     */
    override fun scenarioStateSnapshot(): ScenarioState {
        return GrandConcertState(
            songsBoughtThisCycle = songsBoughtThisCycle,
            songsBoughtThisCareer = songsBoughtThisCareer,
            lastConcertBoundary = lastConcertBoundary,
        )
    }

    /**
     * Per-turn Lesson-shop visit. Any read failure, mismatch, or unexpected screen aborts without another tap; the
     * shop's own Back is the way out. Bounded by [MAX_LESSON_VISITS_PER_RUN] and [MAX_PURCHASES_PER_VISIT].
     */
    override fun onBeforeMainScreenUpdate() {
        if (lessonVisitsThisRun >= MAX_LESSON_VISITS_PER_RUN) return
        val visitDay = date.day
        if (visitDay > 1 && visitDay == lastNoBuyVisitDay) return

        val careerBitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> careerBitmap.getPixel(x, y) }
        val slot = grandConcertLessonSlotState(sampler)
        if (slot != LessonSlotState.UNLOCKED && slot != LessonSlotState.UNLOCKED_SCHEDULED) {
            return
        }

        lessonVisitsThisRun++
        MessageLog.i(
            TAG,
            "[GRAND_CONCERT] [LESSON_READ] Lessons button state=$slot; opening shop for visit " +
                "$lessonVisitsThisRun/$MAX_LESSON_VISITS_PER_RUN.",
        )
        game.tapCoordinate(GrandConcertTheme.LESSON_SLOT_X.toDouble(), GrandConcertTheme.LESSON_SLOT_Y.toDouble(), "gc_open_lessons")
        game.wait(1.0)

        val list = readLessonListSettled()
        if (list != null) {
            val context = buildLessonContext()
            lessonReader.logLessonList(list)
            logLessonScores(list, context)
            val outcome = spendVisit(list, context, MAX_PURCHASES_PER_VISIT, GrandConcertPolicy.SPEND_MIN_SCORE)
            // A no-buy or gate-advance-only visit blocks later ticks this turn; a real purchase leaves the door open
            // for the restock. Chaining gate advances would drain points into junk techniques.
            lastNoBuyVisitDay = if (outcome.purchases > outcome.gateAdvances) -1 else visitDay
        } else {
            MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_READ] Lesson list did not open or was unreadable; clawing back to the career screen.")
        }

        exitLessonShop()
    }

    private fun buildLessonContext(): LessonScoreContext {
        val day = date.day
        // day<=1 means the date is not read yet (a real turn 1 cannot reach the shop): score without turn context, not
        // as pre-1st-concert, which inflated a song to 365.
        if (day <= 1) {
            return LessonScoreContext(songsLearnedThisCycle = songsBoughtThisCycle, energyPercent = trainee.energy, statPriority = statPriority)
        }
        // STRICTLY before: the concert turn still belongs to the ending cycle; with <= the counter reset early.
        val boundary = CONCERT_TURNS.lastOrNull { it < day } ?: 0
        if (boundary != lastConcertBoundary) {
            lastConcertBoundary = boundary
            songsBoughtThisCycle = 0
            MessageLog.i(TAG, "[GRAND_CONCERT] [LESSON_BUY] New concert cycle (turn $day); song counter reset.")
        }
        val nextConcert = CONCERT_TURNS.firstOrNull { it > day }
        val segment =
            when {
                day <= 24 -> ConcertSegment.BEFORE_PROMO_1
                day <= 36 -> ConcertSegment.BEFORE_PROMO_2
                day <= 48 -> ConcertSegment.BEFORE_PROMO_3
                day <= 60 -> ConcertSegment.BEFORE_PROMO_4
                else -> ConcertSegment.BEFORE_GRAND
            }
        return LessonScoreContext(
            songsLearnedThisCycle = songsBoughtThisCycle,
            cycleSongTarget = GrandConcertPolicy.songTargetForCycle(CONCERT_TURNS.count { it < day }),
            turnsUntilConcert = nextConcert?.let { it - day },
            turnsAfterNextConcert = nextConcert?.let { (CAREER_END_TURN - it).coerceAtLeast(0) },
            segment = segment,
            energyPercent = trainee.energy,
            statPriority = statPriority,
        )
    }

    /**
     * Point-economy context for the training scorer: caps from concerts performed (200 base, +50 each,
     * research-confirmed, never OCR'd) and the per-type deficit toward the cheapest song. A type with an unreadable
     * balance or cost is omitted so the scorer never biases on a guess.
     */
    override fun grandConcertPointContext(balances: Map<PerformancePointType, Int?>?): GrandConcertPointContext? {
        val lessonContext = buildLessonContext()
        val day = date.day
        if (day <= 1) return null
        val concertsPassed = CONCERT_TURNS.count { it < day }
        val caps = PerformancePointType.entries.associateWith { 200 + 50 * concertsPassed }
        val balancesMap = balances ?: emptyMap()
        val expectedSongsByNow = GrandConcertPolicy.PURCHASED_SONG_TARGETS.take(concertsPassed).sum()

        // The current-song-only demand went blind in technique-gate phases and did not prepare for the next song when
        // behind cadence. The lookahead is one step (cheapest unpurchased song in the stage catalog), only while
        // behind.
        val behindTotal = songsBoughtThisCareer < expectedSongsByNow
        val nextSongCost =
            if (behindTotal) {
                // Exclude the current target while it is on offer (still "unpurchased" until bought) so the lookahead
                // does not double-count it; in a gate phase the stale target may be the next song.
                val excludeCurrent = lastSongTargetTitle.takeIf { lastOfferHadSong }
                GrandConcertSongCatalog.cheapestUnpurchasedInStage(concertsPassed + 1, purchasedSongTitles, excludeCurrent)?.cost
            } else {
                null
            }
        val demand =
            GrandConcertPointDemand.merge(
                currentSongCost = lastSongTargetCost,
                gateTechniqueCost = lastGateTechniqueCost,
                offerHadSong = lastOfferHadSong,
                nextSongCost = nextSongCost,
                balances = balancesMap,
            )

        return GrandConcertPointContext(
            balances = balancesMap,
            caps = caps,
            deficit = demand.deficit,
            songsBoughtThisCycle = songsBoughtThisCycle,
            purchasedFloor = GrandConcertPolicy.songTargetForCycle(concertsPassed),
            turnsUntilConcert = lessonContext.turnsUntilConcert,
            songTargetTitle = lastSongTargetTitle,
            songsBoughtThisCareer = songsBoughtThisCareer,
            // Cadence total a healthy run has by the start of this cycle; behind it, the bias stays armed after the
            // cycle floor is met.
            expectedSongsByNow = expectedSongsByNow,
            currentSongDemand = demand.currentSongDemand,
            gateTechniqueDemand = demand.gateTechniqueDemand,
            nextSongDemand = demand.nextSongDemand,
        )
    }

    /**
     * A fan requirement may yield to a training turn when [GrandConcertFanPolicy] proves enough slack. Fail-closed: the
     * policy proof inputs stay null via [GrandConcertFanPressure.reviewGatedPolicyInputs], so it still resolves to a
     * fail-safe race.
     * Concerts award no fans and the guaranteed race minima (about 398 fans per Junior turn against a 3000 target) are
     * far too weak to permit a defer; activation needs a guaranteed-fan data foundation that does not yet exist. Only
     * the fan arm is eligible; [GC_FAN] logs the snapshot and decision every turn.
     */
    override fun considerFanRaceDeferral(): Boolean {
        if (!racing.hasFanRequirement || racing.hasTrophyRequirement || racing.hasInsufficientGoalRacePtsRequirement) return false
        val concertBehindPace = grandConcertPointContext(null)?.behindPace ?: false

        val snapshot = GrandConcertFanPressure.evaluate(fanFacts, trainee.name, date.day, trainee.fans)

        // Policy proof inputs stay null even when the snapshot knows the deadline; only an independent review may
        // replace them.
        val policyInputs = GrandConcertFanPressure.reviewGatedPolicyInputs(snapshot)
        val decision =
            GrandConcertFanPolicy.decide(
                fanRequirementActive = true,
                turnsUntilDeadline = policyInputs.turnsUntilDeadline,
                racesStillNeeded = policyInputs.racesStillNeeded,
                concertBehindPace = concertBehindPace,
            )
        MessageLog.i(TAG, GrandConcertFanPressure.telemetryLine(snapshot, concertBehindPace, policyInputs, decision))
        return decision == GrandConcertFanPolicy.FanRaceDecision.DEFER_TO_TRAINING
    }

    /**
     * Fan requirement from the committed route facts, not the dead race_criteria_fans template: the earliest
     * goal-or-gate period at or after this turn, active while its threshold is unmet. No screen reads.
     */
    override fun currentFanRequirementFromScenarioFacts(): GrandConcertFanRequirement.Result =
        GrandConcertFanRequirement.evaluate(fanFacts, trainee.name, date.day, trainee.fans)

    /**
     * Greedy spend loop with a stop rule over an open lesson list. Two concert protections precede the greedy pick:
     * [GrandConcertPolicy.chooseSongFirst] under the three-song floor, and the technique reserve
     * [GrandConcertPolicy.TECH_RESERVE_TOTAL] while a concert remains.
     */
    private fun spendVisit(initialList: LessonList, context: LessonScoreContext, maxPurchases: Int, minScore: Int): SpendVisitOutcome {
        // Pre-concert hold: with the Great Success songs secured and the concert next turn, a purchase is a net loss:
        // an unbought revealed song survives as the next cycle's first credit, a purchase refreshes the trio away, and
        // technique progress resets at the concert.
        if (!context.careerComplete &&
            (context.songsLearnedThisCycle ?: 0) >= GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value &&
            (context.turnsUntilConcert ?: Int.MAX_VALUE) <= 1
        ) {
            MessageLog.i(
                TAG,
                "[GRAND_CONCERT] [LESSON_BUY] Pre-concert hold: ${context.songsLearnedThisCycle} song(s) secured and the concert " +
                    "is next turn, so this visit buys nothing (a revealed song carries across the concert; a purchase would " +
                    "refresh it away).",
            )
            return SpendVisitOutcome(0, 0)
        }
        var purchases = 0
        var gateAdvances = 0
        var list = initialList
        while (purchases < maxPurchases) {
            // A partially readable offer does not veto the visit: affordability is proven per card.
            if (!list.complete) {
                MessageLog.i(
                    TAG,
                    "[GRAND_CONCERT] [LESSON_BUY] Offer partially readable; considering only the readable cards.",
                )
            }
            val report = GrandConcertPolicy.describeLessonOffer(list, HypeTier.UNKNOWN, context)
            val urgent =
                (context.songsLearnedThisCycle ?: 0) < GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value &&
                    (context.turnsUntilConcert ?: Int.MAX_VALUE) <= 5
            val reserveActive = !context.careerComplete && context.turnsUntilConcert != null
            var gateAdvance = false
            val cycleTarget = context.cycleSongTarget ?: GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value
            val songPick = GrandConcertPolicy.chooseSongFirst(report, context.songsLearnedThisCycle, cycleTarget, list.balances.total())
            if (songPick != null) {
                val milestone = (context.songsLearnedThisCycle ?: 0) >= GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value
                MessageLog.i(
                    TAG,
                    "[GRAND_CONCERT] [LESSON_BUY] Song-first: ${context.songsLearnedThisCycle}/$cycleTarget new songs this cycle" +
                        (if (milestone) " (milestone extra; the technique reserve stays intact)" else "") +
                        ", so \"${songPick.title}\" is bought regardless of its score (${songPick.score}).",
                )
            }
            var pick = songPick
            if (pick == null) {
                pick =
                    GrandConcertPolicy.chooseSpend(
                        report,
                        minScore,
                        list.balances.total(),
                        reserveActive,
                        songTargetCost = lastSongTargetCost,
                        balances = list.balances,
                    )
            }
            if (pick == null) {
                pick =
                    GrandConcertPolicy.chooseGateAdvance(
                        report,
                        minScore,
                        urgent,
                        totalBalance = list.balances.total(),
                        reserveActive = reserveActive,
                        songTargetCost = lastSongTargetCost,
                        balances = list.balances,
                    )
                gateAdvance = pick != null
            }
            if (pick == null) {
                MessageLog.i(
                    TAG,
                    "[GRAND_CONCERT] [LESSON_BUY] Stop rule: no affordable offer at or above score $minScore and no gate advance " +
                        "(${report.ranked.joinToString { "\"${it.title}\" s=${it.score} a=${it.affordable}" }}).",
                )
                break
            }
            if (gateAdvance) {
                MessageLog.i(
                    TAG,
                    "[GRAND_CONCERT] [LESSON_BUY] Gate advance: no song offered and nothing clears $minScore, so buying the " +
                        "cheapest technique to keep the song gate moving.",
                )
            }
            val intended = list.cards.first { it.slot == pick.slot }
            if (!attemptLearn(intended, pick.score)) break
            purchases++
            if (gateAdvance) gateAdvances++
            if (intended.kind == LessonCardKind.SONG) {
                songsBoughtThisCycle++
                songsBoughtThisCareer++
                intended.title?.let { purchasedSongTitles.add(it) }
            }

            game.wait(1.2)
            // Re-read through the settle loop: the list repaints top-down after a buy, and a single early read ended a
            // career-end drain after one purchase.
            val next = readLessonListSettled()
            if (next == null) {
                MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_BUY] The list did not return after learning; ending the visit.")
                break
            }
            verifyReceipt(list, intended, next)
            lessonReader.logLessonList(next)
            logLessonScores(next, context)
            list = next
        }
        if (purchases > 0) {
            MessageLog.i(TAG, "[GRAND_CONCERT] [LESSON_BUY] Learned $purchases lesson(s) this visit.")
        }
        return SpendVisitOutcome(purchases, gateAdvances)
    }

    private data class SpendVisitOutcome(val purchases: Int, val gateAdvances: Int)

    /**
     * Tap the card, verify the confirmation dialog names exactly the intended card, and only then tap Learn. Every
     * other outcome cancels, so a mis-tap or mis-read costs at most a Cancel.
     */
    private fun attemptLearn(intended: LessonListCard, score: Int): Boolean {
        MessageLog.i(
            TAG,
            "[GRAND_CONCERT] [LESSON_BUY] Attempting slot ${intended.slot} \"${intended.title}\" (${intended.kind}, score=$score).",
        )
        game.tapCoordinate(540.0, (GrandConcertLessonGeometry.CARD_HEADER_YS[intended.slot] + 120).toDouble(), "gc_lesson_card")
        game.wait(1.0)

        var confirmation = lessonReader.readConfirmation(game.imageUtils.getSourceBitmap())
        if (confirmation == null) {
            game.wait(0.8)
            confirmation = lessonReader.readConfirmation(game.imageUtils.getSourceBitmap())
        }
        if (confirmation == null) {
            MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_BUY] No confirmation dialog appeared; aborting the attempt without further taps.")
            return false
        }
        if (confirmation.isSchedule) {
            MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_BUY] Got the SCHEDULE dialog (card not affordable after all); cancelling.")
            tapCancel()
            return false
        }
        val verdict = confirmation.verifyAgainst(intended)
        if (verdict != LearnVerdict.EXACT_MATCH) {
            MessageLog.w(
                TAG,
                "[GRAND_CONCERT] [LESSON_BUY] Verification $verdict: dialog says \"${confirmation.title}\"/${confirmation.kind}, " +
                    "intended \"${intended.title}\"/${intended.kind}; cancelling.",
            )
            tapCancel()
            return false
        }

        game.tapCoordinate(GrandConcertLessonGeometry.CONFIRM_AFFIRMATIVE_X.toDouble(), GrandConcertLessonGeometry.CONFIRM_AFFIRMATIVE_Y.toDouble(), "gc_lesson_learn")
        game.wait(1.6)
        val after = game.imageUtils.getSourceBitmap()
        val stillUp = grandConcertDialogHeaderPresent(SparkPixelSampler { x, y -> after.getPixel(x, y) })
        if (stillUp) {
            MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_BUY] The dialog is still up after Learn; not tapping again this visit.")
            return false
        }
        MessageLog.i(TAG, "[GRAND_CONCERT] [LESSON_BUY] Learned \"${intended.title}\".")
        return true
    }

    private fun tapCancel() {
        game.tapCoordinate(GrandConcertLessonGeometry.CONFIRM_CANCEL_X.toDouble(), GrandConcertLessonGeometry.CONFIRM_CANCEL_Y.toDouble(), "gc_lesson_cancel")
        game.wait(0.8)
    }

    /**
     * Purchase receipt: where before, cost, and after all read, before minus cost must equal after. A mismatch is
     * logged, never acted on, so OCR noise cannot poison a verified purchase.
     */
    private fun verifyReceipt(before: LessonList, bought: LessonListCard, after: LessonList) {
        for (type in PerformancePointType.entries) {
            val b = before.balances[type] ?: continue
            val c = bought.cost[type] ?: continue
            val a = after.balances[type] ?: continue
            if (b - c != a) {
                MessageLog.w(
                    TAG,
                    "[GRAND_CONCERT] [LESSON_BUY] Receipt mismatch on ${type.displayName}: $b - $c != $a " +
                        "(an OCR misread or an unexpected spend).",
                )
            }
        }
    }

    /**
     * Returns to the career screen from the Lesson list without spending: the list's Back, then generic
     * Back/Cancel/Close (none confirm a learn or schedule).
     */
    private fun exitLessonShop() {
        game.tapCoordinate(GrandConcertLessonGeometry.LIST_BACK_X.toDouble(), GrandConcertLessonGeometry.LIST_BACK_Y.toDouble(), "gc_lesson_back")
        game.wait(1.0)
        if (checkMainScreen()) return
        ButtonCancel.click(game.imageUtils)
        game.wait(0.5)
        ButtonBack.click(game.imageUtils)
        game.wait(0.5)
        ButtonClose.click(game.imageUtils)
        game.wait(0.5)
    }

    private fun logLessonScores(list: LessonList, context: LessonScoreContext) {
        val report = GrandConcertPolicy.describeLessonOffer(list, HypeTier.UNKNOWN, context)
        report.ranked.forEach { line ->
            val costText =
                line.rawCost?.let { c ->
                    PerformancePointType.entries.joinToString(",") { "${it.displayName.take(2)}:${c[it] ?: "?"}" }
                } ?: "unreadable"
            MessageLog.i(
                TAG,
                "[GRAND_CONCERT] [LESSON_SCORE] #${line.slot} \"${line.title}\" ${line.kind} score=${line.score} " +
                    "affordable=${line.affordable ?: "unknown"}${if (line.scheduled) " scheduled" else ""} " +
                    "cost=$costText costTotal=${line.rawCostTotal ?: "?"}",
            )
        }
        report.notes.forEach { MessageLog.i(TAG, "[GRAND_CONCERT] [LESSON_SCORE] note: $it") }
        report.missingEvidence.forEach { MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_SCORE] missing: $it") }
    }

    companion object {
        /**
         * The five concert turns (Junior Late Dec through Senior Late Dec); JP_CONFIRMED spacing corroborated by live
         * careers.
         */
        private val CONCERT_TURNS = listOf(24, 36, 48, 60, 72)

        /** Final career turn for the runway estimate: GameDate runs 1-72 plus the 73-75 finale season. */
        private const val CAREER_END_TURN = 75

        /** How many times a lesson-list read is retried while the trio is still incomplete. */
        private const val LESSON_LIST_READ_ATTEMPTS = 3

        /**
         * Runaway guard on shop visits per run (40 proved too tight live; the per-turn no-buy gate keeps the normal
         * rate near one per turn).
         */
        private const val MAX_LESSON_VISITS_PER_RUN = 120

        /** Per-visit purchase bound for the mid-career loop. */
        private const val MAX_PURCHASES_PER_VISIT = 4

        /**
         * Purchase bound for the career-end drain, sized for the worst observed leftover (227 of one type funds nine
         * cheap techniques); a runaway backstop.
         */
        private const val MAX_PURCHASES_CAREER_COMPLETE = 30

        /**
         * [drainLessonsAtCareerComplete] sentinel: the list never appeared, so the caller must keep the drain
         * retryable.
         */
        private const val DRAIN_LIST_NEVER_SEEN = -1

        /** Escort loop budget: playback plus a handful of result screens fits well inside this. */
        private const val MAX_ESCORT_TICKS = 40

        private const val MAX_PLAYBACK_MENU_TAPS = 3

        /**
         * Escort re-entries per pending concert before handing over: a preserved career blocks the single career slot,
         * so a give-up once left a queue dead for hours.
         */
        private const val MAX_CONCERT_ESCORT_ATTEMPTS = 3

        /** Settle time between escort attempts, so a mid-animation frame is not re-read instantly. */
        private const val CONCERT_ESCORT_RETRY_WAIT = 3.0

        /** Convenience for callers that only have the raw settings string. */
        fun isGrandConcert(scenario: String?): Boolean = GrandConcertScenario.matches(scenario)
    }
}
