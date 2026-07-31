package com.steve1316.uma_android_automation.bot

import android.util.Log
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.types.RaceGrade
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.TraineeNameMatcher
import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Recognizes training events by performing OCR on event titles and matching them against known character and support card event data using string similarity algorithms.
 *
 * @property game The [Game] instance for interacting with the game state.
 * @property imageUtils The [CustomImageUtils] instance for image processing and OCR.
 */
class TrainingEventRecognizer(private val game: Game, private val imageUtils: CustomImageUtils) {
    /** Map of special event matching patterns used to filter false positives during detection. */
    val eventPatterns = SPECIAL_EVENT_PATTERNS

    /** Character event data loaded from SQLite settings. This contains the mapping of character events to their options and rewards. */
    private val characterEventData: JSONObject? =
        try {
            val characterDataString = SettingsHelper.getStringSetting("trainingEvent", "characterEventData")
            if (characterDataString.isNotEmpty()) {
                val jsonObject = JSONObject(characterDataString)
                if (game.debugMode) MessageLog.d(TAG, "[DEBUG] characterEventData:: Data length from SQLite: ${jsonObject.length()}.")
                jsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            if (game.debugMode) MessageLog.d(TAG, "[DEBUG] characterEventData:: Failed to load character event data from SQLite: ${e.message}")
            null
        }

    /** Support event data loaded from SQLite settings. This contains the mapping of support card events to their options and rewards. */
    private val supportEventData: JSONObject? =
        try {
            val supportDataString = SettingsHelper.getStringSetting("trainingEvent", "supportEventData")
            if (supportDataString.isNotEmpty()) {
                val jsonObject = JSONObject(supportDataString)
                if (game.debugMode) MessageLog.d(TAG, "[DEBUG] supportEventData:: Data length from SQLite: ${jsonObject.length()}.")
                jsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            if (game.debugMode) MessageLog.d(TAG, "[DEBUG] supportEventData:: Failed to load support event data from SQLite: ${e.message}")
            null
        }

    /** Scenario event data loaded from SQLite settings. This contains the mapping of scenario-specific events to their options and rewards. */
    private val scenarioEventData: JSONObject? =
        try {
            val scenarioDataString = SettingsHelper.getStringSetting("trainingEvent", "scenarioEventData")
            if (scenarioDataString.isNotEmpty()) {
                val jsonObject = JSONObject(scenarioDataString)
                if (game.debugMode) MessageLog.d(TAG, "[DEBUG] scenarioEventData:: Data length from SQLite: ${jsonObject.length()}.")
                jsonObject
            } else {
                null
            }
        } catch (e: Exception) {
            if (game.debugMode) MessageLog.d(TAG, "[DEBUG] scenarioEventData:: Failed to load scenario event data from SQLite: ${e.message}")
            null
        }

    /**
     * Card-specific variants of special events: family keys owned by exactly one character, like
     * Gold City's "Victory!" or Maruzensky's "The Road to a Rad Victory!". They may only match for
     * their own trainee; see [computeSingleOwnerSpecialFamilyKeys].
     */
    private val singleOwnerSpecialFamilyKeys: Set<String> by lazy { computeSingleOwnerSpecialFamilyKeys(characterEventData) }

    /** Whether to hide OCR comparison results in the log output. */
    private val hideComparisonResults: Boolean = SettingsHelper.getBooleanSetting("trainingEvent", "enableHideOCRComparisonResults")

    /** The minimum confidence score required for an OCR match to be accepted immediately. */
    val minimumConfidence = SettingsHelper.getIntSetting("trainingEvent", "ocrConfidence").toDouble() / 100.0

    /** The grayscale threshold used for OCR pre-processing. */
    private val threshold = SettingsHelper.getIntSetting("debug", "ocrThreshold").toDouble()

    /** Whether to automatically retry OCR detection with different thresholds if confidence is low. */
    private val enableAutomaticRetry = SettingsHelper.getBooleanSetting("trainingEvent", "enableAutomaticOCRRetry")

    /** Service for calculating string similarity using the Jaro-Winkler algorithm. */
    private val stringSimilarityService = StringSimilarityServiceImpl(JaroWinklerStrategy())

    /** Cache used to store OCR matching results and avoid redundant string comparisons. */
    private val ocrMatchingCache = mutableMapOf<String, MatchingResult>()

    /**
     * Store a quadruple of values for training event results.
     *
     * @property first The list of event option rewards.
     * @property second The confidence score of the match.
     * @property third The title of the matched event.
     * @property fourth The name of the character or support card.
     */
    data class Quadruple<out A, out B, out C, out D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * Store the result of finding the most similar string in the event data.
     *
     * @property confidence The similarity score between the OCR result and the event title.
     * @property category The category of the event (either "character" or "support").
     * @property eventTitle The title of the matched event.
     * @property supportCardTitle The name of the support card if it is a support event.
     * @property eventOptionRewards The list of rewards for each option in the event.
     * @property character The name of the character if it is a character event.
     * @property special Whether the result came from the special event selection (pattern-pinned identity).
     */
    private data class MatchingResult(
        val confidence: Double,
        val category: String,
        val eventTitle: String,
        val supportCardTitle: String,
        val eventOptionRewards: ArrayList<String>,
        val character: String,
        val special: Boolean = false,
    )

    /**
     * How the chosen copy's option count relates to what was actually visible on screen.
     *
     * The count is the only evidence that separates a one-option card-specific event from the
     * two-option graded common event that shares its title, so the verdict is carried out of the
     * selection instead of being logged inside it (the selection stays pure and JVM-testable).
     */
    enum class OptionCountVerdict {
        /** A positive on-screen count was available and the chosen copy has exactly that many options. */
        MATCHED,

        /** A positive on-screen count was available and NO copy in the family has that many options. */
        MISMATCHED,

        /** No usable count, and the family's copies disagree on option count, so the choice is unproven. */
        UNVERIFIED,

        /** No usable count was needed: every copy in the family has the same option count. */
        NOT_APPLICABLE,
    }

    /**
     * The outcome of the deliberate special event selection.
     *
     * @property source Which data set supplied the copy: "scenario", "character", or "support".
     * @property ownerName The scenario name, character name, or support card title the copy is attributed to.
     * @property eventTitle The full data key of the chosen copy (grade token and condition line included).
     * @property eventOptionRewards The chosen copy's option rewards.
     * @property confidence The match confidence to report for the selection.
     * @property optionCountVerdict Whether the on-screen option count corroborated the chosen copy.
     * @property withheldCardEventKey A card-specific copy the active trainee owns that was refused
     *   because no trusted option count backed it, or null when nothing was withheld.
     */
    data class SpecialEventSelection(
        val source: String,
        val ownerName: String,
        val eventTitle: String,
        val eventOptionRewards: ArrayList<String>,
        val confidence: Double,
        val optionCountVerdict: OptionCountVerdict,
        val withheldCardEventKey: String? = null,
    )

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]TrainingEventRecognizer"

        /**
         * Map of special event matching patterns used to filter false positives during detection.
         *
         * Keyed by the canonical special event name; the values are distinctive substrings of the
         * on-screen title. Every pattern must stay distinctive: the Etsuko entries carry their own
         * "Elated" / "Exhaustive" markers because a shared bare "Etsuko" pattern once routed Elated
         * screens into the Exhaustive data.
         */
        val SPECIAL_EVENT_PATTERNS: Map<String, List<String>> =
            mapOf(
                "New Year's Resolutions" to listOf("New Year's Resolutions", "Resolutions"),
                "New Year's Shrine Visit" to listOf("New Year's Shrine Visit", "Shrine Visit"),
                "Victory!" to listOf("Victory!"),
                "Solid Showing" to listOf("Solid Showing"),
                "Defeat" to listOf("Defeat"),
                "Get Well Soon!" to listOf("Get Well Soon"),
                "Don't Overdo It!" to listOf("Don't Overdo It"),
                "Extra Training" to listOf("Extra Training"),
                "Acupuncture (Just an Acupuncturist, No Worries! ☆)" to listOf("Acupuncture", "Just an Acupuncturist"),
                "Etsuko's Elated Coverage" to listOf("Elated Coverage", "Elated"),
                "Etsuko's Exhaustive Coverage" to listOf("Exhaustive Coverage", "Exhaustive"),
                "Tutorial" to listOf("Tutorial"),
                "A Team at Last" to listOf("A Team at Last", "Team at Last"),
            )

        /** Grade-variant suffixes the data carries on graded copies of a common event. */
        private val GRADE_VARIANT_TOKENS = listOf("(G1)", "(G2/G3)", "(Pre/OP)")

        /**
         * Acceptance floor for matching a data owner name against the active trainee's name.
         * Mirrors the launch navigator's trainee match threshold: the inputs have the same noise
         * profile (preset names vs an OCR'd header name).
         */
        private const val OWNER_MATCH_THRESHOLD = 0.86

        /** Matches progression symbols like (❯), (❯❯), (❯❯❯) and their variations. */
        private val PROGRESSION_SYMBOL_REGEX = Regex("""\([❯❮]+\)""")

        /** Ranking order for candidate sources within a family group: specific to generic. */
        private val SOURCE_RANK = mapOf("scenario" to 0, "character" to 1, "support" to 2)

        /** Service for calculating string similarity in the special event selection. */
        private val specialSelectionSimilarityService = StringSimilarityServiceImpl(JaroWinklerStrategy())

        /**
         * Standardizes an event title by removing progression symbols, newlines, and whitespaces.
         *
         * @param title The event title to clean.
         * @return The cleaned and standardized event title.
         */
        fun cleanTitle(title: String): String {
            val cleanedProgression = title.replace(PROGRESSION_SYMBOL_REGEX, "")
            return cleanedProgression.replace("\n", "").replace(" ", "").replace("\r", "")
        }

        /**
         * Detects which special event, if any, an OCR'd title belongs to via its distinctive substrings.
         *
         * @param ocrResult The raw OCR'd event title.
         * @return The canonical special event name, or null if no pattern matches.
         */
        fun detectSpecialEvent(ocrResult: String): String? {
            for ((eventName, patterns) in SPECIAL_EVENT_PATTERNS) {
                if (patterns.any { pattern -> ocrResult.contains(pattern) }) return eventName
            }
            return null
        }

        /**
         * Whether a data key belongs to a special event's family: the key carries one of the special
         * event's distinctive patterns, possibly decorated with a grade token ("Victory! (G1)\n1st"),
         * a wrapper ("Failed training (Get Well Soon!)"), or a card-specific expansion ("The Road to
         * a Rad Victory!", "The Applications of Acupuncture"). Patterns are matched instead of the
         * canonical name because a family key need not carry the full name (every pattern is a
         * substring of its canonical name, so this subsumes name containment). Compared on cleaned
         * titles so spacing and newlines cannot break the containment.
         */
        fun isSpecialFamilyKey(eventName: String, specialEventName: String): Boolean {
            val cleanedEventName = cleanTitle(eventName)
            val patterns = SPECIAL_EVENT_PATTERNS[specialEventName] ?: listOf(specialEventName)
            return patterns.any { cleanedEventName.contains(cleanTitle(it)) }
        }

        /**
         * The screen-equivalent title of a data key: progression glyphs and condition lines dropped,
         * trailing grade token stripped. Graded copies of one event collapse onto the same value while
         * distinct events ("Extra Training" vs "Extra Training to Blow Off Steam") stay distinct.
         */
        fun stripVariantDecorations(eventName: String): String {
            val firstLine = firstTitleLine(eventName)
            for (token in GRADE_VARIANT_TOKENS) {
                if (firstLine.endsWith(token)) return firstLine.removeSuffix(token).trim()
            }
            return firstLine
        }

        /** The grade token a data key carries ("(G1)", "(G2/G3)", "(Pre/OP)"), or null when it has none. */
        fun variantGradeToken(eventName: String): String? {
            val firstLine = firstTitleLine(eventName)
            return GRADE_VARIANT_TOKENS.firstOrNull { firstLine.endsWith(it) }
        }

        /** The first non-blank line of a key after progression glyph removal (the on-screen title line). */
        private fun firstTitleLine(eventName: String): String =
            eventName
                .replace(PROGRESSION_SYMBOL_REGEX, "")
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim() ?: ""

        /**
         * Maps the most recent race's grade onto the data's three grade families. FINALE and EX races
         * reward at the top tier so they read as G1; DEBUT and MAIDEN sit in the Pre/OP family. An
         * unknown grade defaults to G1: the graded copies only differ in displayed stat and skill
         * point amounts, never in option structure, so a wrong default is cosmetic.
         */
        fun gradeVariantToken(grade: RaceGrade?): String =
            when (grade) {
                RaceGrade.G2, RaceGrade.G3 -> "(G2/G3)"
                RaceGrade.PRE_OP, RaceGrade.OP, RaceGrade.DEBUT, RaceGrade.MAIDEN -> "(Pre/OP)"
                else -> "(G1)"
            }

        /**
         * Event keys that belong to a special event's family but are owned by exactly one character:
         * card-specific variants such as Gold City's "Victory!" / "Solid Showing" / "Defeat" or
         * Maruzensky's "The Road to a Rad Victory!". These may only match when their owner is the
         * active trainee. Without that restriction, Gold City's 1-option exact-name copies outscore
         * the graded 2-option copies every other trainee actually gets, which silently forced every
         * configured race-result option back to Option 1.
         */
        fun computeSingleOwnerSpecialFamilyKeys(characterEventData: JSONObject?): Set<String> {
            if (characterEventData == null) return emptySet()
            val ownerCounts = mutableMapOf<String, Int>()
            characterEventData.keys().forEach { characterKey ->
                characterEventData.getJSONObject(characterKey).keys().forEach { eventName ->
                    ownerCounts[eventName] = (ownerCounts[eventName] ?: 0) + 1
                }
            }
            return ownerCounts
                .filter { (eventName, count) ->
                    count == 1 && SPECIAL_EVENT_PATTERNS.keys.any { isSpecialFamilyKey(eventName, it) }
                }.keys
        }

        /** Whether a data owner name refers to the active trainee (outfit-aware fuzzy match). */
        fun ownerMatchesActiveTrainee(ownerName: String, activeTraineeName: String): Boolean =
            activeTraineeName.isNotEmpty() && TraineeNameMatcher.score(ownerName, activeTraineeName) >= OWNER_MATCH_THRESHOLD

        /**
         * Whether an on-screen option count is trustworthy enough to decide anything.
         *
         * Callers pass a count that already survived
         * [TrainingEvent.acceptStableOptionCount][TrainingEvent.Companion.acceptStableOptionCount],
         * so anything positive arriving here was observed on two consecutive captures. A count that
         * never settled arrives as null, exactly like a count that was never read: both mean
         * "unknown", and neither is evidence about the shape of the screen.
         */
        fun isAuthoritativeOptionCount(visibleOptionCount: Int?): Boolean = visibleOptionCount != null && visibleOptionCount > 0

        /**
         * Whether a card-specific (single-owner) copy may be considered at all: it must belong to the
         * active trainee, and a trusted count must not contradict its shape.
         *
         * This is deliberately permissive about an unknown count, because most card-specific copies
         * are the only candidate for their on-screen title (Maruzensky's "The Road to a Rad
         * Victory!") and dropping them outright would push those events onto unrelated data. Where
         * the copy actually competes against differently shaped data, the stricter
         * [singleOwnerCopyIsCountConfirmed] governs instead.
         */
        fun singleOwnerCopyIsEligible(
            ownerName: String,
            activeTraineeName: String,
            dataOptionCount: Int,
            visibleOptionCount: Int?,
        ): Boolean {
            if (!ownerMatchesActiveTrainee(ownerName, activeTraineeName)) return false
            return !isAuthoritativeOptionCount(visibleOptionCount) || dataOptionCount == visibleOptionCount
        }

        /**
         * Whether a card-specific copy has AFFIRMATIVE evidence behind it: the active trainee owns it
         * and a trusted count says the screen has exactly as many options as the copy does.
         *
         * Required wherever choosing the card copy means rejecting a differently shaped alternative.
         * Gold City's one-option "Victory!" and the two-option graded common copy are both hers and
         * share a title, so ownership cannot separate them; an absent, zero, or unsettled count is
         * not evidence for the rarer one-option event, and inferring it from her identity alone is
         * what silently clamped a configured Option 2 back to Option 1 on every race she ran.
         */
        fun singleOwnerCopyIsCountConfirmed(
            ownerName: String,
            activeTraineeName: String,
            dataOptionCount: Int,
            visibleOptionCount: Int?,
        ): Boolean =
            ownerMatchesActiveTrainee(ownerName, activeTraineeName) &&
                isAuthoritativeOptionCount(visibleOptionCount) &&
                dataOptionCount == visibleOptionCount

        /** One selectable copy of a special event, with enough context to rank it deterministically. */
        private data class SpecialEventCandidate(
            val source: String,
            val ownerName: String,
            val eventName: String,
            val rewards: ArrayList<String>,
            val soleOwner: Boolean,
        )

        /**
         * Deliberately selects the data copy for a pattern-detected special event.
         *
         * The pattern pre-filter pins the event's identity by distinctive substring, so instead of a
         * fuzzy scan (which cannot reach the graded keys from a bare on-screen title, and whose
         * early-return depends on JSON iteration order) the copy is chosen in three steps:
         * 1. Candidates: every data key in the special event's family, minus card-specific variants
         *    that are not the active trainee's own, and minus card-specific variants whose option
         *    count the screen contradicts (see [singleOwnerCopyIsEligible]).
         * 2. Event: candidates are grouped by screen-equivalent title and the OCR'd title picks the
         *    best-scoring group, so "Extra Training" and "Extra Training to Blow Off Steam" resolve
         *    to the copy actually on screen.
         * 3. Copy: within the group, [chooseWithinGroup] applies the on-screen option count first,
         *    then ownership, then the race grade, then a deterministic tiebreak.
         *
         * [visibleOptionCount] is the number of option rows the caller actually saw on screen, or
         * null when it could not be read; only a positive value is authoritative. It is the sole
         * evidence separating a one-option card event from the two-option graded common event that
         * shares its title, so it participates BEFORE the event key is chosen, never as an
         * after-the-fact clamp.
         *
         * @return The selected copy, or null when the family has no data at all (e.g. "Tutorial").
         */
        fun selectSpecialEvent(
            specialEventName: String,
            ocrTitle: String,
            scenarioEvents: JSONObject?,
            scenarioName: String,
            characterEventData: JSONObject?,
            supportEventData: JSONObject?,
            activeTraineeName: String,
            lastRaceGrade: RaceGrade?,
            visibleOptionCount: Int?,
        ): SpecialEventSelection? {
            val candidates = mutableListOf<SpecialEventCandidate>()

            fun rewardsOf(events: JSONObject, eventName: String): ArrayList<String> {
                val array = events.getJSONArray(eventName)
                val rewards = ArrayList<String>()
                for (i in 0 until array.length()) {
                    rewards.add(array.getString(i))
                }
                return rewards
            }

            scenarioEvents?.keys()?.forEach { eventName ->
                if (isSpecialFamilyKey(eventName, specialEventName)) {
                    candidates.add(SpecialEventCandidate("scenario", scenarioName, eventName, rewardsOf(scenarioEvents, eventName), soleOwner = false))
                }
            }

            if (characterEventData != null) {
                val ownerCounts = mutableMapOf<String, Int>()
                characterEventData.keys().forEach { characterKey ->
                    characterEventData.getJSONObject(characterKey).keys().forEach { eventName ->
                        if (isSpecialFamilyKey(eventName, specialEventName)) {
                            ownerCounts[eventName] = (ownerCounts[eventName] ?: 0) + 1
                        }
                    }
                }
                characterEventData.keys().forEach { characterKey ->
                    val characterEvents = characterEventData.getJSONObject(characterKey)
                    characterEvents.keys().forEach { eventName ->
                        if (!isSpecialFamilyKey(eventName, specialEventName)) return@forEach
                        val rewards = rewardsOf(characterEvents, eventName)
                        val soleOwner = ownerCounts[eventName] == 1
                        // A card-specific variant is only real for its own trainee, and only when the
                        // screen shows the number of options that copy actually has; everyone else,
                        // and every contradicted count, falls through to the graded common copies.
                        if (soleOwner && !singleOwnerCopyIsEligible(characterKey, activeTraineeName, rewards.size, visibleOptionCount)) return@forEach
                        candidates.add(SpecialEventCandidate("character", characterKey, eventName, rewards, soleOwner))
                    }
                }
            }

            supportEventData?.keys()?.forEach { supportName ->
                val supportEvents = supportEventData.getJSONObject(supportName)
                supportEvents.keys().forEach { eventName ->
                    if (isSpecialFamilyKey(eventName, specialEventName)) {
                        candidates.add(SpecialEventCandidate("support", supportName, eventName, rewardsOf(supportEvents, eventName), soleOwner = false))
                    }
                }
            }

            if (candidates.isEmpty()) return null

            val processedOcr = cleanTitle(ocrTitle)
            val groups = candidates.groupBy { cleanTitle(stripVariantDecorations(it.eventName)) }
            val scoredGroups = groups.mapValues { (groupTitle, _) -> specialSelectionSimilarityService.score(processedOcr, groupTitle) }
            val bestGroupTitle =
                scoredGroups.entries
                    .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                    .first()
                    .key
            val chosen = chooseWithinGroup(groups.getValue(bestGroupTitle), activeTraineeName, lastRaceGrade, visibleOptionCount)

            // The pattern pre-filter already pinned the event identity, so a family match is trusted
            // even when the data key wraps the on-screen title (a "Get Well Soon!" screen matches the
            // "Failed training (Get Well Soon!)" key at a low raw string similarity).
            val confidence =
                maxOf(
                    scoredGroups.getValue(bestGroupTitle),
                    specialSelectionSimilarityService.score(processedOcr, cleanTitle(specialEventName)),
                )

            return SpecialEventSelection(
                chosen.candidate.source,
                chosen.candidate.ownerName,
                chosen.candidate.eventName,
                chosen.candidate.rewards,
                confidence,
                chosen.verdict,
                chosen.withheldCardEventKey,
            )
        }

        /**
         * A chosen candidate, what the on-screen option count had to say about it, and the
         * card-specific copy that was refused for lack of count evidence (null when none was).
         */
        private data class GroupChoice(val candidate: SpecialEventCandidate, val verdict: OptionCountVerdict, val withheldCardEventKey: String?)

        /**
         * Picks one copy out of a screen-equivalent title group; see [selectSpecialEvent] step 3.
         *
         * Order matters. The on-screen option count runs FIRST, because a group can hold copies that
         * are the same event by title and different events by shape: Gold City's one-option
         * placement-conditioned "Victory!" sits in the same group as the two-option graded common
         * "Victory! (G1)". Ownership alone cannot separate those two for Gold City herself, and
         * deciding by ownership and repairing the option index afterwards is exactly the defect this
         * ordering exists to prevent.
         */
        private fun chooseWithinGroup(
            group: List<SpecialEventCandidate>,
            activeTraineeName: String,
            lastRaceGrade: RaceGrade?,
            visibleOptionCount: Int?,
        ): GroupChoice {
            // Step 1: what is actually on screen. Only copies whose option count matches survive; if
            // none does, the whole group stays in play and the verdict records that the evidence and
            // the data disagree so the caller can say so.
            val authoritative = isAuthoritativeOptionCount(visibleOptionCount)
            val countMatched = if (authoritative) group.filter { it.rewards.size == visibleOptionCount } else emptyList()
            val pool = if (countMatched.isNotEmpty()) countMatched else group
            val verdict =
                when {
                    authoritative && countMatched.isNotEmpty() -> OptionCountVerdict.MATCHED
                    authoritative -> OptionCountVerdict.MISMATCHED
                    group.distinctBy { it.rewards.size }.size > 1 -> OptionCountVerdict.UNVERIFIED
                    else -> OptionCountVerdict.NOT_APPLICABLE
                }

            // Step 2: ownership, but only where it is allowed to decide. A card-specific copy may
            // replace the common copies when nothing else in the pool has a different shape (the
            // count could not have separated them anyway), or when a trusted count confirms its
            // exact option count. Otherwise it is withheld: an unknown count must never promote the
            // rare one-option card event on trainee identity alone.
            val cardCopy = pool.filter { it.soleOwner }.minByOrNull { it.eventName }
            val shapeAmbiguous = pool.distinctBy { it.rewards.size }.size > 1
            val countConfirmsCardCopy = cardCopy != null && authoritative && cardCopy.rewards.size == visibleOptionCount
            if (cardCopy != null && (!shapeAmbiguous || countConfirmsCardCopy)) {
                return GroupChoice(cardCopy, verdict, withheldCardEventKey = null)
            }

            // The card copy lost its claim; drop it so the later steps cannot hand it back, unless it
            // is all there is.
            val withheldCardEventKey = cardCopy?.eventName
            val remaining = if (cardCopy != null) pool.filterNot { it.soleOwner }.ifEmpty { pool } else pool

            // Step 3: prefer the graded copy matching the race just run; grade-less keys form their own bucket.
            val tokenPreference = (listOf(gradeVariantToken(lastRaceGrade)) + GRADE_VARIANT_TOKENS).distinct()
            val byToken = remaining.groupBy { variantGradeToken(it.eventName) }
            val bucket = tokenPreference.firstNotNullOfOrNull { byToken[it] } ?: byToken[null] ?: remaining

            // Step 4: deterministic tiebreak only.
            val candidate =
                bucket
                    .sortedWith(
                        compareBy<SpecialEventCandidate> { SOURCE_RANK[it.source] ?: Int.MAX_VALUE }
                            .thenByDescending { it.source == "character" && ownerMatchesActiveTrainee(it.ownerName, activeTraineeName) }
                            .thenBy { it.eventName }
                            .thenBy { it.ownerName },
                    ).first()
            return GroupChoice(candidate, verdict, withheldCardEventKey)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Find the most similar string from the event data compared to the OCR result.
     *
     * @param ocrResult The string results from OCR detection.
     * @param visibleOptionCount The number of option rows the caller saw on screen, or null when it
     *   could not be read. Only a positive value is authoritative; see [isAuthoritativeOptionCount].
     * @return A [MatchingResult] containing the best match found, or default values if no match is found.
     */
    private fun findMostSimilarString(ocrResult: String, visibleOptionCount: Int?): MatchingResult {
        MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Now starting process to find most similar string to: $ocrResult")

        val activeTraineeName = resolveActiveTraineeName()
        val lastRaceGrade = (game.task as? Campaign)?.getLastRaceGrade()

        // Filter false positives by checking against special event patterns first. A pattern hit
        // pins the event's identity, so the data copy is selected deliberately instead of by the
        // fuzzy scan below: the graded family keys ("Victory! (G1)\n1st") can never win a fuzzy
        // comparison against a bare on-screen title. Not cached: the right copy depends on the
        // active trainee, the last race's grade, and the on-screen option count, all of which change
        // between events.
        val matchedSpecialEvent = detectSpecialEvent(ocrResult)
        if (matchedSpecialEvent != null) {
            MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Detected special event pattern: $matchedSpecialEvent. Will restrict search to this event's family.")
            val selection =
                selectSpecialEvent(
                    specialEventName = matchedSpecialEvent,
                    ocrTitle = ocrResult,
                    scenarioEvents = scenarioEventData?.optJSONObject(game.scenario),
                    scenarioName = game.scenario,
                    characterEventData = characterEventData,
                    supportEventData = supportEventData,
                    activeTraineeName = activeTraineeName,
                    lastRaceGrade = lastRaceGrade,
                    visibleOptionCount = visibleOptionCount,
                )
            if (selection == null) {
                // No data key belongs to this family (e.g. "Tutorial"); the caller's dedicated
                // branches key off the special event name itself.
                return MatchingResult(0.0, "", matchedSpecialEvent, "", arrayListOf(), "", special = true)
            }
            logOptionCountVerdict(selection, matchedSpecialEvent, ocrResult, activeTraineeName, lastRaceGrade, visibleOptionCount)
            MessageLog.i(
                TAG,
                "[TRAINING_EVENT_RECOGNIZER] Special event resolved to \"${selection.eventTitle.replace("\n", " ")}\" " +
                    "(${selection.source}: ${selection.ownerName}) with confidence ${game.decimalFormat.format(selection.confidence)}.",
            )
            return when (selection.source) {
                "support" -> MatchingResult(selection.confidence, "support", selection.eventTitle, selection.ownerName, selection.eventOptionRewards, "", special = true)
                // Scenario-sourced results keep the legacy shape: category "character" with the scenario name.
                else -> MatchingResult(selection.confidence, "character", selection.eventTitle, "", selection.eventOptionRewards, selection.ownerName, special = true)
            }
        }

        // Check the cache first to avoid redundant similarity calculations.
        ocrMatchingCache[ocrResult]?.let {
            MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Using cached result for: $ocrResult")
            return it
        }

        // Initialize result variables with default values.
        var confidence = 0.0
        var category = ""
        var eventTitle = ""
        var supportCardTitle = ""
        var eventOptionRewards: ArrayList<String> = arrayListOf()
        var character = ""

        // Standardize the OCR result for comparison by removing progression symbols, newlines, and whitespaces.
        val processedResult = cleanTitle(ocrResult)

        // Search for the most similar string within the scenario event data specifically for the current scenario.
        scenarioEventData?.optJSONObject(game.scenario)?.let { scenarioEvents ->
            scenarioEvents.keys().forEach { eventName ->
                val eventOptionsArray = scenarioEvents.getJSONArray(eventName)
                val eventOptions = ArrayList<String>()
                for (i in 0 until eventOptionsArray.length()) {
                    eventOptions.add(eventOptionsArray.getString(i))
                }

                // Calculate similarity score between standardized OCR result and known event name.
                val cleanedEventName = cleanTitle(eventName)
                val score = stringSimilarityService.score(processedResult, cleanedEventName)
                if (!hideComparisonResults) {
                    MessageLog.i(
                        TAG,
                        "[SCENARIO] ${game.scenario} \"${processedResult}\" vs. \"${cleanedEventName}\" (from \"${eventName}\") confidence: ${game.decimalFormat.format(score)}",
                    )
                }

                if (score >= confidence) {
                    confidence = score
                    eventTitle = eventName
                    eventOptionRewards = eventOptions
                    category = "character"
                    character = game.scenario

                    // Return early if we find a match that meets the minimum confidence criteria.
                    if (score >= minimumConfidence) {
                        val result = MatchingResult(confidence, category, eventTitle, supportCardTitle, eventOptionRewards, character)
                        ocrMatchingCache[ocrResult] = result
                        return result
                    }
                }
            }
        }

        // Search for the most similar string within the character event data.
        characterEventData?.keys()?.forEach { characterKey ->
            val characterEvents = characterEventData.getJSONObject(characterKey)
            characterEvents.keys().forEach { eventName ->
                val eventOptionsArray = characterEvents.getJSONArray(eventName)
                val eventOptions = ArrayList<String>()
                for (i in 0 until eventOptionsArray.length()) {
                    eventOptions.add(eventOptionsArray.getString(i))
                }

                // A card-specific variant of a special event (Gold City's "Victory!", Maruzensky's
                // "The Road to a Rad Victory!") needs affirmative evidence here, not merely an
                // uncontradicted guess: this path runs on titles the pattern pre-filter could not
                // place, so a garble plus an unreadable screen must not be enough to land on the
                // one-option copy. Everything rejected here still competes as a normal fuzzy
                // candidate through its owner's other events, and a weak overall match is caught by
                // the confidence floor in TrainingEvent.
                if (eventName in singleOwnerSpecialFamilyKeys &&
                    !singleOwnerCopyIsCountConfirmed(characterKey, activeTraineeName, eventOptions.size, visibleOptionCount)
                ) {
                    return@forEach
                }

                // Calculate similarity score between standardized OCR result and known event name.
                val cleanedEventName = cleanTitle(eventName)
                val score = stringSimilarityService.score(processedResult, cleanedEventName)
                if (!hideComparisonResults) {
                    MessageLog.i(TAG, "[CHARACTER] $characterKey \"${processedResult}\" vs. \"${cleanedEventName}\" (from \"${eventName}\") confidence: ${game.decimalFormat.format(score)}")
                }

                if (score >= confidence) {
                    confidence = score
                    eventTitle = eventName
                    eventOptionRewards = eventOptions
                    category = "character"
                    character = characterKey

                    // Return early if we find a match that meets the minimum confidence criteria.
                    if (score >= minimumConfidence) {
                        val result = MatchingResult(confidence, category, eventTitle, supportCardTitle, eventOptionRewards, character)
                        ocrMatchingCache[ocrResult] = result
                        return result
                    }
                }
            }
        }

        // Search for the most similar string within the support card event data.
        supportEventData?.keys()?.forEach { supportName ->
            val supportEvents = supportEventData.getJSONObject(supportName)
            supportEvents.keys().forEach { eventName ->
                val eventOptionsArray = supportEvents.getJSONArray(eventName)
                val eventOptions = ArrayList<String>()
                for (i in 0 until eventOptionsArray.length()) {
                    eventOptions.add(eventOptionsArray.getString(i))
                }

                // Calculate similarity score between standardized OCR result and known event name.
                val cleanedEventName = cleanTitle(eventName)
                val score = stringSimilarityService.score(processedResult, cleanedEventName)
                if (!hideComparisonResults) {
                    MessageLog.i(TAG, "[SUPPORT] $supportName \"${processedResult}\" vs. \"${cleanedEventName}\" (from \"${eventName}\") confidence: $score")
                }

                if (score >= confidence) {
                    confidence = score
                    eventTitle = eventName
                    supportCardTitle = supportName
                    eventOptionRewards = eventOptions
                    category = "support"

                    // Return early if we find a match that meets the minimum confidence criteria.
                    if (score >= minimumConfidence) {
                        val result = MatchingResult(confidence, category, eventTitle, supportCardTitle, eventOptionRewards, character)
                        ocrMatchingCache[ocrResult] = result
                        return result
                    }
                }
            }
        }

        MessageLog.i(TAG, "${if (!hideComparisonResults) "\n" else ""}[TRAINING_EVENT_RECOGNIZER] Finished process to find similar string.")
        MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Event data fetched for \"${eventTitle}\".")

        // Cache the result before returning.
        val result = MatchingResult(confidence, category, eventTitle, supportCardTitle, eventOptionRewards, character)
        ocrMatchingCache[ocrResult] = result
        return result
    }

    /**
     * Reports what the on-screen option count said about the chosen copy, when it said anything bad.
     *
     * A silent selection is fine when the screen corroborated the pick; the two cases worth a line
     * are "the screen and the data disagree" and "there was nothing to check against and the family
     * is shape-ambiguous", because both mean the option index that follows rests on weaker evidence
     * than usual. Logging lives here rather than inside the selection so the selection stays pure.
     */
    private fun logOptionCountVerdict(
        selection: SpecialEventSelection,
        specialEventName: String,
        ocrResult: String,
        activeTraineeName: String,
        lastRaceGrade: RaceGrade?,
        visibleOptionCount: Int?,
    ) {
        val context =
            "OCR \"${ocrResult.replace("\n", " ")}\", trainee \"${activeTraineeName.ifEmpty { "?" }}\", " +
                "grade ${lastRaceGrade?.name ?: "unknown"}, visible options ${visibleOptionCount ?: "unreadable"}"
        when (selection.optionCountVerdict) {
            OptionCountVerdict.MISMATCHED ->
                MessageLog.w(
                    TAG,
                    "[WARN] findMostSimilarString:: No \"$specialEventName\" copy has $visibleOptionCount option(s) ($context). " +
                        "Using \"${selection.eventTitle.replace("\n", " ")}\" (${selection.ownerName}) with ${selection.eventOptionRewards.size} option(s) instead.",
                )

            OptionCountVerdict.UNVERIFIED ->
                MessageLog.w(
                    TAG,
                    "[WARN] findMostSimilarString:: \"$specialEventName\" copies differ in option count and the screen count is unusable ($context). " +
                        "Chose \"${selection.eventTitle.replace("\n", " ")}\" (${selection.ownerName}) with ${selection.eventOptionRewards.size} option(s) as the safer shape.",
                )

            OptionCountVerdict.MATCHED, OptionCountVerdict.NOT_APPLICABLE -> Unit
        }

        // Independent of the verdict: name the card-specific copy that was refused, so a career that
        // should have taken one is diagnosable instead of just quietly taking the common event.
        selection.withheldCardEventKey?.let { withheld ->
            MessageLog.w(
                TAG,
                "[WARN] findMostSimilarString:: Withheld the card-specific \"${withheld.replace("\n", " ")}\" because no trusted option count backed it ($context). " +
                    "Used \"${selection.eventTitle.replace("\n", " ")}\" with ${selection.eventOptionRewards.size} option(s) instead.",
            )
        }
    }

    /**
     * The character name of the trainee this career is actually playing. Preset applies record it
     * (general.appliedPresetTrainee, kept in sync for rotation careers too, possibly outfit-bearing);
     * the in-career header read is the fallback for careers started without one. Empty when neither
     * is available, in which case card-specific event variants simply never match.
     */
    private fun resolveActiveTraineeName(): String {
        val applied = SettingsHelper.getStringSetting("general", "appliedPresetTrainee").trim()
        if (applied.isNotEmpty()) return applied
        val inCareer = (game.task as? Campaign)?.trainee?.name?.trim() ?: ""
        // Trainee.readName writes the literal "null" when the reference point is missing.
        return if (inCareer.equals("null", ignoreCase = true)) "" else inCareer
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Start the training event recognition process.
     *
     * This method performs OCR on the event title and matches it against known event data. If the confidence is low and automatic retry is enabled, it increments the threshold and retries.
     *
     * @param visibleOptionCount The number of option rows the caller counted on the event screen, or
     *   null when the scan failed. Passed explicitly (never read from shared state) because it decides
     *   between a one-option card-specific event and the two-option graded common event that shares
     *   its title. Only a positive value is treated as evidence.
     * @return A [Quadruple] containing the event option rewards, confidence score, event title, and character/support name.
     */
    fun start(visibleOptionCount: Int?): Quadruple<ArrayList<String>, Double, String, String> {
        // Initialize the best result found with default values.
        var bestResult = MatchingResult(0.0, "", "", "", arrayListOf(), "")

        var increment = 0.0

        val startTime: Long = System.currentTimeMillis()
        while (true) {
            // Perform Tesseract OCR detection on the event title region.
            val ocrResult: String =
                if ((255.0 - threshold - increment) > 0.0) {
                    imageUtils.findEventTitle(increment)
                } else {
                    break
                }

            if (ocrResult.isNotEmpty() && ocrResult != "") {
                // Attempt to find the most similar string compared to the OCR result.
                val matchingResult = findMostSimilarString(ocrResult, visibleOptionCount)
                if (matchingResult.special) {
                    MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Special event \"${matchingResult.eventTitle.replace("\n", " ")}\" detected.")
                    bestResult = matchingResult
                    break
                }

                // Update the best result if the current matching result has higher confidence.
                if (matchingResult.confidence >= bestResult.confidence) {
                    bestResult = matchingResult
                }

                // Log the result of the recognition attempt.
                when (matchingResult.category) {
                    "character" -> {
                        MessageLog.i(
                            TAG,
                            "\n[TRAINING_EVENT_RECOGNIZER] Character ${matchingResult.character} Event Name = ${matchingResult.eventTitle} with confidence = ${
                                game.decimalFormat.format(
                                    matchingResult.confidence,
                                )
                            }",
                        )
                    }

                    "support" -> {
                        MessageLog.i(
                            TAG,
                            "\n[TRAINING_EVENT_RECOGNIZER] Support ${matchingResult.supportCardTitle} Event Name = ${matchingResult.eventTitle} with confidence = ${
                                game.decimalFormat.format(matchingResult.confidence)
                            }",
                        )
                    }
                }

                if (enableAutomaticRetry && !hideComparisonResults) {
                    MessageLog.i(TAG, "\n[TRAINING_EVENT_RECOGNIZER] Threshold incremented by $increment")
                }

                // Round the confidence score to two decimal places for comparison.
                val roundedConfidence = (matchingResult.confidence * 100.0).roundToInt() / 100.0
                if (roundedConfidence < minimumConfidence && enableAutomaticRetry) {
                    // Increment the threshold and retry detection if confidence is below the minimum.
                    increment += 5.0
                } else {
                    break
                }
            } else {
                // Increment the threshold and retry detection if no OCR result was found.
                increment += 5.0
            }
        }

