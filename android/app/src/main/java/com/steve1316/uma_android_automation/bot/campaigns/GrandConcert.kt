package com.steve1316.uma_android_automation.bot.campaigns

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.CampaignBreakpointException
import com.steve1316.uma_android_automation.bot.ConcertSegment
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.LessonCardKind
import com.steve1316.uma_android_automation.bot.GrandConcertHandoff
import com.steve1316.uma_android_automation.bot.GrandConcertHandoffReason
import com.steve1316.uma_android_automation.bot.GrandConcertLessonReader
import com.steve1316.uma_android_automation.bot.GrandConcertPointContext
import com.steve1316.uma_android_automation.bot.GrandConcertPolicy
import com.steve1316.uma_android_automation.bot.GrandConcertScenario
import com.steve1316.uma_android_automation.bot.HypeTier
import com.steve1316.uma_android_automation.bot.LearnVerdict
import com.steve1316.uma_android_automation.bot.LessonList
import com.steve1316.uma_android_automation.bot.LessonListCard
import com.steve1316.uma_android_automation.bot.LessonScoreContext
import com.steve1316.uma_android_automation.bot.PerformancePointType
import com.steve1316.uma_android_automation.bot.PerformancePointVector
import com.steve1316.uma_android_automation.components.ButtonBack
import com.steve1316.uma_android_automation.components.ButtonCancel
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.IconRaceDayRibbon
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
import com.steve1316.uma_android_automation.utils.grandConcertPlaybackSkipPresent
import com.steve1316.uma_android_automation.utils.grandConcertResultNextPresent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles the Grand Concert scenario ("Brighter Together Our Grand Concert", community name
 * "Grand Live"), added to Global on 2026-07-22 22:00 UTC.
 *
 * What is automated: everything the shared [Campaign] loop already does. A Grand Concert career
 * uses the same career screen, date band, training menu, racing flow, training events, skill
 * purchasing, and career-end handling as URA Finale, and all of those were verified against
 * launch-night captures of the real screens (Scenario Select through the first career turn).
 * The only shared-layer adjustment the scenario needs is its stat caps, which live in
 * [com.steve1316.uma_android_automation.bot.Training.getScenarioStatCap].
 *
 * What else is automated here: the Lesson shop. Each career turn the campaign opens the shop,
 * reads the trio and balances, scores them with the researched strategy weights
 * ([GrandConcertPolicy]), and buys through a verify-or-cancel gate that taps Learn only when the
 * confirmation dialog names exactly the intended card ([attemptLearn]). On the Complete Career
 * screen it drains the leftover points the same way before opening Skills. Concerts are driven by
 * [runConcertEscort]. From there the ordinary career-end path takes over: the finalize gate
 * approves the Complete Career click and CareerLaunchNavigator walks results, sparks, and the
 * post-run To Home dialog, none of which is scenario-gated, so a full career needs no input
 * (proven end to end 2026-07-25, A+ 14176). The handoffs below are the fallback for a screen this
 * class cannot identify: they preserve the career rather than let a generic Confirm/Next/OK fall
 * through onto it, because a stray tap there can spend points or skip a concert, and neither is
 * recoverable.
 *
 * The handoff boundary is also why this class exists at all rather than reusing [UraFinale]:
 * a scenario whose unknown screens are LIVE needs its unknown-screen response to be "stop and
 * ask the player", not the relaunch ladder that a dead game deserves.
 *
 * @property game The [Game] instance for interacting with the game state.
 */
class GrandConcert(game: Game) : Campaign(game) {
    /** No finale-win banner is claimed: the Grand Concert finale has not been captured, so the
     * career ledger must not record a win/lose signal it cannot actually read. */
    override val capturesFinaleWins: Boolean = false

    /** Set once so the player is told what supervision this scenario needs, without spamming a
     * line every turn. */
    private var announcedSupportLevel = false

    /** Reads the live Lesson list into telemetry. Never taps - navigation stays here in the campaign. */
    private val lessonReader = GrandConcertLessonReader(game)

    /** How many Lesson-shop visits this run has performed, capped by [MAX_LESSON_VISITS_PER_RUN]. */
    private var lessonVisitsThisRun = 0

