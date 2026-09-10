package com.example.data

import android.os.Build
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R

/**
 * One phone-UI family's step-by-step instructions for reaching Developer Options and Wireless
 * Debugging. Shown in the setup wizard so a first-time user does not have to leave the app to
 * figure out how their specific phone exposes these settings.
 */
data class DeveloperOptionsGuide(
    val id: String,
    @StringRes val labelRes: Int,
    @ArrayRes val stepsRes: Int,
)

object DeveloperOptionsGuides {
    val GENERIC = DeveloperOptionsGuide("generic", R.string.guide_generic, R.array.guide_generic_steps)

    val all: List<DeveloperOptionsGuide> = listOf(
        DeveloperOptionsGuide("samsung", R.string.guide_samsung, R.array.guide_samsung_steps),
        DeveloperOptionsGuide("xiaomi", R.string.guide_xiaomi, R.array.guide_xiaomi_steps),
        DeveloperOptionsGuide("huawei", R.string.guide_huawei, R.array.guide_huawei_steps),
        DeveloperOptionsGuide("coloros", R.string.guide_coloros, R.array.guide_coloros_steps),
        DeveloperOptionsGuide("vivo", R.string.guide_vivo, R.array.guide_vivo_steps),
        DeveloperOptionsGuide("pixel", R.string.guide_pixel, R.array.guide_pixel_steps),
        GENERIC,
    )

    /** Best-effort match against [Build.MANUFACTURER]/[Build.BRAND]; falls back to [GENERIC]. */
    fun detectForDevice(manufacturer: String = Build.MANUFACTURER.orEmpty(), brand: String = Build.BRAND.orEmpty()): DeveloperOptionsGuide {
        val haystack = "$manufacturer $brand".lowercase()
        return when {
            haystack.contains("samsung") -> byId("samsung")
            haystack.contains("xiaomi") || haystack.contains("redmi") || haystack.contains("poco") -> byId("xiaomi")
            haystack.contains("huawei") || haystack.contains("honor") -> byId("huawei")
            haystack.contains("oppo") || haystack.contains("oneplus") || haystack.contains("realme") -> byId("coloros")
            haystack.contains("vivo") || haystack.contains("iqoo") -> byId("vivo")
            haystack.contains("google") -> byId("pixel")
            else -> GENERIC
        }
    }

    fun byId(id: String): DeveloperOptionsGuide = all.first { it.id == id }
}
