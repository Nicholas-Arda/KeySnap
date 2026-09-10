package com.example.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import io.github.nicholasarda.keysnap.R
import java.util.Locale

/**
 * The app's in-app language choice.
 *
 * SharedPreferences is the single source of truth even on Android 13+, where [LocaleManager] keeps
 * its own copy: two stores that can disagree is worse than one store plus a mirror. [wrap] is what
 * actually applies the choice on Android 11/12, which have no [LocaleManager]; on 13+ the mirror is
 * what carries the language into services and toasts, whose contexts the activity never wraps.
 *
 * An empty tag means "follow the system", and then nothing is overridden at all — so the per-app
 * language screen Android 13+ offers keeps working for anyone who has never opened this picker.
 */
object AppLocale {

    private const val KEY = "app_language"

    /** Persisted tag to its label. The empty tag is "System default". */
    val options: List<Pair<String, Int>> = listOf(
        "" to R.string.language_system,
        // Alphabetical by native name: Latin script first, then Cyrillic, Arabic, Devanagari.
        "de" to R.string.language_german,
        "en" to R.string.language_english,
        "es" to R.string.language_spanish,
        "fr" to R.string.language_french,
        "it" to R.string.language_italian,
        "pt-BR" to R.string.language_portuguese,
        "tr" to R.string.language_turkish,
        "ru" to R.string.language_russian,
        "ar" to R.string.language_arabic,
        "hi" to R.string.language_hindi,
    )

    @StringRes
    fun labelFor(tag: String): Int =
        options.firstOrNull { it.first == tag }?.second ?: R.string.language_system

    /** Flag emoji for the tag's country; empty for "follow the system", which has no country. */
    fun flagFor(tag: String): String = when (tag) {
        "en" -> "🇬🇧"
        "tr" -> "🇹🇷"
        "de" -> "🇩🇪"
        "es" -> "🇪🇸"
        "fr" -> "🇫🇷"
        "ar" -> "🇸🇦"
        "pt-BR" -> "🇧🇷"
        "ru" -> "🇷🇺"
        "it" -> "🇮🇹"
        "hi" -> "🇮🇳"
        else -> ""
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("arda_mapper_prefs", Context.MODE_PRIVATE)

    fun stored(context: Context): String = prefs(context).getString(KEY, "").orEmpty()

    /** Persists the choice and mirrors it to the platform. The caller recreates the activity. */
    fun set(context: Context, tag: String) {
        prefs(context).edit().putString(KEY, tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    /** Call from `attachBaseContext`. Returns [base] unchanged while the system language is chosen. */
    fun wrap(base: Context): Context {
        val tag = stored(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
