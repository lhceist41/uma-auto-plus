package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Text labels drawn smaller than 1080x1920 are retried at 0.96, 0.92 and 0.88 after a miss at 1.0, same 0.8 bar. A label drawn at
 * full size must never trigger a retry; labels whose false hit could spend, choose or flip a screen decision never get one.
 * When the Start measurement finds smaller text, the allow-list gets the 0.98 to 0.90 ladder with a 0.85 bar instead.
 */
@DisplayName("Text scale fallback")
class TextScaleFallbackTest {
    private val confidence = 0.8
    private val header = "components/label/stat_table_header_skill_points"
    private val menu = "components/label/home_menu"

    @BeforeEach
    @AfterEach
    fun clearGate() = TextScaleFallback.resetGate()

    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "android/app/src/main/assets").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun fixture(name: String): FixturePng =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/statheader/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }

    private fun textScaleFixture(name: String): Gray =
        gray(requireNotNull(javaClass.getResourceAsStream("/fixtures/textscale/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) })

    private fun asset(path: String): Gray = gray(File(root(), "android/app/src/main/assets/images/$path.png").inputStream().use { FixturePng.read(it) })

    private val template: FixturePng =
        File(root(), "android/app/src/main/assets/images/$header.png").inputStream().use { FixturePng.read(it) }

    private class Gray(val width: Int, val height: Int, val v: DoubleArray)

    /** The matcher's grayscale: the library converts RGBA with BGR2GRAY, so red and blue weights are swapped. */
    private fun gray(img: FixturePng): Gray =
        Gray(
            img.width,
            img.height,
            DoubleArray(img.width * img.height) {
                val argb = img.getRGB(it % img.width, it / img.width)
                0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)
            },
        )

    /** Bilinear resize to the library's truncated size, like the template scaling in its match(). */
    private fun scaled(g: Gray, scale: Double): Gray {
        if (scale == 1.0) return g
        val w = (g.width * scale).toInt()
        val h = (g.height * scale).toInt()
        val out = DoubleArray(w * h)
        for (y in 0 until h) {
            val sy = ((y + 0.5) * g.height / h - 0.5).coerceIn(0.0, g.height - 1.0)
            val y0 = sy.toInt()
            val y1 = minOf(y0 + 1, g.height - 1)
            val fy = sy - y0
            for (x in 0 until w) {
                val sx = ((x + 0.5) * g.width / w - 0.5).coerceIn(0.0, g.width - 1.0)
                val x0 = sx.toInt()
                val x1 = minOf(x0 + 1, g.width - 1)
                val fx = sx - x0
                val top = g.v[y0 * g.width + x0] * (1 - fx) + g.v[y0 * g.width + x1] * fx
                val bottom = g.v[y1 * g.width + x0] * (1 - fx) + g.v[y1 * g.width + x1] * fx
                out[y * w + x] = top * (1 - fy) + bottom * fy
            }
        }
        return Gray(w, h, out)
    }

    /** Best TM_CCOEFF_NORMED score of [t] anywhere in [img]; 0.0 when [t] does not fit, like the measurement. */
    private fun bestScore(img: Gray, t: Gray): Double {
        if (t.width > img.width || t.height > img.height) return 0.0
        val tMean = t.v.average()
        val tc = DoubleArray(t.v.size) { t.v[it] - tMean }
        val tNorm = sqrt(tc.sumOf { it * it })
        var best = -1.0
        for (oy in 0..(img.height - t.height)) {
            for (ox in 0..(img.width - t.width)) {
                var sum = 0.0
                for (ty in 0 until t.height) for (tx in 0 until t.width) sum += img.v[(oy + ty) * img.width + ox + tx]
                val mean = sum / t.v.size
                var cross = 0.0
                var sq = 0.0
                for (ty in 0 until t.height) {
                    for (tx in 0 until t.width) {
                        val d = img.v[(oy + ty) * img.width + ox + tx] - mean
                        cross += d * tc[ty * t.width + tx]
                        sq += d * d
                    }
                }
                val score = if (sq == 0.0) 0.0 else cross / (sqrt(sq) * tNorm)
                if (score > best) best = score
            }
        }
        return best
    }

    /** The anchor's best score at every gate scale, as CustomImageUtils.textScaleScores measures it. */
    private fun gateScores(frame: Gray, path: String): Map<Double, Double> {
        val t = asset(path)
        return TextScaleFallback.GATE_SCALES.associateWith { bestScore(frame, scaled(t, it)) }
    }

    /** Sets the gate to false through the real measurement: an anchor found at 1.0 with nothing better. */
    private fun gateFalse() {
        TextScaleFallback.measureAtStart(confidence) { if (it == menu) mapOf(1.0 to 1.0, 0.98 to 0.86, 0.96 to 0.67, 0.94 to 0.5, 0.92 to 0.39, 0.9 to 0.38) else null }
        assertEquals(false, TextScaleFallback.textShrinks)
    }

    /** Sets the gate to true through the real measurement: the 480 DPI "Menu" scores. */
    private fun gateTrue() {
        TextScaleFallback.measureAtStart(confidence) { if (it == menu) mapOf(1.0 to 0.833, 0.98 to 0.962, 0.96 to 0.909, 0.94 to 0.754, 0.92 to 0.64, 0.9 to 0.45) else null }
        assertEquals(true, TextScaleFallback.textShrinks)
    }

    /** The override's flow on one frame: 1.0 as today, then the fallback with its bar; returns (scale, score) of the hit and every retry scale tried. */
    private fun lookUp(
        state: TextScaleFallback,
        frame: Gray,
        name: String = header,
        tmpl: Gray = gray(template),
    ): Pair<Pair<Double, Double>?, List<Double>> {
        val atOne = bestScore(frame, tmpl)
        if (atOne >= confidence) {
            state.matchedAtFullSize(name)
            return (1.0 to atOne) to emptyList()
        }
        val tried = mutableListOf<Double>()
        val hit =
            state.afterMiss(name) { scale, minConfidence ->
                tried.add(scale)
                bestScore(frame, scaled(tmpl, scale)).takeIf { it >= maxOf(confidence, minConfidence ?: 0.0) }
            }
        return hit?.let { it.scale to it.value } to tried
    }

    @Test
    @DisplayName("a frame drawn at 1080x1920 size matches at 1.0, and later misses of that label get no retry")
    fun fullSizeNeedsNoRetry() {
        val state = TextScaleFallback()
        val (hit, tried) = lookUp(state, gray(fixture("header_1080x1920")))
        assertEquals(1.0, hit!!.first)
        assertTrue(hit.second >= 0.95, "scored ${hit.second}")
        assertEquals(emptyList<Double>(), tried)
        assertEquals(emptyList<Double>(), state.scalesAfterMiss(header), "a real absence later costs nothing")
    }

    @Test
    @DisplayName("a frame whose text is drawn at 0.92 misses at 1.0 and is found by the retry, in the scale order")
    fun smallerTextIsFound() {
        val frame = gray(fixture("header_scaled_092"))
        val tmpl = gray(template)
        assertTrue(bestScore(frame, tmpl) < confidence, "the 1.0 lookup must miss, as on the phone")
        val firstPassing = TextScaleFallback.SCALES.first { bestScore(frame, scaled(tmpl, it)) >= confidence }
        val (hit, tried) = lookUp(TextScaleFallback(), frame)
        assertNotNull(hit)
        assertEquals(firstPassing, hit!!.first)
        assertTrue(hit.first <= 0.96 && hit.second >= confidence)
        assertEquals(TextScaleFallback.SCALES.takeWhile { it != firstPassing } + firstPassing, tried)
        assertTrue(bestScore(frame, scaled(tmpl, 0.92)) >= 0.9, "0.92 is the drawn size")
    }

    @Test
    @DisplayName("the phone's own frame: the header misses at 1.0 and the retry finds it at 0.92")
    fun phoneFrame() {
        val frame = gray(fixture("header_a13_1080x2408"))
        val tmpl = gray(template)
        assertTrue(bestScore(frame, tmpl) < confidence)
        val (hit, tried) = lookUp(TextScaleFallback(), frame)
        assertEquals(0.92, hit!!.first)
        assertTrue(hit.second >= 0.85, "scored ${hit.second}")
        assertEquals(listOf(0.96, 0.92), tried)
    }

    @Test
    @DisplayName("the retry keeps the 0.8 bar: a smaller scale on a full-size frame is not accepted")
    fun retryKeepsTheBar() {
        val frame = gray(fixture("header_1080x1920"))
        assertTrue(bestScore(frame, scaled(gray(template), 0.88)) < confidence)
        assertTrue(bestScore(frame, scaled(gray(template), 0.92)) < confidence)
        assertEquals(listOf(0.96, 0.92, 0.88), TextScaleFallback.SCALES)
    }

    @Test
    @DisplayName("after a hit the label retries only its learned scale, and only the run's first hit is logged")
    fun learnedScale() {
        gateFalse()
        val state = TextScaleFallback()
        val first = state.afterMiss(header) { scale, _ -> if (scale == 0.92) "found" else null }
        assertEquals(0.92, first!!.scale)
        assertTrue(first.firstOfRun)
        assertEquals(listOf(0.92), state.scalesAfterMiss(header))
        val again = state.afterMiss("components/label/energy") { scale, _ -> if (scale == 0.96) "found" else null }
        assertFalse(again!!.firstOfRun)
    }

    @Test
    @DisplayName("a label never seen stops retrying after three full misses")
    fun boundedMisses() {
        val state = TextScaleFallback()
        var attempts = 0
        repeat(5) {
            assertNull(
                state.afterMiss<String>(header) { _, _ ->
                    attempts++
                    null
                },
            )
        }
        assertEquals(TextScaleFallback.MAX_FULL_MISSES * TextScaleFallback.SCALES.size, attempts)
        assertEquals(emptyList<Double>(), state.scalesAfterMiss(header))
    }

    @Test
    @DisplayName("buttons, icons and labels whose false hit could change a decision never get a retry, gate on or off")
    fun excludedTemplates() {
        val excluded =
            listOf(
                "components/button/training",
                "components/icon/tazuna",
                "components/label/stat_aptitude_A",
                "components/label/stat_maxed",
                "components/label/skill_list_screen_skill_points",
                "components/label/skill_list_screen_skill_points_v2",
                "components/label/support_formation",
                "components/label/event_boost_off",
                "components/label/scenario_select_ura",
                "components/label/scheduled_race",
                "components/label/race_criteria_fans",
                "components/label/now_loading",
                "components/label/connecting",
                "components/label/multi_race_popup",
                "components/label/item_request_held",
                "components/label/on_sale",
                "components/label/training_cannot_perform",
                "components/label/first_place",
                "components/label/umamusume_class_fans",
                "components/label/home_menu",
                "components/button/special_missions_tab_main",
                "components/button/special_missions_tab_daily",
                "components/button/menu_bar_race_selected",
                "components/button/strategy_pace_select",
                "components/button/no",
                "components/button/yes",
            )
        for (gate in listOf(::clearGate, ::gateTrue)) {
            gate()
            val state = TextScaleFallback()
            for (t in excluded) {
                assertEquals(emptyList<Double>(), state.scalesAfterMiss(t), t)
                assertNull(state.afterMiss<String>(t) { _, _ -> error("no retry for $t") })
            }
        }
    }

    @Test
    @DisplayName("every allowed label is a real template, and the override only widens the default 1080-wide lookup")
    fun wiring() {
        for (path in TextScaleFallback.ALLOWED + TextScaleFallback.ANCHORS.map { it.first }) {
            assertTrue(File(root(), "android/app/src/main/assets/images/$path.png").isFile, path)
        }
        val imageUtils = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/utils/CustomImageUtils.kt").readText().replace("\r\n", "\n")
        val override = imageUtils.substring(imageUtils.indexOf("override fun match("), imageUtils.indexOf("/** Converts this bitmap to a new OpenCV [Mat]. */"))
        val guard = override.indexOf("if (testScale != 0.0 || customScale != 1.0 || !is1080p) return result")
        assertTrue(guard > 0)
        assertTrue(override.indexOf("textScaleFallback.matchedAtFullSize(templateName)") > guard)
        assertTrue(override.indexOf("textScaleFallback.afterMiss(templateName)") > override.indexOf("if (result.first) {"))
        assertTrue(
            override.contains("val retryConfidence = minConfidence?.let { maxOf(if (customConfidence == 0.0) confidence else customConfidence, it) } ?: customConfidence"),
            "without the gate the retry keeps the caller's confidence; with it the bar never drops below the caller's",
        )
        assertTrue(override.contains("useSingleScale, retryConfidence, scale).takeIf { it.first }"))
        assertTrue(override.contains("if (hit.firstOfRun) {") && override.contains("[LAYOUT] Text scale"))
        assertTrue(override.contains("if (hit.opensGate) {"))
        assertFalse(imageUtils.contains("override fun matchAll("), "multi-hit counts stay single-scale")
        val measure = imageUtils.substring(imageUtils.indexOf("fun measureTextScale(): String {"), imageUtils.indexOf("private fun textScaleScores("))
        assertTrue(measure.indexOf("if (customScale != 1.0 || !is1080p) {") in 0 until measure.indexOf("getSourceBitmap()"), "measured only where match retries")
        assertTrue(measure.contains("listOf(LabelHomeMenu.template, LabelStatTableHeaderSkillPoints.template)"))
        val label = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/components/Label.kt").readText()
        assertTrue(label.contains("Template(\"components/label/home_menu\", region = Region.topQuarter)"))
        val game = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/bot/Game.kt").readText().replace("\r\n", "\n")
        val start = game.substring(game.indexOf("    fun start(): TaskResult {"))
        val layout = start.indexOf("[LAYOUT] Top cutout inset")
        val measureCall = start.indexOf("MessageLog.i(TAG, imageUtils.measureTextScale())")
        assertTrue(layout in 0 until measureCall && measureCall < start.indexOf("runDiagnostic()"), "measured once per Start, after the layout log")
        assertEquals(1, Regex("""measureTextScale\(\)""").findAll(game).count())
        val startModule = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt").readText().replace("\r\n", "\n")
        val session = startModule.substring(startModule.indexOf("fun onStartEvent(event: StartEvent) {"))
        assertTrue(session.indexOf("TextScaleFallback.resetGate()") in 0 until session.indexOf("dispatchDiagnostic("), "reset before the session's first navigation")
    }

    @Test
    @DisplayName("the Sparks keep confirmation false-hits stat_distance at 0.96, so that label stays behind the Details dialog")
    fun sparksFalseHit() {
        val frame = requireNotNull(javaClass.getResourceAsStream("/fixtures/sparks/keep_confirmation_12row_top.png")) { "missing sparks fixture" }.use { FixturePng.read(it) }
        // "Standard Distance" spark name around (247, 889); scored in a window there, inside the label's top-half region.
        val window = gray(frame).let { g -> Gray(350, 170, DoubleArray(350 * 170) { g.v[(820 + it / 350) * g.width + 150 + it % 350] }) }
        val tmpl = gray(File(root(), "android/app/src/main/assets/images/components/label/stat_distance.png").inputStream().use { FixturePng.read(it) })
        assertTrue(bestScore(window, tmpl) < confidence, "the 1.0 lookup misses, as today")
        val hit = TextScaleFallback().afterMiss("components/label/stat_distance") { scale, _ -> bestScore(window, scaled(tmpl, scale)).takeIf { it >= confidence } }
        assertEquals(0.96, hit!!.scale, "the retry would accept the spark name if asked on this screen")
        val base = "android/app/src/main/java/com/steve1316/uma_android_automation"
        val all = File(root(), base).walkTopDown().filter { it.extension == "kt" && it.name != "Label.kt" }.associate { it.name to it.readText().replace("\r\n", "\n") }
        assertEquals(listOf("Trainee.kt"), all.filter { Regex("""\bLabelStatDistance\b""").containsMatchIn(it.value) }.keys.toList())
        val trainee = all.getValue("Trainee.kt")
        assertEquals(1, Regex("""label = LabelStatDistance\)""").findAll(trainee).count())
        assertEquals(1, Regex("""updateTrackDistanceAptitudes\(imageUtils = imageUtils\)""").findAll(trainee).count(), "only updateAptitudes calls the distance read")
        val callers = all.filter { Regex("""\.updateAptitudes\(""").containsMatchIn(it.value) }
        assertEquals(listOf("Campaign.kt"), callers.keys.toList())
        val campaign = callers.getValue("Campaign.kt")
        val call = campaign.indexOf("trainee.updateAptitudes(game.imageUtils)")
        assertEquals(1, Regex("""\.updateAptitudes\(""").findAll(campaign).count())
        val branch = Regex(""""(\w+)" -> \{""").findAll(campaign.substring(0, call)).last().groupValues[1]
        assertEquals("umamusume_details", branch, "the one caller is the Details dialog branch")
    }

    @Test
    @DisplayName("gate: the phone's header says smaller text at 0.92 or 0.94, the 1080x1920 header says full size, no anchor says unknown")
    fun gateFromHeader() {
        val phone = gateScores(gray(fixture("header_a13_1080x2408")), header)
        assertEquals(true, TextScaleFallback.anchorShrinks(phone, confidence))
        assertTrue(phone.maxBy { it.value }.key in setOf(0.92, 0.94), "best ${phone.maxBy { it.value }}")
        assertEquals(false, TextScaleFallback.anchorShrinks(gateScores(gray(fixture("header_1080x1920")), header), confidence))
        val noAnchor = textScaleFixture("absent/m240_stat_table_header_skill_points")
        assertNull(TextScaleFallback.anchorShrinks(gateScores(noAnchor, header), confidence))
        assertNull(TextScaleFallback.anchorShrinks(gateScores(noAnchor, menu), confidence))

        assertEquals(TextScaleFallback.NOT_MEASURED, TextScaleFallback.measureAtStart(confidence) { gateScores(noAnchor, it) })
        assertNull(TextScaleFallback.textShrinks)
        val phoneFrame = gray(fixture("header_a13_1080x2408"))
        assertEquals(
            "[LAYOUT] Text scale 0.92 from \"Skill Pts\" (career Main): text draws smaller here, scaled retries on for 15 text templates.",
            TextScaleFallback.measureAtStart(confidence) { gateScores(phoneFrame, it) },
        )
        assertEquals(true, TextScaleFallback.textShrinks)
        TextScaleFallback.resetGate()
        assertNull(TextScaleFallback.textShrinks, "every session starts unmeasured")
    }

    @Test
    @DisplayName("gate: \"Menu\" at 240 DPI is full size, the same screen at 480 DPI draws it at 0.98")
    fun gateFromMenu() {
        val at240 = textScaleFixture("menu_240")
        assertEquals("[LAYOUT] Text scale 1.00 from \"Menu\" (Home): no scaled retries.", TextScaleFallback.measureAtStart(confidence) { gateScores(at240, it) })
        assertEquals(false, TextScaleFallback.textShrinks)
        val at480 = textScaleFixture("menu_480")
        assertEquals(
            "[LAYOUT] Text scale 0.98 from \"Menu\" (Home): text draws smaller here, scaled retries on for 15 text templates.",
            TextScaleFallback.measureAtStart(confidence) { gateScores(at480, it) },
        )
        assertEquals(true, TextScaleFallback.textShrinks)
        for (name in listOf("absent/m240_home_menu", "absent/p2316_home_menu")) {
            assertNull(TextScaleFallback.anchorShrinks(gateScores(textScaleFixture(name), menu), confidence), "$name: Menu's strongest absent window")
        }
    }

    @Test
    @DisplayName("gate on: the 480 DPI Auto-Select and Details buttons miss at 1.0 and are found at 0.98")
    fun buttonsAt480() {
        for ((name, path) in listOf("auto_select_480" to "components/button/auto_select", "details_480" to "components/button/details")) {
            val frame = textScaleFixture(name)
            val tmpl = asset(path)
            assertTrue(bestScore(frame, tmpl) < confidence, "$name misses at 1.0")
            assertNull(lookUp(TextScaleFallback(), frame, path, tmpl).first, "$name: no retry before the gate")
            gateTrue()
            val (hit, tried) = lookUp(TextScaleFallback(), frame, path, tmpl)
            assertEquals(0.98, hit!!.first, name)
            assertTrue(hit.second >= TextScaleFallback.LADDER_CONFIDENCE, "$name scored ${hit.second}")
            assertEquals(listOf(0.98), tried)
            TextScaleFallback.resetGate()
        }
    }

    @Test
    @DisplayName("gate on: the ladder runs 0.98 down to 0.90 with a 0.85 bar, and the phone's header is first found at 0.94")
    fun ladderOrderAndBar() {
        gateTrue()
        assertEquals(listOf(0.98, 0.96, 0.94, 0.92, 0.90), TextScaleFallback.LADDER)
        assertEquals(0.85, TextScaleFallback.LADDER_CONFIDENCE)
        val scores = mapOf(0.98 to 0.83, 0.96 to 0.84, 0.94 to 0.86, 0.92 to 0.95)
        val bars = mutableListOf<Double?>()
        val tried = mutableListOf<Double>()
        val hit =
            TextScaleFallback().afterMiss("components/button/back") { scale, minConfidence ->
                tried.add(scale)
                bars.add(minConfidence)
                scores[scale]?.takeIf { it >= maxOf(confidence, minConfidence ?: 0.0) }
            }
        assertEquals(0.94, hit!!.scale, "0.83 and 0.84 pass the old 0.8 bar but not 0.85")
        assertEquals(listOf(0.98, 0.96, 0.94), tried)
        assertEquals(listOf(0.85, 0.85, 0.85), bars)
        val (phoneHit, phoneTried) = lookUp(TextScaleFallback(), gray(fixture("header_a13_1080x2408")))
        assertEquals(0.94, phoneHit!!.first)
        assertEquals(listOf(0.98, 0.96, 0.94), phoneTried)
        assertTrue(phoneHit.second >= 0.85, "scored ${phoneHit.second}")
    }

    @Test
    @DisplayName("gate on: no allowed template is found on the strongest MuMu 240 and 1080x2316 windows where it misses at 1.0")
    fun noFalseHitsWithGateOn() {
        gateTrue()
        val dir = File(javaClass.getResource("/fixtures/textscale/absent")!!.toURI())
        for (path in TextScaleFallback.ALLOWED) {
            for (group in listOf("m240", "p2316")) {
                val name = "absent/${group}_${path.substringAfterLast('/')}"
                assertTrue(File(dir, "${name.substringAfter('/')}.png").isFile, "missing $name")
                val frame = textScaleFixture(name)
                val tmpl = asset(path)
                val (hit, tried) = lookUp(TextScaleFallback(), frame, path, tmpl)
                assertNull(hit, "$name scored ${hit?.second} at ${hit?.first}")
                assertEquals(TextScaleFallback.LADDER, tried, name)
            }
        }
    }

    @Test
    @DisplayName("gate off or unknown: scalesAfterMiss gives exactly what it gave before the gate, for every template and state")
    fun gateOffUnchanged() {
        val templates = TextScaleFallback.ALLOWED + listOf("components/button/training", "components/label/home_menu", "components/label/support_formation")
        for (gate in listOf(::clearGate, ::gateFalse)) {
            gate()
            val state = TextScaleFallback()
            // The scalesAfterMiss of the class before the gate: the 6 labels, 0.96/0.92/0.88, learned scale, 3 misses.
            val fullSize = mutableSetOf<String>()
            val learned = mutableMapOf<String, Double>()
            val misses = mutableMapOf<String, Int>()

            fun before(t: String): List<Double> =
                when {
                    t !in TextScaleFallback.LABELS || t in fullSize -> emptyList()
                    t in learned -> listOf(learned.getValue(t))
                    (misses[t] ?: 0) >= 3 -> emptyList()
                    else -> listOf(0.96, 0.92, 0.88)
                }

            fun check() = templates.forEach { assertEquals(before(it), state.scalesAfterMiss(it), "$it gate ${TextScaleFallback.textShrinks}") }
            check()
            for (t in listOf("components/label/energy", "components/button/back")) {
                state.matchedAtFullSize(t)
                if (t in TextScaleFallback.LABELS) fullSize.add(t)
            }
            check()
            assertNull(state.afterMiss<String>("components/button/details") { _, _ -> error("no retry") })
            repeat(4) {
                state.afterMiss<String>("components/label/stat_style") { _, minConfidence ->
                    assertNull(minConfidence, "the caller's confidence as before")
                    null
                }
            }
            misses["components/label/stat_style"] = 4
            check()
            if (TextScaleFallback.textShrinks == false) {
                state.afterMiss(header) { scale, _ -> if (scale == 0.92) "found" else null }
                learned[header] = 0.92
                check()
                assertEquals(false, TextScaleFallback.textShrinks, "a measured full size is not overturned by a label hit")
            }
        }
    }

    @Test
    @DisplayName("gate unknown: a label found smaller turns the gate on for the rest of the run")
    fun labelHitOpensUnknownGate() {
        val state = TextScaleFallback()
        val hit = state.afterMiss(header) { scale, _ -> if (scale == 0.92) "found" else null }
        assertTrue(hit!!.opensGate)
        assertEquals(true, TextScaleFallback.textShrinks)
        assertEquals(TextScaleFallback.LADDER, TextScaleFallback().scalesAfterMiss("components/button/auto_select"), "another run's instance sees it too")
        val next = state.afterMiss("components/label/energy") { scale, _ -> if (scale == 0.96) "found" else null }
        assertFalse(next!!.opensGate)
    }
}
