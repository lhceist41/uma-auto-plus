package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.SmartBorrowList
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The deck record every career carries: what a driven launch publishes, how a career claims it by launch id, and the
 * privacy rule that the picker row's owner line never reaches the record.
 */
@DisplayName("Launch deck record")
class LaunchDeckObservationTest {
    @BeforeEach
    @AfterEach
    fun isolate() {
        LaunchDeckObservation.latest = null
        LaunchTransactionGate.reset()
    }

    private val curated = listOf("[Oguri Cap's Lunchbox] Oguri Cap") + SmartBorrowList.priority

    private fun observation(
        id: String = "proc-1",
        slot: Int? = 3,
        counts: List<Int>? = listOf(4, 1, 0, 0, 1),
        borrow: LaunchBorrow? = LaunchBorrow("[Fire at My Heels] Kitasan Black", "Kitasan Black", "Fire at My Heels", null, "smart"),
    ) = LaunchDeckObservation(
        launchTransactionId = id,
        slot = slot,
        slotReason = if (slot == null) "not read: Required Support Deck is off" else null,
        typeCounts = counts,
        typeCountsReason = if (counts == null) "not read: Deck Concentration Check is off" else null,
        borrow = borrow,
        borrowReason = if (borrow == null) "not tapped: the Friends slot was already filled" else null,
    )

    @Nested
    @DisplayName("borrow identity from the tap boundary")
    inner class BorrowIdentity {
        @Test
        @DisplayName("a smart pick keeps the matched entry, character and outfit and drops the owner line")
        fun smartPickDropsOwner() {
            val row = "TrainerSteve\n[Fire at My Heels]\nKitasan Black"
            val borrow = launchBorrowFromTap(row, "borrow_smart_row", curated)
            assertEquals("[Fire at My Heels] Kitasan Black", borrow.entry)
            assertEquals("Kitasan Black", borrow.character)
            assertEquals("Fire at My Heels", borrow.outfit)
            assertEquals("smart", borrow.source)
            assertNull(borrow.limitBreak)
            assertFalse(launchDeckJson(observation(borrow = borrow)).toString().contains("TrainerSteve"))
        }

        @Test
        @DisplayName("a default pick of a card off the list has no entry but still names the card")
        fun defaultPickNamesTheCard() {
            val borrow = launchBorrowFromTap("Owner Name\n[Tracen Reception]\nTazuna Hayakawa", "borrow_card_first_valid_row", emptyList())
            assertNull(borrow.entry)
            assertEquals("Tazuna Hayakawa", borrow.character)
            assertEquals("Tracen Reception", borrow.outfit)
            assertEquals("default", borrow.source)
        }

        @Test
        @DisplayName("a row that read as one bracketless line is never taken as a character name")
        fun bracketlessSingleLineIsUnread() {
            val borrow = launchBorrowFromTap("TrainerSteve", "borrow_card_first_valid_row", curated)
            assertNull(borrow.character)
            assertNull(borrow.outfit)
            assertNull(borrow.entry)
            // A corrupted closing bracket defeats both the fuzzy entry match and the split: the pick is recorded as unread, never guessed.
            val noisy = launchBorrowFromTap("[Fire at My Heelsl Kitasan Black", "borrow_smart_row", curated)
            assertNull(noisy.entry)
            assertNull(noisy.character)
            assertNull(noisy.display)
            assertEquals("[Fire at My Heels] Kitasan Black", launchBorrowFromTap("[Fire at My Heels]\nKitasan Black", "borrow_smart_row", curated).entry)
        }

        @Test
        @DisplayName("a merged outfit and character line splits at the closing bracket")
        fun mergedLineSplits() {
            assertEquals("Kitasan Black" to "Fire at My Heels", borrowRowIdentity("[Fire at My Heels] Kitasan Black"))
            assertEquals(null to "Fire at My Heels", borrowRowIdentity("Owner\n[Fire at My Heels]"))
        }

        @Test
        @DisplayName("an owner name with brackets of its own never becomes the outfit")
        fun ownerBracketsNeverLeak() {
            assertEquals(null to null, borrowRowIdentity("Bob(JP)\nKitasan Black"))
            assertEquals(null to null, borrowRowIdentity("Bob(JP)\nFire at My Heelsl\nKitasan Black"))
            assertEquals("Kitasan Black" to "Fire at My Heels", borrowRowIdentity("Bob(JP)\n[Fire at My Heels]\nKitasan Black"))
            assertEquals("Kitasan Black" to "Fire at My Heels", borrowRowIdentity("[GG] Bob\n[Fire at My Heels]\nKitasan Black"))
            assertEquals(null to null, borrowRowIdentity("[GG] Bob\nKitasan Black\nextra"))
        }

        @Test
        @DisplayName("the build-aware launch records character, outfit and limit break; the preferred template row only its source")
        fun buildAwareAndTemplate() {
            val built = launchBorrowFromTap("Kitasan Black [Fire at My Heels] lb=4", "a3r2_launch_borrow_select", curated)
            assertEquals("Kitasan Black", built.character)
            assertEquals("Fire at My Heels", built.outfit)
            assertEquals(4, built.limitBreak)
            assertEquals("build_aware", built.source)
            val unread = launchBorrowFromTap("- [-] lb=-", "a3r2_launch_borrow_select", curated)
            assertNull(unread.character)
            assertNull(unread.outfit)
            assertNull(unread.limitBreak)
            val template = launchBorrowFromTap("preferred template row", "borrow_preferred_row", curated)
            assertEquals("preferred_template", template.source)
            assertNull(template.character)
            assertNull(template.display)
        }
    }

