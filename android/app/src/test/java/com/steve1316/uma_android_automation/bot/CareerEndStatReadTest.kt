package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** The career-end stat read never passes an older value off as fresh: stats still not accepted after a second read are named lastKnown in `[CAREER_END]`. */
@DisplayName("Career-end stat read")
class CareerEndStatReadTest {
    /** Plays [readCareerEndStats] over scripted reads; returns (result, reads, reopens with their argument). */
    private fun play(reads: List<Set<StatName>>, reopenLands: Boolean = true): Triple<Set<StatName>, Int, List<Set<StatName>>> {
        var readCount = 0
        val reopens = mutableListOf<Set<StatName>>()
        val result =
            readCareerEndStats(
                read = { reads[readCount++] },
                reopen = {
                    reopens += it
                    reopenLands
                },
            )
        return Triple(result, readCount, reopens)
    }

    @Nested
    @DisplayName("the read")
    inner class Read {
        @Test
        fun `a good final read is taken once and marks nothing`() {
            assertEquals(Triple(emptySet<StatName>(), 1, emptyList<Set<StatName>>()), play(listOf(emptySet())))
        }

        @Test
        fun `an unusable final read is read once more, and a good second read marks nothing`() {
            val power = setOf(StatName.POWER)
            assertEquals(Triple(emptySet<StatName>(), 2, listOf(power)), play(listOf(power, emptySet())))
        }

        @Test
        fun `a stat still unusable after the second read is returned, and there is no third read`() {
            val power = setOf(StatName.POWER)
            assertEquals(Triple(power, 2, listOf(power)), play(listOf(power, power, emptySet())))
        }

        @Test
        fun `the second read decides on its own`() {
            assertEquals(setOf(StatName.WIT), play(listOf(setOf(StatName.POWER), setOf(StatName.WIT))).first)
        }

        @Test
        fun `when the dialog cannot be opened again the first read stands`() {
            val all = StatName.entries.toSet()
            assertEquals(Triple(all, 1, listOf(all)), play(listOf(all, emptySet()), reopenLands = false))
        }
    }

    @Nested
    @DisplayName("the lastKnown marker")
    inner class Marker {
        @Test
        fun `it names the stats in ledger order with the ledger keys`() {
            assertEquals(listOf("spd", "pwr", "wit"), lastKnownLedgerKeys(setOf(StatName.WIT, StatName.SPEED, StatName.POWER), emptySet()))
            assertEquals(listOf("spd", "sta", "pwr", "grt", "wit"), lastKnownLedgerKeys(StatName.entries.toSet(), emptySet()))
        }

        @Test
        fun `a stat reported as unread is not last-known, and a good read marks nothing`() {
            assertEquals(listOf("pwr"), lastKnownLedgerKeys(setOf(StatName.POWER, StatName.WIT), setOf(StatName.WIT)))
            assertEquals(emptyList<String>(), lastKnownLedgerKeys(emptySet(), emptySet()))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val campaign by lazy { source("bot/Campaign.kt") }
        private val trainee by lazy { source("types/Trainee.kt") }

        @Test
        fun `the Details read tracks every stat it does not accept`() {
            val sequential = trainee.substringAfter("if (isAptitudeDialog) detailsFloorRejections.putAll(floorRejected)").substringBefore("} finally {")
            assertTrue(sequential.contains("val decision = decideStatUpdate(statName, oldValue, newValue, newValue != readerValue)"))
            assertTrue(sequential.contains("val accepted = decision is StatMismatchPolicy.Decision.Accept || decision is StatMismatchPolicy.Decision.Promote"))
            assertTrue(sequential.contains("if (accepted) detailsUnacceptedReads.remove(statName) else detailsUnacceptedReads.add(statName)"))
            assertTrue(sequential.indexOf("if (isAptitudeDialog) {") < sequential.indexOf("when (decision) {"))
        }

        @Test
        fun `the career end starts every stat unread and reads through readCareerEndStats`() {
            val end = campaign.substringAfter("trainee.detailsFloorRejections.clear()\n").substringBefore("// Print the final Trainee information.")
            assertTrue(end.startsWith("                trainee.detailsUnacceptedReads.clear()\n                trainee.detailsUnacceptedReads.addAll(StatName.entries)\n"))
            val read = end.substringAfter("readCareerEndStats(").substringBefore("\n                    )\n")
            assertTrue(read.contains("handleDialogs()\n                            trainee.detailsUnacceptedReads.toSet()"))
            assertTrue(read.contains("val opened = buttonLocation != null && ButtonDetails.click(game.imageUtils)"))
            assertTrue(read.indexOf("trainee.detailsFloorRejections.clear()") > read.indexOf("if (opened) {"), "the rejections reset only for a second read")
            assertTrue(end.contains("careerEndLastKnownStats = lastKnownLedgerKeys(notAccepted, contradicted.keys)"))
            assertEquals(1, Regex("""handleDialogs\(\)""").findAll(end).count(), "the Details read runs only inside the read lambda")
        }

        @Test
        fun `the ledger line and the corpus record carry lastKnown only when a stat is last-known`() {
            assertTrue(campaign.contains("            append(\" wit=\").append(st.wit)\n            if (careerEndLastKnownStats.isNotEmpty()) append(\" lastKnown=\").append(careerEndLastKnownStats.joinToString(\",\"))\n            append(\" skillPts=\")"))
            assertTrue(campaign.contains("                put(\"wit\", st.wit)\n                if (careerEndLastKnownStats.isNotEmpty()) put(\"lastKnown\", JSONArray(careerEndLastKnownStats))\n"))
        }

        @Test
        fun `the queue report's career result gets the same last-known stats`() {
            assertTrue(campaign.contains("                listOf(st.speed, st.stamina, st.power, st.guts, st.wit),\n                careerEndLastKnownStats,\n            )"))
        }

        private fun source(relative: String): String = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

        private fun kotlinRoot(): File {
            var dir: File? = File(System.getProperty("user.dir") ?: ".")
            repeat(5) {
                val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
                if (a.isDirectory) return a
                val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
                if (b.isDirectory) return b
                dir = dir?.parentFile
            }
            throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
        }
    }
}
