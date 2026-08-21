package az.pixclean.dup

import android.content.ContentResolver
import android.graphics.Bitmap
import az.pixclean.core.Bitmaps
import az.pixclean.data.Db
import az.pixclean.data.Photo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Three complementary signatures per photo:
 *
 *  - pHash  (64 bit) DCT of a 32x32 luma plane. Survives re-compression, resizing and
 *           brightness shifts, which is exactly what messaging apps do to a photo.
 *  - dHash  (64 bit) adjacent-pixel gradient of a 9x8 luma plane. Fails differently from
 *           pHash, so requiring both to agree kills most false positives.
 *  - colour (48 byte) 4x4 grid of mean RGB. Mean-subtracted at compare time, so it checks
 *           *layout* of colour rather than absolute colour, and rejects the classic
 *           "two unrelated dark photos have similar hashes" failure.
 */
object Signature {

    private const val N = 32           // pHash working plane
    private const val DCT_KEEP = 8     // top-left DCT block used for the hash
    private const val GRID = 4         // colour signature grid

    /** cos table for a separable DCT-II; built once. */
    private val COS: Array<FloatArray> = Array(N) { k ->
        FloatArray(N) { n -> cos(Math.PI / N * (n + 0.5) * k).toFloat() }
    }

    fun compute(resolver: ContentResolver, photo: Photo): Db.SigRow? {
        val bmp = decodeSmall(resolver, photo) ?: return null
        try {
            val plane = Bitmap.createScaledBitmap(bmp, N, N, true)
            val gray = FloatArray(N * N)
            val px = IntArray(N * N)
            plane.getPixels(px, 0, N, 0, 0, N, N)
            for (i in px.indices) gray[i] = luma(px[i])

            val pHash = pHashOf(gray)
            val colorSig = colorSigOf(px)

            val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
            val dHash = dHashOf(small)

            if (plane !== bmp) plane.recycle()
            if (small !== bmp) small.recycle()
            return Db.SigRow(photo.id, dHash, pHash, colorSig)
        } catch (_: Exception) {
            return null
        } finally {
            bmp.recycle()
        }
    }

    // ------------------------------------------------------------------ decode

    /**
     * ~96 px on the short side: small enough that thousands of photos are cheap, large
     * enough that the 32x32 downscale is not aliasing garbage.
     */
    private fun decodeSmall(resolver: ContentResolver, photo: Photo): Bitmap? =
        Bitmaps.decode(resolver, photo.uri, photo.width, photo.height, minShortSide = 96)

    // ------------------------------------------------------------------ hashes

    private fun luma(p: Int): Float {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return (r * 0.299f + g * 0.587f + b * 0.114f)
    }

    private fun pHashOf(gray: FloatArray): Long {
        // Separable DCT-II: rows, then columns. 2*N^3 ops instead of N^4.
        val tmp = FloatArray(N * N)
        for (y in 0 until N) {
            val row = y * N
            for (k in 0 until DCT_KEEP) {
                var s = 0f
                val ck = COS[k]
                for (x in 0 until N) s += gray[row + x] * ck[x]
                tmp[row + k] = s
            }
        }
        val block = FloatArray(DCT_KEEP * DCT_KEEP)
        for (k in 0 until DCT_KEEP) {
            val ck = COS[k]
            for (u in 0 until DCT_KEEP) {
                var s = 0f
                for (y in 0 until N) s += tmp[y * N + u] * ck[y]
                block[k * DCT_KEEP + u] = s
            }
        }

        // Median over the block excluding DC, which carries only overall brightness.
        val vals = FloatArray(block.size - 1)
        var j = 0
        for (i in block.indices) if (i != 0) vals[j++] = block[i]
        vals.sort()
        val median = (vals[vals.size / 2 - 1] + vals[vals.size / 2]) / 2f

        var bits = 0L
        for (i in block.indices) {
            if (i != 0 && block[i] > median) bits = bits or (1L shl i)
        }
        return bits
    }

    private fun dHashOf(small: Bitmap): Long {
        val px = IntArray(9 * 8)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        var bits = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val a = luma(px[y * 9 + x])
                val b = luma(px[y * 9 + x + 1])
                if (a > b) bits = bits or (1L shl bit)
                bit++
            }
        }
        return bits
    }

    private fun colorSigOf(px: IntArray): ByteArray {
        val cell = N / GRID
        val out = ByteArray(GRID * GRID * 3)
        var o = 0
        for (gy in 0 until GRID) {
            for (gx in 0 until GRID) {
                var r = 0; var g = 0; var b = 0
                for (y in 0 until cell) {
                    val row = (gy * cell + y) * N + gx * cell
                    for (x in 0 until cell) {
                        val p = px[row + x]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                    }
                }
                val n = cell * cell
                out[o++] = (r / n).toByte()
                out[o++] = (g / n).toByte()
                out[o++] = (b / n).toByte()
            }
        }
        return out
    }

    // ----------------------------------------------------------------- compare

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Mean-subtracted L1 distance between two colour grids, per channel. Removes any
     * global exposure/white-balance difference and compares the arrangement of colour.
     * Returns 0..255-ish; identical layout scores near 0.
     */
    fun colorDistance(a: ByteArray, b: ByteArray): Int {
        if (a.size != b.size) return Int.MAX_VALUE
        var total = 0
        for (ch in 0 until 3) {
            var sa = 0; var sb = 0
            var i = ch
            while (i < a.size) { sa += a[i].toInt() and 0xFF; sb += b[i].toInt() and 0xFF; i += 3 }
            val n = a.size / 3
            val ma = sa.toFloat() / n
            val mb = sb.toFloat() / n
            var d = 0f
            i = ch
            while (i < a.size) {
                d += abs(((a[i].toInt() and 0xFF) - ma) - ((b[i].toInt() and 0xFF) - mb))
                i += 3
            }
            total += (d / n).roundToInt()
        }
        return total / 3
    }

    /** Spread of a colour grid; low spread means a flat image whose hashes mean little. */
    fun colorSpread(a: ByteArray): Int {
        var min = 255; var max = 0
        for (v in a) {
            val x = v.toInt() and 0xFF
            if (x < min) min = x
            if (x > max) max = x
        }
        return max - min
    }
}
