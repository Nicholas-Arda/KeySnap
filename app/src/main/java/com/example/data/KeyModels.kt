package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.KeyEvent
import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TriggerPressType(
    @StringRes val titleRes: Int,
    @StringRes val shortBadgeRes: Int,
    @StringRes val descriptionRes: Int,
) {
    SINGLE_PRESS(R.string.press_single_title, R.string.press_single_badge, R.string.press_single_description),
    DOUBLE_PRESS(R.string.press_double_title, R.string.press_double_badge, R.string.press_double_description),
    LONG_PRESS(R.string.press_long_title, R.string.press_long_badge, R.string.press_long_description),
}

data class PhysicalKeyOption(
    val keyCode: Int,
    val displayName: String,
    val shortLabel: String,
    val requiresHijack: Boolean = false
)

/** Every code Android gives a real label for. Anything else the user is asked to name. */
private val LABELLED_KEY_NAMES: Map<Int, Int> = mapOf(
    KeyEvent.KEYCODE_VOLUME_DOWN to R.string.key_volume_down,
    KeyEvent.KEYCODE_VOLUME_UP to R.string.key_volume_up,
    KeyEvent.KEYCODE_POWER to R.string.key_power,
    KeyEvent.KEYCODE_ASSIST to R.string.key_assist,
    KeyEvent.KEYCODE_VOICE_ASSIST to R.string.key_voice_assist,
    KeyEvent.KEYCODE_HEADSETHOOK to R.string.key_headsethook,
    KeyEvent.KEYCODE_CAMERA to R.string.key_camera,
    KeyEvent.KEYCODE_BACK to R.string.key_back,
    KeyEvent.KEYCODE_HOME to R.string.key_home,
    KeyEvent.KEYCODE_MENU to R.string.key_menu,
    KeyEvent.KEYCODE_MUTE to R.string.key_mute,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to R.string.key_media_play_pause,
    KeyEvent.KEYCODE_WAKEUP to R.string.key_wakeup,
    300 to R.string.key_alert_slider,
)

/**
 * Prefix of the auto-generated name given to a key Android has no label for. Deliberately *not*
 * translated: this string is persisted as the key's display name, and [isGeneratedKeyName] has to
 * still recognise it after the user switches the app's language.
 */
private const val UNLABELLED_KEY_PREFIX = "Key code #"

/**
 * The same prefix in the Turkish copy this app shipped before. Saved key names persist, so a key
 * auto-named by an older build must still be recognised as unlabelled rather than treated as a name
 * the user chose.
 */
private const val LEGACY_UNLABELLED_KEY_PREFIX = "Tuş Kodu #"

fun getReadableKeyName(resources: Resources, keyCode: Int): String =
    LABELLED_KEY_NAMES[keyCode]?.let(resources::getString) ?: "$UNLABELLED_KEY_PREFIX$keyCode"

/**
 * True when Android gives this code no meaningful label, so the app should ask the user to name it
 * rather than saving a placeholder. Checking the code beats sniffing the returned string.
 */
fun isUnlabelledKey(keyCode: Int): Boolean = keyCode !in LABELLED_KEY_NAMES

/** True for a persisted name that an older or current build generated rather than the user. */
fun isGeneratedKeyName(name: String): Boolean =
    name.startsWith(UNLABELLED_KEY_PREFIX) || name.startsWith(LEGACY_UNLABELLED_KEY_PREFIX)

data class MillisecondSettingSpec(
    val min: Long,
    val max: Long,
    val step: Long,
    val default: Long,
) {
    val sliderSteps: Int = ((max - min) / step - 1).toInt()
}

object TriggerTimingSpecs {
    val doublePressWindow = MillisecondSettingSpec(150L, 750L, 50L, 300L)
    val longPressThreshold = MillisecondSettingSpec(300L, 1500L, 100L, 500L)
    val hapticDuration = MillisecondSettingSpec(20L, 200L, 10L, 60L)
}

fun normalizeTimingValue(value: Long, spec: MillisecondSettingSpec): Long {
    val clamped = value.coerceIn(spec.min, spec.max)
    val snapped = spec.min + ((clamped - spec.min + spec.step / 2) / spec.step) * spec.step
    return snapped.coerceIn(spec.min, spec.max)
}

data class KeyMappingConfig(
    val isEnabled: Boolean = true,
    val targetKeyCode: Int? = null,
    val pressType: TriggerPressType = TriggerPressType.DOUBLE_PRESS,
    val consumeOriginalEvent: Boolean = true,
    val vibrateOnTrigger: Boolean = true,
    val doublePressWindowMs: Long = TriggerTimingSpecs.doublePressWindow.default,
    val longPressThresholdMs: Long = TriggerTimingSpecs.longPressThreshold.default,
    val hapticDurationMs: Long = TriggerTimingSpecs.hapticDuration.default,
)

