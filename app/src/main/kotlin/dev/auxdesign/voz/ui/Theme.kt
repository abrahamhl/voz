package dev.auxdesign.voz.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Brand roles Material 3 has no slot for. Yellow ([voice]) means "the mic is open" and nothing else. */
@Immutable
data class VozColors(
    val orb: Color,
    val onOrb: Color,
    val orbRing: Color?,
    val voice: Color,
    val onVoice: Color,
    val voiceRing: Color?,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** High contrast draws a white border around cards instead of relying on surface tints. */
    val cardBorder: Color?,
)

private val Navy = Color(0xFF1B3A8C)
private val Yellow = Color(0xFFFFE600)

private val LightScheme = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFE6EBFF), onPrimaryContainer = Color(0xFF0F2463),
    secondary = Navy, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EBFF), onSecondaryContainer = Color(0xFF0F2463),
    tertiary = Navy, onTertiary = Color.White,
    background = Color.White, onBackground = Color(0xFF0B1020),
    surface = Color.White, onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFF3F6FD), onSurfaceVariant = Color(0xFF3D4660),
    surfaceTint = Navy,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F6FD),
    surfaceContainer = Color(0xFFF3F6FD), surfaceContainerHigh = Color(0xFFE6EBFF),
    surfaceContainerHighest = Color(0xFFE6EBFF),
    outline = Color(0xFF5B6585), outlineVariant = Color(0xFFD5DBEB),
    error = Color(0xFFC62828), onError = Color.White,
    errorContainer = Color(0xFFFDECEC), onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF0F2463),
    primaryContainer = Color(0xFF1D2748), onPrimaryContainer = Color(0xFFD7DEF7),
    secondary = Color(0xFFB4C5FF), onSecondary = Color(0xFF0F2463),
    secondaryContainer = Color(0xFF1D2748), onSecondaryContainer = Color(0xFFD7DEF7),
    tertiary = Color(0xFFB4C5FF), onTertiary = Color(0xFF0F2463),
    background = Color(0xFF0B1020), onBackground = Color(0xFFEEF1FA),
    surface = Color(0xFF0B1020), onSurface = Color(0xFFEEF1FA),
    surfaceVariant = Color(0xFF141B33), onSurfaceVariant = Color(0xFFB9C1D9),
    surfaceTint = Color(0xFFB4C5FF),
    surfaceContainerLowest = Color(0xFF0B1020), surfaceContainerLow = Color(0xFF141B33),
    surfaceContainer = Color(0xFF141B33), surfaceContainerHigh = Color(0xFF1D2748),
    surfaceContainerHighest = Color(0xFF1D2748),
    outline = Color(0xFF8791B0), outlineVariant = Color(0xFF2A3350),
    error = Color(0xFFFF8A80), onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF3D1418), onErrorContainer = Color(0xFFFFD9D6),
)

private val HighContrastScheme = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A1A1A), onPrimaryContainer = Color.White,
    secondary = Color.White, onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A1A), onSecondaryContainer = Color.White,
    tertiary = Color.White, onTertiary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color.White,
    surfaceTint = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1A1A1A), surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    outline = Color.White, outlineVariant = Color.White,
    error = Color(0xFFFF8A80), onError = Color.Black,
    errorContainer = Color(0xFF330000), onErrorContainer = Color.White,
)

private val LightVoz = VozColors(
    orb = Navy, onOrb = Color.White, orbRing = null,
    voice = Yellow, onVoice = Color(0xFF0B1020), voiceRing = Navy,
    success = Color(0xFF1B6E3A), successContainer = Color(0xFFE4F3E8), onSuccessContainer = Color(0xFF0E3B1F),
    cardBorder = null,
)

private val DarkVoz = VozColors(
    orb = Navy, onOrb = Color.White, orbRing = Color(0xFFB4C5FF),
    voice = Yellow, onVoice = Color(0xFF0B1020), voiceRing = null,
    success = Color(0xFF7DD89A), successContainer = Color(0xFF12351F), onSuccessContainer = Color(0xFFC8F2D4),
    cardBorder = null,
)

private val HighContrastVoz = VozColors(
    orb = Color.Black, onOrb = Color.White, orbRing = Color.White,
    voice = Yellow, onVoice = Color.Black, voiceRing = null,
    success = Color(0xFF7CFC9A), successContainer = Color(0xFF002200), onSuccessContainer = Color.White,
    cardBorder = Color.White,
)

val LocalVozColors = staticCompositionLocalOf { LightVoz }

/** True when the user turned off animations ("Remove animations"): every motion becomes instant. */
val LocalReduceMotion = staticCompositionLocalOf { false }

private fun typography(highContrast: Boolean): Typography {
    val body = if (highContrast) FontWeight.Medium else FontWeight.Normal
    return Typography(
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = body),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = body),
        labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun VozTheme(highContrast: Boolean, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = when {
        highContrast -> HighContrastScheme
        dark -> DarkScheme
        else -> LightScheme
    }
    val voz = when {
        highContrast -> HighContrastVoz
        dark -> DarkVoz
        else -> LightVoz
    }
    var reduceMotion by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduceMotion = !ValueAnimator.areAnimatorsEnabled() }
    CompositionLocalProvider(LocalVozColors provides voz, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = typography(highContrast), content = content)
    }
}

/** Screen title: a TalkBack heading. */
@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** Group title inside a screen: a heading too, so TalkBack users can jump between groups. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** A full-row switch: the whole row is one 56dp touch target and one TalkBack item. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null, enabled: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A full-row radio option (use inside a `selectableGroup`). */
@Composable
fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
