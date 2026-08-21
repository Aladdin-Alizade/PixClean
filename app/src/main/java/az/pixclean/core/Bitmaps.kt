package az.pixclean.core

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object Bitmaps {

    /**
     * Decodes with the cheapest inSampleSize that still leaves [minShortSide] pixels on the
     * short edge, then caps the long edge at [maxLongSide]. EXIF rotation is applied so a
     * photo rotated by flag and the same photo rotated in place look identical to us.
     */
    fun decode(
        resolver: ContentResolver,
        uri: Uri,
        knownW: Int,
        knownH: Int,
        minShortSide: Int,
        maxLongSide: Int = Int.MAX_VALUE,
    ): Bitmap? {
        var w = knownW
        var h = knownH
        if (w <= 0 || h <= 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (!open(resolver, uri) { BitmapFactory.decodeStream(it, null, bounds) }) return null
            w = bounds.outWidth; h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
        }

        var sample = 1
        while (minOf(w, h) / (sample * 2) >= minShortSide && maxOf(w, h) / (sample * 2) >= 1) sample *= 2
        while (maxOf(w, h) / sample > maxLongSide) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp: Bitmap? = null
        try {
            resolver.openInputStream(uri)?.use { bmp = BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        }
        val raw = bmp ?: return null

        val deg = orientationDegrees(resolver, uri)
        if (deg == 0) return raw
        return try {
            val m = Matrix().apply { postRotate(deg.toFloat()) }
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            if (rotated !== raw) raw.recycle()
            rotated
        } catch (_: Exception) {
            raw
        } catch (_: OutOfMemoryError) {
            raw
        }
    }

    fun orientationDegrees(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }

    private inline fun open(resolver: ContentResolver, uri: Uri, body: (java.io.InputStream) -> Unit): Boolean = try {
        resolver.openInputStream(uri)?.use(body) != null
    } catch (_: Exception) {
        false
    }
}
