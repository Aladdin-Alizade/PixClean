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
}
