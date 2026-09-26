package com.nico.assistant.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Jetons du design system « liquid glass ».
 *
 * Tout ce qui a une couleur, un rayon, un espacement ou un ressort dans l'app vient d'ici :
 * un écran ne déclare jamais de valeur visuelle en dur.
 */
object NicoColors {
    /** Fond de l'app : noir profond, très légèrement bleuté pour que le verre ait du relief. */
    val Void = Color(0xFF040406)

    /** Rouge Nothing, en aplat (boutons principaux, état actif). */
    val NothingRed = Color(0xFFD71921)

    /** Rouge éclairci, lisible en texte sur fond sombre (contraste > 4,5:1). */
    val RedBright = Color(0xFFFF4D57)

    /** Couleurs des « blobs » du fond animé. */
    val BlobRed = Color(0xFFD71921)
    val BlobViolet = Color(0xFF6D28D9)
    val BlobBlue = Color(0xFF1D4ED8)

    val TextPrimary = Color(0xFFF5F5F7)
    /** 70 % : descriptions, sous-titres. Contraste ≈ 9:1 sur le fond, ≈ 6:1 sur un blob. */
    val TextSecondary = Color(0xB3FFFFFF)
    /** 50 % : indices, compteurs discrets. Reste au-dessus de 4,5:1 sur le fond. */
    val TextTertiary = Color(0x80FFFFFF)
    val TextDisabled = Color(0x4DFFFFFF)

    /** Remplissages du verre : blanc translucide, jamais de gris opaque. */
    val GlassFill = Color(0x14FFFFFF)
    val GlassFillRaised = Color(0x1FFFFFFF)
    val GlassFillSubtle = Color(0x0AFFFFFF)
    val GlassFillPressed = Color(0x0FFFFFFF)

    /** Teinte posée sur le flou des barres flottantes. */
    val BarTint = Color(0x990A0A0F)

    /** Fond des feuilles et dialogues : plus dense, le flou vient de la fenêtre. */
    val SheetTint = Color(0xE00E0E14)

    val Hairline = Color(0x1AFFFFFF)
    val Scrim = Color(0x73000000)

    val Success = Color(0xFF3DD68C)
    val Warning = Color(0xFFFFB547)

    /** Accent des conditions « Seulement si ». */
    val Condition = Color(0xFF38D5E0)

    /** Surface opaque de repli (menus système, anciens Android sans flou). */
    val SurfaceSolid = Color(0xFF15151B)
}

/** Dégradés du verre : reflet spéculaire et brillance du haut de la surface. */
object GlassBrushes {
    /** Bordure 1 dp : reflet blanc 28 % en haut à gauche qui s'éteint vers 5 %. */
    val SpecularBorder: Brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.28f),
        0.45f to Color.White.copy(alpha = 0.07f),
        1f to Color.White.copy(alpha = 0.05f)
    )

    /** Bordure accentuée (focus, sélection). */
    val SpecularBorderStrong: Brush = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.55f),
        0.5f to Color.White.copy(alpha = 0.16f),
        1f to Color.White.copy(alpha = 0.10f)
    )

    /** Lumière douce qui tombe sur le haut de la surface. */
    val Sheen: Brush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.07f),
        0.55f to Color.Transparent
    )

    /** Remplissage du bouton principal. */
    val Primary: Brush = Brush.verticalGradient(
        0f to Color(0xFFF0343C),
        1f to Color(0xFFC0121A)
    )
}

object NicoRadius {
    val Chip = 14.dp
    val Field = 18.dp
    val Tile = 22.dp
    val Card = 26.dp
    val Sheet = 32.dp
}

object NicoSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Marge latérale des écrans. */
    val gutter = 20.dp

    /** Plus petite zone tactile acceptée (accessibilité). */
    val touchTarget = 48.dp
}

/** Ressorts partagés : toutes les animations de l'app en sont dérivées. */
object NicoMotion {
    /** Apparitions, changements de taille. */
    fun <T> gentle(): SpringSpec<T> =
        spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

    /** Retour tactile : appui, bascule. */
    fun <T> snappy(): SpringSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)

    /** Éléments qui méritent un léger rebond (micro, pastille d'écoute). */
    fun <T> bouncy(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Échelle d'une surface enfoncée. */
    const val PressedScale = 0.97f

    /** Durée d'un cycle complet du fond animé : assez lent pour ne jamais distraire. */
    const val BackdropCycleMs = 48_000
}
