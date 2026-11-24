package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.example.model.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers both Mobile (LAUNCHER) and Android TV (LEANBACK_LAUNCHER) apps using
 * Play-policy compliant <queries> intent resolution (no QUERY_ALL_PACKAGES needed).
 */
class InstalledAppsRepository(private val context: Context) {

    suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPackage = context.packageName

        val mobileIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val tvIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        val resolved = ArrayList<ResolveInfo>()
        resolved.addAll(queryActivities(pm, mobileIntent))
        resolved.addAll(queryActivities(pm, tvIntent))

        val seenPackages = HashSet<String>()
        val iconSizePx = (36 * context.resources.displayMetrics.density).toInt().coerceIn(48, 128)
        val apps = ArrayList<InstalledAppInfo>(resolved.size)

        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == selfPackage || !seenPackages.add(pkg)) continue

            val appInfo = activityInfo.applicationInfo
            val label = runCatching {
                info.loadLabel(pm).toString().trim()
            }.getOrNull().orEmpty().ifEmpty { pkg }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val bitmap = runCatching {
                drawableToScaledBitmap(info.loadIcon(pm), iconSizePx)
            }.getOrNull()

            apps.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    isSystemApp = isSystem,
                    icon = bitmap
                )
            )
        }

        apps.sortedWith(compareBy<InstalledAppInfo> { it.isSystemApp }.thenBy { it.appName.lowercase() })
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        }.getOrDefault(emptyList())
    }

    private fun drawableToScaledBitmap(drawable: Drawable?, sizePx: Int): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
