package az.pixclean.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/** One row of MediaStore, plus every signature we have computed for it. */
class Photo(
    val id: Long,
    val name: String,
    val bucket: String,
    val relPath: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val mime: String,
    val sha: String? = null,
    val dHash: Long = 0L,
    val pHash: Long = 0L,
    val colorSig: ByteArray? = null,
    val sigVersion: Int = 0,
    /** Set for files found by walking a folder the user picked, rather than via MediaStore. */
    val docUri: String? = null,
) {
    val uri: Uri
        get() = docUri?.let(Uri::parse)
            ?: ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    val fromFolder: Boolean get() = docUri != null
    val pixels: Long get() = width.toLong() * height.toLong()
    val hasSignature: Boolean get() = sigVersion >= Signatures.VERSION && colorSig != null

    fun copy(
        sha: String? = this.sha,
        dHash: Long = this.dHash,
        pHash: Long = this.pHash,
        colorSig: ByteArray? = this.colorSig,
        sigVersion: Int = this.sigVersion,
    ) = Photo(
        id, name, bucket, relPath, size, width, height, dateAdded, dateModified, mime,
        sha, dHash, pHash, colorSig, sigVersion, docUri
    )

    override fun toString() = "Photo($id, $name, ${size}B, ${width}x$height)"
}

object Signatures {
    /** Bump when the perceptual-hash maths changes so old rows get recomputed. */
    const val VERSION = 1
}

/** A set of photos the app believes are the same picture. */
class PhotoGroup(
    val kind: GroupKind,
    val keeper: Photo,
    /** Members other than the keeper, ordered best-first. */
    val others: List<Member>,
) {
    val size: Int get() = others.size + 1

    /** Bytes that would be freed by removing every non-keeper. */
    val reclaimable: Long get() = others.sumOf { it.photo.size }

    class Member(val photo: Photo, val distance: Int)
}

enum class GroupKind { EXACT, SIMILAR }

/** One detected face, with its embedding and cluster assignment. */
class FaceRow(
    val faceId: Long,
    val photoId: Long,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val quality: Float,
    val embedding: FloatArray?,
    val clusterId: Int,
    val personId: Long,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

class PersonCluster(
    val clusterId: Int,
    val personId: Long,
    val name: String?,
    val coverFace: FaceRow,
    val faces: List<FaceRow>,
) {
    val photoIds: List<Long> get() = faces.map { it.photoId }.distinct()
    val displayName: String get() = name ?: "Adsız"
}
