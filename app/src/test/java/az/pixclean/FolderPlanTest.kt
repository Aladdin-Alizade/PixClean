package az.pixclean

import az.pixclean.data.FaceRow
import az.pixclean.data.FolderPlans
import az.pixclean.data.PersonCluster
import az.pixclean.data.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The folder list is what the user reads before agreeing to move their photos, so the counts
 * on it have to be exactly what will happen — no photo in two folders, none left out.
 */
class FolderPlanTest {

    private fun at(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { set(year, month - 1, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
            .timeInMillis

    private var next = 1L

    private fun photo(taken: Long = 0L, modifiedSeconds: Long = 0L) = Photo(
        id = next++, name = "p.jpg", bucket = "DCIM", relPath = "/x/p.jpg", size = 1000,
        width = 100, height = 100, dateAdded = 0, dateModified = modifiedSeconds,
        dateTaken = taken, mime = "image/jpeg",
    )

    private fun face(id: Long, photoId: Long) =
        FaceRow(id, photoId, 0, 0, 10, 10, 0.9f, FloatArray(4), 0, 0)

    private fun person(cluster: Int, name: String?, photoIds: List<Long>) = PersonCluster(
        clusterId = cluster,
        personId = 0,
        name = name,
        coverFace = face(cluster.toLong(), photoIds.first()),
        faces = photoIds.map { face(it, it) },
    )

    @Test
    fun `months are named so a file browser sorts them by date`() {
        val plans = FolderPlans.forMonths(
            listOf(
                photo(taken = at(2026, 8, 3)),
                photo(taken = at(2026, 8, 20)),
                photo(taken = at(2025, 12, 1)),
                photo(taken = at(2026, 1, 15)),
            )
        )
        assertEquals(3, plans.size)
        assertEquals(listOf("2026-08 Avqust", "2026-01 Yanvar", "2025-12 Dekabr"), plans.map { it.suggestedName })
        assertEquals(2, plans.first().count)
    }

    @Test
    fun `every photo lands in exactly one month`() {
        val photos = (1..40).map { photo(taken = at(2026, (it % 12) + 1, 1 + it % 20)) }
        val plans = FolderPlans.forMonths(photos)
        val placed = plans.flatMap { it.photoIds }
        assertEquals(photos.size, placed.size)
        assertEquals(photos.size, placed.distinct().size)
    }

    @Test
    fun `a photo with no capture time falls back to its file date`() {
        val onlyFileDate = photo(taken = 0L, modifiedSeconds = at(2026, 3, 9) / 1000)
        val plans = FolderPlans.forMonths(listOf(onlyFileDate))
        assertEquals("2026-03 Mart", plans.single().suggestedName)
    }

    @Test
    fun `capture time wins over the file date`() {
        val both = photo(taken = at(2024, 7, 4), modifiedSeconds = at(2026, 3, 9) / 1000)
        assertEquals("2024-07 İyul", FolderPlans.forMonths(listOf(both)).single().suggestedName)
    }

    @Test
    fun `a photo shared by two people is only moved once`() {
        // 7 is in both groups; the larger one keeps it, and nothing is duplicated.
        val big = person(1, "Anar", listOf(1L, 2L, 3L, 7L))
        val small = person(2, null, listOf(7L, 8L))

        val plans = FolderPlans.forPeople(listOf(small, big))
        val placed = plans.flatMap { it.photoIds }
        assertEquals(placed.size, placed.distinct().size)
        assertTrue("the bigger group keeps the shared photo", 7L in plans.first { it.suggestedName == "Anar" }.photoIds)
    }

    @Test
    fun `groups too small to be worth a folder are left alone`() {
        val plans = FolderPlans.forPeople(listOf(person(1, null, listOf(1L))), minPhotos = 2)
        assertTrue(plans.isEmpty())
    }
}
