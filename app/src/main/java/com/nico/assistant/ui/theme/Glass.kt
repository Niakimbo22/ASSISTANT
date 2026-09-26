package com.nico.assistant.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La matière « verre » : remplissage blanc translucide, brillance en haut, bordure
 * spéculaire 1 dp. Aucune ombre : la profondeur vient de la transparence.
 *
 * Sur les cartes, le fond derrière est fait de halos déjà flous : un vrai flou n'y changerait
 * rien à l'œil mais coûterait un rendu par carte. Le flou réel (Haze) est réservé aux
 * surfaces qui flottent au-dessus de contenu net — barres, dock, pastille d'écoute — et aux
 * feuilles et dialogues (flou de fenêtre).
 */
fun Modifier.glass(
    shape: Shape,
    fill: Color = NicoColors.GlassFill,
    border: Brush? = GlassBrushes.SpecularBorder,
    sheen: Boolean = true,
    borderWidth: Dp = 1.dp
): Modifier {
    var result = this
        .clip(shape)
        .background(fill, shape)
    if (sheen) result = result.background(GlassBrushes.Sheen, shape)
    if (border != null) result = result.border(borderWidth, border, shape)
    return result
}

/** Léger enfoncement au toucher, sur ressort. */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = NicoMotion.PressedScale
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = NicoMotion.snappy(),
        label = "enfoncement"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Carte en verre. Cliquable si [onClick] est fourni : elle s'enfonce, s'éclaire et
 * donne un petit retour haptique.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(NicoRadius.Card),
    fill: Color = NicoColors.GlassFill,
    border: Brush? = GlassBrushes.SpecularBorder,
    contentPadding: PaddingValues = PaddingValues(NicoSpacing.lg),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberHaptics()

    var surface = modifier
    if (onClick != null) surface = surface.pressScale(interaction)
    surface = surface.glass(shape = shape, fill = fill, border = border)
    if (onClick != null) {
        surface = surface
            .background(if (pressed) NicoColors.GlassFillPressed else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
    }

    Column(
        modifier = surface.padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/** Zone d'aperçu : le vrai fond animé, pour juger le verre dans son contexte. */
@Composable
internal fun GlassPreview(content: @Composable ColumnScope.() -> Unit) {
    NicoAssistantTheme {
        Box {
            GlassBackdrop()
            Column(
                modifier = Modifier.fillMaxWidth().padding(NicoSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(NicoSpacing.md),
                content = content
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 360)
@Composable
private fun GlassCardPreview() {
    GlassPreview {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("Carte en verre", style = MaterialTheme.typography.titleMedium)
            Text(
                "Blanc 8 %, bordure spéculaire, coins 26 dp.",
                style = MaterialTheme.typography.bodyMedium,
                color = NicoColors.TextSecondary
            )
        }
        GlassCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            Text("Carte cliquable", style = MaterialTheme.typography.titleMedium)
        }
    }
}