    /** The last turn whose shop visit found nothing to buy. The offer only changes when the turn
     * advances or a purchase restocks it, so re-opening on later ticks of the same turn only burns
     * the visit budget (observed live: 2-3 opens per turn would exhaust the run cap by mid-career).
     * day<=1 (date not read yet) never blocks, so the catch-up visit after a bot restart always runs. */
    private var lastNoBuyVisitDay = -1

    /** Songs BOUGHT by the spend loop in the current concert cycle. Resets when the turn counter
     * crosses a concert boundary. Blind to songs granted free or bought manually before the bot
     * attached, so it is a floor on the true cycle count, which is the safe direction: the
     * deadline term can only overestimate urgency, never suppress it. */
    private var songsBoughtThisCycle = 0

    /** Songs BOUGHT by the spend loop across the whole career, for the 16/18-song milestone
     * telemetry. Same restart blindness as the cycle counter: a floor on the true total. */
    private var songsBoughtThisCareer = 0

    /** The concert boundary [songsBoughtThisCycle] was last reset at, from [CONCERT_TURNS]. */
    private var lastConcertBoundary = 0

    /** Set after a failed concert escort so the retry is a handoff, never a loop. */
    private var concertEscortFailed = false

    /** The cheapest unscheduled song the shop last offered with a fully readable cost, remembered
     * across turns so the training scorer can steer point income toward its per-type deficit.
     * Refreshed on every settled list read; a trio with no readable song keeps the previous
     * target (the offer does not expire, so the remembered cost stays actionable). */
    private var lastSongTargetCost: PerformancePointVector? = null
    private var lastSongTargetTitle: String? = null

    /** Set once the end-of-career Lessons drain has run, so skill-screen entry retries never
     * repeat it. */
    private var careerEndDrainDone = false

    /**
     * Announces the scenario's support level the first time the campaign runs a turn. Kept
     * cheap and idempotent: this is the only per-run scenario-specific behavior that exists
     * until real Lesson and concert fixtures land.
     */
    fun announceSupportLevelOnce() {
        if (announcedSupportLevel) return
        announcedSupportLevel = true
        MessageLog.w(
            TAG,
            "[GRAND_CONCERT] Experimental supervised support: training, races, events, skills, the Lesson shop " +
                "(with a verify-before-Learn purchase gate), the concerts, and the career-end sequence through to the " +
                "home screen are all automated. A Lesson or concert screen the bot does not recognize stops the run " +
                "safely with the career preserved, so Start can resume it.",
        )
    }

    /**
     * Builds the typed stop for a Grand Concert screen the bot cannot drive. The caller stops
     * the bot with this rather than clicking: the game is alive, so a relaunch would destroy a
     * recoverable situation, and a generic Confirm could spend points the player cannot get back.
     */
    fun handOffToPlayer(reason: GrandConcertHandoffReason, screenNote: String? = null, evidenceScreenshot: String? = null): GrandConcertHandoff {
        val handoff = GrandConcertHandoff(reason, screenNote, evidenceScreenshot)
        MessageLog.w(TAG, "[GRAND_CONCERT] ${handoff.playerMessage()}")
        return handoff
    }

    /**
     * Career-end detection includes the scenario's own Complete Career pixel probe alongside the
     * shared complete_career button template. The two detectors race on the screen's fade-in: the
     * probe reads the banner and icon cells before the button template clears its 0.8 confidence
     * gate (measured 2026-07-26: the template scored 0.963 on the settled frame but missed 1.2s
     * earlier, while the probe hit). When the probe won that race it used to reach the
     * campaign-specific fallback below, which drained lessons and then handed the career to the
     * player with 1132 skill points unspent. Routing both detectors through this check sends
     * every career-end frame down the one good path: drain, careerComplete plan, finalize gate,
     * navigator.
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
     * Campaign-specific screen check, reached once every shared screen check has missed. Owns the
     * concert-pending screen: the escort runs the concert, and any unrecognized state inside that
     * flow ends in a career-preserving handoff.
     *
     * The Complete Career screen is deliberately NOT handled here anymore: [checkEndScreen]
     * recognises it (template or pixel probe) earlier in the main loop, so the shared career-end
     * path owns it end to end. The old fallback here could win the fade-in race against the
     * button template and hand the career off with every skill point unspent (2026-07-26).
     */
    override fun checkCampaignSpecificConditions(): Boolean {
        val bitmap = game.imageUtils.getSourceBitmap()
        val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }

