package dev.auxdesign.voz.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.auxdesign.voz.R
import dev.auxdesign.voz.voice.OrbState

/** Decorative icon (the meaning is always in the text next to it). */
@Composable
fun VozIcon(@DrawableRes id: Int, tint: Color, size: Dp = 24.dp) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/** Back arrow + screen title, the title being a TalkBack heading. */
@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.width(8.dp))
        Heading(title, modifier = Modifier.weight(1f))
    }
}

/**
 * The mic orb: icon only (no text inside a fixed shape), a single tap toggles. Its state lives in the
 * stateDescription; the state line under it is hidden from TalkBack so nothing is read twice.
 */
@Composable
fun VoiceOrb(state: OrbState, level: Float?, stepText: String?, diameter: Dp, onToggle: () -> Unit) {
    val voz = LocalVozColors.current
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    val micOpen = state == OrbState.LISTENING || state == OrbState.CONFIRMING
    val working = state == OrbState.UNDERSTANDING || state == OrbState.ACTING || state == OrbState.SPEAKING
    val fillTarget = when {
        micOpen -> voz.voice
        working -> scheme.primaryContainer
        else -> voz.orb
    }
    val fill by animateColorAsState(fillTarget, animationSpec = tween(if (reduceMotion) 0 else 100), label = "orbFill")
    val iconColor = when {
        micOpen -> voz.onVoice
        working -> scheme.onPrimaryContainer
        else -> voz.onOrb
    }
    val ring: Color? = when {
        micOpen -> voz.voiceRing
        working || state == OrbState.STARTING -> scheme.outline
        else -> voz.orbRing
    }
    val stateText = stringResource(
        when (state) {
            OrbState.IDLE -> R.string.orb_sd_idle
            OrbState.STARTING -> R.string.orb_starting
            OrbState.LISTENING -> R.string.orb_listening
            OrbState.UNDERSTANDING -> R.string.orb_sd_understanding
            OrbState.ACTING -> R.string.orb_sd_understanding
            OrbState.SPEAKING -> R.string.orb_speaking
            OrbState.CONFIRMING -> R.string.orb_sd_confirming
        },
    )
    val description = stepText ?: stateText
    val orbLabel = stringResource(R.string.orb_cd)
    val clickLabel = stringResource(if (state == OrbState.IDLE) R.string.orb_action_start else R.string.orb_action_stop)
    val levelColor = voz.voiceRing ?: voz.voice

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter + 40.dp)) {
        if (micOpen) {
            // Level ring: only drawn from the real input level; a static ring when motion is off or the level is unknown.
            Box(
                Modifier
                    .size(diameter + 40.dp)
                    .drawBehind {
                        val extra = if (reduceMotion || level == null) 4.dp.toPx() else (4 + 12 * level).dp.toPx()
                        drawCircle(
                            color = levelColor,
                            radius = diameter.toPx() / 2 + extra / 2 + 2.dp.toPx(),
                            style = Stroke(width = extra),
                        )
                    },
            )
        }
        if ((state == OrbState.UNDERSTANDING || state == OrbState.ACTING) && !reduceMotion) {
            CircularProgressIndicator(modifier = Modifier.size(diameter + 16.dp), strokeWidth = 4.dp)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(fill)
                .then(if (ring != null) Modifier.border(4.dp, ring, CircleShape) else Modifier)
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onToggle)
                .semantics {
                    contentDescription = orbLabel
                    stateDescription = description
                },
        ) {
            VozIcon(if (state.showsStop) R.drawable.ic_stop else R.drawable.ic_mic, tint = iconColor, size = diameter * 0.4f)
        }
    }
}

private val OrbState.showsStop: Boolean
    get() = this == OrbState.UNDERSTANDING || this == OrbState.ACTING || this == OrbState.SPEAKING || this == OrbState.STARTING

enum class BannerKind { INFO, ATTENTION, ERROR, SUCCESS }

