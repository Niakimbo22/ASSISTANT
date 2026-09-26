package com.nico.assistant.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nico.assistant.data.model.Automation
import com.nico.assistant.ui.theme.ActionIconBadge
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassCard
import com.nico.assistant.ui.theme.GlassDock
import com.nico.assistant.ui.theme.GlassIconButton
import com.nico.assistant.ui.theme.GlassIconButtonStyle
import com.nico.assistant.ui.theme.GlassScaffold
import com.nico.assistant.ui.theme.GlassSwitch
import com.nico.assistant.ui.theme.GlassTextField
import com.nico.assistant.ui.theme.GlassTopBar
import com.nico.assistant.ui.theme.LargeTitle
import com.nico.assistant.ui.theme.MicOrb
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoMotion
import com.nico.assistant.ui.theme.NicoRadius
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.SectionLabel
import com.nico.assistant.ui.theme.Symbols
import com.nico.assistant.ui.theme.rememberHaptics
import com.nico.assistant.ui.theme.automationTitleKey
import com.nico.assistant.ui.theme.sharedElementOrSelf
import kotlinx.coroutines.delay

/**
 * Écran d'accueil : la liste des automatisations (spec §7.2).
 *
 * Chaque carte se lit comme une phrase — « Quand je dis “bonne nuit” → 🔕 🔉 ☀️ » — et le dock
 * en verre porte le micro, la création et le journal. C'est le catalogue que Nico construit à
 * l'usage ; aucune commande n'est codée en dur.
 */
@Composable
fun AutomationListScreen(
    viewModel: AutomationListViewModel,
    listening: Boolean,
    voiceLevel: Float,
    onCreate: () -> Unit,
    onEdit: (Automation) -> Unit,
    onMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val automations by viewModel.automations.collectAsState()
    val query by viewModel.query.collectAsState()
    val deleted by viewModel.recentlyDeleted.collectAsState()
    val totals by viewModel.totals.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 }
    }
    // Le titre compact n'apparaît dans la barre qu'une fois le grand titre sorti de l'écran.
    val titleCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > with(density) { 44.dp.toPx() }
        }
    }

    LaunchedEffect(deleted) {
        val automation = deleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "« ${automation.name} » supprimée",
            actionLabel = "Annuler"
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
    }

    // Les cartes présentes à l'ouverture entrent en cascade ; celles qui arrivent au défilement, non.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(700)
        entered = true
    }

    GlassScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            GlassTopBar(
                title = "Automatisations",
                scrolled = scrolled,
                showTitle = titleCollapsed,
                actions = {
                    GlassIconButton(
                        icon = Symbols.Settings,
                        contentDescription = "Réglages",
                        onClick = onOpenSettings
                    )
                }
            )
        },
        bottomBar = {
            GlassDock {
                GlassIconButton(
                    icon = Symbols.History,
                    contentDescription = "Journal d'exécution",
                    onClick = onOpenLogs,
                    style = GlassIconButtonStyle.Glass,
                    size = 52.dp
                )
                MicOrb(onClick = onMic, listening = listening, level = voiceLevel)
                GlassIconButton(
                    icon = Symbols.Add,
                    contentDescription = "Nouvelle automatisation",
                    onClick = onCreate,
                    style = GlassIconButtonStyle.Glass,
                    size = 52.dp,
                    iconSize = 26.dp
                )
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + NicoSpacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
        ) {
            item(key = "title") {
                LargeTitle(
                    text = "Automatisations",
                    subtitle = subtitleOf(totals.first, totals.second)
                )
            }

            item(key = "search") {
                SearchField(
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    modifier = Modifier.padding(horizontal = NicoSpacing.gutter).padding(bottom = NicoSpacing.xs)
                )
            }

            if (automations.isEmpty()) {
                item(key = "empty") {
                    if (query.isNotBlank()) {
                        NoResult(query)
                    } else {
                        EmptyState(
                            onCreate = onCreate,
                            onTemplate = viewModel::createFromTemplate
                        )
                    }
                }
            } else {
                items(automations, key = { it.id }) { automation ->
                    val index = automations.indexOf(automation)
                    Box(
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = NicoSpacing.gutter)
                            .entrance(index, animate = !entered)
                    ) {
                        SwipeableAutomationCard(
                            automation = automation,
                            onClick = { onEdit(automation) },
                            onToggle = { enabled -> viewModel.setEnabled(automation, enabled) },
                            onDelete = { viewModel.delete(automation) }
                        )
                    }
                }
            }
        }
    }
}

