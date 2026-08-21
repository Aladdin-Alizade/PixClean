package az.pixclean.core

import android.content.IntentSender
import kotlinx.coroutines.CompletableDeferred

/**
 * Deleting or moving someone else's media always needs a system confirmation dialog.
 * The activity owns the launcher; everything else just awaits an answer through here.
 */
class ConsentBroker {

    @Volatile
    var launch: ((IntentSender) -> Unit)? = null

    private var pending: CompletableDeferred<Boolean>? = null

    suspend fun ask(sender: IntentSender): Boolean {
        val launcher = launch ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        launcher(sender)
        return try {
            deferred.await()
        } finally {
            pending = null
        }
    }

    fun deliver(granted: Boolean) {
        pending?.complete(granted)
    }
}
