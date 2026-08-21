package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.APTITUDE_ROLES
import com.steve1316.uma_android_automation.utils.RosterIdentityEvidence
import com.steve1316.uma_android_automation.utils.STAT_KEYS
import com.steve1316.uma_android_automation.utils.rosterFingerprint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The roster-scan record model: identity derivation, the completeness invariants that decide whether
 * a snapshot may be trusted as the account's current roster, duplicate preservation, and the durable
 * serialization. Every case here is a rule the scan is not allowed to bend - most of all the one that
 * says a scan disagreeing with the account's own Registered count is INCOMPLETE regardless of how
 * cleanly each individual entry read.
 */
@DisplayName("Veteran roster scan records")
class VeteranRosterScanEventTest {
    private fun observation(
        character: String? = "Taiki Shuttle",
        outfit: String? = "Wild Frontier",
        rank: String? = "A",
        rating: Int? = 10192,
        stats: List<Int?> = listOf(949, 699, 648, 687, 420),
        statGrades: List<String?> = listOf("A+", "B", "B", "B", "C"),
        aptitudes: List<String?> = listOf("A", "B", "A", "A", "E", "G", "C", "A", "E", "G"),
        favoriteState: String = "not_set",
        careerInfo: RosterCareerInfoObservation? = null,
    ) = RosterEntryObservation(character, outfit, rank, rating, stats, statGrades, aptitudes, favoriteState, careerInfo)

    private fun listState(
        used: Int? = 257,
        capacity: Int? = 260,
        filtersOff: Boolean? = true,
        sortKey: String? = "Rating",
        sortDirection: String? = "Asc",
    ) = RosterListState(used, capacity, filtersOff, sortKey, sortDirection)

    private fun assemble(
        observations: List<RosterEntryObservation>,
        list: RosterListState = listState(),
        termination: RosterScanTermination = RosterScanTermination.COUNT_REACHED,
        entryLimit: Int = 0,
    ) = assembleRosterScan(
        scanId = "scan-1",
        startedAt = 1_000L,
        completedAt = 2_000L,
        list = list,
        entryLimit = entryLimit,
        observations = observations.mapIndexed { i, o -> (1_000L + i) to o },
        termination = termination,
        appVersion = "2.5.9",
        screenWidth = 1080,
        screenHeight = 1920,
    )

    /** N distinct entries: the rating carries the difference so every fingerprint is unique. */
    private fun distinct(n: Int): List<RosterEntryObservation> = (0 until n).map { observation(rating = 10_000 + it) }

    @Nested
    @DisplayName("identity")
    inner class Identity {
        @Test
        fun `a fully read entry fingerprints identically to the shared roster evidence hash`() {
            val o = observation()
            val expected =
                rosterFingerprint(
                    RosterIdentityEvidence(
                        "Taiki Shuttle",
                        "Wild Frontier",
                        "A",
                        10192,
                        listOf(949, 699, 648, 687, 420),
                        listOf("A", "B", "A", "A", "E", "G", "C", "A", "E", "G"),
                    ),
                )
            assertEquals(expected, entryFingerprint(o))
        }

        @Test
        fun `any unread identity feeder leaves the entry unfingerprinted rather than guessed`() {
            assertNull(entryFingerprint(observation(character = null)))
            assertNull(entryFingerprint(observation(rating = null)))
            assertNull(entryFingerprint(observation(stats = listOf(949, null, 648, 687, 420))))
            assertNull(entryFingerprint(observation(aptitudes = List(10) { if (it == 3) null else "A" })))
        }

        @Test
        fun `an unread stat grade does not block identity but is named as unresolved`() {
            val entry = assemble(listOf(observation(statGrades = listOf("A+", null, "B", "B", "C"))), list = listState(used = 1)).entries.single()
            assertNotNull(entry.rosterFingerprint)
            assertEquals(listOf("statGrade_sta"), entry.unresolvedFields)
            assertTrue(entry.readCompleteness > 0.9 && entry.readCompleteness < 1.0)
        }

        @Test
        fun `unresolved field names use the corpus stat keys and the aptitude role names`() {
            val entry =
                assemble(
                    listOf(observation(stats = listOf(null, 699, 648, 687, 420), aptitudes = List(10) { if (it == 0) null else "A" })),
                    list = listState(used = 1),
                ).entries.single()
            assertTrue(entry.unresolvedFields.contains("stat_${STAT_KEYS[0]}"), entry.unresolvedFields.toString())
            assertTrue(entry.unresolvedFields.contains("aptitude_${APTITUDE_ROLES[0]}"), entry.unresolvedFields.toString())
        }
    }

