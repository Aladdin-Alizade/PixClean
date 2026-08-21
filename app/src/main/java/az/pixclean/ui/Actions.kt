package az.pixclean.ui

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import az.pixclean.core.ConsentBroker
import az.pixclean.core.ScanEngine
import az.pixclean.data.MediaActions
import az.pixclean.data.Photo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every destructive path funnels through here so the confirmation dialog, the database
 * cleanup and the undo hint stay in one place instead of being re-implemented per screen.
 */
class Actions(
    private val context: Context,
    private val engine: ScanEngine,
    private val broker: ConsentBroker,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
) {
    fun remove(photos: List<Photo>, toTrash: Boolean, onDone: () -> Unit = {}) {
        if (photos.isEmpty()) return
        scope.launch {
            val outcome = MediaActions.remove(context, photos, toTrash, broker)
            if (outcome.succeeded.isNotEmpty()) {
                engine.forget(outcome.succeeded)
                val freed = az.pixclean.ui.components.humanBytes(outcome.freedBytes)
                snackbar.showMessage("${outcome.message} · $freed azad oldu")
            } else {
                snackbar.showMessage(outcome.message)
            }
            onDone()
        }
    }

    fun move(photos: List<Photo>, album: String, onDone: () -> Unit = {}) {
        if (photos.isEmpty()) return
        scope.launch {
            val outcome = MediaActions.moveToAlbum(context, photos, album, broker)
            if (outcome.succeeded.isNotEmpty()) engine.forget(outcome.succeeded)
            snackbar.showMessage(outcome.message)
            onDone()
        }
    }

    fun toast(message: String) {
        scope.launch { snackbar.showMessage(message) }
    }
}

private suspend fun SnackbarHostState.showMessage(text: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(text, withDismissAction = true)
}

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions not provided") }
