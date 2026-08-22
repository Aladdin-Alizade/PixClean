package az.pixclean.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.pixclean.data.GroupKind
import az.pixclean.data.Photo
import az.pixclean.data.PhotoGroup
import az.pixclean.ui.LocalActions
import az.pixclean.ui.components.EmptyState
import az.pixclean.ui.components.PhotoThumb
import az.pixclean.ui.components.TileButton
import az.pixclean.ui.components.TileCheckbox
import az.pixclean.ui.components.Pill
import az.pixclean.ui.components.SelectionBar
import az.pixclean.ui.components.humanBytes
import az.pixclean.ui.components.similarityWords
import az.pixclean.ui.theme.PixTheme
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Space
import coil3.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE = SimpleDateFormat("dd.MM.yyyy", Locale("az"))

fun Photo.dateText(): String = DATE.format(Date(dateModified * 1000L))

/**
 * The screen the whole app exists for: the copy being kept on the left, everything the
 * scanner thinks is the same picture on the right, and no action taken until you pick.
 */
@Composable
fun GroupDetailScreen(
    group: PhotoGroup?,
    useTrash: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    if (group == null) {
        EmptyState(
            icon = Icons.Rounded.CheckCircle,
            title = "Bu qrup artıq yoxdur",
            body = "Şəkillər silindiyi və ya köçürüldüyü üçün qrup dağıldı.",
            modifier = Modifier.padding(contentPadding),
        ) { TextButton(onClick = onBack) { Text("Geri") } }
        return
    }

    val actions = LocalActions.current
    val working by actions.busy.collectAsStateWithLifecycle()
    val members = remember(group) { listOf(group.keeper) + group.others.map { it.photo } }
    val distances = remember(group) { group.others.associate { it.photo.id to it.distance } }

    var keeperId by remember(group) { mutableStateOf(group.keeper.id) }
    var selected by remember(group) { mutableStateOf(emptySet<Long>()) }
    var preview by remember { mutableStateOf<Photo?>(null) }

    val keeper = members.firstOrNull { it.id == keeperId } ?: group.keeper
    val others = members.filter { it.id != keeper.id }
    val selectedPhotos = others.filter { it.id in selected }
    val trashAvailable = useTrash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (group.kind == GroupKind.EXACT) "Eyni fayllar" else "Oxşar şəkillər",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${members.size} nüsxə · ${humanBytes(others.sumOf { it.size })} azad oluna bilər",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    val ids = others.map { it.id }.toSet()
                    selected = if (selected.containsAll(ids)) emptySet() else ids
                }) {
                    Text(if (selected.size == others.size && others.isNotEmpty()) "Sıfırla" else "Hamısı")
                }
            }

            Row(Modifier.fillMaxSize().padding(horizontal = Space.lg)) {
                KeeperPane(
                    photo = keeper,
                    modifier = Modifier.weight(0.42f).verticalScroll(rememberScrollState()),
                    onPreview = { preview = keeper },
                )
                Column(Modifier.weight(0.58f).padding(start = Space.md)) {
                    Text(
                        "Silinə bilər (${others.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                        contentPadding = PaddingValues(bottom = Space.xxxl * 2),
                    ) {
                        items(others, key = { it.id }) { photo ->
                            DuplicateTile(
                                photo = photo,
                                distance = distances[photo.id],
                                showDistance = group.kind == GroupKind.SIMILAR,
                                checked = photo.id in selected,
                                onToggle = {
                                    selected = if (photo.id in selected) selected - photo.id else selected + photo.id
                                },
                                onPreview = { preview = photo },
                                onMakeKeeper = {
                                    keeperId = photo.id
                                    selected = selected - photo.id
                                },
                            )
                        }
                    }
                }
            }
        }

        SelectionBar(
            selected = selectedPhotos,
            trashAvailable = trashAvailable,
            busy = working,
            onRemove = { toTrash -> actions.remove(selectedPhotos, toTrash) { selected = emptySet() } },
            onMove = { album -> actions.move(selectedPhotos, album) { selected = emptySet() } },
            onClear = { selected = emptySet() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }

    preview?.let { photo ->
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Bağla") } },
            title = { Text(photo.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)),
                    )
                    MetaLine(photo)
                }
            },
        )
    }
}

@Composable
private fun KeeperPane(photo: Photo, modifier: Modifier = Modifier, onPreview: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Pill("SAXLANIR", PixTheme.colors.keep, PixTheme.colors.keepContainer, icon = Icons.Rounded.CheckCircle)
        Box {
            PhotoThumb(
                model = photo.uri,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                ring = PixTheme.colors.keep,
                ringWidth = 2.dp,
            )
            TileButton(
                icon = Icons.Rounded.Visibility,
                label = "Böyüt",
                onClick = onPreview,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.sm),
            )
        }
        Text(
            photo.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MetaLine(photo)
    }
}

@Composable
private fun MetaLine(photo: Photo) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(
            "${photo.width}×${photo.height} · ${humanBytes(photo.size)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${photo.dateText()} · ${photo.bucket}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DuplicateTile(
    photo: Photo,
    distance: Int?,
    showDistance: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onMakeKeeper: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box {
            PhotoThumb(
                model = photo.uri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable(onClick = onToggle),
                ring = if (checked) MaterialTheme.colorScheme.error else null,
            )
            TileCheckbox(
                checked = checked,
                onToggle = onToggle,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Row(
                Modifier.align(Alignment.BottomEnd).padding(Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                TileButton(Icons.Rounded.PushPin, "Bunu saxla", onMakeKeeper)
                TileButton(Icons.Rounded.Visibility, "Böyüt", onPreview)
            }
        }
        Text(
            "${photo.width}×${photo.height}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                humanBytes(photo.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showDistance && distance != null) {
                Pill(similarityWords(distance), PixTheme.colors.similar, PixTheme.colors.similarContainer)
            }
        }
    }
}