    @Nested
    @DisplayName("completeness against the account's own Registered count")
    inner class Completeness {
        @Test
        fun `257 displayed and 257 read is trusted`() {
            val scan = assemble(distinct(257), list = listState(used = 257)).header
            assertEquals(RosterScanCompleteness.TRUSTED_COMPLETE, scan.completeness)
            assertTrue(scan.enumerationComplete)
            assertTrue(scan.identityComplete)
            assertTrue(scan.trustedForRetention)
            assertEquals(0, scan.countDiscrepancy)
        }

        @Test
        fun `a count-complete walk with unidentified entries is enumeration-complete but not identity-complete`() {
            // The whole point of the split: the walk visited all 257 positions and ended at the count,
            // but some entries could not be fingerprinted. That must read as "covered the roster,
            // identity incomplete" - not be lumped in with a walk that actually missed entries.
            val rows = distinct(255) + observation(character = null, rating = 90_001) + observation(outfit = null, rating = 90_002)
            val scan = assemble(rows, list = listState(used = 257)).header
            assertEquals(257, scan.entriesEnumerated)
            assertEquals(0, scan.countDiscrepancy)
            assertEquals(2, scan.unidentifiedCount)
            assertTrue(scan.enumerationComplete, "the walk covered exactly the account's own count")
            assertFalse(scan.identityComplete, "two entries did not fingerprint")
            assertFalse(scan.trustedForRetention)
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
        }

        @Test
        fun `257 displayed and 256 read is incomplete`() {
            val scan = assemble(distinct(256), list = listState(used = 257), termination = RosterScanTermination.CHEVRON_END).header
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
            assertEquals(-1, scan.countDiscrepancy)
        }

        @Test
        fun `257 displayed and 258 read is incomplete`() {
            val scan = assemble(distinct(258), list = listState(used = 257)).header
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
            assertEquals(1, scan.countDiscrepancy)
        }

        @Test
        fun `duplicates never hide a count mismatch`() {
            // 257 rows but only 256 distinct Veterans: the count matches, the identity set does not.
            val rows = distinct(256) + observation(rating = 10_000)
            val scan = assemble(rows, list = listState(used = 257)).header
            assertEquals(257, scan.entriesEnumerated)
            assertEquals(0, scan.countDiscrepancy)
            assertEquals(256, scan.uniqueFingerprints)
            assertEquals(1, scan.duplicateFingerprintCount)
            assertTrue(scan.enumerationComplete, "count matched, so the roster was covered")
            assertFalse(scan.identityComplete, "a repeated fingerprint leaves the identity set short")
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
        }

        @Test
        fun `an unconfirmed filters state is fail-closed even when every count agrees`() {
            for (filters in listOf(null, false)) {
                val scan = assemble(distinct(3), list = listState(used = 3, filtersOff = filters)).header
                assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness, "filtersOff=$filters")
            }
        }

        @Test
        fun `an unread Registered count can never be trusted`() {
            val scan = assemble(distinct(3), list = listState(used = null, capacity = null)).header
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
            assertNull(scan.countDiscrepancy)
        }

        @Test
        fun `an unidentified entry keeps the scan incomplete`() {
            val rows = distinct(2) + observation(character = null, rating = 12_345)
            val scan = assemble(rows, list = listState(used = 3)).header
            assertEquals(1, scan.unidentifiedCount)
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
        }

