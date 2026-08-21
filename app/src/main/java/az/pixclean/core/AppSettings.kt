package az.pixclean.core

import android.content.Context
import az.pixclean.dup.KeeperRule
import az.pixclean.dup.SimilarityLevel
import az.pixclean.faces.FaceDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs(
    val similarity: SimilarityLevel,
    val faceLevel: SimilarityLevel,
    val keeperRule: KeeperRule,
    val useTrash: Boolean,
    val minPeopleGroup: Int,
    /** Album names the user has switched off; empty means the whole gallery. */
    val excludedBuckets: Set<String>,
    /** Tree uris of folders the user picked. Empty means "the whole gallery". */
    val scanRoots: Set<String>,
    val faceDetail: FaceDetail,
)

class AppSettings(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("pixclean", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Prefs> = _state

    val value: Prefs get() = _state.value

    private fun read() = Prefs(
        similarity = enumOr(sp.getString(K_SIM, null), SimilarityLevel.BALANCED),
        faceLevel = enumOr(sp.getString(K_FACE, null), SimilarityLevel.BALANCED),
        keeperRule = keeperOr(sp.getString(K_KEEP, null)),
        useTrash = sp.getBoolean(K_TRASH, true),
        minPeopleGroup = sp.getInt(K_MIN_PEOPLE, 2),
        excludedBuckets = sp.getStringSet(K_EXCLUDED, emptySet()).orEmpty().toSet(),
        scanRoots = sp.getStringSet(K_ROOTS, emptySet()).orEmpty().toSet(),
        faceDetail = FaceDetail.entries.firstOrNull { it.name == sp.getString(K_DETAIL, null) }
            ?: FaceDetail.NORMAL,
    )

    fun update(
        similarity: SimilarityLevel = value.similarity,
        faceLevel: SimilarityLevel = value.faceLevel,
        keeperRule: KeeperRule = value.keeperRule,
        useTrash: Boolean = value.useTrash,
        minPeopleGroup: Int = value.minPeopleGroup,
        excludedBuckets: Set<String> = value.excludedBuckets,
        scanRoots: Set<String> = value.scanRoots,
        faceDetail: FaceDetail = value.faceDetail,
    ) {
        sp.edit()
            .putString(K_SIM, similarity.name)
            .putString(K_FACE, faceLevel.name)
            .putString(K_KEEP, keeperRule.name)
            .putBoolean(K_TRASH, useTrash)
            .putInt(K_MIN_PEOPLE, minPeopleGroup)
            .putStringSet(K_EXCLUDED, excludedBuckets)
            .putStringSet(K_ROOTS, scanRoots)
            .putString(K_DETAIL, faceDetail.name)
            .apply()
        _state.value =
            Prefs(
                similarity, faceLevel, keeperRule, useTrash, minPeopleGroup,
                excludedBuckets, scanRoots, faceDetail,
            )
    }

    private fun enumOr(name: String?, fallback: SimilarityLevel) =
        SimilarityLevel.entries.firstOrNull { it.name == name } ?: fallback

    private fun keeperOr(name: String?) =
        KeeperRule.entries.firstOrNull { it.name == name } ?: KeeperRule.BEST_QUALITY

    private companion object {
        const val K_SIM = "similarity"
        const val K_FACE = "faceLevel"
        const val K_KEEP = "keeper"
        const val K_TRASH = "trash"
        const val K_MIN_PEOPLE = "minPeople"
        const val K_EXCLUDED = "excludedBuckets"
        const val K_ROOTS = "scanRoots"
        const val K_DETAIL = "faceDetail"
    }
}
