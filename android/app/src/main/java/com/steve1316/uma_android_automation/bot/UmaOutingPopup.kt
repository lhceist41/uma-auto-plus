package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.components.ButtonNext
import com.steve1316.uma_android_automation.components.LabelUmaOutingBanner
import com.steve1316.uma_android_automation.utils.CustomImageUtils

/** From the banner match's centre down to the centre of the Outing Login Bonus "TAP" bar, measured on 1080x1920 frames. */
internal const val UMA_OUTING_TAP_BELOW_BANNER: Double = 1235.0

private val TAG: String = "[${MainActivity.loggerTag}]UmaOutingPopup"

/**
 * The Uma Outing popups shown after the game-day reset (Outing Login Bonus with only a "TAP" bar, Outing Gauge MAX with
 * Next) have no dialog title, only the "Let's Go! Uma Outing!" banner. Taps Next when shown, else the TAP bar under the
 * banner; neither spends anything. False when the banner is not up.
 */
internal fun dismissUmaOutingPopup(imageUtils: CustomImageUtils, bitmap: Bitmap): Boolean {
    val banner = LabelUmaOutingBanner.findImageWithBitmap(imageUtils, bitmap) ?: return false
    if (ButtonNext.click(imageUtils, sourceBitmap = bitmap)) {
        MessageLog.i(TAG, "[OUTING] Uma Outing popup; tapped Next.")
        return true
    }
    MessageLog.i(TAG, "[OUTING] Uma Outing popup without Next; tapping its TAP bar.")
    CoordinateTap.tap(MyAccessibilityService.getInstance(), bitmap.width / 2.0, banner.y + UMA_OUTING_TAP_BELOW_BANNER * bitmap.width / 1080.0, "uma_outing_tap")
    return true
}
