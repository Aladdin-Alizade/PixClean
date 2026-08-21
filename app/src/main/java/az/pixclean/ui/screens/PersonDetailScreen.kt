package az.pixclean.ui.screens

import android.os.Build
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import az.pixclean.data.PersonCluster
import az.pixclean.data.Photo
import az.pixclean.ui.LocalActions
import az.pixclean.ui.components.EmptyState
import az.pixclean.ui.components.FaceThumb
import az.pixclean.ui.components.PhotoThumb
import az.pixclean.ui.components.TileCheckbox
import az.pixclean.ui.components.SelectionBar
import az.pixclean.ui.theme.PixTheme
import az.pixclean.ui.theme.Sizes
import az.pixclean.ui.theme.Space

@Composable
fun PersonDetailScreen(
    cluster: PersonCluster?,
    photoIndex: Map<Long, Photo>,
    useTrash: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onRename: (PersonCluster, String) -> Unit,
) {
    if (cluster == null) {
        EmptyState(
            icon = Icons.Rounded.Groups,
            title = "Qrup tapılmadı",
            body = "Üz indeksi yenidən qurulduğu üçün bu qrup dəyişdi.",
            modifier = Modifier.padding(contentPadding),
        ) { TextButton(onClick = onBack) { Text("Geri") } }
        return
    }

    val actions = LocalActions.current
    val photos = remember(cluster, photoIndex) { cluster.photoIds.mapNotNull { photoIndex[it] } }
    var selected by remember(cluster) { mutableStateOf(emptySet<Long>()) }
    var renaming by remember { mutableStateOf(false) }
    val selectedPhotos = photos.filter { it.id in selected }
    val trashAvailable = useTrash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Sizes.thumbGrid),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + Space.xxxl * 2,
                start = Space.lg,
                end = Space.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                    FaceThumb(
                        model = photoIndex[cluster.coverFace.photoId]?.uri,
                        left = cluster.coverFace.left,
                        top = cluster.coverFace.top,
                        right = cluster.coverFace.right,
                        bottom = cluster.coverFace.bottom,
                        size = 44.dp,
                        ring = PixTheme.colors.people,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(cluster.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${photos.size} şəkil · ${cluster.faces.size} üz",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Adını dəyiş") }
                    TextButton(onClick = {
                        val ids = photos.map { it.id }.toSet()
                        selected = if (selected.containsAll(ids)) emptySet() else ids
                    }) { Text(if (selected.size == photos.size && photos.isNotEmpty()) "Sıfırla" else "Hamısı") }
                }
            }

            items(photos, key = { it.id }) { photo ->
                Box {
                    PhotoThumb(
                        model = photo.uri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable {
                                selected = if (photo.id in selected) selected - photo.id else selected + photo.id
                            },
                        ring = if (photo.id in selected) MaterialTheme.colorScheme.error else null,
                    )
                    TileCheckbox(
                        checked = photo.id in selected,
                        onToggle = {
                            selected = if (photo.id in selected) selected - photo.id else selected + photo.id
                        },
                        modifier = Modifier.align(Alignment.TopStart),
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

    if (renaming) {
        var name by remember { mutableStateOf(cluster.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Kimdir?") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Ad") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { renaming = false; if (name.isNotBlank()) onRename(cluster, name) },
                ) { Text("Yadda saxla") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("İmtina") } },
        )
    }
}
