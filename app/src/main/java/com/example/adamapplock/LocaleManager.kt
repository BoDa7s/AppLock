package com.example.adamapplock

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {
    data class LanguageOption(val tag: String?, @StringRes val labelRes: Int)

    val supportedLanguages: List<LanguageOption> = listOf(
        LanguageOption(null, R.string.language_system_default),
        LanguageOption("en", R.string.language_english),
        LanguageOption("ar", R.string.language_arabic),
        LanguageOption("es", R.string.language_spanish),
        LanguageOption("fr", R.string.language_french),
        LanguageOption("hi", R.string.language_hindi),
    )

    fun applyStoredLocale(context: Context) {
        val storedTag = Prefs.getLanguageCode(context)
        val locales = if (storedTag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(storedTag)
        }

        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun setAppLanguage(context: Context, languageTag: String?) {
        Prefs.setLanguageCode(context, languageTag)
        applyStoredLocale(context)
    }

    fun getCurrentLanguageTag(context: Context): String? =
        Prefs.getLanguageCode(context)

    fun findOption(languageTag: String?): LanguageOption =
        supportedLanguages.firstOrNull { it.tag == languageTag } ?: supportedLanguages.first()
}
