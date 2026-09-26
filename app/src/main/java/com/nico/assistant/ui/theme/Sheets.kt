package com.nico.assistant.ui.theme

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay

private val SheetShape = RoundedCornerShape(topStart = NicoRadius.Sheet, topEnd = NicoRadius.Sheet)

/**
 * Flou de fenêtre (Android 12+) : quand une feuille ou un dialogue s'ouvre, tout l'écran
 * derrière se floute progressivement. C'est le compositeur système qui le calcule — un vrai
 * verre dépoli, gratuit pour l'app. Sans effet (et sans erreur) sur les versions antérieures
 * ou si l'économiseur de batterie coupe les flous.
 */
@Composable
fun WindowBlurBehind(radius: Dp = 28.dp) {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    LaunchedEffect(view, radiusPx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@LaunchedEffect
        val window: Window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        // Montée progressive : un flou qui apparaît d'un coup fait « saut d'image ».
        val steps = 8
        for (step in 1..steps) {
            val params = window.attributes
            params.blurBehindRadius = radiusPx * step / steps
            window.attributes = params
            delay(18)
        }
    }
}

/** Poignée de la feuille, avec le reflet spéculaire qui court sur son bord supérieur. */
@Composable
private fun GlassDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.35f),
                        1f to Color.Transparent
                    ),
                    start = Offset(0f, 0.5f),
                    end = Offset(size.width, 0.5f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 5.dp)
                .background(Color.White.copy(alpha = 0.35f), CircleShape)
        )
    }
}

/**
 * Feuille modale en verre. Fond flouté par la fenêtre, coins 32 dp, poignée, et
 * contenu qui respecte la barre de navigation et le clavier.
 *
 * @param skipPartiallyExpanded `false` : la feuille s'ouvre à mi-hauteur et peut être tirée en
 * plein écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = NicoColors.SheetTint,
        contentColor = NicoColors.TextPrimary,
        tonalElevation = 0.dp,
        scrimColor = NicoColors.Scrim,
        dragHandle = { GlassDragHandle() }
    ) {
        WindowBlurBehind()
        Column(
            modifier = Modifier.fillMaxWidth().background(GlassBrushes.Sheen),
            content = content
        )
    }
}

/** En-tête de feuille : titre, sous-titre optionnel, action à droite. */
@Composable
fun GlassSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = NicoSpacing.gutter, end = NicoSpacing.sm, top = NicoSpacing.xs, bottom = NicoSpacing.sm)
    ) {
        if (leading != null) {
            leading()
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = if (leading != null) NicoSpacing.sm else 0.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = NicoColors.TextSecondary)
            }
        }
        if (trailing != null) trailing()
    }
}

/** Liste d'options dans une feuille : remplace les menus déroulants de l'ancienne UI. */
@Composable
fun GlassOptionSheet(
    title: String?,
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    GlassSheet(onDismissRequest = onDismiss) {
        if (title != null) GlassSheetHeader(title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NicoSpacing.md)
                .navigationBarsPadding()
                .padding(bottom = NicoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.xxs)
        ) {
            if (options.isEmpty()) {
                Text(
                    "Rien à choisir pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NicoColors.TextSecondary,
                    modifier = Modifier.padding(NicoSpacing.md)
                )
            }
            for ((value, label) in options) {
                OptionRow(label = label, selected = value == selected, onClick = { onPick(value) })
            }
        }
    }
}

@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(NicoRadius.Field)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.glass(shape, fill = NicoColors.GlassFillRaised)
                else Modifier
            )
            .clickable(role = Role.RadioButton) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = NicoSpacing.md, vertical = NicoSpacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = NicoColors.TextSecondary)
            }
        }
        if (selected) {
            Icon(Symbols.Check, contentDescription = "Sélectionné", tint = NicoColors.RedBright, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Dialogue en verre, centré, fond flouté. Pour les messages courts : rapport de test,
 * confirmation. Tout ce qui demande de la place passe par [GlassSheet].
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    buttons: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        WindowBlurBehind()
        Column(
            modifier = modifier
                .padding(NicoSpacing.xl)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(NicoRadius.Sheet), fill = NicoColors.SheetTint)
                .padding(NicoSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
        ) {
            if (icon != null) icon()
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs),
                content = content
            )
            if (buttons != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = NicoSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs),
                    content = buttons
                )
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 480)
@Composable
private fun GlassSheetContentPreview() {
    // Les feuilles et dialogues vivent dans leur propre fenêtre : on prévisualise leur contenu.
    GlassPreview {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(SheetShape, fill = NicoColors.SheetTint)
        ) {
            GlassDragHandle()
            GlassSheetHeader(title = "Correspondance", subtitle = "Comment comparer ta phrase")
            Column(modifier = Modifier.padding(horizontal = NicoSpacing.md)) {
                OptionRow("Flexible (recommandé)", selected = true, onClick = {})
                OptionRow("Exacte", selected = false, onClick = {})
                OptionRow("Contient la phrase", selected = false, onClick = {}, description = "Utile pour les phrases longues")
            }
        }
        Box(modifier = Modifier.width(1.dp))
    }
}
