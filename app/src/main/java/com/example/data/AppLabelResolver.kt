package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.SystemClock
import android.util.LruCache
import io.github.nicholasarda.keysnap.R

/** Display-only app identity. Package ids in saved actions and payloads are never rewritten. */
data class AppLabel(val label: String, val installed: Boolean) {
    fun displayName(resources: Resources): String =
        if (installed) label else "$label · ${resources.getString(R.string.app_not_installed)}"
}

/** Uses the same package visibility as the app picker; no broad package permission is needed. */
class AppLabelResolver internal constructor(
    private val packageManager: PackageManager,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class Entry(val value: AppLabel, val at: Long)
    private val cache = LruCache<String, Entry>(256)

    @Synchronized
    fun resolve(packageName: String, resources: Resources): AppLabel {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return AppLabel("", true)
        val key = "${resources.configuration.locales.toLanguageTags()}:$pkg"
        val time = now()
        cache.get(key)?.takeIf { time - it.at < CACHE_MS }?.let { return it.value }
        val value = try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            AppLabel(packageManager.getApplicationLabel(info).toString().ifBlank { pkg }, true)
        } catch (_: PackageManager.NameNotFoundException) {
            AppLabel(pkg, false)
        }
        cache.put(key, Entry(value, time))
        return value
    }

    fun displayName(packageName: String, resources: Resources): String = resolve(packageName, resources).displayName(resources)

    companion object {
        // Refresh both successful and missing lookups after app installs, removals or label updates.
        private const val CACHE_MS = 60_000L
        @Volatile private var instance: AppLabelResolver? = null

        fun get(context: Context): AppLabelResolver = instance ?: synchronized(this) {
            instance ?: AppLabelResolver(context.applicationContext.packageManager).also { instance = it }
        }
    }
}
