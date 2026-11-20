package com.example.data

import java.util.Locale

/** An `at_place` circle: `lat`, `lng` and `radius` (m) as the map page sent them. */
data class Place(val lat: Double, val lng: Double, val radius: Int) {
    val key: String get() = String.format(Locale.ROOT, "%.5f,%.5f,%d", lat, lng, radius)

    fun toParams(): Map<String, String> = mapOf(
        "lat" to String.format(Locale.ROOT, "%.5f", lat),
        "lng" to String.format(Locale.ROOT, "%.5f", lng),
        "radius" to radius.toString(),
    )

    companion object {
        /** Fast (GPS) circles go down to this; "Battery friendly" ones only to [MIN_BATTERY_RADIUS_M]. */
        const val MIN_RADIUS_M = 100
        const val MIN_BATTERY_RADIUS_M = 150
        const val MAX_RADIUS_M = 2000

        /** `mode` param: "battery" (default, Android's proximity alert) or this (PlaceTrackingService). */
        const val MODE_FAST = "fast"

        /** Optional words for the Fast mode's notification; blank falls back to the default text. */
        const val NOTICE_TITLE = "notice_title"
        const val NOTICE_TEXT = "notice_text"

        /** Null unless `lat`, `lng` and `radius` are all present and in range. */
        fun parse(params: Map<String, String>): Place? {
            val lat = params["lat"]?.trim()?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
            val lng = params["lng"]?.trim()?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
            val radius = params["radius"]?.trim()?.toIntOrNull()?.takeIf { it in MIN_RADIUS_M..MAX_RADIUS_M } ?: return null
            return Place(lat, lng, radius)
        }
    }
}
