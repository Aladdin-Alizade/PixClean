package az.pixclean.faces

import az.pixclean.data.FaceRow
import kotlin.math.sqrt

/**
 * Groups face embeddings into people.
 *
 * Three passes, cheapest first:
 *   1. seed  - walk faces best-quality-first, attach to the nearest centroid or open a new
 *              cluster. Seeding with good faces matters: a blurry profile shot should never
 *              be the thing a cluster is defined by.
 *   2. merge - centroids that drifted together get folded, with a stricter bar than the
 *              assignment threshold so two similar-looking people are not fused.
 *   3. settle- every face is re-offered to every centroid, which repairs faces that landed
 *              in the wrong cluster early on.
 *
 * O(N*K) per pass rather than the O(N^2) of true agglomerative clustering, which is what
 * makes twenty thousand faces finish in seconds instead of minutes.
 */
object FaceClusterer {

    private const val MERGE_MARGIN = 0.04f
    private const val MAX_K_FOR_MERGE = 3000
    private const val MIN_QUALITY = 0.30f

    class Assignment(val faceId: Long, val clusterId: Int, val personId: Long)

    class Result(val assignments: List<Assignment>, val clusterCount: Int, val usedFaces: Int)

    fun cluster(
        faces: List<FaceRow>,
        threshold: Float,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val usable = faces
            .filter { it.embedding != null && it.quality >= MIN_QUALITY }
            .sortedByDescending { it.quality }
        if (usable.isEmpty()) return Result(emptyList(), 0, 0)

        val dim = usable.first().embedding!!.size
        val sums = ArrayList<FloatArray>()      // running unnormalised sum per cluster
        val centroids = ArrayList<FloatArray>() // L2-normalised view of the above
        val counts = ArrayList<Int>()
        val assign = IntArray(usable.size) { -1 }

        // 1. seed
        for (i in usable.indices) {
            val e = usable[i].embedding!!
            if (e.size != dim) continue
            val best = nearest(centroids, e, threshold)
            if (best >= 0) {
                add(sums[best], e); counts[best] = counts[best] + 1
                centroids[best] = normalized(sums[best])
                assign[i] = best
            } else {
                sums.add(e.copyOf()); centroids.add(normalized(e)); counts.add(1)
                assign[i] = centroids.size - 1
            }
            if (i % 256 == 0) onProgress(0.45f * i / usable.size)
        }

        // 2. merge
        if (centroids.size in 2..MAX_K_FOR_MERGE) {
            val map = IntArray(centroids.size) { it }
            fun root(x: Int): Int { var r = x; while (map[r] != r) { map[r] = map[map[r]]; r = map[r] }; return r }
            val bar = (threshold + MERGE_MARGIN).coerceAtMost(0.97f)
            for (a in 0 until centroids.size - 1) {
                if (root(a) != a) continue
                for (b in a + 1 until centroids.size) {
                    if (root(b) != b) continue
                    if (FaceEmbedders.cosine(centroids[a], centroids[b]) >= bar) {
                        map[root(b)] = a
                        add(sums[a], sums[b]); counts[a] = counts[a] + counts[b]
                        centroids[a] = normalized(sums[a])
                    }
                }
                onProgress(0.45f + 0.25f * a / centroids.size)
            }
            for (i in assign.indices) if (assign[i] >= 0) assign[i] = root(assign[i])
        }

        // Compact cluster ids after merging.
        val remap = HashMap<Int, Int>()
        for (i in assign.indices) {
            val a = assign[i]
            if (a >= 0) assign[i] = remap.getOrPut(a) { remap.size }
        }
        val k = remap.size
        val liveSums = Array(k) { FloatArray(dim) }
        val liveCounts = IntArray(k)
        for (i in assign.indices) {
            val a = assign[i]
            if (a >= 0) { add(liveSums[a], usable[i].embedding!!); liveCounts[a]++ }
        }
        val liveCentroids = Array(k) { normalized(liveSums[it]) }

        // 3. settle
        for (i in usable.indices) {
            val e = usable[i].embedding!!
            if (e.size != dim) continue
            val best = nearest(liveCentroids.asList(), e, threshold)
            if (best >= 0 && best != assign[i]) assign[i] = best
            if (i % 256 == 0) onProgress(0.70f + 0.25f * i / usable.size)
        }

        // Keep whatever names the user already gave: a cluster inherits the person id most
        // of its members carried in, so renaming survives a rescan.
        val votes = HashMap<Int, HashMap<Long, Int>>()
        for (i in usable.indices) {
            val pid = usable[i].personId
            if (pid > 0 && assign[i] >= 0) {
                votes.getOrPut(assign[i]) { HashMap() }.merge(pid, 1, Int::plus)
            }
        }
        val personOf = HashMap<Int, Long>()
        for ((cluster, tally) in votes) {
            personOf[cluster] = tally.maxByOrNull { it.value }!!.key
        }

        val out = ArrayList<Assignment>(usable.size + (faces.size - usable.size))
        for (i in usable.indices) {
            val c = assign[i]
            out.add(Assignment(usable[i].faceId, c, personOf[c] ?: 0L))
        }
        // Faces that failed the quality gate are parked, not silently kept in an old cluster.
        val usedIds = usable.mapTo(HashSet()) { it.faceId }
        for (f in faces) if (f.faceId !in usedIds) out.add(Assignment(f.faceId, -1, 0L))

        onProgress(1f)
        return Result(out, remap.size, usable.size)
    }

    private fun nearest(centroids: List<FloatArray>, e: FloatArray, threshold: Float): Int {
        var bestIdx = -1
        var bestSim = threshold
        for (c in centroids.indices) {
            val sim = FaceEmbedders.cosine(centroids[c], e)
            if (sim >= bestSim) { bestSim = sim; bestIdx = c }
        }
        return bestIdx
    }

    private fun add(target: FloatArray, v: FloatArray) {
        val n = minOf(target.size, v.size)
        for (i in 0 until n) target[i] += v[i]
    }

    private fun normalized(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val n = sqrt(s)
        if (n < 1e-8f) return v.copyOf()
        return FloatArray(v.size) { v[it] / n }
    }
}
