package az.pixclean.ui

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import az.pixclean.core.ConsentBroker
import az.pixclean.core.ScanEngine
import az.pixclean.data.MediaActions
import az.pixclean.data.Photo
import az.pixclean.ui.components.humanBytes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Every destructive path funnels through here so the confirmation dialog, the database
 * cleanup and the undo hint stay in one place instead of being re-implemented per screen.
 *
 * It lives on the process, not on the screen. Deleting a few thousand photos outlasts a
 * rotation, and when this ran on the composition's scope a turn of the phone cancelled the
 * work halfway: the files the system had already removed stayed in the app's index, so the
 * list went on offering photos that were not there any more. The screen is free to come and
 * go; the operation finishes either way, and reports through [messages].
 */
class Actions private constructor(
    private val context: Context,
    private val engine: ScanEngine,
    private val broker: ConsentBroker,
) {

    private val failures = CoroutineExceptionHandler { _, e ->
        android.util.Log.w("PixClean", "action failed", e)
        _busy.value = false
        _messages.tryEmit("Əməliyyat tamamlanmadı.")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + failures)

    private val _busy = MutableStateFlow(false)

    /** True while a delete, move or sort is in flight, so the buttons that start one can say so. */
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** What to tell the user once something has finished. Collected by the root screen. */
    val messages: SharedFlow<String> = _messages

    fun remove(photos: List<Photo>, toTrash: Boolean, onDone: () -> Unit = {}) {
        if (photos.isEmpty()) return
        run(onDone) {
            val outcome = MediaActions.remove(context, photos, toTrash, broker)
            if (outcome.succeeded.isEmpty()) outcome.message
            else {
                engine.forget(outcome.succeeded)
                "${outcome.message} · ${humanBytes(outcome.freedBytes)} azad oldu"
            }
        }
    }

    fun move(photos: List<Photo>, album: String, onDone: () -> Unit = {}) {
        if (photos.isEmpty()) return
        run(onDone) {
            val outcome = MediaActions.moveToAlbum(context, photos, album, broker)
            if (outcome.succeeded.isNotEmpty()) engine.forget(outcome.succeeded)
            outcome.message
        }
    }

    /** Files photos into the folders the user just approved. */
    fun organize(folders: List<Pair<String, List<Photo>>>, onDone: () -> Unit = {}) {
        if (folders.isEmpty()) return
        run(onDone) {
            val outcome = MediaActions.organizeIntoFolders(context, folders, broker)
            // The photos still exist, they just live somewhere else now. Dropping them from the
            // index would leave the app claiming the gallery is empty, so re-read it instead —
            // cached signatures make that near-instant.
            if (outcome.succeeded.isNotEmpty()) engine.scanPhotos()
            outcome.message
        }
    }

    /**
     * Runs one operation and refuses to start a second while it lasts.
     *
     * Not a nicety: moving files needs the system's permission dialog, and the system shows
     * one at a time. A second tap on "delete" before the first dialog appeared used to raise
     * a second request that displaced the first, and the first tap's work then waited forever
     * for an answer nobody would give.
     *
     * The caller's callback runs *before* the message, not after. Showing a snackbar suspends
     * until it goes away, so doing it the other way round left the screen sitting on a
     * finished job for another four seconds. The work is over when the files have moved; the
     * message is just the receipt.
     */
    private fun run(onDone: () -> Unit, body: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            val message = try {
                body()
            } finally {
                _busy.value = false
            }
            onDone()
            _messages.tryEmit(message)
        }
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    companion object {
        @Volatile
        private var instance: Actions? = null

        fun get(context: Context): Actions {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val app = context.applicationContext
                return Actions(app, ScanEngine.get(app), ConsentBroker.shared).also { instance = it }
            }
        }
    }
}

suspend fun SnackbarHostState.showMessage(text: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(text, withDismissAction = true)
}

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions not provided") }
