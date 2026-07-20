package com.steve1316.uma_android_automation.bot

import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Lifecycle tests for the Spark Reroll transaction: who may create it, which transitions are
 * legal, what invalidates it, and the structural guarantees (single spend, single final
 * confirmation, records-before-completion). The screen handlers cannot run under JUnit, so
 * the ordering and ownership rules they rely on are enforced by source guards, the same way
 * the career-finalization lifecycle is pinned.
 */
@DisplayName("Spark reroll transaction lifecycle")
class SparkRerollGateTest {
    private fun reading(complete: Boolean = true) =
        SparkSetReading(
            listOf(
                SparkRowFact("Stamina", 2, SparkRowKind.STAT),
                SparkRowFact("Pace Chaser", 2, SparkRowKind.APTITUDE),
                SparkRowFact("u", 1, SparkRowKind.UNIQUE),
            ),
            if (complete) SparkScanTermination.COMPLETE_END_MARKER else SparkScanTermination.TIMED_OUT_PARTIAL,
        )

    private fun choice(side: SparkSetSide = SparkSetSide.ORIGINAL, certain: Boolean = true): SparkChoice {
        val breakdown = SparkKeepPolicy.breakdown(reading(), SparkChooserProfile(null, null, emptyList(), null, null, null, emptyList()), true)
        return SparkChoice(side, if (certain) "tie" else "incomplete_read", "test", certain, breakdown, breakdown)
    }

    private fun freshTransaction(): SparkRerollTransaction {
        SparkRerollGate.beginCareer("nonce1", queueRun = 2, nowMs = 1_000L)
        return SparkRerollGate.transaction!!
    }

    /** Walk a transaction to the given state along the happy path. */
    private fun driveTo(state: SparkTxState): SparkRerollTransaction {
        val tx = freshTransaction()
        if (state == SparkTxState.IDLE) return tx
        tx.captureOriginal(reading(), "[Murmuring Stream] Super Creek", "Unity Cup")
        if (state == SparkTxState.ORIGINAL_CAPTURED) return tx
        tx.approveSpend("test gate positive")
        if (state == SparkTxState.SPEND_APPROVED) return tx
        tx.confirmSpend(nowMs = 2_000L)
        if (state == SparkTxState.SPEND_CONFIRMED) return tx
        tx.captureRerolled(reading())
        if (state == SparkTxState.REROLLED_CAPTURED) return tx
        tx.introPassed()
        if (state == SparkTxState.SELECTION_INTRO_PASSED) return tx
        tx.recordPagerRead(SparkSetSide.ORIGINAL, reading())
        tx.recordPagerRead(SparkSetSide.REROLLED, reading())
        tx.setsVerified()
        if (state == SparkTxState.BOTH_SETS_VERIFIED) return tx
        tx.selectWinner(choice())
        if (state == SparkTxState.WINNER_SELECTED) return tx
        tx.verifyFinalConfirmation()
        if (state == SparkTxState.FINAL_CONFIRMATION_VERIFIED) return tx
        tx.markKeptRecorded()
        tx.markChoiceRecorded()
        tx.complete()
        return tx
    }

    @BeforeEach
    @AfterEach
    fun isolate() {
        SparkRerollGate.reset()
    }

    @Nested
    @DisplayName("creation and identity")
    inner class Creation {
        @Test
        fun `a real career start replaces whatever the previous career left`() {
            val old = driveTo(SparkTxState.SPEND_CONFIRMED)
            SparkRerollGate.beginCareer("nonce2", queueRun = 3, nowMs = 9_000L)
            val fresh = SparkRerollGate.transaction!!
            assertNotEquals(old, fresh)
            assertEquals(SparkTxState.IDLE, fresh.state)
            assertFalse(fresh.spendEverConfirmed)
        }

        @Test
        fun `a process with no career start has no transaction and no authorization`() {
            assertNull(SparkRerollGate.transaction, "process recreation must not inherit a transaction")
            assertFalse(sparkSelectionDrivable(SparkRerollGate.transaction, 1_000_000L))
        }

        @Test
        fun `capturing the original binds the exact career token`() {
            val tx = freshTransaction()
            tx.captureOriginal(reading(), "[Jokester Vibes] Tosen Jordan", "URA Finale")
            assertEquals("[Jokester Vibes] Tosen Jordan|URA Finale|run2|nonce1", tx.careerToken)
        }

        @Test
        fun `two careers can never share a token even when everything else matches`() {
            val first = buildSparkCareerToken("Super Creek", "Unity Cup", 1, "nA")
            val second = buildSparkCareerToken("Super Creek", "Unity Cup", 1, "nB")
            assertNotEquals(first, second)
        }
    }

