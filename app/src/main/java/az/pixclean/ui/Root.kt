package az.pixclean.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import az.pixclean.core.ConsentBroker
import az.pixclean.core.MediaAccess
import az.pixclean.core.Permissions
import az.pixclean.core.Phase
import az.pixclean.core.ScanEngine
import az.pixclean.data.FolderPlanKind
import az.pixclean.data.FolderPlans
import az.pixclean.service.ScanService
import az.pixclean.ui.components.EmptyState
import az.pixclean.ui.screens.GroupDetailScreen
import az.pixclean.ui.screens.FoldersScreen
import az.pixclean.ui.screens.GroupsScreen
import az.pixclean.ui.screens.HomeScreen
import az.pixclean.ui.screens.PeopleScreen
import az.pixclean.ui.screens.PersonDetailScreen
import az.pixclean.ui.screens.SettingsScreen
import az.pixclean.ui.theme.Space

private class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("home", "Ana", Icons.Rounded.Speed),
    Tab("exact", "Dublikat", Icons.Rounded.ContentCopy),
    Tab("similar", "Oxşar", Icons.Rounded.Difference),
    Tab("people", "Şəxslər", Icons.Rounded.Groups),
)

@Composable
fun PixCleanRoot(broker: ConsentBroker) {
    val context = LocalContext.current
    val engine = remember { ScanEngine.get(context) }
    val state by engine.state.collectAsStateWithLifecycle()
    val prefs by engine.settings.state.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { Actions(context.applicationContext, engine, broker, scope, snackbar) }

    var access by remember { mutableStateOf(Permissions.access(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) access = Permissions.access(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var asked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { access = Permissions.access(context) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun askNotificationsOnce() {
        if (Permissions.notificationsNeeded(context)) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Ask as soon as the app opens rather than making the user find a button first, and ask
    // only for photos: bundling the optional notification prompt in here put a bell dialog in
    // front of someone who has not seen the app yet. Notifications are asked for at the first
    // scan instead, where "we will show you progress" is self-explanatory.
    LaunchedEffect(Unit) {
        if (!asked && Permissions.access(context) != MediaAccess.FULL) {
            asked = true
            permissionLauncher.launch(Permissions.required())
        }
    }

    // The engine keeps running when the app is backgrounded; the service is what stops the
    // system from killing it half way through ten thousand photos.
    LaunchedEffect(state.phase) {
        if (state.phase != Phase.IDLE) ScanService.start(context) else ScanService.stop(context)
    }

    LaunchedEffect(access) {
        if (access == MediaAccess.FULL) engine.loadExisting()
    }

    // An exception message is written for a developer. The user is told what it means for
    // them instead; the original stays in the log for when it is actually needed.
    LaunchedEffect(state.error) {
        state.error?.let { raw ->
            android.util.Log.w("PixClean", "scan failed: $raw")
            val friendly = when {
                raw.contains("ENOSPC", true) || raw.contains("space", true) ->
                    "Yaddaşda yer çatmadı."
                raw.contains("permission", true) || raw.contains("Security", true) ->
                    "Şəkillərə giriş icazəsi yoxdur. Tənzimləmələrdən icazə verin."
                raw.contains("OutOfMemory", true) ->
                    "Yaddaş çatmadı. Tətbiqi yenidən açıb cəhd edin."
                else -> "Tarama yarımçıq qaldı. Yenidən cəhd edin."
            }
            snackbar.showSnackbar(friendly, withDismissAction = true)
            engine.clearError()
        }
    }

    if (access != MediaAccess.FULL) {
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                EmptyState(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = if (access == MediaAccess.PARTIAL) "Yalnız bir neçə şəkilə icazə var"
                    else "Şəkillərə giriş lazımdır",
                    body = if (access == MediaAccess.PARTIAL)
                        "Dublikatları tapmaq üçün bütün qalereyanı görmək lazımdır. " +
                            "Tənzimləmələrdə «Bütün şəkillər» seçin."
                    else
                        "PixClean şəkilləri yalnız telefonun içində analiz edir. " +
                            "Heç bir şəkil heç yerə göndərilmir.",
                    modifier = Modifier.weight(1f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Button(onClick = { permissionLauncher.launch(Permissions.required()) }) {
                            Text("Yenidən soruş")
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }) { Text("Tənzimləmələri aç") }
                    }
                }
            }
        }
        return
    }

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination
    val showBar = TABS.any { tab -> route?.hierarchy?.any { it.route == tab.route } == true }

    CompositionLocalProvider(LocalActions provides actions) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        TABS.forEach { tab ->
                            val selected = route?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { inner ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("home") {
                    HomeScreen(
                        state = state,
                        prefs = prefs,
                        contentPadding = inner,
                        onScanPhotos = { askNotificationsOnce(); engine.scanPhotos() },
                        onScanFaces = { askNotificationsOnce(); engine.scanFaces() },
                        onCancel = engine::cancel,
                        onOpen = { navController.navigate(it) },
                        onSettings = { navController.navigate("settings") },
                    )
                }
                composable("exact") {
                    GroupsScreen(
                        title = "Eyni fayllar",
                        groups = state.exact,
                        state = state,
                        useTrash = prefs.useTrash,
                        contentPadding = inner,
                        onOpenGroup = { navController.navigate("group/exact/${it.keeper.id}") },
                        onScan = engine::scanPhotos,
                    )
                }
                composable("similar") {
                    GroupsScreen(
                        title = "Oxşar şəkillər",
                        groups = state.similar,
                        state = state,
                        useTrash = prefs.useTrash,
                        contentPadding = inner,
                        onOpenGroup = { navController.navigate("group/similar/${it.keeper.id}") },
                        onScan = engine::scanPhotos,
                    )
                }
                composable("people") {
                    PeopleScreen(
                        state = state,
                        prefs = prefs,
                        contentPadding = inner,
                        onScanFaces = engine::scanFaces,
                        onOpenPerson = { navController.navigate("person/${it.clusterId}") },
                        onRename = engine::renamePerson,
                        onMerge = engine::mergePeople,
                    )
                }
                composable("folders/{kind}") { entry ->
                    val kind = if (entry.arguments?.getString("kind") == "months") {
                        FolderPlanKind.MONTHS
                    } else {
                        FolderPlanKind.PEOPLE
                    }
                    val proposals = remember(kind, state.people) {
                        FolderPlans.forPeople(state.people)
                    }
                    FoldersScreen(
                        kind = kind,
                        proposals = proposals,
                        photoIndex = state.photoIndex,
                        busy = state.running,
                        contentPadding = inner,
                        onBack = { navController.popBackStack() },
                        onCreate = { chosen ->
                            actions.organize(
                                chosen.map { folder ->
                                    folder.name to folder.photoIds.mapNotNull { state.photoIndex[it] }
                                },
                            ) { navController.popBackStack() }
                        },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        engine = engine,
                        state = state,
                        prefs = prefs,
                        contentPadding = inner,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("group/{kind}/{keeperId}") { entry ->
                    val kind = entry.arguments?.getString("kind") ?: "exact"
                    val keeperId = entry.arguments?.getString("keeperId")?.toLongOrNull() ?: -1L
                    val groups = if (kind == "exact") state.exact else state.similar
                    GroupDetailScreen(
                        group = groups.firstOrNull { it.keeper.id == keeperId },
                        useTrash = prefs.useTrash,
                        contentPadding = inner,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("person/{clusterId}") { entry ->
                    val clusterId = entry.arguments?.getString("clusterId")?.toIntOrNull() ?: -1
                    PersonDetailScreen(
                        cluster = state.people.firstOrNull { it.clusterId == clusterId },
                        photoIndex = state.photoIndex,
                        useTrash = prefs.useTrash,
                        contentPadding = inner,
                        onBack = { navController.popBackStack() },
                        onRename = engine::renamePerson,
                        allPeople = state.people,
                        onMoveToPerson = engine::movePhotosToPerson,
                    )
                }
            }
        }
    }
}