private fun subtitleOf(total: Int, active: Int): String = when (total) {
    0 -> "AUCUNE AUTOMATISATION"
    1 -> "1 AUTOMATISATION · ${if (active == 1) "ACTIVE" else "INACTIVE"}"
    else -> "$total AUTOMATISATIONS · $active ACTIVES"
}

/** Glissement et fondu d'entrée, décalés selon la position : les cartes arrivent en cascade. */
@Composable
private fun Modifier.entrance(index: Int, animate: Boolean): Modifier {
    val progress = remember { Animatable(if (animate && index < 10) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(40L * index)
            progress.animateTo(1f, NicoMotion.gentle())
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    GlassTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "Rechercher un nom, une phrase",
        leadingIcon = Symbols.Search,
        modifier = modifier,
        trailing = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                GlassIconButton(
                    icon = Symbols.Close,
                    contentDescription = "Effacer la recherche",
                    onClick = { onQueryChange("") },
                    tint = NicoColors.TextSecondary,
                    iconSize = 18.dp
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAutomationCard(
    automation: Automation,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val haptics = rememberHaptics()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.reject()
                onDelete()
                true
            } else {
                false
            }
        }
    )

    // Un petit « clic » quand le seuil de suppression est franchi, dans un sens ou dans l'autre.
    val armed = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    LaunchedEffect(armed) { if (armed) haptics.gestureStart() }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Les cartes sont translucides : le fond rouge ne doit exister que pendant le geste,
            // sinon il transparaîtrait sous chaque carte au repos.
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                DeleteBackground(armed = armed)
            }
        }
    ) {
        AutomationCard(automation, onClick, onToggle)
    }
}

/** Ce qui apparaît sous la carte quand on la fait glisser : une pastille rouge qui s'arme. */
@Composable
private fun DeleteBackground(armed: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.15f else 0.85f,
        animationSpec = NicoMotion.bouncy(),
        label = "corbeille"
    )
    val shape = RoundedCornerShape(NicoRadius.Card)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    0f to NicoColors.NothingRed.copy(alpha = 0f),
                    1f to NicoColors.NothingRed.copy(alpha = if (armed) 0.55f else 0.25f)
                ),
                shape
            )
            .padding(horizontal = NicoSpacing.xl),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            Symbols.DeleteFilled,
            contentDescription = null,
            tint = NicoColors.TextPrimary,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}

/** La carte-phrase : nom, « Quand je dis … », puis la rangée d'icônes des actions. */
@Composable
private fun AutomationCard(
    automation: Automation,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (automation.enabled) 1f else 0.5f,
        animationSpec = NicoMotion.gentle(),
        label = "carte inactive"
    )

    GlassCard(
        onClick = onClick,
        onClickLabel = "Modifier",
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = NicoSpacing.lg, end = NicoSpacing.xs, top = NicoSpacing.sm, bottom = NicoSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = automation.name.ifBlank { "Sans nom" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .sharedElementOrSelf(automationTitleKey(automation.id))
                        .alpha(contentAlpha)
                )
            }
            GlassSwitch(
                checked = automation.enabled,
                onCheckedChange = onToggle,
                contentDescription = if (automation.enabled) "Désactiver" else "Activer"
            )
        }

        Column(modifier = Modifier.padding(end = NicoSpacing.sm).alpha(contentAlpha)) {
            PhraseSentence(automation)
            Spacer(modifier = Modifier.height(NicoSpacing.sm))
            ActionStrip(automation)
        }
    }
}