    @Nested
    @DisplayName("spend discipline")
    inner class Spend {
        @Test
        fun `a spend cannot be approved on an incomplete original read`() {
            val tx = freshTransaction()
            tx.captureOriginal(reading(complete = false), "t", "s")
            val result = tx.approveSpend("gate positive")
            assertFalse(result.ok)
            assertEquals(SparkTxState.ORIGINAL_CAPTURED, tx.state)
        }

        @Test
        fun `a duplicate spend is structurally impossible`() {
            val tx = driveTo(SparkTxState.SPEND_CONFIRMED)
            assertTrue(tx.spendEverConfirmed)
            assertFalse(tx.approveSpend("again").ok, "no second approval after a confirmed spend")
            assertFalse(tx.confirmSpend(3_000L).ok, "no second confirmation outside SPEND_APPROVED")
        }

        @Test
        fun `a spend cannot be confirmed without an approval`() {
            val tx = freshTransaction()
            tx.captureOriginal(reading(), "t", "s")
            assertFalse(tx.confirmSpend(2_000L).ok)
        }

        @Test
        fun `declining is terminal for the reroll`() {
            val tx = driveTo(SparkTxState.ORIGINAL_CAPTURED)
            assertTrue(tx.declineSpend("gate negative").ok)
            assertEquals(SparkTxState.DECLINED, tx.state)
            assertTrue(tx.terminal)
            assertFalse(tx.approveSpend("late change of heart").ok)
        }
    }

