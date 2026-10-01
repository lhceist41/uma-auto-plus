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

    /** Card-specific family keys: they may only match their own trainee, see [computeSingleOwnerSpecialFamilyKeys]. */
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
     * The option count is the only evidence separating a one-option card event from the two-option graded common
     * event sharing its title; the verdict is carried out of the selection so the selection stays pure and JVM-testable.
     */
    enum class OptionCountVerdict {
        MATCHED,

        MISMATCHED,

        /** No usable count, and the family's copies disagree on option count: the choice is unproven. */
        UNVERIFIED,

        NOT_APPLICABLE,
    }

    /** The deliberate selection's outcome; [withheldCardEventKey] is a card-specific copy the trainee owns that was refused for lack of a trusted option count. */
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
         * Distinctive substrings of the on-screen title per canonical special event. The Etsuko entries carry their
         * own "Elated" / "Exhaustive" markers: a shared bare "Etsuko" pattern once routed Elated screens into Exhaustive data.
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

        private val GRADE_VARIANT_TOKENS = listOf("(G1)", "(G2/G3)", "(Pre/OP)")

        /** Same threshold as the launch navigator's trainee match: same noise profile (preset names vs an OCR'd header name). */
        private const val OWNER_MATCH_THRESHOLD = 0.86

        private val PROGRESSION_SYMBOL_REGEX = Regex("""\([❯❮]+\)""")

        private val SOURCE_RANK = mapOf("scenario" to 0, "character" to 1, "support" to 2)

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

        fun detectSpecialEvent(ocrResult: String): String? {
            for ((eventName, patterns) in SPECIAL_EVENT_PATTERNS) {
                if (patterns.any { pattern -> ocrResult.contains(pattern) }) return eventName
            }
            return null
        }

        /**
         * Patterns are matched, not the canonical name, because a family key may be decorated ("Victory! (G1)\n1st",
         * "Failed training (Get Well Soon!)", card-specific expansions) and need not carry the full name. Compared on cleaned titles.
         */
        fun isSpecialFamilyKey(eventName: String, specialEventName: String): Boolean {
            val cleanedEventName = cleanTitle(eventName)
            val patterns = SPECIAL_EVENT_PATTERNS[specialEventName] ?: listOf(specialEventName)
            return patterns.any { cleanedEventName.contains(cleanTitle(it)) }
        }

        /** Graded copies of one event collapse onto the same value while distinct events ("Extra Training" vs "Extra Training to Blow Off Steam") stay distinct. */
        fun stripVariantDecorations(eventName: String): String {
            val firstLine = firstTitleLine(eventName)
            for (token in GRADE_VARIANT_TOKENS) {
                if (firstLine.endsWith(token)) return firstLine.removeSuffix(token).trim()
            }
            return firstLine
        }

        fun variantGradeToken(eventName: String): String? {
            val firstLine = firstTitleLine(eventName)
            return GRADE_VARIANT_TOKENS.firstOrNull { firstLine.endsWith(it) }
        }

        private fun firstTitleLine(eventName: String): String =
            eventName
                .replace(PROGRESSION_SYMBOL_REGEX, "")
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim() ?: ""

        /**
         * FINALE and EX read as G1, DEBUT and MAIDEN as Pre/OP. An unknown grade defaults to G1: graded copies differ
         * only in stat and skill amounts, never option structure, so a wrong default is cosmetic.
         */
        fun gradeVariantToken(grade: RaceGrade?): String =
            when (grade) {
                RaceGrade.G2, RaceGrade.G3 -> "(G2/G3)"
                RaceGrade.PRE_OP, RaceGrade.OP, RaceGrade.DEBUT, RaceGrade.MAIDEN -> "(Pre/OP)"
                else -> "(G1)"
            }

        /**
         * Card-specific variants (Gold City's "Victory!" / "Solid Showing" / "Defeat", Maruzensky's "The Road to a Rad
         * Victory!") may only match their owner: otherwise Gold City's 1-option exact-name copies outscore the graded
         * 2-option copies everyone else gets, which forced every configured race-result option back to Option 1.
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

        fun ownerMatchesActiveTrainee(ownerName: String, activeTraineeName: String): Boolean =
            activeTraineeName.isNotEmpty() && TraineeNameMatcher.score(ownerName, activeTraineeName) >= OWNER_MATCH_THRESHOLD

        /**
         * Callers pass a count that already survived acceptStableOptionCount; a count that never settled arrives as null, like
         * one never read: both mean unknown and are no evidence about the screen.
         */
        fun isAuthoritativeOptionCount(visibleOptionCount: Int?): Boolean = visibleOptionCount != null && visibleOptionCount > 0

        /**
         * Permissive about an unknown count on purpose: most card-specific copies are the only candidate for their title, and
         * dropping them would push those events onto unrelated data. [singleOwnerCopyIsCountConfirmed] governs where the copy
         * competes against differently shaped data.
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
         * Affirmative evidence: the trainee owns the copy and a trusted count equals its option count. Gold City's one-option
         * "Victory!" and the two-option graded copy share a title and owner, so an unknown count must not pick the rarer
         * one-option event from identity alone: that clamped a configured Option 2 back to Option 1 on every race she ran.
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

        private data class SpecialEventCandidate(
            val source: String,
            val ownerName: String,
            val eventName: String,
            val rewards: ArrayList<String>,
            val soleOwner: Boolean,
        )

        /**
         * Selects the data copy for a pattern-detected special event: candidates are the family keys minus foreign or
         * count-contradicted card-specific variants ([singleOwnerCopyIsEligible]); the OCR'd title picks the best group of
         * screen-equivalent titles; [chooseWithinGroup] picks the copy. A fuzzy scan cannot reach graded keys from a bare title,
         * and its early-return depends on JSON iteration order. A positive [visibleOptionCount] participates before the key is
         * chosen, never as an after-the-fact clamp. Null when the family has no data (e.g. "Tutorial").
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
                        // A card-specific variant is only real for its own trainee and a matching option count; the rest fall through to the graded common copies.
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

            // The pattern pre-filter pinned the identity, so a family match is trusted even when the key wraps the title ("Get Well Soon!" vs "Failed training (Get Well Soon!)").
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

        private data class GroupChoice(val candidate: SpecialEventCandidate, val verdict: OptionCountVerdict, val withheldCardEventKey: String?)

        /**
         * The on-screen option count runs FIRST: a group can hold copies that share a title but differ in shape (Gold City's
         * one-option "Victory!" vs the two-option graded "Victory! (G1)"); deciding by ownership and repairing the option
         * index afterwards is the defect this order prevents.
         */
        private fun chooseWithinGroup(
            group: List<SpecialEventCandidate>,
            activeTraineeName: String,
            lastRaceGrade: RaceGrade?,
            visibleOptionCount: Int?,
        ): GroupChoice {
            // Step 1: keep copies whose option count matches the screen; if none does, keep the whole group and record the disagreement in the verdict.
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

            // Step 2: ownership may decide only when nothing else in the pool has a different shape, or a trusted count confirms
            // the card copy's option count; otherwise it is withheld (an unknown count must not promote the rare one-option card event).
            val cardCopy = pool.filter { it.soleOwner }.minByOrNull { it.eventName }
            val shapeAmbiguous = pool.distinctBy { it.rewards.size }.size > 1
            val countConfirmsCardCopy = cardCopy != null && authoritative && cardCopy.rewards.size == visibleOptionCount
            if (cardCopy != null && (!shapeAmbiguous || countConfirmsCardCopy)) {
                return GroupChoice(cardCopy, verdict, withheldCardEventKey = null)
            }

            // Drop the losing card copy so later steps cannot hand it back, unless it is all there is.
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

        // A pattern hit pins the event identity, so the copy is selected deliberately: graded keys ("Victory! (G1)\n1st") can
        // never win a fuzzy comparison against a bare title. Not cached: the copy depends on the trainee, last race grade and option count.
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
                // No data key in this family (e.g. "Tutorial"); dedicated branches key off the special event name.
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

                // A card-specific variant needs affirmative evidence here: this path runs on titles the pre-filter could not place, so a
                // garble plus an unreadable screen must not land on the one-option copy. Rejected ones still compete as normal fuzzy
                // candidates; the confidence floor in TrainingEvent catches weak matches.
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
     * Logs only a bad verdict (screen and data disagree, or nothing to check in a shape-ambiguous family); both leave the
     * option index on weaker evidence. Kept out of the selection so it stays pure.
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

        // Independent of the verdict: name the refused card-specific copy so a career that should have taken one is diagnosable.
        selection.withheldCardEventKey?.let { withheld ->
            MessageLog.w(
                TAG,
                "[WARN] findMostSimilarString:: Withheld the card-specific \"${withheld.replace("\n", " ")}\" because no trusted option count backed it ($context). " +
                    "Used \"${selection.eventTitle.replace("\n", " ")}\" with ${selection.eventOptionRewards.size} option(s) instead.",
            )
        }
    }

    /**
     * The trainee's character name: the applied preset trainee (kept in sync for rotation careers, possibly outfit-bearing),
     * else the in-career header read; empty when neither exists, so card-specific variants never match. Shared with
     * [TrainingEvent] so the Acupuncture gate compares the trainee across two screens with one resolution order.
     */
    internal fun resolveActiveTraineeName(): String {
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
