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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrainingTabSwitchTest {
    /**
     * The Training screen as the game behaves: a tap on another tab's button selects it at once, but its header shows
     * only after [headerLagMs] (the old tab's header stays until then). A tap on the selected tab executes that training.
     * A [frozen] screen keeps showing the frame from before the tap.
     */
    private class FakeTrainingScreen(
        active: StatName?,
        val headerLagMs: Long = 0,
        val landsFromTap: Int = 1,
        val buttonFound: Boolean = true,
        val unreadable: Set<StatName> = emptySet(),
        val frozen: Boolean = false,
    ) {
        var now = 0L
        var taps = 0
        val trained = mutableListOf<StatName>()
        private var selected: StatName? = active
        private var shown: StatName? = active
        private var showsAt = 0L

        fun tap(button: StatName): Boolean {
            if (!buttonFound) return false
            taps++
            if (selected == button) {
                trained += button
            } else if (taps >= landsFromTap) {
                selected = button
                showsAt = now + headerLagMs
            }
            return true
        }

        fun activeTab(): StatName? {
            if (!frozen && now >= showsAt) shown = selected
            return shown?.takeUnless { it in unreadable }
        }

        fun headerShown(target: StatName): Boolean = activeTab() == target

        fun pause() {
            now += 100
        }

        fun switchTo(target: StatName): Boolean =
            switchTrainingTab(
                target = target,
                activeTab = ::activeTab,
                targetHeaderShown = { headerShown(target) },
                tap = { tap(target) },
                nowMs = { now },
                pause = ::pause,
                warn = {},
            )

        /** The loop this replaces: tap, wait 0.2 s, check the header, up to 3 times. */
        fun oldLoop(target: StatName) {
            repeat(3) {
                tap(target)
                now += 200
                if (headerShown(target)) return
            }
        }

        /** The first version of the fix: re-tap after 1.5 s while the tab from before the tap still shows. */
        fun retapWhileOldTabShows(target: StatName) {
            val before = activeTab()
            repeat(3) {
                tap(target)
                val until = now + TRAINING_TAB_SETTLE_MS
                while (now < until) {
                    if (headerShown(target)) return
                    pause()
                }
                if (activeTab() != before) return
            }
        }
    }

    @Test
    fun `the old loop trains the tab it is switching to when the header is slow`() {
        val screen = FakeTrainingScreen(active = StatName.SPEED, headerLagMs = 500)
        screen.oldLoop(StatName.STAMINA)
        // In the game the first such tap starts the training; the fake keeps the screen, so it counts every one.
        assertEquals(StatName.STAMINA, screen.trained.firstOrNull())
    }

    @Test
    fun `a re-tap after a stall or a frozen frame trains, which is why there is none`() {
        val stalled = FakeTrainingScreen(active = StatName.SPEED, headerLagMs = 2000)
        stalled.retapWhileOldTabShows(StatName.POWER)
        assertEquals(StatName.POWER, stalled.trained.firstOrNull())

        val frozen = FakeTrainingScreen(active = StatName.SPEED, frozen = true)
        frozen.retapWhileOldTabShows(StatName.POWER)
        assertEquals(StatName.POWER, frozen.trained.firstOrNull())
    }

    @Test
    fun `a slow header gets one tap and no training`() {
        val screen = FakeTrainingScreen(active = StatName.SPEED, headerLagMs = 500)
        assertTrue(screen.switchTo(StatName.STAMINA))
        assertEquals(1, screen.taps)
        assertTrue(screen.trained.isEmpty())
    }

    @Test
    fun `a header at or past the settle window gets exactly one tap and fails`() {
        for (lag in listOf(TRAINING_TAB_SETTLE_MS + 100, 2000L, 5000L)) {
            val screen = FakeTrainingScreen(active = StatName.SPEED, headerLagMs = lag)
            assertFalse(screen.switchTo(StatName.POWER), "lag $lag")
            assertEquals(1, screen.taps, "lag $lag")
            assertTrue(screen.trained.isEmpty(), "lag $lag")
        }
    }

    @Test
    fun `a frozen frame gets exactly one tap and fails`() {
        val screen = FakeTrainingScreen(active = StatName.GUTS, frozen = true)
        assertFalse(screen.switchTo(StatName.WIT))
        assertEquals(1, screen.taps)
        assertTrue(screen.trained.isEmpty())
    }

    @Test
    fun `a tab already selected is never tapped`() {
        val screen = FakeTrainingScreen(active = StatName.SPEED)
        assertTrue(screen.switchTo(StatName.SPEED))
        assertEquals(0, screen.taps)
    }

    @Test
    fun `an unreadable screen is never tapped`() {
        val screen = FakeTrainingScreen(active = null)
        assertFalse(screen.switchTo(StatName.POWER))
        assertEquals(0, screen.taps)
    }

    @Test
    fun `a header that never matches after the switch gets no second tap`() {
        val screen = FakeTrainingScreen(active = StatName.STAMINA, unreadable = setOf(StatName.POWER))
        assertFalse(screen.switchTo(StatName.POWER))
        assertEquals(1, screen.taps)
        assertTrue(screen.trained.isEmpty())
    }

    @Test
    fun `a tap that did not land is not repeated`() {
        val screen = FakeTrainingScreen(active = StatName.POWER, landsFromTap = 2)
        assertFalse(screen.switchTo(StatName.GUTS))
        assertEquals(1, screen.taps)
    }

    @Test
    fun `a button that is not found sends no tap`() {
        val screen = FakeTrainingScreen(active = StatName.SPEED, buttonFound = false)
        assertFalse(screen.switchTo(StatName.WIT))
        assertEquals(0, screen.taps)
    }

    @Test
    fun `no switch between any two tabs trains, for header delays inside the settle window`() {
        for (from in StatName.entries) {
            for (to in StatName.entries) {
                for (lag in listOf(0L, 200L, 500L, 1000L, TRAINING_TAB_SETTLE_MS - 100)) {
                    val screen = FakeTrainingScreen(active = from, headerLagMs = lag)
                    assertTrue(screen.switchTo(to), "$from -> $to, lag $lag")
                    assertTrue(screen.trained.isEmpty(), "$from -> $to, lag $lag trained ${screen.trained}")
                    assertEquals(if (from == to) 0 else 1, screen.taps, "$from -> $to, lag $lag")
                }
            }
        }
    }

    private val standardButtons: Map<StatName, ComponentInterface> =
        mapOf(
            StatName.SPEED to ButtonTrainingSpeed,
            StatName.STAMINA to ButtonTrainingStamina,
            StatName.POWER to ButtonTrainingPower,
            StatName.GUTS to ButtonTrainingGuts,
            StatName.WIT to ButtonTrainingWit,
        )

    private val grandConcertButtons: Map<StatName, ComponentInterface> =
        mapOf(
            StatName.SPEED to ButtonTrainingSpeedGrandConcert,
            StatName.STAMINA to ButtonTrainingStaminaGrandConcert,
            StatName.POWER to ButtonTrainingPowerGrandConcert,
            StatName.GUTS to ButtonTrainingGutsGrandConcert,
            StatName.WIT to ButtonTrainingWitGrandConcert,
        )

    @Test
    fun `every scenario maps each stat to its own button`() {
        assertEquals(standardButtons, trainingButtonsFor(false))
        assertEquals(grandConcertButtons, trainingButtonsFor(true))
        for ((scenario, expected) in listOf(
            "URA Finale" to standardButtons,
            "Unity Cup" to standardButtons,
            "Trackblazer" to standardButtons,
            GrandConcertScenario.KEY to grandConcertButtons,
        )) {
            val buttons = trainingButtonsFor(GrandConcertScenario.matches(scenario))
            val statOfButton = expected.entries.associate { (stat, button) -> button to stat }
            for (target in StatName.entries) {
                val screen = FakeTrainingScreen(active = if (target == StatName.SPEED) StatName.WIT else StatName.SPEED)
                // The fake selects the stat the tapped button belongs to, so a swapped map switches to the wrong tab.
                val switched =
                    switchTrainingTab(
                        target = target,
                        activeTab = screen::activeTab,
                        targetHeaderShown = { screen.headerShown(target) },
                        tap = { screen.tap(statOfButton.getValue(buttons.getValue(target))) },
                        nowMs = { screen.now },
                        pause = screen::pause,
                        warn = {},
                    )
                assertTrue(switched, "$scenario $target")
            }
        }
    }
}
