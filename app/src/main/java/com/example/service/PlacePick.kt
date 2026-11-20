package com.example.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.example.data.Place
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * Picking a place happens on ardasenn.com/keysnap/place/ in a browser tab, never in a WebView, so
 * no map SDK and no page under the app's control ever sees a location. The page answers with
 * `keysnap://place?lat=..&lng=..&r=..`, which MainActivity hands to [deliver].
 */
object PlacePick {
    private const val PAGE = "https://ardasenn.com/keysnap/place/"

    /** The last place the page sent back, as constraint params; the open sheet takes it and clears it. */
    val result = MutableStateFlow<Map<String, String>?>(null)

    @Volatile private var waiting = false

    /**
     * Opens the map in a Custom Tab (plain browser when none). The current place rides in the
     * fragment, which the browser never sends to the server; `app` makes the answer come back
     * to this build only (the Play app and a test build can sit side by side).
     */
    fun open(context: Context, current: Place?) {
        val prefill = current?.let { String.format(Locale.ROOT, "&lat=%.5f&lng=%.5f&r=%d", it.lat, it.lng, it.radius) }.orEmpty()
        val uri = Uri.parse("$PAGE#app=${context.packageName}$prefill")
        // The Custom Tabs protocol is two extras; androidx.browser is not needed for it.
        val tab = Intent(Intent.ACTION_VIEW, uri)
            .putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
            .putExtra("androidx.browser.customtabs.extra.SHARE_STATE", 2) // off
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { context !is android.app.Activity } ?: 0)
        waiting = true
        try {
            context.startActivity(tab)
        } catch (e: ActivityNotFoundException) {
            waiting = false
        }
    }

    /** The place sheet closed: an answer arriving after it belongs to no pick. */
    fun cancel() {
        waiting = false
        result.value = null
    }

    /** True when [uri] was the map's answer; anything else (or an answer nobody asked for) is ignored. */
    fun deliver(uri: Uri?): Boolean {
        if (uri?.scheme != "keysnap" || uri.host != "place") return false
        if (!waiting) return true
        val params = mapOf(
            "lat" to uri.getQueryParameter("lat").orEmpty(),
            "lng" to uri.getQueryParameter("lng").orEmpty(),
            "radius" to uri.getQueryParameter("r").orEmpty(),
        )
        val place = Place.parse(params) ?: return true
        waiting = false
        result.value = place.toParams()
        return true
    }
}
