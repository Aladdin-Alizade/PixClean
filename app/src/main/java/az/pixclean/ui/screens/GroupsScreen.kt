package az.pixclean.ui.screens

import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.pixclean.core.EngineState
import az.pixclean.data.GroupKind
import az.pixclean.data.Photo
import az.pixclean.data.PhotoGroup
import az.pixclean.ui.LocalActions
import az.pixclean.ui.components.EmptyState
import az.pixclean.ui.components.PhotoThumb
import az.pixclean.ui.components.Pill
import az.pixclean.ui.components.SelectionBar
import az.pixclean.ui.components.humanBytes
import az.pixclean.ui.components.similarityWords
import az.pixclean.ui.theme.PixTheme
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Sizes
import az.pixclean.ui.theme.Space

@Composable
fun GroupsScreen(
    title: String,
    groups: List<PhotoGroup>,
    state: EngineState,
    useTrash: Boolean,
    contentPadding: PaddingValues,
    onOpenGroup: (PhotoGroup) -> Unit,
    onScan: () -> Unit,
) {
    val actions = LocalActions.current
    var selected by remember(groups) { mutableStateOf(emptySet<Long>()) }
    val byId = remember(groups) { groups.flatMap { it.others.map { m -> m.photo } }.associateBy { it.id } }
    val selectedPhotos = remember(selected, byId) { selected.mapNotNull { byId[it] } }
    val trashAvailable = useTrash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Box(Modifier.fillMaxSize()) {
        if (groups.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.ContentCopy,
                title = if (state.photoCount == 0) "Hələ heç nə taranmayıb" else "Heç nə tapılmadı",
                body = if (state.photoCount == 0)
                    "Başlamaq üçün qalereyanı tarayın. Minlərlə şəkil olsa da problem deyil — " +
                        "nəticələr yadda saxlanır, növbəti dəfə yalnız yeni şəkillər analiz olunur."
                else "Bu meyarlara uyğun qrup yoxdur. Tənzimləmələrdə həssaslığı dəyişə bilərsiniz.",
                modifier = Modifier.padding(contentPadding),
            ) {
                if (state.photoCount == 0) {
                    OutlinedButton(onClick = onScan, enabled = !state.running) { Text("Tara") }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + Space.lg,
                    bottom = contentPadding.calculateBottomPadding() + Space.xxxl * 2,
                    start = Space.lg,
                    end = Space.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${groups.size} qrup · ${humanBytes(groups.sumOf { it.reclaimable })} azad edilə bilər",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = {
                                selected = if (selected.size == byId.size) emptySet() else byId.keys
                            }) {
                                Text(if (selected.size == byId.size) "Seçimi sıfırla" else "Hamısını seç")
                            }
                        }
                    }
                }

                items(groups, key = { it.keeper.id }) { group ->
                    GroupCard(
                        group = group,
                        selected = selected,
                        onToggleGroup = {
                            val ids = group.others.map { it.photo.id }.toSet()
                            selected = if (selected.containsAll(ids)) selected - ids else selected + ids
                        },
                        onOpen = { onOpenGroup(group) },
                    )
                }
            }
        }

        SelectionBar(
            selected = selectedPhotos,
            trashAvailable = trashAvailable,
            onRemove = { toTrash -> actions.remove(selectedPhotos, toTrash) { selected = emptySet() } },
            onMove = { album -> actions.move(selectedPhotos, album) { selected = emptySet() } },
            onClear = { selected = emptySet() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }
}

@Composable
private fun GroupCard(
    group: PhotoGroup,
    selected: Set<Long>,
    onToggleGroup: () -> Unit,
    onOpen: () -> Unit,
) {
    val ids = group.others.map { it.photo.id }
    val allSelected = ids.isNotEmpty() && selected.containsAll(ids)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable(onClick = onOpen).padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhotoThumb(
                    model = group.keeper.uri,
                    modifier = Modifier.size(Sizes.thumbSmall),
                    ring = PixTheme.colors.keep,
                )
                Icon(
                    Icons.Rounded.CheckCircle, null,
                    tint = PixTheme.colors.keep,
                    modifier = Modifier.size(16.dp),
                )
                group.others.take(3).forEach { member ->
                    PhotoThumb(
                        model = member.photo.uri,
                        modifier = Modifier.size(Sizes.thumbSmall),
                        ring = if (member.photo.id in selected) MaterialTheme.colorScheme.error else null,
                    )
                }
                if (group.others.size > 3) {
                    Box(
                        Modifier.size(Sizes.thumbSmall),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "+${group.others.size - 3}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        group.keeper.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        if (group.kind == GroupKind.EXACT) {
                            Pill("tam eyni fayl", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
                        } else {
                            val worst = group.others.maxOfOrNull { it.distance } ?: 0
                            Pill(similarityWords(worst), PixTheme.colors.similar, PixTheme.colors.similarContainer)
                        }
                        Text(
                            "${group.others.size} əlavə nüsxə · ${humanBytes(group.reclaimable)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Checkbox(checked = allSelected, onCheckedChange = { onToggleGroup() })
            }
        }
    }
}
