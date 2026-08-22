package az.pixclean.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import az.pixclean.data.Photo
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Space

/**
 * Choosing which person some photos really belong to. Faces are shown rather than names,
 * because half the groups have no name yet and a face is what the user is matching against.
 */
@Composable
fun PersonPicker(
    people: List<az.pixclean.data.PersonCluster>,
    coverOf: (az.pixclean.data.PersonCluster) -> Any?,
    onPick: (az.pixclean.data.PersonCluster?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hansı qrupa köçürülsün?") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                modifier = Modifier.heightIn(max = 380.dp),
            ) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .clickable { onPick(null) }
                            .padding(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Yeni qrup yarat", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                items(people, key = { it.clusterId }) { person ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.md))
                            .clickable { onPick(person) }
                            .padding(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        FaceThumb(
                            model = coverOf(person),
                            left = person.coverFace.left,
                            top = person.coverFace.top,
                            right = person.coverFace.right,
                            bottom = person.coverFace.bottom,
                            size = 40.dp,
                        )
                        Column {
                            Text(person.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${person.photoIds.size} şəkil",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

/**
 * The bar that appears once something is selected. Removal is one tap away but the wording
 * always says where the files go, and the recoverable option is the filled button.
 */
@Composable
fun SelectionBar(
    selected: List<Photo>,
    trashAvailable: Boolean,
    onRemove: (toTrash: Boolean) -> Unit,
    onMove: (album: String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    extraAction: (@Composable () -> Unit)? = null,
) {
    var confirmHardDelete by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }

    // A scan finishing rebuilds the groups and empties the selection underneath whatever is
    // open. Leaving the dialog up would have it offer to delete nothing, and the button would
    // do nothing when pressed — which reads as the app having locked up.
    LaunchedEffect(selected.isEmpty()) {
        if (selected.isEmpty()) {
            confirmHardDelete = false
            moveDialog = false
        }
    }

    AnimatedVisibility(
        visible = selected.isNotEmpty(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
        ) {
            Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${selected.size} şəkil seçilib", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${humanBytes(selected.sumOf { it.size })} azad olacaq",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onClear) { Text("Ləğv et") }
                }
                extraAction?.invoke()
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (trashAvailable) {
                        Button(
                            onClick = { onRemove(true) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                            Text("Zibilə at", Modifier.padding(start = Space.sm))
                        }
                    }
                    OutlinedButton(
                        onClick = { moveDialog = true },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.DriveFileMove, null, Modifier.size(18.dp))
                        Text("Köçür", Modifier.padding(start = Space.sm))
                    }
                    OutlinedButton(
                        onClick = { confirmHardDelete = true },
                        enabled = !busy,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (confirmHardDelete) {
        AlertDialog(
            onDismissRequest = { confirmHardDelete = false },
            title = { Text("Həmişəlik silinsin?") },
            text = {
                Text(
                    "${selected.size} şəkil (${humanBytes(selected.sumOf { it.size })}) " +
                        "geri qaytarılmadan silinəcək. Zibil qutusuna atsanız 30 gün ərzində geri qaytara bilərsiniz."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmHardDelete = false; onRemove(false) }) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmHardDelete = false }) { Text("İmtina") } },
        )
    }

    if (moveDialog) {
        var album by remember { mutableStateOf("PixClean") }
        AlertDialog(
            onDismissRequest = { moveDialog = false },
            title = { Text("Albom seç") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        "Şəkillər Pictures qovluğunda bu adda albom altına köçürüləcək. " +
                            "Silinmir — sonra özünüz baxıb qərar verə bilərsiniz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        singleLine = true,
                        label = { Text("Albom adı") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { moveDialog = false; onMove(album) }) { Text("Köçür") }
            },
            dismissButton = { TextButton(onClick = { moveDialog = false }) { Text("İmtina") } },
        )
    }
}
