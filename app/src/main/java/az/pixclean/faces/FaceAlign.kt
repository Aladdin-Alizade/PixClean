package az.pixclean.faces

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Warps a detected face into the 112x112 frame every face-recognition model expects.
 *
 * Alignment is the single biggest accuracy lever after the model itself: an unaligned
 * crop of the same person at two head angles produces embeddings that look like two
 * different people. We fit a similarity transform (rotate + uniform scale + translate)
 * that puts both eyes on the ArcFace reference positions.
 */
object FaceAlign {

    const val SIZE = 112

    // ArcFace 5-point template, eyes only. Index 0 is the eye on the *image* left.
    private const val L_X = 38.2946f
    private const val L_Y = 51.6963f
    private const val R_X = 73.5318f
    private const val R_Y = 51.5014f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun align(source: Bitmap, box: Rect, eyeA: PointF?, eyeB: PointF?, size: Int = SIZE): Bitmap? {
        val out = try {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return null
        }
        val canvas = Canvas(out)
        // The template is defined at 112 px; scaling it keeps the same framing at any size,
        // which beats aligning to 112 and upscaling into a 160 px model.
        val k = size / SIZE.toFloat()
        val matrix = if (eyeA != null && eyeB != null) eyeMatrix(eyeA, eyeB, k) else boxMatrix(box, k)
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    private fun eyeMatrix(a: PointF, b: PointF, k: Float): Matrix {
        // Order by x so the transform never flips the face upside down, whatever the
        // detector calls "left".
        val left = if (a.x <= b.x) a else b
        val right = if (a.x <= b.x) b else a

        val srcDist = hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble()).toFloat()
        if (srcDist < 1f) {
            return boxMatrix(
                Rect(left.x.toInt() - 40, left.y.toInt() - 40, left.x.toInt() + 40, left.y.toInt() + 40), k
            )
        }

        val dstDist = (R_X - L_X) * k
        val scale = dstDist / srcDist
        val angle = Math.toDegrees(atan2((right.y - left.y).toDouble(), (right.x - left.x).toDouble())).toFloat()

        val srcCx = (left.x + right.x) / 2f
        val srcCy = (left.y + right.y) / 2f
        val dstCx = (L_X + R_X) / 2f * k
        val dstCy = (L_Y + R_Y) / 2f * k

        return Matrix().apply {
            postTranslate(-srcCx, -srcCy)
            postRotate(-angle)
            postScale(scale, scale)
            postTranslate(dstCx, dstCy)
        }
    }

    /** No landmarks: centre the box with a margin, which is what the eye fit would roughly do. */
    private fun boxMatrix(box: Rect, k: Float): Matrix {
        val side = max(box.width(), box.height()) * 1.35f
        val cx = box.exactCenterX()
        val cy = box.exactCenterY() - box.height() * 0.05f
        val scale = SIZE * k / side
        return Matrix().apply {
            postTranslate(-cx, -cy)
            postScale(scale, scale)
            postTranslate(SIZE * k / 2f, SIZE * k / 2f)
        }
    }
}
