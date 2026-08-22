package az.pixclean.core

import android.content.IntentSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deleting or moving someone else's media always needs a system confirmation dialog.
 * The activity owns the launcher; everything else just awaits an answer through here.
 *
 * Two rules make this safe to call from anywhere:
 *
 * One request at a time. The system shows a single dialog and reports a single answer, so a
 * second request raised while one is open used to overwrite the first one's slot — the first
 * caller then waited for a reply that could never arrive and its coroutine hung for the life
 * of the process, with the selection bar still on screen and no way to clear it. Two taps on
 * "delete" is all it took. Requests now queue behind each other.
 *
 * The wait outlives the activity. A rotation while the dialog is up destroys and recreates
 * the activity, and the system delivers the answer to the new one; the broker is process-wide
 * so it is still there to receive it. When the activity goes away for good instead, the
 * waiter is released rather than left hanging.
 */
class ConsentBroker {

    private val turn = Mutex()

    @Volatile
    private var launcher: ((IntentSender) -> Unit)? = null

    @Volatile
    private var pending: CompletableDeferred<Boolean>? = null

    /** Called by the activity that owns the result launcher. */
    fun attach(launch: (IntentSender) -> Unit) {
        launcher = launch
    }

    /**
     * [recreating] is the activity telling us it is only being rebuilt — a rotation — so the
     * answer to a dialog that is still open will arrive at its replacement. When it is
     * leaving for good nothing will ever answer, and whoever is waiting has to be let go.
     */
    fun detach(recreating: Boolean) {
        launcher = null
        if (!recreating) pending?.complete(false)
    }

    suspend fun ask(sender: IntentSender): Boolean = turn.withLock {
        val launch = launcher ?: return@withLock false
        val answer = CompletableDeferred<Boolean>()
        pending = answer
        try {
            launch(sender)
            answer.await()
        } finally {
            pending = null
        }
    }

    fun deliver(granted: Boolean) {
        pending?.complete(granted)
    }

    companion object {
        /**
         * One per process, not one per activity: the activity is the thing that gets thrown
         * away and rebuilt mid-operation, which is exactly what the waiting has to survive.
         */
        val shared = ConsentBroker()
    }
}
