package az.pixclean.data

/**
 * A folder the app is *proposing* to create. Nothing on the device changes until the user
 * has read the list, renamed what they want and pressed the button — the original version
 * created albums as a side effect of grouping, which is a surprise nobody asked for.
 */
class ProposedFolder(
    val key: String,
    val suggestedName: String,
    val photoIds: List<Long>,
    /** Kept separately so renaming does not lose the suggestion. */
    val name: String = suggestedName,
    val enabled: Boolean = true,
) {
    val count: Int get() = photoIds.size

    fun withName(value: String) = ProposedFolder(key, suggestedName, photoIds, value, enabled)
    fun withEnabled(value: Boolean) = ProposedFolder(key, suggestedName, photoIds, name, value)
}

enum class FolderPlanKind(val label: String) {
    PEOPLE("Şəxslər üzrə"),
    MONTHS("Ay üzrə"),
}

object FolderPlans {

    /**
     * One folder per person, and every photo in exactly one of them.
     *
     * Copying would be the honest answer to a photo showing two people — but this is an app
     * whose whole job is removing duplicate files, and having it manufacture some is not a
     * trade worth making. So a shared photo goes to the larger group and the UI says so.
     */
    fun forPeople(people: List<PersonCluster>, minPhotos: Int = 2): List<ProposedFolder> {
        val claimed = HashSet<Long>()
        var unnamed = 0
        val out = ArrayList<ProposedFolder>()
        for (person in people.sortedByDescending { it.photoIds.size }) {
            val mine = person.photoIds.filter { claimed.add(it) }
            if (mine.size < minPhotos) continue
            val name = person.name?.takeIf { it.isNotBlank() } ?: "Şəxs ${++unnamed}"
            out.add(ProposedFolder("person-${person.clusterId}", name, mine))
        }
        return out
    }

    private val MONTHS = arrayOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun",
        "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr",
    )

    /**
     * One folder per month of capture. The name leads with the numeric year and month so a
     * file browser sorts them chronologically rather than alphabetically by month name.
     *
     * Capture time comes from EXIF where the photo has it. A downloaded or copied picture has
     * none, and falls back to the file's own timestamp — wrong for some, but a photo filed
     * under the month it arrived beats a photo filed nowhere.
     */
    fun forMonths(photos: List<Photo>): List<ProposedFolder> {
        val calendar = java.util.Calendar.getInstance()
        return photos
            .groupBy { photo ->
                calendar.timeInMillis = photo.capturedAt
                calendar.get(java.util.Calendar.YEAR) * 100 + calendar.get(java.util.Calendar.MONTH)
            }
            .toSortedMap(compareByDescending { it })
            .map { (stamp, group) ->
                val year = stamp / 100
                val month = stamp % 100
                ProposedFolder(
                    key = "month-$stamp",
                    suggestedName = "%04d-%02d %s".format(year, month + 1, MONTHS[month]),
                    photoIds = group.map { it.id },
                )
            }
    }
}
