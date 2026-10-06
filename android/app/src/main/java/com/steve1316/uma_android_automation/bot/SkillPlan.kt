package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.types.RunningStyle
import com.steve1316.uma_android_automation.types.SkillData
import com.steve1316.uma_android_automation.types.SkillList
import com.steve1316.uma_android_automation.types.SkillListEntry
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
import com.steve1316.uma_android_automation.utils.CAREER_END_SCAN_BUDGET_MS
import com.steve1316.uma_android_automation.utils.OutcomeCorpus
import org.json.JSONObject
import org.opencv.core.Point

private const val USE_MOCK_DATA: Boolean = false
private const val MOCK_SKILL_POINTS: Int = 1495

/**
 * Handle operations based on the user's Skill Plan Settings.
 *
 * @property game The [Game] instance used for bot interaction.
 * @property campaign The [Campaign] instance currently being automated.
 */
class SkillPlan(private val game: Game, private val campaign: Campaign) {
    /** The preferred running style from settings. */
    val skillSettingRunningStyleString = SettingsHelper.getStringSetting("skills", "preferredRunningStyle")

    /** The preferred track distance from settings. */
    val skillSettingTrackDistanceString = SettingsHelper.getStringSetting("skills", "preferredTrackDistance")

    /** The preferred track surface from settings. */
    val skillSettingTrackSurfaceString = SettingsHelper.getStringSetting("skills", "preferredTrackSurface")

    /** When true, skip the ◎ upgrade of a skill and buy only its ○ form, stretching the budget across more distinct skills. */
    private val skipDoubleCircleUpgrades = SettingsHelper.getBooleanSetting("skills", "skipDoubleCircleUpgrades", false)

    /** When true, the rank objective's career-end tail may buy any skill for rating, not only skills the trainee's running style can activate. */
    private val careerEndBuyAnySkill = SettingsHelper.getBooleanSetting("skills", "careerEndBuyAnySkill", false)

    /** The preferred track distance override for training. */
    private val trainingSettingTrackDistanceString = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")

    /** Null until the planner resolves it, so early exits omit the telemetry field. */
    private var sessionStrategyTailAllowed: Boolean? = null

    /** The trigger [start] resolved; the career-end fallback fires only on CAREER_COMPLETE. */
    private var sessionEffectiveTrigger: SkillCheckTrigger? = null

    /** True only when the constrained career-end fallback ran; null otherwise so the telemetry field is omitted. */
    private var sessionCareerEndFallback: Boolean? = null

    /**
     * Null until the planning scan runs; the buy passes do not overwrite it (purchase coverage is policed by the
     * points-delta arbiter).
     */
    private var sessionPlanningScanComplete: Boolean? = null

    /** Set independent of the corpus append: a telemetry IO failure must never blind the Finish guard. */
    internal var lastSessionEvidence: FinalizeEvidence? = null
        private set

    /** Recovery-protection outcome for telemetry; all null when the gate never armed. */
    private var sessionRecoveryRuleActive: Boolean? = null
    private var sessionRecoveryRequired: Boolean? = null
    private var sessionRecoverySkill: String? = null
    private var sessionRecoveryObservedPrice: Int? = null

    /** Names whose purchase was VERIFIED (Skill Points moved); they survive each round's simulation reset. */
    private val sessionVerifiedBuys: MutableSet<String> = mutableSetOf()

    /** The original race strategy from settings. */
    private val racingSettingRunningStyleString = SettingsHelper.getStringSetting("racing", "originalRaceStrategy")

    /** Map of skill plan names to their corresponding settings. */
    val skillPlans: Map<String, SkillPlanSettings> =
        try {
            val plansString = SettingsHelper.getStringSetting("skills", "plans")
            if (plansString.isNotEmpty()) {
                val jsonObject = JSONObject(plansString)
                val plansMap = mutableMapOf<String, SkillPlanSettings>()
                jsonObject.keys().forEach { planName ->
                    try {
                        val planData = jsonObject.getJSONObject(planName)
                        val strategyString: String = planData.optString("strategy", "")
                        val skillIds: List<Int> =
                            planData
                                .optString("plan", "")
                                .split(",")
                                .map { it.trim() }
                                .mapNotNull { it.toIntOrNull() }
                        val skillNames: List<String> = skillIds.mapNotNull { game.skillDatabase.getSkillName(it) }
                        plansMap[planName] =
                            SkillPlanSettings(
                                bIsEnabled = planData.optBoolean("enabled", false),
                                strategy = SpendingStrategy.fromName(strategyString) ?: SpendingStrategy.DEFAULT,
                                bEnableBuyInheritedUniqueSkills = planData.optBoolean("enableBuyInheritedUniqueSkills", false),
                                bEnableBuyNegativeSkills = planData.optBoolean("enableBuyNegativeSkills", false),
                                skillNames = skillNames,
                            )
                    } catch (e: Exception) {
                        // Skip just this entry: one bad plan must not empty ALL plans.
                        MessageLog.w(TAG, "[WARN] skillPlans:: Skipping unparseable plan '$planName': ${e.message}")
                    }
                }
                plansMap
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            MessageLog.w(TAG, "[WARN] skillPlans:: Could not parse skill plan settings: ${e.message}")
            emptyMap()
        }

    /** The strategy used for spending skill points. */
    enum class SpendingStrategy {
        /** Default spending strategy. Currently synonymous with OPTIMIZE_RANK. */
        DEFAULT,

        /** Prioritize skills that match the trainee's aptitudes and community-tier rankings. */
        OPTIMIZE_SKILLS,

        /** Prioritize skills that offer the best rank increase per point spent. */
        OPTIMIZE_RANK,

        /**
         * Unlike greedy [OPTIMIZE_RANK], respects base/upgrade mutual exclusion (owning both wastes the base cost).
         */
        OPTIMIZE_KNAPSACK,

        ;

        companion object {
            private val nameMap = entries.associateBy { it.name }
            private val ordinalMap = entries.associateBy { it.ordinal }

            /**
             * Tolerates whitespace and hyphen/underscore drift so a stray persisted format does not fall back to
             * greedy.
             */
            fun fromName(value: String): SpendingStrategy? = nameMap[value.trim().uppercase().replace('-', '_')]

            /** Retrieve the [SpendingStrategy] by its ordinal value. */
            fun fromOrdinal(ordinal: Int): SpendingStrategy? = ordinalMap[ordinal]
        }
    }

    /**
     * Encapsulates the configuration for a specific skill plan.
     *
     * @property bIsEnabled Whether the skill plan is active.
     * @property strategy The [SpendingStrategy] to follow.
     * @property bEnableBuyInheritedUniqueSkills Whether to purchase inherited unique skills.
     * @property bEnableBuyNegativeSkills Whether to purchase negative (blue) skills.
     * @property skillNames The list of specific skill names to purchase as part of this plan.
     */
    data class SkillPlanSettings(
        val bIsEnabled: Boolean,
        val strategy: SpendingStrategy,
        val bEnableBuyInheritedUniqueSkills: Boolean,
        val bEnableBuyNegativeSkills: Boolean,
        val skillNames: List<String>,
    )

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]SkillPlan"

        /**
         * Represents a skill available for purchase in a pure calculation context.
         *
         * @property name The skill name.
         * @property price The skill's price in skill points.
         * @property evaluationPoints The rank points gained upon purchase.
         * @property isNegative Whether this is a negative (purple) skill.
         * @property isInheritedUnique Whether this is an inherited unique skill.
         * @property isUserPlanned Whether this skill is in the user's plan.
         * @property communityTier The community tier ranking (lower is better, null = unranked).
         */
        data class SkillCandidate(
            val name: String,
            val price: Int,
            val evaluationPoints: Int,
            val isNegative: Boolean = false,
            val isInheritedUnique: Boolean = false,
            val isUserPlanned: Boolean = false,
            val communityTier: Int? = null,
        ) {
            /** The ratio of rank gained to price. Higher is better. */
            val evaluationPointRatio: Double
                get() = if (price > 0) evaluationPoints.toDouble() / price.toDouble() else 0.0
        }

        /**
         * Upstream's approximation of the cheapest useful purchase (70 at the deepest observed 40% hint discount =
         * 42), NOT a proven floor; it only skips mid-career scans and is never consulted by the finalization guard.
         */
        internal const val SKILL_POINTS_EARLY_EXIT_FLOOR = 42

        /** The ◎ upgrade of an ○ skill, by the name suffix the OCR pass appends. */
        fun isDoubleCircleUpgrade(name: String): Boolean = name.trimEnd().endsWith("◎")

        /**
         * Whether a skill is compatible with the resolved Style preference on every axis. A skill passes when, for each axis with a
         * preference, it either has no commitment on that axis (generic / aptitude-independent) or its value matches. Running style
         * matches on the explicit style or any inferred style, mirroring the Optimize Skills include-pass.
         *
         * @param skillDistance The skill's track distance, or null.
         * @param skillStyle The skill's explicit running style, or null.
         * @param skillInferredStyles The skill's inferred running styles (may be empty).
         * @param skillSurface The skill's track surface, or null.
         * @param prefDistance The preferred track distance, or null for no restriction.
         * @param prefStyle The preferred running style, or null for no restriction.
         * @param prefSurface The preferred track surface, or null for no restriction.
         * @return True if the skill is buyable under the preference.
         */
        fun matchesPreference(
            skillDistance: TrackDistance?,
            skillStyle: RunningStyle?,
            skillInferredStyles: List<RunningStyle>,
            skillSurface: TrackSurface?,
            prefDistance: TrackDistance?,
            prefStyle: RunningStyle?,
            prefSurface: TrackSurface?,
        ): Boolean {
            val distanceOk = prefDistance == null || skillDistance == null || skillDistance == prefDistance
            val surfaceOk = prefSurface == null || skillSurface == null || skillSurface == prefSurface
            val styleOk =
                prefStyle == null ||
                    (skillStyle == null && skillInferredStyles.isEmpty()) ||
                    skillStyle == prefStyle ||
                    prefStyle in skillInferredStyles
            return distanceOk && surfaceOk && styleOk
        }

