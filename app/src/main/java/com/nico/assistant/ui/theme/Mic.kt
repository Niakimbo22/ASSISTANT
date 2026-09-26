package com.nico.assistant.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Gros bouton micro en verre rouge, entouré d'un halo qui respire. Pendant l'écoute, le halo
 * suit le niveau sonore (onRmsChanged).
 *
 * @param level niveau sonore brut d'onRmsChanged (≈ -2 à 10 dB).
 */
@Composable
fun MicOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    level: Float = 0f,
    size: Dp = 68.dp
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val orbSize = size
    val breath = rememberInfiniteTransition(label = "halo")
    val pulse by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "pulsation"
    )
    val normalized = (level.coerceIn(0f, 10f) / 10f)
    val loudness by animateFloatAsState(
        targetValue = if (listening) normalized else 0f,
        animationSpec = NicoMotion.snappy(),
        label = "niveau"
    )
    val intensity by animateFloatAsState(
        targetValue = if (listening) 1f else 0.55f,
        animationSpec = NicoMotion.gentle(),
        label = "intensité"
    )

    Box(
        modifier = modifier.size(size * 1.9f),
        contentAlignment = Alignment.Center
    ) {
        // Deux anneaux qui s'éloignent en s'estompant, décalés d'une demi-période.
        Canvas(modifier = Modifier.matchParentSize()) {
            val base = orbSize.toPx() / 2f
            val maxExtra = (this.size.minDimension / 2f) - base
            for (shift in listOf(0f, 0.5f)) {
                val p = (pulse + shift) % 1f
                val radius = base + maxExtra * p * (0.75f + loudness * 0.25f)
                drawCircle(
                    color = NicoColors.NothingRed.copy(alpha = (1f - p) * 0.45f * intensity),
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            // Lueur douce proportionnelle à la voix.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to NicoColors.NothingRed.copy(alpha = 0.35f * intensity + loudness * 0.3f),
                    1f to Color.Transparent,
                    center = center,
                    radius = base * (1.5f + loudness * 0.4f)
                ),
                radius = base * (1.5f + loudness * 0.4f)
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val s = 1f + loudness * 0.12f
                    scaleX = s
                    scaleY = s
                }
                .pressScale(interaction, pressedScale = 0.92f)
                .glass(CircleShape, fill = Color.Transparent, border = GlassBrushes.SpecularBorderStrong)
                .background(
                    Brush.radialGradient(
                        0f to Color(0xFFFF3B45),
                        1f to Color(0xFFB0101A)
                    ),
                    CircleShape
                )
                .background(GlassBrushes.Sheen, CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (listening) "Arrêter l'écoute" else "Écouter"
                ) {
                    haptics.confirm()
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (listening) Symbols.StopFilled else Symbols.MicFilled,
                contentDescription = if (listening) "Arrêter l'écoute" else "Écouter",
                tint = Color.White,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

/**
 * Dock flottant en verre, centré en bas, au-dessus de la navigation gestuelle. Il n'est
 * jamais posé sur le contenu : les listes réservent sa hauteur (voir [GlassScaffold]).
 */
@Composable
fun GlassDock(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = NicoSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NicoSpacing.md),
            content = content
        )
    }
}

/**
 * Onde vocale : barres dont la hauteur suit le niveau sonore, animées en continu pour ne
 * jamais paraître figées entre deux mesures.
 */
@Composable
fun VoiceWave(
    level: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    bars: Int = 5,
    active: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "onde")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase de l'onde"
    )
    val amplitude by animateFloatAsState(
        targetValue = if (active) 0.25f + (level.coerceIn(0f, 10f) / 10f) * 0.75f else 0.15f,
        animationSpec = NicoMotion.snappy(),
        label = "amplitude"
    )
    Box(
        modifier = modifier.drawBehind {
            val gap = size.width / (bars * 2f - 1f)
            for (i in 0 until bars) {
                val wobble = abs(sin((phase * 2f * PI + i * 0.9f).toFloat()))
                val center = 1f - abs(i - (bars - 1) / 2f) / bars
                val h = size.height * (0.2f + 0.8f * amplitude * (0.45f + 0.55f * wobble) * center)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * gap * 2f, (size.height - h) / 2f),
                    size = Size(gap, h),
                    cornerRadius = CornerRadius(gap / 2f, gap / 2f)
                )
            }
        }
    )
}

@Preview(widthDp = 360, heightDp = 260)
@Composable
private fun MicPreview() {
    GlassPreview {
        GlassDock {
            GlassIconButton(Symbols.History, "Journal", onClick = {}, style = GlassIconButtonStyle.Glass, size = 52.dp)
            MicOrb(onClick = {})
            GlassIconButton(Symbols.Add, "Nouvelle automatisation", onClick = {}, style = GlassIconButtonStyle.Glass, size = 52.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NicoSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            MicOrb(onClick = {}, listening = true, level = 7f, size = 56.dp)
            VoiceWave(level = 6f, modifier = Modifier.size(width = 40.dp, height = 24.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(1.dp, GlassBrushes.SpecularBorder, CircleShape)
            )
        }
    }
}
