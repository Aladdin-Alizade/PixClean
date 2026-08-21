package az.pixclean.faces

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import az.pixclean.core.Bitmaps
import az.pixclean.data.Db
import az.pixclean.data.Photo
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.min

/**
 * Detect -> gate -> align -> embed, for one photo.
 *
 * Boxes are stored normalised to 0..10000 of the EXIF-corrected image, so the UI can crop
 * a thumbnail at whatever resolution it likes without re-running anything.
 */
/** How much of the original resolution the detector gets to look at. */
enum class FaceDetail(val label: String, val longSide: Int, val shortSide: Int) {
    NORMAL("Normal", 1280, 480),
    HIGH("Yüksək", 1920, 720),
}

class FaceScanner(
    private val embedder: FaceEmbedder,
    private val detail: FaceDetail = FaceDetail.NORMAL,
) : AutoCloseable {

    companion object {
        const val NORM = 10_000

        /** Below this many pixels in the analysis frame the face has no usable detail. */
        private const val MIN_FACE_PX = 40
        private const val MAX_YAW = 45f
        private const val MAX_ROLL = 30f

    }

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.06f)
            .build()
    )

    fun scan(resolver: ContentResolver, photo: Photo): List<Db.DetectedFaceRow>? {
        val bmp = Bitmaps.decode(
            resolver, photo.uri, photo.width, photo.height,
            minShortSide = detail.shortSide, maxLongSide = detail.longSide
        ) ?: return null

        return try {
            val faces = Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
            val out = ArrayList<Db.DetectedFaceRow>(faces.size)
            for (face in faces) {
                val row = process(bmp, face) ?: continue
                out.add(row)
            }
            out
        } catch (_: Exception) {
            null
        } finally {
            bmp.recycle()
        }
    }

    private fun process(bmp: Bitmap, face: Face): Db.DetectedFaceRow? {
        val box = face.boundingBox
        val clipped = Rect(
            box.left.coerceIn(0, bmp.width), box.top.coerceIn(0, bmp.height),
            box.right.coerceIn(0, bmp.width), box.bottom.coerceIn(0, bmp.height)
        )
        if (clipped.width() < MIN_FACE_PX || clipped.height() < MIN_FACE_PX) return null

        val yaw = face.headEulerAngleY
        val roll = face.headEulerAngleZ
        if (abs(yaw) > MAX_YAW || abs(roll) > MAX_ROLL) return null

        val eyeA = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val eyeB = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val aligned = FaceAlign.align(
            bmp, clipped,
            eyeA?.let { PointF(it.x, it.y) },
            eyeB?.let { PointF(it.x, it.y) },
            size = embedder.inputSize,
        ) ?: return null

        val embedding = try {
            embedder.embed(aligned)
        } finally {
            aligned.recycle()
        } ?: return null

        val relSize = min(clipped.width(), clipped.height()).toFloat() / min(bmp.width, bmp.height)
        val hasEyes = eyeA != null && eyeB != null
        val quality = (min(relSize / 0.35f, 1f) * 0.45f) +
            ((1f - abs(yaw) / MAX_YAW) * 0.25f) +
            ((1f - abs(roll) / MAX_ROLL) * 0.15f) +
            (if (hasEyes) 0.15f else 0f)

        // Stored as a *square* box with a little headroom. Keeping it square in pixel space
        // means the UI can crop a round avatar from any photo without knowing its aspect.
        val side = (maxOf(clipped.width(), clipped.height()) * 1.28f).toInt()
        val cx = clipped.centerX()
        val cy = clipped.centerY() - (clipped.height() * 0.04f).toInt()
        val sq = Rect(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        sq.offset(
            -minOf(0, sq.left) - maxOf(0, sq.right - bmp.width),
            -minOf(0, sq.top) - maxOf(0, sq.bottom - bmp.height),
        )
        sq.set(
            sq.left.coerceIn(0, bmp.width), sq.top.coerceIn(0, bmp.height),
            sq.right.coerceIn(0, bmp.width), sq.bottom.coerceIn(0, bmp.height),
        )

        return Db.DetectedFaceRow(
            left = sq.left * NORM / bmp.width,
            top = sq.top * NORM / bmp.height,
            right = sq.right * NORM / bmp.width,
            bottom = sq.bottom * NORM / bmp.height,
            quality = quality,
            embedding = embedding,
        )
    }

    override fun close() {
        runCatching { detector.close() }
    }
}
