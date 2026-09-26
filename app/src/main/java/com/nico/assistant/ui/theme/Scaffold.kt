package com.nico.assistant.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * État Haze de l'écran courant : tout ce qui est posé sur le contenu (barres, dock,
 * pastille d'écoute) le floute à travers lui.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Style du verre dépoli des surfaces flottantes. */
object GlassHaze {
    val Bar = HazeStyle(
        backgroundColor = NicoColors.Void,
        tints = listOf(HazeTint(NicoColors.BarTint)),
        blurRadius = 28.dp,
        noiseFactor = 0.06f
    )

    val Floating = HazeStyle(
        backgroundColor = NicoColors.Void,
        tints = listOf(HazeTint(Color(0x730E0E14))),
        blurRadius = 24.dp,
        noiseFactor = 0.08f
    )
}

/**
 * Verre dépoli réel : floute ce qui défile derrière. Sans [LocalHazeState] (aperçus), se
 * rabat sur un remplissage translucide.
 */
@Composable
fun Modifier.frostedGlass(
    shape: Shape,
    style: HazeStyle = GlassHaze.Floating,
    border: Boolean = true
): Modifier {
    val haze = LocalHazeState.current
    val base = if (haze != null) {
        this.glass(shape, fill = Color.Transparent, border = null, sheen = false)
            .hazeEffect(haze, style)
    } else {
        this.glass(shape, fill = NicoColors.SheetTint, border = null, sheen = false)
    }
    return base.glass(
        shape,
        fill = Color.Transparent,
        border = if (border) GlassBrushes.SpecularBorder else null
    )
}

/**
 * Charpente de chaque écran : fond animé, contenu qui défile sous des barres en verre
 * flottantes, et calques (feuilles, pastille d'écoute) au-dessus.
 *
 * [content] reçoit les marges à laisser libres : hauteur réelle des barres, barres système
 * comprises. Rien n'est jamais caché sous une barre ou sous la navigation gestuelle.
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }

    val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topPadding = max(with(density) { topHeight.toDp() }, systemTop)
    val bottomPadding = max(with(density) { bottomHeight.toDp() }, systemBottom)

    Box(modifier = modifier.fillMaxSize().background(NicoColors.Void)) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            GlassBackdrop()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            ) {
                content(PaddingValues(top = topPadding, bottom = bottomPadding))
            }
        }

        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topHeight = it.height }
            ) { topBar() }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomHeight = it.height }
            ) { bottomBar() }

            if (snackbarHostState != null) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPadding + NicoSpacing.sm)
                ) { data -> GlassSnackbar(data) }
            }

            overlay()
        }
    }
}

/**
 * Barre du haut en verre flottant.
 *
 * @param scrolled le verre n'apparaît que quand du contenu passe dessous : au repos, le
 * titre de l'écran respire sur le fond.
 * @param showTitle `false` quand l'écran affiche déjà un grand titre dans son contenu : le
 * titre compact n'apparaît alors qu'une fois ce dernier sorti de l'écran.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    scrolled: Boolean = true,
    showTitle: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val glassAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = NicoMotion.gentle(),
        label = "verre de la barre"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = NicoMotion.gentle(),
        label = "titre compact"
    )
    val haze = LocalHazeState.current

    Box(modifier = modifier.fillMaxWidth()) {
        // Calque du verre, qui s'estompe au repos. Séparé du contenu pour ne pas le rendre transparent.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = glassAlpha }
                .then(if (haze != null) Modifier.hazeEffect(haze, GlassHaze.Bar) else Modifier.background(NicoColors.SheetTint))
                .drawBehind {
                    drawLine(
                        color = NicoColors.Hairline,
                        start = Offset(0f, size.height - 0.5f),
                        end = Offset(size.width, size.height - 0.5f),
                        strokeWidth = 1f
                    )
                }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .height(64.dp)
                .padding(horizontal = NicoSpacing.xxs)
        ) {
            if (navigationIcon != null) {
                navigationIcon()
            } else {
                Box(modifier = Modifier.padding(start = NicoSpacing.md))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = NicoSpacing.xs)
                    .graphicsLayer { alpha = titleAlpha }
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Bouton retour standard des barres. */
@Composable
fun BackButton(onClick: () -> Unit) {
    GlassIconButton(icon = Symbols.ArrowBack, contentDescription = "Retour", onClick = onClick)
}

/** Grand titre d'écran, aéré, dans le contenu (façon « large title »). */
@Composable
fun LargeTitle(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier.padding(horizontal = NicoSpacing.gutter).padding(top = NicoSpacing.xs, bottom = NicoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NicoSpacing.xxs)
    ) {
        Text(text, style = MaterialTheme.typography.displaySmall)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = NicoColors.TextTertiary)
        }
    }
}

/**
 * Barre du bas en verre, collée au bord et au-dessus de la navigation gestuelle. Pour le
 * bouton principal d'un écran (« Tester maintenant »).
 */
@Composable
fun GlassBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val haze = LocalHazeState.current
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(if (haze != null) Modifier.hazeEffect(haze, GlassHaze.Bar) else Modifier.background(NicoColors.SheetTint))
                .drawBehind {
                    drawLine(
                        color = NicoColors.Hairline,
                        start = Offset(0f, 0.5f),
                        end = Offset(size.width, 0.5f),
                        strokeWidth = 1f
                    )
                }
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NicoSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = NicoSpacing.gutter, vertical = NicoSpacing.sm),
            content = content
        )
    }
}

/** Snackbar en verre, avec son action (« Annuler ») en rouge lisible. */
@Composable
fun GlassSnackbar(data: SnackbarData) {
    val shape = RoundedCornerShape(NicoRadius.Tile)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = NicoSpacing.gutter)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .frostedGlass(shape)
            .padding(start = NicoSpacing.lg, end = NicoSpacing.xs)
    ) {
        Text(
            text = data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(vertical = NicoSpacing.sm)
        )
        val action = data.visuals.actionLabel
        if (action != null) {
            GlassButton(
                text = action,
                onClick = { data.performAction() },
                style = GlassButtonStyle.Ghost,
                height = 44.dp
            )
        }
    }
}

/** Hauteur de la zone réservée en bas d'une liste, pour qu'un dock flottant ne cache rien. */
fun dockClearance(dockHeight: Dp): Dp = dockHeight + NicoSpacing.xl

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun GlassScaffoldPreview() {
    NicoAssistantTheme {
        GlassScaffold(
            topBar = {
                GlassTopBar(
                    title = "Automatisations",
                    actions = { GlassIconButton(Symbols.Settings, "Réglages", onClick = {}) }
                )
            },
            bottomBar = {
                GlassBottomBar {
                    GlassButton("Tester maintenant", onClick = {}, icon = Symbols.PlayArrowFilled, modifier = Modifier.weight(1f))
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).padding(NicoSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(NicoSpacing.md)
            ) {
                LargeTitle("Automatisations", subtitle = "8 ACTIVES")
                repeat(3) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Carte $it", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
