package com.nico.assistant.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Fond vivant de l'app : noir profond et trois halos colorés qui dérivent lentement.
 *
 * C'est lui que le verre « réfracte ». Les halos sont des dégradés radiaux, déjà flous par
 * construction : pas besoin d'un RenderEffect coûteux pour obtenir la douceur voulue.
 *
 * La phase est lue dans la phase de dessin uniquement : l'animation ne recompose rien.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val phase = LocalBackdropPhase.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(NicoColors.Void)
                val t = phase.value * 2f * PI.toFloat()
                for (blob in Blobs) drawBlob(blob, t)
                // Voile en haut et en bas : la barre d'état et la barre de gestes restent lisibles.
                drawRect(
                    Brush.verticalGradient(
                        0f to NicoColors.Void.copy(alpha = 0.55f),
                        0.18f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1f to NicoColors.Void.copy(alpha = 0.6f)
                    )
                )
            }
    )
}

/**
 * Un halo : position de repos (fractions de l'écran), amplitude de dérive, et fréquences
 * entières pour que la boucle se referme sans saut.
 */
private class Blob(
    val color: Color,
    val alpha: Float,
    val x: Float,
    val y: Float,
    val dx: Float,
    val dy: Float,
    val fx: Int,
    val fy: Int,
    val offset: Float,
    /** Rayon en fraction du plus grand côté. */
    val radius: Float
)

private val Blobs = listOf(
    Blob(NicoColors.BlobRed, 0.42f, x = 0.18f, y = 0.16f, dx = 0.16f, dy = 0.08f, fx = 1, fy = 2, offset = 0f, radius = 0.55f),
    Blob(NicoColors.BlobViolet, 0.38f, x = 0.85f, y = 0.52f, dx = 0.12f, dy = 0.14f, fx = 2, fy = 1, offset = 1.7f, radius = 0.60f),
    Blob(NicoColors.BlobBlue, 0.30f, x = 0.30f, y = 0.92f, dx = 0.18f, dy = 0.06f, fx = 1, fy = 1, offset = 3.4f, radius = 0.50f)
)

private fun DrawScope.drawBlob(blob: Blob, t: Float) {
    val w = size.width
    val h = size.height
    val center = Offset(
        x = w * (blob.x + blob.dx * sin(t * blob.fx + blob.offset)),
        y = h * (blob.y + blob.dy * cos(t * blob.fy + blob.offset))
    )
    val radius = max(w, h) * blob.radius
    drawCircle(
        brush = Brush.radialGradient(
            0f to blob.color.copy(alpha = blob.alpha),
            0.45f to blob.color.copy(alpha = blob.alpha * 0.35f),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun GlassBackdropPreview() {
    NicoAssistantTheme { GlassBackdrop() }
}
