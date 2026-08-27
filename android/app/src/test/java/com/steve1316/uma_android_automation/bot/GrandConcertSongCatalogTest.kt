package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The static song catalog and the technique classifier. The matcher tests mirror the OCR failure
 * modes the live captures actually produced: truncated card titles, punctuation drift, and the
 * "Run n' Run!" vs "RUNxRUN" spelling split between the live client and the research reports.
 */
@DisplayName("Grand Concert song catalog")
class GrandConcertSongCatalogTest {
    private fun v(da: Int, pa: Int, vo: Int, vi: Int, co: Int) = PerformancePointVector.of(da, pa, vo, vi, co)

    @Nested
    @DisplayName("title matching")
    inner class TitleMatching {
        @Test
        fun `an exact title matches`() {
            assertEquals("Run for Our Dream!", GrandConcertSongCatalog.match("Run For Our Dream")?.title)
        }

        @Test
        fun `punctuation and case drift folds away`() {
            assertEquals("Believe in Miracles!", GrandConcertSongCatalog.match("believe in miracles!")?.title)
            assertEquals("Full Speed Ahead! Umadol Power", GrandConcertSongCatalog.match("Full Speed Ahead! Umadol Power☆")?.title)
        }

        @Test
        fun `the live-observed Global renames resolve, and the reports' spellings still alias to them`() {
            assertEquals("Hey, Guess What!", GrandConcertSongCatalog.match("Hey, Guess What!")?.title)
            assertEquals("Hey, Guess What!", GrandConcertSongCatalog.match("A・NO・NE")?.title)
            assertEquals("Grow Up and Shine!", GrandConcertSongCatalog.match("Grow Up and Shine!")?.title)
            assertEquals("Grow Up and Shine!", GrandConcertSongCatalog.match("Grow Up, Shine!")?.title)
            assertEquals("Getaway! Fallin' Love", GrandConcertSongCatalog.match("Getaway! Fallin' Love")?.title)
            assertEquals("Getaway! Fallin' Love", GrandConcertSongCatalog.match("Run Away! Fallin' Love")?.title)
        }

        @Test
        fun `a truncated card title matches by prefix - the observed failure mode`() {
            assertEquals("Zero Is Where the Center Stands!", GrandConcertSongCatalog.match("Zero is Where the Cent")?.title)
        }

        @Test
        fun `the live client spelling and the report spelling both resolve`() {
            assertEquals("Run n' Run!", GrandConcertSongCatalog.match("Run n' Run!")?.title)
            assertEquals("Run n' Run!", GrandConcertSongCatalog.match("RUNxRUN")?.title)
        }

        @Test
        fun `a one-character OCR slip still matches`() {
            assertEquals("Run for Our Dream!", GrandConcertSongCatalog.match("Run Fer Our Dream")?.title)
        }

        @Test
        fun `junk never matches`() {
            assertNull(GrandConcertSongCatalog.match(null))
            assertNull(GrandConcertSongCatalog.match(""))
            assertNull(GrandConcertSongCatalog.match("Da"))
            assertNull(GrandConcertSongCatalog.match("Completely Unknown Song"))
        }

        @Test
        fun `the free fixed songs are in the catalog`() {
            val makeDebut = GrandConcertSongCatalog.match("Make Debut!")
            val glu = GrandConcertSongCatalog.match("GIRLS' LEGEND U")
            assertTrue(makeDebut?.free == true && makeDebut.alwaysBuy)
            assertTrue(glu?.free == true && glu.alwaysBuy)
        }
    }

