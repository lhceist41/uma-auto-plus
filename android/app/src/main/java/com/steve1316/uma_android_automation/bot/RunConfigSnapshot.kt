package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.SettingsHelper

/**
 * An immutable capture of a career's launch-critical configuration, taken once at the
 * career-attachment boundary.
 *
 * Why: the bot reads settings live from SQLite throughout a career (SettingsHelper opens the
 * same file the app writes). On 2026-07-20 a preset write that landed ~2 minutes late -- after
 * the wrong trainee had already launched -- then flipped the live-read skill-spend objective
 * from `rank` to `sparks` mid-career, because a reconstructed Campaign re-read the row after the
 * write. The React Start barrier now prevents a stale launch at its source, but this snapshot
 * makes the run's configuration identity explicit and detectable across the layer boundary: the
 * `settingsRevision` the app verified on disk before Start is captured here and logged, so a
 * mid-career drift is visible instead of silent.
 *
 * Scope note: this captures + logs the identity and freezes the launch-critical values it holds.
 * It does not yet re-route the scattered live SettingsHelper readers through itself -- Campaign's
 * objective, the preferred axes, and the plan are still read live at construction. With the Start
 * barrier in place those reads are stable before launch, so the proven mixture cannot recur from
 * the delayed-write cause. Routing every run-scoped reader through this snapshot (a full frozen
 * envelope) is the documented follow-up; the live-read fields are listed in HOW_IT_WORKS.
 */
object RunConfigSnapshot {
    /** One captured run configuration. All fields are read once and never mutated. */
    data class RunConfig(
        val revision: Int,
        val trainee: String,
        val scenario: String,
        val objective: String,
        val mode: String,
        val tier: String,
        val armedAtMs: Long,
    )

    @Volatile
    private var current: RunConfig? = null

    /** The captured configuration for the active run, or null when no run is armed. */
    val config: RunConfig?
        get() = current

    val isArmed: Boolean
        get() = current != null

    /**
     * Capture the launch-critical settings for a run. Reads each value once, from the live
     * settings, and freezes them. Idempotent per run only in the sense that a later [arm] simply
     * replaces the snapshot with the newer career's values (a new career must receive the newer
     * revision, never the previous run's).
     */
    fun arm(
        revision: Int,
        trainee: String,
        scenario: String,
        objective: String,
        mode: String,
        tier: String,
        nowMs: Long,
    ): RunConfig {
        val snapshot = RunConfig(revision, trainee, scenario, objective, mode, tier, nowMs)
        current = snapshot
        return snapshot
    }

    /** Capture from the live settings at the career-attachment boundary. */
    fun armFromSettings(nowMs: Long): RunConfig =
        arm(
            revision = SettingsHelper.getIntSetting("general", "settingsRevision", 0),
            trainee = SettingsHelper.getStringSetting("general", "appliedPresetTrainee", ""),
            scenario = SettingsHelper.getStringSetting("general", "scenario", ""),
            objective = SettingsHelper.getStringSetting("skills", "skillSpendObjective", "rank"),
            mode = SettingsHelper.getStringSetting("skills", "skillSpendMode", "manual"),
            tier = SettingsHelper.getStringSetting("skills", "accountTier", "auto"),
            nowMs = nowMs,
        )

    /** True when the on-disk revision still matches the armed snapshot (no mid-run drift). Null
     * (unarmed) is treated as a non-match so an unexpected call site cannot read as "coherent". */
    fun revisionMatches(liveRevision: Int): Boolean = current?.revision == liveRevision

    /** Clear the snapshot (between-run boundary / test isolation). */
    fun clear() {
        current = null
    }

    /** A greppable one-line identity for the run, for the loaded_run_config diagnostic. */
    fun describe(config: RunConfig): String =
        "revision=${config.revision} trainee=\"${config.trainee}\" scenario=\"${config.scenario}\" " +
            "objective=${config.objective} mode=${config.mode} tier=${config.tier}"
}
