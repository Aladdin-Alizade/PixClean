package az.pixclean.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import az.pixclean.MainActivity
import az.pixclean.PixCleanApp
import az.pixclean.R
import az.pixclean.core.Phase
import az.pixclean.core.ScanEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps a long scan alive when the user leaves the app, and mirrors the engine's progress
 * into a notification. It owns no state of its own — it just watches the engine.
 */
class ScanService : Service() {

    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStartId = 0
    private var lastNotifyAt = 0L
    private var lastPhase: Phase? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startForegroundCompat(build("Hazırlanır", 0, 0))
        acquireWakeLock()
        if (watcher == null) {
            watcher = scope.launch {
                ScanEngine.get(this@ScanService).state.collectLatest { s ->
                    if (s.phase == Phase.IDLE) {
                        // The engine passes through IDLE between phases — a photo scan ending
                        // and a face scan starting is two events, not one. Stopping on the
                        // first sighting killed the service the next phase had just started,
                        // taking the wake lock with it. Wait, look again, and hand back the
                        // start id so a newer command wins.
                        delay(IDLE_GRACE_MS)
                        if (ScanEngine.get(this@ScanService).state.value.phase == Phase.IDLE) {
                            stopSelf(lastStartId)
                        }
                        return@collectLatest
                    }
                    // The engine reports progress several times a second. Posting that many
                    // notifications gets the app rate-limited by the system, which then drops
                    // updates of its own choosing — including, sometimes, the last one, leaving
                    // a progress bar frozen part-way through a scan that actually finished.
                    val now = android.os.SystemClock.elapsedRealtime()
                    val finished = s.total > 0 && s.done >= s.total
                    if (s.phase != lastPhase || finished || now - lastNotifyAt >= NOTIFY_EVERY_MS) {
                        lastPhase = s.phase
                        lastNotifyAt = now
                        notify(build(s.phase.label, s.done, s.total))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        watcher = null
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * Timed out after two hours so a scan that dies without notice cannot hold the CPU awake
     * for the rest of the day; the service releases it as soon as the engine goes idle anyway.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = runCatching {
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PixClean:scan")
                .apply {
                    setReferenceCounted(false)
                    acquire(2 * 60 * 60 * 1000L)
                }
        }.getOrNull()
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
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
        private const val IDLE_GRACE_MS = 2_000L
        private const val NOTIFY_EVERY_MS = 900L

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
    }
}