    @Nested
    @DisplayName("catalog integrity")
    inner class Integrity {
        @Test
        fun `23 songs, 2 free, 21 purchasable - the 17-of-21 purchase plan's universe`() {
            assertEquals(23, GrandConcertSongCatalog.songs.size)
            assertEquals(2, GrandConcertSongCatalog.songs.count { it.free })
            assertEquals(21, GrandConcertSongCatalog.songs.count { !it.free })
        }

        @Test
        fun `the research's buy-on-sight songs carry the flag`() {
            val alwaysBuy = GrandConcertSongCatalog.songs.filter { it.alwaysBuy }.map { it.title }.toSet()
            assertEquals(setOf("Make Debut!", "Run for Our Dream!", "Grow Up and Shine!", "GIRLS' LEGEND U"), alwaysBuy)
        }

        @Test
        fun `every folded title and alias is unique, so the matcher can never be ambiguous at fold level`() {
            fun fold(s: String) = s.lowercase().replace('0', 'o').replace('1', 'i').replace('l', 'i').filter { it.isLetterOrDigit() }
            val keys = GrandConcertSongCatalog.songs.flatMap { s -> listOf(fold(s.title)) + s.aliases.map { fold(it) } }
            assertEquals(keys.size, keys.toSet().size, keys.toString())
        }

        @Test
        fun `phases stay within the five concert cycles`() {
            assertTrue(GrandConcertSongCatalog.songs.all { it.phase in 1..5 })
        }

        @Test
        fun `the three former source conflicts now carry the client's own values`() {
            // These were flagged as "sources disagree" until master.mdb settled them on 2026-07-25.
            // Guides variously showed Sunbeam Cheer as Wit +3 and disputed the other two cost
            // vectors; the shipped client says otherwise, so the flags are gone and these are the
            // values to defend.
            assertEquals(MasteryBonusType.WIT_TRAINING_2, GrandConcertSongCatalog.match("Sunbeam Cheer")?.mastery)
            assertEquals(PerformancePointVector.of(0, 42, 0, 0, 21), GrandConcertSongCatalog.match("Sunbeam Cheer")?.cost)
            assertEquals(PerformancePointVector.of(0, 0, 21, 0, 42), GrandConcertSongCatalog.match("Seven Colors Scenery")?.cost)
            assertEquals(PerformancePointVector.of(0, 22, 0, 0, 22), GrandConcertSongCatalog.match("Dream Sky")?.cost)
        }

        @Test
        fun `the client's exact song titles all match themselves`() {
            // Regression guard for the class of bug that hid Precious Treasure Box: a catalog entry
            // under a JP romanization folds too far from the shipped title to ever match a live
            // read. Every title below is the string master.mdb ships.
            val shipped =
                listOf(
                    "Make Debut!", "Believe in Miracles!", "Zero Is Where the Center Stands!", "Getaway! Fallin' Love",
                    "Go This Way", "Ring Ring Diary", "Here Comes Our Time", "Run n' Run!", "Full Speed Ahead! Umadol Power",
                    "Run for Our Dream!", "Hey, Guess What!", "Our Blue Bird Days", "Grow Up and Shine!", "Sunbeam Cheer",
                    "Hoppity Sunny Days", "Seven Colors Scenery", "Dream Sky", "Present March", "Precious Treasure Box",
                    "The World's at Our Whim", "Sky-Blue Spring", "Fanfare for the Future!",
                )
            for (title in shipped) assertEquals(title, GrandConcertSongCatalog.match(title)?.title, "no self-match for \"$title\"")
        }
    }

