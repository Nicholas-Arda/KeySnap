package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.util.LruCache
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

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

    private val appContext = context.applicationContext
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
                        label = AppLabelResolver.get(appContext).resolve(info.activityInfo.packageName, appContext.resources).label,
                    )
                },
            )
            cache = apps
            apps
        }
    }

    /** Enabled keyboards, for "Input method is chosen". Needs the manifest's InputMethod `<queries>` entry. */
    suspend fun keyboardApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val imm = appContext.getSystemService(InputMethodManager::class.java)
        val packages = runCatching { imm?.enabledInputMethodList.orEmpty().map { it.packageName }.distinct() }.getOrDefault(emptyList())
        sortInstalledApps(packages.map { InstalledApp(it, AppLabelResolver.get(appContext).resolve(it, appContext.resources).label) })
    }

    /**
     * Apps with a contactless *payment* service, for "Default payment app", labelled
     * "App · service" (Play services carries "Google Pay"). Non-payment HCE services (Nearby, car
     * keys) are left out. Needs the manifest's HOST_APDU_SERVICE `<queries>` entry.
     */
    suspend fun paymentApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE")
        val resolved = runCatching { packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        val described = resolved.mapNotNull { info -> paymentDescription(info.serviceInfo)?.let { info.serviceInfo.packageName to it } }
        sortInstalledApps(
            described.groupBy({ it.first }, { it.second }).map { (pkg, descriptions) ->
                val label = AppLabelResolver.get(appContext).resolve(pkg, appContext.resources).label
                val service = descriptions.firstOrNull { it.isNotBlank() && it != label }
                InstalledApp(pkg, if (service == null) label else "$label · $service")
            },
        )
    }

    /** The service's description when one of its AID groups is in the payment category, else null. */
    private fun paymentDescription(service: ServiceInfo): String? = runCatching {
        service.loadXmlMetaData(packageManager, "android.nfc.cardemulation.host_apdu_service")?.use { xml ->
            val res = packageManager.getResourcesForApplication(service.applicationInfo)
            // By attribute resource id, as the framework does: obfuscated apps strip attribute names.
            fun attr(name: String): String {
                val attrId = android.content.res.Resources.getSystem().getIdentifier(name, "attr", "android")
                for (i in 0 until xml.attributeCount) {
                    if (xml.getAttributeNameResource(i) != attrId) continue
                    val ref = xml.getAttributeResourceValue(i, 0)
                    return if (ref != 0) runCatching { res.getString(ref) }.getOrDefault("") else xml.getAttributeValue(i).orEmpty()
                }
                return ""
            }
            var description = ""
            var payment = false
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType != XmlPullParser.START_TAG) continue
                when (xml.name) {
                    "host-apdu-service" -> description = attr("description")
                    "aid-group" -> if (attr("category") == "payment") payment = true
                }
            }
            description.takeIf { payment }
        }
    }.getOrNull()

    fun labelFor(packageName: String): String? =
        AppLabelResolver.get(appContext).resolve(packageName, appContext.resources).takeIf { it.installed }?.label

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