    @Nested
    @DisplayName("career record")
    inner class CareerRecord {
        @Test
        @DisplayName("a driven launch with deck 3 and a borrow records both")
        fun drivenLaunch() {
            val json = launchDeckJson(observation())
            assertTrue(json.getBoolean("observed"))
            assertEquals(3, json.getInt("slot"))
            assertFalse(json.has("slotReason"))
            val types = json.getJSONObject("types")
            assertEquals(listOf(4, 1, 0, 0, 1), listOf("spd", "sta", "pwr", "grt", "wit").map { types.getInt(it) })
            val borrow = json.getJSONObject("borrow")
            assertEquals("Kitasan Black", borrow.getString("character"))
            assertEquals("Fire at My Heels", borrow.getString("outfit"))
            assertEquals("[Fire at My Heels] Kitasan Black", borrow.getString("entry"))
            assertTrue(borrow.isNull("limitBreak"))
            assertTrue(json.isNull("cards"))
            assertEquals(LAUNCH_DECK_CARDS_NOT_READ, json.getString("cardsReason"))
            assertEquals(" deck=3 borrow=\"[Fire at My Heels] Kitasan Black\"", launchDeckLedgerTokens(observation()))
        }

        @Test
        @DisplayName("an unread deck number, type counts or borrow is null beside its reason")
        fun unreadFieldsCarryReasons() {
            val json = launchDeckJson(observation(slot = null, counts = null, borrow = null))
            assertTrue(json.getBoolean("observed"))
            assertTrue(json.isNull("slot"))
            assertEquals("not read: Required Support Deck is off", json.getString("slotReason"))
            assertTrue(json.isNull("types"))
            assertEquals("not read: Deck Concentration Check is off", json.getString("typesReason"))
            assertTrue(json.isNull("borrow"))
            assertEquals("not tapped: the Friends slot was already filled", json.getString("borrowReason"))
            assertEquals(" deck=unknown", launchDeckLedgerTokens(observation(slot = null, counts = null, borrow = null)))
        }

        @Test
        @DisplayName("a launch nobody observed records not observed, with every value null")
        fun notObserved() {
            val json = launchDeckJson(null)
            assertFalse(json.getBoolean("observed"))
            assertEquals(LAUNCH_DECK_NOT_OBSERVED, json.getString("reason"))
            for (key in listOf("slot", "types", "borrow", "cards")) assertTrue(json.isNull(key), key)
            assertEquals(" deck=unknown", launchDeckLedgerTokens(null))
        }

        @Test
        @DisplayName("the record is plain JSON a line writer can append")
        fun serializes() {
            val parsed = JSONObject(launchDeckJson(observation()).toString())
            assertEquals(3, parsed.getInt("slot"))
        }

        @Test
        @DisplayName("the log line names every value and every reason")
        fun logLine() {
            val line = launchDeckLogLine(observation(counts = null))
            val expected =
                "[DECK] Launch deck record: slot=3 types=unknown (not read: Deck Concentration Check is off) " +
                    "borrow=\"[Fire at My Heels] Kitasan Black\" source=smart launchTx=proc-1"
            assertEquals(expected, line)
        }
    }

