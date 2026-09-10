package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

/**
 * One entry per package, sorted by display label. A package can resolve several launcher
 * activities, so duplicates are collapsed; a blank label falls back to the package name rather
 * than rendering an empty row.
 */
internal fun sortInstalledApps(apps: List<InstalledApp>): List<InstalledApp> = apps
    .map { if (it.label.isBlank()) it.copy(label = it.packageName) else it }
    .distinctBy { it.packageName }
    .sortedBy { it.label.lowercase() }

/**
 * The apps offered by the action picker. Backed by the manifest's launcher `<queries>` element,
 * so no runtime permission is involved and `QUERY_ALL_PACKAGES` is not needed — the cost is that
 * packages with no launcher entry are invisible, which is why the picker also accepts a typed
 * package name.
 */
class InstalledAppsRepository(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
    @Volatile private var cache: List<InstalledApp>? = null

    suspend fun launchableApps(): List<InstalledApp> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = runCatching { packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            val apps = sortInstalledApps(
                resolved.map { info ->
                    InstalledApp(
                        packageName = info.activityInfo.packageName,
                        label = info.loadLabel(packageManager).toString(),
                    )
                },
            )
            cache = apps
            apps
        }
    }

    fun labelFor(packageName: String): String? =
        cache?.firstOrNull { it.packageName == packageName }?.label

    /**
     * The launcher icon rendered at [sizePx], or null when the package is gone (uninstalled between
     * listing and scrolling). Decoded on demand, one row at a time, so the picker never pays for
     * icons it has not shown; the result is cached so scrolling back is free.
     */
    suspend fun icon(packageName: String, sizePx: Int): ImageBitmap? {
        icons.get(packageName)?.let { return it.asImageBitmap() }
        return withContext(Dispatchers.IO) {
            runCatching { packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx) }
                .getOrNull()
                ?.also { icons.put(packageName, it) }
                ?.asImageBitmap()
        }
    }

    private companion object {
        // Process-wide rather than per instance: icons belong to the package, not to whoever
        // asked, and the picker's default loader builds its own repository.
        val icons = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }
}
