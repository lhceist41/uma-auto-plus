package com.steve1316.uma_android_automation.utils

import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.Inflater

/**
 * Minimal PNG reader for the spark screen fixtures. Local Android unit tests compile against
 * android.jar, which carries neither java.awt nor javax.imageio, so the fixtures are decoded
 * here with nothing beyond java.util.zip: chunk walk, one Inflater pass over the IDAT stream,
 * and the five standard scanline filters. Deliberately supports exactly what the fixture set
 * is (8-bit RGBA, non-interlaced, 1080x1920) and fails loudly on anything else - a fixture
 * that stops matching this shape should break the suite, not decode wrongly.
 *
 * Correctness is cross-pinned by the fixture tests themselves: the probe expectations were
 * measured on the same files with an independent decoder, so a filter bug here cannot
 * reproduce the full 9-screen classification matrix.
 */
class FixturePng private constructor(val width: Int, val height: Int, private val pixels: IntArray) {
    /** ARGB at (x, y), matching Bitmap.getPixel / BufferedImage.getRGB semantics. */
    fun getRGB(x: Int, y: Int): Int = pixels[y * width + x]

    companion object {
        private const val BYTES_PER_PIXEL = 4

        fun read(stream: InputStream): FixturePng {
            val input = DataInputStream(stream)
            val signature = ByteArray(8)
            input.readFully(signature)
            require(signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) { "not a PNG" }

            var width = 0
            var height = 0
            var idat = ByteArray(0)
            while (true) {
                val length = input.readInt()
                val type = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                val data = ByteArray(length).also { input.readFully(it) }
                input.readInt() // CRC, unchecked
                when (type) {
                    "IHDR" -> {
                        width = readIntBE(data, 0)
                        height = readIntBE(data, 4)
                        val bitDepth = data[8].toInt()
                        val colorType = data[9].toInt()
                        val interlace = data[12].toInt()
                        require(bitDepth == 8 && colorType == 6 && interlace == 0) {
                            "fixture must be 8-bit RGBA non-interlaced, got depth=$bitDepth type=$colorType interlace=$interlace"
                        }
                    }
                    "IDAT" -> idat += data
                    "IEND" -> break
                }
            }
            require(width > 0 && height > 0) { "IHDR missing" }

            val stride = width * BYTES_PER_PIXEL
            val raw = ByteArray((stride + 1) * height)
            val inflater = Inflater()
            inflater.setInput(idat)
            var offset = 0
            while (!inflater.finished() && offset < raw.size) {
                val n = inflater.inflate(raw, offset, raw.size - offset)
                if (n == 0 && inflater.needsInput()) break
                offset += n
            }
            inflater.end()
            require(offset == raw.size) { "truncated IDAT: got $offset of ${raw.size}" }

            val pixels = IntArray(width * height)
            val previous = ByteArray(stride)
            val current = ByteArray(stride)
            for (y in 0 until height) {
                val filter = raw[y * (stride + 1)].toInt() and 0xFF
                System.arraycopy(raw, y * (stride + 1) + 1, current, 0, stride)
                unfilter(filter, current, previous, stride)
                var x = 0
                var i = 0
                while (x < width) {
                    val r = current[i].toInt() and 0xFF
                    val g = current[i + 1].toInt() and 0xFF
                    val b = current[i + 2].toInt() and 0xFF
                    val a = current[i + 3].toInt() and 0xFF
                    pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    x++
                    i += BYTES_PER_PIXEL
                }
                System.arraycopy(current, 0, previous, 0, stride)
            }
            return FixturePng(width, height, pixels)
        }

        private fun readIntBE(data: ByteArray, at: Int): Int =
            ((data[at].toInt() and 0xFF) shl 24) or
                ((data[at + 1].toInt() and 0xFF) shl 16) or
                ((data[at + 2].toInt() and 0xFF) shl 8) or
                (data[at + 3].toInt() and 0xFF)

        private fun unfilter(filter: Int, current: ByteArray, previous: ByteArray, stride: Int) {
            when (filter) {
                0 -> Unit
                1 ->
                    for (i in BYTES_PER_PIXEL until stride) {
                        current[i] = (current[i] + current[i - BYTES_PER_PIXEL]).toByte()
                    }
                2 ->
                    for (i in 0 until stride) {
                        current[i] = (current[i] + previous[i]).toByte()
                    }
                3 ->
                    for (i in 0 until stride) {
                        val left = if (i >= BYTES_PER_PIXEL) current[i - BYTES_PER_PIXEL].toInt() and 0xFF else 0
                        val up = previous[i].toInt() and 0xFF
                        current[i] = (current[i] + ((left + up) / 2)).toByte()
                    }
                4 ->
                    for (i in 0 until stride) {
                        val left = if (i >= BYTES_PER_PIXEL) current[i - BYTES_PER_PIXEL].toInt() and 0xFF else 0
                        val up = previous[i].toInt() and 0xFF
                        val upLeft = if (i >= BYTES_PER_PIXEL) previous[i - BYTES_PER_PIXEL].toInt() and 0xFF else 0
                        current[i] = (current[i] + paeth(left, up, upLeft)).toByte()
                    }
                else -> throw IllegalArgumentException("unsupported PNG filter $filter")
            }
        }

        private fun paeth(a: Int, b: Int, c: Int): Int {
            val p = a + b - c
            val pa = kotlin.math.abs(p - a)
            val pb = kotlin.math.abs(p - b)
            val pc = kotlin.math.abs(p - c)
            return when {
                pa <= pb && pa <= pc -> a
                pb <= pc -> b
                else -> c
            }
        }
    }
}