    @Nested
    @DisplayName("selection discipline")
    inner class Selection {
        @Test
        fun `post-spend states are exactly the selection-drivable ones`() {
            val drivable =
                setOf(
                    SparkTxState.SPEND_CONFIRMED,
                    SparkTxState.REROLLED_CAPTURED,
                    SparkTxState.SELECTION_INTRO_PASSED,
                    SparkTxState.BOTH_SETS_VERIFIED,
                    SparkTxState.WINNER_SELECTED,
                    SparkTxState.FINAL_CONFIRMATION_VERIFIED,
                )
            for (state in SparkTxState.entries) {
                if (state == SparkTxState.DECLINED || state == SparkTxState.BLOCKED || state == SparkTxState.COMPLETE) continue
                val tx = driveTo(state)
                assertEquals(state, tx.state, "driveTo must land on $state")
                assertEquals(state in drivable, tx.postSpend, state.name)
            }
        }

        @Test
        fun `both pager pages must be read before the sets count as verified`() {
            val tx = driveTo(SparkTxState.REROLLED_CAPTURED)
            tx.introPassed()
            tx.recordPagerRead(SparkSetSide.REROLLED, reading())
            assertFalse(tx.setsVerified().ok, "one page is not both pages")
            tx.recordPagerRead(SparkSetSide.ORIGINAL, reading())
            assertTrue(tx.setsVerified().ok)
        }

        @Test
        fun `a certain winner requires both sets verified`() {
            val tx = driveTo(SparkTxState.SELECTION_INTRO_PASSED)
            assertFalse(tx.selectWinner(choice(SparkSetSide.REROLLED)).ok)
        }

        @Test
        fun `the uncertain fallback may only keep the original, and only with the original page read`() {
            val tx = driveTo(SparkTxState.SELECTION_INTRO_PASSED)
            assertFalse(tx.selectWinner(choice(SparkSetSide.ORIGINAL, certain = false)).ok, "no original page read yet")
            tx.recordPagerRead(SparkSetSide.ORIGINAL, reading())
            assertFalse(tx.selectWinner(choice(SparkSetSide.REROLLED, certain = false)).ok, "an uncertain rerolled keep is never allowed")
            assertTrue(tx.selectWinner(choice(SparkSetSide.ORIGINAL, certain = false)).ok)
        }

        @Test
        fun `the final confirmation can only be verified after a winner exists`() {
            val tx = driveTo(SparkTxState.BOTH_SETS_VERIFIED)
            assertFalse(tx.verifyFinalConfirmation().ok)
            tx.selectWinner(choice())
            assertTrue(tx.verifyFinalConfirmation().ok)
        }

        @Test
        fun `a spend career cannot complete without its kept and choice records`() {
            val tx = driveTo(SparkTxState.FINAL_CONFIRMATION_VERIFIED)
            assertFalse(tx.complete().ok, "no records written yet")
            tx.markKeptRecorded()
            assertFalse(tx.complete().ok, "kept alone is not enough")
            tx.markChoiceRecorded()
            assertTrue(tx.complete().ok)
            assertEquals(SparkTxState.COMPLETE, tx.state)
        }

        @Test
        fun `a duplicate final confirmation is refused after completion`() {
            val tx = driveTo(SparkTxState.COMPLETE)
            assertFalse(tx.verifyFinalConfirmation().ok, "COMPLETE never re-verifies")
            assertTrue(tx.complete().ok, "complete is idempotent for the re-click path")
        }

        @Test
        fun `the one confirmation retry and the one pager-navigation retry are single-use`() {
            val tx = driveTo(SparkTxState.WINNER_SELECTED)
            assertTrue(tx.useConfirmationRetry())
            assertFalse(tx.useConfirmationRetry())
            assertTrue(tx.usePagerNavRetry())
            assertFalse(tx.usePagerNavRetry())
        }

        @Test
        fun `blocking is terminal and keeps the reason`() {
            val tx = driveTo(SparkTxState.WINNER_SELECTED)
            tx.block("header contradiction")
            assertEquals(SparkTxState.BLOCKED, tx.state)
            assertEquals("header contradiction", tx.blockedReason)
            assertFalse(tx.postSpend, "a blocked transaction never drives a selection screen again")
        }
    }

