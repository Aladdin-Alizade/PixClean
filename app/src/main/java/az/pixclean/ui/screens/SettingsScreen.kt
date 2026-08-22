package az.pixclean.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import az.pixclean.core.EngineState
import az.pixclean.core.Prefs
import az.pixclean.core.ScanEngine
import az.pixclean.dup.KeeperRule
import az.pixclean.dup.SimilarityLevel
import az.pixclean.data.FolderScanner
import java.io.File
import az.pixclean.faces.FaceDetail
import az.pixclean.faces.FaceEmbedders
import az.pixclean.ui.LocalActions
import az.pixclean.ui.theme.PixTheme
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    engine: ScanEngine,
    state: EngineState,
    prefs: Prefs,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var imported by remember { mutableStateOf(FaceEmbedders.hasImportedModel(context)) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        engine.settings.update(scanRoots = prefs.scanRoots + uri.toString())
        actions.toast("«${FolderScanner.label(uri)}» əlavə olundu — taranır")
        engine.scanPhotos()
    }

    val importModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Import into a staging file and only promote it once the interpreter actually
            // loads it, so a mistyped pick cannot leave a broken "model" installed.
            val error = withContext(Dispatchers.IO) {
                val target = FaceEmbedders.modelPath(context)
                val staging = File(target.parentFile, "face.tflite.incoming")
                runCatching {
                    staging.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        staging.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching "Fayl oxunmadı"
                    val problem = FaceEmbedders.validate(staging)
                    if (problem != null) {
                        staging.delete()
                        problem
                    } else {
                        target.delete()
                        if (staging.renameTo(target)) null else "Fayl yadda saxlanmadı"
                    }
                }.getOrElse { staging.delete(); "Fayl oxunmadı" }
            }
            imported = FaceEmbedders.hasImportedModel(context)
            if (error == null) {
                actions.toast("Model əlavə olundu. Üzləri yenidən tarayın.")
                engine.resetFaceIndex()
            } else {
                actions.toast(error)
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + Space.xxl,
            start = Space.lg,
            end = Space.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.sm)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
                Text("Tənzimləmələr", style = MaterialTheme.typography.headlineSmall)
            }
        }

        item {
            Group(
                "Taranacaq qovluqlar",
                if (prefs.scanRoots.isEmpty())
                    "Hazırda bütün qalereya taranır. Konkret bir qovluq seçsəniz, yalnız orada " +
                        "(və alt qovluqlarında) olan şəkillər analiz olunacaq — qalereyada " +
                        "görünməyən fayllar da daxil."
                else
                    "Yalnız aşağıdakı qovluqlar və onların alt qovluqları analiz olunur.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    prefs.scanRoots.forEach { root ->
                        val uri = remember(root) { Uri.parse(root) }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(Radius.md),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(start = Space.md, top = Space.sm, bottom = Space.sm, end = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Folder, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(Modifier.weight(1f).padding(start = Space.md)) {
                                    Text(
                                        FolderScanner.label(uri),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        FolderScanner.treePath(uri) ?: root,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.MiddleEllipsis,
                                    )
                                }
                                IconButton(enabled = !state.running, onClick = {
                                    engine.settings.update(scanRoots = prefs.scanRoots - root)
                                    runCatching {
                                        context.contentResolver.releasePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    }
                                    engine.scanPhotos()
                                }) {
                                    Icon(Icons.Rounded.Close, "Sil", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        OutlinedButton(onClick = { pickFolder.launch(null) }, enabled = !state.running) {
                            Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp))
                            Text(
                                if (prefs.scanRoots.isEmpty()) "Qovluq seç" else "Başqa qovluq əlavə et",
                                Modifier.padding(start = Space.sm),
                            )
                        }
                        if (prefs.scanRoots.isNotEmpty()) {
                            TextButton(onClick = {
                                engine.settings.update(scanRoots = emptySet())
                                engine.scanPhotos()
                            }, enabled = !state.running) { Text("Bütün qalereya") }
                        }
                    }
                }
            }
        }

        if (state.buckets.isNotEmpty()) {
            item {
                Group(
                    "Hansı qovluqlar taransın",
                    "Söndürülmüş qovluq heç bir analizə düşmür və qruplarda görünmür. " +
                        "Qovluğu yenidən açandan sonra taramanı bir dəfə təkrarlayın.",
                ) {}
            }
            items(state.buckets, key = { it.first }) { (name, count) ->
                val enabled = name !in prefs.excludedBuckets
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "$count şəkil",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            val next = prefs.excludedBuckets.toMutableSet()
                            if (on) next.remove(name) else next.add(name)
                            engine.settings.update(excludedBuckets = next)
                            engine.regroup()
                        },
                    )
                }
            }
        }

        item {
            Group("Oxşarlıq həssaslığı", "Sərt rejim yalnız demək olar ki, eyni şəkilləri birləşdirir. " +
                "Geniş rejim kadrlaşdırılmış və yenidən sıxılmış nüsxələri də tutur, amma səhv qruplar ehtimalı artır.") {
                ChipRow(
                    options = SimilarityLevel.entries,
                    selected = prefs.similarity,
                    label = { it.label },
                    onSelect = {
                        engine.settings.update(similarity = it)
                        engine.regroup()
                    },
                )
            }
        }

        item {
            Group("Hansı nüsxə saxlanılsın", "Qrupda «SAXLANIR» kimi göstəriləcək şəkil. " +
                "Hər qrupda bunu əl ilə də dəyişə bilərsiniz.") {
                ChipRow(
                    options = KeeperRule.entries,
                    selected = prefs.keeperRule,
                    label = { it.label },
                    onSelect = {
                        engine.settings.update(keeperRule = it)
                        engine.regroup()
                    },
                )
            }
        }

        item {
            Group("Üz qruplaşdırma həssaslığı", "Sərt rejim eyni adamı bir neçə qrupa böləcək; " +
                "geniş rejim oxşar adamları birləşdirə bilər.") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ChipRow(
                        options = SimilarityLevel.entries,
                        selected = prefs.faceLevel,
                        label = { it.label },
                        onSelect = {
                            engine.settings.update(faceLevel = it)
                            if (state.faceCount > 0) engine.recluster()
                        },
                    )
                    Text(
                        "Bunu dəyişmək üçün şəkilləri təkrar analiz etmək lazım deyil — " +
                            "üzlər artıq tapılıb, sadəcə yenidən qruplaşdırılır.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Group(
                "Üz axtarışı dəqiqliyi",
                "«Yüksək» rejimdə şəkil daha böyük ölçüdə analiz olunur, ona görə qrup " +
                    "fotolarındakı kiçik üzlər də tapılır — əvəzində tarama təxminən iki dəfə uzun çəkir. " +
                    "Dəyişiklik üzlərin yenidən taranmasını tələb edir — əl ilə etdiyiniz " +
                    "qrup dəyişiklikləri sıfırlanacaq.",
            ) {
                ChipRow(
                    options = FaceDetail.entries,
                    selected = prefs.faceDetail,
                    label = { it.label },
                    onSelect = {
                        if (it != prefs.faceDetail) {
                            engine.settings.update(faceDetail = it)
                            actions.toast("Üzləri yenidən tarayın")
                        }
                    },
                )
            }
        }

        item {
            Group("Ən azı neçə üzü olan qrup göstərilsin", null) {
                ChipRow(
                    options = listOf(1, 2, 3, 5),
                    selected = prefs.minPeopleGroup,
                    label = { "$it" },
                    onSelect = { engine.settings.update(minPeopleGroup = it) },
                )
            }
        }

        item {
            Group(
                "Üz tanıma modeli",
                when {
                    state.modelBacked && imported ->
                        "Sizin yüklədiyiniz model işləyir. Silsəniz tətbiqin öz modelinə qayıdacaq."
                    state.modelBacked ->
                        "Model tətbiqin içindədir və işləyir — heç nə yükləmək lazım deyil."
                    imported ->
                        "Yüklədiyiniz fayl açılmır. Silsəniz tətbiqin öz modeli işə düşəcək."
                    else ->
                        "Model yüklənmədi, sadə rejim işləyir."
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedButton(
                        onClick = { importModel.launch(arrayOf("*/*")) },
                        enabled = !state.running,
                    ) {
                        Text(if (imported) "Başqa model seç" else "Öz modelinizi yükləyin")
                    }
                    if (imported) {
                        TextButton(enabled = !state.running, onClick = {
                            FaceEmbedders.modelPath(context).delete()
                            imported = FaceEmbedders.hasImportedModel(context)
                            engine.resetFaceIndex()
                            actions.toast("Tətbiqin öz modelinə qayıdıldı")
                        }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        item {
            Group("Silmə davranışı", null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Əvvəlcə zibil qutusuna at", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = prefs.useTrash,
                        onCheckedChange = { engine.settings.update(useTrash = it) },
                    )
                }
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Group("Tapılmış üzlər", "Yalnız tapılmış üzlər silinir — şəkillərə toxunulmur.") {
                OutlinedButton(enabled = !state.running, onClick = {
                    engine.resetFaceIndex()
                    actions.toast("Tapılmış üzlər silindi")
                }) { Text("Üzləri sıfırla") }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(Radius.lg),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text("Necə işləyir", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Eyni fayllar: iki fayl bayt-bayt müqayisə olunur. Uyğun gəlirsə eyni " +
                            "fayldırlar — burada təxmin yoxdur. Sürət üçün əvvəlcə ölçü yoxlanılır, " +
                            "ona görə minlərlə şəkildən yalnız bir neçəsi açılır.\n\n" +
                            "Oxşar şəkillər: şəklin adına və ölçüsünə yox, özünə baxılır. " +
                            "Kiçildilmiş, yenidən göndərilmiş və ya sıxılmış nüsxə də tapılır. " +
                            "Üç ayrı yoxlama razılaşmasa qrup yaranmır — birinin səhvi tək " +
                            "başına qrup qurmur.\n\n" +
                            "Üzlər: hər şəkildə üz axtarılır, gözlərə görə düzləndirilir və " +
                            "eyni adamın şəkilləri bir qrupda toplanır.\n\n" +
                            "Şəkilləriniz cihazdan kənara çıxmır — tətbiqin internet icazəsi yoxdur.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
