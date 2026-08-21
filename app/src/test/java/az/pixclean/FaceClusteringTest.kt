package az.pixclean

import az.pixclean.data.FaceRow
import az.pixclean.faces.FaceClusterer
import az.pixclean.faces.FaceEmbedders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The clustering maths is the part that decides whether "same person" works, and it cannot
 * be exercised on a device without real faces of real people. These tests stand in for that:
 * synthetic identities with a known answer, so a regression in the seed/merge/settle passes
 * shows up as a failing count rather than as quietly worse grouping.
 */
class FaceClusteringTest {

    private val dim = 192
    private val PERSON_B = 77L

    private fun unit(rnd: Random): FloatArray =
        FaceEmbedders.l2Normalize(FloatArray(dim) { (rnd.nextFloat() - 0.5f) })

    private fun jitter(base: FloatArray, rnd: Random, scale: Float): FloatArray =
        FaceEmbedders.l2Normalize(FloatArray(dim) { base[it] + (rnd.nextFloat() - 0.5f) * scale })

    private fun face(id: Long, emb: FloatArray, quality: Float = 0.8f) =
        FaceRow(id, id, 0, 0, 100, 100, quality, emb, -1, 0)

    @Test
    fun `separate identities land in separate clusters`() {
        val rnd = Random(7)
        val identities = 6
        val perIdentity = 8
        val faces = ArrayList<FaceRow>()
        var id = 1L
        val truth = HashMap<Long, Int>()
        repeat(identities) { person ->
            val base = unit(rnd)
            repeat(perIdentity) {
                val e = jitter(base, rnd, 0.12f)
                faces.add(face(id, e))
                truth[id] = person
                id++
            }
        }

        val result = FaceClusterer.cluster(faces, threshold = 0.62f)
        assertEquals(identities * perIdentity, result.usedFaces)
        assertEquals("one cluster per identity", identities, result.clusterCount)

        // every cluster must be pure: no two identities share a cluster
        val byCluster = result.assignments.groupBy { it.clusterId }
        for ((cluster, members) in byCluster) {
            if (cluster < 0) continue
            val people = members.map { truth[it.faceId] }.distinct()
            assertEquals("cluster $cluster mixes identities", 1, people.size)
        }
    }

    @Test
    fun `low quality faces are parked rather than forced into a cluster`() {
        val rnd = Random(11)
        val base = unit(rnd)
        val good = (1L..5L).map { face(it, jitter(base, rnd, 0.1f), quality = 0.9f) }
        val blurry = face(99L, jitter(base, rnd, 0.1f), quality = 0.05f)

        val result = FaceClusterer.cluster(good + blurry, threshold = 0.62f)
        assertEquals(5, result.usedFaces)
        assertEquals(-1, result.assignments.first { it.faceId == 99L }.clusterId)
    }

    @Test
    fun `a stricter threshold never merges what a looser one kept apart`() {
        val rnd = Random(13)
        val faces = ArrayList<FaceRow>()
        var id = 1L
        repeat(4) {
            val base = unit(rnd)
            repeat(6) { faces.add(face(id++, jitter(base, rnd, 0.12f))) }
        }
        val strict = FaceClusterer.cluster(faces, 0.72f).clusterCount
        val loose = FaceClusterer.cluster(faces, 0.52f).clusterCount
        assertTrue("strict=$strict loose=$loose", strict >= loose)
    }

    @Test
    fun `a face the user moved by hand stays moved after re-clustering`() {
        val rnd = Random(23)
        val looksLikeA = unit(rnd)
        val looksLikeB = unit(rnd)
        val faces = ArrayList<FaceRow>()

        repeat(5) { faces.add(face(it + 1L, jitter(looksLikeA, rnd, 0.1f))) }
        repeat(5) {
            val id = it + 10L
            faces.add(FaceRow(id, id, 0, 0, 100, 100, 0.8f, jitter(looksLikeB, rnd, 0.1f), 0, PERSON_B))
        }
        // Looks like A to the maths, but the user says it is B. The pin has to win.
        val stray = FaceRow(99L, 99L, 0, 0, 100, 100, 0.8f, jitter(looksLikeA, rnd, 0.1f), -1, PERSON_B, pinned = true)
        faces.add(stray)

        val result = FaceClusterer.cluster(faces, threshold = 0.62f)
        val strayAssignment = result.assignments.first { it.faceId == 99L }
        val bCluster = result.assignments.first { it.faceId == 10L }.clusterId

        assertEquals("the moved face should sit with person B", bCluster, strayAssignment.clusterId)
        assertEquals(PERSON_B, strayAssignment.personId)
    }

    @Test
    fun `a pinned face survives the quality gate`() {
        val rnd = Random(29)
        val base = unit(rnd)
        val good = (1L..4L).map { face(it, jitter(base, rnd, 0.1f), quality = 0.9f) }
        val blurryButPinned =
            FaceRow(50L, 50L, 0, 0, 100, 100, 0.02f, jitter(base, rnd, 0.1f), 7, PERSON_B, pinned = true)

        val result = FaceClusterer.cluster(good + blurryButPinned, threshold = 0.62f)
        val pinned = result.assignments.first { it.faceId == 50L }
        assertEquals(7, pinned.clusterId)
        assertEquals(PERSON_B, pinned.personId)
    }

    @Test
    fun `naming survives a re-cluster`() {
        val rnd = Random(17)
        val base = unit(rnd)
        val named = (1L..6L).map {
            FaceRow(it, it, 0, 0, 100, 100, 0.8f, jitter(base, rnd, 0.1f), 0, personId = 42L)
        }
        val result = FaceClusterer.cluster(named, 0.62f)
        assertTrue(result.assignments.filter { it.clusterId >= 0 }.all { it.personId == 42L })
    }
}
