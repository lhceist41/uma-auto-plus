package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.components.ButtonClose
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.IconTrainingEventHorseshoe
import com.steve1316.uma_android_automation.types.Mood
import com.steve1316.uma_android_automation.types.NegativeStatus
import com.steve1316.uma_android_automation.types.PositiveStatus
import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl
import org.json.JSONObject
import org.opencv.core.Point

/**
 * This class is responsible for detecting, analyzing, and responding to Training Events.
 *
 * @property game The [Game] instance for interacting with the game state.
 * @property campaign The [Campaign] instance for accessing campaign-specific data.
 */
class TrainingEvent(private val game: Game, private val campaign: Campaign) {
    /** Recognizer used to perform OCR and string matching for Training Events. */
    private val trainingEventRecognizer: TrainingEventRecognizer = TrainingEventRecognizer(game, game.imageUtils)

    /** Whether to prioritize options that provide energy gains. */
    private val enablePrioritizeEnergyOptions: Boolean = SettingsHelper.getBooleanSetting("trainingEvent", "enablePrioritizeEnergyOptions")

    /** Special event overrides loaded from SQLite settings. */
    private val specialEventOverrides: Map<String, EventOverride> =
        try {
            val overridesString = SettingsHelper.getStringSetting("trainingEvent", "specialEventOverrides")
            if (overridesString.isNotEmpty()) {
                val jsonObject = JSONObject(overridesString)
                val overridesMap = mutableMapOf<String, EventOverride>()
                jsonObject.keys().forEach { eventName ->
                    val eventData = jsonObject.getJSONObject(eventName)
                    overridesMap[eventName] =
                        EventOverride(
                            selectedOption = eventData.getString("selectedOption"),
                            requiresConfirmation = eventData.getBoolean("requiresConfirmation"),
                        )
                }
                overridesMap
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] specialEventOverrides:: Could not parse special event overrides: ${e.message}")
            emptyMap()
        }

    /** Character event overrides loaded from SQLite settings. */
    private val characterEventOverrides: Map<String, Int> =
        try {
            val overridesString = SettingsHelper.getStringSetting("trainingEvent", "characterEventOverrides")
            if (overridesString.isNotEmpty()) {
                val jsonObject = JSONObject(overridesString)
                val overridesMap = mutableMapOf<String, Int>()
                jsonObject.keys().forEach { eventKey ->
                    overridesMap[eventKey] = jsonObject.getInt(eventKey)
                }
                overridesMap
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] characterEventOverrides:: Could not parse character event overrides: ${e.message}")
            emptyMap()
        }

    /** Support event overrides loaded from SQLite settings. */
    private val supportEventOverrides: Map<String, Int> =
        try {
            val overridesString = SettingsHelper.getStringSetting("trainingEvent", "supportEventOverrides")
            if (overridesString.isNotEmpty()) {
                val jsonObject = JSONObject(overridesString)
                val overridesMap = mutableMapOf<String, Int>()
                jsonObject.keys().forEach { eventKey ->
                    overridesMap[eventKey] = jsonObject.getInt(eventKey)
                }
                overridesMap
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] supportEventOverrides:: Could not parse support event overrides: ${e.message}")
            emptyMap()
        }

    /**
     * Skill-hint reward lines that should always win an event option choice over any alternative.
     *
     * Per the Trackblazer guide, Nimble Navigator and Uma Stan are the two scenario-event skill hints
     * to always grab regardless of the other option's stat payoff. Lowercased so a single `.contains(...)`
     * check against a lowercased reward line suffices.
     */
    private val priorityScenarioSkillHints: List<String> =
        listOf(
            "nimble navigator",
            "uma stan",
        )

    /** Scenario event overrides loaded from SQLite settings. */
    private val scenarioEventOverrides: Map<String, Int> =
        try {
            val overridesString = SettingsHelper.getStringSetting("trainingEvent", "scenarioEventOverrides")
            if (overridesString.isNotEmpty()) {
                val jsonObject = JSONObject(overridesString)
                val overridesMap = mutableMapOf<String, Int>()
                jsonObject.keys().forEach { eventKey ->
                    overridesMap[eventKey] = jsonObject.getInt(eventKey)
                }
                overridesMap
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] scenarioEventOverrides:: Could not parse scenario event overrides: ${e.message}")
            emptyMap()
        }

    /**
     * Store the override settings for a special Training Event.
     *
     * @property selectedOption The name of the option to select.
     * @property requiresConfirmation Whether the selection requires a confirmation dialog.
     */
    data class EventOverride(val selectedOption: String, val requiresConfirmation: Boolean)

    /**
     * A trusted option-row count and the capture that produced it.
     *
     * @property count The number of option rows, observed twice in a row.
     * @property acceptedAtIndex Index into the observation sequence of the confirming capture, so
     *   the caller can reuse that capture's coordinates rather than an earlier partial one.
     */
    data class StableOptionCount(val count: Int, val acceptedAtIndex: Int)

    /** What to do with the option rows currently in hand when it is time to tap. */
    enum class OptionTapAction {
        /** The selected option exists in the current list; tap that row. */
        USE_ROW,

        /** The list cannot serve the selected option; refresh it and re-plan. */
        RESCAN,

        /** Still short after a refresh: tap the last row that exists and report the shortfall. */
        CLAMP_TO_LAST_ROW,

        /** No rows at all after a refresh; the caller falls back to a single retrying search. */
        NO_ROWS_AVAILABLE,
    }

    /** [OptionTapAction] plus the row index it applies to (-1 when there is no row to use). */
    data class OptionTapPlan(val action: OptionTapAction, val rowIndex: Int)