        // The concert-pending screen: run the concert through the escort. Every escort tap is
        // gated on a probe for the exact screen it belongs to, and any unrecognized state ends in
        // the same career-preserving handoff that used to fire immediately.
        if (grandConcertConcertPendingScreenPresent(sampler)) {
            if (concertEscortFailed) {
                val handoff = handOffToPlayer(GrandConcertHandoffReason.CONCERT_NOT_AUTOMATED, "the concert escort already failed once this run; finish it manually")
                throw CampaignBreakpointException(handoff.playerMessage())
            }
            MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Concert-pending screen detected; running the concert escort.")
            if (!runConcertEscort()) {
                concertEscortFailed = true
                val handoff = handOffToPlayer(GrandConcertHandoffReason.CONCERT_NOT_AUTOMATED, "the concert flow reached a screen the escort does not know; finish it manually")
                throw CampaignBreakpointException(handoff.playerMessage())
            }
            return true
        }

        return false
    }

    /**
     * The concert escort: runs one concert from the pending screen back to a screen the main
     * loop can drive. Built from the maintainer's screen-by-screen captures of the 3rd Concert
     * (fixtures concert_confirm / concert_playback / concert_success_banner / concert_overview).
     *
     * The discipline is the same as the lesson spend loop's: every tap is gated on a probe for
     * the exact screen that owns the control, unrecognized frames only wait, and the wait budget
     * ends in false so the caller can hand off with the career preserved. The flow is linear
     * (confirm -> playback -> result screens -> career), with one branch: a Late Dec concert is
     * followed by the next turn's New Year trainee event, which belongs to the MAIN loop's event
     * handler, so meeting a Training Event screen is a successful exit, not an anomaly.
     */
    private fun runConcertEscort(): Boolean {
        // The one number that decides the result tier, logged where the analysis scripts can
        // find it: the 2026-07-26 careers missed Great Success on roughly two of five concerts
        // and the count had to be reconstructed from purchase logs to even see it.
        MessageLog.i(
            TAG,
            "[GRAND_CONCERT] [CONCERT] Entering the concert with $songsBoughtThisCycle new song(s) this cycle " +
                "(Great Success needs ${GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value}; career purchased total " +
                "$songsBoughtThisCareer).",
        )
        game.tap(GrandConcertEscort.CONCERT_BUTTON_X.toDouble(), GrandConcertEscort.CONCERT_BUTTON_Y.toDouble(), "gc_concert_open")
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
        while (ticks++ < MAX_ESCORT_TICKS) {
            val bitmap = game.imageUtils.getSourceBitmap()
            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            when {
                grandConcertPlaybackSkipPresent(sampler) -> {
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Playback detected; skipping the performance.")
                    game.tap(GrandConcertEscort.SKIP_GLYPH_X.toDouble(), GrandConcertEscort.SKIP_GLYPH_Y.toDouble(), "gc_concert_skip")
                    game.wait(2.0)
                }
                grandConcertResultNextPresent(sampler) -> {
                    game.tap(GrandConcertEscort.NEXT_BUTTON_X.toDouble(), GrandConcertEscort.NEXT_BUTTON_Y.toDouble(), "gc_concert_next")
                    game.wait(1.5)
                }
                grandConcertBonusesUpdatedPresent(sampler) -> {
                    // The queued-bonus activation notice that follows a concert. Close DISMISSES
                    // it; Confirm opens the Active Concert Bonuses detail panel (the escort once
                    // confirmed itself onto that panel and had to hand off).
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Bonuses Updated acknowledgment; closing.")
                    game.tap(GrandConcertEscort.BONUSES_CLOSE_X.toDouble(), GrandConcertEscort.BONUSES_CLOSE_Y.toDouble(), "gc_concert_bonuses_close")
                    game.wait(1.2)
                }
                grandConcertActiveBonusesPanelPresent(sampler) -> {
                    // Defensive: the detail panel behind the Bonuses Updated dialog's Confirm.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Active Concert Bonuses panel; closing.")
                    game.tap(GrandConcertEscort.ACTIVE_BONUSES_CLOSE_X.toDouble(), GrandConcertEscort.ACTIVE_BONUSES_CLOSE_Y.toDouble(), "gc_concert_active_bonuses_close")
                    game.wait(1.2)
                }
                grandConcertOnStagePresent(sampler) -> {
                    // The Grand's "ON STAGE!" huddle (observed live at the finale, where it
                    // exhausted the first escort's budget); one tap on the medallion proceeds.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] ON STAGE huddle; tapping to proceed.")
                    game.tap(GrandConcertEscort.ON_STAGE_TAP_X.toDouble(), GrandConcertEscort.ON_STAGE_TAP_Y.toDouble(), "gc_concert_on_stage")
                    game.wait(2.0)
                }
                grandConcertConcertConfirmPresent(sampler) -> {
                    // The start confirmation back mid-flow means an earlier tap was swallowed or
                    // an interstitial bounced the game back to it (seen at the Grand finale).
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
                    // A mandatory race day can land on the very turn a concert ends: Sakura
                    // Bakushin O's CBC Sho followed her turn-60 concert (2026-07-26 23:45), the
                    // career screen swapped its Training layout for the Race Day banner,
                    // checkMainScreen missed that variant, and the escort burned its whole
                    // budget on a perfectly ordinary screen before handing off. The main loop
                    // owns race days, so this is a successful exit exactly like the Training
                    // Event one. The icon check is used directly because the full
                    // checkMandatoryRacePrepScreen can tap Back as a side effect.
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Concert complete; a mandatory race day follows and the main loop owns it.")
                    return true
                }
                else -> game.wait(1.0)
            }
        }
        MessageLog.w(TAG, "[GRAND_CONCERT] [CONCERT] Escort budget exhausted on an unrecognised screen.")
        return false
    }

    /**
     * Drives an open start confirmation to completion. On the Grand finale's variant the
     * cutscene-skip checkbox is checked first (verified by the glyph turning green) so the full
     * cinematic never plays; then Start is tapped and the dialog is confirmed gone before
     * reporting success. Every tap is re-verified because the Grand's heavier dialog swallowed
     * the escort's single fire-and-forget Start tap on the second validation career.
     */
    private fun startConcertFromConfirm(): Boolean {
        for (attempt in 1..4) {
            val bitmap = game.imageUtils.getSourceBitmap()
            val sampler = SparkPixelSampler { x, y -> bitmap.getPixel(x, y) }
            if (!grandConcertConcertConfirmPresent(sampler)) return true
            when (grandConcertCutsceneCheckboxState(sampler)) {
                GrandCutsceneCheckbox.UNCHECKED -> {
                    MessageLog.i(TAG, "[GRAND_CONCERT] [CONCERT] Grand finale confirm: checking the cutscene-skip box.")
                    game.tap(GrandConcertEscort.GRAND_CONFIRM_CHECKBOX_X.toDouble(), GrandConcertEscort.GRAND_CONFIRM_CHECKBOX_Y.toDouble(), "gc_grand_cutscene_skip")
                    game.wait(0.8)
                }
                GrandCutsceneCheckbox.CHECKED, GrandCutsceneCheckbox.ABSENT -> {
                    game.tap(GrandConcertEscort.CONFIRM_START_X.toDouble(), GrandConcertEscort.CONFIRM_START_Y.toDouble(), "gc_concert_start")
                    game.wait(2.0)
                }
            }
        }
        MessageLog.w(TAG, "[GRAND_CONCERT] [CONCERT] The start confirmation did not leave after repeated taps; aborting the escort.")
        return false
    }

    /**
     * GC career-end skill-screen entry. The URA Learn-button template does not exist on the
     * Complete Career screen (observed 2026-07-24: six template misses, and only the
     * unspent-skill-points finalize guard kept the career from completing with 500 SP unspent).
     * On the GC layout this drains the leftover performance points through Lessons once, then
     * opens the skill screen via the screen's own Skills button; on any other layout it falls
     * back to the shared template entry.
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
            // A drain that never saw the list leaves the flag unset: with the probe now part of
            // checkEndScreen, the first entry attempt can run on a fade-in frame where the
            // Lessons tap lands on nothing, and the bounded career-end entry retry in
            // Campaign.process deserves a real second drain instead of a silent skip that
            // expires the leftover points.
            careerEndDrainDone = spent >= 0
            game.wait(1.0)
        }
        MessageLog.i(TAG, "[GRAND_CONCERT] [CAREER_COMPLETE] Opening the skill screen via the Complete Career layout's Skills button.")
        game.tap(GrandConcertCareerComplete.SKILLS_X.toDouble(), GrandConcertCareerComplete.SKILLS_Y.toDouble(), "gc_career_complete_skills")
    }

    /**
     * Reads the open lesson list, re-reading while the trio is incomplete.
     *
     * The list paints top-down, so a read taken too early returns the first card and leaves the
     * other two entirely blank (no title, kind or cost). That is indistinguishable from a genuinely
     * short offer at the scoring layer, and it cost a real career-end drain: the only card the
     * reader saw was an unaffordable song, so the stop rule fired and the run finalized with
     * Da 85 / Pa 23 / Vo 35 / Vi 33 / Co 18 unspent while affordable techniques sat in the two
     * slots it never read (2026-07-25). Keeps the best attempt so a genuinely short list still
     * proceeds after the retries rather than blocking.
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
        best?.let { rememberSongTarget(it) }
        return best
    }

    /** Remembers the cheapest readable, unscheduled song on [list] as the training scorer's
     * point-steering target. Song absent or cost unreadable keeps the previous target. */
    private fun rememberSongTarget(list: LessonList) {
        val song =
            list.cards
                .filter { it.kind == LessonCardKind.SONG && it.scheduled != true && it.cost.fullyKnown }
                .minByOrNull { it.cost.total() ?: Int.MAX_VALUE }
                ?: return
        lastSongTargetCost = song.cost
        lastSongTargetTitle = song.title
        MessageLog.i(
            TAG,
            "[GRAND_CONCERT] [LESSON_READ] Point-steering target: \"${song.title}\" " +
                "cost=${PerformancePointType.entries.joinToString("/") { "${it.displayName.take(2)}${song.cost[it]}" }}",
        )
    }

    /**
     * The end-of-career Lessons drain, entered from the Complete Career screen's own Lessons
     * button. Runs the same guarded spend loop as the mid-career visit, but in career-complete
     * scoring mode (compounding and queued bonuses are residual, deadlines moot) and with the
     * stop line at 1: expiring points have zero opportunity cost, so anything with positive value
     * beats losing them. Always claws back to the Complete Career screen afterwards.
     *
     * Returns the number of lessons learned, or [DRAIN_LIST_NEVER_SEEN] when the list never
     * appeared at all - the caller uses that to keep its drain retryable rather than treating a
     * missed tap as a completed drain.
     */
    private fun drainLessonsAtCareerComplete(): Int {
        game.tap(GrandConcertCareerComplete.LESSONS_X.toDouble(), GrandConcertCareerComplete.LESSONS_Y.toDouble(), "gc_career_complete_lessons")
        game.wait(1.5)
        val list = readLessonListSettled()
        if (list == null) {
            // "Did not open" is an inference from an empty read, and the two causes need opposite
            // fixes: a tap that missed the button, or a list that opened but this layout cannot be
            // parsed. Nothing distinguished them on 2026-07-25, when the drain gave up with 0/3
            // cards readable on all three attempts and 300 points expired unspent. Capture the frame
            // so the next occurrence answers it instead of costing another career's leftovers.
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
        val context = LessonScoreContext(careerComplete = true, energyPercent = trainee.energy)
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
     * Grand Concert per-turn hook: the Lesson-shop visit. When the Lessons button is unlocked on
     * the career screen, open the shop, read the offered list, and run the guarded spend loop:
     * score the trio, and while the best affordable card clears the buy threshold, learn it
     * through [attemptLearn]'s verify-or-cancel gate. Everything is logged per purchase, and any
     * read failure, verification mismatch, or unexpected screen aborts the visit without another
     * tap - the shop's own Back is always the way out.
     *
     * Bounded to [MAX_LESSON_VISITS_PER_RUN] visits per run and [MAX_PURCHASES_PER_VISIT] buys per
     * visit so a misbehaving scorer is bounded by construction.
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
        game.tap(GrandConcertTheme.LESSON_SLOT_X.toDouble(), GrandConcertTheme.LESSON_SLOT_Y.toDouble(), "gc_open_lessons")
        game.wait(1.0)

        val list = readLessonListSettled()
        if (list != null) {
            val context = buildLessonContext()
            lessonReader.logLessonList(list)
            logLessonScores(list, context)
            val outcome = spendVisit(list, context, MAX_PURCHASES_PER_VISIT, GrandConcertPolicy.SPEND_MIN_SCORE)
            // A no-buy or gate-advance-only visit blocks this turn's later ticks; a real (at-or-
            // above-gate) purchase leaves the door open for the restocked offer. Gate advances
            // already forced their restocks inside the visit, and chaining them across ticks
            // would drain points into junk techniques. An unreadable list never blocks: that is
            // a transient to retry.
            lastNoBuyVisitDay = if (outcome.purchases > outcome.gateAdvances) -1 else visitDay
        } else {
            MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_READ] Lesson list did not open or was unreadable; clawing back to the career screen.")
        }

        exitLessonShop()
    }

    /**
     * Builds the scoring context from the turn counter: which concert cycle this is, how close the
     * next concert is (arms the per-cycle deadline term), the post-activation runway for queued
     * concert bonuses, and the current energy. Also rolls the per-cycle song counter when the turn
     * crosses a concert boundary.
     */
    private fun buildLessonContext(): LessonScoreContext {
        val day = date.day
        // GameDate initializes day to 1 and the visit hook runs BEFORE the turn's date read, so
        // the first visit after a bot start sees day=1. A real turn 1 can never reach the shop
        // (Lessons unlocks later), so day<=1 always means "not read yet": score without turn
        // context rather than as pre-1st-concert (which inflated a song to 365 on restart).
        if (day <= 1) {
            return LessonScoreContext(songsLearnedThisCycle = songsBoughtThisCycle, energyPercent = trainee.energy)
        }
        // STRICTLY-before: the concert turn itself still belongs to the ENDING cycle. With <=,
        // the first context built on the concert day (the training recommendation runs before
        // the concert fires) reset the counter early, and the 09:50 validation concert entered
        // logging "0 new song(s)" with 2 actually bought.
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
        )
    }

    /**
     * The training scorer's point-economy context: cycle state from [buildLessonContext] (which
     * also rolls the per-cycle song counter, so a context requested before the cycle's first shop
     * visit still sees a fresh count), caps computed from the concerts already performed (200
     * base, +50 each, research-confirmed on any success tier; never OCR'd), and the per-type
     * deficit toward the remembered cheapest song. A type whose balance or cost is unreadable is
     * omitted from the deficit so the scorer never biases on a guess.
     */
    override fun grandConcertPointContext(balances: Map<PerformancePointType, Int?>?): GrandConcertPointContext? {
        val lessonContext = buildLessonContext()
        val day = date.day
        if (day <= 1) return null
        // Strictly-before, matching buildLessonContext: on the concert day itself the caps have
        // not risen yet and the cycle target is still the ending cycle's.
        val concertsPassed = CONCERT_TURNS.count { it < day }
        val caps = PerformancePointType.entries.associateWith { 200 + 50 * concertsPassed }
        val balancesMap = balances ?: emptyMap()
        val deficit = LinkedHashMap<PerformancePointType, Int>()
        val cost = lastSongTargetCost
        if (cost != null) {
            for (type in PerformancePointType.entries) {
                val c = cost[type] ?: continue
                val b = balancesMap[type] ?: continue
                deficit[type] = (c - b).coerceAtLeast(0)
            }
        }
        return GrandConcertPointContext(
            balances = balancesMap,
            caps = caps,
            deficit = deficit,
            songsBoughtThisCycle = songsBoughtThisCycle,
            // The bias chases the cycle's milestone target (3-4-4-3-3), not just the Great
            // Success floor: wanting a fourth song means wanting the income for it too.
            purchasedFloor = GrandConcertPolicy.songTargetForCycle(concertsPassed),
            turnsUntilConcert = lessonContext.turnsUntilConcert,
            songTargetTitle = lastSongTargetTitle,
        )
    }

    /**
     * The greedy-with-stop-rule spend loop over an open lesson list. Buys the best affordable
     * offer at or above [minScore], re-reads the refreshed trio, and repeats until the stop rule
     * fires, a purchase attempt aborts, or [maxPurchases] is reached.
     *
     * Two concert-protection rules sit ahead of the plain greedy pick (both added 2026-07-26
     * after the songs-per-cycle measurement showed two of five concerts missing Great Success):
     * [GrandConcertPolicy.chooseSongFirst] buys any affordable song while the cycle is below the
     * three-song floor, and [GrandConcertPolicy.chooseSpend]'s technique reserve keeps the point
     * pool from being drained below [GrandConcertPolicy.TECH_RESERVE_TOTAL] while a future
     * concert remains. The technique-only-trio stall the first live run hit is handled by
     * [GrandConcertPolicy.chooseGateAdvance].
     */
    private fun spendVisit(initialList: LessonList, context: LessonScoreContext, maxPurchases: Int, minScore: Int): SpendVisitOutcome {
        // Pre-concert hold: with the cycle's Great Success songs secured and the concert next
        // turn, buying anything is a net loss. A revealed song left unbought survives the concert
        // and counts as the new cycle's first lesson credit ("song-saving", research-confirmed),
        // while any purchase refreshes the whole trio away; and un-revealed technique progress
        // resets at the concert regardless, so a last technique buys nothing that survives.
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
            // A partially readable offer no longer vetoes the visit: the ranking only contains
            // readable cards and affordability must be proven per card, so buying among the
            // readable ones is safe. (The first live run stalled for whole turns because one
            // flaky card row vetoed everything.)
            if (!list.complete) {
                MessageLog.i(
                    TAG,
                    "[GRAND_CONCERT] [LESSON_BUY] Offer partially readable; considering only the readable cards.",
                )
            }
            val report = GrandConcertPolicy.describeLessonOffer(list, HypeTier.UNKNOWN, context)
            // Deadline pressure widens the gate-advance cost cap: with the cycle floor unmet and
            // the concert close, an expensive gate technique beats arriving a song short.
            val urgent =
                (context.songsLearnedThisCycle ?: 0) < GrandConcertPolicy.GREAT_SUCCESS_SONG_FLOOR.value &&
                    (context.turnsUntilConcert ?: Int.MAX_VALUE) <= 5
            // The technique reserve holds whenever a future concert remains and this is not the
            // career-end drain: mid-career techniques must not spend the pool below what the
            // next songs need. Song purchases and gate advances are exempt by design.
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
                pick = GrandConcertPolicy.chooseGateAdvance(report, minScore, urgent)
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
            }

            game.wait(1.2)
            // Re-read through the same settle loop the visit's first read uses. A single read here
            // ended a whole career-end drain after ONE purchase on 2026-07-26, leaving 461 points to
            // expire: the list repaints top-down after a buy, so a read taken too early returns
            // nothing and the visit gave up rather than waiting. That is the exact failure the
            // settle helper was written for, and it was guarding only the way in.
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

    /** Outcome of one shop visit: total purchases, and how many were gate advances (bought below
     * the score gate purely to force a restock). */
    private data class SpendVisitOutcome(val purchases: Int, val gateAdvances: Int)

    /**
     * The transactional Learn: tap the card, read the confirmation dialog, verify it names exactly
     * the intended card, and only then tap Learn. Every other outcome cancels: a Schedule dialog
     * (the card was not actually affordable), an unreadable dialog, or a title/kind mismatch. The
     * one non-negotiable rule is that the affirmative button is tapped only after EXACT_MATCH, so
     * a mis-tap or a mis-read can cost at most a Cancel.
     */
    private fun attemptLearn(intended: LessonListCard, score: Int): Boolean {
        MessageLog.i(
            TAG,
            "[GRAND_CONCERT] [LESSON_BUY] Attempting slot ${intended.slot} \"${intended.title}\" (${intended.kind}, score=$score).",
        )
        game.tap(540.0, (GrandConcertLessonGeometry.CARD_HEADER_YS[intended.slot] + 120).toDouble(), "gc_lesson_card")
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

        game.tap(GrandConcertLessonGeometry.CONFIRM_AFFIRMATIVE_X.toDouble(), GrandConcertLessonGeometry.CONFIRM_AFFIRMATIVE_Y.toDouble(), "gc_lesson_learn")
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
        game.tap(GrandConcertLessonGeometry.CONFIRM_CANCEL_X.toDouble(), GrandConcertLessonGeometry.CONFIRM_CANCEL_Y.toDouble(), "gc_lesson_cancel")
        game.wait(0.8)
    }

    /** The purchase receipt: for every balance where the before, cost, and after values all read,
     * before minus cost must equal after. A mismatch is logged, never acted on - OCR noise on one
     * cell must not poison an otherwise verified purchase. */
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
     * Returns to the career screen from the Lesson list without spending anything. Taps the list's own
     * Back button first, then falls back to the generic Back/Cancel/Close in case a stray tap left a
     * confirmation dialog up - none of Back/Cancel/Close ever confirm a learn or schedule. The base
     * [handleMainScreen] re-checks [checkMainScreen] after this hook and bails safely if we are somehow
     * not back yet, so the main loop always re-converges.
     */
    private fun exitLessonShop() {
        game.tap(GrandConcertLessonGeometry.LIST_BACK_X.toDouble(), GrandConcertLessonGeometry.LIST_BACK_Y.toDouble(), "gc_lesson_back")
        game.wait(1.0)
        if (checkMainScreen()) return
        ButtonCancel.click(game.imageUtils)
        game.wait(0.5)
        ButtonBack.click(game.imageUtils)
        game.wait(0.5)
        ButtonClose.click(game.imageUtils)
        game.wait(0.5)
    }

    /**
     * Logs the strategy scorer's ranking of the offer that was just read, in the SAME context the
     * spend decision uses, so a logged score is the score the picker saw (the first live run's
     * energy technique printed 31 from a blank context while the decision correctly saw 8).
     */
    private fun logLessonScores(list: LessonList, context: LessonScoreContext) {
        val report = GrandConcertPolicy.describeLessonOffer(list, HypeTier.UNKNOWN, context)
        report.ranked.forEach { line ->
            MessageLog.i(
                TAG,
                "[GRAND_CONCERT] [LESSON_SCORE] #${line.slot} \"${line.title}\" ${line.kind} score=${line.score} " +
                    "affordable=${line.affordable ?: "unknown"}${if (line.scheduled) " scheduled" else ""}",
            )
        }
        report.notes.forEach { MessageLog.i(TAG, "[GRAND_CONCERT] [LESSON_SCORE] note: $it") }
        report.missingEvidence.forEach { MessageLog.w(TAG, "[GRAND_CONCERT] [LESSON_SCORE] missing: $it") }
    }

    companion object {
        /** The five concert turns (Junior Late Dec through Senior Late Dec), used to derive the
         * cycle boundaries, the deadline countdown, and the concert segment. JP_CONFIRMED spacing
         * corroborated by this fork's own live careers. */
        private val CONCERT_TURNS = listOf(24, 36, 48, 60, 72)

        /** Final career turn for the queued-bonus runway estimate: GameDate runs 1-72 plus the
         * 73-75 finale season. */
        private const val CAREER_END_TURN = 75

        /** How many times a lesson-list read is retried while the trio is still incomplete. */
        private const val LESSON_LIST_READ_ATTEMPTS = 3

        /** Runaway guard on Lesson-shop visits per run. The per-turn no-buy gate keeps the normal
         * rate near one visit per turn, so a full 75-turn career stays well under this; 40 proved
         * too tight live (2-3 same-turn opens burned it by mid-career, silently starving the
         * late concerts of songs). */
        private const val MAX_LESSON_VISITS_PER_RUN = 120

        /** Per-visit purchase bound for the mid-career spend loop. A turn's point income funds a
         * couple of purchases at most, so anything past this is a scorer misbehaving. */
        private const val MAX_PURCHASES_PER_VISIT = 4

        /** Purchase bound for the end-of-career drain, which legitimately empties the whole pool.
         * Sized for the worst observed leftover (227 of one type funds nine cheap techniques);
         * this is a runaway backstop, not a target - the stop rule ends real drains. */
        private const val MAX_PURCHASES_CAREER_COMPLETE = 30

        /** [drainLessonsAtCareerComplete] sentinel: the Lessons list never appeared, so the drain
         * did not happen at all and the caller must keep it retryable. */
        private const val DRAIN_LIST_NEVER_SEEN = -1

        /** Escort loop budget: playback plus a handful of result screens fits well inside this. */
        private const val MAX_ESCORT_TICKS = 40

        /** Convenience for callers that only have the raw settings string. */
        fun isGrandConcert(scenario: String?): Boolean = GrandConcertScenario.matches(scenario)
    }
}