    @Nested
    @DisplayName("claiming by launch id")
    inner class Claim {
        @Test
        @DisplayName("the career that adopts the published launch claims its deck")
        fun drivenCareerClaims() {
            LaunchTransactionGate.initProcess("proc")
            val launch = LaunchTransactionGate.beginLaunch(1L)
            LaunchDeckObservation.latest = observation(id = launch.id)
            val adopted = LaunchTransactionGate.adopt(2L)
            assertEquals(launch.id, adopted.id)
            assertEquals(3, LaunchDeckObservation.forCareer(adopted.id)?.slot)
        }

        @Test
        @DisplayName("a resumed or restarted career mints its own id and gets no deck, never the previous launch's")
        fun resumedCareerGetsNone() {
            LaunchTransactionGate.initProcess("proc")
            val launch = LaunchTransactionGate.beginLaunch(1L)
            LaunchDeckObservation.latest = observation(id = launch.id)
            LaunchTransactionGate.adopt(2L)
            // The next navigation found the career already running and resumed it: no new publish, a fresh id.
            LaunchTransactionGate.beginLaunch(3L)
            val resumed = LaunchTransactionGate.adopt(4L)
            assertNull(LaunchDeckObservation.forCareer(resumed.id))
            assertNull(LaunchDeckObservation.forCareer(null))
            assertFalse(launchDeckJson(LaunchDeckObservation.forCareer(resumed.id)).getBoolean("observed"))
        }

        @Test
        @DisplayName("a launch whose Start Career click failed or was stopped publishes nothing, so a hand-started career claims nothing")
        fun failedClickPublishesNothing() {
            LaunchTransactionGate.initProcess("proc")
            val launch = LaunchTransactionGate.beginLaunch(1L)
            // Built before the click, never published because the click did not return true.
            val built = observation(id = launch.id)
            assertNull(LaunchDeckObservation.latest)
            // The player starts a career by hand; it adopts the still pending launch id.
            val adopted = LaunchTransactionGate.adopt(2L)
            assertEquals(launch.id, adopted.id)
            assertNull(LaunchDeckObservation.forCareer(adopted.id))
            assertFalse(launchDeckJson(LaunchDeckObservation.forCareer(adopted.id)).getBoolean("observed"))
            assertEquals(3, built.slot)
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val base = "android/app/src/main/java/com/steve1316/uma_android_automation"

        @Test
        @DisplayName("the career record takes the deck by the adopted launch id and the ledger line carries its tokens")
        fun campaignWiring() {
            val campaign = source("$base/bot/Campaign.kt")
            assertTrue(campaign.contains("val launchDeck = LaunchDeckObservation.forCareer(LaunchTransactionGate.active?.id)"))
            assertTrue(campaign.contains("put(\"deck\", launchDeckJson(launchDeck))"))
            assertTrue(campaign.contains("append(launchDeckLedgerTokens(launchDeck))"))
        }

        @Test
        @DisplayName("the launch publishes before the Start Career tap, records only sent taps, and resets per navigation")
        fun navigatorWiring() {
            val navigator = source("$base/CareerLaunchNavigator.kt")
            val build = navigator.indexOf("val launchDeck = buildLaunchDeckObservation(checkDeckConcentration())")
            assertTrue(build > 0)
            val click = navigator.indexOf("if (ButtonStartCareer.click(iu) || ButtonStartCareerOffset.click(iu) || ButtonStartCareerRight.click(iu)) {", build)
            val publish = navigator.indexOf("publishLaunchDeckObservation(launchDeck)", build)
            assertTrue(build < click && click < publish && publish < navigator.indexOf("return TransitionResult.Continue", click), "publish only inside the successful click branch")
            assertEquals(1, Regex("publishLaunchDeckObservation\\(launchDeck\\)").findAll(navigator).count())
            assertEquals(1, Regex("LaunchDeckObservation\\.latest = ").findAll(navigator).count())
            val opened = navigator.indexOf("borrowPickerOpenedThisLaunch = true")
            assertTrue(opened > 0 && navigator.lastIndexOf("friendSlotFillAttempts++", opened) > opened - 80, "the picker flag is set where the picker opens")
            val flagReset = navigator.indexOf("borrowPickerOpenedThisLaunch = false")
            assertTrue(flagReset > navigator.indexOf("fun navigate(") && flagReset < navigator.indexOf("LaunchTransactionGate.beginLaunch("))
            assertTrue(navigator.contains("!borrowPickerOpenedThisLaunch -> \"not tapped: the Friends slot was already filled\""))
            val tapped = navigator.indexOf("BorrowTapStatus.TAPPED -> {")
            assertTrue(tapped > 0 && navigator.indexOf("launchBorrowTap = identity to actionName", tapped) - tapped < 80)
            assertEquals(1, Regex("launchBorrowTap = identity to actionName").findAll(navigator).count())
            val reset = navigator.indexOf("launchBorrowTap = null")
            assertTrue(reset > navigator.indexOf("fun navigate(") && reset < navigator.indexOf("LaunchTransactionGate.beginLaunch("))
            val body = navigator.substring(navigator.indexOf("private fun buildLaunchDeckObservation("), navigator.indexOf("LaunchDeckObservation.latest = observation"))
            assertFalse(body.contains("getSourceBitmap") || body.contains("performOCR") || body.contains("CoordinateTap") || body.contains(".click("), "the publish must read nothing new")
        }

        private fun source(relative: String): String {
            var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
            while (dir != null) {
                val file = File(dir, relative)
                if (file.isFile) return file.readText().replace("\r\n", "\n")
                dir = dir.parentFile
            }
            error("source file not found: $relative")
        }
    }
}