        internal fun knapsackTailAllows(
            filter: CareerEndTailFilter,
            skillDistance: TrackDistance?,
            skillStyle: RunningStyle?,
            skillInferredStyles: List<RunningStyle>,
            skillSurface: TrackSurface?,
            prefDistance: TrackDistance?,
            prefStyle: RunningStyle?,
            prefSurface: TrackSurface?,
        ): Boolean =
            when (filter) {
                CareerEndTailFilter.ANY_SKILL -> true
                CareerEndTailFilter.RUNNING_STYLE -> matchesPreference(skillDistance, skillStyle, skillInferredStyles, skillSurface, null, prefStyle, null)
                CareerEndTailFilter.PROFILE -> matchesPreference(skillDistance, skillStyle, skillInferredStyles, skillSurface, prefDistance, prefStyle, prefSurface)
            }

        /**
         * Stricter than the knapsack set: negatives and inherited uniques are excluded (their toggles own them), plus
         * the double-circle skip and Style axes.
         */
        fun careerEndFallbackCandidateAllowed(
            isNegative: Boolean,
            isInheritedUnique: Boolean,
            isDoubleCircle: Boolean,
            skipDoubleCircleUpgrades: Boolean,
            matchesAxes: Boolean,
        ): Boolean = !isNegative && !isInheritedUnique && (!skipDoubleCircleUpgrades || !isDoubleCircle) && matchesAxes

        /**
         * Structural only: a purchasable white or gold recovery (never an inherited unique or a negative) that the
         * Style preference accepts and that commits to a distance/style/surface axis or is on the small verified
         * allow-list. Axis-free condition traps (Triple 7s, Shake It Out) fail; inferred styles do not count as
         * commitment.
         */
        internal fun isRecoveryInjectionCandidate(
            skillData: SkillData,
            prefDistance: TrackDistance?,
            prefStyle: RunningStyle?,
            prefSurface: TrackSurface?,
        ): Boolean {
            if (recoveryClassOf(skillData.iconId) == RecoveryClass.NONE) return false
            if (skillData.bIsInheritedUnique || skillData.bIsNegative) return false
            if (!matchesPreference(
                    skillData.trackDistance,
                    skillData.runningStyle,
                    skillData.inferredRunningStyles,
                    skillData.trackSurface,
                    prefDistance,
                    prefStyle,
                    prefSurface,
                )
            ) {
                return false
            }
            val committed = skillData.trackDistance != null || skillData.runningStyle != null || skillData.trackSurface != null
            return committed || skillData.id in GENERAL_RECOVERY_IDS
        }

        /** [price] is the live cumulative screen price. */
        internal data class RecoveryCandidate(
            val name: String,
            val recoveryClass: RecoveryClass,
            val price: Int,
            val skillId: Int,
        )

        /** WHITE before GOLD (cheapest reliable heal), then lowest live price, then skill ID. */
        internal fun pickRecoveryCandidate(candidates: List<RecoveryCandidate>): RecoveryCandidate? =
            candidates.minWithOrNull(
                compareBy({ it.recoveryClass != RecoveryClass.WHITE }, { it.price }, { it.skillId }),
            )

        /** The rank fill spends leftovers by ratio, so it skips negatives (their toggle owns them) and skills that add no rating. */
        fun rankFillAllows(isNegative: Boolean, evaluationPoints: Int): Boolean = !isNegative && evaluationPoints > 0

        /**
         * Pure calculation function that determines which skills to buy using the Optimize Rank strategy.
         *
         * Greedily selects skills with the highest evaluation-point-to-price ratio within
         * the available budget.
         *
         * @param candidates List of available skills for purchase.
         * @param budget Available skill points to spend.
         * @param alreadyPlanned Skills already planned for purchase (to avoid duplicates).
         * @param skipDoubleCircle When true, exclude ◎ upgrades so the budget spreads across more ○ skills.
         * @return Ordered list of (name, price) pairs representing skills to buy.
         */
        fun calculateOptimizeRankPurchases(
            candidates: List<SkillCandidate>,
            budget: Int,
            alreadyPlanned: List<String> = emptyList(),
            skipDoubleCircle: Boolean = false,
        ): List<Pair<String, Int>> {
            val result = mutableListOf<Pair<String, Int>>()
            var remaining = budget

            val sorted =
                candidates
                    .filter { it.name !in alreadyPlanned && it.price > 0 && rankFillAllows(it.isNegative, it.evaluationPoints) && (!skipDoubleCircle || !isDoubleCircleUpgrade(it.name)) }
                    .sortedByDescending { it.evaluationPointRatio }

            for (skill in sorted) {
                if (skill.price <= remaining) {
                    result.add(skill.name to skill.price)
                    remaining -= skill.price
                }
            }

            return result
        }

        /** For an upgrade chain only the upgraded form activates, so scores are not summed. */
        data class KnapsackChoice(
            val items: List<SkillCandidate>,
        ) {
            /**
             * SP cost: the LAST item's price, not the sum. A chain member's screen price already includes its
             * unpurchased prerequisites, so summing charged the base twice and made the DP under-buy gold/◎ upgrades.
             */
            val cost: Int = items.lastOrNull()?.price ?: 0

            /** Max across the items, not the sum: only the upgrade ◎ activates. */
            val score: Int = items.maxOfOrNull { it.evaluationPoints } ?: 0

            val isSkip: Boolean = items.isEmpty()

            val names: List<String> = items.map { it.name }
        }

        /** A required group (user-planned, negative, inherited unique) has no skip option. */
        data class KnapsackGroup(
            val choices: List<KnapsackChoice>,
            val isRequired: Boolean = false,
        )

        /**
         * Port of `optimizeGrouped` in `daftuyda/UmaTools` `js/optimizer.js`; empty if a required group is
         * unreachable under the budget.
         */
        fun calculateOptimizeKnapsackPurchases(
            groups: List<KnapsackGroup>,
            budget: Int,
        ): List<Pair<String, Int>> {
            if (groups.isEmpty() || budget <= 0) return emptyList()

            val numGroups = groups.size
            val sentinel = Int.MIN_VALUE / 4 // Avoids overflow when added to a positive score
            val budgetLimit = budget

            var dpPrev = IntArray(budgetLimit + 1) { 0 }
            var dpCurr = IntArray(budgetLimit + 1) { sentinel }

            val choice = Array(numGroups + 1) { IntArray(budgetLimit + 1) { -1 } }

            for (g in 1..numGroups) {
                val group = groups[g - 1]
                val opts = group.choices
                val skipAllowed = !group.isRequired || opts.any { it.isSkip }

                for (b in 0..budgetLimit) {
                    if (skipAllowed) {
                        dpCurr[b] = dpPrev[b]
                        choice[g][b] = -1
                    } else {
                        dpCurr[b] = sentinel
                        choice[g][b] = -1
                    }

                    for (k in opts.indices) {
                        val o = opts[k]
                        if (o.isSkip) continue
                        val w = o.cost.coerceAtLeast(0)
                        val v = o.score.coerceAtLeast(0)
                        if (w <= b && dpPrev[b - w] > sentinel / 2) {
                            val cand = dpPrev[b - w] + v
                            if (cand > dpCurr[b]) {
                                dpCurr[b] = cand
                                choice[g][b] = k
                            }
                        }
                    }
                }

                val tmp = dpPrev
                dpPrev = dpCurr
                dpCurr = tmp
                dpCurr.fill(sentinel)
            }

            if (dpPrev[budgetLimit] <= sentinel / 2) {
                return emptyList()
            }

            val result = mutableListOf<Pair<String, Int>>()
            var remaining = budgetLimit
            val pickedGroups = mutableListOf<KnapsackChoice>()
            for (g in numGroups downTo 1) {
                val k = choice[g][remaining]
                if (k < 0) continue // Skipped this group
                val picked = groups[g - 1].choices[k]
                if (picked.isSkip) continue
                pickedGroups.add(picked)
                remaining -= picked.cost
            }

            // Reverse into selection order (base before upgrade within each chain).
            for (choice in pickedGroups.asReversed()) {
                var previousChainPrice = 0
                for (item in choice.items) {
                    // Chain members carry cumulative screen prices, so emit each link's increment (what the screen
                    // charges once earlier links are owned); clamped because an OCR misread can break the
                    // invariant.
                    result.add(item.name to (item.price - previousChainPrice).coerceAtLeast(0))
                    previousChainPrice = item.price
                }
            }
            return result
        }

        /** Skills sharing an upgrade chain become one group; the rest become [skip, pick] singletons. */
        fun buildKnapsackGroups(
            candidates: List<SkillCandidate>,
            upgradeChains: Map<String, List<String>>,
            requiredNames: Set<String> = emptySet(),
        ): List<KnapsackGroup> {
            val byName: Map<String, SkillCandidate> = candidates.associateBy { it.name }
            val processedNames = mutableSetOf<String>()
            val groups = mutableListOf<KnapsackGroup>()

            for (candidate in candidates) {
                if (candidate.name in processedNames) continue

                // The map may key by any chain member; the value is the full ordered chain.
                val chain: List<String> = upgradeChains[candidate.name].orEmpty()
                val chainPresent: List<SkillCandidate> =
                    if (chain.isNotEmpty()) {
                        chain.mapNotNull { byName[it] }
                    } else {
                        listOf(candidate)
                    }

                if (chainPresent.size <= 1) {
                    val item = chainPresent.firstOrNull() ?: candidate
                    val isRequired = item.name in requiredNames
                    val choices =
                        buildList {
                            if (!isRequired) add(KnapsackChoice(emptyList()))
                            add(KnapsackChoice(listOf(item)))
                        }
                    groups.add(KnapsackGroup(choices, isRequired))
                    processedNames.add(item.name)
                    continue
                }

                val chainRequired = chainPresent.any { it.name in requiredNames }
                val choices =
                    buildList {
                        if (!chainRequired) add(KnapsackChoice(emptyList()))
                        for (i in chainPresent.indices) {
                            add(KnapsackChoice(chainPresent.subList(0, i + 1).toList()))
                        }
                    }
                groups.add(KnapsackGroup(choices, chainRequired))
                chainPresent.forEach { processedNames.add(it.name) }
            }

            return groups
        }

