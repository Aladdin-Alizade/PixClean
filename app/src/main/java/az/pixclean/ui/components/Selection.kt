package az.pixclean.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import az.pixclean.data.Photo
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Space

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
) {
    var confirmHardDelete by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (trashAvailable) {
                        Button(
                            onClick = { onRemove(true) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                            Text("Zibilə at", Modifier.padding(start = Space.sm))
                        }
                    }
                    OutlinedButton(
                        onClick = { moveDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.DriveFileMove, null, Modifier.size(18.dp))
                        Text("Köçür", Modifier.padding(start = Space.sm))
                    }
                    OutlinedButton(
                        onClick = { confirmHardDelete = true },
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
