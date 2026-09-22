package dev.auxdesign.voz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.auxdesign.voz.core.model.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class BubbleSize(val dp: Int) { SMALL(56), MEDIUM(72), LARGE(96) }

data class VozSettings(
    /** null = follow the phone language. */
    val language: Lang? = null,
    val cloudEnabled: Boolean = false,
    val commentDepth: Int = DEFAULT_COMMENT_DEPTH,
    val speechRate: Float = 1.0f,
    val bubbleSize: BubbleSize = BubbleSize.MEDIUM,
    val highContrast: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    fun sanitized(): VozSettings = copy(
        commentDepth = commentDepth.coerceIn(MIN_COMMENT_DEPTH, MAX_COMMENT_DEPTH),
        speechRate = speechRate.coerceIn(MIN_RATE, MAX_RATE),
    )

    companion object {
        const val DEFAULT_COMMENT_DEPTH = 5
        const val MIN_COMMENT_DEPTH = 1
        const val MAX_COMMENT_DEPTH = 15
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
    }
}

/** Maps settings to and from DataStore preferences (pure, unit-tested). */
object SettingsCodec {
    val LANGUAGE = stringPreferencesKey("language")
    val CLOUD = booleanPreferencesKey("cloud_enabled")
    val DEPTH = intPreferencesKey("comment_depth")
    val RATE = floatPreferencesKey("speech_rate")
    val BUBBLE = stringPreferencesKey("bubble_size")
    val CONTRAST = booleanPreferencesKey("high_contrast")
    val ONBOARDED = booleanPreferencesKey("onboarding_done")

    fun read(p: Preferences): VozSettings {
        val defaults = VozSettings()
        return VozSettings(
            language = Lang.fromTag(p[LANGUAGE]),
            cloudEnabled = p[CLOUD] ?: defaults.cloudEnabled,
            commentDepth = p[DEPTH] ?: defaults.commentDepth,
            speechRate = p[RATE] ?: defaults.speechRate,
            bubbleSize = BubbleSize.entries.firstOrNull { it.name == p[BUBBLE] } ?: defaults.bubbleSize,
            highContrast = p[CONTRAST] ?: defaults.highContrast,
            onboardingDone = p[ONBOARDED] ?: defaults.onboardingDone,
        ).sanitized()
    }

    fun write(p: MutablePreferences, settings: VozSettings) {
        val s = settings.sanitized()
        val lang = s.language
        if (lang == null) p.remove(LANGUAGE) else p[LANGUAGE] = lang.tag
        p[CLOUD] = s.cloudEnabled
        p[DEPTH] = s.commentDepth
        p[RATE] = s.speechRate
        p[BUBBLE] = s.bubbleSize.name
        p[CONTRAST] = s.highContrast
        p[ONBOARDED] = s.onboardingDone
    }
}

private val Context.vozDataStore: DataStore<Preferences> by preferencesDataStore(name = "voz_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<VozSettings> = context.vozDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(SettingsCodec::read)

    suspend fun update(transform: (VozSettings) -> VozSettings) {
        context.vozDataStore.edit { prefs -> SettingsCodec.write(prefs, transform(SettingsCodec.read(prefs))) }
    }
}
