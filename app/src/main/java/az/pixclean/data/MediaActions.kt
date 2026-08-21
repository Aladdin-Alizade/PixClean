package az.pixclean.data

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import az.pixclean.core.ConsentBroker
import java.io.File

/**
 * Removing and relocating photos. Everything destructive goes through the platform
 * confirmation dialog, and the default is the recoverable bin rather than a hard delete,
 * because a duplicate finder that is wrong once should not cost you a photo.
 */
object MediaActions {

    class Outcome(val succeeded: List<Long>, val freedBytes: Long, val message: String)

    suspend fun remove(
        context: Context,
        photos: List<Photo>,
        toTrash: Boolean,
        broker: ConsentBroker,
    ): Outcome {
        if (photos.isEmpty()) return Outcome(emptyList(), 0, "Seçim boşdur")
        val resolver = context.contentResolver

        // Files found by walking a picked folder are not MediaStore rows: we already hold write
        // access to their tree, so they are deleted directly — but there is no system bin for
        // them, so the wording has to say "permanently" rather than imply an undo that is not there.
        val (folder, gallery) = photos.partition { it.fromFolder }
        val folderOk = ArrayList<Long>()
        var folderFreed = 0L
        for (p in folder) {
            val gone = try {
                DocumentsContract.deleteDocument(resolver, p.uri)
            } catch (_: Exception) {
                false
            }
            if (gone) { folderOk.add(p.id); folderFreed += p.size }
        }
        val folderNote = if (folderOk.isEmpty()) "" else " · ${folderOk.size} fayl qovluqdan həmişəlik silindi"

        if (gallery.isEmpty()) {
            return Outcome(folderOk, folderFreed, "${folderOk.size} şəkil silindi".trim())
        }
        val uris = gallery.map { it.uri }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = if (toTrash) {
                MediaStore.createTrashRequest(resolver, uris, true).intentSender
            } else {
                MediaStore.createDeleteRequest(resolver, uris).intentSender
            }
            val granted = broker.ask(sender)
            return if (granted) {
                Outcome(
                    folderOk + gallery.map { it.id },
                    folderFreed + gallery.sumOf { it.size },
                    (if (toTrash) "${gallery.size} şəkil zibil qutusuna atıldı"
                    else "${gallery.size} şəkil silindi") + folderNote,
                )
            } else {
                Outcome(folderOk, folderFreed, "Ləğv edildi$folderNote")
            }
        }

        // API 29 and below: one file at a time, asking only when the system demands it.
        val ok = ArrayList<Long>(folderOk)
        var freed = folderFreed
        for (p in gallery) {
            val deleted = try {
                resolver.delete(p.uri, null, null) > 0
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    if (broker.ask(e.userAction.actionIntent.intentSender)) {
                        runCatching { resolver.delete(p.uri, null, null) > 0 }.getOrDefault(false)
                    } else false
                } else false
            }
            if (deleted) { ok.add(p.id); freed += p.size }
        }
        return Outcome(ok, freed, "${ok.size} şəkil silindi$folderNote")
    }

    /**
     * Moves photos into Pictures/<album>. On API 30+ this is a real move: MediaStore
     * relocates the file when RELATIVE_PATH changes, so it costs no extra storage. Older
     * versions fall back to copy-then-delete.
     */
    suspend fun moveToAlbum(
        context: Context,
        photos: List<Photo>,
        album: String,
        broker: ConsentBroker,
    ): Outcome {
        if (photos.isEmpty()) return Outcome(emptyList(), 0, "Seçim boşdur")
        val safeAlbum = album.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "PixClean" }
        val relative = "${Environment.DIRECTORY_PICTURES}/$safeAlbum/"

        // Folder-backed files are not MediaStore rows, so the write-request path does not apply
        // to them; they are copied into the album and then removed through their own document uri.
        val (folder, gallery) = photos.partition { it.fromFolder }
        val folderMoved = if (folder.isEmpty()) emptyList()
        else copyThenDelete(context, folder, safeAlbum, relative, broker).succeeded
        if (gallery.isEmpty()) {
            return Outcome(folderMoved, 0, "${folderMoved.size} şəkil «$safeAlbum» albomuna köçürüldü")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val granted = broker.ask(
                MediaStore.createWriteRequest(context.contentResolver, gallery.map { it.uri }).intentSender
            )
            if (!granted) return Outcome(folderMoved, 0, "Ləğv edildi")
            val moved = relocate(context, gallery, relative)
            if (moved.isNotEmpty()) {
                val total = folderMoved + moved
                return Outcome(total, 0, "${total.size} şəkil «$safeAlbum» albomuna köçürüldü")
            }
        }
        val rest = copyThenDelete(context, gallery, safeAlbum, relative, broker)
        val total = folderMoved + rest.succeeded
        return Outcome(total, 0, "${total.size} şəkil «$safeAlbum» albomuna köçürüldü")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun relocate(context: Context, photos: List<Photo>, relative: String): List<Long> {
        val resolver = context.contentResolver
        val ok = ArrayList<Long>()
        for (p in photos) {
            val values = ContentValues().apply { put(MediaStore.Images.Media.RELATIVE_PATH, relative) }
            val updated = try {
                resolver.update(p.uri, values, null, null) > 0
            } catch (_: Exception) {
                false
            }
            if (updated) ok.add(p.id)
        }
        return ok
    }

    private suspend fun copyThenDelete(
        context: Context,
        photos: List<Photo>,
        album: String,
        relative: String,
        broker: ConsentBroker,
    ): Outcome {
        val resolver = context.contentResolver
        val copied = ArrayList<Photo>()
        for (p in photos) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, p.name)
                put(MediaStore.Images.Media.MIME_TYPE, p.mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relative)
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), album)
                    dir.mkdirs()
                    put(MediaStore.Images.Media.DATA, File(dir, p.name).absolutePath)
                }
            }
            val target: Uri = try {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: continue
            } catch (_: Exception) {
                continue
            }
            val wrote = try {
                resolver.openInputStream(p.uri)?.use { input ->
                    resolver.openOutputStream(target)?.use { output -> input.copyTo(output, 128 * 1024) } != null
                } == true
            } catch (_: Exception) {
                false
            }
            if (wrote) copied.add(p) else runCatching { resolver.delete(target, null, null) }
        }
        if (copied.isEmpty()) return Outcome(emptyList(), 0, "Köçürmək alınmadı")
        val removal = remove(context, copied, toTrash = false, broker = broker)
        return Outcome(removal.succeeded, 0, "${removal.succeeded.size} şəkil «$album» albomuna köçürüldü")
    }
}