        @Test
        fun `only a termination consistent with reaching the end can be trusted`() {
            val endLike = setOf(RosterScanTermination.COUNT_REACHED, RosterScanTermination.CHEVRON_END)
            for (reason in RosterScanTermination.entries) {
                val scan = assemble(distinct(4), list = listState(used = 4), termination = reason).header
                val expected = if (reason in endLike) RosterScanCompleteness.TRUSTED_COMPLETE else RosterScanCompleteness.INCOMPLETE
                assertEquals(expected, scan.completeness, "termination=$reason")
            }
        }

        @Test
        fun `a bounded development run reports incomplete by construction`() {
            val scan = assemble(distinct(5), list = listState(used = 257), termination = RosterScanTermination.ENTRY_LIMIT_REACHED, entryLimit = 5).header
            assertEquals(5, scan.entriesEnumerated)
            assertEquals(5, scan.entryLimit)
            assertEquals(-252, scan.countDiscrepancy)
            // The five entries all identified cleanly, so identity is complete; enumeration is not,
            // because the walk stopped 252 short of the account's own count. The split names both.
            assertFalse(scan.enumerationComplete)
            assertTrue(scan.identityComplete)
            assertFalse(scan.trustedForRetention)
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
        }

        @Test
        fun `a zero-entry precondition failure assembles without pretending anything was read`() {
            val scan =
                assemble(
                    emptyList(),
                    list = RosterListState(null, null, null, null, null),
                    termination = RosterScanTermination.PRECONDITION_FAILED,
                ).header
            assertEquals(0, scan.entriesEnumerated)
            assertEquals(0, scan.uniqueFingerprints)
            assertEquals(RosterScanCompleteness.INCOMPLETE, scan.completeness)
        }
    }

    @Nested
    @DisplayName("duplicate handling")
    inner class Duplicates {
        @Test
        fun `two identical entries stay two entries, each marked with its multiplicity`() {
            val assembled = assemble(listOf(observation(), observation()), list = listState(used = 2))
            assertEquals(2, assembled.entries.size)
            assertEquals(listOf(0, 1), assembled.entries.map { it.scanIndex })
            assertEquals(assembled.entries[0].rosterFingerprint, assembled.entries[1].rosterFingerprint)
            assertTrue(assembled.entries.all { it.identityMultiplicity == 2 })
        }

        @Test
        fun `a distinct neighbour of a duplicate pair keeps multiplicity one`() {
            val assembled = assemble(listOf(observation(), observation(), observation(rating = 11_111)), list = listState(used = 3))
            assertEquals(listOf(2, 2, 1), assembled.entries.map { it.identityMultiplicity })
        }

        @Test
        fun `unidentified entries are not treated as duplicates of each other`() {
            val assembled = assemble(listOf(observation(character = null), observation(outfit = null)), list = listState(used = 2))
            assertTrue(assembled.entries.all { it.rosterFingerprint == null && it.identityMultiplicity == 1 })
            assertEquals(0, assembled.header.duplicateFingerprintCount)
            assertEquals(2, assembled.header.unidentifiedCount)
        }
    }

    @Nested
    @DisplayName("serialization")
    inner class Serialization {
        @Test
        fun `the header record carries the counts and the verdict`() {
            val scan = assemble(distinct(5), list = listState(used = 257), termination = RosterScanTermination.ENTRY_LIMIT_REACHED, entryLimit = 5).header
            val json = serializeRosterScanHeader(scan)
            assertEquals("roster_scan", json.getString("type"))
            assertEquals(ROSTER_SCAN_SCHEMA_VERSION, json.getInt("schemaVersion"))
            assertEquals("scan-1", json.getString("scanId"))
            assertEquals(257, json.getInt("displayedRegisteredUsed"))
            assertEquals(260, json.getInt("displayedRegisteredCapacity"))
            assertTrue(json.getBoolean("filtersOff"))
            assertEquals("Rating", json.getString("sortKey"))
            assertEquals("Asc", json.getString("sortDirection"))
            assertEquals(5, json.getInt("entriesEnumerated"))
            assertEquals(-252, json.getInt("countDiscrepancy"))
            assertEquals("entry_limit_reached", json.getString("terminationReason"))
            assertFalse(json.getBoolean("enumerationComplete"))
            assertTrue(json.getBoolean("identityComplete"))
            assertFalse(json.getBoolean("trustedForRetention"))
            assertEquals("incomplete", json.getString("completeness"))
            assertEquals(1080, json.getInt("screenWidth"))
        }

        @Test
        fun `an unread list field is omitted rather than serialized as a zero`() {
            val scan = assemble(emptyList(), list = RosterListState(null, null, null, null, null), termination = RosterScanTermination.PRECONDITION_FAILED).header
            val json = serializeRosterScanHeader(scan)
            assertFalse(json.has("displayedRegisteredUsed"))
            assertFalse(json.has("filtersOff"))
            assertFalse(json.has("countDiscrepancy"))
        }

        @Test
        fun `the entry record carries the observation keyed by the corpus stat and aptitude names`() {
            val entry = assemble(listOf(observation()), list = listState(used = 1)).entries.single()
            val json = serializeRosterScanEntry("scan-1", entry)
            assertEquals("roster_entry", json.getString("type"))
            assertEquals("scan-1", json.getString("scanId"))
            assertEquals(0, json.getInt("scanIndex"))
            assertEquals("Taiki Shuttle", json.getString("character"))
            assertEquals("Wild Frontier", json.getString("outfit"))
            assertEquals(949, json.getJSONObject("stats").getInt("spd"))
            assertEquals(420, json.getJSONObject("stats").getInt("wit"))
            assertEquals("A+", json.getJSONObject("statGrades").getString("spd"))
            assertEquals("A", json.getJSONObject("aptitudes").getString("turf"))
            assertEquals("G", json.getJSONObject("aptitudes").getString("end"))
            assertEquals("not_set", json.getString("favoriteState"))
            assertEquals(1.0, json.getDouble("readCompleteness"))
            assertEquals(0, json.getJSONArray("unresolvedFields").length())
            assertEquals(entry.rosterFingerprint, json.getString("rosterFingerprint"))
        }

        @Test
        fun `protection stays unknown even when the favorite glyph read cleanly`() {
            val json = serializeRosterScanEntry("scan-1", assemble(listOf(observation()), list = listState(used = 1)).entries.single())
            assertEquals("not_set", json.getString("favoriteState"))
            assertEquals("unknown", json.getString("protectionState"))
        }

        @Test
        fun `the Career Info block is absent when that pass did not run`() {
            val json = serializeRosterScanEntry("scan-1", assemble(listOf(observation()), list = listState(used = 1)).entries.single())
            assertFalse(json.has("careerInfo"))
        }

        @Test
        fun `a Career Info pass serializes its fields and counts toward completeness`() {
            val career = RosterCareerInfoObservation(18, 13, 191730, "The Beginning: URA Finale", 10192, "2026-08-10")
            val entry = assemble(listOf(observation(careerInfo = career)), list = listState(used = 1)).entries.single()
            val json = serializeRosterScanEntry("scan-1", entry)
            val block = json.getJSONObject("careerInfo")
            assertEquals(18, block.getInt("races"))
            assertEquals(191730, block.getInt("fans"))
            assertEquals("The Beginning: URA Finale", block.getString("scenario"))
            assertEquals("2026-08-10", block.getString("dateAcquired"))
            assertEquals(1.0, entry.readCompleteness)
        }

        @Test
        fun `an unread Career Info field is named and lowers completeness without dropping the block`() {
            val career = RosterCareerInfoObservation(18, 13, null, "The Beginning: URA Finale", 10192, null)
            val entry = assemble(listOf(observation(careerInfo = career)), list = listState(used = 1)).entries.single()
            assertTrue(entry.unresolvedFields.containsAll(listOf("careerFans", "careerDateAcquired")))
            assertTrue(entry.readCompleteness < 1.0)
            assertTrue(serializeRosterScanEntry("scan-1", entry).getJSONObject("careerInfo").has("races"))
        }

        @Test
        fun `an unfingerprinted entry omits the fingerprint rather than emitting an empty one`() {
            val entry = assemble(listOf(observation(character = null)), list = listState(used = 1)).entries.single()
            assertFalse(serializeRosterScanEntry("scan-1", entry).has("rosterFingerprint"))
        }
    }
}
