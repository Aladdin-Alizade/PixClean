package az.pixclean.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess { NONE, PARTIAL, FULL }

object Permissions {

    /** The permission the app cannot work without. */
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }



    fun access(context: Context): MediaAccess {
        val full = when {
            Build.VERSION.SDK_INT >= 33 -> granted(context, Manifest.permission.READ_MEDIA_IMAGES)
            else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (full) return MediaAccess.FULL
        if (Build.VERSION.SDK_INT >= 34 &&
            granted(context, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        ) return MediaAccess.PARTIAL
        return MediaAccess.NONE
    }

    fun notificationsNeeded(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && !granted(context, Manifest.permission.POST_NOTIFICATIONS)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
