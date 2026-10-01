package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Training analysis frame capture")
class GrandConcertTelemetryTest {
    @Test
    @DisplayName("Captures only in a debug build or with Debug Mode on")
    fun gate() {
        assertFalse(GrandConcertTelemetry.enabled(debugBuild = false, debugMode = false))
        assertTrue(GrandConcertTelemetry.enabled(debugBuild = false, debugMode = true))
        assertTrue(GrandConcertTelemetry.enabled(debugBuild = true, debugMode = false))
    }

    @Test
    @DisplayName("Grand Concert keeps gc_train names; other scenarios get their own prefix")
    fun frameNames() {
        assertEquals("gc_train_0496_SPEED", GrandConcertTelemetry.frameName(GrandConcertScenario.KEY, 496, StatName.SPEED))
        assertEquals("urafinale_train_0001_WIT", GrandConcertTelemetry.frameName("URA Finale", 1, StatName.WIT))
        assertEquals("unitycup_train_0012_GUTS", GrandConcertTelemetry.frameName("Unity Cup", 12, StatName.GUTS))
        assertEquals("trackblazer_train_0300_POWER", GrandConcertTelemetry.frameName("Trackblazer", 300, StatName.POWER))
    }
}
