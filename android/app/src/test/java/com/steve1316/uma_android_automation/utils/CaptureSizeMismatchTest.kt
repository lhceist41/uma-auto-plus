package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Capture size against screen size at Start")
class CaptureSizeMismatchTest {
    @Test
    fun `equal sizes give no warning`() {
        assertNull(captureSizeMismatchWarning(1080, 2408, 1080, 2408))
    }

    @Test
    fun `a rotated screen of the same size gives no warning`() {
        assertNull(captureSizeMismatchWarning(1080, 2408, 2408, 1080))
    }

    @Test
    fun `a shorter capture warns once with both sizes`() {
        val warning = captureSizeMismatchWarning(1080, 2316, 1080, 2408)
        assertEquals(
            "[LAYOUT] The bot captures 1080x2316 but the screen is 1080x2408. Taps and screen reads can be off by the difference. " +
                "If the bot misses buttons, tap Share bug report on Home and include this line.",
            warning,
        )
    }

    @Test
    fun `a different width warns too`() {
        assertEquals(true, captureSizeMismatchWarning(1088, 1920, 1080, 1920)?.contains("1088x1920 but the screen is 1080x1920"))
    }
}