        /**
         * Pure calculation function that determines which skills to buy using the common strategy.
         *
         * Buys in order: negative skills, inherited unique skills, then user-planned skills,
         * respecting the budget and enabled flags.
         *
         * @param candidates All available skill candidates.
         * @param budget Available skill points to spend.
         * @param settings Configuration for which skill types to buy.
         * @return Ordered list of (name, price) pairs representing skills to buy.
         */
        fun calculateCommonPurchases(
            candidates: List<SkillCandidate>,
            budget: Int,
            settings: SkillPlanSettings,
        ): List<Pair<String, Int>> {
            val result = mutableListOf<Pair<String, Int>>()
            var remaining = budget
            val bought = mutableSetOf<String>()

            // Phase 1: Negative skills
            if (settings.bEnableBuyNegativeSkills) {
                for (skill in candidates.filter { it.isNegative }) {
                    if (skill.name in bought) continue
                    if (skill.price <= remaining) {
                        result.add(skill.name to skill.price)
                        remaining -= skill.price
                        bought.add(skill.name)
                    }
                }
            }

            // Phase 2: Inherited unique skills
            if (settings.bEnableBuyInheritedUniqueSkills) {
                for (skill in candidates.filter { it.isInheritedUnique }) {
                    if (skill.name in bought) continue
                    if (skill.price <= remaining) {
                        result.add(skill.name to skill.price)
                        remaining -= skill.price
                        bought.add(skill.name)
                    }
                }
            }

            // Phase 3: User-planned skills (in the order specified by plan)
            for (skill in candidates.filter { it.isUserPlanned }) {
                if (skill.name in bought) continue
                if (skill.price <= remaining) {
                    result.add(skill.name to skill.price)
                    remaining -= skill.price
                    bought.add(skill.name)
                }
            }

            return result
        }