data class LiveKeyEvent(
    val id: Long = System.currentTimeMillis(),
    val keyCode: Int,
    val keyName: String,
    val actionName: String,
    val isTriggerMatch: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class KeyMappingRepository internal constructor(private val prefs: SharedPreferences) {
    companion object {
        @Volatile
        private var instance: KeyMappingRepository? = null

        fun getInstance(context: Context): KeyMappingRepository =
            instance ?: synchronized(this) {
                instance ?: KeyMappingRepository(
                    context.applicationContext.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<KeyMappingConfig> = _config.asStateFlow()

    private val _savedKeys = MutableStateFlow<List<PhysicalKeyOption>>(emptyList())
    val savedKeys: StateFlow<List<PhysicalKeyOption>> = _savedKeys.asStateFlow()

    init {
        loadSavedKeys()
    }

    private fun loadSavedKeys() {
        val count = prefs.getInt("saved_keys_count", 0)
        val keys = mutableListOf<PhysicalKeyOption>()
        for (i in 0 until count) {
            val code = prefs.getInt("saved_key_${i}_code", -1)
            val name = prefs.getString("saved_key_${i}_name", "") ?: ""
            if (code != -1 && name.isNotBlank()) {
                val shortName = name
                keys.add(PhysicalKeyOption(code, name, shortName, code == KeyEvent.KEYCODE_POWER || code == KeyEvent.KEYCODE_ASSIST))
            }
        }
        _savedKeys.value = keys
    }

    private fun loadConfig(): KeyMappingConfig {
        val isEnabled = prefs.getBoolean("is_enabled", true)
        val targetKeyCodeVal = prefs.getInt("target_key_code", -1)
        val targetKeyCode = if (targetKeyCodeVal != -1) targetKeyCodeVal else null
        val pressTypeName = prefs.getString("press_type", TriggerPressType.DOUBLE_PRESS.name) ?: TriggerPressType.DOUBLE_PRESS.name
        val pressType = try {
            TriggerPressType.valueOf(pressTypeName)
        } catch (e: Exception) {
            TriggerPressType.DOUBLE_PRESS
        }
        val consumeOriginal = prefs.getBoolean("consume_original", true)
        val vibrate = prefs.getBoolean("vibrate", true)
        val doublePressWindowMs = readTimingValue("double_press_window_ms", TriggerTimingSpecs.doublePressWindow)
        val longPressThresholdMs = readTimingValue("long_press_threshold_ms", TriggerTimingSpecs.longPressThreshold)
        val hapticDurationMs = readTimingValue("haptic_duration_ms", TriggerTimingSpecs.hapticDuration)

        return KeyMappingConfig(
            isEnabled = isEnabled,
            targetKeyCode = targetKeyCode,
            pressType = pressType,
            consumeOriginalEvent = consumeOriginal,
            vibrateOnTrigger = vibrate,
            doublePressWindowMs = doublePressWindowMs,
            longPressThresholdMs = longPressThresholdMs,
            hapticDurationMs = hapticDurationMs,
        )
    }

    private fun readTimingValue(key: String, spec: MillisecondSettingSpec): Long {
        val stored = runCatching { prefs.getLong(key, spec.default) }.getOrDefault(spec.default)
        return normalizeTimingValue(stored, spec)
    }

    fun updateConfig(newConfig: KeyMappingConfig) {
        val normalizedConfig = newConfig.copy(
            doublePressWindowMs = normalizeTimingValue(newConfig.doublePressWindowMs, TriggerTimingSpecs.doublePressWindow),
            longPressThresholdMs = normalizeTimingValue(newConfig.longPressThresholdMs, TriggerTimingSpecs.longPressThreshold),
            hapticDurationMs = normalizeTimingValue(newConfig.hapticDurationMs, TriggerTimingSpecs.hapticDuration),
        )
        if (normalizedConfig == _config.value) return

        prefs.edit().apply {
            putBoolean("is_enabled", normalizedConfig.isEnabled)
            if (normalizedConfig.targetKeyCode != null) {
                putInt("target_key_code", normalizedConfig.targetKeyCode)
            } else {
                remove("target_key_code")
            }
            putString("press_type", normalizedConfig.pressType.name)
            putBoolean("consume_original", normalizedConfig.consumeOriginalEvent)
            putBoolean("vibrate", normalizedConfig.vibrateOnTrigger)
            putLong("double_press_window_ms", normalizedConfig.doublePressWindowMs)
            putLong("long_press_threshold_ms", normalizedConfig.longPressThresholdMs)
            putLong("haptic_duration_ms", normalizedConfig.hapticDurationMs)
            apply()
        }
        _config.value = normalizedConfig
    }

    fun addSavedKey(keyCode: Int, name: String) {
        val current = _savedKeys.value.toMutableList()
        if (current.none { it.keyCode == keyCode }) {
            val shortName = name
            current.add(PhysicalKeyOption(keyCode, name, shortName, keyCode == KeyEvent.KEYCODE_POWER || keyCode == KeyEvent.KEYCODE_ASSIST))
            _savedKeys.value = current
            prefs.edit().putInt("saved_keys_count", current.size).apply()
            current.forEachIndexed { index, key ->
                prefs.edit().putInt("saved_key_${index}_code", key.keyCode).apply()
                prefs.edit().putString("saved_key_${index}_name", key.displayName).apply()
            }
        }
    }

    fun setTargetKey(keyCode: Int) {
        updateConfig(_config.value.copy(targetKeyCode = keyCode))
    }

    fun setPressType(type: TriggerPressType) {
        updateConfig(_config.value.copy(pressType = type))
    }

    fun setEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(isEnabled = enabled))
    }

    fun setConsumeOriginalEvent(consume: Boolean) {
        updateConfig(_config.value.copy(consumeOriginalEvent = consume))
    }

    fun setVibrate(vibrate: Boolean) {
        updateConfig(_config.value.copy(vibrateOnTrigger = vibrate))
    }

    fun setDoublePressWindowMs(value: Long) {
        updateConfig(_config.value.copy(doublePressWindowMs = value))
    }

    fun setLongPressThresholdMs(value: Long) {
        updateConfig(_config.value.copy(longPressThresholdMs = value))
    }

    fun setHapticDurationMs(value: Long) {
        updateConfig(_config.value.copy(hapticDurationMs = value))
    }
}