@Composable
private fun PhraseSentence(automation: Automation) {
    val phrase = automation.phrases.firstOrNull()
    val others = (automation.phrases.size - 1).coerceAtLeast(0)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = NicoColors.TextSecondary)) { append("Quand je dis ") }
        if (phrase != null) {
            withStyle(SpanStyle(color = NicoColors.TextPrimary, fontWeight = FontWeight.Medium)) {
                append("« $phrase »")
            }
        } else {
            withStyle(SpanStyle(color = NicoColors.TextTertiary)) { append("… (aucune phrase)") }
        }
        if (others > 0) {
            withStyle(SpanStyle(color = NicoColors.TextTertiary)) {
                append(if (others == 1) "  ou 1 autre" else "  ou $others autres")
            }
        }
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** → puis une icône par action ; au-delà de quatre, un compteur rond de même taille. */
@Composable
private fun ActionStrip(automation: Automation) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            Symbols.ArrowForward,
            contentDescription = null,
            tint = NicoColors.TextTertiary,
            modifier = Modifier.size(18.dp)
        )
        if (automation.actions.isEmpty()) {
            Text("aucune action", style = MaterialTheme.typography.bodySmall, color = NicoColors.TextTertiary)
        }
        for (spec in automation.actions.take(MAX_ICONS)) {
            ActionIconBadge(type = spec.type, size = 30.dp)
        }
        val hidden = automation.actions.size - MAX_ICONS
        if (hidden > 0) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(NicoColors.GlassFillRaised, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+$hidden", style = MaterialTheme.typography.labelMedium, color = NicoColors.TextSecondary, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (automation.conditions.isNotEmpty()) {
            Icon(
                Symbols.Rule,
                contentDescription = "Avec conditions",
                tint = NicoColors.Condition,
                modifier = Modifier.size(18.dp)
            )
        }
        if (automation.runCount > 0) {
            Text(
                text = "${automation.runCount}×",
                style = MaterialTheme.typography.labelMedium,
                color = NicoColors.TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NoResult(query: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(NicoSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs)
    ) {
        Icon(Symbols.Search, contentDescription = null, tint = NicoColors.TextTertiary, modifier = Modifier.size(32.dp))
        Text("Aucun résultat", style = MaterialTheme.typography.titleMedium)
        Text(
            "Rien ne correspond à « $query ».",
            style = MaterialTheme.typography.bodyMedium,
            color = NicoColors.TextSecondary
        )
    }
}

/**
 * Écran vide : une invitation claire et trois modèles à créer d'un seul tap, chacun lisible
 * comme une phrase avec ses icônes.
 */
@Composable
private fun EmptyState(onCreate: () -> Unit, onTemplate: (Automation) -> Unit) {
    val haptics = rememberHaptics()
    val templates = remember { AutomationTemplates.all() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = NicoSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(NicoSpacing.xl)) {
            Icon(Symbols.AutoAwesomeFilled, contentDescription = null, tint = NicoColors.RedBright, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(NicoSpacing.sm))
            Text("Apprends-lui ta première phrase", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(NicoSpacing.xs))
            Text(
                "Une phrase, une ou plusieurs actions : c'est prêt. Pars d'un modèle ou crée la tienne.",
                style = MaterialTheme.typography.bodyMedium,
                color = NicoColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(NicoSpacing.lg))
            GlassButton(
                text = "Créer une automatisation",
                onClick = onCreate,
                icon = Symbols.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionLabel("Modèles en un tap", icon = Symbols.BoltFilled, modifier = Modifier.padding(top = NicoSpacing.sm, start = NicoSpacing.xxs))

        for (template in templates) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    haptics.confirm()
                    onTemplate(template)
                },
                onClickLabel = "Créer ce modèle",
                contentPadding = PaddingValues(NicoSpacing.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        PhraseSentence(template)
                        Spacer(modifier = Modifier.height(NicoSpacing.sm))
                        ActionStrip(template)
                    }
                    Icon(
                        Symbols.Add,
                        contentDescription = null,
                        tint = NicoColors.TextSecondary,
                        modifier = Modifier.padding(start = NicoSpacing.sm).size(24.dp)
                    )
                }
            }
        }
    }
}

private const val MAX_ICONS = 4
