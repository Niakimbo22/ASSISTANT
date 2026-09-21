package com.nico.assistant.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Direction visuelle Nothing (spec §7.6) : noir pur, rouge Nothing avec parcimonie,
 * coins peu arrondis, monospace pour tout ce qui est technique.
 *
 * Pas de couleurs dynamiques : l'app doit avoir la même tête sur n'importe quel fond d'écran.
 */
private val Black = Color(0xFF000000)
private val Surface = Color(0xFF0A0A0A)
private val SurfaceVariant = Color(0xFF141414)
private val Border = Color(0xFF1F1F1F)
private val NothingRed = Color(0xFFD71921)
private val OnDark = Color(0xFFF5F5F5)
private val Muted = Color(0xFF9A9A9A)

private val NothingColors = darkColorScheme(
    primary = NothingRed,
    onPrimary = OnDark,
    primaryContainer = SurfaceVariant,
    onPrimaryContainer = OnDark,
    secondary = OnDark,
    onSecondary = Black,
    background = Black,
    onBackground = OnDark,
    surface = Surface,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Muted,
    outline = Border,
    outlineVariant = Border,
    error = NothingRed,
    onError = OnDark
)

/** Coins peu arrondis, jamais de pilule. */
private val NothingShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

/** Monospace pour les libellés techniques et les compteurs, sans-serif pour le reste. */
private val NothingTypography = Typography().let { base ->
    base.copy(
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        ),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace)
    )
}

/**
 * @param darkTheme ignoré : l'app est toujours sombre, c'est la direction visuelle.
 * Le paramètre est conservé pour ne pas casser les appels existants.
 */
@Composable
fun NicoAssistantTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NothingColors,
        shapes = NothingShapes,
        typography = NothingTypography,
        content = content
    )
}
