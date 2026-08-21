package az.pixclean.core

import android.app.Application
import android.content.Context
import az.pixclean.data.Db
import az.pixclean.data.FaceRow
import az.pixclean.data.FolderScanner
import az.pixclean.data.MediaStoreScanner
import az.pixclean.data.PersonCluster
import az.pixclean.data.Photo
import az.pixclean.data.PhotoGroup
import az.pixclean.dup.ExactHash
import az.pixclean.dup.Grouping
import az.pixclean.dup.Signature
import az.pixclean.faces.FaceClusterer
import az.pixclean.faces.FaceEmbedder
import az.pixclean.faces.FaceEmbedders
import az.pixclean.faces.FaceScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

enum class Phase(val label: String) {
    IDLE(""),
    INDEXING("Qalereya oxunur"),
    HASHING("Eyni fayllar yoxlanılır"),
    SIGNING("Şəkillər müqayisəyə hazırlanır"),
    GROUPING("Qruplaşdırılır"),
    FACE_DETECT("Üzlər tapılır"),
    CLUSTERING("Üzlər qruplaşdırılır"),
}

class EngineState(
    val phase: Phase = Phase.IDLE,
    val done: Int = 0,
    val total: Int = 0,
    val photoCount: Int = 0,
    val exact: List<PhotoGroup> = emptyList(),
    val similar: List<PhotoGroup> = emptyList(),
    val flatSkipped: Int = 0,
    val people: List<PersonCluster> = emptyList(),
    val faceCount: Int = 0,
    val scannedForFaces: Boolean = false,
    val embedderName: String = "",
    val modelBacked: Boolean = false,
    val photoIndex: Map<Long, Photo> = emptyMap(),
    val buckets: List<Pair<String, Int>> = emptyList(),
    val error: String? = null,
) {
    val running: Boolean get() = phase != Phase.IDLE
    val progress: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    val exactWaste: Long get() = exact.sumOf { it.reclaimable }
    val similarWaste: Long get() = similar.sumOf { it.reclaimable }

    fun copy(
        phase: Phase = this.phase,
        done: Int = this.done,
        total: Int = this.total,
        photoCount: Int = this.photoCount,
        exact: List<PhotoGroup> = this.exact,
        similar: List<PhotoGroup> = this.similar,
        flatSkipped: Int = this.flatSkipped,
        people: List<PersonCluster> = this.people,
        faceCount: Int = this.faceCount,
        scannedForFaces: Boolean = this.scannedForFaces,
        embedderName: String = this.embedderName,
        modelBacked: Boolean = this.modelBacked,
        photoIndex: Map<Long, Photo> = this.photoIndex,
        buckets: List<Pair<String, Int>> = this.buckets,
        error: String? = this.error,
    ) = EngineState(
        phase, done, total, photoCount, exact, similar, flatSkipped, people, faceCount,
        scannedForFaces, embedderName, modelBacked, photoIndex, buckets, error
    )
}

/**
 * Owns the whole pipeline and every result. Lives on the Application so a rotation or a
 * trip through the launcher never restarts a scan of ten thousand photos.
 */
class ScanEngine(private val app: Application, val settings: AppSettings) {

