package com.nico.assistant.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nico.assistant.R

/**
 * Inter pour le contenu, Inter Display pour les grands titres, Space Mono uniquement pour
 * les libellés de section et les compteurs. Polices embarquées (OFL, voir docs/licenses) :
 * aucune police téléchargeable, donc aucun accès réseau.
 */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold)
)

val InterDisplayFamily = FontFamily(
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold)
)

val MonoFamily = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold)
)

private val NicoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = InterDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    /** Compteurs : « 12× », scores, heures du journal. */
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    ),
    /** Libellés de section : « QUAND JE DIS ». */
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp
    )
)

private val NicoColorScheme = darkColorScheme(
    primary = NicoColors.NothingRed,
    onPrimary = Color.White,
    primaryContainer = NicoColors.GlassFillRaised,
    onPrimaryContainer = NicoColors.TextPrimary,
    secondary = NicoColors.TextPrimary,
    onSecondary = NicoColors.Void,
    background = NicoColors.Void,
    onBackground = NicoColors.TextPrimary,
    surface = NicoColors.SurfaceSolid,
    onSurface = NicoColors.TextPrimary,
    surfaceVariant = NicoColors.SurfaceSolid,
    onSurfaceVariant = NicoColors.TextSecondary,
    surfaceContainer = NicoColors.SurfaceSolid,
    surfaceContainerHigh = NicoColors.SurfaceSolid,
    surfaceContainerHighest = NicoColors.SurfaceSolid,
    inverseSurface = NicoColors.TextPrimary,
    inverseOnSurface = NicoColors.Void,
    outline = NicoColors.Hairline,
    outlineVariant = NicoColors.Hairline,
    error = NicoColors.RedBright,
    onError = Color.White,
    scrim = Color.Black
)

private val NicoShapes = Shapes(
    extraSmall = RoundedCornerShape(NicoRadius.Chip),
    small = RoundedCornerShape(NicoRadius.Chip),
    medium = RoundedCornerShape(NicoRadius.Field),
    large = RoundedCornerShape(NicoRadius.Card),
    extraLarge = RoundedCornerShape(NicoRadius.Sheet)
)

/**
 * Phase (0 → 1, en boucle) du fond animé, partagée par tous les écrans : changer d'écran ne
 * fait pas sauter les blobs d'une position à l'autre.
 */
val LocalBackdropPhase = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Thème de l'app. Toujours sombre : c'est la direction visuelle, et le verre n'a de sens que
 * sur un fond profond.
 *
 * @param darkTheme ignoré, conservé pour ne pas casser les appels existants.
 */
@Composable
fun NicoAssistantTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val phase = rememberInfiniteTransition(label = "fond").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = NicoMotion.BackdropCycleMs, easing = LinearEasing)
        ),
        label = "phase du fond"
    )

    MaterialTheme(
        colorScheme = NicoColorScheme,
        shapes = NicoShapes,
        typography = NicoTypography
    ) {
        CompositionLocalProvider(
            LocalContentColor provides NicoColors.TextPrimary,
            LocalBackdropPhase provides phase,
            content = content
        )
    }
}
