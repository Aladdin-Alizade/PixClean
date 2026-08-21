package az.pixclean.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plain SQLite. No ORM on purpose: the hot paths here are bulk upserts of tens of
 * thousands of rows, and compiled statements inside one transaction are several times
 * faster than anything generated.
 */
class Db(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    companion object {
        private const val NAME = "pixclean.db"
        private const val VERSION = 3

        fun floatsToBlob(v: FloatArray): ByteArray {
            val bb = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in v) bb.putFloat(f)
            return bb.array()
        }

        fun blobToFloats(b: ByteArray?): FloatArray? {
            if (b == null || b.size < 4) return null
            val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(b.size / 4) { bb.float }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous=NORMAL")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE photos (
              id INTEGER PRIMARY KEY,
              name TEXT NOT NULL,
              bucket TEXT NOT NULL,
              relPath TEXT NOT NULL,
              size INTEGER NOT NULL,
              width INTEGER NOT NULL,
              height INTEGER NOT NULL,
              dateAdded INTEGER NOT NULL,
              dateModified INTEGER NOT NULL,
              mime TEXT NOT NULL,
              sha TEXT,
              dhash INTEGER NOT NULL DEFAULT 0,
              phash INTEGER NOT NULL DEFAULT 0,
              colorSig BLOB,
              sigVersion INTEGER NOT NULL DEFAULT 0,
              docUri TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_photos_size ON photos(size)")
        db.execSQL("CREATE INDEX idx_photos_sha ON photos(sha)")
        db.execSQL("CREATE INDEX idx_photos_sig ON photos(sigVersion)")

        db.execSQL(
            """
            CREATE TABLE faces (
              faceId INTEGER PRIMARY KEY AUTOINCREMENT,
              photoId INTEGER NOT NULL,
              l INTEGER NOT NULL, t INTEGER NOT NULL, r INTEGER NOT NULL, b INTEGER NOT NULL,
              quality REAL NOT NULL,
              embedding BLOB,
              embVersion INTEGER NOT NULL DEFAULT 0,
              clusterId INTEGER NOT NULL DEFAULT -1,
              personId INTEGER NOT NULL DEFAULT 0,
              pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_faces_photo ON faces(photoId)")
        db.execSQL("CREATE INDEX idx_faces_cluster ON faces(clusterId)")

        // Which photos we already ran the detector over, so a rescan is incremental.
        db.execSQL(
            """
            CREATE TABLE faceScan (
              photoId INTEGER PRIMARY KEY,
              dateModified INTEGER NOT NULL,
              faceCount INTEGER NOT NULL,
              embVersion INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE people (
              personId INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Adding a column is not a reason to make somebody scan ten thousand photos again.
        if (oldVersion == 2 && newVersion == 3) {
            runCatching { db.execSQL("ALTER TABLE faces ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0") }
            return
        }
        db.execSQL("DROP TABLE IF EXISTS photos")
        db.execSQL("DROP TABLE IF EXISTS faces")
        db.execSQL("DROP TABLE IF EXISTS faceScan")
        db.execSQL("DROP TABLE IF EXISTS people")
        onCreate(db)
    }

    // ---------------------------------------------------------------- photos

    /**
     * Writes the MediaStore listing. Rows whose size or mtime changed lose their cached
     * signatures; untouched rows keep theirs, which is what makes a rescan cheap.
     * Returns the ids that no longer exist on the device.
     */
    fun syncPhotos(fresh: List<Photo>): List<Long> {
        val db = writableDatabase
        val known = HashMap<Long, Pair<Long, Long>>(fresh.size * 2)
        db.rawQuery("SELECT id, size, dateModified FROM photos", null).use { c ->
            while (c.moveToNext()) known[c.getLong(0)] = c.getLong(1) to c.getLong(2)
        }

        val insert = db.compileStatement(
            "INSERT OR REPLACE INTO photos" +
                "(id,name,bucket,relPath,size,width,height,dateAdded,dateModified,mime,sha,dhash,phash,colorSig,sigVersion,docUri)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,NULL,0,0,NULL,0,?)"
        )
        val touch = db.compileStatement(
            "UPDATE photos SET name=?,bucket=?,relPath=?,width=?,height=?,dateAdded=?,mime=? WHERE id=?"
        )

        db.beginTransaction()
        try {
            for (p in fresh) {
                val prev = known.remove(p.id)
                if (prev != null && prev.first == p.size && prev.second == p.dateModified) {
                    touch.clearBindings()
                    touch.bindString(1, p.name); touch.bindString(2, p.bucket); touch.bindString(3, p.relPath)
                    touch.bindLong(4, p.width.toLong()); touch.bindLong(5, p.height.toLong())
                    touch.bindLong(6, p.dateAdded); touch.bindString(7, p.mime); touch.bindLong(8, p.id)
                    touch.executeUpdateDelete()
                } else {
                    insert.clearBindings()
                    insert.bindLong(1, p.id); insert.bindString(2, p.name); insert.bindString(3, p.bucket)
                    insert.bindString(4, p.relPath); insert.bindLong(5, p.size)
                    insert.bindLong(6, p.width.toLong()); insert.bindLong(7, p.height.toLong())
                    insert.bindLong(8, p.dateAdded); insert.bindLong(9, p.dateModified); insert.bindString(10, p.mime)
                    p.docUri?.let { insert.bindString(11, it) }
                    insert.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            insert.close(); touch.close()
        }

        val gone = known.keys.toList()
        if (gone.isNotEmpty()) deletePhotos(gone)
        return gone
    }

    fun allPhotos(): List<Photo> {
        val out = ArrayList<Photo>(4096)
        readableDatabase.rawQuery(
            "SELECT id,name,bucket,relPath,size,width,height,dateAdded,dateModified,mime,sha,dhash,phash,colorSig,sigVersion,docUri FROM photos",
            null
        ).use { c ->
            while (c.moveToNext()) out.add(readPhoto(c))
        }
        return out
    }

    private fun readPhoto(c: android.database.Cursor) = Photo(
        id = c.getLong(0), name = c.getString(1), bucket = c.getString(2), relPath = c.getString(3),
        size = c.getLong(4), width = c.getInt(5), height = c.getInt(6),
        dateAdded = c.getLong(7), dateModified = c.getLong(8), mime = c.getString(9),
        sha = if (c.isNull(10)) null else c.getString(10),
        dHash = c.getLong(11), pHash = c.getLong(12),
        colorSig = if (c.isNull(13)) null else c.getBlob(13),
        sigVersion = c.getInt(14),
        docUri = if (c.isNull(15)) null else c.getString(15),
    )

    fun writeShas(rows: List<Pair<Long, String>>) = bulk(
        "UPDATE photos SET sha=? WHERE id=?", rows
    ) { st, (id, sha) -> st.bindString(1, sha); st.bindLong(2, id) }

    fun writeSignatures(rows: List<SigRow>) = bulk(
        "UPDATE photos SET dhash=?,phash=?,colorSig=?,sigVersion=? WHERE id=?", rows
    ) { st, r ->
        st.bindLong(1, r.dHash); st.bindLong(2, r.pHash); st.bindBlob(3, r.colorSig)
        st.bindLong(4, Signatures.VERSION.toLong()); st.bindLong(5, r.id)
    }

    class SigRow(val id: Long, val dHash: Long, val pHash: Long, val colorSig: ByteArray)

    fun deletePhotos(ids: Collection<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.chunked(400).forEach { chunk ->
                val q = chunk.joinToString(",")
                db.execSQL("DELETE FROM photos WHERE id IN ($q)")
                db.execSQL("DELETE FROM faces WHERE photoId IN ($q)")
                db.execSQL("DELETE FROM faceScan WHERE photoId IN ($q)")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun photoCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM photos", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    // ----------------------------------------------------------------- faces

    /** Photo ids that still need the face detector run over them. */
    fun photosNeedingFaceScan(embVersion: Int): Set<Long> {
        val done = HashSet<Long>()
        readableDatabase.rawQuery(
            "SELECT s.photoId FROM faceScan s JOIN photos p ON p.id=s.photoId " +
                "WHERE s.dateModified = p.dateModified AND s.embVersion = ?",
            arrayOf(embVersion.toString())
        ).use { c -> while (c.moveToNext()) done.add(c.getLong(0)) }

        val all = HashSet<Long>()
        readableDatabase.rawQuery("SELECT id FROM photos", null).use { c ->
            while (c.moveToNext()) all.add(c.getLong(0))
        }
        all.removeAll(done)
        return all
    }

    fun replaceFaces(photoId: Long, dateModified: Long, embVersion: Int, faces: List<DetectedFaceRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM faces WHERE photoId=?", arrayOf<Any>(photoId))
            val st = db.compileStatement(
                "INSERT INTO faces(photoId,l,t,r,b,quality,embedding,embVersion,clusterId,personId) VALUES (?,?,?,?,?,?,?,?,-1,0)"
            )
            for (f in faces) {
                st.clearBindings()
                st.bindLong(1, photoId)
                st.bindLong(2, f.left.toLong()); st.bindLong(3, f.top.toLong())
                st.bindLong(4, f.right.toLong()); st.bindLong(5, f.bottom.toLong())
                st.bindDouble(6, f.quality.toDouble())
                st.bindBlob(7, floatsToBlob(f.embedding))
                st.bindLong(8, embVersion.toLong())
                st.executeInsert()
            }
            st.close()
            val cv = ContentValues().apply {
                put("photoId", photoId); put("dateModified", dateModified)
                put("faceCount", faces.size); put("embVersion", embVersion)
            }
            db.insertWithOnConflict("faceScan", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    class DetectedFaceRow(
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val quality: Float, val embedding: FloatArray,
    )

    fun allFaces(embVersion: Int): List<FaceRow> {
        val out = ArrayList<FaceRow>(2048)
        readableDatabase.rawQuery(
            "SELECT faceId,photoId,l,t,r,b,quality,embedding,clusterId,personId,pinned FROM faces WHERE embVersion=?",
            arrayOf(embVersion.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(
                FaceRow(
                    c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5),
                    c.getFloat(6), blobToFloats(if (c.isNull(7)) null else c.getBlob(7)),
                    c.getInt(8), c.getLong(9), c.getInt(10) != 0
                )
            )
        }
        return out
    }

    fun writeClusters(rows: List<Triple<Long, Int, Long>>) = bulk(
                // No pinned=0 guard here on purpose: the clusterer already pulls pinned faces to their
        // person's cluster, so letting its numbering apply to them keeps ids consistent. Skipping
        // them would strand a corrected face under a cluster id that no longer means anything.
        "UPDATE faces SET clusterId=?, personId=? WHERE faceId=?", rows
    ) { st, (faceId, cluster, person) ->
        st.bindLong(1, cluster.toLong()); st.bindLong(2, person); st.bindLong(3, faceId)
    }

    fun faceCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM faces", null).use {
        if (it.moveToFirst()) it.getInt(0) else 0
    }

    /** A different embedder means old vectors are not comparable; drop them. */
    fun dropOtherEmbeddings(embVersion: Int) {
        writableDatabase.execSQL("DELETE FROM faces WHERE embVersion <> ?", arrayOf<Any>(embVersion))
        writableDatabase.execSQL("DELETE FROM faceScan WHERE embVersion <> ?", arrayOf<Any>(embVersion))
    }

    fun clearFaces() {
        writableDatabase.execSQL("DELETE FROM faces")
        writableDatabase.execSQL("DELETE FROM faceScan")
    }

    // ---------------------------------------------------------------- people

    fun people(): Map<Long, String> {
        val m = HashMap<Long, String>()
        readableDatabase.rawQuery("SELECT personId,name FROM people", null).use { c ->
            while (c.moveToNext()) m[c.getLong(0)] = c.getString(1)
        }
        return m
    }

    /** Names a cluster; creates the person row if needed and stamps every face in it. */
    fun namePerson(clusterId: Int, name: String, existingPersonId: Long): Long {
        val db = writableDatabase
        val pid = if (existingPersonId > 0) {
            db.execSQL("UPDATE people SET name=? WHERE personId=?", arrayOf<Any>(name, existingPersonId))
            existingPersonId
        } else {
            val cv = ContentValues().apply { put("name", name) }
            db.insert("people", null, cv)
        }
        db.execSQL("UPDATE faces SET personId=? WHERE clusterId=?", arrayOf<Any>(pid, clusterId))
        return pid
    }

    /**
     * A cluster the user is about to move faces into needs an identity that outlives the next
     * re-clustering, so it gets a person row even when it has no name yet.
     */
    fun ensurePerson(clusterId: Int): Long {
        val existing = readableDatabase.rawQuery(
            "SELECT personId FROM faces WHERE clusterId=? AND personId>0 LIMIT 1",
            arrayOf(clusterId.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        if (existing > 0) return existing
        val pid = writableDatabase.insert("people", null, ContentValues().apply { put("name", "") })
        writableDatabase.execSQL("UPDATE faces SET personId=? WHERE clusterId=?", arrayOf<Any>(pid, clusterId))
        return pid
    }

    fun nextClusterId(): Int = readableDatabase
        .rawQuery("SELECT IFNULL(MAX(clusterId), -1) + 1 FROM faces", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun createPerson(): Long =
        writableDatabase.insert("people", null, ContentValues().apply { put("name", "") })

    /**
     * A hand-made correction is not a suggestion: it is pinned, so re-clustering has to keep it.
     */
    fun pinFacesTo(faceIds: Collection<Long>, clusterId: Int, personId: Long) {
        if (faceIds.isEmpty()) return
        writableDatabase.execSQL(
            "UPDATE faces SET clusterId=?, personId=?, pinned=1 WHERE faceId IN (${faceIds.joinToString(",")})",
            arrayOf<Any>(clusterId, personId)
        )
    }

    fun mergeClusters(intoClusterId: Int, fromClusterIds: List<Int>) {
        if (fromClusterIds.isEmpty()) return
        val db = writableDatabase
        val pid = readableDatabase.rawQuery(
            "SELECT personId FROM faces WHERE clusterId=? LIMIT 1", arrayOf(intoClusterId.toString())
        ).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        db.execSQL(
            "UPDATE faces SET clusterId=?, personId=? WHERE clusterId IN (${fromClusterIds.joinToString(",")})",
            arrayOf<Any>(intoClusterId, pid)
        )
    }

    // ------------------------------------------------------------------ util

    private fun <T> bulk(sql: String, rows: List<T>, bind: (android.database.sqlite.SQLiteStatement, T) -> Unit) {
        if (rows.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement(sql)
            for (r in rows) {
                st.clearBindings()
                bind(st, r)
                st.executeUpdateDelete()
            }
            st.close()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
