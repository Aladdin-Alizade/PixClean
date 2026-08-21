package az.pixclean.faces

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import az.pixclean.dup.SimilarityLevel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Turns an aligned 112x112 face into a vector where "same person" means "high cosine".
 *
 * Two implementations. [TFLiteEmbedder] runs a real face-recognition network and is what
 * you want; [HogEmbedder] is a classical descriptor that ships with the app so the feature
 * works out of the box, at clearly lower accuracy. Which one is live is surfaced in the UI
 * rather than hidden, because the difference matters for how much you trust a group.
 */
interface FaceEmbedder {
    val version: Int
    val displayName: String
    val isModelBacked: Boolean
    val dim: Int

    /** Side length the aligner should render, so no rescaling step degrades the crop. */
    val inputSize: Int
    fun threshold(level: SimilarityLevel): Float
    fun embed(aligned: Bitmap): FloatArray?
    fun close()
}

object FaceEmbedders {

    const val MODEL_FILE = "face.tflite"
    private const val TAG = "FaceEmbedders"

    fun modelPath(context: Context) = File(File(context.filesDir, "models"), MODEL_FILE)

    /** The model shipped inside the APK. Present on every install; nothing to set up. */
    fun hasBundledModel(context: Context): Boolean = try {
        context.assets.list("")?.contains(MODEL_FILE) == true
    } catch (_: Exception) {
        false
    }

    /** A model the user chose themselves, which takes precedence over the bundled one. */
    fun hasImportedModel(context: Context): Boolean =
        modelPath(context).let { it.exists() && it.length() > 1024 }

    fun hasModel(context: Context): Boolean = hasImportedModel(context) || hasBundledModel(context)

    /**
     * Loads the file as a model and checks its tensors make sense for a face embedder.
     * Anything can be handed to a file picker, so "the copy succeeded" is not evidence the
     * import worked — only actually running the interpreter is.
     *
     * @return null when the file is usable, otherwise a message to show the user.
     */
    fun validate(file: File): String? {
        if (!file.exists() || file.length() < 1024) return "Fayl boşdur və ya oxunmur"
        val embedder = try {
            TFLiteEmbedder(mapFile(file), file.length())
        } catch (e: Throwable) {
            return "Bu fayl üz tanıma modeli deyil"
        }
        return try {
            if (embedder.dim !in 32..2048) {
                "Bu fayl üz tanıma modelinə oxşamır"
            } else null
        } finally {
            runCatching { embedder.close() }
        }
    }

    fun create(context: Context): FaceEmbedder {
        val imported = modelPath(context)
        if (imported.exists() && imported.length() > 1024) {
            runCatching { return TFLiteEmbedder(mapFile(imported), imported.length()) }
                .onFailure { Log.w(TAG, "imported model unusable, falling back", it) }
        }
        if (hasBundledModel(context)) {
            runCatching {
                val afd = context.assets.openFd(MODEL_FILE)
                val buffer = FileInputStream(afd.fileDescriptor).channel
                    .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                return TFLiteEmbedder(buffer, afd.declaredLength)
            }.onFailure { Log.w(TAG, "asset model unusable, falling back", it) }
        }
        return HogEmbedder()
    }

    private fun mapFile(f: File): MappedByteBuffer =
        FileInputStream(f).channel.use { it.map(FileChannel.MapMode.READ_ONLY, 0, f.length()) }

    fun l2Normalize(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s)
        if (n < 1e-8f) return v
        for (i in v.indices) v[i] /= n
        return v
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return -1f
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}

// --------------------------------------------------------------------- TFLite

class TFLiteEmbedder(model: MappedByteBuffer, modelLength: Long) : FaceEmbedder {

    private val interpreter = Interpreter(model, Interpreter.Options().apply {
        numThreads = 2
        setUseXNNPACK(true)
    })

    private val inH: Int
    private val inW: Int
    private val quantizedIn: Boolean
    private val outDim: Int
    private val quantizedOut: Boolean
    private val outScale: Float
    private val outZero: Int
    private val inBuffer: ByteBuffer
    private val pixels: IntArray
    private val normalization: Normalization

