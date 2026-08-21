package az.pixclean.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Walks a folder the user picked with the system folder chooser.
 *
 * MediaStore already indexes most of shared storage, so this exists for the rest: folders
 * with a .nomedia marker, freshly copied directories the media scanner has not reached, and
 * removable volumes. Anything MediaStore already knows about is skipped here, otherwise one
 * file would show up twice and the app would call a photo its own duplicate.
 */
object FolderScanner {

    private const val MAX_FILES = 60_000
    private const val MAX_DEPTH = 12

    private val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "heic", "heif", "gif", "bmp", "tif", "tiff", "webp",
        "dng", "raw", "cr2", "cr3", "nef", "arw", "orf", "rw2", "avif",
    )

    /** Best-effort absolute path for a tree uri, used to filter MediaStore by prefix. */
    fun treePath(treeUri: Uri): String? = try {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        pathOfDocId(docId)
    } catch (_: Exception) {
        null
    }

    fun label(treeUri: Uri): String =
        treePath(treeUri)?.substringAfterLast('/')
            ?: runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast('/') }
                .getOrDefault("Qovluq")

    private fun pathOfDocId(docId: String): String? {
        val parts = docId.split(':', limit = 2)
        if (parts.isEmpty()) return null
        val volume = parts[0]
        val rest = parts.getOrElse(1) { "" }
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
        return (if (rest.isEmpty()) base else "$base/$rest").trimEnd('/')
    }

    class Known(paths: Collection<String>, nameSizes: Collection<String>) {
        val paths: Set<String> = paths.toHashSet()
        val nameSizes: Set<String> = nameSizes.toHashSet()
    }

    fun known(photos: List<Photo>) = Known(
        photos.map { it.relPath },
        photos.map { "${it.name}|${it.size}" },
    )

    fun scan(context: Context, treeUri: Uri, known: Known): List<Photo> {
        val resolver = context.contentResolver
        val out = ArrayList<Photo>()
        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            Log.w(TAG, "bad tree uri " + treeUri, e)
            return out
        }
        val bucketLabel = label(treeUri)

        var seenRows = 0
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        while (queue.isNotEmpty() && out.size < MAX_FILES) {
            val (parentId, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val cursor = try {
                resolver.query(children, projection, null, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "cannot list " + parentId, e)
                null
            } ?: continue

            cursor.use { c ->
                while (c.moveToNext() && out.size < MAX_FILES) {
                    seenRows++
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH && !name.startsWith(".")) queue.add(docId to depth + 1)
                        continue
                    }
                    if (name.startsWith(".")) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!mime.startsWith("image/") && ext !in IMAGE_EXT) continue

                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    if (size <= 0) continue

                    // Skip only what MediaStore already has. When the real path is known that
                    // is an exact test; name+size is the fallback for volumes where it is not,
                    // and must never run otherwise — two folders legitimately holding a file of
                    // the same name and size is precisely the duplicate this app exists to find.
                    val path = pathOfDocId(docId)
                    if (path != null) {
                        if (path in known.paths) continue
                    } else if ("$name|$size" in known.nameSizes) {
                        continue
                    }

                    val modified = if (c.isNull(4)) 0L else c.getLong(4) / 1000L
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                    out.add(
                        Photo(
                            id = syntheticId(docUri),
                            name = name,
                            bucket = path?.let { File(it).parentFile?.name } ?: bucketLabel,
                            relPath = path ?: docUri,
                            size = size,
                            width = 0,
                            height = 0,
                            dateAdded = modified,
                            dateModified = modified,
                            mime = if (mime.startsWith("image/")) mime else "image/*",
                            docUri = docUri,
                        )
                    )
                }
            }
        }
        Log.d(TAG, "walked $treeUri: $seenRows entries, ${out.size} new image(s)")
        return out
    }

    private const val TAG = "FolderScanner"

    /**
     * A stable 64-bit id derived from the document uri, with bit 62 set so it can never
     * collide with a MediaStore row id.
     */
    private fun syntheticId(docUri: String): Long {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64 offset basis
        for (ch in docUri) {
            h = h xor ch.code.toLong()
            h *= 0x100000001b3L
        }
        return (h and 0x3FFF_FFFF_FFFFL) or (1L shl 62)
    }
}
