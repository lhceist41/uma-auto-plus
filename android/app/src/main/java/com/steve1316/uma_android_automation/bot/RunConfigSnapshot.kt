package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.SettingsHelper

/** Immutable capture of a career's launch-critical configuration, taken once at career attachment.
 * The bot reads settings live from SQLite, so a late preset write could flip the objective mid-career;
 * logging the verified `settingsRevision` here makes such drift visible. Other live readers are listed in HOW_IT_WORKS. */
object RunConfigSnapshot {
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

    val config: RunConfig?
        get() = current

    val isArmed: Boolean
        get() = current != null

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

    /** Null (unarmed) counts as a non-match so an unexpected call site cannot read as coherent. */
    fun revisionMatches(liveRevision: Int): Boolean = current?.revision == liveRevision

    fun clear() {
        current = null
    }

    fun describe(config: RunConfig): String =
        "revision=${config.revision} trainee=\"${config.trainee}\" scenario=\"${config.scenario}\" " +
            "objective=${config.objective} mode=${config.mode} tier=${config.tier}"
}