    init {
        val inT = interpreter.getInputTensor(0)
        val shape = inT.shape()
        inH = if (shape.size >= 3) shape[1] else FaceAlign.SIZE
        inW = if (shape.size >= 3) shape[2] else FaceAlign.SIZE
        quantizedIn = inT.dataType() == DataType.UINT8

        val outT = interpreter.getOutputTensor(0)
        outDim = outT.shape().last()
        quantizedOut = outT.dataType() == DataType.UINT8
        outScale = outT.quantizationParams().scale.takeIf { it != 0f } ?: 1f
        outZero = outT.quantizationParams().zeroPoint

        // Two conventions dominate and they are not interchangeable: ArcFace / MobileFaceNet
        // exports expect (x - 127.5) / 128 at 112x112, while FaceNet exports expect per-image
        // standardisation at 160x160. Feeding a model the wrong one produces embeddings that
        // look valid and cluster badly, which is the worst kind of failure — so pick by the
        // input size the model itself declares.
        normalization = when {
            quantizedIn -> Normalization.RAW
            inH >= 150 -> Normalization.PER_IMAGE_STANDARD
            else -> Normalization.FIXED_SCALE
        }

        val bytesPerChannel = if (quantizedIn) 1 else 4
        inBuffer = ByteBuffer.allocateDirect(inH * inW * 3 * bytesPerChannel).order(ByteOrder.nativeOrder())
        pixels = IntArray(inH * inW)
    }

    override val version: Int = 1000 + (modelLength % 900_000L).toInt() + outDim
    override val displayName: String = "Tam üz tanıma modeli"
    override val isModelBacked: Boolean = true
    override val dim: Int = outDim
    override val inputSize: Int = inW

    private enum class Normalization(val label: String) {
        FIXED_SCALE("ArcFace"),
        PER_IMAGE_STANDARD("FaceNet"),
        RAW("uint8"),
    }

    override fun threshold(level: SimilarityLevel): Float = when (level) {
        SimilarityLevel.STRICT -> 0.72f
        SimilarityLevel.BALANCED -> 0.62f
        SimilarityLevel.LOOSE -> 0.52f
    }

    @Synchronized
    override fun embed(aligned: Bitmap): FloatArray? = try {
        val src = if (aligned.width == inW && aligned.height == inH) aligned
        else Bitmap.createScaledBitmap(aligned, inW, inH, true)
        src.getPixels(pixels, 0, inW, 0, 0, inW, inH)

        inBuffer.rewind()
        when (normalization) {
            Normalization.RAW -> for (p in pixels) {
                inBuffer.put(((p shr 16) and 0xFF).toByte())
                inBuffer.put(((p shr 8) and 0xFF).toByte())
                inBuffer.put((p and 0xFF).toByte())
            }

            Normalization.FIXED_SCALE -> for (p in pixels) {
                inBuffer.putFloat((((p shr 16) and 0xFF) - 127.5f) / 128f)
                inBuffer.putFloat((((p shr 8) and 0xFF) - 127.5f) / 128f)
                inBuffer.putFloat(((p and 0xFF) - 127.5f) / 128f)
            }

            Normalization.PER_IMAGE_STANDARD -> {
                var sum = 0.0
                var sumSq = 0.0
                for (p in pixels) {
                    for (sh in intArrayOf(16, 8, 0)) {
                        val v = ((p shr sh) and 0xFF).toDouble()
                        sum += v; sumSq += v * v
                    }
                }
                val n = pixels.size * 3.0
                val mean = sum / n
                val std = kotlin.math.sqrt((sumSq / n - mean * mean).coerceAtLeast(0.0))
                // The floor is what the reference implementation uses; it stops a flat crop
                // from being amplified into noise.
                val denom = kotlin.math.max(std, 1.0 / kotlin.math.sqrt(n)).toFloat()
                val m = mean.toFloat()
                for (p in pixels) {
                    inBuffer.putFloat((((p shr 16) and 0xFF) - m) / denom)
                    inBuffer.putFloat((((p shr 8) and 0xFF) - m) / denom)
                    inBuffer.putFloat(((p and 0xFF) - m) / denom)
                }
            }
        }
        inBuffer.rewind()
        if (src !== aligned) src.recycle()

        val out: FloatArray
        if (quantizedOut) {
            val raw = Array(1) { ByteArray(outDim) }
            interpreter.run(inBuffer, raw)
            out = FloatArray(outDim) { ((raw[0][it].toInt() and 0xFF) - outZero) * outScale }
        } else {
            val raw = Array(1) { FloatArray(outDim) }
            interpreter.run(inBuffer, raw)
            out = raw[0]
        }
        FaceEmbedders.l2Normalize(out)
    } catch (_: Exception) {
        null
    }

