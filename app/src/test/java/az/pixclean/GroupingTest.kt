package az.pixclean

import az.pixclean.data.Photo
import az.pixclean.data.Signatures
import az.pixclean.dup.Grouping
import az.pixclean.dup.KeeperRule
import az.pixclean.dup.SimilarityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the two promises the app makes: an exact group is byte-identical, and a similar
 * group needs two independent hashes plus colour layout to agree before anything is offered
 * for deletion.
 */
class GroupingTest {

    private var next = 1L

    /** A colour grid with real spread, so the flat-image guard does not filter it out. */
    private fun sig(seed: Int): ByteArray = ByteArray(48) { (((it * 37 + seed * 11) % 200) + 20).toByte() }

    private fun photo(
        name: String,
        sha: String? = null,
        pHash: Long = 0,
        dHash: Long = 0,
        colorSig: ByteArray = sig(1),
        width: Int = 4000,
        height: Int = 3000,
        size: Long = 2_000_000,
        modified: Long = 1_700_000_000,
    ) = Photo(
        id = next++, name = name, bucket = "DCIM", relPath = "/x/$name", size = size,
        width = width, height = height, dateAdded = modified, dateModified = modified,
        mime = "image/jpeg", sha = sha, dHash = dHash, pHash = pHash,
        colorSig = colorSig, sigVersion = Signatures.VERSION,
    )

    private fun flip(v: Long, bits: Int): Long {
        var out = v
        repeat(bits) { out = out xor (1L shl (it * 3 % 64)) }
        return out
    }

    @Test
    fun `only byte identical files form an exact group`() {
        val photos = listOf(
            photo("a.jpg", sha = "AAA"),
            photo("a copy.jpg", sha = "AAA"),
            photo("a (1).jpg", sha = "AAA"),
            photo("b.jpg", sha = "BBB"),
            photo("c.jpg", sha = null),
        )
        val groups = Grouping.exactGroups(photos, KeeperRule.BEST_QUALITY)
        assertEquals(1, groups.size)
        assertEquals(3, groups.first().size)
        assertEquals(2, groups.first().others.size)
    }

    @Test
    fun `keeper rule picks the copy it says it will`() {
        val small = photo("small.jpg", sha = "S", width = 1000, height = 750, size = 200_000, modified = 100)
        val big = photo("big.jpg", sha = "S", width = 4000, height = 3000, size = 2_000_000, modified = 500)

        val best = Grouping.exactGroups(listOf(small, big), KeeperRule.BEST_QUALITY).first()
        assertEquals("big.jpg", best.keeper.name)

        val oldest = Grouping.exactGroups(listOf(small, big), KeeperRule.OLDEST).first()
        assertEquals("small.jpg", oldest.keeper.name)

        val newest = Grouping.exactGroups(listOf(small, big), KeeperRule.NEWEST).first()
        assertEquals("big.jpg", newest.keeper.name)
    }

    @Test
    fun `a re-compressed copy is similar but an unrelated photo is not`() {
        val base = 0x0F1E2D3C4B5A6978L
        val original = photo("orig.jpg", pHash = base, dHash = base, colorSig = sig(3))
        val recompressed = photo(
            "orig-shared.jpg", pHash = flip(base, 2), dHash = flip(base, 3),
            colorSig = sig(3), size = 300_000,
        )
        val unrelated = photo("other.jpg", pHash = base.inv(), dHash = base.inv(), colorSig = sig(9))

        val result = Grouping.similarGroups(
            listOf(original, recompressed, unrelated), SimilarityLevel.BALANCED, KeeperRule.BEST_QUALITY
        )
        assertEquals(1, result.groups.size)
        assertEquals(2, result.groups.first().size)
        assertTrue(result.groups.first().others.none { it.photo.name == "other.jpg" })
    }

    @Test
    fun `matching hashes are rejected when the colour layout disagrees`() {
        val h = 0x1122334455667788L
        val a = photo("a.jpg", pHash = h, dHash = h, colorSig = sig(2))
        val b = photo("b.jpg", pHash = h, dHash = h, colorSig = sig(40))

        val result = Grouping.similarGroups(listOf(a, b), SimilarityLevel.BALANCED, KeeperRule.BEST_QUALITY)
        assertEquals("colour guard should split these", 0, result.groups.size)
    }

    @Test
    fun `matching hashes are rejected when the aspect ratio disagrees`() {
        val h = 0x1122334455667788L
        val square = photo("square.jpg", pHash = h, dHash = h, width = 2000, height = 2000)
        val wide = photo("wide.jpg", pHash = h, dHash = h, width = 3840, height = 1080)

        val result = Grouping.similarGroups(listOf(square, wide), SimilarityLevel.BALANCED, KeeperRule.BEST_QUALITY)
        assertEquals(0, result.groups.size)
    }

    @Test
    fun `flat images are skipped instead of being matched on meaningless hashes`() {
        val flat = ByteArray(48) { 128.toByte() }
        val a = photo("black1.jpg", pHash = 0, dHash = 0, colorSig = flat)
        val b = photo("black2.jpg", pHash = 0, dHash = 0, colorSig = flat)

        val result = Grouping.similarGroups(listOf(a, b), SimilarityLevel.BALANCED, KeeperRule.BEST_QUALITY)
        assertEquals(0, result.groups.size)
        assertEquals(2, result.skippedFlat)
    }

    @Test
    fun `a pure byte-identical set does not appear again under similar`() {
        val a = photo("a.jpg", sha = "Z", pHash = 7, dHash = 7, colorSig = sig(5))
        val b = photo("b.jpg", sha = "Z", pHash = 7, dHash = 7, colorSig = sig(5))

        val result = Grouping.similarGroups(listOf(a, b), SimilarityLevel.BALANCED, KeeperRule.BEST_QUALITY)
        assertEquals(0, result.groups.size)
    }

    @Test
    fun `a looser level never finds fewer groups than a stricter one`() {
        val base = 0x0F1E2D3C4B5A6978L
        val photos = (0..5).map { i ->
            photo("p$i.jpg", pHash = flip(base, i * 2), dHash = flip(base, i * 2), colorSig = sig(3))
        }
        val strict = Grouping.similarGroups(photos, SimilarityLevel.STRICT, KeeperRule.BEST_QUALITY)
        val loose = Grouping.similarGroups(photos, SimilarityLevel.LOOSE, KeeperRule.BEST_QUALITY)
        assertTrue(
            loose.groups.sumOf { it.size } >= strict.groups.sumOf { it.size }
        )
    }
}
