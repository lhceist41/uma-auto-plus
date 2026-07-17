package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.types.RunningStyle
import com.steve1316.uma_android_automation.types.SkillList
import com.steve1316.uma_android_automation.types.SkillListEntry
import com.steve1316.uma_android_automation.types.TrackDistance
import com.steve1316.uma_android_automation.types.TrackSurface
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

    /** The preferred track distance override for training. */
    private val trainingSettingTrackDistanceString = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")

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
                        // Skip just this entry, not the whole map — a try/catch around the entire loop
                        // would let one bad plan empty ALL plans.
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

        /** Grouped 0/1 knapsack DP across upgrade chains to maximize total rank under budget. Unlike
         * [OPTIMIZE_RANK]'s greedy ratio, it respects mutual exclusion between a base skill and its
         * upgrade — owning both wastes the base cost since only the upgrade activates — and evaluates
         * non-greedy combos the ratio sort misses (two cheap mid-tier skills vs one unaffordable top-tier).
         */
        OPTIMIZE_KNAPSACK,

        ;

        companion object {
            private val nameMap = entries.associateBy { it.name }
            private val ordinalMap = entries.associateBy { it.ordinal }

            /** Look up by name, tolerating whitespace and hyphen/underscore drift from the persisted
             *  form (e.g. "optimize-knapsack" -> OPTIMIZE_KNAPSACK) so a stray format doesn't silently
             *  fall back to greedy DEFAULT. */
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
         * Whether a skill is the ◎ (double-circle) upgrade of an ○ skill, identified by the name suffix the
         * OCR pass appends ([SkillList.getSkillListEntryTitle]). The "skip double-circle upgrades" toggle drops
         * these so the budget spreads across more distinct ○ skills instead of paying up for one ◎.
         *
         * @param name The skill name to test.
         * @return True if the name ends with the ◎ marker.
         */
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
                    .filter { it.name !in alreadyPlanned && it.price > 0 && (!skipDoubleCircle || !isDoubleCircleUpgrade(it.name)) }
                    .sortedByDescending { it.evaluationPointRatio }

            for (skill in sorted) {
                if (skill.price <= remaining) {
                    result.add(skill.name to skill.price)
                    remaining -= skill.price
                }
            }

            return result
        }

        /**
         * One mutually-exclusive choice within a [KnapsackGroup].
         *
         * For an upgrade-chain group like base ○ → upgrade ◎, choices look like:
         *   - empty list (skip the group)
         *   - [base only] (cost = base.price, score = base.evalPt)
         *   - [base, upgrade] (cost = upgrade.price, score = upgrade.evalPt;
         *     only the upgraded form activates so we don't sum the scores)
         *
         * @property items Skill candidates picked together by this choice. Empty = "skip this group".
         */
        data class KnapsackChoice(
            val items: List<SkillCandidate>,
        ) {
            /** SP cost of this choice: the LAST item's price, not the sum. A chain member's screen
             * price already includes its unpurchased prerequisites (SkillListEntry), so summing
             * charged the base twice per combo and made the DP under-buy gold/◎ upgrades. Chain
             * choices are prefixes (base first), so the last item carries the combined price;
             * for singletons the two are the same. */
            val cost: Int = items.lastOrNull()?.price ?: 0

            /** Score for this choice: the max [SkillCandidate.evaluationPoints] across the items, not
             * the sum — owning both base ○ and upgrade ◎ activates only the upgrade, so the base's
             * score is superseded. Singleton = the one item's eval_pt; skip = 0.
             */
            val score: Int = items.maxOfOrNull { it.evaluationPoints } ?: 0

            /** True if this choice picks nothing (the implicit skip option). */
            val isSkip: Boolean = items.isEmpty()

            /** Names of the skills picked by this choice, in order. */
            val names: List<String> = items.map { it.name }
        }

        /**
         * A group of mutually-exclusive [KnapsackChoice] options the DP must pick at most one from.
         *
         * Typical groups:
         *   - **Singleton standalone skill** — choices: [skip], [pick]
         *   - **Upgrade chain** — choices: [skip], [base], [base, upgrade1], [base, upgrade1, upgrade2]
         *   - **Required skill** (user-planned, negative, inherited unique) — choices: [pick] only
         *     (no skip option) so the DP must include it
         *
         * @property choices All possible selections within this group. Must contain at least one choice.
         * @property isRequired When true, the DP cannot choose to skip this group; it must pick a
         *   non-empty choice. Used for skills the user explicitly planned or for negative-skill cleanup.
         */
        data class KnapsackGroup(
            val choices: List<KnapsackChoice>,
            val isRequired: Boolean = false,
        )

        /**
         * Run a grouped 0/1 knapsack DP to choose the highest-scoring combination of skill purchases
         * within [budget].
         *
         * Algorithm: standard grouped knapsack with rolling DP arrays for memory efficiency
         * (`O(2 × budget)` instead of `O(groups × budget)` for the value table). Reconstruction uses
         * a full `choice[g][b]` table to recover which option was picked per group.
         *
         * Faithful Kotlin port of the algorithm in `daftuyda/UmaTools` `js/optimizer.js`
         * (`optimizeGrouped` function). Adapted to use [SkillCandidate] directly instead of
         * row-metadata, and to treat empty/required choices via [KnapsackChoice.isSkip] +
         * [KnapsackGroup.isRequired] flags rather than a JS `none` sentinel.
         *
         * @param groups Mutually-exclusive groups of skill choices.
         * @param budget Total SP budget available.
         * @return Ordered list of (name, price) pairs to buy. Empty if a required group is unreachable
         *   under the budget.
         */
        fun calculateOptimizeKnapsackPurchases(
            groups: List<KnapsackGroup>,
            budget: Int,
        ): List<Pair<String, Int>> {
            if (groups.isEmpty() || budget <= 0) return emptyList()

            val numGroups = groups.size
            val sentinel = Int.MIN_VALUE / 4 // Avoids overflow when added to a positive score
            val budgetLimit = budget

            // Rolling DP arrays: dpPrev[b] = best score using first (g-1) groups with exactly b budget used.
            // dpCurr[b] = best score using first g groups. After processing g, swap and continue.
            var dpPrev = IntArray(budgetLimit + 1) { 0 }
            var dpCurr = IntArray(budgetLimit + 1) { sentinel }

            // choice[g][b] = index of the chosen option in groups[g-1] for state (g, b), or -1 for "skip".
            val choice = Array(numGroups + 1) { IntArray(budgetLimit + 1) { -1 } }

            for (g in 1..numGroups) {
                val group = groups[g - 1]
                val opts = group.choices
                // The group has an implicit skip path if it isn't required AND no explicit skip option exists,
                // OR if any of its choices is already a skip (cost=0, score=0).
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

                // Swap and clear curr for the next group iteration.
                val tmp = dpPrev
                dpPrev = dpCurr
                dpCurr = tmp
                dpCurr.fill(sentinel)
            }

            // dpPrev[budgetLimit] now holds the optimal score for all groups within budget.
            if (dpPrev[budgetLimit] <= sentinel / 2) {
                // A required group was unreachable under the budget — no feasible plan.
                return emptyList()
            }

            // Reconstruct the chosen options by walking the choice table backwards.
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

            // Groups were walked backwards above, so reverse to emit in selection order with
            // base-before-upgrade within each chain.
            for (choice in pickedGroups.asReversed()) {
                var previousChainPrice = 0
                for (item in choice.items) {
                    // Chain members carry cumulative screen prices, so emit each link's increment
                    // over the previous one - that is what the screen will charge once the earlier
                    // links are owned, and it is what the execution loop's affordability gate
                    // compares against its live remaining budget. Pair prices now sum to the
                    // choice's DP cost. Singletons emit their full price (previous = 0). Clamped:
                    // prices come from OCR, and a misread that breaks the cumulative invariant
                    // must not emit a negative price into the affordability gate.
                    result.add(item.name to (item.price - previousChainPrice).coerceAtLeast(0))
                    previousChainPrice = item.price
                }
            }
            return result
        }

        /**
         * Build [KnapsackGroup]s from a flat list of skill candidates by merging skills that share an
         * upgrade chain into a single group with combo choices.
         *
         * For each chain `[base, up1, up2]` from [upgradeChains], if any of those names appear in
         * [candidates], the corresponding skills become one group with options:
         *   - skip
         *   - [base] only
         *   - [base, up1]
         *   - [base, up1, up2]
         *
         * Skills not part of any chain become singleton groups with choices [skip, pick].
         *
         * @param candidates Available skills the bot can currently purchase.
         * @param upgradeChains Map of skill name → ordered chain (base first, upgrades after) from
         *   [SkillDatabase.skillUpgradeChains].
         * @param requiredNames Names of skills that must be included (user-planned, negatives, etc.);
         *   their groups are marked [KnapsackGroup.isRequired].
         * @return List of groups suitable for [calculateOptimizeKnapsackPurchases].
         */
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

                // Resolve the canonical chain order for this candidate. The map may key by any chain
                // member; the value is the full ordered chain.
                val chain: List<String> = upgradeChains[candidate.name].orEmpty()
                val chainPresent: List<SkillCandidate> =
                    if (chain.isNotEmpty()) {
                        chain.mapNotNull { byName[it] }
                    } else {
                        listOf(candidate)
                    }

                // If only one chain member is present in the candidate list, treat as singleton.
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

                // Multi-link chain: choices are [skip, base, base+up1, base+up1+up2, ...].
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
         * @return Ordered list of (name, price) pairs representing all skills to buy.
         */
        fun calculateSkillPurchases(
            candidates: List<SkillCandidate>,
            budget: Int,
            settings: SkillPlanSettings,
            skipDoubleCircle: Boolean = false,
        ): List<Pair<String, Int>> {
            if (!settings.bIsEnabled) return emptyList()

            val result = mutableListOf<Pair<String, Int>>()

            // Common purchases first
            val common = calculateCommonPurchases(candidates, budget, settings)
            result.addAll(common)
            val spent = common.sumOf { it.second }
            val alreadyBought = common.map { it.first }

            // Strategy-specific purchases. Drop ◎ upgrades up front when the toggle is on so every
            // strategy below — including the knapsack DP that models the ○ -> ◎ chain as a group — only
            // ever sees the ○ form.
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
                        // No upgrade-chain map here (it lives in SkillDatabase), so this static helper
                        // runs the DP with singleton groups only — still better than greedy on budget-fit
                        // edge cases, but without the mutual-exclusion benefit. Callers with the chain map
                        // should call [calculateOptimizeKnapsackPurchases] + [buildKnapsackGroups] directly;
                        // the in-bot [getSkillsToBuyOptimizeKnapsackStrategy] does and gets the full benefit.
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
        // TEMP (verification harness): runs the REAL purchase pass on the current skill list screen
        // instead of the read-only simulation, so the (+) tap fix can be exercised without a full career.
        // To use: enable debugMode_startSkillListBuyTest, open the career-end "Learn" screen, Start.
        // Revert to the simulation once the tap fix is confirmed.
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

            // bIsAvailable guard ported from upstream 90b51885: auto-obtained rows tallied here
            // spent budget on skills that were never purchasable.
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

            // Same 90b51885 guard as the negative-skill tally above.
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
                // Respect the wave budget in plan order. Without this, the plan committed every
                // available planned skill regardless of total cost, and since the buyer purchases in
                // scroll order (not plan order) an over-budget set stranded the expensive critical
                // skills (careers reaching 2500m+ gates without Swinging Maestro). A budget-true set is
                // order-insensitive — everything planned gets bought.
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

        return result.toMap()
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

                    // Skip ◎ upgrades when the toggle is on so the budget buys more distinct ○ skills.
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
        var remainingSkills: Map<String, SkillListEntry> = skillList.getAvailableSkills()
        while (remainingSkills.any { it.value.screenPrice <= remainingSkillPoints }) {
            val sortedByPointRatio: List<SkillListEntry> =
                remainingSkills.values
                    .sortedByDescending { it.evaluationPointRatio }

            for (entry in sortedByPointRatio) {
                // Don't add duplicate entries.
                if (entry.name in result || entry.name in skillsToBuy) {
                    continue
                }

                // Skip ◎ upgrades when the toggle is on so the budget buys more distinct ○ skills.
                if (skipDoubleCircleUpgrades && isDoubleCircleUpgrade(entry.name)) {
                    continue
                }

                // Strictly respect the Style preference: skip off-style/distance/surface skills when a
                // preference is set (a no_preference axis resolves to null and never restricts). Without
                // this, OPTIMIZE_RANK bought purely by ratio, and off-preference skills also leaked in via
                // the OPTIMIZE_SKILLS leftover-budget tail that spends through this strategy.
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

            remainingSkills = skillList.getAvailableSkills()

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

        // Apply the Style preference to the knapsack candidate set too. Upstream's gate only covered
        // Optimize Skills/Rank; the knapsack is our addition, so extend the same gate here for
        // consistency. A no_preference axis resolves to null and never restricts (default presets unaffected).
        val (preferredRunningStyle, preferredTrackDistance, preferredTrackSurface) = resolvePreferredAxes()
        val available =
            skillList.getAvailableSkills().filterValues { entry ->
                entry.bIsAvailable &&
                    entry.name !in skillsToBuy &&
                    entry.screenPrice > 0 &&
                    // Drop ◎ upgrades before buildKnapsackGroups runs, otherwise the ○ -> ◎ chain group still
                    // offers the [○, ◎] combo and the DP buys the ◎ — the toggle would no-op on the one
                    // strategy every preset uses at careerComplete.
                    (!skipDoubleCircleUpgrades || !isDoubleCircleUpgrade(entry.name)) &&
                    matchesPreference(
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

        // Convert live SkillListEntry instances into SkillCandidate snapshots for the DP.
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

        // Group skills that share an upgrade chain so the DP can evaluate the
        // "buy base then upgrade" combo as a single mutually-exclusive option.
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

        // Execute the plan: buy each chosen skill via the live SkillListEntry. We iterate the plan
        // in DP order (base before upgrade within an upgrade chain) so the in-game purchase chain
        // works correctly — buying the upgrade requires the base to already be owned.
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

        // Execute strategy-specific checks.
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
        // Evaluate the exit conditions on every NON-candidate entry, not only after a buy. The
        // post-buy check below never re-runs once the last buyable skill is bought (non-planned
        // entries used to return before reaching it), so a single unbuyable leftover made every
        // pass walk the full list. With no refunds, the scroll has nothing left to accomplish
        // once each planned skill is either owned or priced beyond the remaining budget. A
        // candidate entry skips these checks entirely: it must always get its buy attempt first
        // (its recorded price may run stale-high while the live row is buyable).
        val bIsBuyCandidate = !entry.bIsObtained && !entry.bIsVirtual && entry.name in skillsToBuy
        if (!bIsBuyCandidate) {
            val outstandingSkills: List<String> = skillsToBuy.filter { it !in skillList.getObtainedSkills() }
            if (outstandingSkills.isEmpty()) {
                MessageLog.i(TAG, "[SKILLS] All skills purchased. Exiting loop early...")
                return true
            }
            val bAnyStillBuyable =
                outstandingSkills.any { name ->
                    // An unknown price means the row has not been seen this scan - it may appear
                    // further down the list, so the scroll must continue.
                    val livePrice: Int? = skillList.getAllSkills()[name]?.screenPrice
                    livePrice == null || livePrice <= skillList.skillPoints
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
                    // Track the purchase so the estimated rank stays current without re-reading the Details Skills tab.
                    campaign.trainee.ownedSkillNames.add(purchaseResult.name)
                }
            }
        } else {
            val purchaseResult: SkillListEntry? = skillList.buySkill(entry.name, point)
            if (purchaseResult != null) {
                MessageLog.i(TAG, "[INFO] Buying \"${purchaseResult.name}\" for ${purchaseResult.price} pts")
                // Track the purchase so the estimated rank stays current without re-reading the Details Skills tab.
                campaign.trainee.ownedSkillNames.add(purchaseResult.name)
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
        val bitmap: Bitmap = game.imageUtils.getSourceBitmap()

        val skillList = SkillList(game, campaign)

        // Verify that the bot is currently at the skill list screen.
        val bIsCareerComplete: Boolean = skillList.checkCareerCompleteSkillListScreen(bitmap)
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
                    // Was skillPlans[...]!! — a degraded/empty plans map (bad parse, unmigrated settings,
                    // fresh install before a write) crashed skill buying instead of aborting. Abort gracefully.
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

        // Resolved plan key + trigger for telemetry. A null trigger means the caller did not name one
        // (the debug harness), so the record carries the plan and omits the trigger rather than guessing.
        val resolvedPlanKey: String = skillPlanName ?: if (bIsCareerComplete) PLAN_CAREER_COMPLETE else PLAN_PRE_FINALS
        val effectiveTrigger: SkillCheckTrigger? = trigger ?: if (bIsCareerComplete) SkillCheckTrigger.CAREER_COMPLETE else null

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

        // Exit if the current skill points are below the minimum possible skill cost.
        if (skillPoints < 42) {
            MessageLog.i(TAG, "[SKILLS] Skill Points < 42. Cannot afford any skills. Aborting...")
            recordSkillSpend(SkillSpendOutcome.NOTHING_TO_BUY, effectiveTrigger, resolvedPlanKey, skillPlanSettings, spBefore = skillPoints, spAfter = skillPoints)
            skillList.cancelAndExit()
            return true
        }

        // Gather and parse all skill entries from the screen.
        skillList.parseSkillListEntries(bUseMockData = USE_MOCK_DATA)
        if (skillList.getAllSkills().isEmpty()) {
            MessageLog.e(TAG, "[ERROR] start:: Failed to detect skills.")
            recordSkillSpend(SkillSpendOutcome.ABORTED_PARSE, effectiveTrigger, resolvedPlanKey, skillPlanSettings, spBefore = skillPoints, spAfter = skillPoints)
            skillList.cancelAndExit()
            return false
        }

        skillList.printSkillListEntries(verbose = true)

        // Ground-truth snapshot: skills whose Obtained pill was detected on screen during the read
        // pass (genuinely owned, e.g. on career-end re-entry). The post-planning state reset below
        // must not clear these - doing so corrupts upgrade-chain pricing and ownership reads.
        val ownedAtParse: Set<String> = skillList.getObtainedSkills().keys

        // Calculate the list of skills to purchase based on settings and points.
        val skillsToPurchase: Map<String, Int> =
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

        // Planner output, snapshotted before execution can mutate the live entries. This is the
        // `proposed` set: what the ranking decided to buy, at the price it planned against.
        val proposedSkills: List<ProposedSkill> = skillsToPurchase.map { (name, price) -> ProposedSkill(name, price) }

        // Reset the in-memory purchase simulation from planning, preserving screen-confirmed ownership.
        skillList.sellAllSkills(preserve = ownedAtParse)

        // Iterate through the list again and perform the confirmed purchases.
        //
        // A single scroll pass is not trusted to cover the whole list: the end-of-list heuristics
        // can conclude "done" early (dropped swipes, or purchases reflowing rows mid-pass), stranding
        // planned skills unbought. Re-run from the top while planned skills remain. Re-runs are safe:
        // obtained entries are skipped in the callback, and already-selected rows no longer present a
        // matchable Skill Up button.
        val maxBuyPasses = 3
        var totalEntriesSeen = 0
        for (buyPass in 1..maxBuyPasses) {
            // Heal a wiped Accessibility grant between passes - the most common mid-buy failure
            // (the emulator drops the service and every tap/swipe silently stops registering).
            game.ensureAccessibilityService()
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
            // Drop what the current budget can no longer cover — re-scrolling the whole list for a
            // skill that cannot be bought is pure waste. Prices can drift between parse and buy, so
            // evaluate against the live screenPrice where known.
            val remaining: List<String> =
                unbought.filter { name ->
                    val price: Int = skillList.getAllSkills()[name]?.screenPrice ?: skillsToPurchase[name] ?: Int.MAX_VALUE
                    price <= skillList.skillPoints
                }
            val droppedUnaffordable: List<String> = unbought - remaining.toSet()
            if (droppedUnaffordable.isNotEmpty()) {
                MessageLog.w(TAG, "[WARN] Dropping ${droppedUnaffordable.size} planned skill(s) no longer affordable with ${skillList.skillPoints} SP: ${droppedUnaffordable.joinToString(", ")}.")
            }
            if (remaining.isEmpty()) {
                if (buyPass > 1 || droppedUnaffordable.isNotEmpty()) {
                    MessageLog.i(TAG, "[SKILLS] Nothing further to buy after $buyPass buy pass(es). Proceeding to confirm.")
                }
                break
            }
            if (entriesSeenThisPass == 0) {
                // The pass saw NOTHING - the list is unreadable or input is blocked (popup, stale
                // capture, emulator input outage), not merely incomplete. Try to clear a blocking
                // dialog before the next pass.
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

        // If every pass was blind AND nothing new got bought, the screen state is unknown - do not
        // blind-confirm (a misplaced Confirm/Back sequence is how selections get silently lost).
        val boughtAny: Boolean = skillsToPurchase.keys.any { it in skillList.getObtainedSkills() && it !in ownedAtParse }
        if (!boughtAny && totalEntriesSeen == 0) {
            MessageLog.e(TAG, "[ERROR] start:: All buy passes processed zero entries and nothing was bought. Not confirming; aborting the skill plan.")
            recordSkillSpend(
                SkillSpendOutcome.ABORTED_PARSE,
                effectiveTrigger,
                resolvedPlanKey,
                skillPlanSettings,
                spBefore = skillPoints,
                spAfter = skillList.skillPoints,
                proposed = proposedSkills,
                confirmed = confirmedPurchases(skillList, skillsToPurchase.keys, ownedAtParse),
                skillList = skillList,
            )
            campaign.trainee.skillPoints = skillList.skillPoints
            return false
        }

        // The commit must land on working input - heal the grant one more time if needed.
        game.ensureAccessibilityService()
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
            proposed = proposedSkills,
            confirmed = confirmedPurchases(skillList, skillsToPurchase.keys, ownedAtParse),
            skillList = skillList,
        )
        campaign.trainee.skillPoints = skillList.skillPoints
        return committed
    }

    /**
     * Skills this session actually obtained: on screen as obtained now, and not already owned when the
     * list was parsed. Evidence, never intent - a tap that silently missed must not be recorded as a
     * purchase, so the planned set is filtered by what the screen reports.
     */
    private fun confirmedPurchases(skillList: SkillList, planned: Set<String>, ownedAtParse: Set<String>): List<String> {
        val obtained: Map<String, SkillListEntry> = skillList.getObtainedSkills()
        return planned.filter { it in obtained && it !in ownedAtParse }
    }

    /**
     * Appends one `type:"skill_spend"` record for this session. Best-effort in every sense: wrapped in
     * runCatching so a corpus failure cannot change what [start] returns, and every optional identity
     * field is omitted rather than guessed when it is not available.
     */
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
        runCatching {
            val livePrices: Map<String, Int> =
                skillList?.getAllSkills()?.mapValues { it.value.screenPrice } ?: emptyMap()
            // The points delta is the arbiter. If it says purchases happened that the obtained set
            // never saw, every "skipped" verdict below would be unsound - flag the gap and say nothing
            // more, rather than name skills as unbought when the points prove otherwise.
            val confirmedIncomplete: Boolean =
                SkillSpendTelemetry.confirmationIsIncomplete(proposed, confirmed.toSet(), spBefore, spAfter)
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
                    // The threshold policy the career is running under - the ACTING value Campaign
                    // resolved at construction, not a re-read, so the record cannot disagree with
                    // the decision that governed the run.
                    threshold = campaign.resolvedSkillThreshold.value,
                    tier = campaign.resolvedSkillThreshold.tierToken(),
                    reason = campaign.resolvedSkillThreshold.reason,
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
    }
}