    @Nested
    @DisplayName("career attachment boundary (the 2026-07-19 launch-Home defect)")
    inner class Attachment {
        /** The cold-start launch sequence: the navigator crosses the game's Home screen on its
         * way INTO the career, before the career task ever runs. */
        private fun launchNavigationPassesHome() {
            SparkRerollGate.clearOnHome()
        }

        @Test
        fun `a cold-start launch that passes Home does not lose the career's transaction`() {
            // Arming happens at attachment, so during launch navigation there is nothing to
            // lose in the first place - and even if a transaction existed, Home now spares a
            // pre-spend one. Both halves are asserted.
            assertNull(SparkRerollGate.transaction, "no transaction may exist before the career is attached")
            launchNavigationPassesHome()
            assertNull(SparkRerollGate.transaction)

            val tx = freshTransaction() // attachment
            launchNavigationPassesHome()
            assertNotNull(SparkRerollGate.transaction, "a pre-spend transaction must survive a Home pass")
            assertEquals(tx, SparkRerollGate.transaction)
        }

        @Test
        fun `a mid-career Home bounce does not destroy a pre-spend transaction`() {
            // The game's daily reset can drop the bot back to the lobby mid-career; the bot
            // re-enters the same career. That Home pass must not disarm the spark flow.
            val tx = driveTo(SparkTxState.ORIGINAL_CAPTURED)
            launchNavigationPassesHome()
            assertEquals(tx, SparkRerollGate.transaction)
            assertEquals(SparkTxState.ORIGINAL_CAPTURED, SparkRerollGate.transaction?.state)
        }

        @Test
        fun `a post-spend transaction at Home is cleared as stale`() {
            driveTo(SparkTxState.SPEND_CONFIRMED)
            assertTrue(SparkRerollGate.clearOnHome(), "a committed 30 TP spend must not survive a Home return")
            assertNull(SparkRerollGate.transaction)
        }

        @Test
        fun `a terminal transaction at Home is cleared`() {
            // DECLINED and BLOCKED are off the happy path, so they are driven explicitly.
            val declined = driveTo(SparkTxState.ORIGINAL_CAPTURED)
            declined.declineSpend("gate negative")
            assertEquals(SparkTxState.DECLINED, declined.state)
            assertTrue(SparkRerollGate.clearOnHome(), "DECLINED")
            assertNull(SparkRerollGate.transaction)
            SparkRerollGate.reset()

            val blocked = driveTo(SparkTxState.ORIGINAL_CAPTURED)
            blocked.block("test")
            assertEquals(SparkTxState.BLOCKED, blocked.state)
            assertTrue(SparkRerollGate.clearOnHome(), "BLOCKED")
            assertNull(SparkRerollGate.transaction)
            SparkRerollGate.reset()

            val complete = driveTo(SparkTxState.COMPLETE)
            assertEquals(SparkTxState.COMPLETE, complete.state)
            assertTrue(SparkRerollGate.clearOnHome(), "COMPLETE")
            assertNull(SparkRerollGate.transaction)
        }

        @Test
        fun `preSpend is exactly the non-terminal states that never committed TP`() {
            for (state in SparkTxState.entries) {
                if (state == SparkTxState.BLOCKED) continue
                val tx = driveTo(state)
                val expected = !tx.spendEverConfirmed && !tx.terminal
                assertEquals(expected, tx.preSpend, state.name)
                SparkRerollGate.reset()
            }
        }

        @Test
        fun `a bot started directly inside an active career still attaches`() {
            // No launch navigation runs in that case; attachment is the same single call.
            val tx = freshTransaction()
            assertEquals(SparkTxState.IDLE, tx.state)
            assertNotNull(SparkRerollGate.transaction)
        }

        @Test
        fun `Home before attachment cannot act as post-career cleanup`() {
            // With nothing armed, a Home pass is a no-op rather than a silent cleanup that
            // would mask a missing attachment.
            assertFalse(SparkRerollGate.clearOnHome())
        }
    }

    @Nested
    @DisplayName("invalidating lifecycle events")
    inner class Invalidation {
        @Test
        fun `manual stop, abort, error, breakpoint, and skipped run all invalidate`() {
            for (code in TaskResultCode.entries.filter { it != TaskResultCode.TASK_RESULT_COMPLETE }) {
                assertTrue(shouldClearSparkTransactionForRunResult(code), code.name)
            }
            assertFalse(shouldClearSparkTransactionForRunResult(TaskResultCode.TASK_RESULT_COMPLETE))
        }

        @Test
        fun `invalidate drops the transaction regardless of phase`() {
            // invalidate is the unconditional path (run results, interrupted navigation): even
            // a pre-spend transaction goes, because the run it belonged to is over.
            driveTo(SparkTxState.SPEND_CONFIRMED)
            SparkRerollGate.invalidate("run result MANUALLY_STOPPED")
            assertNull(SparkRerollGate.transaction)

            driveTo(SparkTxState.ORIGINAL_CAPTURED)
            SparkRerollGate.invalidate("between-run navigation interrupted")
            assertNull(SparkRerollGate.transaction, "invalidate is unconditional, unlike the Home clear")

            driveTo(SparkTxState.WINNER_SELECTED)
            SparkRerollGate.clearOnHome()
            assertNull(SparkRerollGate.transaction)
        }

        @Test
        fun `a missing transaction never drives a selection screen`() {
            assertFalse(sparkSelectionDrivable(null, 5_000L))
        }

        @Test
        fun `a pre-spend or terminal transaction never drives a selection screen`() {
            for (state in listOf(SparkTxState.IDLE, SparkTxState.ORIGINAL_CAPTURED, SparkTxState.SPEND_APPROVED, SparkTxState.DECLINED, SparkTxState.COMPLETE)) {
                val tx = driveTo(state)
                assertFalse(sparkSelectionDrivable(tx, 3_000L), state.name)
                SparkRerollGate.reset()
            }
        }

        @Test
        fun `a stale spend is unusable even in a post-spend state`() {
            val tx = driveTo(SparkTxState.SPEND_CONFIRMED)
            val spentAt = tx.spendConfirmedAtMs!!
            assertTrue(sparkSelectionDrivable(tx, spentAt + SPARK_TRANSACTION_MAX_AGE_MS))
            assertFalse(sparkSelectionDrivable(tx, spentAt + SPARK_TRANSACTION_MAX_AGE_MS + 1))
            assertFalse(sparkSelectionDrivable(tx, spentAt - 1), "a clock running backwards is stale, not fresh")
        }
    }

