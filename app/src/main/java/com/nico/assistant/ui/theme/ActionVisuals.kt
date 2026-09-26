package com.nico.assistant.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionGlyph
import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.action.ActionType

/** Icône Material Symbols de chaque pictogramme d'action. Exhaustif : rien n'est oublié. */
val ActionGlyph.icon: ImageVector
    get() = when (this) {
        ActionGlyph.APPS -> Symbols.AppsFilled
        ActionGlyph.LINK -> Symbols.Link
        ActionGlyph.SETTINGS -> Symbols.Settings
        ActionGlyph.SEND -> Symbols.SendFilled
        ActionGlyph.NAVIGATION -> Symbols.NavigationFilled
        ActionGlyph.WEB_SEARCH -> Symbols.TravelExplore
        ActionGlyph.CALL -> Symbols.CallFilled
        ActionGlyph.SMS -> Symbols.SmsFilled
        ActionGlyph.SHARE -> Symbols.ShareFilled
        ActionGlyph.CHAT -> Symbols.ForumFilled
        ActionGlyph.MUSIC -> Symbols.MusicNoteFilled
        ActionGlyph.MUSIC_QUEUE -> Symbols.QueueMusic
        ActionGlyph.PLAY_PAUSE -> Symbols.PlayPause
        ActionGlyph.WIFI -> Symbols.Wifi
        ActionGlyph.BLUETOOTH -> Symbols.Bluetooth
        ActionGlyph.DO_NOT_DISTURB -> Symbols.DoNotDisturbOnFilled
        ActionGlyph.AIRPLANE -> Symbols.FlightFilled
        ActionGlyph.ROTATION -> Symbols.ScreenRotation
        ActionGlyph.TORCH -> Symbols.FlashlightOnFilled
        ActionGlyph.VOLUME -> Symbols.VolumeUpFilled
        ActionGlyph.BRIGHTNESS -> Symbols.Brightness6Filled
        ActionGlyph.TERMINAL -> Symbols.Terminal
        ActionGlyph.SPEAK -> Symbols.RecordVoiceOverFilled
        ActionGlyph.VIBRATE -> Symbols.Vibration
        ActionGlyph.WAIT -> Symbols.HourglassTopFilled
        ActionGlyph.TIMER -> Symbols.Timer
        ActionGlyph.ALARM -> Symbols.Alarm
        ActionGlyph.NOTE -> Symbols.EditNote
        ActionGlyph.COPY -> Symbols.ContentCopy
        ActionGlyph.NOTIFICATION -> Symbols.NotificationsFilled
        ActionGlyph.AUTOMATION -> Symbols.AccountTreeFilled
    }

val ActionCategory.color: Color get() = Color(accent)

/** Icône, couleur et libellé d'une action, tels que tous les écrans les affichent. */
@Immutable
data class ActionVisual(val icon: ImageVector, val color: Color, val label: String)

/**
 * Point d'entrée unique des écrans : ils ne choisissent jamais eux-mêmes une icône ou une
 * couleur d'action.
 */
fun ActionType.visual(): ActionVisual {
    val action = ActionRegistry.find(this)
    return ActionVisual(
        icon = glyph.icon,
        color = action?.category?.color ?: NicoColors.TextSecondary,
        label = action?.label ?: name
    )
}

/**
 * Pastille ronde colorée portant l'icône d'une action : fond teinté translucide, icône à
 * la couleur de la catégorie, liseré lumineux.
 */
@Composable
fun ActionIconBadge(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    dimmed: Boolean = false
) {
    val tint = if (dimmed) NicoColors.TextDisabled else color
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.linearGradient(
                    0f to tint.copy(alpha = 0.34f),
                    1f to tint.copy(alpha = 0.12f)
                ),
                CircleShape
            )
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (dimmed) tint else Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun ActionIconBadge(type: ActionType, modifier: Modifier = Modifier, size: Dp = 40.dp, dimmed: Boolean = false) {
    val visual = type.visual()
    ActionIconBadge(visual.icon, visual.color, modifier, size, dimmed)
}

/** Variante carrée arrondie, pour les tuiles du sélecteur d'action. */
@Composable
fun ActionIconTile(type: ActionType, modifier: Modifier = Modifier, size: Dp = 44.dp, dimmed: Boolean = false) {
    val visual = type.visual()
    val tint = if (dimmed) NicoColors.TextDisabled else visual.color
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.linearGradient(0f to tint.copy(alpha = 0.38f), 1f to tint.copy(alpha = 0.14f)),
                shape
            )
            .border(1.dp, tint.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(visual.icon, contentDescription = null, tint = if (dimmed) tint else Color.White, modifier = Modifier.size(size * 0.52f))
    }
}

@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun ActionVisualsPreview() {
    GlassPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIconBadge(Symbols.DoNotDisturbOnFilled, Color(ActionCategory.SYSTEME.accent))
            ActionIconBadge(Symbols.MusicNoteFilled, Color(ActionCategory.MEDIA.accent))
            ActionIconBadge(Symbols.CallFilled, Color(ActionCategory.COMMUNICATION.accent))
            ActionIconBadge(Symbols.AppsFilled, Color(ActionCategory.APPS.accent))
            ActionIconBadge(Symbols.Timer, Color(ActionCategory.UTILITAIRES.accent))
            ActionIconBadge(Symbols.Terminal, Color(ActionCategory.SYSTEME.accent), dimmed = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIconTile(ActionType.TOGGLE_WIFI)
            ActionIconTile(ActionType.SPEAK)
            ActionIconTile(ActionType.RUN_SHELL, dimmed = true)
        }
    }
}
