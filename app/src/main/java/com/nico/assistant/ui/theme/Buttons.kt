package com.nico.assistant.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassButtonStyle {
    /** Action principale de l'écran : aplat rouge Nothing. */
    Primary,

    /** Action secondaire : verre. */
    Secondary,

    /** Action tertiaire : texte seul. */
    Ghost,

    /** Destructrice : verre, texte rouge. */
    Danger
}

/**
 * Bouton du design system. Forme pilule, 52 dp de haut.
 *
 * Désactivé, il perd sa couleur **et** son contraste : impossible de le confondre avec un
 * bouton actif (c'était le défaut du ✓ gris de l'ancien éditeur).
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()
    val shape = CircleShape
    val active = enabled && !loading

    val base = modifier
        .defaultMinSize(minHeight = height)
        .then(if (active) Modifier.pressScale(interaction) else Modifier)

    val surface = when {
        !enabled -> base.glass(shape, fill = NicoColors.GlassFillSubtle, sheen = false)
        style == GlassButtonStyle.Primary -> base
            .glass(shape, fill = Color.Transparent, border = GlassBrushes.SpecularBorder)
            .background(GlassBrushes.Primary, shape)
            .background(GlassBrushes.Sheen, shape)
        style == GlassButtonStyle.Ghost -> base
        else -> base.glass(shape, fill = NicoColors.GlassFillRaised)
    }

    val contentColor = when {
        !enabled -> NicoColors.TextDisabled
        style == GlassButtonStyle.Primary -> Color.White
        style == GlassButtonStyle.Danger -> NicoColors.RedBright
        else -> NicoColors.TextPrimary
    }

    Box(
        modifier = surface
            .background(if (pressed) NicoColors.GlassFillPressed else Color.Transparent, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
            .padding(horizontal = NicoSpacing.lg, vertical = NicoSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith fadeOut() },
            label = "chargement"
        ) { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NicoSpacing.xs)
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

enum class GlassIconButtonStyle { Glass, Plain, Primary }

/** Bouton icône rond : 48 dp de zone tactile, quelle que soit la taille visible. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassIconButtonStyle = GlassIconButtonStyle.Plain,
    enabled: Boolean = true,
    tint: Color = NicoColors.TextPrimary,
    size: Dp = NicoSpacing.touchTarget,
    iconSize: Dp = 22.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val disabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.35f,
        animationSpec = NicoMotion.gentle(),
        label = "désactivé"
    )

    val surface = when (style) {
        GlassIconButtonStyle.Glass -> Modifier.glass(CircleShape, fill = NicoColors.GlassFillRaised)
        GlassIconButtonStyle.Primary -> Modifier
            .glass(CircleShape, fill = Color.Transparent)
            .background(if (enabled) GlassBrushes.Primary else SolidTransparent, CircleShape)
        GlassIconButtonStyle.Plain -> Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction, pressedScale = 0.9f)
            .then(surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
            .alpha(disabledAlpha),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

private val SolidTransparent = SolidColor(Color.Transparent)

@Preview(widthDp = 360, heightDp = 420)
@Composable
private fun GlassButtonPreview() {
    GlassPreview {
        GlassButton("Tester maintenant", onClick = {}, icon = Symbols.PlayArrowFilled, modifier = Modifier.fillMaxWidth())
        GlassButton("Ajouter une action", onClick = {}, icon = Symbols.Add, style = GlassButtonStyle.Secondary)
        GlassButton("Désactivé", onClick = {}, enabled = false)
        GlassButton("Supprimer", onClick = {}, style = GlassButtonStyle.Danger, icon = Symbols.Delete)
        GlassButton("Plus tard", onClick = {}, style = GlassButtonStyle.Ghost)
        Row(horizontalArrangement = Arrangement.spacedBy(NicoSpacing.sm)) {
            GlassIconButton(Symbols.Settings, "Réglages", onClick = {}, style = GlassIconButtonStyle.Glass)
            GlassIconButton(Symbols.Check, "Enregistrer", onClick = {}, style = GlassIconButtonStyle.Primary)
            GlassIconButton(Symbols.Check, "Enregistrer", onClick = {}, style = GlassIconButtonStyle.Primary, enabled = false)
        }
    }
}