    @Nested
    @DisplayName("source guards: ownership and screen-detection ordering")
    inner class SourceGuards {
        @Test
        fun `transactions are created only at the career attachment boundary in Game start`() {
            // Moved out of StartModule's run loop on 2026-07-19: run start precedes the
            // cold-start launch navigation, whose Home pass destroyed the transaction.
            val begin = "SparkRerollGate.beginCareer("
            val callers = sourceFiles().filter { it.name != "SparkRerollGate.kt" && begin in it.readText() }.map { it.name }.toSet()
            assertEquals(setOf("Game.kt"), callers, "only the career attachment point may create a transaction")
            assertFalse(begin in sourceFile("StartModule.kt").readText(), "the queue run loop must not arm before launch navigation")
        }

        @Test
        fun `the arming call sits after launch navigation and before the career task starts`() {
            val game = sourceFile("bot/Game.kt").readText()
            val navigate = game.indexOf("navigator.navigate(")
            val arm = game.indexOf("SparkRerollGate.beginCareer(")
            val taskStart = game.indexOf("task.start(maxRuntimeMinutes")
            assertTrue(navigate in 0 until arm, "arming must follow the launch navigation that crosses Home")
            assertTrue(arm in 0 until taskStart, "arming must precede the career task")
        }

        @Test
        fun `arming is gated on a real career, never on a misc task`() {
            val game = sourceFile("bot/Game.kt").readText()
            val arm = game.indexOf("SparkRerollGate.beginCareer(")
            assertTrue(arm > 0)
            val preamble = game.substring(maxOf(0, arm - 900), arm)
            assertTrue("!isMiscTask" in preamble, "Daily Races / Team Trials are not careers and must not arm a transaction")
        }

        @Test
        fun `invalidation happens only at the explicit lifecycle sites`() {
            for (call in listOf("SparkRerollGate.invalidate(", "SparkRerollGate.clearOnHome(")) {
                val callers = sourceFiles().filter { it.name != "SparkRerollGate.kt" && call in it.readText() }.map { it.name }.toSet()
                assertTrue(callers.isNotEmpty(), "$call must be wired")
                assertTrue(
                    callers.all { it == "StartModule.kt" || it == "CareerLaunchNavigator.kt" },
                    "$call belongs to the run-result path, interrupted navigation, and the Home return - found $callers",
                )
            }
        }

        @Test
        fun `Campaign never touches the spark gate, and Game touches it only to arm`() {
            assertFalse(
                "SparkRerollGate." in sourceFile("bot/Campaign.kt").readText(),
                "Campaign must not mutate or read the gate - construction-time side effects are the finalize-gate defect all over again",
            )
            // Game owns exactly one interaction: the attachment-point arming inside start().
            // A constructor-time touch would let the navigator's throwaway Game mint or destroy
            // a transaction.
            val game = sourceFile("bot/Game.kt").readText()
            assertEquals(1, Regex("SparkRerollGate\\.").findAll(game).count(), "Game.kt may reference the gate exactly once")
            assertTrue("SparkRerollGate.beginCareer(" in game)
            val classHeader = game.indexOf("class Game")
            val startFun = game.indexOf("fun start()")
            val armAt = game.indexOf("SparkRerollGate.beginCareer(")
            assertTrue(classHeader in 0 until startFun && startFun < armAt, "the gate may only be touched inside start(), never at construction")
        }

        @Test
        fun `every dedicated spark state is detected before the generic POST_RUN_RESULTS chain`() {
            val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
            val detectStart = navigator.indexOf("private fun detectScreenState(")
            assertTrue(detectStart >= 0)
            val genericChain = navigator.indexOf("POST_RUN_RESULTS - generic post-run", detectStart)
            assertTrue(genericChain > detectStart, "the generic chain marker must exist inside detectScreenState")
            for (state in listOf(
                "return LaunchScreenState.CONFIRM_REROLL_DIALOG",
                "return LaunchScreenState.SPARK_SELECTION_CONFIRMATION",
                "return LaunchScreenState.SPARKS_KEEP_CONFIRMATION",
                "return LaunchScreenState.SPARK_SELECTION_INTRO",
                "return LaunchScreenState.SPARK_SELECTION_PAGER",
                "return LaunchScreenState.SPARKS_REROLLED_RESULT",
            )) {
                val at = navigator.indexOf(state, detectStart)
                assertTrue(at in (detectStart + 1) until genericChain, "$state must be returned before the generic Next/OK/Confirm/Close chain")
            }
        }

        @Test
        fun `the generic results handler owns no chooser screen`() {
            val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
            val handler = navigator.indexOf("private fun handlePostRunResults(")
            assertTrue(handler >= 0)
            val end = navigator.indexOf("private fun", handler + 10)
            val body = navigator.substring(handler, if (end > handler) end else navigator.length)
            for (token in listOf("SPARK_SELECTION", "SparkRerollGate", "sparkPager", "sparkConfirmation")) {
                assertFalse(token in body, "handlePostRunResults must stay generic; $token belongs to the dedicated handlers")
            }
        }

        @Test
        fun `the kept and choice records are written before the final Confirm click`() {
            val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
            val handler = navigator.indexOf("private fun handleSparkSelectionConfirmation(")
            assertTrue(handler >= 0)
            val clickAt = navigator.indexOf("ButtonConfirm.click", handler)
            val keptAt = navigator.indexOf("recordSparkRows(", handler)
            val choiceAt = navigator.indexOf("appendSparkChoiceRecord(", handler)
            assertTrue(keptAt in (handler + 1) until clickAt, "the kept record precedes the final Confirm")
            assertTrue(choiceAt in (handler + 1) until clickAt, "the choice record precedes the final Confirm")
        }

        @Test
        fun `the ordinary keep confirmation has its own handler, separate from the winner logic`() {
            val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
            assertTrue("SPARKS_KEEP_CONFIRMATION -> handleSparksKeepConfirmation()" in navigator)
            val handler = navigator.indexOf("private fun handleSparksKeepConfirmation(")
            assertTrue(handler > 0)
            val end = navigator.indexOf("private fun clickSparkConfirmationCancel(", handler)
            val body = navigator.substring(handler, end)
            // The plain pill names no side, so none of the winner-header machinery may run here.
            for (token in listOf("transaction.winner", "selectWinner", "verifyFinalConfirmation", "appendSparkChoiceRecord", "navigateToPagerPage")) {
                assertFalse(token in body, "the ordinary keep confirmation must not use $token")
            }
            // It must never claim the header named a side it did not name.
            assertFalse("names the rerolled set but no live reroll transaction" in body)
        }

        @Test
        fun `the keep confirmation blocks without a transaction and on a post-spend contradiction`() {
            val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
            val handler = navigator.indexOf("private fun handleSparksKeepConfirmation(")
            val end = navigator.indexOf("private fun clickSparkConfirmationCancel(", handler)
            val body = navigator.substring(handler, end)
            val nullBlock = body.indexOf("if (transaction == null)")
            val spendBlock = body.indexOf("if (transaction.spendEverConfirmed)")
            val confirmClick = body.indexOf("ButtonConfirm.click")
            assertTrue(nullBlock in 0 until confirmClick, "a missing transaction must block before any Confirm")
            assertTrue(spendBlock in 0 until confirmClick, "a plain pill after a confirmed spend is contradictory and must block first")
            val keptRecord = body.indexOf("recordSparkRows(")
            assertTrue(keptRecord in 0 until confirmClick, "the kept record is written before the Confirm click")
        }

        @Test
        fun `the legacy confirmSparks fallback cannot double-write the kept record`() {
            val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
            val fn = navigator.indexOf("private fun confirmSparks(")
            val end = navigator.indexOf("/** Wraps the capture for the pure pixel probes", fn)
            val body = navigator.substring(fn, end)
            assertTrue("transaction?.keptRecorded != true" in body, "the fallback shares the transaction's dedupe token")
            assertTrue("transaction == null" in body, "with a live transaction the dedicated handler owns the kept record")
        }

        @Test
        fun `the spend dialog state is gated on an approved spend before its template runs`() {
            val navigator = sourceFile("CareerLaunchNavigator.kt").readText()
            val at = navigator.indexOf("SparkTxState.SPEND_APPROVED && ButtonRerollSparksConfirm.check")
            assertTrue(at >= 0, "the spend dialog detection must stay transaction-gated; its green button is the 30 TP spend")
        }
    }