    /**
     * Which option a special event ends up selecting.
     *
     * @property optionIndex The 0-based index to act on.
     * @property usedCharacterOverride Whether a per-trainee override supplied the index.
     * @property clamped Whether the requested index did not exist on the matched event's data.
     */
    data class SpecialOptionDecision(val optionIndex: Int, val usedCharacterOverride: Boolean, val clamped: Boolean)

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]TrainingEvent"

        /** How many captures the option-row read may take before giving up on a stable answer. */
        const val OPTION_ROW_MAX_CAPTURES = 4

        /** Seconds between option-row captures. Four captures therefore add at most ~0.6s of waiting. */
        const val OPTION_ROW_CAPTURE_INTERVAL = 0.2

        /**
         * Decides when a sequence of option-row observations may be trusted.
         *
         * Horseshoe matching runs on a single capture with no retries, and a partially rendered event
         * screen genuinely reports fewer rows than it ends up having (the Unity Cup tutorial crashed
         * on exactly that). The two directions of that error are not symmetric, so the rules are not
         * either:
         *
         * - **Two or more rows** may be accepted as soon as two consecutive captures agree on the
         *   count, provided no earlier capture saw MORE. Rows appear as a screen draws, they do not
         *   vanish, so the largest count seen so far is a lower bound on the event's real shape and a
         *   later smaller count is a dropped read rather than a new truth.
         * - **One row** is only ever accepted when the whole capture budget has been spent and every
         *   capture in it saw exactly one row. "One" is the reading a half-drawn two-option screen
         *   produces, and it is also the reading that hands a normal race result to a one-option
         *   card-specific event, so it demands the strongest evidence available: a count that never
         *   moved across the entire observation window. Two early ones prove nothing, which is why
         *   `[1,1,2,2]` settles on 2 and `[1,1,1,2]` settles on nothing at all.
         *
         * Zero never qualifies under either rule: an empty read is a failed capture, not a shape, so
         * a window that starts with one cannot certify a one-option event.
         *
         * @param observations Row counts in capture order.
         * @param requiredObservationCount How many captures the caller's budget allows; a count of one
         *   is accepted only on a full window of that size.
         * @return the accepted count with the index of the capture that confirmed it, or null when the
         *   sequence proves nothing.
         */
        fun acceptStableOptionCount(observations: List<Int>, requiredObservationCount: Int = OPTION_ROW_MAX_CAPTURES): StableOptionCount? {
            // Counts of two or more: two consecutive agreeing captures, never below an earlier peak.
            var highestSeen = 0
            for (index in observations.indices) {
                val current = observations[index]
                if (index > 0 && current >= 2 && current == observations[index - 1] && current >= highestSeen) {
                    return StableOptionCount(current, index)
                }
                if (current > highestSeen) highestSeen = current
            }

            // A single row: only on a complete window that never showed anything else.
            if (observations.size >= requiredObservationCount && observations.all { it == 1 }) {
                return StableOptionCount(1, observations.size - 1)
            }

            return null
        }

        /**
         * Plans which on-screen row to tap for [selectedIndex] given [availableRows] currently known.
         *
         * The row list is read before OCR, so by tap time it can be short: rows that were still
         * rendering then may have appeared since. Silently tapping row 0 in that case throws away
         * the option the settings asked for and looks identical to success in the log, so a short
         * list asks for one refresh first, and a still-short list is reported rather than hidden.
         *
         * @param rescanned Whether the list has already been refreshed once for this event.
         */
        fun planOptionTap(selectedIndex: Int, availableRows: Int, rescanned: Boolean): OptionTapPlan =
            when {
                selectedIndex in 0 until availableRows -> OptionTapPlan(OptionTapAction.USE_ROW, selectedIndex)
                !rescanned -> OptionTapPlan(OptionTapAction.RESCAN, selectedIndex)
                availableRows > 0 -> OptionTapPlan(OptionTapAction.CLAMP_TO_LAST_ROW, availableRows - 1)
                else -> OptionTapPlan(OptionTapAction.NO_ROWS_AVAILABLE, -1)
            }

        /**
         * Whether a recognized event match is trustworthy enough to act on its option rewards.
         * Returns false (caller falls back to the safe first-option default) when there are no
         * rewards, or when a non-special match scored below the recognizer's confidence floor.
         * Special events bypass the floor: they're matched by distinctive substrings, not fuzzy score.
         */
        fun shouldActOnEventMatch(
            eventRewards: List<String>,
            specialEventHandled: Boolean,
            confidence: Double,
            minimumConfidence: Double,
        ): Boolean {
            if (eventRewards.isEmpty() || eventRewards[0] == "") return false
            return specialEventHandled || confidence >= minimumConfidence
        }

        /**
         * Parses the 0-based option index out of a special override's selected option string
         * ("Option 5: Energy +10" -> 4). "Default" is the first option. Returns null when the
         * string carries no option number.
         */
        fun parseSpecialOverrideOptionIndex(selectedOption: String): Int? {
            if (selectedOption == "Default") return 0
            val optionMatch = Regex("Option (\\d+)").find(selectedOption) ?: return null
            return optionMatch.groupValues[1].toInt() - 1
        }

        /**
         * Resolves the option index for a special event from the two override sources and the matched
         * event's own option count.
         *
         * Pure so the index path can be tested directly: a passing selection test proves the right
         * data was chosen, but only this proves the configured option survives to the tap. Clamping
         * here is a last-resort repair for data that disagrees with the settings, NOT the mechanism
         * that picks between a one-option and a two-option copy of an event; that decision belongs to
         * [TrainingEventRecognizer.selectSpecialEvent] and is made from the on-screen option count
         * before this runs.
         *
         * @param eventOptionCount How many options the matched event data carries; 0 when unknown,
         *   in which case the requested index passes through untouched.
         */
        fun decideSpecialEventOption(specialOverrideIndex: Int, characterOverrideIndex: Int?, eventOptionCount: Int): SpecialOptionDecision {
            val requested = characterOverrideIndex ?: specialOverrideIndex
            val usedCharacterOverride = characterOverrideIndex != null
            val resolved =
                when {
                    requested < 0 -> 0
                    eventOptionCount > 0 && requested >= eventOptionCount -> eventOptionCount - 1
                    else -> requested
                }
            return SpecialOptionDecision(resolved, usedCharacterOverride, clamped = resolved != requested)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Check if the given event title matches any special event overrides.
     *
     * @param eventTitle The detected event title from OCR.
     * @return A [Pair] containing the option index (0-based) and whether confirmation is required, or null if no override is found.
     */
    private fun checkSpecialEventOverride(eventTitle: String): Pair<Int, Boolean>? {
        for ((eventName, patterns) in trainingEventRecognizer.eventPatterns) {
            val override = specialEventOverrides[eventName]
            if (override != null) {
                // Check if any pattern matches the event title.
                val matches = patterns.any { pattern -> eventTitle.contains(pattern) }
                if (matches) {
                    MessageLog.v(TAG, "[TRAINING_EVENT] Detected special event: $eventName")

                    // Parse the option number from the setting (e.g., "Option 5: Energy +10" -> 5).
                    val optionIndex = parseSpecialOverrideOptionIndex(override.selectedOption)
                    if (optionIndex == null) {
                        MessageLog.w(TAG, "[WARN] checkSpecialEventOverride:: Could not parse option number from setting: ${override.selectedOption}. Using option 1 by default.")
                        return Pair(0, override.requiresConfirmation)
                    }
                    if (override.selectedOption == "Default") {
                        MessageLog.v(TAG, "[TRAINING_EVENT] Selecting Option 1 according to special event override.")
                    } else {
                        MessageLog.v(TAG, "[TRAINING_EVENT] Using setting: ${override.selectedOption} (Option ${optionIndex + 1})")
                    }

                    return Pair(optionIndex, override.requiresConfirmation)
                }
            }
        }

        return null
    }

    /**
     * Check if the given character event matches any character event overrides.
     *
     * @param characterName The detected character name.
     * @param eventTitle The detected event title from OCR.
     * @return The 0-based option index if an override is found, otherwise null.
     */
    private fun checkCharacterEventOverride(characterName: String, eventTitle: String): Int? {
        if (characterName.isEmpty()) return null

        val eventKey = "$characterName|$eventTitle"
        val override = characterEventOverrides[eventKey]
        if (override != null) {
            MessageLog.v(TAG, "[TRAINING_EVENT] Detected character event override: $eventKey -> Option ${override + 1}")
            return override
        }

        return null
    }

    /**
     * Check if the given support event matches any support event overrides.
     *
     * @param supportName The detected support card name.
     * @param eventTitle The detected event title from OCR.
     * @return The 0-based option index if an override is found, otherwise null.
     */
    private fun checkSupportEventOverride(supportName: String, eventTitle: String): Int? {
        if (supportName.isEmpty()) return null

        val eventKey = "$supportName|$eventTitle"
        val override = supportEventOverrides[eventKey]
        if (override != null) {
            MessageLog.v(TAG, "[TRAINING_EVENT] Detected support event override: $eventKey -> Option ${override + 1}")
            return override
        }

        return null
    }

    /**
     * Check if the given scenario event matches any scenario event overrides.
     *
     * @param scenarioName The detected scenario name.
     * @param eventTitle The detected event title from OCR.
     * @return The 0-based option index if an override is found, otherwise null.
     */
    private fun checkScenarioEventOverride(scenarioName: String, eventTitle: String): Int? {
        if (scenarioName.isEmpty()) return null

        val eventKey = "$scenarioName|$eventTitle"
        val override = scenarioEventOverrides[eventKey]
        if (override != null) {
            MessageLog.v(TAG, "[TRAINING_EVENT] Detected scenario event override: $eventKey -> Option ${override + 1}")
            return override
        }

        return null
    }

    /**
     * Select the team name for the Unity Cup "A Team at Last" event.
     *
     * This event is unique because it may have between zero and five options. The last option is always the default "Team Carrot", while other options are character suggestions detected via OCR.
     *
     * @param optionLocations The list of detected option locations.
     * @return The 0-based index of the option to select, defaulting to 0 if no match is found.
     */
    private fun selectUnityCupTeamNameEvent(optionLocations: ArrayList<Point>): Int {
        val numOptions = optionLocations.size
        MessageLog.v(TAG, "[TRAINING_EVENT] Handling \"A Team at Last\" event with $numOptions option(s).")

        // If zero or one options are detected, return the first option index (auto-completed or single option).
        if (numOptions <= 1) {
            MessageLog.v(TAG, "[TRAINING_EVENT] Event has $numOptions option(s). Selecting first/only option.")
            return 0
        }

        // Retrieve the user preference for team name from settings.
        val override = specialEventOverrides["A Team at Last"]
        val selectedPreference = override?.selectedOption ?: "Default"
        MessageLog.i(TAG, "[TRAINING_EVENT] User preference for team name: $selectedPreference")

        // Return the first option index if the user preference is "Default".
        if (selectedPreference == "Default") {
            MessageLog.v(TAG, "[TRAINING_EVENT] Using default preference, selecting first option.")
            return 0
        }

        // Return the last option index if the user preference is "Team Carrot (Last Option)".
        if (selectedPreference == "Team Carrot (Last Option)") {
            MessageLog.v(TAG, "[TRAINING_EVENT] Using Team Carrot preference, selecting last option.")
            return numOptions - 1
        }

        // List possible team name character suggestions (excluding "Team Carrot").
        val teamNameOptions =
            listOf(
                "Happy Hoppers, like Taiki suggested",
                "Sunny Runners, like Fukukitaru suggested",
                "Carrot Pudding, like Urara suggested",
                "Blue Bloom, like Rice Shower suggested",
            )

        // Perform OCR on each option except the last one.
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        val detectedOptions = mutableListOf<Pair<Int, String>>()

        for (i in 0 until numOptions - 1) {
            val optionCenter = optionLocations[i]
            val cropX = game.imageUtils.relX(optionCenter.x, 45)
            val cropY = game.imageUtils.relY(optionCenter.y, -30)
            val cropWidth = 800
            val cropHeight = 55

            val ocrText =
                game.imageUtils.performOCROnRegion(
                    sourceBitmap,
                    cropX,
                    cropY,
                    cropWidth,
                    cropHeight,
                    useThreshold = false,
                    useGrayscale = true,
                    scale = 1.0,
                    ocrEngine = "tesseract",
                    debugName = "selectUnityCupTeamNameEvent_option_${i + 1}",
                )

            MessageLog.i(TAG, "[TRAINING_EVENT] Option ${i + 1} OCR result: \"$ocrText\"")
            if (ocrText.isNotEmpty()) {
                detectedOptions.add(Pair(i, ocrText))
            }
        }

        // Find the best match for the user preference using string similarity.
        var bestMatchIndex = 0
        var bestMatchScore = 0.0

        for ((optionIndex, ocrText) in detectedOptions) {
            for (teamName in teamNameOptions) {
                // Perform exact containment check first.
                if (ocrText.contains(teamName, ignoreCase = true) || teamName.contains(ocrText, ignoreCase = true)) {
                    if (teamName == selectedPreference) {
                        MessageLog.v(TAG, "[TRAINING_EVENT] Found exact match for \"$selectedPreference\" at option ${optionIndex + 1}.")
                        return optionIndex
                    }
                }

                // Check similarity if this team name matches the user preference.
                if (teamName == selectedPreference) {
                    val score = StringSimilarityServiceImpl(JaroWinklerStrategy()).score(ocrText.lowercase(), teamName.lowercase())

                    if (score > bestMatchScore) {
                        bestMatchScore = score
                        bestMatchIndex = optionIndex
                        MessageLog.i(TAG, "[TRAINING_EVENT] Option ${optionIndex + 1} matches preference with score: ${game.decimalFormat.format(score)}")
                    }
                }
            }
        }

        // Return the best matching index if the similarity score is high enough.
        if (bestMatchScore >= 0.8) {
            MessageLog.v(TAG, "[TRAINING_EVENT] Selected option ${bestMatchIndex + 1} based on similarity match (score: ${game.decimalFormat.format(bestMatchScore)}).")
            return bestMatchIndex
        }

        // Fallback to selecting the first option if no suitable match is found.
        MessageLog.v(TAG, "[TRAINING_EVENT] No good match found for preference. Falling back to first option.")
        return 0
    }

    /**
     * Print a formatted summary of the Training Event and the selected option.
     *
     * @param eventTitle The detected event title from OCR.
     * @param ownerName The character or support card name that owns this event.
     * @param eventRewards List of reward strings for each option.
     * @param weights List of calculated weights for each option (can be null for override cases).
     * @param selectedOption The 0-based index of the selected option.
     * @param confidence The OCR matching confidence.
     */
    private fun printEventSummary(eventTitle: String, ownerName: String, eventRewards: ArrayList<String>, weights: List<Int>?, selectedOption: Int, confidence: Double) {
        val sb = StringBuilder()
        sb.appendLine("\n========== Training Event Summary ==========")

        val ownerInfo = if (ownerName.isNotEmpty()) " ($ownerName)" else ""
        val cleanedTitle = eventTitle.replace("\n", " ").replace("\r", "")
        sb.appendLine("Event: \"$cleanedTitle\"$ownerInfo [Confidence: ${game.decimalFormat.format(confidence)}]")
        sb.appendLine("Current Date: ${campaign.date}")
        sb.appendLine("")

        sb.appendLine("Options:")

        eventRewards.forEachIndexed { index, reward ->
            // Create a condensed reward summary by joining truncated lines.
            val rewardLines = reward.split("\n").filter { it.isNotBlank() && !it.startsWith("---") }
            val condensed =
                if (rewardLines.size <= 3) {
                    rewardLines.joinToString(", ")
                } else {
                    rewardLines.take(3).joinToString(", ") + "..."
                }

            val weightInfo = if (weights != null && index < weights.size) " [Weight: ${weights[index]}]" else ""
            val selectionMarker = if (index == selectedOption) " <---- SELECTED" else ""
            sb.appendLine("  Option ${index + 1}$weightInfo: $condensed$selectionMarker")
        }

        sb.appendLine("")
        sb.appendLine("Selected: Option ${selectedOption + 1}")
        sb.appendLine("============================================")
        MessageLog.v(TAG, sb.toString())
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The result of reading the event's option rows.
     *
     * @property trustedCount The row count confirmed by two consecutive captures, or null when the
     *   reads never agreed. Null means "unknown", never "no options".
     * @property locations Coordinates from the confirming capture, or from the last capture taken
     *   when nothing was confirmed.
     * @property observations Every row count seen, for diagnostics.
     */
    private data class OptionRowScan(val trustedCount: Int?, val locations: ArrayList<Point>, val observations: List<Int>)

    /**
     * Reads the event's option rows until two consecutive captures agree, up to
     * [OPTION_ROW_MAX_CAPTURES]. Gathers frames only; the acceptance rule itself lives in the pure
     * [acceptStableOptionCount] so it can be tested against exact capture sequences.
     */
    private fun acquireStableOptionRows(): OptionRowScan {
        val observations = mutableListOf<Int>()
        val frames = mutableListOf<ArrayList<Point>>()
        var accepted: StableOptionCount? = null

        for (attempt in 0 until OPTION_ROW_MAX_CAPTURES) {
            if (attempt > 0) game.wait(OPTION_ROW_CAPTURE_INTERVAL)
            val locations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
            observations.add(locations.size)
            frames.add(locations)
            accepted = acceptStableOptionCount(observations, requiredObservationCount = OPTION_ROW_MAX_CAPTURES)
            if (accepted != null) break
        }

        val trace = observations.joinToString(",")
        return if (accepted != null) {
            MessageLog.v(TAG, "[TRAINING_EVENT] Option rows settled at ${accepted.count} after ${observations.size} capture(s) [$trace].")
            OptionRowScan(accepted.count, frames[accepted.acceptedAtIndex], observations)
        } else {
            // Two or more rows settle as soon as two captures agree, so reaching here means either the
            // reads disagreed or a one-row reading did not hold for the whole window.
            MessageLog.w(
                TAG,
                "[WARN] handleTrainingEvent:: Option row count never settled across ${observations.size} capture(s) [$trace]. " +
                    "Treating it as unknown; card-specific one-option events will not be selected on this event.",
            )
            OptionRowScan(null, frames.lastOrNull() ?: arrayListOf(), observations)
        }
    }

    /**
     * Handle the active Training Event. By default, it will select the first option.
     *
     * This method performs OCR to identify the event and its associated rewards. It then evaluates the options based on user preferences and character specific overrides to select the best possible
     * outcome.
     */
    fun handleTrainingEvent() {
        MessageLog.v(TAG, "\n********************")
        MessageLog.v(TAG, "[TRAINING_EVENT] Starting Training Event process on ${campaign.date}.")

        // Check if the bot is currently at the Main Screen.
        if (campaign.checkMainScreen()) {
            MessageLog.v(TAG, "[TRAINING_EVENT] Bot is at the Main Screen. Ending the Training Event process.")
            MessageLog.v(TAG, "********************")
            return
        }

        // Read the option rows before recognition: the count is recognition input, not just tap
        // geometry, because a one-option card-specific event (Gold City's race results) and the
        // two-option graded common event share an on-screen title and only the number of rows says
        // which one the game is showing. The read is repeated until two captures agree, since one
        // capture of a still-rendering screen can report fewer rows than the screen ends up having.
        // The 0.1s settle wait keeps its original purpose ahead of the first capture.
        game.wait(0.1)
        val optionRowScan = acquireStableOptionRows()
        val trainingOptionLocations: ArrayList<Point> = optionRowScan.locations

        val (eventRewards, confidence, eventTitle, characterOrSupportName) = trainingEventRecognizer.start(optionRowScan.trustedCount)

        val regex = Regex("[a-zA-Z]+")
        var optionSelected = 0
        var specialEventHandled = false
        var isTutorialEvent = false
        var tutorialOptionCount = 0

        // Check for special event overrides first.
        val specialEventResult = checkSpecialEventOverride(eventTitle)

        // Handle Tutorial events by detecting the number of options on screen.
        if (eventTitle == "Tutorial") {
            isTutorialEvent = true
            // Detect the number of event options on the screen. Deliberately its own scan: the
            // Tutorial's 2-vs-5 branch turns on an exact count, and this one is taken after the OCR
            // pass above, giving a freshly opened tutorial panel more time to finish rendering.
            val tutorialOptionLocations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
            tutorialOptionCount = tutorialOptionLocations.size

            MessageLog.v(TAG, "[TRAINING_EVENT] Tutorial event detected for Unity Cup. Found $tutorialOptionCount option(s) on screen.")

            when (tutorialOptionCount) {
                2 -> {
                    // If 2 options detected, select the last one (index 1).
                    optionSelected = 1
                    MessageLog.v(TAG, "[TRAINING_EVENT] Selecting last option (option 2) to dismiss Tutorial.")
                }

                5 -> {
                    optionSelected = 4
                    MessageLog.v(TAG, "[TRAINING_EVENT] Selecting last option (option 5) first, then will select first option to close.")
                }

                else -> {
                    // Default to last option if count doesn't match expected values.
                    optionSelected = if (tutorialOptionCount > 0) tutorialOptionCount - 1 else 0
                    MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Unexpected option count ($tutorialOptionCount). Selecting last option.")
                }
            }

            specialEventHandled = true
        } else if (eventTitle == "A Team at Last") {
            // Handle "A Team at Last" Unity Cup event specially.
            MessageLog.i(TAG, "[TRAINING_EVENT] \"A Team at Last\" event detected for Unity Cup.")
            // Its own scan for the same reason as the Tutorial branch: this event's option count
            // varies from zero to five and the OCR pass above gives the panel time to settle.
            val teamNameOptionLocations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
            optionSelected = selectUnityCupTeamNameEvent(teamNameOptionLocations)
            specialEventHandled = true
        } else if (specialEventResult != null) {
            val (selectedOptionIndex, _) = specialEventResult

            val decision = decideSpecialEventOption(selectedOptionIndex, characterOverrideIndex = null, eventOptionCount = eventRewards.size)
            if (decision.clamped) {
                // Either the setting names an option this event genuinely does not have, or the
                // matched copy is the wrong shape because the screen was not read confidently. Both
                // are possible, so name both: attributing this to the setting alone once hid a
                // recognition defect for an entire release.
                MessageLog.w(
                    TAG,
                    "[WARN] handleTrainingEvent:: Special event option ${selectedOptionIndex + 1} does not exist on the matched " +
                        "\"${eventTitle.replace("\n", " ")}\" (${eventRewards.size} option(s)). Using option ${decision.optionIndex + 1}. " +
                        "Check the setting, and the option-row count logged above if the event really has more options.",
                )
            }
            optionSelected = decision.optionIndex

            if (eventRewards.isNotEmpty()) {
                MessageLog.v(TAG, "[TRAINING_EVENT] Special event override applied: option ${optionSelected + 1}: \"${eventRewards[optionSelected]}\"")
            } else {
                MessageLog.v(TAG, "[TRAINING_EVENT] Special event override applied: option ${optionSelected + 1}")
            }
            specialEventHandled = true
        }

        // Guard against acting on a sub-threshold fuzzy match (mis-read title or un-imported support card returns the wrong event's rewards); below the floor, fall through to the safe first-option default.
        if (shouldActOnEventMatch(eventRewards, specialEventHandled, confidence, trainingEventRecognizer.minimumConfidence)) {
            if (!specialEventHandled) {
                // Check for character, support, or scenario event overrides.
                val characterOverride = checkCharacterEventOverride(characterOrSupportName, eventTitle)
                val supportOverride = checkSupportEventOverride(characterOrSupportName, eventTitle)
                val scenarioOverride = checkScenarioEventOverride(characterOrSupportName, eventTitle)

                if (characterOverride != null) {
                    optionSelected = characterOverride

                    // Ensure the selected option is within bounds.
                    if (optionSelected >= eventRewards.size) {
                        MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Selected character event option $optionSelected is out of bounds. Using last option.")
                        optionSelected = eventRewards.size - 1
                    }

                    MessageLog.v(TAG, "[TRAINING_EVENT] Character event override applied.")
                    printEventSummary(eventTitle, characterOrSupportName, eventRewards, null, optionSelected, confidence)
                } else if (supportOverride != null) {
                    optionSelected = supportOverride

                    // Ensure the selected option is within bounds.
                    if (optionSelected >= eventRewards.size) {
                        MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Selected support event option $optionSelected is out of bounds. Using last option.")
                        optionSelected = eventRewards.size - 1
                    }

                    MessageLog.v(TAG, "[TRAINING_EVENT] Support event override applied.")
                    printEventSummary(eventTitle, characterOrSupportName, eventRewards, null, optionSelected, confidence)
                } else if (scenarioOverride != null) {
                    optionSelected = scenarioOverride

                    // Ensure the selected option is within bounds.
                    if (optionSelected >= eventRewards.size) {
                        MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Selected scenario event option $optionSelected is out of bounds. Using last option.")
                        optionSelected = eventRewards.size - 1
                    }

                    MessageLog.v(TAG, "[TRAINING_EVENT] Scenario event override applied.")
                    printEventSummary(eventTitle, characterOrSupportName, eventRewards, null, optionSelected, confidence)
                } else {
                    // Initialize the List for normal event processing.
                    val selectionWeight = List(eventRewards.size) { 0 }.toMutableList()

                    // Sum up the stat gains with additional weight applied to stats that are prioritized.
                    eventRewards.forEachIndexed { rewardIndex, reward ->
                        val formattedReward: List<String> = reward.split("\n")

                        formattedReward.forEach { line ->
                            val formattedLine: String =
                                regex
                                    .replace(line, "")
                                    .replace("(", "")
                                    .replace(")", "")
                                    .trim()
                                    .lowercase()

                            // Skip empty strings and divider lines (lines that are all dashes or start with 5 dashes).
                            if (line.trim().isEmpty() || line.trim().length >= 5 && line.trim().substring(0, 5).all { it == '-' }) {
                                return@forEach
                            }

                            var priorityStatCheck = false
                            if (line.lowercase().contains("can start dating")) {
                                selectionWeight[rewardIndex] += 1000
                            } else if (line.lowercase().contains("event chain ended")) {
                                selectionWeight[rewardIndex] += -300
                            } else if (line.lowercase().contains("(random)")) {
                                selectionWeight[rewardIndex] += -10
                            } else if (line.lowercase().contains("randomly")) {
                                selectionWeight[rewardIndex] += 50
                            } else if (line.lowercase().contains("energy")) {
                                val finalEnergyValue =
                                    try {
                                        val energyValue =
                                            if (formattedLine.contains("/")) {
                                                val splits = formattedLine.split("/")
                                                var sum = 0
                                                for (split in splits) {
                                                    sum +=
                                                        try {
                                                            split.trim().toInt()
                                                        } catch (_: NumberFormatException) {
                                                            20
                                                        }
                                                }
                                                sum
                                            } else {
                                                formattedLine.toInt()
                                            }

                                        if (enablePrioritizeEnergyOptions) {
                                            energyValue * 100
                                        } else {
                                            val energyMultiplier =
                                                when {
                                                    campaign.trainee.energy < 30 -> 4
                                                    campaign.trainee.energy < 50 -> 3
                                                    campaign.trainee.energy < 70 -> 2
                                                    campaign.trainee.energy >= 90 -> 0
                                                    else -> 1
                                                }
                                            energyValue * energyMultiplier
                                        }
                                    } catch (_: NumberFormatException) {
                                        20
                                    }
                                selectionWeight[rewardIndex] += finalEnergyValue
                            } else if (line.lowercase().contains("mood")) {
                                val moodMultiplier =
                                    when (campaign.trainee.mood) {
                                        Mood.AWFUL -> 150
                                        Mood.BAD -> 120
                                        Mood.NORMAL -> 100
                                        Mood.GOOD -> 80
                                        Mood.GREAT -> 0
                                    }
                                val moodWeight = if (formattedLine.contains("-")) -150 else moodMultiplier
                                selectionWeight[rewardIndex] += moodWeight
                            } else if (line.lowercase().contains("bond")) {
                                val bondWeight = if (formattedLine.contains("-")) -20 else 20
                                selectionWeight[rewardIndex] += bondWeight
                            } else if (priorityScenarioSkillHints.any { line.lowercase().contains(it) }) {
                                // Per Trackblazer guide: always grab Nimble Navigator and Uma Stan hints. The
                                // large bump beats stat-option alternatives while both options still compete on
                                // their full weight sum.
                                selectionWeight[rewardIndex] += 500
                                MessageLog.v(TAG, "[TRAINING_EVENT] Priority scenario-skill hint in option ${rewardIndex + 1}: +500 weight.")
                            } else if (line.lowercase().contains("hint")) {
                                selectionWeight[rewardIndex] += 25
                            } else if (PositiveStatus.names.any { status -> line.contains(status) }) {
                                selectionWeight[rewardIndex] += 25
                            } else if (NegativeStatus.names.any { status -> line.contains(status) }) {
                                selectionWeight[rewardIndex] += -25
                            } else if (line.lowercase().contains("skill")) {
                                val finalSkillPoints =
                                    if (formattedLine.contains("/")) {
                                        val splits = formattedLine.split("/")
                                        var sum = 0
                                        for (split in splits) {
                                            sum +=
                                                try {
                                                    split.trim().toInt()
                                                } catch (_: NumberFormatException) {
                                                    10
                                                }
                                        }
                                        sum
                                    } else {
                                        // Guard against OCR leaving non-numeric chars the letter-stripping regex
                                        // misses (e.g. the colon in "Skill points: 24"); otherwise the parse throws
                                        // and crashes the turn. Fallback of 10 matches the slash-separated branch.
                                        try {
                                            formattedLine.toInt()
                                        } catch (_: NumberFormatException) {
                                            MessageLog.w(TAG, "[WARN] Training event reward skill-points parse failed for \"$line\" (formatted=\"$formattedLine\"). Using fallback weight of 10.")
                                            10
                                        }
                                    }
                                selectionWeight[rewardIndex] += finalSkillPoints
                            } else {
                                // Apply inflated weights to the prioritized stats based on their order.
                                campaign.training.statPrioritization.forEachIndexed { index, stat ->
                                    if (line.lowercase().contains(stat.name.lowercase())) {
                                        // Calculate weight bonus based on position (higher priority = higher bonus).
                                        val priorityBonus =
                                            when (index) {
                                                0 -> 50
                                                1 -> 40
                                                2 -> 30
                                                3 -> 20
                                                else -> 10
                                            }

                                        val finalStatValue =
                                            try {
                                                priorityStatCheck = true
                                                if (formattedLine.contains("/")) {
                                                    val splits = formattedLine.split("/")
                                                    var sum = 0
                                                    for (split in splits) {
                                                        sum +=
                                                            try {
                                                                split.trim().toInt()
                                                            } catch (_: NumberFormatException) {
                                                                10
                                                            }
                                                    }
                                                    sum + priorityBonus
                                                } else {
                                                    formattedLine.toInt() + priorityBonus
                                                }
                                            } catch (_: NumberFormatException) {
                                                priorityStatCheck = false
                                                10
                                            }
                                        selectionWeight[rewardIndex] += finalStatValue
                                    }
                                }

                                // Apply normal weights to the rest of the stats.
                                if (!priorityStatCheck) {
                                    val finalStatValue =
                                        try {
                                            if (formattedLine.contains("/")) {
                                                val splits = formattedLine.split("/")
                                                var sum = 0
                                                for (split in splits) {
                                                    sum +=
                                                        try {
                                                            split.trim().toInt()
                                                        } catch (_: NumberFormatException) {
                                                            10
                                                        }
                                                }
                                                sum
                                            } else {
                                                formattedLine.toInt()
                                            }
                                        } catch (_: NumberFormatException) {
                                            10
                                        }
                                    selectionWeight[rewardIndex] += finalStatValue
                                }
                            }
                        }
                    }

                    // Select the best option that aligns with the stat prioritization made in the Training options.
                    val max: Int? = selectionWeight.maxOrNull()
                    optionSelected =
                        if (max == null) {
                            0
                        } else {
                            selectionWeight.indexOf(max)
                        }

                    // Print the selection weights.
                    printEventSummary(eventTitle, characterOrSupportName, eventRewards, selectionWeight, optionSelected, confidence)
                }
            }

            // Print summary for special event overrides (character/support overrides are handled in their branches).
            if (specialEventHandled) {
                printEventSummary(eventTitle, characterOrSupportName, eventRewards, null, optionSelected, confidence)
            }
        } else {
            if (!specialEventHandled) {
                // Record why no match was accepted so an intermittent miss can be root-caused. A
                // best-confidence just under threshold is a near-miss (minor OCR garble); a low one means
                // bad OCR or an unknown event. Raw OCR'd title is on the recognizer's line above.
                MessageLog.w(
                    TAG,
                    "[WARN] handleTrainingEvent:: No event match accepted; selecting first option. Best candidate was " +
                        "\"$eventTitle\" (${characterOrSupportName.ifEmpty { "?" }}) at confidence " +
                        "${game.decimalFormat.format(confidence)} vs threshold " +
                        "${game.decimalFormat.format(trainingEventRecognizer.minimumConfidence)}. " +
                        "Raw OCR title is on the recognizer's \"Now starting process to find most similar string to:\" line above.",
                )
                optionSelected = 0
            }
        }

        // Validate the row list against the option that was actually chosen. The rows were read
        // before OCR, so a row that was still rendering then may exist now; refreshing once is the
        // difference between honoring the configured option and quietly tapping whatever row
        // happened to be captured first.
        var optionRows: ArrayList<Point> = trainingOptionLocations
        var tapPlan = planOptionTap(optionSelected, optionRows.size, rescanned = false)
        if (tapPlan.action == OptionTapAction.RESCAN) {
            val refreshed: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
            MessageLog.w(
                TAG,
                "[WARN] handleTrainingEvent:: Option ${optionSelected + 1} is outside the ${optionRows.size} row(s) read before recognition " +
                    "for \"${eventTitle.replace("\n", " ")}\" (trainee ${campaign.trainee.name.ifEmpty { "?" }}); rescanned and found ${refreshed.size} row(s).",
            )
            optionRows = refreshed
            tapPlan = planOptionTap(optionSelected, optionRows.size, rescanned = true)
        }

        // Handle Tutorial events specially.
        if (isTutorialEvent && optionRows.isNotEmpty()) {
            if (tutorialOptionCount == 5) {
                // Determine the last option location for a 5-option Tutorial.
                val lastOptionLocation =
                    try {
                        optionRows[4]
                    } catch (_: IndexOutOfBoundsException) {
                        optionRows[optionRows.size - 1]
                    }

                game.tap(lastOptionLocation.x + game.imageUtils.relWidth(100), lastOptionLocation.y, IconTrainingEventHorseshoe.template.path)
                MessageLog.i(TAG, "[TRAINING_EVENT] Selected last option (option 5) for Tutorial to back out.")

                game.wait(1.0)

                // Refresh training option locations.
                val updatedTrainingOptionLocations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
                if (updatedTrainingOptionLocations.isNotEmpty()) {
                    // Select the first option to close the Tutorial.
                    val firstOptionLocation = updatedTrainingOptionLocations[0]
                    game.tap(firstOptionLocation.x + game.imageUtils.relWidth(100), firstOptionLocation.y, IconTrainingEventHorseshoe.template.path)
                    MessageLog.i(TAG, "[TRAINING_EVENT] Selected first option (option 1) to close Tutorial.")
                } else {
                    MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Could not find Training Event options after waiting. Tutorial may have already closed.")
                }
            } else {
                // Select the determined option for standard Tutorial cases.
                val selectedLocation =
                    try {
                        optionRows[optionSelected]
                    } catch (_: IndexOutOfBoundsException) {
                        optionRows[optionRows.size - 1]
                    }

                game.tap(selectedLocation.x + game.imageUtils.relWidth(100), selectedLocation.y, IconTrainingEventHorseshoe.template.path)
                MessageLog.i(TAG, "[TRAINING_EVENT] Selected option ${optionSelected + 1} for Tutorial.")
            }

            // Exclude handling for certain scenarios that do not require this logic.
            if (game.scenario != "Trackblazer") {
                // Wait three seconds before processing Next/Close buttons for the Tutorial.
                MessageLog.i(TAG, "[TRAINING_EVENT] Waiting 3 seconds before handling Next/Close buttons for Tutorial.")
                game.wait(3.0)

                // Search for and click Next buttons until the Close button is detected.
                var closeButtonFound = false
                val maxIterations = 20 // Set a limit to prevent infinite loops.
                var iterationCount = 0

                while (!closeButtonFound && iterationCount < maxIterations) {
                    iterationCount++

                    // Check for the Close button first.
                    if (ButtonClose.click(game.imageUtils)) {
                        MessageLog.i(TAG, "[TRAINING_EVENT] Close button found and clicked. Tutorial event handling complete.")
                        closeButtonFound = true
                        break
                    }

                    // Look for the Next button if the Close button is not found.
                    if (ButtonNext.click(game.imageUtils)) {
                        MessageLog.i(TAG, "[TRAINING_EVENT] Next button found and clicked. Waiting for next screen...")
                        game.wait(1.0)
                    } else {
                        // Wait briefly and retry if neither button is found.
                        MessageLog.i(TAG, "[TRAINING_EVENT] Neither Next nor Close button found. Waiting...")
                        game.wait(0.5)
                    }
                }

                if (!closeButtonFound && iterationCount >= maxIterations) {
                    MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Reached maximum iterations while searching for Close button. Tutorial handling may be incomplete.")
                }
            }
        } else {
            // Proceed with normal event handling. The plan already accounts for a refreshed row
            // list; a shortfall that survived the refresh is reported rather than absorbed, because
            // tapping a row the selection did not ask for is the failure this whole path exists to
            // prevent and it is otherwise indistinguishable from success in the log.
            val selectedLocation: Point? =
                when (tapPlan.action) {
                    OptionTapAction.USE_ROW -> optionRows[tapPlan.rowIndex]

                    OptionTapAction.CLAMP_TO_LAST_ROW -> {
                        MessageLog.w(
                            TAG,
                            "[WARN] handleTrainingEvent:: Option ${optionSelected + 1} was selected for \"${eventTitle.replace("\n", " ")}\" but only " +
                                "${optionRows.size} row(s) are on screen after a rescan. Tapping row ${tapPlan.rowIndex + 1}; the configured option was not applied.",
                        )
                        optionRows[tapPlan.rowIndex]
                    }

                    OptionTapAction.NO_ROWS_AVAILABLE -> {
                        MessageLog.w(TAG, "[WARN] handleTrainingEvent:: No option rows detected after a rescan. Falling back to a single retrying search for one row.")
                        IconTrainingEventHorseshoe.find(game.imageUtils, tries = 5).first
                    }

                    // Re-planned above, so the list has already been refreshed by this point.
                    OptionTapAction.RESCAN -> null
                }

            if (selectedLocation != null) {
                game.tap(selectedLocation.x + game.imageUtils.relWidth(100), selectedLocation.y, IconTrainingEventHorseshoe.template.path)

                // Verify if a confirmation dialog is required for this special event.
                if (specialEventResult != null) {
                    val (_, requiresConfirmation) = specialEventResult
                    if (requiresConfirmation) {
                        MessageLog.i(TAG, "[TRAINING_EVENT] Special event requires confirmation, waiting for dialog...")

                        // Wait for the confirmation dialog to appear.
                        game.wait(1.0)

                        // Select the first confirmation option (Yes).
                        val confirmationLocations: ArrayList<Point> = IconTrainingEventHorseshoe.findAll(game.imageUtils)
                        if (confirmationLocations.isNotEmpty()) {
                            val confirmLocation = confirmationLocations[0]
                            game.tap(confirmLocation.x + game.imageUtils.relWidth(100), confirmLocation.y, IconTrainingEventHorseshoe.template.path)
                            MessageLog.i(TAG, "[TRAINING_EVENT] Special event confirmed.")
                        } else {
                            MessageLog.w(TAG, "[WARN] handleTrainingEvent:: Could not find confirmation options for special event.")
                        }
                    }
                }
            }
        }

        MessageLog.v(TAG, "[TRAINING_EVENT] Process to handle detected Training Event completed.")
        MessageLog.v(TAG, "********************")
    }
}