    @Nested
    @DisplayName("technique classification")
    inner class Techniques {
        private fun parse(text: String?, cost: PerformancePointVector = v(0, 10, 0, 0, 0)) =
            GrandConcertSongCatalog.parseTechniqueEffect(text, cost)

        @Test
        fun `the launch-night fixtures classify correctly`() {
            val stamina = parse("Stamina +5")
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SINGLE_STAT, stamina.kind)
            assertEquals(false, stamina.coreStat)
            assertEquals(5, stamina.magnitude)

            val wit = parse("Wit +5")
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SINGLE_STAT, wit.kind)
            assertEquals(true, wit.coreStat)

            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SKILL_HINT, parse("Skill Hint Lvl +1 (Front Runner)").kind)
        }

        @Test
        fun `energy is identified by effect, not by any technique name`() {
            val energy = parse("Energy +30")
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.ENERGY, energy.kind)
            assertEquals(30, energy.magnitude)
            assertFalse(energy.viaCostSignature)
        }

        @Test
        fun `skill point and combined effects split correctly`() {
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SKILL_POINTS, parse("Skill Points +8").kind)
            val combo = parse("Power +4, Skill Points +4")
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.STAT_PLUS_SKILL_POINTS, combo.kind)
            assertEquals(true, combo.coreStat)
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.TWO_STATS, parse("Speed +4, Stamina +4").kind)
        }

        @Test
        fun `a single-type stat-tier cost identifies the stat deterministically`() {
            // Cost type = the granting stat's primary token; tiers 10/16/24 grant +5/+8/+12.
            // The live proof: "Vocal Training Advanced Class" (Vo 24) grants Power +12.
            val power = parse(null, v(0, 0, 24, 0, 0))
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SINGLE_STAT, power.kind)
            assertEquals(12, power.magnitude)
            assertEquals(true, power.coreStat)
            assertTrue(power.viaCostSignature)

            val stamina = parse(null, v(0, 16, 0, 0, 0))
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SINGLE_STAT, stamina.kind)
            assertEquals(8, stamina.magnitude)
            assertEquals(false, stamina.coreStat)

            val speed = parse(null, v(10, 0, 0, 0, 0))
            assertEquals(5, speed.magnitude)
            assertEquals(true, speed.coreStat)
        }

        @Test
        fun `hint and energy costs are never inferred from cost alone`() {
            // Group Lesson Advanced proved hints reach a single-type 30, the amount once assumed
            // uniquely Energy II - so 15/25/30/35 all stay UNKNOWN without text or title.
            for (amount in listOf(15, 25, 30, 35)) {
                assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.UNKNOWN, parse(null, v(0, 0, 0, 0, amount)).kind, "amount $amount")
            }
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.UNKNOWN, parse(null, v(10, 10, 0, 0, 0)).kind, "two-type cost")
        }

        @Test
        fun `the career-end technique titles resolve`() {
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SKILL_POINTS, GrandConcertSongCatalog.matchTechniqueTitle("Watch a Top-Tier Idol's Concert")?.kind)
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SKILL_HINT, GrandConcertSongCatalog.matchTechniqueTitle("Group Lesson Advanced")?.kind)
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.ENERGY, GrandConcertSongCatalog.matchTechniqueTitle("Relaxing Body Massage")?.kind)
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.TWO_STATS, GrandConcertSongCatalog.matchTechniqueTitle("Mic Performance Intermediate Class")?.kind)
            val vocal = GrandConcertSongCatalog.matchTechniqueTitle("Vocal Training Advanced Class")
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SINGLE_STAT, vocal?.kind)
            assertEquals(12, vocal?.magnitude)
        }

        @Test
        fun `a garbled effect line falls back to the observed title catalog - the first live run's failure`() {
            // Exactly what the 2026-07-24 Taiki run logged: garbage effect text, clean titles.
            val sp = GrandConcertSongCatalog.parseTechnique("Watch an Up-and-Coming ldol's Concer", "~w»RIB T W T", v(10, 0, 0, 0, 0))
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SKILL_POINTS, sp.kind)
            assertEquals(5, sp.magnitude)

            val energy = GrandConcertSongCatalog.parseTechnique("Facial-SIimming Massage", null, v(0, 25, 0, 0, 0))
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.ENERGY, energy.kind)
            assertEquals(20, energy.magnitude)

            val hint = GrandConcertSongCatalog.parseTechnique("Group Lesson Basics", "~PIIEE FRINIR VS T 1", v(0, 0, 15, 0, 0))
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SKILL_HINT, hint.kind)
        }

        @Test
        fun `readable effect text stays authoritative over the title`() {
            // If the client ever re-tiers a technique behind an old name, the on-card text wins.
            val read = GrandConcertSongCatalog.parseTechnique("Watch an Up-and-Coming Idol's Concert", "Skill Pts +8", v(0, 16, 0, 0, 0))
            assertEquals(GrandConcertSongCatalog.TechniqueEffectKind.SKILL_POINTS, read.kind)
            assertEquals(8, read.magnitude)
        }

        @Test
        fun `an unknown technique title with garbled text and a non-tier cost stays UNKNOWN`() {
            assertEquals(
                GrandConcertSongCatalog.TechniqueEffectKind.UNKNOWN,
                GrandConcertSongCatalog.parseTechnique("Some Future Technique", "###", v(0, 25, 0, 0, 0)).kind,
            )
        }

        @Test
        fun `every single-stat title agrees with the stat its own cost family actually pays`() {
            // Each family's title entry and the cost-signature fallback are two independent
            // sources for the same fact (TECHNIQUE_TITLES vs COST_TYPE_STAT); this test just
            // cross-checks them instead of hardcoding a stat and comparing it to itself, which
            // would have missed the Audience Involvement / Makeup swap the same way the code did.
            val familyCost =
                mapOf(
                    "Dance Step" to v(10, 0, 0, 0, 0),
                    "Audience Involvement" to v(0, 10, 0, 0, 0),
                    "Vocal Training" to v(0, 0, 10, 0, 0),
                    "Makeup" to v(0, 0, 0, 10, 0),
                    "Composure Training" to v(0, 0, 0, 0, 10),
                )
            for ((family, cost) in familyCost) {
                val costFamilyStat = GrandConcertSongCatalog.parseTechniqueEffect(null, cost).stat
                for (tier in listOf("Basics", "Intermediate Class", "Advanced Class")) {
                    val title = "$family $tier"
                    assertEquals(costFamilyStat, GrandConcertSongCatalog.matchTechniqueTitle(title)?.stat, title)
                }
            }
        }

        @Test
        fun `Audience Involvement grants Stamina and Makeup grants Guts, per the live mastery text`() {
            // learn_confirm_technique.png and technique_list.png show "Audience Involvement
            // Basics" mastering "Stamina +5"; GrandConcertPolicyTest's Makeup fixture is
            // "Guts +5". Both tiers above Basics follow the same family.
            for (tier in listOf("Basics", "Intermediate Class", "Advanced Class")) {
                assertEquals(StatName.STAMINA, GrandConcertSongCatalog.matchTechniqueTitle("Audience Involvement $tier")?.stat, tier)
                assertEquals(StatName.GUTS, GrandConcertSongCatalog.matchTechniqueTitle("Makeup $tier")?.stat, tier)
            }
            assertEquals(StatName.SPEED, GrandConcertSongCatalog.matchTechniqueTitle("Dance Step Basics")?.stat)
            assertEquals(StatName.POWER, GrandConcertSongCatalog.matchTechniqueTitle("Vocal Training Basics")?.stat)
            assertEquals(StatName.WIT, GrandConcertSongCatalog.matchTechniqueTitle("Composure Training Basics")?.stat)
        }
    }
}
