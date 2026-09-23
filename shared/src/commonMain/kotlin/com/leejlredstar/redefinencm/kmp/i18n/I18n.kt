package com.leejlredstar.redefinencm.kmp.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A language the app has copy for. [tag] is its BCP 47 language subtag. */
enum class AppLanguage(val tag: String) {
    ZH("zh"),
    EN("en"),
    JA("ja"),
    ;

    /** This language's copy, generated from `src/commonMain/i18n/<tag>.xml`. */
    val strings: AppStrings
        get() = when (this) {
            ZH -> ZhStrings
            EN -> EnStrings
            JA -> JaStrings
        }
}

/** The choice on the settings page: one language, or whatever the system uses. */
enum class LanguageSetting(val wireValue: String, val language: AppLanguage?) {
    SYSTEM("system", null),
    ZH("zh", AppLanguage.ZH),
    EN("en", AppLanguage.EN),
    JA("ja", AppLanguage.JA),
    ;

    /** The option's label. Each language is named in itself; only "follow system" is translated. */
    val displayName: String
        get() = when (this) {
            SYSTEM -> strings.followSystem
            ZH -> "简体中文"
            EN -> "English"
            JA -> "日本語"
        }

    companion object {
        fun fromWireValue(value: String): LanguageSetting =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

/** The system's preferred languages, most preferred first, as BCP 47 tags ("zh-Hans-CN", "en-US"). */
expect fun systemLanguageTags(): List<String>

/**
 * The language the app shows, and its copy.
 *
 * [strings] and [language] are snapshot state: a composable that reads them, directly or through
 * a getter such as an enum's `displayName`, recomposes when the user switches language. Code
 * outside composition reads the current language at the moment it builds a message.
 */
object I18n {
    private val _setting = MutableStateFlow(LanguageSetting.SYSTEM)
    val setting: StateFlow<LanguageSetting> = _setting.asStateFlow()

    private val _language = MutableStateFlow(resolveLanguage(LanguageSetting.SYSTEM, systemLanguageTags()))

    /** [language] for observers outside composition, such as the notification channels. */
    val languageFlow: StateFlow<AppLanguage> = _language.asStateFlow()

    private var current by mutableStateOf(_language.value)

    val language: AppLanguage get() = current
    val strings: AppStrings get() = current.strings

    /**
     * Reads the stored choice where settings answer at once (desktop, iOS, the browser); call it
     * before the first frame so a stored language never flashes the system's.
     */
    fun load(settings: PlatformSettings) {
        apply(LanguageSetting.fromWireValue(settings.getString(SettingKeys.APP_LANGUAGE, "")))
    }

    /** Reads the stored choice once settings have loaded; Android's DataStore needs this. */
    suspend fun loadStored(settings: PlatformSettings) {
        apply(LanguageSetting.fromWireValue(settings.getStringAsync(SettingKeys.APP_LANGUAGE, "")))
    }

    fun apply(setting: LanguageSetting) {
        _setting.value = setting
        update(resolveLanguage(setting, systemLanguageTags()))
    }

    /** Re-reads the system's languages after they may have changed; only matters while following them. */
    fun refreshSystemLanguage() {
        if (_setting.value == LanguageSetting.SYSTEM) {
            update(resolveLanguage(LanguageSetting.SYSTEM, systemLanguageTags()))
        }
    }

    private fun update(language: AppLanguage) {
        _language.value = language
        current = language
    }
}

/**
 * The language for [setting]. Following the system takes the first of its languages the app has
 * copy for, so a system set to French then Japanese gets Japanese; any Chinese script or region
 * gets Simplified Chinese; a system with none of the three gets English.
 */
internal fun resolveLanguage(setting: LanguageSetting, systemTags: List<String>): AppLanguage =
    setting.language ?: systemTags.firstNotNullOfOrNull(::appLanguageOf) ?: AppLanguage.EN

internal fun appLanguageOf(tag: String): AppLanguage? =
    when (tag.substringBefore('-').substringBefore('_').lowercase()) {
        "zh" -> AppLanguage.ZH
        "en" -> AppLanguage.EN
        "ja" -> AppLanguage.JA
        else -> null
    }

/** The app's copy in the language it shows. Inside a composable, reading it tracks a switch. */
val strings: AppStrings get() = I18n.strings

/**
 * A message held as "what to say" rather than as text, so one already on screen follows a
 * language switch. View models keep these; the UI turns them into words with [text].
 *
 * Two messages are equal when they read the same in every language, so a state holding one
 * still compares by value (StateFlow dedup, test assertions) although it wraps a function.
 */
abstract class UiText {
    abstract fun resolve(strings: AppStrings): String

    override fun equals(other: Any?): Boolean =
        other is UiText && AppLanguage.entries.all { resolve(it.strings) == other.resolve(it.strings) }

    override fun hashCode(): Int = resolve(AppLanguage.ZH.strings).hashCode()

    override fun toString(): String = resolve(AppLanguage.ZH.strings)
}

/** A message worded by [words] from whichever language's copy it is shown in. */
fun UiText(words: (AppStrings) -> String): UiText = object : UiText() {
    override fun resolve(strings: AppStrings): String = words(strings)
}

/** The message in the language shown now; inside a composable it recomposes on a switch. */
val UiText.text: String get() = resolve(I18n.strings)

/** A message that is already words, such as a server's own error text. */
fun uiText(verbatim: String): UiText = UiText { verbatim }

/**
 * A large count in the short form each language uses: 12万 / 3亿 in Chinese, 12万 / 3億 in
 * Japanese, 12K / 3M / 1B in English. Whole units only (rounded down).
 */
fun compactCount(value: Long): String = when (I18n.language) {
    AppLanguage.ZH -> when {
        value >= 100_000_000L -> "${value / 100_000_000L}亿"
        value >= 10_000L -> "${value / 10_000L}万"
        else -> value.toString()
    }
    AppLanguage.JA -> when {
        value >= 100_000_000L -> "${value / 100_000_000L}億"
        value >= 10_000L -> "${value / 10_000L}万"
        else -> value.toString()
    }
    AppLanguage.EN -> when {
        value >= 1_000_000_000L -> "${value / 1_000_000_000L}B"
        value >= 1_000_000L -> "${value / 1_000_000L}M"
        value >= 1_000L -> "${value / 1_000L}K"
        else -> value.toString()
    }
}
