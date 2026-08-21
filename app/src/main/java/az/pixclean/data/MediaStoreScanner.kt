package az.pixclean.data

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.io.File

/** Reads the device photo index. Cheap: one cursor pass, no file I/O. */
object MediaStoreScanner {

    private val PROJECTION = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATA,
    )

    private val storageRoot: String by lazy {
        android.os.Environment.getExternalStorageDirectory().absolutePath
    }

    private fun folderLabel(data: String): String {
        val parent = File(data).parent ?: return "Digər"
        return if (parent == storageRoot) "Daxili yaddaş" else File(parent).name.ifBlank { "Digər" }
    }

    fun scan(context: Context): List<Photo> {
        val resolver: ContentResolver = context.contentResolver
        val out = ArrayList<Photo>(4096)
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Images.Media.SIZE} > 0")
            }
            resolver.query(uri, PROJECTION, args, null)
        } else {
            resolver.query(uri, PROJECTION, "${MediaStore.Images.Media.SIZE} > 0", null, null)
        } ?: return out

        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val iData = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            val seenPaths = HashSet<String>(c.count * 2)
            while (c.moveToNext()) {
                val data = c.getString(iData) ?: ""
                // Some devices expose the same file under two rows; keep the first.
                if (data.isNotEmpty() && !seenPaths.add(data)) continue
                out.add(
                    Photo(
                        id = c.getLong(iId),
                        name = c.getString(iName) ?: File(data).name,
                        // Some rows carry no album name; fall back to the containing folder,
                        // and name the storage root rather than showing its literal "0".
                        bucket = c.getString(iBucket) ?: folderLabel(data),
                        relPath = data,
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        dateAdded = c.getLong(iAdded),
                        dateModified = c.getLong(iMod),
                        dateTaken = if (c.isNull(iTaken)) 0L else c.getLong(iTaken),
                        mime = c.getString(iMime) ?: "image/*",
                    )
                )
            }
        }
        return out
    }
}
