package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.components.ButtonTrainingGuts
import com.steve1316.uma_android_automation.components.ButtonTrainingGutsGrandConcert
import com.steve1316.uma_android_automation.components.ButtonTrainingPower
import com.steve1316.uma_android_automation.components.ButtonTrainingPowerGrandConcert
import com.steve1316.uma_android_automation.components.ButtonTrainingSpeed
import com.steve1316.uma_android_automation.components.ButtonTrainingSpeedGrandConcert
import com.steve1316.uma_android_automation.components.ButtonTrainingStamina
import com.steve1316.uma_android_automation.components.ButtonTrainingStaminaGrandConcert
import com.steve1316.uma_android_automation.components.ButtonTrainingWit
import com.steve1316.uma_android_automation.components.ButtonTrainingWitGrandConcert
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.types.StatName

/** How long a tab switch may take to show its header (the old loop gave it 0.2 s). */
internal const val TRAINING_TAB_SETTLE_MS = 1500L

/** Looks for the target's button before giving up; a look that finds nothing taps nothing. */
internal const val TRAINING_BUTTON_LOOKS = 3

/** Grand Concert draws its own training buttons; every other scenario uses the standard ones. */
internal fun trainingButtonsFor(grandConcert: Boolean): Map<StatName, ComponentInterface> =
    if (grandConcert) {
        mapOf(
            StatName.SPEED to ButtonTrainingSpeedGrandConcert,
            StatName.STAMINA to ButtonTrainingStaminaGrandConcert,
            StatName.POWER to ButtonTrainingPowerGrandConcert,
            StatName.GUTS to ButtonTrainingGutsGrandConcert,
            StatName.WIT to ButtonTrainingWitGrandConcert,
        )
    } else {
        mapOf(
            StatName.SPEED to ButtonTrainingSpeed,
            StatName.STAMINA to ButtonTrainingStamina,
            StatName.POWER to ButtonTrainingPower,
            StatName.GUTS to ButtonTrainingGuts,
            StatName.WIT to ButtonTrainingWit,
        )
    }

/**
 * Switches the Training screen to [target] with at most one tap. A tap on the training that is already selected
 * executes it, and a stalled game can select the tab without showing its header for seconds, so a tap that is not
 * confirmed is never repeated: the switch fails instead and the caller backs out.
 *
 * @param activeTab One read of which training header shows; null when none does.
 * @param targetHeaderShown One check of [target]'s header.
 * @param tap Finds and taps [target]'s button; false when the button was not found and nothing was tapped.
 * @return True once [target] shows. False after the one tap when it does not show in time, and false without tapping
 *   when the screen never shows a readable tab.
 */
internal fun switchTrainingTab(
    target: StatName,
    activeTab: () -> StatName?,
    targetHeaderShown: () -> Boolean,
    tap: () -> Boolean,
    nowMs: () -> Long,
    pause: () -> Unit,
    warn: (String) -> Unit,
): Boolean {
    val deadline = nowMs() + TRAINING_TAB_SETTLE_MS
    var before = activeTab()
    while (before == null && nowMs() < deadline) {
        pause()
        before = activeTab()
    }
    if (before == target) return true
    if (before == null) {
        warn("No training tab could be read, so $target was not tapped.")
        return false
    }

    var looks = 0
    var tapped = false
    while (!tapped && looks < TRAINING_BUTTON_LOOKS) {
        looks++
        tapped = tap()
        if (!tapped) pause()
    }
    if (!tapped) {
        warn("The $target button was not found, so nothing was tapped.")
        return false
    }

    val settled = nowMs() + TRAINING_TAB_SETTLE_MS
    while (nowMs() < settled) {
        if (targetHeaderShown()) return true
        pause()
    }
    if (targetHeaderShown() || activeTab() == target) return true
    warn("$target did not show after its tap; it is not tapped again, since the tap may still select it.")
    return false
}