/** One problem, one recovery action. Never dismissed by a timer. */
@Composable
fun StatusBanner(kind: BannerKind, title: String, body: String?, action: String? = null, live: Boolean = false, onAction: () -> Unit = {}) {
    val voz = LocalVozColors.current
    val scheme = MaterialTheme.colorScheme
    val (container, content, icon, iconTint) = when (kind) {
        BannerKind.INFO -> Quad(scheme.surfaceContainer, scheme.onSurface, R.drawable.ic_info, scheme.primary)
        BannerKind.ATTENTION -> Quad(scheme.primaryContainer, scheme.onPrimaryContainer, R.drawable.ic_warning, scheme.onPrimaryContainer)
        BannerKind.ERROR -> Quad(scheme.errorContainer, scheme.onErrorContainer, R.drawable.ic_error, scheme.error)
        BannerKind.SUCCESS -> Quad(voz.successContainer, voz.onSuccessContainer, R.drawable.ic_check_circle, voz.success)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        border = voz.cardBorder?.let { BorderStroke(2.dp, it) },
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    if (live) liveRegion = LiveRegionMode.Polite
                },
            ) {
                VozIcon(icon, tint = iconTint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (action != null) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(action, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private data class Quad(val container: Color, val content: Color, @DrawableRes val icon: Int, val iconTint: Color)

/** A card with the brand surface (and a white border in high contrast). */
@Composable
fun VozCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val voz = LocalVozColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = voz.cardBorder?.let { BorderStroke(2.dp, it) },
        modifier = modifier.fillMaxWidth().widthIn(max = 600.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** One labelled row of a card, read by TalkBack as a single sentence. */
@Composable
fun CardRow(label: String, value: String, @DrawableRes icon: Int? = null, iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        if (icon != null) {
            VozIcon(icon, tint = iconTint)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * The in-app answer to "¿Confirmo?": large Yes/No buttons, not dismissible by a swipe or a tap outside.
 * The Yes label always repeats the action.
 */
@Composable
fun ConfirmSheet(what: String, partial: String, onYes: () -> Unit, onNo: () -> Unit) {
    val voz = LocalVozColors.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 3.dp,
        border = voz.cardBorder?.let { BorderStroke(2.dp, it) },
        modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.confirm_question, what), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.confirm_say), style = MaterialTheme.typography.bodyLarge)
            if (partial.isNotBlank()) {
                Text(stringResource(R.string.confirm_heard, partial), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onYes, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.confirm_yes, what), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.size(12.dp))
            OutlinedButton(onClick = onNo, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(R.string.confirm_no), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** "−  1.2 times normal  +": replaces sliders, which need a precise drag. */
@Composable
fun Stepper(label: String, value: String, decreaseLabel: String, increaseLabel: String, canDecrease: Boolean, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            OutlinedButton(onClick = onDecrease, enabled = canDecrease, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(56.dp).semantics { contentDescription = decreaseLabel }) {
                VozIcon(R.drawable.ic_remove, tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            OutlinedButton(onClick = onIncrease, enabled = canIncrease, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(56.dp).semantics { contentDescription = increaseLabel }) {
                VozIcon(R.drawable.ic_add, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A row that opens another screen. */
@Composable
fun NavRow(label: String, onClick: () -> Unit, description: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        VozIcon(R.drawable.ic_chevron_right, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A button that shows or hides [content]; TalkBack hears "Expanded" / "Collapsed". */
@Composable
fun Expander(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state },
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            VozIcon(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more, tint = MaterialTheme.colorScheme.onSurface)
        }
        if (expanded) content()
    }
}

/** Status of a permission: icon + words, never colour alone; announced when it changes on return from Settings. */
@Composable
fun PermissionStatus(granted: Boolean) {
    val voz = LocalVozColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        VozIcon(
            if (granted) R.drawable.ic_check_circle else R.drawable.ic_circle,
            tint = if (granted) voz.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (granted) R.string.status_allowed else R.string.status_not_allowed),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** The state line under the orb; hidden from TalkBack because the orb already carries the state. */
@Composable
fun OrbStateLine(text: String, hint: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
