package com.steve1316.uma_android_automation.utils

import com.steve1316.automation_library.data.SharedData
import com.steve1316.uma_android_automation.BoundedSwipe
import com.steve1316.uma_android_automation.HostInputScope
import com.steve1316.uma_android_automation.bot.FROZEN_GRID_MASKED
import com.steve1316.uma_android_automation.bot.frozenFrameGrid
import com.steve1316.uma_android_automation.components.Region
import com.steve1316.uma_android_automation.components.persistentSkipPillRegion
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Every capture is read at 1080 wide. At a raw width of 1080 every step must be a no-op: the same capture object, the same
 * regions, bands and coordinates as before.
 */
@DisplayName("Canonical screen width")
class CanonicalScreenTest {
    private val widths = listOf(720, 1080, 1440, 1600)
    private val tallHeights = listOf(1920, 2316, 2340, 2408)

    private fun root(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "android/app/src/main/assets").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found")
    }

    private fun source(path: String): String = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation/$path").readText().replace("\r\n", "\n")

    @Nested
    @DisplayName("mapping between raw and canonical pixels")
    inner class Mapping {
        @Test
        @DisplayName("a canonical point maps to raw and back within 1 px at every width")
        fun canonicalRoundTrip() {
            for (w in widths) {
                for (c in 0..CANONICAL_WIDTH) {
                    val raw = canonicalToRaw(c.toDouble(), w).roundToInt()
                    val back = rawToCanonical(raw.toDouble(), w).roundToInt()
                    assertTrue(abs(back - c) <= 1, "W=$w c=$c raw=$raw back=$back")
                }
            }
        }

        @Test
        @DisplayName("a raw point maps to canonical and back within 1 px at every width")
        fun rawRoundTrip() {
            for (w in widths) {
                for (r in 0..w) {
                    val c = rawToCanonical(r.toDouble(), w).roundToInt()
                    val back = canonicalToRaw(c.toDouble(), w).roundToInt()
                    assertTrue(abs(back - r) <= 1, "W=$w r=$r c=$c back=$back")
                }
            }
        }

        @Test
        @DisplayName("both directions are the identity at 1080, also for fractional points")
        fun identityAt1080() {
            for (i in 0..4000) {
                val v = i * 0.537
                assertEquals(v, canonicalToRaw(v, 1080))
                assertEquals(v, rawToCanonical(v, 1080))
            }
        }

        @Test
        @DisplayName("both axes use the same factor W/1080")
        fun sameFactor() {
            assertEquals(1440.0, canonicalToRaw(1080.0, 1440), 1e-9)
            assertEquals(480.0, canonicalToRaw(720.0, 720), 1e-9)
            assertEquals(1350.0, rawToCanonical(1800.0, 1440), 1e-9)
        }

        @Test
        @DisplayName("canonical sizes: width 1080, height scaled by 1080/W, unchanged at 1080, unknown stays unknown")
        fun sizes() {
            assertEquals(1080, canonicalWidth(720))
            assertEquals(1080, canonicalWidth(1440))
            assertEquals(0, canonicalWidth(0))
            assertEquals(1920, canonicalHeight(720, 1280))
            assertEquals(1920, canonicalHeight(1440, 2560))
            assertEquals(2340, canonicalHeight(1440, 3120))
            assertEquals(2316, canonicalHeight(1440, 3088))
            assertEquals(1728, canonicalHeight(1600, 2560))
            for (h in tallHeights) assertEquals(h, canonicalHeight(1080, h))
            assertEquals(0, canonicalHeight(0, 0))
        }

        @Test
        @DisplayName("an (x, y, size) window is the same object at 1080 and scaled elsewhere")
        fun rect() {
            val button = Triple(900, 1200, 110)
            assertSame(button, canonicalRect(button, 1080))
            assertSame(button, canonicalRect(button, 0))
            assertEquals(Triple(900, 1200, 110), canonicalRect(Triple(1200, 1600, 147), 1440))
            assertEquals(Triple(900, 1200, 111), canonicalRect(Triple(600, 800, 74), 720))
        }

        @Test
        @DisplayName("the inset is stored in canonical pixels: unchanged at 1080")
        fun inset() {
            for (raw in listOf(0, 48, 71, 94, 125)) assertEquals(raw, canonicalInset(raw, 1080))
            assertEquals(94, canonicalInset(125, 1440))
            assertEquals(48, canonicalInset(32, 720))
        }

        @Test
        @DisplayName("the size note is empty at 1080 and names the canonical size elsewhere")
        fun sizeNote() {
            for (h in tallHeights) assertEquals("", canonicalSizeNote(1080, h))
            assertEquals("", canonicalSizeNote(0, 0))
            assertEquals(" (read as 1080x2340)", canonicalSizeNote(1440, 3120))
        }
    }

    @Nested
    @DisplayName("capture")
    inner class Capture {
        @Test
        @DisplayName("a 1080-wide capture is the same object and is never resized")
        fun sameObjectAt1080() {
            for (h in tallHeights) {
                val capture = Any()
                var resized = false
                val result =
                    toCanonicalCapture(capture, 1080, h) { _, _ ->
                        resized = true
                        Any()
                    }
                assertSame(capture, result, "1080x$h")
                assertFalse(resized, "1080x$h")
                assertNull(canonicalCaptureSize(1080, h))
            }
        }

        @Test
        @DisplayName("other widths are resized to 1080 x round(H * 1080 / W)")
        fun resizedElsewhere() {
            for ((w, h, expected) in listOf(Triple(720, 1280, 1080 to 1920), Triple(1440, 3120, 1080 to 2340), Triple(1600, 2560, 1080 to 1728))) {
                var size: Pair<Int, Int>? = null
                toCanonicalCapture(Any(), w, h) { cw, ch ->
                    size = cw to ch
                    Any()
                }
                assertEquals(expected, size, "${w}x$h")
            }
        }

        @Test
        @DisplayName("getSourceBitmap resizes after the padding crop, and the library's size and offsets are canonical")
        fun wiring() {
            val src = source("utils/CustomImageUtils.kt")
            assertTrue(src.contains("val bitmap = toCanonicalWidth(cropToScreenWidth(super.getSourceBitmap(saveImage), SharedData.displayWidth))"))
            assertTrue(src.contains("toCanonicalCapture(capture, capture.width, capture.height) { width, height ->"))
            assertTrue(src.contains("override val displayWidth: Int get() = CanonicalScreen.width"))
            assertTrue(src.contains("override val displayHeight: Int get() = CanonicalScreen.height"))
            assertTrue(src.contains("override fun relWidth(oldX: Int): Int = oldX\n"))
            assertTrue(src.contains("override fun relHeight(oldY: Int): Int = oldY\n"))
            assertEquals(1, Regex("""SharedData\.display(Width|Height)""").findAll(src).count(), "only the padding crop reads the raw size")
        }
    }

    @Nested
    @DisplayName("band model with canonical input")
    inner class Bands {
        private val ys = listOf(0.0, 52.0, 110.0, 500.0, 960.0, 1400.0, 1771.0, 1920.0)

        @Test
        @DisplayName("gameY on 1080x2316 and 1080x2408 is unchanged with canonical input")
        fun unchangedAt1080() {
            for ((h, inset) in listOf(2316 to 94, 2408 to 94, 2316 to 0, 1920 to 0)) {
                for (band in ScreenBand.entries) {
                    for (y in ys) {
                        assertEquals(
                            gameY(y, band, 1080, h, inset),
                            gameY(y, band, canonicalWidth(1080), canonicalHeight(1080, h), canonicalInset(inset, 1080)),
                            "1080x$h $band y=$y",
                        )
                    }
                }
            }
        }

        @Test
        @DisplayName("a 1440x3088 phone reads the same game rows as the 1080x2316 one it scales")
        fun sameRowsAt1440() {
            assertTrue(isMappedSurface(canonicalWidth(1440), canonicalHeight(1440, 3088)))
            assertTrue(isMappedSurface(canonicalWidth(720), canonicalHeight(720, 1280)))
            for (band in ScreenBand.entries) {
                for (y in ys) {
                    assertEquals(
                        gameY(y, band, 1080, 2316, 94),
                        gameY(y, band, canonicalWidth(1440), canonicalHeight(1440, 3088), canonicalInset(125, 1440)),
                        1e-9,
                        "$band y=$y",
                    )
                }
            }
        }

        @Test
        @DisplayName("the stored inset is canonical")
        fun storedInset() {
            assertTrue(source("utils/ScreenBands.kt").contains("}.let { canonicalInset(it, SharedData.displayWidth) }"))
        }
    }

    @Nested
    @DisplayName("regions")
    inner class Regions {
        @Test
        @DisplayName("Region values on 1080-wide screens are the ones the raw size gave; a 1440 phone gets its canonical size")
        fun regionValues() {
            for ((raw, size) in listOf((1080 to 1920) to (1080 to 1920), (1080 to 2316) to (1080 to 2316), (1080 to 2408) to (1080 to 2408), (1440 to 3120) to (1080 to 2340))) {
                val (w, h) = size
                val region = freshRegion(raw.first, raw.second)
                val expected =
                    mapOf(
                        "TopHalf" to intArrayOf(0, 0, w, h / 2),
                        "TopQuarter" to intArrayOf(0, 0, w, h / 4),
                        "BottomHalf" to intArrayOf(0, h / 2, w, h / 2),
                        "BottomQuarter" to intArrayOf(0, h / 4, w, h / 4),
                        "Middle" to intArrayOf(0, h / 4, w, h / 2),
                        "LeftHalf" to intArrayOf(0, 0, w / 2, h),
                        "RightHalf" to intArrayOf(w / 2, 0, w / 2, h),
                        "ThreeQuarterRight" to intArrayOf((w * 0.625).toInt(), 0, (w * 0.375).toInt(), h),
                        "TopRightThird" to intArrayOf(w - (w / 3), 0, w / 3, h - (h / 3)),
                        "PersistentSkipPill" to persistentSkipPillRegion(w, h),
                    )
                for ((field, value) in expected) assertArrayEquals(value, region(field), "${raw.first}x${raw.second} $field")
            }
        }

        /** [Region] freezes on first access in this JVM, so each size gets its own copy of the class, initialized with that raw size. */
        private fun freshRegion(
            width: Int,
            height: Int,
        ): (String) -> IntArray {
            val saved = SharedData.displayWidth to SharedData.displayHeight
            try {
                SharedData.displayWidth = width
                SharedData.displayHeight = height
                val name = Region::class.java.name
                val bytes = requireNotNull(Region::class.java.getResourceAsStream("/" + name.replace('.', '/') + ".class")).use { it.readBytes() }
                val cls =
                    object : ClassLoader(Region::class.java.classLoader) {
                        fun define(): Class<*> = defineClass(name, bytes, 0, bytes.size)
                    }.define()
                val instance = cls.getField("INSTANCE").get(null)
                val names = listOf("TopHalf", "TopQuarter", "BottomHalf", "BottomQuarter", "Middle", "LeftHalf", "RightHalf", "ThreeQuarterRight", "TopRightThird", "PersistentSkipPill")
                val values = names.associateWith { cls.getMethod("get$it").invoke(instance) as IntArray }
                return { values.getValue(it) }
            } finally {
                SharedData.displayWidth = saved.first
                SharedData.displayHeight = saved.second
            }
        }

        @Test
        @DisplayName("the canonical size equals the raw size on every 1080-wide screen")
        fun canonicalSizeAt1080() {
            val saved = SharedData.displayWidth to SharedData.displayHeight
            try {
                for (h in tallHeights) {
                    SharedData.displayWidth = 1080
                    SharedData.displayHeight = h
                    assertEquals(1080, CanonicalScreen.width)
                    assertEquals(h, CanonicalScreen.height)
                }
                SharedData.displayWidth = 1440
                SharedData.displayHeight = 3120
                assertEquals(1080 to 2340, CanonicalScreen.width to CanonicalScreen.height)
            } finally {
                SharedData.displayWidth = saved.first
                SharedData.displayHeight = saved.second
            }
        }

        @Test
        @DisplayName("raw size reads stay only where the raw size is meant: logs, support gates, the overlay window and the capture crop")
        fun rawReadsOnlyWhereMeant() {
            val allowed =
                mapOf(
                    // Device log, capture-size warning and the not-supported warning.
                    "bot/Game.kt" to 12,
                    // The Grand Concert screen gate.
                    "bot/campaigns/GrandConcert.kt" to 2,
                    // The lineage-capture gate and the Grand Concert gate.
                    "CareerLaunchNavigator.kt" to 6,
                    "StartModule.kt" to 2,
                    "types/TrackblazerShopList.kt" to 2,
                    "utils/OwnInputProbe.kt" to 4,
                    "utils/CustomImageUtils.kt" to 1,
                    "utils/ScreenBands.kt" to 1,
                    "utils/CanonicalScreen.kt" to 8,
                )
            val base = File(root(), "android/app/src/main/java/com/steve1316/uma_android_automation")
            val raw = Regex("""SharedData\.display(Width|Height)""")
            val found =
                base
                    .walkTopDown()
                    .filter { it.extension == "kt" }
                    .associate { it.relativeTo(base).invariantSeparatorsPath to raw.findAll(it.readText()).count() }
                    .filterValues { it > 0 }
            assertEquals(allowed, found)
        }
    }

    @Nested
    @DisplayName("frozen-frame grid and overlay keep-clear")
    inner class Overlay {
        private val flat: (Int, Int) -> Int = { _, _ -> 0xFF808080.toInt() }

        @Test
        @DisplayName("a 1440 phone masks the same cells as the 1080 phone it scales")
        fun frozenGridAt1440() {
            val at1080 = frozenFrameGrid(1080, 1920, Triple(900, 1200, 110), 48, flat)
            val at1440 = frozenFrameGrid(1080, 1920, canonicalRect(Triple(1200, 1600, 147), 1440), canonicalInset(64, 1440), flat)
            assertArrayEquals(at1080, at1440)
            assertTrue(at1080.count { it == FROZEN_GRID_MASKED } > 0)
        }

        @Test
        @DisplayName("the grid's inputs are mapped to canonical before use")
        fun frozenGridWiring() {
            assertTrue(source("bot/Game.kt").contains("frozenFrameGrid(bitmap.width, bitmap.height, runCatching { canonicalRect(floatingButtonRect(myContext)) }.getOrNull(), screenTopInset)"))
        }

        @Test
        @DisplayName("keep-clear hits are unchanged at 1080 and the same on a 1440 phone")
        fun keepClear() {
            for ((x, y) in listOf(400 to 150, 500 to 1500, 500 to 1600, 960 to 40)) {
                val today = overlayKeepClearHits(x, y, 110, 1080, 2316, 94)
                val (cx, cy, cs) = canonicalRect(Triple(x, y, 110), 1080)
                assertEquals(today, overlayKeepClearHits(cx, cy, cs, canonicalWidth(1080), canonicalHeight(1080, 2316), canonicalInset(94, 1080)))
                val (bx, by, bs) = canonicalRect(Triple(x * 4 / 3, y * 4 / 3, 147), 1440)
                assertEquals(today, overlayKeepClearHits(bx, by, bs, canonicalWidth(1440), canonicalHeight(1440, 3088), canonicalInset(125, 1440)), "($x, $y)")
            }
        }
    }

    @Nested
    @DisplayName("taps and swipes")
    inner class Gestures {
        private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

        @Test
        @DisplayName("tap and swipe points are dispatched unchanged at 1080, bit for bit")
        fun identityAt1080() {
            for (i in 0..3000) {
                val v = i * 0.731
                assertEquals(v to v * 2, rawTapPoint(v, v * 2, 1080))
                val f = v.toFloat()
                assertArrayEquals(floatArrayOf(f, f + 1.5f, f * 2, f / 3), rawSwipePoints(f, f + 1.5f, f * 2, f / 3, 1080))
            }
        }

        @Test
        @DisplayName("a tap lands at canonical * W / 1080 on both axes, within 1 px after rounding")
        fun tapAtOtherWidths() {
            for (w in listOf(720, 1440, 1600)) {
                for (c in 0..2400 step 7) {
                    val (x, y) = rawTapPoint(c.toDouble(), (c + 3).toDouble(), w)
                    assertEquals(c * w / 1080.0, x, 1e-9)
                    assertEquals((c + 3) * w / 1080.0, y, 1e-9)
                    assertTrue(abs(x.toInt() - c * w / 1080.0) <= 1.0, "W=$w c=$c")
                }
            }
        }

        @Test
        @DisplayName("a swipe's start, end and distance scale by W / 1080 at 720 and 1440")
        fun swipeAtOtherWidths() {
            for (w in listOf(720, 1440)) {
                val raw = rawSwipePoints(540f, 1400f, 540f, 656f, w)
                val k = w / 1080f
                assertArrayEquals(floatArrayOf(540f * k, 1400f * k, 540f * k, 656f * k), raw, 1e-3f)
                assertEquals((1400f - 656f) * k, raw[1] - raw[3], 1e-3f)
                assertEquals(0f, raw[0] - raw[2], 1e-3f)
            }
        }

        @Test
        @DisplayName("the Start log names the read size and the map factor")
        fun mapLine() {
            assertEquals("[LAYOUT] Screen read at 1080x2316; taps and swipes map to the screen by 1.0000.", tapMapLine(1080, 2316))
            assertTrue(tapMapLine(720, 1280).endsWith("1080x1920; taps and swipes map to the screen by 0.6667."))
            assertTrue(tapMapLine(1440, 3120).endsWith("1080x2340; taps and swipes map to the screen by 1.3333."))
        }

        @Test
        @DisplayName("the helpers map through rawTapPoint and rawSwipePoints with the raw width, and never keep the service")
        fun helperWiring() {
            val src = source("utils/CanonicalScreen.kt")
            assertTrue(src.contains("val (rawX, rawY) = rawTapPoint(x, y, SharedData.displayWidth)\n    return tap(rawX, rawY, imageName, longPress, pressDuration, taps)"))
            assertTrue(src.contains("val raw = rawSwipePoints(oldX, oldY, newX, newY, SharedData.displayWidth)\n    return swipe(raw[0], raw[1], raw[2], raw[3], duration)"))
            assertFalse(src.contains("getInstance"), "the helper takes the caller's service per call")
            assertFalse(Regex("""va[lr] \w+\s*:\s*MyAccessibilityService""").containsMatchIn(src))
        }

        @Test
        @DisplayName("the tap choke points dispatch through the helper")
        fun chokePoints() {
            assertTrue(source("bot/Game.kt").contains("        gestureUtils.tapCanonical(x, y, imageName, taps = taps)\n"))
            assertTrue(source("bot/CoordinateTap.kt").contains("        live.tapCanonical(jx.toDouble(), jy.toDouble(), null, taps = taps)\n"))
            assertTrue(source("components/Components.kt").contains("        MyAccessibilityService.getInstance().tapCanonical(x, y, imageName, taps = taps)\n"))
        }

        @Test
        @DisplayName("no tap, swipe or gesture is dispatched with raw coordinates outside the helper")
        fun noRawGestures() {
            val allowed =
                mapOf(
                    // The probe taps its own overlay window, whose position Android gives in raw pixels.
                    "utils/OwnInputProbe.kt service.dispatchGesture" to 1,
                    // Host swipes carry 0..10000 fractions of the captured frame; the host companion maps them to the real screen size.
                    "CareerLaunchNavigator.kt transport.swipe" to 1,
                    "ProductionHostScrollRecovery.kt activeTransport.swipe" to 1,
                )
            val call = Regex("""([A-Za-z_][\w.]*?(?:\(\))?)\.(tap|swipe|scroll|longPress|dispatchGesture)\(""")
            val base = File(root(), main)
            val found = mutableMapOf<String, Int>()
            base.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
                val path = file.relativeTo(base).invariantSeparatorsPath
                file.readLines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }.forEach { line ->
                    call.findAll(line).forEach { m ->
                        val receiver = m.groupValues[1]
                        // Game.tap and CoordinateTap.tap are the choke points, checked above.
                        if (receiver != "CoordinateTap" && receiver != "game") {
                            val key = "$path $receiver.${m.groupValues[2]}"
                            found[key] = (found[key] ?: 0) + 1
                        }
                    }
                }
            }
            assertEquals(allowed, found, "route it through tapCanonical/swipeCanonical, or allow-list it with its reason")
        }

        @Test
        @DisplayName("host swipes are normalized against the captured frame, so they need no map")
        fun hostSwipesNormalizedOnTheFrame() {
            val nav = source("CareerLaunchNavigator.kt")
            assertEquals(2, Regex("""BoundedSwipe\.fromPixels\(""").findAll(nav).count())
            assertTrue(nav.contains("frameWidth = bitmap.width,\n            frameHeight = bitmap.height,"))
            assertTrue(nav.contains("frameWidth = beforeBitmap.width,\n                frameHeight = beforeBitmap.height,"))
            val request = requireNotNull(BoundedSwipe.fromPixels(HostInputScope.BORROW_LIST_SCROLL, 540f, 1382f, 540f, 734f, 1080, 1920, 900))

            // tools/host-companion/adb.go normalizedToPixel, applied with the device's real size.
            fun hostPixel(
                value: Int,
                extent: Int,
            ): Int = (value * (extent - 1) + 5000) / 10000
            for ((w, h) in listOf(720 to 1280, 1080 to 1920, 1440 to 2560)) {
                assertTrue(abs(hostPixel(request.startX, w) - canonicalToRaw(540.0, w)) <= 1.0, "startX at $w")
                assertTrue(abs(hostPixel(request.startY, h) - canonicalToRaw(1382.0, w)) <= 1.0, "startY at $w")
                assertTrue(abs(hostPixel(request.endY, h) - canonicalToRaw(734.0, w)) <= 1.0, "endY at $w")
            }
        }
    }

    /**
     * A 720 phone renders smaller and is enlarged back to 1080 (bilinear); a 1440 phone renders larger and is shrunk (area). The
     * stand-in resizes a 1080 crop to the phone's width and back. Present templates keep the 0.8 bar.
     */
    @Nested
    @DisplayName("template matching after the canonical resize")
    inner class Matching {
        private inner class Gray(val width: Int, val height: Int, val v: DoubleArray)

        private fun gray(img: FixturePng): Gray =
            Gray(
                img.width,
                img.height,
                DoubleArray(img.width * img.height) {
                    val argb = img.getRGB(it % img.width, it / img.width)
                    0.114 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.299 * (argb and 0xFF)
                },
            )

        private fun fixture(path: String): Gray = gray(requireNotNull(javaClass.getResourceAsStream("/fixtures/$path.png")) { "missing fixture $path" }.use { FixturePng.read(it) })

        private fun template(path: String): Gray = gray(File(root(), "android/app/src/main/assets/images/$path.png").inputStream().use { FixturePng.read(it) })

        /** OpenCV INTER_LINEAR, half-pixel centres, stored back to 8 bits. */
        private fun bilinear(g: Gray, w: Int, h: Int): Gray {
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
                    out[y * w + x] = (top * (1 - fy) + bottom * fy).roundToInt().toDouble()
                }
            }
            return Gray(w, h, out)
        }

        /** OpenCV INTER_AREA for shrinking: each output pixel is the area-weighted mean of the source pixels it covers. */
        private fun area(g: Gray, w: Int, h: Int): Gray {
            val sx = g.width.toDouble() / w
            val sy = g.height.toDouble() / h
            val out = DoubleArray(w * h)
            for (y in 0 until h) {
                val y0 = y * sy
                val y1 = (y + 1) * sy
                for (x in 0 until w) {
                    val x0 = x * sx
                    val x1 = (x + 1) * sx
                    var sum = 0.0
                    var weight = 0.0
                    for (iy in y0.toInt() until minOf(g.height, ceil(y1).toInt())) {
                        val wy = minOf(y1, iy + 1.0) - maxOf(y0, iy.toDouble())
                        for (ix in x0.toInt() until minOf(g.width, ceil(x1).toInt())) {
                            val wx = minOf(x1, ix + 1.0) - maxOf(x0, ix.toDouble())
                            sum += g.v[iy * g.width + ix] * wx * wy
                            weight += wx * wy
                        }
                    }
                    out[y * w + x] = (sum / weight).roundToInt().toDouble()
                }
            }
            return Gray(w, h, out)
        }

        /** The crop as a [rawWidth]-wide phone would show it, then resized back to canonical like the capture. */
        private fun throughPhone(g: Gray, rawWidth: Int): Gray {
            val w = (g.width * rawWidth / 1080.0).roundToInt()
            val h = (g.height * rawWidth / 1080.0).roundToInt()
            return if (rawWidth < 1080) bilinear(area(g, w, h), g.width, g.height) else area(bilinear(g, w, h), g.width, g.height)
        }

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

        private val present =
            listOf(
                "statheader/header_1080x1920" to "components/label/stat_table_header_skill_points",
                "goalcounter/fan_counter_1080x1920" to "components/label/race_criteria_fans",
                "goalcounter/trophy_counter_1080x1920" to "components/label/race_criteria_trophies",
                "textscale/menu_240" to "components/label/home_menu",
            )

        @Test
        @DisplayName("present templates keep 0.8 or more at 720 and 1440 wide")
        fun presentTemplatesKeepTheBar() {
            for ((frame, path) in present) {
                val crop = fixture(frame)
                val t = template(path)
                assertTrue(bestScore(crop, t) >= 0.8, "$frame at 1080")
                for (w in listOf(720, 1440)) {
                    val score = bestScore(throughPhone(crop, w), t)
                    assertTrue(score >= 0.8, "$frame through $w: $score")
                }
            }
        }
    }
}
