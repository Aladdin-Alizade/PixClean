package az.pixclean.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import az.pixclean.MainActivity
import az.pixclean.PixCleanApp
import az.pixclean.R
import az.pixclean.core.Phase
import az.pixclean.core.ScanEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps a long scan alive when the user leaves the app, and mirrors the engine's progress
 * into a notification. It owns no state of its own — it just watches the engine.
 */
class ScanService : Service() {

    private var watcher: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(build("Hazırlanır", 0, 0))
        if (watcher == null) {
            watcher = scope.launch {
                ScanEngine.get(this@ScanService).state.collectLatest { s ->
                    if (s.phase == Phase.IDLE) {
                        stopSelf()
                        return@collectLatest
                    }
                    notify(build(s.phase.label, s.done, s.total))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        watcher = null
        super.onDestroy()
    }

    private fun build(text: String, done: Int, total: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, PixCleanApp.CHANNEL_SCAN)
            .setContentTitle("PixClean")
            .setContentText(if (total > 0) "$text — $done / $total" else text)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .apply { if (total > 0) setProgress(total, done, false) else setProgress(0, 0, true) }
            .build()
    }

    private fun notify(n: Notification) {
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).notify(ID, n)
        }
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, n)
        }
    }

    companion object {
        private const val ID = 42

        fun start(context: Context) {
            runCatching {
                val i = Intent(context, ScanService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ScanService::class.java)) }
        }
    }
}
