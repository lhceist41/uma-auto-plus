package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.finalVeteranFilterReadings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VeteranFilterBaselineTest {
    private fun rgb(r: Int, g: Int, b: Int): Int = (255 shl 24) or (r shl 16) or (g shl 8) or b
    private val white = rgb(241, 241, 241)
    private val rule = rgb(70, 190, 0)
    private val bands = mutableMapOf<String, FixturePng>()

    // Real Display Settings tab strips (see src/test/resources/fixtures/displaysettings/PROVENANCE.md).
    private fun band(name: String): FixturePng =
        bands.getOrPut(name) {
            val path = "/fixtures/displaysettings/$name.png"
            requireNotNull(javaClass.getResourceAsStream(path)) { "missing fixture $path" }.use { FixturePng.read(it) }
        }

    private fun aggregateDimension(
        dimension: VeteranFilterDimension,
        vararg observations: VeteranFilterDimensionRead,
    ): VeteranFilterViewportAggregation =
        VeteranFilterViewportAggregation().also { aggregation ->
            observations.forEachIndexed { index, observation ->
                aggregation.add(
                    VeteranFilterFrameRead(
                        anchors = emptyList(),
                        dimensions = mapOf(dimension to observation),
                        errors = listOf("viewport $index diagnostic"),
                    ),
                )
            }
        }

    private fun finalReadings(vararg viewports: VeteranFilterFrameRead): Map<VeteranFilterDimension, VeteranFilterDimensionRead> {
        val aggregation = VeteranFilterViewportAggregation()
        viewports.forEach(aggregation::add)
        return finalVeteranFilterReadings(aggregation, viewports.last())
    }

    private inner class Frame(private val dimension: VeteranFilterDimension, private val active: Boolean, private val unreadable: Boolean) {
        val pixels = mutableMapOf<Pair<Int, Int>, Int>()
        val labels = mutableMapOf<Pair<Int, Int>, String>()
        var wrongTab = false
        var wrongTitle = false
        var width = 1080
        var tabBand: FixturePng? = band("filter_active")
        val chrome = mutableMapOf<Pair<Int, Int>, String>()

        private val y: Int = when (dimension) {
            VeteranFilterDimension.TRACK -> 332
            VeteranFilterDimension.DISTANCE -> 543
            VeteranFilterDimension.STYLE -> 868
            VeteranFilterDimension.ATTRIBUTE_SPARKS -> 684
            VeteranFilterDimension.APTITUDE_SPARKS -> 646
            VeteranFilterDimension.UNIQUE_SPARKS -> 390
            VeteranFilterDimension.COMMON_SPARKS -> 381
            VeteranFilterDimension.FAVORITES -> 631
            VeteranFilterDimension.MEMO -> 1412
        }
        private val nextY: Int = when (dimension) {
            VeteranFilterDimension.TRACK -> 543
            VeteranFilterDimension.DISTANCE -> 868
            VeteranFilterDimension.STYLE -> 1193
            VeteranFilterDimension.ATTRIBUTE_SPARKS -> 1305
            VeteranFilterDimension.APTITUDE_SPARKS -> 1494
            VeteranFilterDimension.UNIQUE_SPARKS -> 895
            VeteranFilterDimension.COMMON_SPARKS -> if (active) 886 else 631
            VeteranFilterDimension.FAVORITES -> 1412
            VeteranFilterDimension.MEMO -> 0
        }
        private val nextName: String = when (dimension) {
            VeteranFilterDimension.TRACK -> "Distance"
            VeteranFilterDimension.DISTANCE -> "Style"
            VeteranFilterDimension.STYLE -> "Attribute Sparks"
            VeteranFilterDimension.ATTRIBUTE_SPARKS -> "Aptitude Sparks"
            VeteranFilterDimension.APTITUDE_SPARKS -> "Unique Sparks"
            VeteranFilterDimension.UNIQUE_SPARKS -> "Common Sparks"
            VeteranFilterDimension.COMMON_SPARKS -> if (active) "Common Spark 2" else "Favorites"
            VeteranFilterDimension.FAVORITES -> "Memo"
            VeteranFilterDimension.MEMO -> ""
        }
        private val name: String = when (dimension) {
            VeteranFilterDimension.TRACK -> "Track"
            VeteranFilterDimension.DISTANCE -> "Distance"
            VeteranFilterDimension.STYLE -> "Style"
            VeteranFilterDimension.ATTRIBUTE_SPARKS -> "Attribute Sparks"
            VeteranFilterDimension.APTITUDE_SPARKS -> "Aptitude Sparks"
            VeteranFilterDimension.UNIQUE_SPARKS -> "Unique Sparks"
            VeteranFilterDimension.COMMON_SPARKS -> "Common Sparks"
            VeteranFilterDimension.FAVORITES -> "Favorites"
            VeteranFilterDimension.MEMO -> "Memo"
        }

        init {
            line(y, name)
            if (nextY > 0) line(nextY, nextName)
            when (dimension) {
                VeteranFilterDimension.TRACK -> ordinary(listOf("Turf", "Dirt"))
                VeteranFilterDimension.DISTANCE -> ordinary(listOf("Sprint", "Mile", "Medium", "Long"))
                VeteranFilterDimension.STYLE -> ordinary(listOf("Front", "Pace", "Late", "End"))
                VeteranFilterDimension.ATTRIBUTE_SPARKS -> {
                    ordinary(listOf("Speed", "Stamina", "Power", "Guts", "Wit"), selectLast = false)
                    children(363, originActive = active, missingOrigin = unreadable)
                }
                VeteranFilterDimension.APTITUDE_SPARKS -> {
                    ordinary(listOf("Turf", "Dirt", "Sprint", "Mile", "Medium", "Long", "Front", "Pace", "Late", "End"), selectLast = false)
                    children(592, secondActive = active, missingSecond = unreadable)
                }
                VeteranFilterDimension.UNIQUE_SPARKS -> {
                    labels[160 to (y + 43)] = "All Umamusume"
                    labels[630 to (y + 80 - 35)] = "Reset"
                    labels[850 to (y + 80 - 35)] = "Select"
                    box(106, y + 80, false, !unreadable)
                    children(249, secondActive = active)
                }
                VeteranFilterDimension.COMMON_SPARKS -> {
                    if (active) {
                        labels[100 to (y + 45)] = "URA Finale"
                        labels[625 to (y + 80 - 35)] = "Reset"
                        labels[850 to (y + 80 - 35)] = "Select"
                        children(249)
                    } else if (!unreadable) {
                        labels[340 to (y + 55)] = "Add Spark Filter"
                        pixels[500 to (y + 45)] = rgb(222, 222, 222)
                        pixels[500 to (y + 172)] = rgb(223, 223, 223)
                        pixels[500 to (y + 80)] = rgb(255, 255, 255)
                    }
                }
                VeteranFilterDimension.FAVORITES -> {
                    for (index in 0 until 16) {
                        val x = listOf(106, 444, 782)[index % 3]
                        val cy = y + 80 + 114 * (index / 3)
                        labels[(if (index == 0) 160 else x + 100) to (cy - 35)] = if (index == 0) "Not Set" else "Favorites"
                        box(x, cy, active && index == 15, !(unreadable && index == 15))
                    }
                }
                VeteranFilterDimension.MEMO -> {
                    labels[161 to (y + 45)] = "Has Memo"
                    labels[499 to (y + 45)] = "No Memo"
                    box(106, y + 80, active)
                    box(444, y + 80, false, !unreadable)
                }
            }
        }

        private fun line(at: Int, label: String) {
            for (dy in 0..5) for (x in listOf(250, 600, 900)) pixels[x to (at + dy)] = rule
            labels[102 to (at - 58)] = label
        }

        fun moveNextAnchor(by: Int) {
            if (nextY == 0) return
            for (dy in 0..5) for (x in listOf(250, 600, 900)) pixels.remove(x to (nextY + dy))
            labels.remove(102 to (nextY - 58))
            line(nextY + by, nextName)
        }

        private fun box(cx: Int, cy: Int, selected: Boolean, visible: Boolean = true) {
            if (!visible) return
            for (dx in -30..30) {
                pixels[(cx + dx) to (cy - 35)] = rgb(207, 207, 207)
                pixels[(cx + dx) to (cy + 35)] = rgb(119, 119, 136)
            }
            for (dy in -30..30) {
                pixels[(cx - 35) to (cy + dy)] = rgb(161, 161, 178)
                pixels[(cx + 35) to (cy + dy)] = rgb(161, 161, 178)
            }
            for ((tx, ty) in listOf(-16 to 4, -7 to 13, 18 to -13)) for (dx in -4..4) for (dy in -4..4) {
                pixels[(cx + tx + dx) to (cy + ty + dy)] = if (selected) rgb(125, 205, 36) else rgb(200, 200, 204)
            }
        }

        private fun radio(cx: Int, cy: Int, selected: Boolean, visible: Boolean = true) {
            if (!visible) return
            for (d in listOf(-35, 35)) {
                pixels[(cx + d) to cy] = rgb(130, 130, 130)
                pixels[cx to (cy + d)] = rgb(130, 130, 130)
            }
            for (dx in -10..10) for (dy in -10..10) pixels[(cx + dx) to (cy + dy)] =
                if (selected) rgb(85, 130, 23) else rgb(150, 150, 152)
        }

        private fun ordinary(names: List<String>, selectLast: Boolean = true) {
            names.forEachIndexed { index, label ->
                val x = listOf(106, 444, 782)[index % 3]
                val cy = y + 80 + 114 * (index / 3)
                labels[(x + 55) to (cy - 35)] = label
                box(x, cy, active && selectLast && index == names.lastIndex, !(unreadable && index == names.lastIndex))
            }
        }

        private fun children(offset: Int, secondActive: Boolean = false, originActive: Boolean = false, missingSecond: Boolean = false, missingOrigin: Boolean = false) {
            val cy = y + offset
            labels[160 to (cy - 35)] = "All"
            labels[500 to (cy - 35)] = "or Above"
            labels[840 to (cy - 35)] = "Only"
            labels[160 to (cy + 114 - 35)] = "Include Sparks from Origin Legacies"
            radio(106, cy, true)
            radio(444, cy, secondActive, !missingSecond)
            radio(782, cy, false)
            box(106, cy + 114, originActive, !missingOrigin)
        }

        fun build(): VeteranFilterFrame = VeteranFilterFrame(width, 1920, SparkPixelSampler { x, py ->
            pixels[x to py] ?: tabBand?.takeIf { py < it.height }?.getRGB(x, py) ?: white
        }) { x, py, _, _ ->
            chrome[x to py] ?: when (x to py) {
                DISPLAY_SETTINGS_TITLE_X to DISPLAY_SETTINGS_TITLE_Y -> if (wrongTitle) "Spark Selection" else "Display Settings"
                155 to 170 -> "Sort"
                665 to 170 -> if (wrongTab) "Sort" else "Filter"
                750 to 1560 -> "Reset Filters"
                180 to 1730 -> "Cancel"
                705 to 1730 -> "OK"
                else -> labels[x to py] ?: ""
            }
        }
    }

    @Test
    fun `a real Filter-active tab strip is recognized although its label centre is a white glyph`() {
        assertEquals(rgb(249, 253, 243), band("filter_active").getRGB(DISPLAY_SETTINGS_FILTER_TAB_X, DISPLAY_SETTINGS_TAB_Y))
        assertTrue(filterDialogChromeRecognized(Frame(VeteranFilterDimension.TRACK, false, false).build()))
    }

    @Test
    fun `a real Sort-active tab strip is never the Filter tab`() {
        assertFalse(filterDialogChromeRecognized(Frame(VeteranFilterDimension.TRACK, false, false).apply { tabBand = band("sort_active") }.build()))
    }

    @Test
    fun `tab state fails closed when neither or both tabs are green or the dialog is absent`() {
        val neither = Frame(VeteranFilterDimension.TRACK, false, false).apply { tabBand = null }
        for (x in 0 until 1080) neither.pixels[x to DISPLAY_SETTINGS_TAB_Y] = rgb(245, 244, 247)
        val both = Frame(VeteranFilterDimension.TRACK, false, false)
        for (x in 0 until 540) both.pixels[x to DISPLAY_SETTINGS_TAB_Y] = rgb(136, 209, 8)
        val absent = Frame(VeteranFilterDimension.TRACK, false, false).apply { wrongTitle = true }
        for (frame in listOf(neither, both, absent)) assertFalse(filterDialogChromeRecognized(frame.build()))
    }

    private val reset = 750 to 1560
    private val cancel = 180 to 1730
    private val ok = 705 to 1730
    private val sortLabel = 155 to 170
    private val filterLabel = 665 to 170
    private val title = DISPLAY_SETTINGS_TITLE_X to DISPLAY_SETTINGS_TITLE_Y

    private fun chromeWith(region: Pair<Int, Int>, text: String): Boolean =
        filterDialogChromeRecognized(Frame(VeteranFilterDimension.TRACK, false, false).apply { chrome[region] = text }.build())

    @Test
    fun `small OCR misreads of the dialog chrome still recognize the Filter dialog`() {
        // "Reset Filtes" is a live ML Kit read of the Reset Filters button; the others are one- or two-edit variants.
        for ((region, text) in listOf(reset to "Reset Filtes", reset to "ResetFilters", cancel to "Canccl", sortLabel to "Sot", filterLabel to "Fiter", title to "Disp1ay Setings", ok to "0K")) {
            assertTrue(chromeWith(region, text), "'$text' must be tolerated")
        }
    }

    @Test
    fun `dialog chrome that is absent, different, or a near miss fails closed`() {
        for ((region, text) in listOf(
            reset to "", reset to "Cancel", reset to "Select", reset to "Reset Filt", reset to "Rest Flts",
            cancel to "OK", cancel to "Reset Filters", cancel to "Canc", cancel to "", ok to "Cancel", ok to "DK", ok to "",
            sortLabel to "Filter", filterLabel to "Sort", title to "Spark Selection", title to "",
        )) {
            assertFalse(chromeWith(region, text), "'$text' must not pass as the expected control")
        }
    }

    @Test
    fun `an option-label misread keeps the pixel reading while a different label stays UNKNOWN`() {
        fun attributes(read: String) = Frame(VeteranFilterDimension.ATTRIBUTE_SPARKS, false, false).apply {
            labels.replaceAll { _, text -> if (text == "Stamina") read else text }
        }.build()
        assertEquals(FilterDimensionState.NEUTRAL, readVeteranFilterFrame(attributes("Stamlna")).dimensions[VeteranFilterDimension.ATTRIBUTE_SPARKS]?.state)
        for (wrong in listOf("Power", "", "Stam")) {
            assertEquals(FilterDimensionState.UNKNOWN, readVeteranFilterFrame(attributes(wrong)).dimensions[VeteranFilterDimension.ATTRIBUTE_SPARKS]?.state, wrong)
        }
        val swappedMemo = Frame(VeteranFilterDimension.MEMO, false, false).apply {
            labels.replaceAll { _, text -> when (text) { "Has Memo" -> "No Memo"; "No Memo" -> "Has Memo"; else -> text } }
        }.build()
        assertEquals(FilterDimensionState.UNKNOWN, readVeteranFilterFrame(swappedMemo).dimensions[VeteranFilterDimension.MEMO]?.state)
    }

    @Test
    fun `short labels tolerate OCR glyph swaps`() {
        val aptitude = Frame(VeteranFilterDimension.APTITUDE_SPARKS, false, false).apply { labels.replaceAll { _, text -> if (text == "All") "AIl" else text } }.build()
        assertEquals(FilterDimensionState.NEUTRAL, readVeteranFilterFrame(aptitude).dimensions[VeteranFilterDimension.APTITUDE_SPARKS]?.state)
    }

    @Test
    fun `a misread All Umamusume keeps the pixel reading while a named Unique Spark stays active`() {
        fun unique(text: String) = readVeteranFilterFrame(Frame(VeteranFilterDimension.UNIQUE_SPARKS, false, false).apply { labels[160 to (390 + 43)] = text }.build())
            .dimensions[VeteranFilterDimension.UNIQUE_SPARKS]?.state
        assertEquals(FilterDimensionState.NEUTRAL, unique("All Umamusune"))
        assertEquals(FilterDimensionState.ACTIVE, unique("Special Week"))
    }

    @Test
    fun `a near-miss section heading is never attributed to a dimension`() {
        val read = readVeteranFilterFrame(Frame(VeteranFilterDimension.ATTRIBUTE_SPARKS, false, false).apply { labels[102 to (1305 - 58)] = "Aptltude Sparks" }.build())
        assertTrue(read.errors.any { it.startsWith("unrecognized filter section") }, read.errors.toString())
        assertTrue(VeteranFilterDimension.ATTRIBUTE_SPARKS !in read.dimensions)
    }

    @Test
    fun `the Add Spark Filter slot text confirms the empty Common row`() {
        fun common(text: String) = readVeteranFilterFrame(Frame(VeteranFilterDimension.COMMON_SPARKS, false, false).apply { labels[340 to (381 + 55)] = text }.build())
            .dimensions[VeteranFilterDimension.COMMON_SPARKS]?.state
        assertEquals(FilterDimensionState.NEUTRAL, common("Add Spark Fiter"))
        for (text in listOf("", "Select", "Add Spark")) assertEquals(FilterDimensionState.UNKNOWN, common(text), text)
    }

    @Test
    fun `each dimension reads neutral active and unknown from its actual section path`() {
        for (dimension in VeteranFilterDimension.entries) {
            val neutral = readVeteranFilterFrame(Frame(dimension, false, false).build())
            val active = readVeteranFilterFrame(Frame(dimension, true, false).build())
            val unreadable = readVeteranFilterFrame(Frame(dimension, false, true).build())
            assertEquals(FilterDimensionState.NEUTRAL, neutral.dimensions[dimension]?.state, "$dimension neutral: ${neutral.errors} ${neutral.dimensions}")
            assertEquals(FilterDimensionState.ACTIVE, active.dimensions[dimension]?.state, "$dimension active: ${active.errors} ${active.dimensions}")
            assertEquals(FilterDimensionState.UNKNOWN, unreadable.dimensions[dimension]?.state, "$dimension unreadable: ${unreadable.errors} ${unreadable.dimensions}")
        }
    }

    @Test
    fun `a selected child survives inactive parents in the dimension state`() {
        for (dimension in listOf(VeteranFilterDimension.APTITUDE_SPARKS, VeteranFilterDimension.UNIQUE_SPARKS)) {
            val result = readVeteranFilterFrame(Frame(dimension, true, false).build()).dimensions[dimension]
            assertEquals(FilterDimensionState.ACTIVE, result?.state)
            assertTrue(result!!.reasons.any { it.contains("grade two-star") })
        }
        val common = readVeteranFilterFrame(Frame(VeteranFilterDimension.COMMON_SPARKS, true, false).build())
        assertEquals(FilterDimensionState.ACTIVE, common.dimensions[VeteranFilterDimension.COMMON_SPARKS]?.state)
    }

    @Test
    fun `repeated dimension observations use active unknown neutral precedence in either order`() {
        val active = VeteranFilterDimensionRead(FilterDimensionState.ACTIVE, listOf("positive active evidence"))
        val unknown = VeteranFilterDimensionRead(FilterDimensionState.UNKNOWN, listOf("ambiguous clipped evidence"))
        val neutral = VeteranFilterDimensionRead(FilterDimensionState.NEUTRAL, listOf("neutral evidence"))
        val cases =
            listOf(
                Triple(active, unknown, FilterDimensionState.ACTIVE),
                Triple(unknown, active, FilterDimensionState.ACTIVE),
                Triple(active, neutral, FilterDimensionState.ACTIVE),
                Triple(neutral, active, FilterDimensionState.ACTIVE),
                Triple(unknown, neutral, FilterDimensionState.UNKNOWN),
                Triple(neutral, unknown, FilterDimensionState.UNKNOWN),
                Triple(active, active, FilterDimensionState.ACTIVE),
                Triple(unknown, unknown, FilterDimensionState.UNKNOWN),
                Triple(neutral, neutral, FilterDimensionState.NEUTRAL),
            )

        for ((first, second, expected) in cases) {
            val aggregation = aggregateDimension(VeteranFilterDimension.TRACK, first, second)
            val merged = aggregation.readings.getValue(VeteranFilterDimension.TRACK)
            assertEquals(expected, merged.state, "${first.state} + ${second.state}")
            assertEquals((first.reasons + second.reasons).toSet(), merged.reasons.toSet())
            assertEquals(listOf("viewport 0 diagnostic", "viewport 1 diagnostic"), aggregation.diagnostics)
        }
    }

    @Test
    fun `repeated dimension merge is stable across longer observation sequences`() {
        fun read(state: FilterDimensionState) = VeteranFilterDimensionRead(state, listOf(state.name.lowercase()))

        assertEquals(
            FilterDimensionState.ACTIVE,
            aggregateDimension(
                VeteranFilterDimension.TRACK,
                read(FilterDimensionState.NEUTRAL),
                read(FilterDimensionState.UNKNOWN),
                read(FilterDimensionState.ACTIVE),
            ).readings.getValue(VeteranFilterDimension.TRACK).state,
        )
        assertEquals(
            FilterDimensionState.UNKNOWN,
            aggregateDimension(
                VeteranFilterDimension.TRACK,
                read(FilterDimensionState.UNKNOWN),
                read(FilterDimensionState.NEUTRAL),
                read(FilterDimensionState.UNKNOWN),
            ).readings.getValue(VeteranFilterDimension.TRACK).state,
        )
        assertEquals(
            FilterDimensionState.NEUTRAL,
            aggregateDimension(
                VeteranFilterDimension.TRACK,
                read(FilterDimensionState.NEUTRAL),
                read(FilterDimensionState.NEUTRAL),
                read(FilterDimensionState.NEUTRAL),
            ).readings.getValue(VeteranFilterDimension.TRACK).state,
        )
    }

    @Test
    fun `active hidden Spark child survives an overlapping clipped observation with both reasons`() {
        val dimension = VeteranFilterDimension.APTITUDE_SPARKS
        val activeFrame = readVeteranFilterFrame(Frame(dimension, true, false).build())
        val clippedFrame = readVeteranFilterFrame(Frame(dimension, false, true).build())
        val active = activeFrame.dimensions.getValue(dimension)
        val clipped = clippedFrame.dimensions.getValue(dimension)
        assertEquals(FilterDimensionState.ACTIVE, active.state)
        assertEquals(FilterDimensionState.UNKNOWN, clipped.state)

        val merged = finalReadings(activeFrame, clippedFrame).getValue(dimension)
        assertEquals(FilterDimensionState.ACTIVE, merged.state)
        assertTrue(merged.reasons.any { it.contains("grade two-star active") })
        assertTrue(merged.reasons.any { it.contains("grade two-star unreadable") })
    }

    @Test
    fun `active hidden Attribute Spark origin survives an overlapping clipped observation`() {
        val dimension = VeteranFilterDimension.ATTRIBUTE_SPARKS
        val activeFrame = readVeteranFilterFrame(Frame(dimension, true, false).build())
        val clippedFrame = readVeteranFilterFrame(Frame(dimension, false, true).build())

        val merged = finalReadings(activeFrame, clippedFrame).getValue(dimension)
        assertEquals(FilterDimensionState.ACTIVE, merged.state)
        assertTrue(merged.reasons.any { it.contains("origin active") })
        assertTrue(merged.reasons.any { it.contains("origin unreadable") })
    }

    @Test
    fun `wrong tab chooser viewport and section span do not yield neutral`() {
        val wrongTab = Frame(VeteranFilterDimension.TRACK, false, false).apply { wrongTab = true }
        val chooser = Frame(VeteranFilterDimension.TRACK, false, false).apply { wrongTitle = true }
        val viewport = Frame(VeteranFilterDimension.TRACK, false, false).apply { width = 900 }
        val span = Frame(VeteranFilterDimension.TRACK, false, false).apply { moveNextAnchor(30) }
        assertTrue(readVeteranFilterFrame(wrongTab.build()).errors.isNotEmpty())
        assertTrue(readVeteranFilterFrame(chooser.build()).errors.isNotEmpty())
        assertTrue(readVeteranFilterFrame(viewport.build()).errors.isNotEmpty())
        assertFalse(readVeteranFilterFrame(span.build()).dimensions[VeteranFilterDimension.TRACK]?.state == FilterDimensionState.NEUTRAL)
    }
}
