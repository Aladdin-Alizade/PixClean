package az.pixclean.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import az.pixclean.data.FolderPlanKind
import az.pixclean.data.Photo
import az.pixclean.data.ProposedFolder
import az.pixclean.ui.components.EmptyState
import az.pixclean.ui.components.PhotoThumb
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Sizes
import az.pixclean.ui.theme.Space

/**
 * The folder list is a proposal, not an action. Names start filled in and stay editable, the
 * counts say exactly what will move where, and nothing touches the device until the button at
 * the bottom is pressed and the system asks for permission.
 */
@Composable
fun FoldersScreen(
    kind: FolderPlanKind,
    proposals: List<ProposedFolder>,
    photoIndex: Map<Long, Photo>,
    busy: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onCreate: (List<ProposedFolder>) -> Unit,
) {
    var folders by remember(proposals) { mutableStateOf(proposals) }
    val chosen = folders.filter { it.enabled && it.name.isNotBlank() }
    val photoCount = chosen.sumOf { it.count }

    if (proposals.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Header(kind, onBack)
            EmptyState(
                icon = Icons.Rounded.CreateNewFolder,
                title = "Ayırmağa bir şey yoxdur",
                body = when (kind) {
                    FolderPlanKind.PEOPLE ->
                        "Əvvəlcə üzləri tarayın. Ən azı iki şəkli olan hər qrup üçün bir qovluq təklif olunacaq."
                    FolderPlanKind.MONTHS ->
                        "Əvvəlcə şəkilləri tarayın."
                },
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + Space.xxxl * 2,
                start = Space.lg,
                end = Space.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item { Header(kind, onBack) }
            item {
                Text(
                    when (kind) {
                        FolderPlanKind.PEOPLE ->
                            "Hər şəkil bir qovluğa köçürüləcək. İki nəfərin olduğu şəkil daha böyük " +
                                "qrupun qovluğuna düşür — nüsxə çıxarılmır, çünki bu, təmizlədiyimiz " +
                                "dublikatların özündən olardı."
                        FolderPlanKind.MONTHS ->
                            "Hər şəkil çəkilmə tarixinə görə bir aya aid qovluğa köçürüləcək."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(folders, key = { it.key }) { folder ->
                FolderRow(
                    folder = folder,
                    photoIndex = photoIndex,
                    onName = { value ->
                        folders = folders.map { if (it.key == folder.key) it.withName(value) else it }
                    },
                    onToggle = {
                        folders = folders.map {
                            if (it.key == folder.key) it.withEnabled(!it.enabled) else it
                        }
                    },
                )
            }
        }

        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(
                    "Hələ heç nə dəyişməyib. Düyməyə basanda sistem icazə soruşacaq.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onCreate(chosen) },
                    enabled = !busy && chosen.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp))
                    Text(
                        if (chosen.isEmpty()) "Qovluq seçilməyib"
                        else "${chosen.size} qovluq yarat · $photoCount şəkil",
                        Modifier.padding(start = Space.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(kind: FolderPlanKind, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
        Column {
            Text("Qovluqlara ayır", style = MaterialTheme.typography.titleLarge)
            Text(
                kind.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: ProposedFolder,
    photoIndex: Map<Long, Photo>,
    onName: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Checkbox(checked = folder.enabled, onCheckedChange = { onToggle() })
                OutlinedTextField(
                    value = folder.name,
                    onValueChange = onName,
                    singleLine = true,
                    enabled = folder.enabled,
                    label = { Text("Qovluq adı") },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                folder.photoIds.take(4).forEach { id ->
                    PhotoThumb(
                        model = photoIndex[id]?.uri,
                        modifier = Modifier.size(Sizes.thumbSmall),
                    )
                }
                Text(
                    "${folder.count} şəkil",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
