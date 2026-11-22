package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Actions that only exist on OPPO, OnePlus and realme phones (one shared system underneath), and
 * only where the feature itself is there. Anywhere else they are not offered at all, and a shared
 * shortcut carrying one is refused rather than saved as a step that cannot run.
 *
 * Mind Space is reached through the phone's own side key (Snap Key / Plus Key): the bridge sends that
 * key's internal code while its setting points at Mind Space, then puts the setting back. So it needs
 * the Mind Space app and that key setting. The built-in voice assistant is the one a long press of
 * power opens on China builds. Screen translate and Record audio are the side key's press-and-hold
 * jobs, reached the same way (both are guarded by permissions only the side key's own app holds);
 * OSee is a plain activity. Verified on PLG110 (ColorOS 17 CN) 2026-11-21/22; other builds are
 * expected to match but unverified, hence [EXPERIMENTAL].
 */
object OemActions {
    const val MIND_SPACE_CAPTURE = "mind_space_capture"
    const val OPEN_MIND_SPACE = "open_mind_space"
    const val BUILT_IN_ASSISTANT = "built_in_assistant"
    const val TRANSLATE_SCREEN = "translate_screen"
    const val RECORD_AUDIO = "record_audio"
    const val OPEN_OSEE = "open_osee"

    /** Side-key press-and-hold jobs: action type to the app that does the job. */
    val SIDE_KEY_HOLD_APPS: Map<String, String> = mapOf(
        TRANSLATE_SCREEN to "com.coloros.translate",
        RECORD_AUDIO to "com.coloros.soundrecorder",
    )

    val TYPES: Set<String> = setOf(MIND_SPACE_CAPTURE, OPEN_MIND_SPACE, BUILT_IN_ASSISTANT, OPEN_OSEE) + SIDE_KEY_HOLD_APPS.keys
    val EXPERIMENTAL: Set<String> = TYPES

    const val MIND_SPACE_PACKAGE = "com.oplus.aimemory"
    const val ASSISTANT_PACKAGE = "com.heytap.speechassist"
    const val ASSISTANT_ACTION = "heytap.speechassist.action.LOCK_SCREEN_UI"
    const val ASSISTANT_ACTIVITY = "com.heytap.speechassist.business.lockscreen.FloatSpeechActivity"
    const val OSEE_PACKAGE = "com.coloros.ocrscanner"
    const val OSEE_ACTION = "coloros.ocrscanner.intent.action.OSEE"

    /** The side key's job; absent on phones without the key. */
    const val SIDE_KEY_SETTING = "oplus_action_button_switch_state"

    private val BRANDS = setOf("oppo", "oneplus", "realme")

    fun brandMatches(manufacturer: String): Boolean = manufacturer.lowercase() in BRANDS

    /** The brand as it writes itself, for the picker's phone tile; null on any other phone. */
    fun brandLabel(manufacturer: String): String? = when (manufacturer.lowercase()) {
        "oppo" -> "OPPO"
        "oneplus" -> "OnePlus"
        "realme" -> "realme"
        else -> null
    }

    /** Pure rule, so tests need no phone: everything else is always offered. */
    fun offered(type: String, brand: Boolean, installed: (String) -> Boolean, hasSideKey: () -> Boolean): Boolean = when (type) {
        MIND_SPACE_CAPTURE, OPEN_MIND_SPACE -> brand && installed(MIND_SPACE_PACKAGE) && hasSideKey()
        BUILT_IN_ASSISTANT -> brand && installed(ASSISTANT_PACKAGE)
        OPEN_OSEE -> brand && installed(OSEE_PACKAGE)
        in SIDE_KEY_HOLD_APPS -> brand && installed(SIDE_KEY_HOLD_APPS.getValue(type)) && hasSideKey()
        else -> true
    }

    fun offered(context: Context, type: String): Boolean = type !in TYPES || offered(
        type,
        brandMatches(Build.MANUFACTURER.orEmpty()),
        installed = { pkg -> isInstalled(context, pkg) },
        hasSideKey = { sideKeySetting(context) != null },
    )

    private fun isInstalled(context: Context, pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun sideKeySetting(context: Context): String? =
        runCatching { Settings.System.getString(context.contentResolver, SIDE_KEY_SETTING) }.getOrNull()
}
