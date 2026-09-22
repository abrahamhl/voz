package dev.auxdesign.voz.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.SystemClock
import dev.auxdesign.voz.core.model.AppEntry
import dev.auxdesign.voz.core.parse.AppMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Launchable apps on the phone (visible through the manifest <queries> launcher intent). */
class InstalledApps(private val context: Context) {

    @Volatile private var cached: List<AppEntry> = emptyList()

    @Volatile private var loadedAt = 0L

    suspend fun list(): List<AppEntry> = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        val current = cached
        if (current.isNotEmpty() && now - loadedAt < TTL_MS) return@withContext current
        val pm = context.packageManager
        val apps = queryLaunchers(pm)
            .mapNotNull { info -> info.activityInfo?.let { AppEntry(info.loadLabel(pm).toString(), it.packageName) } }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
        cached = apps
        loadedAt = now
        apps
    }

    suspend fun matcher(): AppMatcher = AppMatcher(list())

    private fun queryLaunchers(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            queryLegacy(pm, intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryLegacy(pm: PackageManager, intent: Intent): List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

    private companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}
