package az.pixclean.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import az.pixclean.core.EngineState
import az.pixclean.core.Phase
import az.pixclean.core.Prefs
import az.pixclean.ui.components.humanBytes
import az.pixclean.ui.theme.PixTheme
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Space

@Composable
fun HomeScreen(
    state: EngineState,
    prefs: Prefs,
    contentPadding: PaddingValues,
    onScanPhotos: () -> Unit,
    onScanFaces: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + Space.lg,
            bottom = contentPadding.calculateBottomPadding() + Space.xxl,
            start = Space.lg,
            end = Space.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("PixClean", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (state.photoCount > 0) "${state.photoCount} şəkil indekslənib"
                        else "Hər şey telefonun içində analiz olunur",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Tənzimləmələr") }
            }
        }

        if (state.running) {
            item { ProgressCard(state, onCancel) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ContentCopy,
                    tint = MaterialTheme.colorScheme.error,
                    container = MaterialTheme.colorScheme.errorContainer,
                    value = state.exact.size.toString(),
                    label = "eyni fayl qrupu",
                    detail = humanBytes(state.exactWaste) + " artıq",
                    onClick = { onOpen("exact") },
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Difference,
                    tint = PixTheme.colors.similar,
                    container = PixTheme.colors.similarContainer,
                    value = state.similar.size.toString(),
                    label = "oxşar qrup",
                    detail = humanBytes(state.similarWaste) + " artıq",
                    onClick = { onOpen("similar") },
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Groups,
                    tint = PixTheme.colors.people,
                    container = PixTheme.colors.peopleContainer,
                    value = state.people.size.toString(),
                    label = "şəxs qrupu",
                    detail = "${state.faceCount} üz",
                    onClick = { onOpen("people") },
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PhotoLibrary,
                    tint = PixTheme.colors.keep,
                    container = PixTheme.colors.keepContainer,
                    value = state.photoCount.toString(),
                    label = "şəkil",
                    detail = if (state.flatSkipped > 0) "${state.flatSkipped} boş şəkil nəzərə alınmadı" else "indeksdə",
                    onClick = null,
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                Button(
                    onClick = onScanPhotos,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Text("Dublikatları tara", Modifier.padding(start = Space.sm))
                }
                OutlinedButton(
                    onClick = onScanFaces,
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Text("Üzləri tara və qruplaşdır", Modifier.padding(start = Space.sm))
                }
                OutlinedButton(
                    onClick = { onOpen("folders/people") },
                    enabled = !state.running && state.people.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp))
                    Text("Şəxslərə görə qovluqlara ayır", Modifier.padding(start = Space.sm))
                }
            }
        }

        if (state.faceCount > 0 && !state.modelBacked) {
            item { ModelHint(onSettings) }
        }

        item {
            Text(
                "Eyni fayllar dəqiq müqayisə ilə tapılır — burada səhv ehtimalı yoxdur. " +
                    "Oxşar şəkillər isə şəklin özünə baxılaraq tapılır: kiçildilmiş, yenidən " +
                    "göndərilmiş və ya sıxılmış nüsxələr. " +
                    "Hazırkı həssaslıq: ${prefs.similarity.label}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

@Composable
private fun ProgressCard(state: EngineState, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.phase.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                TextButton(onClick = onCancel) { Text("Dayandır") }
            }
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.pill)),
                    drawStopIndicator = {},
                )
                Text(
                    "${state.done} / ${state.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.pill))
                )
            }
            Text(
                "Arxa fonda davam edir — tətbiqi bağlaya, telefonu kilidləyə bilərsiniz. " +
                    "Ekran sönsə də tarama dayanmır.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ModelHint(onSettings: () -> Unit) {
    Surface(
        color = PixTheme.colors.similarContainer,
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Rounded.Info, null,
                tint = PixTheme.colors.similar,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(
                    "Üz tanıma məhdud rejimdədir",
                    style = MaterialTheme.typography.titleMedium,
                    color = PixTheme.colors.onSimilarContainer,
                )
                Text(
                    "Tətbiqin içindəki üz tanıma modeli yüklənmədi, ona görə sadə üsul işləyir — " +
                        "bu, yalnız yaxın vaxtda, oxşar şəraitdə çəkilmiş üzlərdə etibarlıdır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PixTheme.colors.onSimilarContainer,
                )
                TextButton(onClick = onSettings, contentPadding = PaddingValues(0.dp)) {
                    Text("Tənzimləmələrə bax", color = PixTheme.colors.similar)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    tint: Color,
    container: Color,
    value: String,
    label: String,
    detail: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.lg),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
