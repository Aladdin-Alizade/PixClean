package az.pixclean.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.pixclean.core.EngineState
import az.pixclean.core.Prefs
import az.pixclean.data.PersonCluster
import az.pixclean.ui.components.EmptyState
import az.pixclean.ui.components.FaceThumb
import az.pixclean.ui.theme.PixTheme
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Sizes
import az.pixclean.ui.theme.Space

@Composable
fun PeopleScreen(
    state: EngineState,
    prefs: Prefs,
    contentPadding: PaddingValues,
    onScanFaces: () -> Unit,
    onOpenPerson: (PersonCluster) -> Unit,
    onRename: (PersonCluster, String) -> Unit,
    onMerge: (PersonCluster, List<PersonCluster>) -> Unit,
) {
    val shown = remember(state.people, prefs.minPeopleGroup) {
        state.people.filter { it.faces.size >= prefs.minPeopleGroup }
    }
    val singles = remember(state.people, prefs.minPeopleGroup) {
        state.people.filter { it.faces.size < prefs.minPeopleGroup }
    }

    var mergeMode by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<Int>()) }

    if (state.people.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Groups,
            title = if (state.running) "Üzlər analiz edilir" else "Hələ üz taranmayıb",
            body = "Hər şəkildə üz axtarılır, hər üz vektora çevrilir və eyni adam bir qrupa yığılır. " +
                "Bu, dublikat taramasından yavaşdır — arxa fonda da davam edir.",
            modifier = Modifier.padding(contentPadding),
        ) {
            Button(onClick = onScanFaces, enabled = !state.running) { Text("Üzləri tara") }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Sizes.faceCover),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + Space.lg,
                bottom = contentPadding.calculateBottomPadding() + Space.xxl,
                start = Space.lg,
                end = Space.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item(span = { GridItemSpanMax() }) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Şəxslər", style = MaterialTheme.typography.headlineSmall)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${shown.size} qrup · ${state.faceCount} üz",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            if (mergeMode && picked.size >= 2) {
                                val chosen = state.people.filter { it.clusterId in picked }
                                onMerge(chosen.first(), chosen.drop(1))
                                picked = emptySet()
                                mergeMode = false
                            } else {
                                mergeMode = !mergeMode
                                picked = emptySet()
                            }
                        }) {
                            Text(
                                when {
                                    mergeMode && picked.size >= 2 -> "${picked.size} qrupu birləşdir"
                                    mergeMode -> "İmtina"
                                    else -> "Birləşdir"
                                }
                            )
                        }
                    }
                }
            }

            if (!state.modelBacked) {
                item(span = { GridItemSpanMax() }) { AccuracyNote() }
            }

            items(shown, key = { it.clusterId }) { person ->
                PersonTile(
                    person = person,
                    photoUri = state.photoIndex[person.coverFace.photoId]?.uri,
                    picked = person.clusterId in picked,
                    mergeMode = mergeMode,
                    onClick = {
                        if (mergeMode) {
                            picked = if (person.clusterId in picked) picked - person.clusterId
                            else picked + person.clusterId
                        } else onOpenPerson(person)
                    },
                )
            }

            if (singles.isNotEmpty()) {
                item(span = { GridItemSpanMax() }) {
                    Text(
                        "Tək qalan üzlər (${singles.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.md),
                    )
                }
                items(singles, key = { it.clusterId }) { person ->
                    PersonTile(
                        person = person,
                        photoUri = state.photoIndex[person.coverFace.photoId]?.uri,
                        picked = person.clusterId in picked,
                        mergeMode = mergeMode,
                        onClick = {
                            if (mergeMode) {
                                picked = if (person.clusterId in picked) picked - person.clusterId
                                else picked + person.clusterId
                            } else onOpenPerson(person)
                        },
                    )
                }
            }
        }
    }
}

@Suppress("FunctionName")
private fun GridItemSpanMax() = androidx.compose.foundation.lazy.grid.GridItemSpan(Int.MAX_VALUE)

@Composable
private fun PersonTile(
    person: PersonCluster,
    photoUri: Any?,
    picked: Boolean,
    mergeMode: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        FaceThumb(
            model = photoUri,
            left = person.coverFace.left,
            top = person.coverFace.top,
            right = person.coverFace.right,
            bottom = person.coverFace.bottom,
            size = Sizes.faceCover,
            ring = when {
                mergeMode && picked -> PixTheme.colors.people
                mergeMode -> MaterialTheme.colorScheme.outline
                else -> null
            },
        )
        Text(
            person.displayName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${person.photoIds.size} şəkil",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccuracyNote() {
    Surface(
        color = PixTheme.colors.similarContainer,
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Icon(
                Icons.Rounded.Info, null,
                tint = PixTheme.colors.similar,
                modifier = Modifier.padding(top = 2.dp).size(20.dp),
            )
            Text(
                "Bu qruplar sadə HOG deskriptoru ilə qurulub. Eyni gün, eyni işıqda çəkilmiş üzləri yaxşı tutur, " +
                    "amma illər arası dəyişikliyi tutmur. Tənzimləmələrdən .tflite üz modeli əlavə edin.",
                style = MaterialTheme.typography.bodyMedium,
                color = PixTheme.colors.onSimilarContainer,
            )
        }
    }
}