        // Debug build or Debug Mode: capture event screens the matcher could not confidently place, for the replay corpus.
        if ((com.steve1316.uma_android_automation.BuildConfig.DEBUG || game.debugMode) && bestResult.confidence < minimumConfidence) {
            imageUtils.saveFixture(
                "event_lowconf",
                null,
                mapOf(
                    "scenario" to game.scenario,
                    "bestMatch" to bestResult.eventTitle,
                    "category" to bestResult.category,
                    "score" to bestResult.confidence,
                    "threshold" to minimumConfidence,
                    "visibleOptions" to (visibleOptionCount ?: -1),
                    "character" to bestResult.character,
                    "support" to bestResult.supportCardTitle,
                ),
            )
        }

        val endTime: Long = System.currentTimeMillis()
        Log.d(TAG, "[DEBUG] recognizeTrainingEvent:: Total Runtime for recognizing training event: ${endTime - startTime}ms")

        // Determine the name of the character or support card associated with the best result.
        val characterOrSupportName =
            when (bestResult.category) {
                "character" -> bestResult.character
                "support" -> bestResult.supportCardTitle
                else -> ""
            }

        return Quadruple(bestResult.eventOptionRewards, bestResult.confidence, bestResult.eventTitle, characterOrSupportName)
    }
}
