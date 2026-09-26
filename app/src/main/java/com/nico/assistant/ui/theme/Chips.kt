package com.nico.assistant.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Pastille en verre : phrases déclenchantes, slots, filtres.
 *
 * 40 dp visibles ; le bouton de retrait garde une zone tactile de 40 dp au lieu d'une
 * croix de 16 dp difficile à viser.
 */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    accent: Color? = null,
    selected: Boolean = false,
    mono: Boolean = false,
    onRemove: (() -> Unit)? = null,
    removeDescription: String = "Retirer"
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(NicoRadius.Chip)
    val fill = when {
        selected && accent != null -> accent.copy(alpha = 0.22f)
        selected -> NicoColors.GlassFillRaised
        accent != null -> accent.copy(alpha = 0.12f)
        else -> NicoColors.GlassFill
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(shape)
            .background(fill, shape)
            .border(
                1.dp,
                if (selected) GlassBrushes.SpecularBorderStrong else GlassBrushes.SpecularBorder,
                shape
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        haptics.tick()
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(start = if (leadingIcon != null) 10.dp else 14.dp, end = if (onRemove != null) 2.dp else 14.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = accent ?: NicoColors.TextSecondary,
                modifier = Modifier.padding(end = 6.dp).size(18.dp)
            )
        }
        Text(
            text = text,
            style = if (mono) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            color = if (mono && accent != null) accent else NicoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = removeDescription) {
                        haptics.reject()
                        onRemove()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Symbols.Close,
                    contentDescription = removeDescription,
                    tint = NicoColors.TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Petit badge mono : « SHIZUKU », « REPLI », « CRITIQUE ». Signale un état sans prendre la
 * parole.
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NicoColors.TextSecondary,
    icon: ImageVector? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/**
 * Libellé de section, en mono : « QUAND JE DIS ». Le seul endroit, avec les compteurs, où la
 * police mono apparaît.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = NicoColors.TextTertiary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.heightIn(min = 24.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 8.dp).size(16.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(widthDp = 360, heightDp = 360)
@Composable
private fun GlassChipsPreview() {
    GlassPreview {
        SectionLabel("Quand je dis", icon = Symbols.Mic)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("« bonne nuit »", onRemove = {})
            GlassChip("« dodo »", onRemove = {})
            GlassChip("{app}", mono = true, accent = NicoColors.Condition, leadingIcon = Symbols.Add, onClick = {})
            GlassChip("Système", selected = true, onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge("Shizuku", color = NicoColors.Warning, icon = Symbols.LockFilled)
            StatusBadge("Repli", color = NicoColors.TextSecondary)
            StatusBadge("Critique", color = NicoColors.RedBright)
        }
    }
}