    @Nested
    @DisplayName("corpus compatibility")
    inner class Corpus {
        @Test
        fun `a pre-chooser sparks record parses under the unchanged base schema`() {
            // Verbatim shape of the records the corpus has held since the reroll shipped;
            // the chooser only ADDS fields, so the old readers' fields must stay intact.
            val old =
                JSONObject(
                    """{"type":"sparks","ts":1752275000000,"trainee":"[Murmuring Stream] Super Creek",""" +
                        """"scenario":"Unity Cup","fp":"abc123","phase":"original","rows":[""" +
                        """{"name":"Stamina","stars":2,"kind":"stat"},{"name":"Pace Chaser","stars":2,"kind":"aptitude"}]}""",
                )
            assertEquals("sparks", old.getString("type"))
            assertEquals("original", old.getString("phase"))
            assertEquals(2, old.getJSONArray("rows").length())
            assertEquals("stat", old.getJSONArray("rows").getJSONObject(0).getString("kind"))
        }

        @Test
        fun `the wire kinds and phases the corpus already uses are unchanged`() {
            assertEquals("stat", SparkRowKind.STAT.wire)
            assertEquals("aptitude", SparkRowKind.APTITUDE.wire)
            assertEquals("unique", SparkRowKind.UNIQUE.wire)
            assertEquals("skill", SparkRowKind.WHITE.wire)
            assertEquals("original", SparkSetSide.ORIGINAL.wire)
            assertEquals("rerolled", SparkSetSide.REROLLED.wire)
        }

        @Test
        fun `kept, choice, original, and rerolled records share the career identity through one transaction`() {
            val tx = driveTo(SparkTxState.SPEND_CONFIRMED)
            // Every record the navigator writes for this career stamps tx.careerToken and
            // tx.careerNonce; the token binds trainee, scenario, run, and nonce.
            assertEquals("[Murmuring Stream] Super Creek|Unity Cup|run2|nonce1", tx.careerToken)
            assertEquals("nonce1", tx.careerNonce)
        }

        @Test
        fun `the kept record cannot describe a side other than the chosen winner`() {
            // The writer takes the winner's read; the transaction refuses completion unless
            // that record was written after the header verification of the SAME winner.
            val tx = driveTo(SparkTxState.WINNER_SELECTED)
            assertEquals(SparkSetSide.ORIGINAL, tx.winner)
            assertEquals(tx.choice?.side, tx.winner, "winner and recorded choice are the same object")
        }
    }

    // Source-tree access, shared with the finalize lifecycle guard.
    private fun sourceFile(relative: String): File = File(sourceRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun sourceFiles(): List<File> = sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (candidate.isDirectory) return candidate
            val fromRepoRoot = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (fromRepoRoot.isDirectory) return fromRepoRoot
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