        /**
         * Pure calculation function that combines common and strategy-specific purchases.
         *
         * @param candidates All available skill candidates.
         * @param budget Available skill points to spend.
         * @param settings Configuration for the skill plan.
         * @param skipDoubleCircle When true, exclude ◎ upgrades from the strategy-specific phase so the
         *   budget spreads across more ○ skills. The common phase (negative/inherited/user-planned) is
         *   intentionally unaffected — those are explicit picks, not opportunistic ratio fill.
         * @param allowStrategyTail When false (2B-1 planned-only shaping, Adaptive + sparks), stop after
         *   the common phases: the strategy-specific tail never runs and the leftover budget stays
         *   unspent. The common phases are unaffected -- planned skills and the inherited/negative
         *   toggles behave exactly as always.
         * @return Ordered list of (name, price) pairs representing all skills to buy.
         */
        fun calculateSkillPurchases(
            candidates: List<SkillCandidate>,
            budget: Int,
            settings: SkillPlanSettings,
            skipDoubleCircle: Boolean = false,
            allowStrategyTail: Boolean = true,
        ): List<Pair<String, Int>> {
            if (!settings.bIsEnabled) return emptyList()

            val result = mutableListOf<Pair<String, Int>>()

            // Common purchases first
            val common = calculateCommonPurchases(candidates, budget, settings)
            result.addAll(common)
            val spent = common.sumOf { it.second }
            val alreadyBought = common.map { it.first }

            // Planned-only: the strategy tail is skipped and leftover budget accepted.
            if (!allowStrategyTail) return result

            // Drop ◎ upgrades up front when the toggle is on so every strategy below, including the knapsack DP's
            // ○ -> ◎ chain group, only sees the ○ form.
            val remainingCandidates =
                candidates.filter {
                    it.name !in alreadyBought && (!skipDoubleCircle || !isDoubleCircleUpgrade(it.name))
                }
            val strategyPurchases =
                when (settings.strategy) {
                    SpendingStrategy.DEFAULT, SpendingStrategy.OPTIMIZE_RANK -> {
                        calculateOptimizeRankPurchases(remainingCandidates, budget - spent, alreadyBought)
                    }
                    SpendingStrategy.OPTIMIZE_SKILLS -> {
                        // For OPTIMIZE_SKILLS, filter by community tier first, then fall back to rank
                        val tiered =
                            remainingCandidates
                                .filter { it.communityTier != null }
                                .sortedWith(compareBy<SkillCandidate> { it.communityTier }.thenByDescending { it.evaluationPointRatio })
                        val tieredResult = mutableListOf<Pair<String, Int>>()
                        var tieredRemaining = budget - spent
                        val tieredBought = alreadyBought.toMutableList()
                        for (skill in tiered) {
                            if (skill.name in tieredBought) continue
                            if (skill.price <= tieredRemaining) {
                                tieredResult.add(skill.name to skill.price)
                                tieredRemaining -= skill.price
                                tieredBought.add(skill.name)
                            }
                        }
                        // Fall back to optimize rank for remaining budget
                        val rankFallback =
                            calculateOptimizeRankPurchases(
                                remainingCandidates.filter { it.name !in tieredBought },
                                tieredRemaining,
                                tieredBought,
                            )
                        tieredResult + rankFallback
                    }
                    SpendingStrategy.OPTIMIZE_KNAPSACK -> {
                        // No chain map here (it lives in SkillDatabase): singleton groups only; use
                        // [buildKnapsackGroups] for the full benefit.
                        val singletonGroups =
                            remainingCandidates.map { c ->
                                KnapsackGroup(
                                    choices = listOf(KnapsackChoice(emptyList()), KnapsackChoice(listOf(c))),
                                    isRequired = false,
                                )
                            }
                        calculateOptimizeKnapsackPurchases(singletonGroups, budget - spent)
                    }
                }
            result.addAll(strategyPurchases)

            return result
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Debug Tests

    /**
     * Perform a test run of the skill list OCR and purchasing logic using mock skill points.
     *
     * This method allows for testing the skill identification and selection logic without performing actual transactions in the game.
     */
    fun startSkillListBuyTest() {
        // Debug harness (debugMode_startSkillListBuyTest): runs the REAL purchase pass on the current skill list
        // screen instead of the simulation.
        MessageLog.i(TAG, "\n[TEST] Now beginning Skill List Buy test (REAL purchase pass). Waiting up to 30s for the Learn screen...")
        val testSkillList = SkillList(game, campaign)
        var bOnSkillScreen = false
        for (i in 1..30) {
            if (testSkillList.checkCareerCompleteSkillListScreen() || testSkillList.checkSkillListScreen()) {
                bOnSkillScreen = true
                break
            }
            game.wait(1.0, skipWaitingForLoading = true)
        }
        if (!bOnSkillScreen) {
            MessageLog.e(TAG, "[ERROR] startSkillListBuyTest:: Learn/skill-list screen not detected within 30s. Open it in the game and restart the bot.")
            return
        }
        val result: Boolean = start()
        MessageLog.i(TAG, "[TEST] Skill List Buy test complete (start() returned $result).")
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Retrieve all available negative skills from the skill list.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for the identified negative skills.
     */
    private fun getNegativeSkills(skillPlanSettings: SkillPlanSettings, skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        if (!skillPlanSettings.bEnableBuyNegativeSkills) {
            return emptyMap()
        }

        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints = availableSkillPoints

        val entries: Map<String, SkillListEntry> = skillList.getNegativeSkills()
        for ((name, entry) in entries) {
            // Don't add any duplicate entries.
            if (name in skillsToBuy) {
                continue
            }

            // Auto-obtained rows are skipped: tallying them spent budget on skills that were never purchasable.
            if (entry.bIsAvailable && entry.screenPrice <= remainingSkillPoints) {
                result[name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }
        }

        return result.toMap()
    }

    /**
     * Retrieve all available inherited unique skills from the skill list.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for the identified inherited unique skills.
     */
    private fun getInheritedUniqueSkills(skillPlanSettings: SkillPlanSettings, skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        if (!skillPlanSettings.bEnableBuyInheritedUniqueSkills) {
            return emptyMap()
        }

        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints = availableSkillPoints

        val entries: Map<String, SkillListEntry> = skillList.getInheritedUniqueSkills()
        for ((name, entry) in entries) {
            if (name in skillsToBuy || name in result) {
                continue
            }

            if (entry.bIsAvailable && entry.screenPrice <= remainingSkillPoints) {
                result[name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }
        }

        return result.toMap()
    }

    /**
     * Retrieve all available skills from the user's skill plan that are present in the skill list.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for the identified user-planned skills.
     */
    private fun getUserPlannedSkills(skillPlanSettings: SkillPlanSettings, skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        if (skillPlanSettings.skillNames.isEmpty()) {
            return emptyMap()
        }

        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints = availableSkillPoints

        // If two versions of the same skill are in the skill list and plan, prioritize the higher level version.
        // For example, if "Corner Recovery O" and "Swinging Maestro" are both in the plan and list,
        // prioritize "Swinging Maestro". If points are insufficient, attempt to buy "Corner Recovery O" instead.
        for (name in skillPlanSettings.skillNames) {
            // Don't add duplicate entries.
            if (name in skillsToBuy || name in result) {
                continue
            }

            val entry: SkillListEntry? = skillList.getEntry(name)
            if (entry == null) {
                MessageLog.e(TAG, "[ERROR] getUserPlannedSkills:: Failed to find entry for \"$name\".")
                continue
            }

            // Handle exact matches.
            if (entry.bIsAvailable) {
                // Respect the wave budget in plan order: the buyer purchases in scroll order, so an over-budget
                // set stranded the expensive critical skills (careers reached 2500m+ gates without Swinging
                // Maestro).
                if (entry.screenPrice > remainingSkillPoints) {
                    MessageLog.v(
                        TAG,
                        "[SKILLS] getUserPlannedSkills:: Skipping \"$name\" (${entry.screenPrice}pt) - exceeds the remaining wave budget (${remainingSkillPoints}pt).",
                    )
                    continue
                }
                result[name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
                continue
            }

            // If no exact match exists, check for in-place upgrade chains.
            // Obtaining a skill hint for an in-place chain skill allows upgrading to any higher versions.
            // Higher versions of non-in-place chains require their own skill hints to unlock.

            // Skip the entry if no downgraded versions exist in the skill list.
            val availableEntry: SkillListEntry = entry.getFirstAvailableDowngrade() ?: continue

            // If a downgraded version exists, calculate the sequence of upgrades required to reach the planned skill.
            val upgrades: List<SkillListEntry> = availableEntry.getUpgradesUntil(name)

            // Handle in-place upgrade skill chains.
            if (upgrades.all { it.bIsInPlace }) {
                // Only add entries that haven't already been planned or purchased.
                val unacquired: List<SkillListEntry> =
                    upgrades
                        .filter { it.name !in skillsToBuy && it.name !in result }

                val totalPrice: Int = unacquired.sumOf { it.price }
                if (totalPrice <= remainingSkillPoints) {
                    unacquired.forEach { it.buy() }
                    val toAdd: Map<String, Int> = unacquired.associate { it.name to it.price }
                    result.putAll(toAdd)
                    remainingSkillPoints -= totalPrice
                }
                continue
            }
        }

        return result.toMap()
    }

    /**
     * Retrieve all available negative, inherited unique, and user-planned skills.
     *
     * These common skill checks are performed across all spending strategies.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for all identified common skills.
     */
    private fun getSkillsToBuyCommon(skillPlanSettings: SkillPlanSettings, skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()

        result +=
            getNegativeSkills(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = skillsToBuy + result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )

        result +=
            getInheritedUniqueSkills(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = skillsToBuy + result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )

        result +=
            getUserPlannedSkills(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = skillsToBuy + result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )

        result +=
            getRecoveryInjectionSkills(
                skillList = skillList,
                skillsToBuy = skillsToBuy + result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )

        return result.toMap()
    }

    /**
     * Recovery-deficit protection; runs only when the adaptive gate arms, never opens a session itself. Satisfied by
     * a compatible recovery already owned or selected this session (a planned-but-never-observed one does not count:
     * a Potential-gated Cooldown must never block the fallback); otherwise buys the cheapest compatible observed
     * candidate (WHITE before GOLD) from the wave budget.
     */
    private fun getRecoveryInjectionSkills(skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        val axes = resolvePreferredAxes()
        if (!allowsRecoveryInjection(campaign.resolvedSkillThreshold.mode, campaign.skillSpendObjective, axes.trackDistance)) {
            return emptyMap()
        }
        sessionRecoveryRuleActive = true

        // Inherited-unique recoveries count as owned though they are never injection candidates.
        val satisfiedBy: String? =
            (skillList.getObtainedSkills().keys + campaign.trainee.ownedSkillNames + skillsToBuy)
                .firstOrNull { name ->
                    val data: SkillData? = game.skillDatabase.getSkillData(name)
                    data != null && recoveryClassOf(data.iconId) != RecoveryClass.NONE
                }
        if (satisfiedBy != null) {
            sessionRecoveryRequired = false
            MessageLog.i(TAG, "[SKILLS] Recovery protection satisfied by '$satisfiedBy'.")
            return emptyMap()
        }
        sessionRecoveryRequired = true

        val candidates: List<RecoveryCandidate> =
            skillList.getAvailableSkills().values
                .filter { entry ->
                    entry.name !in skillsToBuy &&
                        entry.bIsAvailable &&
                        entry.screenPrice > 0 &&
                        // Same dead-tap exclusion as every other candidate source.
                        entry.name !in skillList.deadTapSkills &&
                        isRecoveryInjectionCandidate(entry.skillData, axes.trackDistance, axes.runningStyle, axes.trackSurface)
                }
                .map { RecoveryCandidate(it.name, recoveryClassOf(it.skillData.iconId), it.screenPrice, it.skillData.id) }
        val choice: RecoveryCandidate? = pickRecoveryCandidate(candidates)
        if (choice == null) {
            MessageLog.i(TAG, "[SKILLS] Recovery protection found no compatible observed candidate.")
            return emptyMap()
        }
        if (choice.price > availableSkillPoints) {
            MessageLog.i(
                TAG,
                "[SKILLS] Recovery protection candidate '${choice.name}' costs ${choice.price} SP and does not fit the remaining budget ($availableSkillPoints SP).",
            )
            return emptyMap()
        }
        val entry: SkillListEntry = skillList.getAvailableSkills()[choice.name] ?: return emptyMap()
        entry.buy()
        sessionRecoverySkill = choice.name
        sessionRecoveryObservedPrice = choice.price
        MessageLog.i(TAG, "[SKILLS] Recovery protection selected '${choice.name}' at ${choice.price} SP.")
        return mapOf(choice.name to choice.price)
    }

    /**
     * Retrieve all available skills following the default spending strategy.
     *
     * Currently, this strategy is synonymous with OPTIMIZE_RANK.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for the default strategy.
     */
    private fun getSkillsToBuyDefaultStrategy(skillPlanSettings: SkillPlanSettings, skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        // Currently does not implement additional logic beyond common skills.
        return emptyMap()
    }

    /** The resolved Style preference for each axis (null = no restriction). */
    private data class PreferredAxes(
        /** The resolved preferred running style, or null for no restriction. */
        val runningStyle: RunningStyle?,
        /** The resolved preferred track distance, or null for no restriction. */
        val trackDistance: TrackDistance?,
        /** The resolved preferred track surface, or null for no restriction. */
        val trackSurface: TrackSurface?,
    )

    /**
     * Resolve the global Style preference settings into concrete enum values, applying the no_preference / inherit rules.
     * Shared by Optimize Skills, Optimize Rank, and the knapsack candidate filter so the Style preference is applied
     * identically across every spending strategy.
     *
     * @return The resolved running style, track distance, and track surface (any of which may be null for no restriction).
     */
    private fun resolvePreferredAxes(): PreferredAxes {
        val runningStyle: RunningStyle? =
            when (skillSettingRunningStyleString.lowercase()) {
                "no_preference" -> null
                "inherit" -> RunningStyle.fromShortName(racingSettingRunningStyleString) ?: campaign.trainee.runningStyle
                else -> RunningStyle.fromName(skillSettingRunningStyleString)
            }
        val trackDistance: TrackDistance? =
            when (skillSettingTrackDistanceString.lowercase()) {
                "no_preference" -> null
                "inherit" -> TrackDistance.fromName(trainingSettingTrackDistanceString) ?: campaign.trainee.trackDistance
                else -> TrackDistance.fromName(skillSettingTrackDistanceString)
            }
        val trackSurface: TrackSurface? =
            when (skillSettingTrackSurfaceString.lowercase()) {
                "no_preference" -> null
                else -> TrackSurface.fromName(skillSettingTrackSurfaceString)
            }
        return PreferredAxes(runningStyle, trackDistance, trackSurface)
    }

    /**
     * Retrieve all available skills following the OptimizeSkills strategy.
     *
     * This strategy calculates optimal skills based on a community tier list and evaluates them based on their rank-to-price ratio. It filters skills to match user-specified aptitudes for running
     * style, track distance, and track surface.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for the OptimizeSkills strategy.
     */
    private fun getSkillsToBuyOptimizeSkillsStrategy(skillPlanSettings: SkillPlanSettings, skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints = availableSkillPoints

        val (preferredRunningStyle, preferredTrackDistance, preferredTrackSurface) = resolvePreferredAxes()

        MessageLog.d(TAG, "[DEBUG] getSkillsToBuyOptimizeSkillsStrategy:: Using preferred running style: $preferredRunningStyle")
        MessageLog.d(TAG, "[DEBUG] getSkillsToBuyOptimizeSkillsStrategy:: Using preferred track distance: $preferredTrackDistance")
        MessageLog.d(TAG, "[DEBUG] getSkillsToBuyOptimizeSkillsStrategy:: Using preferred track surface: $preferredTrackSurface")

        // Retrieve skills that match the specified aptitudes or are style-agnostic.
        fun getFilteredSkills(remainingSkillPoints: Int): Map<String, SkillListEntry> {
            val result: MutableMap<String, SkillListEntry> = mutableMapOf()

            result.putAll(skillList.getAptitudeIndependentSkills(preferredRunningStyle))

            if (preferredRunningStyle != null) {
                result.putAll(skillList.getRunningStyleSkills(preferredRunningStyle))
                result.putAll(skillList.getInferredRunningStyleSkills(preferredRunningStyle))
            }
            if (preferredTrackDistance != null) {
                result.putAll(skillList.getTrackDistanceSkills(preferredTrackDistance))
            }
            if (preferredTrackSurface != null) {
                result.putAll(skillList.getTrackSurfaceSkills(preferredTrackSurface))
            }

            result.values.removeAll { it.price > remainingSkillPoints }

            return result.toMap()
        }

        // Iterate until no more affordable skills are found, as purchasing can unlock new options.
        val maxIterations = 10
        var i = 0
        var remainingSkills: Map<String, SkillListEntry> = getFilteredSkills(remainingSkillPoints)
        while (remainingSkills.any { it.value.screenPrice <= remainingSkillPoints }) {
            // Group entries by community tier, with higher tiers prioritized.
            val groupedByCommunityTier: Map<Int?, List<SkillListEntry>> =
                remainingSkills.values
                    .groupBy { it.communityTier }
                    .toSortedMap(compareBy { it })

            // Iterate from the highest tier to lowest, ignoring unranked (null) entries.
            for ((communityTier, group) in groupedByCommunityTier) {
                if (communityTier == null) {
                    continue
                }

                // Sort within the tier by evaluation point ratio.
                val sortedByPointRatio: List<SkillListEntry> = group.sortedByDescending { it.evaluationPointRatio }
                for (entry in sortedByPointRatio) {
                    // Don't add duplicate entries.
                    if (entry.name in result || entry.name in skillsToBuy) {
                        continue
                    }

                    if (skipDoubleCircleUpgrades && isDoubleCircleUpgrade(entry.name)) {
                        continue
                    }

                    if (!entry.bIsAvailable || entry.screenPrice > remainingSkillPoints) {
                        continue
                    }

                    result[entry.name] = entry.screenPrice
                    remainingSkillPoints -= entry.screenPrice
                    entry.buy()
                }
            }

            remainingSkills = getFilteredSkills(remainingSkillPoints)
            if (i++ > maxIterations) {
                break
            }
        }

        // Spend remaining skill points using the Optimize Rank strategy.
        result +=
            getSkillsToBuyOptimizeRankStrategy(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = skillsToBuy + result.keys.toList(),
                availableSkillPoints = remainingSkillPoints,
            )

        return result.toMap()
    }

    /**
     * Retrieve all available skills following the Optimize Rank strategy.
     *
     * This strategy maximizes total rank by purchasing skills with the highest rank-to-price ratio. User-specified skill aptitudes are ignored in this strategy.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for the Optimize Rank strategy.
     */
    private fun getSkillsToBuyOptimizeRankStrategy(skillPlanSettings: SkillPlanSettings, skillList: SkillList, skillsToBuy: List<String>, availableSkillPoints: Int): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints = availableSkillPoints
        val (preferredRunningStyle, preferredTrackDistance, preferredTrackSurface) = resolvePreferredAxes()

        // Iterate until no more affordable skills are found, as purchasing can unlock new options.
        val maxIterations = 10
        var i = 0

        fun rankFillSkills(): Map<String, SkillListEntry> = skillList.getAvailableSkills().filterValues { rankFillAllows(it.skillData.bIsNegative, it.evaluationPoints) }
        var remainingSkills: Map<String, SkillListEntry> = rankFillSkills()
        while (remainingSkills.any { it.value.screenPrice <= remainingSkillPoints }) {
            val sortedByPointRatio: List<SkillListEntry> =
                remainingSkills.values
                    .sortedByDescending { it.evaluationPointRatio }

            for (entry in sortedByPointRatio) {
                // Don't add duplicate entries.
                if (entry.name in result || entry.name in skillsToBuy) {
                    continue
                }

                if (skipDoubleCircleUpgrades && isDoubleCircleUpgrade(entry.name)) {
                    continue
                }

                // Strictly respect the Style preference (no_preference resolves to null and never restricts);
                // otherwise OPTIMIZE_RANK bought purely by ratio and off-preference skills leaked in via the
                // leftover tail.
                if (!matchesPreference(
                        entry.trackDistance,
                        entry.runningStyle,
                        entry.inferredRunningStyles,
                        entry.trackSurface,
                        preferredTrackDistance,
                        preferredRunningStyle,
                        preferredTrackSurface,
                    )
                ) {
                    continue
                }

                if (entry.screenPrice > remainingSkillPoints) {
                    continue
                }

                result[entry.name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }

            remainingSkills = rankFillSkills()

            if (i++ > maxIterations) {
                break
            }
        }

        return result.toMap()
    }

    /**
     * Retrieve skills to purchase using the grouped 0/1 knapsack DP strategy.
     *
     * Builds [KnapsackGroup]s from the live skill list using [SkillDatabase.skillUpgradeChains] so
     * base ○ and its upgrade ◎ form one mutually-exclusive group. The DP picks the optimal combo per
     * group within budget, fixing the greedy-by-ratio bug where a base could be bought now and its
     * upgrade later, wasting the base's cost when only the upgrade activates.
     *
     * Single planning pass + single buy pass: we don't re-run the DP after each purchase like
     * [getSkillsToBuyOptimizeRankStrategy] does for greedy. The DP already considers the full
     * candidate list as a batch, so iterative re-scanning would mostly re-compute the same plan.
     * If any planned skill becomes unavailable mid-execution (e.g. scrolls off-screen), it's
     * picked up by the next skillPointCheck cycle.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param skillsToBuy The list of skills already planned for purchase by the common phase.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for the Knapsack strategy.
     */
    private fun getSkillsToBuyOptimizeKnapsackStrategy(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()
        if (availableSkillPoints <= 0) return result.toMap()

        // Same Style-preference gate as Optimize Skills/Rank (upstream's gate omitted the knapsack).
        val (preferredRunningStyle, preferredTrackDistance, preferredTrackSurface) = resolvePreferredAxes()
        val tailFilter = careerEndTailFilter(campaign.skillSpendObjective, sessionEffectiveTrigger, careerEndBuyAnySkill, preferredRunningStyle != null)
        when (tailFilter) {
            CareerEndTailFilter.RUNNING_STYLE -> MessageLog.i(TAG, "[KNAPSACK] Career end with the rank objective: the tail may buy $preferredRunningStyle skills at any distance or surface.")
            CareerEndTailFilter.ANY_SKILL -> MessageLog.i(TAG, "[KNAPSACK] Career end with the rank objective: the tail may buy any skill by rating (setting on).")
            CareerEndTailFilter.PROFILE -> Unit
        }
        val available =
            skillList.getAvailableSkills().filterValues { entry ->
                entry.bIsAvailable &&
                    entry.name !in skillsToBuy &&
                    entry.screenPrice > 0 &&
                    // Never re-plan a skill whose taps the game refused this session: the scan can list an owned
                    // skill as buyable and planning it burns a real candidate's budget.
                    entry.name !in skillList.deadTapSkills &&
                    // Drop ◎ upgrades before buildKnapsackGroups, or the chain group still offers [○, ◎] and the
                    // toggle no-ops at careerComplete.
                    (!skipDoubleCircleUpgrades || !isDoubleCircleUpgrade(entry.name)) &&
                    knapsackTailAllows(
                        tailFilter,
                        entry.trackDistance,
                        entry.runningStyle,
                        entry.inferredRunningStyles,
                        entry.trackSurface,
                        preferredTrackDistance,
                        preferredRunningStyle,
                        preferredTrackSurface,
                    )
            }
        if (available.isEmpty()) {
            MessageLog.i(TAG, "[KNAPSACK] No available skills to plan against. Budget remaining: $availableSkillPoints.")
            return result.toMap()
        }

        val candidates: List<SkillCandidate> =
            available.values.map { entry ->
                SkillCandidate(
                    name = entry.name,
                    price = entry.screenPrice,
                    evaluationPoints = entry.evaluationPoints,
                    isNegative = entry.skillData.bIsNegative,
                    isInheritedUnique = entry.skillData.bIsInheritedUnique,
                    isUserPlanned = entry.name in skillPlanSettings.skillNames,
                    communityTier = entry.skillData.communityTier,
                )
            }

        val groups =
            buildKnapsackGroups(
                candidates = candidates,
                upgradeChains = game.skillDatabase.skillUpgradeChains,
                requiredNames = emptySet(), // Common phase already handled user-planned/negative/inherited.
            )

        MessageLog.d(
            TAG,
            "[KNAPSACK] Planning ${candidates.size} candidates across ${groups.size} groups under $availableSkillPoints SP.",
        )

        val plan: List<Pair<String, Int>> = calculateOptimizeKnapsackPurchases(groups, availableSkillPoints)
        if (plan.isEmpty()) {
            MessageLog.i(TAG, "[KNAPSACK] DP returned empty plan (no feasible purchases under budget).")
            return result.toMap()
        }

        val planTotal = plan.sumOf { it.second }
        MessageLog.i(
            TAG,
            "[KNAPSACK] DP plan: ${plan.size} skills for $planTotal SP. Skills: ${plan.joinToString { "${it.first}(${it.second})" }}",
        )

        // Iterate in DP order (base before upgrade within a chain): buying the upgrade requires the base to be owned.
        var remaining = availableSkillPoints
        for ((name, price) in plan) {
            if (price > remaining) {
                MessageLog.w(TAG, "[KNAPSACK] Skipping \"$name\" — DP plan price $price exceeds remaining budget $remaining (live re-scan may have changed prices).")
                continue
            }
            val entry = skillList.getAvailableSkills()[name]
            if (entry == null || !entry.bIsAvailable) {
                MessageLog.w(TAG, "[KNAPSACK] Planned skill \"$name\" no longer available on screen. Skipping.")
                continue
            }
            entry.buy()
            result[name] = entry.screenPrice
            remaining -= entry.screenPrice
        }

        return result.toMap()
    }

    /**
     * Constrained career-end fallback (sparks objective at CAREER_COMPLETE only): spends what the planned phase left
     * on profile-compatible skills, since the game discards unspent points at Finish.
     */
    private fun getSkillsToBuyCareerEndFallback(
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()
        if (availableSkillPoints <= 0) return result.toMap()

        val (preferredRunningStyle, preferredTrackDistance, preferredTrackSurface) = resolvePreferredAxes()
        val available =
            skillList.getAvailableSkills().filterValues { entry ->
                entry.bIsAvailable &&
                    entry.name !in skillsToBuy &&
                    entry.screenPrice > 0 &&
                    // Same dead-tap exclusion as the knapsack strategy.
                    entry.name !in skillList.deadTapSkills &&
                    careerEndFallbackCandidateAllowed(
                        isNegative = entry.skillData.bIsNegative,
                        isInheritedUnique = entry.skillData.bIsInheritedUnique,
                        isDoubleCircle = isDoubleCircleUpgrade(entry.name),
                        skipDoubleCircleUpgrades = skipDoubleCircleUpgrades,
                        matchesAxes =
                            matchesPreference(
                                entry.trackDistance,
                                entry.runningStyle,
                                entry.inferredRunningStyles,
                                entry.trackSurface,
                                preferredTrackDistance,
                                preferredRunningStyle,
                                preferredTrackSurface,
                            ),
                    )
            }
        if (available.isEmpty()) {
            MessageLog.i(TAG, "[KNAPSACK] Career-end fallback found no compatible skills to plan against. Budget remaining: $availableSkillPoints.")
            return result.toMap()
        }

        val candidates: List<SkillCandidate> =
            available.values.map { entry ->
                SkillCandidate(
                    name = entry.name,
                    price = entry.screenPrice,
                    evaluationPoints = entry.evaluationPoints,
                    isNegative = entry.skillData.bIsNegative,
                    isInheritedUnique = entry.skillData.bIsInheritedUnique,
                    isUserPlanned = false,
                    communityTier = entry.skillData.communityTier,
                )
            }
        val groups =
            buildKnapsackGroups(
                candidates = candidates,
                upgradeChains = game.skillDatabase.skillUpgradeChains,
                requiredNames = emptySet(),
            )
        val plan: List<Pair<String, Int>> = calculateOptimizeKnapsackPurchases(groups, availableSkillPoints)
        if (plan.isEmpty()) {
            MessageLog.i(TAG, "[KNAPSACK] Career-end fallback DP returned empty plan (no feasible purchases under budget).")
            return result.toMap()
        }

        val planTotal = plan.sumOf { it.second }
        MessageLog.i(
            TAG,
            "[KNAPSACK] Career-end fallback plan: ${plan.size} compatible skills for $planTotal SP. Skills: ${plan.joinToString { "${it.first}(${it.second})" }}",
        )

        var remaining = availableSkillPoints
        for ((name, price) in plan) {
            if (price > remaining) {
                MessageLog.w(TAG, "[KNAPSACK] Skipping \"$name\" (career-end fallback): plan price $price exceeds remaining budget $remaining.")
                continue
            }
            val entry = skillList.getAvailableSkills()[name]
            if (entry == null || !entry.bIsAvailable) {
                MessageLog.w(TAG, "[KNAPSACK] Career-end fallback skill \"$name\" no longer available on screen. Skipping.")
                continue
            }
            entry.buy()
            result[name] = entry.screenPrice
            remaining -= entry.screenPrice
        }

        return result.toMap()
    }

    /**
     * Retrieve all available skills to purchase based on the specified spending strategy.
     *
     * @param skillPlanSettings The [SkillPlanSettings] to follow.
     * @param skillList The [SkillList] to analyze.
     * @param availableSkillPoints The current amount of available skill points.
     * @return A map of skill names to their prices for all skills to be purchased.
     */
    fun getSkillsToBuy(skillPlanSettings: SkillPlanSettings, skillList: SkillList, availableSkillPoints: Int): Map<String, Int> {
        MessageLog.i(TAG, "[SKILLS] Beginning process of calculating skills to purchase...")

        if (!skillPlanSettings.bIsEnabled) {
            MessageLog.i(TAG, "[SKILLS] Skill plan is disabled. No skills will be purchased.")
            return emptyMap()
        }

        val result: MutableMap<String, Int> = mutableMapOf()

        // Execute common skill checks first.
        result +=
            getSkillsToBuyCommon(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )

        // Adaptive + sparks skips the strategy tail so leftover budget is not drained into spark-diluting filler;
        // Manual always allows it.
        val bAllowStrategyTail: Boolean = strategyTailAllowed(campaign.resolvedSkillThreshold.mode, campaign.skillSpendObjective)
        // At CAREER_COMPLETE the game DISCARDS unspent points, so a sparks session extends into the constrained
        // fallback (a live career handed 716 points to Finish under pure planned-only).
        val bCareerEndFallback: Boolean =
            !bAllowStrategyTail &&
                careerEndConstrainedFallbackAllowed(campaign.resolvedSkillThreshold.mode, campaign.skillSpendObjective, sessionEffectiveTrigger)
        sessionStrategyTailAllowed = bAllowStrategyTail
        sessionCareerEndFallback = if (bCareerEndFallback) true else null
        if (!bAllowStrategyTail) {
            if (bCareerEndFallback) {
                MessageLog.i(
                    TAG,
                    "[SKILLS] Career-end spending (${campaign.skillSpendObjective.token()} objective): planned skills first, then profile-compatible skills (unspent points are discarded by the game at Finish).",
                )
            } else {
                MessageLog.i(
                    TAG,
                    "[SKILLS] Planned-only spending (${campaign.skillSpendObjective.token()} objective): skipping the ${skillPlanSettings.strategy.name} strategy tail.",
                )
            }
        }

        // Execute strategy-specific checks.
        if (bAllowStrategyTail) {
            result +=
                when (skillPlanSettings.strategy) {
                    SpendingStrategy.DEFAULT -> {
                        getSkillsToBuyDefaultStrategy(
                            skillPlanSettings = skillPlanSettings,
                            skillList = skillList,
                            skillsToBuy = result.keys.toList(),
                            availableSkillPoints = availableSkillPoints - result.values.sum(),
                        )
                    }

                    SpendingStrategy.OPTIMIZE_SKILLS -> {
                        getSkillsToBuyOptimizeSkillsStrategy(
                            skillPlanSettings = skillPlanSettings,
                            skillList = skillList,
                            skillsToBuy = result.keys.toList(),
                            availableSkillPoints = availableSkillPoints - result.values.sum(),
                        )
                    }

                    SpendingStrategy.OPTIMIZE_RANK -> {
                        getSkillsToBuyOptimizeRankStrategy(
                            skillPlanSettings = skillPlanSettings,
                            skillList = skillList,
                            skillsToBuy = result.keys.toList(),
                            availableSkillPoints = availableSkillPoints - result.values.sum(),
                        )
                    }

                    SpendingStrategy.OPTIMIZE_KNAPSACK -> {
                        getSkillsToBuyOptimizeKnapsackStrategy(
                            skillPlanSettings = skillPlanSettings,
                            skillList = skillList,
                            skillsToBuy = result.keys.toList(),
                            availableSkillPoints = availableSkillPoints - result.values.sum(),
                        )
                    }
                }
        } else if (bCareerEndFallback) {
            // Always the knapsack: the one path that enforces the Style-preference axes.
            result +=
                getSkillsToBuyCareerEndFallback(
                    skillList = skillList,
                    skillsToBuy = result.keys.toList(),
                    availableSkillPoints = availableSkillPoints - result.values.sum(),
                )
        }

        MessageLog.v(TAG, "================ Skills To Buy =================")
        for ((name, price) in result) {
            MessageLog.v(TAG, "\t$name: $price")
        }
        MessageLog.v(
            TAG,
            "\n\tTOTAL: ${result.values.sum()} / ${if (USE_MOCK_DATA) MOCK_SKILL_POINTS else skillList.skillPoints} pts with ${if (USE_MOCK_DATA) MOCK_SKILL_POINTS else skillList.skillPoints - result.values.sum()} left over pts",
        )
        MessageLog.v(TAG, "================================================")

        return result.toMap()
    }

    /**
     * Log the details of a detected skill list entry and handle its purchase if planned.
     *
     * @param entry The detected [SkillListEntry].
     * @param point The screen location of the skill's purchase button.
     * @param skillsToBuy The list of skill names planned for purchase.
     * @param skillList The [SkillList] managing the current scan.
     * @return True if all planned skills have been purchased, triggering an early exit; false otherwise.
     */
    private fun onSkillListEntryDetected(entry: SkillListEntry, point: Point, skillsToBuy: List<String>, skillList: SkillList): Boolean {
        // Evaluate exits on every NON-candidate entry, not only after a buy (the post-buy check never re-runs once
        // the last buyable skill is bought, so one unbuyable leftover made every pass walk the full list); a
        // candidate entry gets its buy attempt first since its recorded price may be stale-high.
        val bIsBuyCandidate =
            !entry.bIsObtained && !entry.bIsVirtual && entry.name in skillsToBuy &&
                // A dead-tapped row already ran the full tap-retry budget this session.
                entry.name !in skillList.deadTapSkills
        if (!bIsBuyCandidate) {
            val outstandingSkills: List<String> = skillsToBuy.filter { it !in skillList.getObtainedSkills() }
            if (outstandingSkills.isEmpty()) {
                MessageLog.i(TAG, "[SKILLS] All skills purchased. Exiting loop early...")
                return true
            }
            val bAnyStillBuyable =
                outstandingSkills.any { name ->
                    // An unknown price means the row has not been seen this scan: keep scrolling. Dead-tapped
                    // names never justify more scrolling.
                    val livePrice: Int? = skillList.getAllSkills()[name]?.screenPrice
                    name !in skillList.deadTapSkills && (livePrice == null || livePrice <= skillList.skillPoints)
                }
            if (!bAnyStillBuyable) {
                MessageLog.i(TAG, "[SKILLS] No remaining planned skill fits the ${skillList.skillPoints} SP budget (outstanding: ${outstandingSkills.joinToString(", ")}). Exiting the pass early...")
                return true
            }
            return false
        }

        // Determine if there are other in-place versions of this skill that need to be purchased.
        if (entry.bIsInPlace) {
            val namesToBuy: List<String> =
                listOf(entry.name) +
                    entry.getUpgradeNames().filter { it in skillsToBuy }

            for (name in namesToBuy) {
                val purchaseResult: SkillListEntry? = skillList.buySkill(name, point)
                if (purchaseResult != null) {
                    MessageLog.i(TAG, "[INFO] Buying \"${purchaseResult.name}\" for ${purchaseResult.price} pts")
                    campaign.trainee.ownedSkillNames.add(purchaseResult.name)
                    sessionVerifiedBuys.add(purchaseResult.name)
                }
            }
        } else {
            val purchaseResult: SkillListEntry? = skillList.buySkill(entry.name, point)
            if (purchaseResult != null) {
                MessageLog.i(TAG, "[INFO] Buying \"${purchaseResult.name}\" for ${purchaseResult.price} pts")
                campaign.trainee.ownedSkillNames.add(purchaseResult.name)
                sessionVerifiedBuys.add(purchaseResult.name)
            }
        }

        // Check if all planned skills have been purchased to allow for an early exit.
        val obtained: Map<String, SkillListEntry> = skillList.getObtainedSkills()
        if (skillsToBuy.all { it in obtained }) {
            MessageLog.i(TAG, "[SKILLS] All skills purchased. Exiting loop early...")
            return true
        }

        return false
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Start the skill purchasing process.
     *
     * This method orchestrates the full flow: identifying affordable skills based on the user's settings and then interacting with the game UI to buy them.
     *
     * @param skillPlanName Optional name of the skill plan to execute. If null, defaults based on career status.
     * @return True if the process completed successfully, false otherwise.
     */
    fun start(skillPlanName: String? = null, trigger: SkillCheckTrigger? = null): Boolean {
        // Null on early-exit paths so those records omit the fields rather than carry stale ones.
        sessionStrategyTailAllowed = null
        sessionCareerEndFallback = null
        sessionEffectiveTrigger = null
        sessionPlanningScanComplete = null
        sessionRecoveryRuleActive = null
        sessionRecoveryRequired = null
        sessionRecoverySkill = null
        sessionRecoveryObservedPrice = null
        sessionVerifiedBuys.clear()

        val bitmap: Bitmap = game.imageUtils.getSourceBitmap()

        val skillList = SkillList(game, campaign)

        // Verify that the bot is currently at the skill list screen.
        val bIsCareerComplete: Boolean = skillList.checkCareerCompleteSkillListScreen(bitmap)
        // The career-end Learn list is too long for the ordinary list budget and the finalization guard approves
        // only a read proven to reach the end, so it gets the dedicated budget.
        if (bIsCareerComplete) skillList.scanBudgetMs = CAREER_END_SCAN_BUDGET_MS
        if (!bIsCareerComplete && !skillList.checkSkillListScreen(bitmap)) {
            MessageLog.e(TAG, "[ERROR] start:: Not at skill list screen. Aborting...")
            recordSkillSpend(SkillSpendOutcome.FAILED, trigger, skillPlanName, null)
            return false
        }

        // Determine which skill plan to execute based on the current context.
        val skillPlanSettings: SkillPlanSettings =
            if (skillPlanName == null) {
                val resolvedPlanName = if (bIsCareerComplete) "careerComplete" else "preFinals"
                val resolvedPlan: SkillPlanSettings? = skillPlans[resolvedPlanName]
                if (resolvedPlan == null) {
                    // A degraded/empty plans map must abort gracefully, not crash.
                    MessageLog.e(TAG, "[ERROR] start:: No '$resolvedPlanName' skill plan found (plans map empty or missing the key). Aborting skill purchase.")
                    recordSkillSpend(SkillSpendOutcome.FAILED, trigger, resolvedPlanName, null)
                    return false
                }
                resolvedPlan
            } else {
                val tmpPlan: SkillPlanSettings? = skillPlans[skillPlanName]
                if (tmpPlan == null) {
                    MessageLog.e(TAG, "[ERROR] start:: Invalid skill plan name: $skillPlanName")
                    recordSkillSpend(SkillSpendOutcome.FAILED, trigger, skillPlanName, null)
                    return false
                }
                tmpPlan
            }

        // A null trigger (the debug harness): omit it rather than guess.
        val resolvedPlanKey: String = skillPlanName ?: if (bIsCareerComplete) PLAN_CAREER_COMPLETE else PLAN_PRE_FINALS
        val effectiveTrigger: SkillCheckTrigger? = trigger ?: if (bIsCareerComplete) SkillCheckTrigger.CAREER_COMPLETE else null
        sessionEffectiveTrigger = effectiveTrigger

        // If no purchasing options are enabled, exit early to avoid unnecessary scanning.
        if (
            skillPlanSettings.skillNames.isEmpty() &&
            skillPlanSettings.strategy == SpendingStrategy.DEFAULT &&
            !skillPlanSettings.bEnableBuyInheritedUniqueSkills &&
            !skillPlanSettings.bEnableBuyNegativeSkills
        ) {
            MessageLog.w(TAG, "[WARN] start:: Skill Plan is empty and no options to purchase any skills are enabled. Aborting...")
            recordSkillSpend(SkillSpendOutcome.EMPTY_PLAN, effectiveTrigger, resolvedPlanKey, skillPlanSettings)
            skillList.cancelAndExit()
            return true
        }

        // Ensure that the trainee's aptitudes are up-to-date before calculating purchases.
        if (!USE_MOCK_DATA && !campaign.trainee.bHasUpdatedAptitudes) {
            skillList.checkStats()
        }

        val skillPoints: Int =
            if (USE_MOCK_DATA) {
                MOCK_SKILL_POINTS
            } else {
                skillList.detectSkillPoints(bitmap) ?: 0
            }

        // Mid-career cannot-afford early exit (42 is upstream's approximation, never consumed by the finalization
        // guard). Adaptive careerComplete sessions skip it: the guard needs a complete scan, not a price-floor
        // shortcut.
        if (skillPoints < SKILL_POINTS_EARLY_EXIT_FLOOR) {
            val guardNeedsEvidence =
                campaign.resolvedSkillThreshold.mode == SkillSpendMode.ADAPTIVE && effectiveTrigger == SkillCheckTrigger.CAREER_COMPLETE
            if (!guardNeedsEvidence) {
                MessageLog.i(TAG, "[SKILLS] Skill Points < $SKILL_POINTS_EARLY_EXIT_FLOOR. Cannot afford any skills. Aborting...")
                recordSkillSpend(SkillSpendOutcome.NOTHING_TO_BUY, effectiveTrigger, resolvedPlanKey, skillPlanSettings, spBefore = skillPoints, spAfter = skillPoints)
                skillList.cancelAndExit()
                return true
            }
            MessageLog.i(
                TAG,
                "[SKILLS] Skill Points $skillPoints are below the mid-career early-exit heuristic, but the career-end session still scans so the finalization guard gets complete candidate-exhaustion evidence.",
            )
        }

        // Gather and parse all skill entries from the screen.
        skillList.parseSkillListEntries(bUseMockData = USE_MOCK_DATA)
        sessionPlanningScanComplete = if (USE_MOCK_DATA) true else skillList.lastScanComplete
        if (skillList.getAllSkills().isEmpty()) {
            MessageLog.e(TAG, "[ERROR] start:: Failed to detect skills.")
            recordSkillSpend(SkillSpendOutcome.ABORTED_PARSE, effectiveTrigger, resolvedPlanKey, skillPlanSettings, spBefore = skillPoints, spAfter = skillPoints)
            skillList.cancelAndExit()
            return false
        }

        skillList.printSkillListEntries(verbose = true)

        // Screen-confirmed ownership: the post-planning reset must not clear it or upgrade-chain pricing and
        // ownership reads corrupt.
        val ownedAtParse: Set<String> = skillList.getObtainedSkills().keys

        // Calculate the list of skills to purchase based on settings and points.
        var skillsToPurchase: Map<String, Int> =
            getSkillsToBuy(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                availableSkillPoints = skillPoints,
            )

        // Exit if no skills were identified for purchase.
        if (skillsToPurchase.isEmpty()) {
            recordSkillSpend(SkillSpendOutcome.NOTHING_TO_BUY, effectiveTrigger, resolvedPlanKey, skillPlanSettings, spBefore = skillPoints, spAfter = skillList.skillPoints)
            skillList.cancelAndExit()
            campaign.trainee.skillPoints = skillList.skillPoints
            return true
        }

        // Planner output across rounds, deduplicated by name at the first planned price (the `proposed` telemetry
        // set).
        val proposedByName: LinkedHashMap<String, ProposedSkill> = LinkedHashMap()
        val allPlannedNames: MutableSet<String> = mutableSetOf()

        // The scan can list an owned skill as buyable, the DP burns budget on that phantom and its taps die,
        // leaving real candidates unbought (this stalled a queue). So each round re-plans over the live budget
        // with dead-tapped names excluded, and the pool shrinks until the loop converges.
        val maxPlanRounds = 3
        var totalEntriesSeen = 0
        for (planRound in 1..maxPlanRounds) {
            for (skill in skillsToPurchase) {
                proposedByName.putIfAbsent(skill.key, ProposedSkill(skill.key, skill.value))
            }
            allPlannedNames += skillsToPurchase.keys
            val verifiedBuysBeforeRound: Int = sessionVerifiedBuys.size
            val deadTapsBeforeRound: Int = skillList.deadTapSkills.size

            skillList.sellAllSkills(preserve = ownedAtParse + sessionVerifiedBuys)

            // Iterate through the list again and perform the confirmed purchases.
            //
            // A single scroll pass is not trusted to cover the whole list: the end-of-list heuristics
            // can conclude "done" early (dropped swipes, or purchases reflowing rows mid-pass), stranding
            // planned skills unbought. Re-run from the top while planned skills remain. Re-runs are safe:
            // obtained entries are skipped in the callback, and already-selected rows no longer present a
            // matchable Skill Up button.
            val maxBuyPasses = 3
            for (buyPass in 1..maxBuyPasses) {
                // Heal a wiped Accessibility grant: the emulator drops the service and every tap silently stops
                // registering.
                if (!game.ensureAccessibilityService()) {
                    MessageLog.e(TAG, "[SKILLS] The Accessibility Service is off and cannot be restored without WRITE_SECURE_SETTINGS; skill taps in this pass will not land.")
                }
                var entriesSeenThisPass = 0
                skillList.parseSkillListEntries { currentList: SkillList, entry: SkillListEntry, point: Point ->
                    entriesSeenThisPass++
                    onSkillListEntryDetected(
                        entry = entry,
                        point = point,
                        skillsToBuy = skillsToPurchase.keys.toList(),
                        skillList = currentList,
                    )
                }
                totalEntriesSeen += entriesSeenThisPass

                val unbought: List<String> = skillsToPurchase.keys.filter { it !in skillList.getObtainedSkills() }
                // Drop what the live budget can no longer cover (prices drift between parse and buy) and dead-
                // tapped names.
                val remaining: List<String> =
                    unbought.filter { name ->
                        val price: Int = skillList.getAllSkills()[name]?.screenPrice ?: skillsToPurchase[name] ?: Int.MAX_VALUE
                        price <= skillList.skillPoints && name !in skillList.deadTapSkills
                    }
                val droppedUnaffordable: List<String> = unbought - remaining.toSet()
                if (droppedUnaffordable.isNotEmpty()) {
                    MessageLog.w(TAG, "[WARN] Dropping ${droppedUnaffordable.size} planned skill(s) no longer buyable with ${skillList.skillPoints} SP: ${droppedUnaffordable.joinToString(", ")}.")
                }
                if (remaining.isEmpty()) {
                    if (buyPass > 1 || droppedUnaffordable.isNotEmpty()) {
                        MessageLog.i(TAG, "[SKILLS] Nothing further to buy after $buyPass buy pass(es). Proceeding.")
                    }
                    break
                }
                if (entriesSeenThisPass == 0) {
                    // The pass saw NOTHING (unreadable list or blocked input): try to clear a blocking dialog first.
                    MessageLog.e(TAG, "[ERROR] Buy pass $buyPass processed zero entries - screen unreadable or input blocked. Attempting dialog recovery before retry.")
                    campaign.handleDialogs()
                    game.wait(1.0, skipWaitingForLoading = true)
                }
                if (buyPass < maxBuyPasses) {
                    MessageLog.w(TAG, "[WARN] Buy pass $buyPass ended with ${remaining.size} planned skill(s) unbought: ${remaining.joinToString(", ")}. Re-running the buy pass...")
                } else {
                    MessageLog.w(TAG, "[WARN] ${remaining.size} planned skill(s) still unbought after $maxBuyPasses buy passes: ${remaining.joinToString(", ")}. Confirming what was bought.")
                }
            }

            if (planRound >= maxPlanRounds) break
            // Extra rounds are CAREER-END ONLY: points expire at Finish and the guard refuses to Finish over
            // spendable money; mid-career, leftover SP is deliberate reserve.
            if (!bIsCareerComplete) break
            val liveSp: Int = skillList.skillPoints
            val stillAffordable: Int =
                classifyRemainingCandidates(buildRemainingCandidates(skillList), liveSp, skipDoubleCircleUpgrades).affordableCount
            if (stillAffordable == 0) break
            val roundProgress: Boolean =
                sessionVerifiedBuys.size > verifiedBuysBeforeRound || skillList.deadTapSkills.size > deadTapsBeforeRound
            if (!roundProgress) {
                MessageLog.w(TAG, "[WARN] Plan round $planRound made no progress ($stillAffordable affordable candidate(s) remain with $liveSp SP); not re-planning against a stuck screen.")
                break
            }
            val nextPlan: Map<String, Int> =
                getSkillsToBuy(
                    skillPlanSettings = skillPlanSettings,
                    skillList = skillList,
                    availableSkillPoints = liveSp,
                )
            if (nextPlan.isEmpty()) {
                MessageLog.i(TAG, "[SKILLS] The classifier counts $stillAffordable affordable candidate(s) but the planner returned no plan under $liveSp SP (strategy filters differ); proceeding to confirm.")
                break
            }
            skillsToPurchase = nextPlan
            MessageLog.i(TAG, "[SKILLS] Plan round ${planRound + 1}: re-planning $stillAffordable remaining affordable candidate(s) under $liveSp SP.")
        }

        // Every pass blind and nothing bought: do not blind-confirm (a misplaced Confirm/Back sequence silently
        // loses selections).
        val boughtAny: Boolean = allPlannedNames.any { it in skillList.getObtainedSkills() && it !in ownedAtParse }
        if (!boughtAny && totalEntriesSeen == 0) {
            MessageLog.e(TAG, "[ERROR] start:: All buy passes processed zero entries and nothing was bought. Not confirming; aborting the skill plan.")
            recordSkillSpend(
                SkillSpendOutcome.ABORTED_PARSE,
                effectiveTrigger,
                resolvedPlanKey,
                skillPlanSettings,
                spBefore = skillPoints,
                spAfter = skillList.skillPoints,
                proposed = proposedByName.values.toList(),
                confirmed = confirmedPurchases(skillList, allPlannedNames, ownedAtParse),
                skillList = skillList,
            )
            campaign.trainee.skillPoints = skillList.skillPoints
            return false
        }

        if (!game.ensureAccessibilityService()) {
            MessageLog.e(TAG, "[SKILLS] The Accessibility Service is off and cannot be restored without WRITE_SECURE_SETTINGS; the purchase commit below cannot land.")
        }
        val committed: Boolean = skillList.confirmAndExit()
        if (!committed) {
            MessageLog.e(TAG, "[ERROR] start:: Purchase commit could not be verified - selections may still be pending on the Learn screen.")
        }
        recordSkillSpend(
            if (committed) SkillSpendOutcome.COMMITTED else SkillSpendOutcome.COMMIT_UNVERIFIED,
            effectiveTrigger,
            resolvedPlanKey,
            skillPlanSettings,
            spBefore = skillPoints,
            spAfter = skillList.skillPoints,
            proposed = proposedByName.values.toList(),
            confirmed = confirmedPurchases(skillList, allPlannedNames, ownedAtParse),
            skillList = skillList,
        )
        campaign.trainee.skillPoints = skillList.skillPoints
        return committed
    }

    /**
     * On screen as obtained now and not owned at parse. Evidence, never intent: a silently missed tap must not count
     * as a purchase.
     */
    private fun confirmedPurchases(skillList: SkillList, planned: Set<String>, ownedAtParse: Set<String>): List<String> {
        val obtained: Map<String, SkillListEntry> = skillList.getObtainedSkills()
        return planned.filter { it in obtained && it !in ownedAtParse }
    }

    /** Best-effort: runCatching so a corpus failure cannot change what [start] returns. */
    @Suppress("LongParameterList")
    private fun recordSkillSpend(
        outcome: SkillSpendOutcome,
        trigger: SkillCheckTrigger?,
        planKey: String?,
        settings: SkillPlanSettings?,
        spBefore: Int? = null,
        spAfter: Int? = null,
        proposed: List<ProposedSkill> = emptyList(),
        confirmed: List<String> = emptyList(),
        skillList: SkillList? = null,
    ) {
        // The points delta is the arbiter: if purchases happened that the obtained set never saw, every "skipped"
        // verdict below is unsound, so flag the gap and name nothing as unbought.
        val confirmedIncomplete: Boolean =
            SkillSpendTelemetry.confirmationIsIncomplete(proposed, confirmed.toSet(), spBefore, spAfter)
        // Assigned independent of the corpus append: a telemetry IO failure must never blind the Finish guard.
        lastSessionEvidence =
            computeFinalizeEvidence(
                outcome = outcome,
                trigger = trigger,
                planKey = planKey,
                skillList = skillList,
                spAfter = spAfter,
                confirmedIncomplete = confirmedIncomplete,
            )
        runCatching {
            val livePrices: Map<String, Int> =
                skillList?.getAllSkills()?.mapValues { it.value.screenPrice } ?: emptyMap()
            val skipped =
                if (proposed.isEmpty() || confirmedIncomplete) {
                    emptyList()
                } else {
                    SkillSpendTelemetry.deriveSkipped(proposed, confirmed.toSet(), livePrices, spAfter ?: 0)
                }
            val record =
                SkillSpendTelemetry.buildRecord(
                    timestamp = System.currentTimeMillis(),
                    outcome = outcome,
                    trigger = trigger,
                    planKey = planKey,
                    strategy = settings?.strategy?.name,
                    trainee = campaign.trainee.name.ifEmpty { null }?.replace(" ", "_"),
                    scenario = game.scenario.ifEmpty { null }?.replace(" ", "_"),
                    fp = campaign.currentConfigFingerprint(),
                    turn = campaign.date.day,
                    spBefore = spBefore,
                    spAfter = spAfter,
                    proposed = proposed,
                    confirmed = confirmed,
                    skipped = skipped,
                    confirmedIncomplete = confirmedIncomplete,
                    // The ACTING policy Campaign resolved at construction, so the record cannot disagree with the
                    // decision that governed the run.
                    threshold = campaign.resolvedSkillThreshold.value,
                    tier = campaign.resolvedSkillThreshold.tierToken(),
                    reason = campaign.resolvedSkillThreshold.reason,
                    objective = campaign.skillSpendObjective.token(),
                    criticalRace = campaign.activeTriggerContext?.takeIf { it.trigger == trigger }?.criticalRace,
                    criticalRaceSource = campaign.activeTriggerContext?.takeIf { it.trigger == trigger }?.criticalRaceSource,
                    turnsUntilRace = campaign.activeTriggerContext?.takeIf { it.trigger == trigger }?.turnsUntilRace,
                    plannedSkill = campaign.activeTriggerContext?.takeIf { it.trigger == trigger }?.plannedSkill,
                    plannedSkillObservedPrice = campaign.activeTriggerContext?.takeIf { it.trigger == trigger }?.plannedSkillObservedPrice,
                    strategyTailAllowed = sessionStrategyTailAllowed,
                    careerEndFallback = sessionCareerEndFallback,
                    recoveryRuleActive = sessionRecoveryRuleActive,
                    recoveryRequired = sessionRecoveryRequired,
                    recoverySkill = sessionRecoverySkill,
                    recoveryObservedPrice = sessionRecoveryObservedPrice,
                )
            OutcomeCorpus.append(game.myContext, record)
            MessageLog.i(
                TAG,
                "[SKILL_SPEND] ${outcome.token()} plan=$planKey trigger=${trigger?.name ?: "-"} sp=${spBefore ?: "-"}->${spAfter ?: "-"} " +
                    "proposed=${proposed.size} confirmed=${confirmed.size}${if (confirmedIncomplete) " (confirmation incomplete)" else ""}",
            )
        }.onFailure {
            MessageLog.w(TAG, "[SKILL_SPEND] Failed to append the skill-spend record: $it")
        }

        // Only a session that genuinely saw the skill screen refreshes the observed-availability store (AVAILABLE
        // rows only): a failed parse is not evidence of absence. Outside the telemetry runCatching so a corpus
        // failure cannot starve it.
        runCatching {
            if (skillList != null &&
                outcome in setOf(SkillSpendOutcome.COMMITTED, SkillSpendOutcome.COMMIT_UNVERIFIED, SkillSpendOutcome.NOTHING_TO_BUY)
            ) {
                val availableWithPrices: Map<String, Int> =
                    skillList.getAllSkills().filterValues { it.bIsAvailable }.mapValues { it.value.screenPrice }
                campaign.plannedSkillEvidence.recordParse(
                    availableWithPrices = availableWithPrices,
                    parseTurn = campaign.date.day,
                    fromAffordableSession = trigger == SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE,
                    confirmedPurchases = confirmed,
                )
            }
        }.onFailure {
            MessageLog.w(TAG, "[SKILL_SPEND] Failed to update planned-skill evidence: $it")
        }
    }

    /**
     * getAllSkills keeps obtained and virtual rows as explicit freshness facts for [classifyRemainingCandidates];
     * shared with the buy-round loop so both use the same rules, including dead-tap exclusion.
     */
    private fun buildRemainingCandidates(skillList: SkillList): List<RemainingCandidate> {
        val axes = resolvePreferredAxes()
        return skillList.getAllSkills().map { (name, entry) ->
            RemainingCandidate(
                name = name,
                price = entry.screenPrice,
                obtained = entry.bIsObtained,
                virtual = entry.bIsVirtual,
                isNegative = entry.skillData.bIsNegative,
                isInheritedUnique = entry.skillData.bIsInheritedUnique,
                isDoubleCircle = isDoubleCircleUpgrade(name),
                matchesAxes =
                    matchesPreference(
                        entry.trackDistance,
                        entry.runningStyle,
                        entry.inferredRunningStyles,
                        entry.trackSurface,
                        axes.trackDistance,
                        axes.runningStyle,
                        axes.trackSurface,
                    ),
                deadTapExhausted = name in skillList.deadTapSkills,
            )
        }
    }

    /**
     * No candidate row is skipped silently: each is ELIGIBLE or counted under its exclusion reason, so "exhausted" is
     * a proven statement backed by [SkillList.lastScanComplete].
     */
    private fun computeFinalizeEvidence(
        outcome: SkillSpendOutcome,
        trigger: SkillCheckTrigger?,
        planKey: String?,
        skillList: SkillList?,
        spAfter: Int?,
        confirmedIncomplete: Boolean,
    ): FinalizeEvidence {
        val exhaustion: CandidateExhaustion =
            if (skillList != null && spAfter != null) {
                classifyRemainingCandidates(buildRemainingCandidates(skillList), spAfter, skipDoubleCircleUpgrades)
            } else {
                CandidateExhaustion(0, 0, null, null, null, emptyMap())
            }
        return FinalizeEvidence(
            sessionOutcome = outcome,
            trigger = trigger,
            planKey = planKey,
            scanComplete = sessionPlanningScanComplete == true,
            plannerComplete = outcome == SkillSpendOutcome.COMMITTED || outcome == SkillSpendOutcome.NOTHING_TO_BUY,
            confirmationComplete = outcome != SkillSpendOutcome.COMMIT_UNVERIFIED && !confirmedIncomplete,
            fallbackAttempted = sessionCareerEndFallback == true,
            verifiedRemainingSp = spAfter,
            eligibleCandidateCount = exhaustion.eligibleCount,
            affordableEligibleCandidateCount = exhaustion.affordableCount,
            cheapestAffordableEligibleName = exhaustion.cheapestAffordableName,
            cheapestAffordableEligiblePrice = exhaustion.cheapestAffordablePrice,
            cheapestEligiblePrice = exhaustion.cheapestEligiblePrice,
            excludedByReason = exhaustion.excludedByReason,
            timestampMs = System.currentTimeMillis(),
        )
    }
}
