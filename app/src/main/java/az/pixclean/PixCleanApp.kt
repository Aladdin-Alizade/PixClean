package az.pixclean

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import az.pixclean.core.ScanEngine
import az.pixclean.service.ScanService

class PixCleanApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SCAN,
                "Skan gedişatı",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Şəkillər analiz edilərkən göstərilir" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        // Wired here rather than in the UI because the process outlives the screen: the
        // service has to come up the moment work starts, whatever the activity is doing.
        ScanEngine.get(this).onWorkStarted = { ScanService.start(this) }
    }

    companion object {
        const val CHANNEL_SCAN = "scan"
    }
}
