package az.pixclean.dup

import az.pixclean.data.GroupKind
import az.pixclean.data.Photo
import az.pixclean.data.PhotoGroup
import kotlin.math.abs
import kotlin.math.ln

enum class SimilarityLevel(
    val label: String,
    val pMax: Int,
    val dMax: Int,
    val colorMax: Int,
    val aspectTol: Double,
) {
    STRICT("Sərt", 4, 8, 14, 0.15),
    BALANCED("Balanslı", 8, 12, 22, 0.30),
    LOOSE("Geniş", 12, 18, 34, 0.60);

    val pStrict: Int get() = pMax / 2
    val dStrict: Int get() = dMax / 2
}

enum class KeeperRule(val label: String) {
    BEST_QUALITY("Ən keyfiyyətli"),
    OLDEST("Ən köhnə"),
    NEWEST("Ən yeni"),
}

object Grouping {

    /** Below this the picture is essentially flat; its hashes carry no information. */
    private const val MIN_COLOR_SPREAD = 12

    // ------------------------------------------------------------------ exact

    fun exactGroups(photos: List<Photo>, rule: KeeperRule): List<PhotoGroup> {
        val bySha = HashMap<String, ArrayList<Photo>>()
        for (p in photos) {
            val sha = p.sha ?: continue
            bySha.getOrPut(sha) { ArrayList(2) }.add(p)
        }
        return bySha.values
            .filter { it.size > 1 }
            .map { members -> buildGroup(GroupKind.EXACT, members, rule) { _, _ -> 0 } }
            .sortedByDescending { it.reclaimable }
    }

    // ---------------------------------------------------------------- similar

    class SimilarResult(
        val groups: List<PhotoGroup>,
        val skippedFlat: Int,
        val comparedPairs: Long,
    )

    fun similarGroups(photos: List<Photo>, level: SimilarityLevel, rule: KeeperRule): SimilarResult {
        val usable = ArrayList<Photo>(photos.size)
        var flat = 0
        for (p in photos) {
            val sig = p.colorSig
            if (!p.hasSignature || sig == null) continue
            if (Signature.colorSpread(sig) < MIN_COLOR_SPREAD) { flat++; continue }
            usable.add(p)
        }
        if (usable.size < 2) return SimilarResult(emptyList(), flat, 0)

        // Banded index. Splitting a 64-bit hash into 8 bytes means any two hashes within
        // Hamming distance 7 must collide in at least one band (pigeonhole), so every such
        // pair is generated without ever touching the full N^2 matrix.
        val buckets = HashMap<Int, ArrayList<Int>>(usable.size * 4)
        fun index(hash: Long, salt: Int, idx: Int) {
            for (band in 0 until 8) {
                val v = ((hash ushr (band * 8)) and 0xFF).toInt()
                val key = (salt shl 20) or (band shl 12) or v
                buckets.getOrPut(key) { ArrayList(4) }.add(idx)
            }
        }
        for (i in usable.indices) {
            index(usable[i].pHash, 0, i)
            index(usable[i].dHash, 1, i)
        }

        val uf = UnionFind(usable.size)
        var compared = 0L
        val neighbours = HashSet<Int>()

        fun collect(hash: Long, salt: Int, self: Int) {
            for (band in 0 until 8) {
                val v = ((hash ushr (band * 8)) and 0xFF).toInt()
                val list = buckets[(salt shl 20) or (band shl 12) or v] ?: continue
                if (list.size > MAX_BUCKET) continue
                for (j in list) if (j > self) neighbours.add(j)
            }
        }

        // Per-photo candidate set rather than one global visited-pairs set: a gallery with
        // thousands of near-identical screenshots can produce millions of candidate pairs,
        // and holding all of them would be the one place this design could run out of memory.
        for (i in usable.indices) {
            neighbours.clear()
            collect(usable[i].pHash, 0, i)
            collect(usable[i].dHash, 1, i)
            for (j in neighbours) {
                if (uf.find(i) == uf.find(j)) continue
                compared++
                if (isSimilar(usable[i], usable[j], level)) uf.union(i, j)
            }
        }

        val comps = HashMap<Int, ArrayList<Photo>>()
        for (i in usable.indices) comps.getOrPut(uf.find(i)) { ArrayList(2) }.add(usable[i])

        val out = ArrayList<PhotoGroup>()
        for (members in comps.values) {
            if (members.size < 2) continue
            // A component can chain (A~B, B~C, A!~C). Re-anchor on the keeper and split off
            // anything that is not actually similar to it, so every shown group is coherent.
            for (tight in refine(members, level)) {
                if (tight.size < 2) continue
                if (tight.all { it.sha != null } && tight.map { it.sha }.distinct().size == 1) continue // pure exact set
                out.add(buildGroup(GroupKind.SIMILAR, tight, rule) { k, m ->
                    Signature.hamming(k.pHash, m.pHash)
                })
            }
        }
        return SimilarResult(out.sortedByDescending { it.reclaimable }, flat, compared)
    }