    private val db = Db(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private var job: Job? = null

    private val ioWorkers = (Runtime.getRuntime().availableProcessors() * 2).coerceIn(4, 12)
    private val cpuWorkers = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    private val faceWorkers = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    val busy: Boolean get() = job?.isActive == true

    /**
     * The single answer to "which photos is this app allowed to touch". Applied by hashing,
     * signatures, face detection and grouping alike, so a folder switched off in settings can
     * never reappear in one of them.
     */
    private fun included(photos: List<Photo>): List<Photo> {
        val prefs = settings.value
        val roots = rootPaths()
        var out = photos
        if (roots.isNotEmpty()) {
            out = out.filter { p -> p.fromFolder || roots.any { p.relPath.startsWith(it) } }
        }
        if (prefs.excludedBuckets.isNotEmpty()) {
            out = out.filter { it.bucket !in prefs.excludedBuckets }
        }
        return out
    }

    /** Root filter only — used for the album list, which must still show switched-off albums. */
    private fun includedByRootOnly(photos: List<Photo>): List<Photo> {
        val roots = rootPaths()
        if (roots.isEmpty()) return photos
        return photos.filter { p -> p.fromFolder || roots.any { p.relPath.startsWith(it) } }
    }

    /**
     * Stored faces are only comparable when both the embedder and the resolution the detector
     * saw are the same, so both feed the version stamp; changing either triggers a clean rescan.
     */
    private fun scanVersion(embedder: FaceEmbedder): Int =
        embedder.version + settings.value.faceDetail.ordinal * 7919

    private fun rootPaths(): List<String> = settings.value.scanRoots.mapNotNull {
        FolderScanner.treePath(android.net.Uri.parse(it))
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(phase = Phase.IDLE)
    }

    // ------------------------------------------------------------ photo scan

    fun scanPhotos() {
        if (busy) return
        job = scope.launch {
            try {
                runPhotoScan()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Throwable) {
                _state.value = _state.value.copy(phase = Phase.IDLE, error = e.message ?: e.toString())
            }
        }
    }

    private suspend fun runPhotoScan() {
        val resolver = app.contentResolver

        set { copy(phase = Phase.INDEXING, done = 0, total = 0, error = null) }
        val fresh = withContext(Dispatchers.IO) { MediaStoreScanner.scan(app) }
        val everything = withContext(Dispatchers.IO) { fresh + walkPickedFolders(fresh) }
        withContext(Dispatchers.IO) { db.syncPhotos(everything) }
        var photos = included(withContext(Dispatchers.IO) { db.allPhotos() })
        set { copy(photoCount = photos.size) }

        // --- exact duplicates: size bucket -> 64 KB head -> full digest
        val sizeBuckets = photos.groupBy { it.size }.values.filter { it.size > 1 }
        val candidates = sizeBuckets.flatten()
        if (candidates.isNotEmpty()) {
            set { copy(phase = Phase.HASHING, done = 0, total = candidates.size) }
            val heads = ConcurrentHashMap<Long, String>(candidates.size)
            val counter = AtomicInteger()
            forEachParallel(candidates, ioWorkers) { p ->
                ExactHash.hash(resolver, p.uri, ExactHash.HEAD_BYTES.toLong())?.let { heads[p.id] = it }
                tick(counter, candidates.size)
            }

            val needFull = candidates
                .groupBy { it.size to (heads[it.id] ?: "?") }
                .values.filter { it.size > 1 }.flatten()
                .filter { it.sha == null }

            if (needFull.isNotEmpty()) {
                set { copy(done = 0, total = needFull.size) }
                val full = ConcurrentHashMap<Long, String>(needFull.size)
                val c2 = AtomicInteger()
                forEachParallel(needFull, ioWorkers) { p ->
                    ExactHash.hash(resolver, p.uri)?.let { full[p.id] = it }
                    tick(c2, needFull.size)
                }
                withContext(Dispatchers.IO) { db.writeShas(full.map { it.key to it.value }) }
            }
        }

        // --- perceptual signatures, only for photos that do not have a current one
        photos = included(withContext(Dispatchers.IO) { db.allPhotos() })
        val needSig = photos.filter { !it.hasSignature }
        if (needSig.isNotEmpty()) {
            set { copy(phase = Phase.SIGNING, done = 0, total = needSig.size) }
            val counter = AtomicInteger()
            val pending = java.util.Collections.synchronizedList(ArrayList<Db.SigRow>(512))
            forEachParallel(needSig, cpuWorkers) { p ->
                Signature.compute(resolver, p)?.let { row ->
                    pending.add(row)
                    // Flush periodically so an interrupted scan keeps its work.
                    if (pending.size >= 400) {
                        val batch = synchronized(pending) { ArrayList(pending).also { pending.clear() } }
                        withContext(Dispatchers.IO) { db.writeSignatures(batch) }
                    }
                }
                tick(counter, needSig.size)
            }
            val rest = synchronized(pending) { ArrayList(pending).also { pending.clear() } }
            withContext(Dispatchers.IO) { db.writeSignatures(rest) }
        }

        regroupInternal()
        set { copy(phase = Phase.IDLE) }
    }

    // ------------------------------------------------------------- face scan

    fun scanFaces() {
        if (busy) return
        job = scope.launch {
            try {
                runFaceScan()
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Throwable) {
                _state.value = _state.value.copy(phase = Phase.IDLE, error = e.message ?: e.toString())
            }
        }
    }

    private suspend fun runFaceScan() {
        val resolver = app.contentResolver
        set { copy(phase = Phase.FACE_DETECT, done = 0, total = 0, error = null) }

        if (db.photoCount() == 0) {
            val fresh = withContext(Dispatchers.IO) { MediaStoreScanner.scan(app) }
            withContext(Dispatchers.IO) { db.syncPhotos(fresh + walkPickedFolders(fresh)) }
        }

        val embedder: FaceEmbedder = withContext(Dispatchers.IO) { FaceEmbedders.create(app) }
        val detail = settings.value.faceDetail
        set { copy(embedderName = embedder.displayName, modelBacked = embedder.isModelBacked) }
        withContext(Dispatchers.IO) { db.dropOtherEmbeddings(scanVersion(embedder)) }

        val photos = included(withContext(Dispatchers.IO) { db.allPhotos() })
        val byId = photos.associateBy { it.id }
        val todoIds = withContext(Dispatchers.IO) { db.photosNeedingFaceScan(scanVersion(embedder)) }
        val todo = todoIds.mapNotNull { byId[it] }

        try {
            if (todo.isNotEmpty()) {
                set { copy(done = 0, total = todo.size) }
                val scanners = List(faceWorkers) { FaceScanner(embedder, detail) }
                val counter = AtomicInteger()
                try {
                    forEachParallelIndexed(todo, faceWorkers) { worker, photo ->
                        val faces = scanners[worker].scan(resolver, photo) ?: emptyList()
                        withContext(Dispatchers.IO) {
                            db.replaceFaces(photo.id, photo.dateModified, scanVersion(embedder), faces)
                        }
                        tick(counter, todo.size)
                    }
                } finally {
                    scanners.forEach { it.close() }
                }
            }

            set { copy(phase = Phase.CLUSTERING, done = 0, total = 100) }
            clusterInternal(embedder)
        } finally {
            embedder.close()
        }
        set { copy(phase = Phase.IDLE, scannedForFaces = true) }
    }

    private suspend fun clusterInternal(embedder: FaceEmbedder) {
        val faces = withContext(Dispatchers.IO) { db.allFaces(scanVersion(embedder)) }
        val threshold = embedder.threshold(settings.value.faceLevel)
        val result = FaceClusterer.cluster(faces, threshold) { p ->
            _state.value = _state.value.copy(done = (p * 100).toInt(), total = 100)
        }
        withContext(Dispatchers.IO) {
            db.writeClusters(result.assignments.map { Triple(it.faceId, it.clusterId, it.personId) })
        }
        val refreshed = withContext(Dispatchers.IO) { db.allFaces(scanVersion(embedder)) }
        set { copy(people = buildPeople(refreshed), faceCount = refreshed.size) }
    }

    /** Re-clusters using stored embeddings only. Cheap enough to run when a slider moves. */
    fun recluster() {
        if (busy) return
        job = scope.launch {
            val embedder = withContext(Dispatchers.IO) { FaceEmbedders.create(app) }
            try {
                set { copy(phase = Phase.CLUSTERING, done = 0, total = 100) }
                clusterInternal(embedder)
            } finally {
                embedder.close()
                set { copy(phase = Phase.IDLE) }
            }
        }
    }

    private fun buildPeople(faces: List<FaceRow>): List<PersonCluster> {
        val names = db.people()
        return faces
            .filter { it.clusterId >= 0 }
            .groupBy { it.clusterId }
            .map { (cluster, members) ->
                val cover = members.maxByOrNull { it.quality } ?: members.first()
                val pid = members.firstOrNull { it.personId > 0 }?.personId ?: 0L
                PersonCluster(cluster, pid, names[pid], cover, members.sortedByDescending { it.quality })
            }
            .sortedWith(compareByDescending<PersonCluster> { it.faces.size }.thenBy { it.clusterId })
    }

    /** Files inside picked folders that MediaStore does not already know about. */
    private fun walkPickedFolders(mediaStorePhotos: List<Photo>): List<Photo> {
        val roots = settings.value.scanRoots
        if (roots.isEmpty()) return emptyList()
        val known = FolderScanner.known(mediaStorePhotos)
        val seen = HashSet<Long>()
        val out = ArrayList<Photo>()
        for (root in roots) {
            val uri = runCatching { android.net.Uri.parse(root) }.getOrNull() ?: continue
            for (p in FolderScanner.scan(app, uri, known)) {
                if (seen.add(p.id)) out.add(p)
            }
        }
        return out
    }

    // -------------------------------------------------------------- grouping

    fun regroup() {
        if (busy) return
        job = scope.launch {
            set { copy(phase = Phase.GROUPING) }
            regroupInternal()
            set { copy(phase = Phase.IDLE) }
        }
    }

    /**
     * [silent] restores results at launch without raising a phase: otherwise a cold start
     * looks like a running scan, disables the scan buttons and pops a foreground notification
     * for work the user never asked for.
     */
    private suspend fun regroupInternal(silent: Boolean = false) {
        if (!silent) set { copy(phase = Phase.GROUPING, done = 0, total = 0) }
        val prefs = settings.value
        val all = withContext(Dispatchers.IO) { db.allPhotos() }
        val photos = included(all)
        // Buckets describe what is in scope, not what is in the database, so switching a
        // folder off does not leave a stale album row behind claiming otherwise.
        val inScope = if (settings.value.excludedBuckets.isEmpty()) photos else includedByRootOnly(all)
        val buckets = inScope.groupingBy { it.bucket }.eachCount()
            .map { it.key to it.value }
            .sortedByDescending { it.second }
        val exact = withContext(Dispatchers.Default) { Grouping.exactGroups(photos, prefs.keeperRule) }
        val similar = withContext(Dispatchers.Default) {
            Grouping.similarGroups(photos, prefs.similarity, prefs.keeperRule)
        }
        set {
            copy(
                photoCount = photos.size,
                buckets = buckets,
                photoIndex = all.associateBy { it.id },
                exact = exact,
                similar = similar.groups,
                flatSkipped = similar.skippedFlat,
            )
        }
    }

    /** Called after the system confirms a delete or a move, so the UI never shows a ghost. */
    fun forget(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) { db.deletePhotos(ids) }
            regroupInternal(silent = true)
            val version = _state.value
            if (version.faceCount > 0) {
                val embedder = withContext(Dispatchers.IO) { FaceEmbedders.create(app) }
                try {
                    val faces = withContext(Dispatchers.IO) { db.allFaces(scanVersion(embedder)) }
                    set { copy(people = buildPeople(faces), faceCount = faces.size) }
                } finally {
                    embedder.close()
                }
            }
        }
    }

    fun renamePerson(cluster: PersonCluster, name: String) {
        scope.launch {
            withContext(Dispatchers.IO) { db.namePerson(cluster.clusterId, name.trim(), cluster.personId) }
            val embedder = withContext(Dispatchers.IO) { FaceEmbedders.create(app) }
            try {
                val faces = withContext(Dispatchers.IO) { db.allFaces(scanVersion(embedder)) }
                set { copy(people = buildPeople(faces)) }
            } finally {
                embedder.close()
            }
        }
    }

    /**
     * Moves the faces that put [photoIds] in [from] over to [target], or into a group of their
     * own when [target] is null. Pinned, so the next re-clustering keeps the correction.
     */
    fun movePhotosToPerson(from: PersonCluster, photoIds: Set<Long>, target: PersonCluster?) {
        if (photoIds.isEmpty()) return
        scope.launch {
            val faceIds = from.faces.filter { it.photoId in photoIds }.map { it.faceId }
            if (faceIds.isEmpty()) return@launch
            withContext(Dispatchers.IO) {
                if (target == null) {
                    db.pinFacesTo(faceIds, db.nextClusterId(), db.createPerson())
                } else {
                    db.pinFacesTo(faceIds, target.clusterId, db.ensurePerson(target.clusterId))
                }
            }
            refreshPeople()
        }
    }

    private suspend fun refreshPeople() {
        val embedder = withContext(Dispatchers.IO) { FaceEmbedders.create(app) }
        try {
            val faces = withContext(Dispatchers.IO) { db.allFaces(scanVersion(embedder)) }
            set { copy(people = buildPeople(faces), faceCount = faces.size) }
        } finally {
            embedder.close()
        }
    }

    fun mergePeople(into: PersonCluster, from: List<PersonCluster>) {
        scope.launch {
            withContext(Dispatchers.IO) { db.mergeClusters(into.clusterId, from.map { it.clusterId }) }
            val embedder = withContext(Dispatchers.IO) { FaceEmbedders.create(app) }
            try {
                val faces = withContext(Dispatchers.IO) { db.allFaces(scanVersion(embedder)) }
                set { copy(people = buildPeople(faces)) }
            } finally {
                embedder.close()
            }
        }
    }

    fun resetFaceIndex() {
        scope.launch {
            withContext(Dispatchers.IO) { db.clearFaces() }
            set { copy(people = emptyList(), faceCount = 0, scannedForFaces = false) }
        }
    }

    fun loadExisting() {
        if (busy) return
        job = scope.launch {
            val photos = withContext(Dispatchers.IO) { db.allPhotos() }
            if (photos.isEmpty()) return@launch
            regroupInternal(silent = true)
            val embedder = withContext(Dispatchers.IO) { FaceEmbedders.create(app) }
            try {
                val faces = withContext(Dispatchers.IO) { db.allFaces(scanVersion(embedder)) }
                set {
                    copy(
                        people = buildPeople(faces),
                        faceCount = faces.size,
                        embedderName = embedder.displayName,
                        modelBacked = embedder.isModelBacked,
                        scannedForFaces = faces.isNotEmpty(),
                    )
                }
            } finally {
                embedder.close()
            }
        }
    }

    fun clearError() = set { copy(error = null) }

    // ------------------------------------------------------------------ util

    private inline fun set(block: EngineState.() -> EngineState) {
        _state.value = _state.value.block()
    }

    @Volatile private var lastTick = 0L
    private fun tick(counter: AtomicInteger, total: Int) {
        val n = counter.incrementAndGet()
        val now = System.currentTimeMillis()
        if (n == total || now - lastTick > 120) {
            lastTick = now
            _state.value = _state.value.copy(done = n, total = total)
        }
    }

    private suspend fun <T> forEachParallel(items: List<T>, workers: Int, body: suspend (T) -> Unit) =
        forEachParallelIndexed(items, workers) { _, item -> body(item) }

    private suspend fun <T> forEachParallelIndexed(
        items: List<T>,
        workers: Int,
        body: suspend (worker: Int, item: T) -> Unit,
    ) = coroutineScope {
        val next = AtomicInteger(0)
        (0 until workers).map { worker ->
            async(Dispatchers.IO) {
                while (true) {
                    coroutineContext.ensureActive()
                    val i = next.getAndIncrement()
                    if (i >= items.size) break
                    body(worker, items[i])
                }
            }
        }.awaitAll()
    }

    companion object {
        @Volatile private var instance: ScanEngine? = null
        fun get(context: Context): ScanEngine {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val app = context.applicationContext as Application
                return ScanEngine(app, AppSettings(app)).also { instance = it }
            }
        }
    }
}
