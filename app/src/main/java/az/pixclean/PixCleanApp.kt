package az.pixclean

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import az.pixclean.core.ScanEngine

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
        ScanEngine.get(this)
    }

    companion object {
        const val CHANNEL_SCAN = "scan"
    }
}