    @Synchronized
    override fun close() = interpreter.close()
}

// ------------------------------------------------------------------- fallback

/**
 * Histogram of oriented gradients over the aligned face. Nothing learned, so it keys on
 * shape and texture rather than identity: reliable for the same person photographed under
 * similar conditions (bursts, one event), unreliable across years and lighting. Good enough
 * to be useful with zero setup, and honest about being the lesser option.
 */
class HogEmbedder : FaceEmbedder {

    private companion object {
        const val N = 64        // working plane
        const val CELL = 8      // 8x8 cells
        const val BINS = 8
        const val CELLS = N / CELL
    }

    override val version: Int = 1
    override val displayName: String = "Sadə rejim"
    override val isModelBacked: Boolean = false
    override val dim: Int = (CELLS - 1) * (CELLS - 1) * BINS * 4
    override val inputSize: Int = FaceAlign.SIZE

    override fun threshold(level: SimilarityLevel): Float = when (level) {
        SimilarityLevel.STRICT -> 0.88f
        SimilarityLevel.BALANCED -> 0.82f
        SimilarityLevel.LOOSE -> 0.75f
    }

    override fun embed(aligned: Bitmap): FloatArray? = try {
        val small = Bitmap.createScaledBitmap(aligned, N, N, true)
        val px = IntArray(N * N)
        small.getPixels(px, 0, N, 0, 0, N, N)
        if (small !== aligned) small.recycle()

        val gray = FloatArray(N * N)
        for (i in px.indices) {
            val p = px[i]
            gray[i] = ((p shr 16 and 0xFF) * 0.299f + (p shr 8 and 0xFF) * 0.587f + (p and 0xFF) * 0.114f)
        }

        val cells = Array(CELLS) { Array(CELLS) { FloatArray(BINS) } }
        for (y in 1 until N - 1) {
            for (x in 1 until N - 1) {
                val gx = gray[y * N + x + 1] - gray[y * N + x - 1]
                val gy = gray[(y + 1) * N + x] - gray[(y - 1) * N + x]
                val mag = sqrt(gx * gx + gy * gy)
                if (mag < 1e-4f) continue
                var ang = atan2(gy, gx).toFloat()          // -PI..PI
                if (ang < 0) ang += Math.PI.toFloat()      // unsigned orientation, 0..PI
                val bin = ((ang / Math.PI.toFloat()) * BINS).toInt().coerceIn(0, BINS - 1)
                cells[y / CELL][x / CELL][bin] += mag
            }
        }

        // 2x2 block normalisation makes the descriptor tolerant of local contrast changes.
        val out = FloatArray(dim)
        var o = 0
        for (by in 0 until CELLS - 1) {
            for (bx in 0 until CELLS - 1) {
                var norm = 0f
                for (dy in 0..1) for (dx in 0..1) for (k in 0 until BINS) {
                    val v = cells[by + dy][bx + dx][k]; norm += v * v
                }
                val inv = 1f / sqrt(norm + 1e-6f)
                for (dy in 0..1) for (dx in 0..1) for (k in 0 until BINS) {
                    out[o++] = cells[by + dy][bx + dx][k] * inv
                }
            }
        }
        FaceEmbedders.l2Normalize(out)
    } catch (_: Exception) {
        null
    }

    override fun close() {}
}