    private const val MAX_BUCKET = 4000

    private fun refine(members: List<Photo>, level: SimilarityLevel): List<List<Photo>> {
        if (members.size <= 2) return listOf(members)
        val out = ArrayList<List<Photo>>()
        var pool = members
        var guard = 0
        while (pool.size >= 2 && guard++ < 32) {
            val anchor = pool.maxByOrNull { qualityScore(it) }!!
            val keep = ArrayList<Photo>()
            val rest = ArrayList<Photo>()
            for (p in pool) {
                if (p === anchor || isSimilar(anchor, p, level)) keep.add(p) else rest.add(p)
            }
            out.add(keep)
            if (rest.size == pool.size) break
            pool = rest
        }
        if (pool.size >= 2 && guard >= 32) out.add(pool)
        return out
    }

    fun isSimilar(a: Photo, b: Photo, level: SimilarityLevel): Boolean {
        val dp = Signature.hamming(a.pHash, b.pHash)
        val dd = Signature.hamming(a.dHash, b.dHash)

        // Two independent hashes must agree. If one is very confident the other may relax,
        // which recovers heavily re-compressed copies without opening the door to noise.
        val ok = (dp <= level.pMax && dd <= level.dMax) ||
            (dp <= level.pStrict && dd <= level.dMax + 6) ||
            (dd <= level.dStrict && dp <= level.pMax + 4)
        if (!ok) return false

        if (level.aspectTol < 1.0 && a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0) {
            val ra = a.width.toDouble() / a.height
            val rb = b.width.toDouble() / b.height
            if (abs(ln(ra / rb)) > level.aspectTol) return false
        }

        val ca = a.colorSig ?: return false
        val cb = b.colorSig ?: return false
        return Signature.colorDistance(ca, cb) <= level.colorMax
    }

    // ------------------------------------------------------------------ utils

    fun qualityScore(p: Photo): Long = p.pixels * 1000 + (p.size / 1024)

    private fun buildGroup(
        kind: GroupKind,
        members: List<Photo>,
        rule: KeeperRule,
        distance: (Photo, Photo) -> Int,
    ): PhotoGroup {
        val keeper = when (rule) {
            KeeperRule.BEST_QUALITY -> members.maxWithOrNull(
                compareBy({ qualityScore(it) }, { -it.dateModified })
            )
            KeeperRule.OLDEST -> members.minByOrNull { it.dateModified }
            KeeperRule.NEWEST -> members.maxByOrNull { it.dateModified }
        } ?: members.first()

        val others = members
            .filter { it.id != keeper.id }
            .map { PhotoGroup.Member(it, distance(keeper, it)) }
            .sortedWith(compareBy({ it.distance }, { -qualityScore(it.photo) }))
        return PhotoGroup(kind, keeper, others)
    }

    private class UnionFind(n: Int) {
        private val parent = IntArray(n) { it }
        private val rank = ByteArray(n)
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }
            return r
        }
        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra == rb) return
            when {
                rank[ra] < rank[rb] -> parent[ra] = rb
                rank[ra] > rank[rb] -> parent[rb] = ra
                else -> { parent[rb] = ra; rank[ra]++ }
            }
        }
    }
}
