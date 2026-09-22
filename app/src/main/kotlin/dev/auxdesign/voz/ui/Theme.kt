package dev.auxdesign.voz.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Brand = Color(0xFF1B3A8C)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Color(0xFF00696E),
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF0A2A6B),
    secondary = Color(0xFF7ED6DC),
    onSecondary = Color(0xFF00363A),
)

/** Black and yellow, maximum contrast (well above WCAG AAA 7:1). */
private val HighContrastColors = darkColorScheme(
    primary = Color(0xFFFFE600),
    onPrimary = Color.Black,
    secondary = Color(0xFF00E5FF),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color.White,
    outline = Color.White,
)

@Composable
fun VozTheme(highContrast: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        highContrast -> HighContrastColors
        isSystemInDarkTheme() -> DarkColors
        else -> LightColors
    }
    val base = Typography()
    val typography = if (highContrast) {
        base.copy(
            bodyLarge = base.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
            bodyMedium = base.bodyMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
            labelLarge = base.labelLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
    } else {
        base
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

@Composable
fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** A full-row switch: the whole row is one 48dp+ touch target and one TalkBack item. */
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
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
