package dev.auxdesign.voz.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.auxdesign.voz.core.model.Lang
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Localized strings for spoken replies, in the language VOZ is speaking (may differ from the UI locale). */
class Strings(private val app: Context) {
    private val contexts = ConcurrentHashMap<Lang, Context>()

    fun get(lang: Lang, @StringRes id: Int, vararg args: Any): String = localized(lang).getString(id, *args)

    private fun localized(lang: Lang): Context = contexts.getOrPut(lang) {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang.tag))
        app.createConfigurationContext(config)
    }
}

/** Permission checks and the system screens used by onboarding. */
object Permissions {
    fun hasMic(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun canOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun runtimeRequest(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

    fun overlaySettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    fun appInfo(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Opens a settings screen, ignoring devices that do not have it. */
    fun open(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
    }
}

/** The language VOZ listens and speaks in: the user override, else the phone language, else English. */
fun effectiveLang(override: Lang?): Lang = override ?: Lang.fromTagOrDefault(Locale.getDefault().toLanguageTag())
