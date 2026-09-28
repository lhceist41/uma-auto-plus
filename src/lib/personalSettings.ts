/**
 * The settings that belong to the player and the device, not to a trainee's build: device timing, OCR
 * tuning, on-screen display, how the bot reads the screen, and the player's own stop points. Every preset ships a value for each of
 * them, so a plain merge would reset a slow device's wait delay or switch Stop Before Finals off on
 * every apply and every queue rotation switch. Both paths call [keepPersonalSettings] after the
 * preset is merged, so the player's value always wins, even when a preset ships one.
 *
 * Anything that changes how a career is played (training, racing strategy and plan, skills, events,
 * the deck, the scenario) is NOT here: a preset owns those. `enableCraneGameAttempt` is personal: it
 * spends nothing and only chooses between attempting the crane game and stopping the run.
 */
export const PERSONAL_SETTINGS: Readonly<Record<string, readonly string[]>> = {
    general: ["waitDelay", "dialogWaitDelay", "enableStopBeforeFinals", "enableStopAtDate", "stopAtDates", "enableCraneGameAttempt"],
    racing: ["enableStopOnMandatoryRaces"],
    trainingEvent: ["ocrConfidence", "enableAutomaticOCRRetry", "enableHideOCRComparisonResults"],
    training: ["enableYoloStatDetection", "enableTrainingAnalysisValidation"],
    misc: ["enableSettingsDisplay", "enableMessageIdDisplay", "messageLogFontSize", "overlayButtonSizeDP"],
}

/**
 * Puts the player's personal settings from [base] back onto [merged] (a preset already merged onto
 * [base]). A key the base does not carry is left as the merge produced it.
 */
export function keepPersonalSettings(merged: any, base: any): void {
    for (const [category, keys] of Object.entries(PERSONAL_SETTINGS)) {
        if (!merged?.[category] || !base?.[category]) continue
        const kept: Record<string, unknown> = {}
        for (const key of keys) {
            if (key in base[category]) kept[key] = base[category][key]
        }
        merged[category] = { ...merged[category], ...kept }
    }
}
