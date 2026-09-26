package com.nico.assistant.ui.editor

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.Backend
import com.nico.assistant.core.executor.ExecutionReport
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.ui.actionpicker.ActionPickerSheet
import com.nico.assistant.ui.actionpicker.ParamSummary
import com.nico.assistant.ui.theme.ActionIconBadge
import com.nico.assistant.ui.theme.BackButton
import com.nico.assistant.ui.theme.GlassBottomBar
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassButtonStyle
import com.nico.assistant.ui.theme.GlassCard
import com.nico.assistant.ui.theme.GlassChip
import com.nico.assistant.ui.theme.GlassDialog
import com.nico.assistant.ui.theme.GlassIconButton
import com.nico.assistant.ui.theme.GlassIconButtonStyle
import com.nico.assistant.ui.theme.GlassScaffold
import com.nico.assistant.ui.theme.GlassSelectField
import com.nico.assistant.ui.theme.GlassTextField
import com.nico.assistant.ui.theme.GlassToggleRow
import com.nico.assistant.ui.theme.GlassTopBar
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoMotion
import com.nico.assistant.ui.theme.NicoRadius
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.SectionLabel
import com.nico.assistant.ui.theme.StatusBadge
import com.nico.assistant.ui.theme.Symbols
import com.nico.assistant.ui.theme.VoiceWave
import com.nico.assistant.ui.theme.rememberHaptics
import com.nico.assistant.ui.theme.visual
import kotlinx.coroutines.launch

/**
 * Éditeur d'automatisation (spec §7.3) : l'écran central de la V2.
 *
 * Trois blocs de verre qui se lisent de haut en bas comme une phrase — QUAND JE DIS / SEULEMENT
 * SI / ALORS — puis les réglages. « Tester maintenant » reste collé en bas, au-dessus de la
 * navigation gestuelle.
 */
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    isBackendAvailable: (Backend) -> Boolean = { it == Backend.INTENT || it == Backend.INTERNAL },
    onBack: () -> Unit
) {
    val draft by viewModel.draft.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val report by viewModel.testReport.collectAsState()
    val running by viewModel.running.collectAsState()
    val dictation by viewModel.dictation.collectAsState()
    val haptics = rememberHaptics()
    val scrollState = rememberScrollState()

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    // Les erreurs ne s'affichent qu'après une tentative d'enregistrement : un éditeur vierge
    // n'a pas à crier.
    var showErrors by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = draft.name.ifBlank { if (draft.isNew) "Nouvelle automatisation" else "Automatisation" },
                scrolled = scrollState.value > 8,
                navigationIcon = { BackButton(onBack) },
                actions = {
                    SaveButton(
                        valid = draft.isValid,
                        onSave = {
                            if (draft.isValid) {
                                haptics.confirm()
                                viewModel.save()
                            } else {
                                haptics.reject()
                                showErrors = true
                            }
                        }
                    )
                }
            )
        },
        bottomBar = {
            GlassBottomBar {
                GlassButton(
                    text = "Tester maintenant",
                    onClick = viewModel::testNow,
                    icon = Symbols.PlayArrowFilled,
                    loading = running,
                    enabled = draft.actions.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        },
        overlay = {
            if (pickerOpen) {
                val index = editingIndex
                ActionPickerSheet(
                    initial = index?.let { draft.actions.getOrNull(it) },
                    availableSlots = draft.availableSlots,
                    isBackendAvailable = isBackendAvailable,
                    onConfirm = { spec ->
                        if (index == null) viewModel.addAction(spec) else viewModel.replaceAction(index, spec)
                        pickerOpen = false
                        editingIndex = null
                    },
                    onDismiss = {
                        pickerOpen = false
                        editingIndex = null
                    }
                )
            }
            report?.let { TestReportDialog(it, viewModel::dismissTestReport) }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(
                    top = padding.calculateTopPadding() + NicoSpacing.xs,
                    bottom = padding.calculateBottomPadding() + NicoSpacing.xl
                )
                .padding(horizontal = NicoSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.md)
        ) {
            val nameMissing = showErrors && draft.name.isBlank()
            GlassTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = "Nom",
                placeholder = "Ex. Mode nuit",
                required = true,
                isError = nameMissing,
                supportingText = if (nameMissing) "Donne un nom à cette automatisation" else null
            )

            EditorBlock(
                title = "Quand je dis",
                icon = Symbols.MicFilled,
                accent = NicoColors.RedBright,
                count = draft.phrases.size
            ) {
                PhraseEditor(
                    phrases = draft.phrases,
                    dictation = dictation,
                    showError = showErrors && draft.phrases.none { it.isNotBlank() },
                    onAdd = viewModel::addPhrase,
                    onRemove = viewModel::removePhrase,
                    onDictate = viewModel::toggleDictation
                )
                GlassSelectField(
                    label = "Correspondance",
                    display = MATCH_MODE_LABELS[draft.matchMode] ?: draft.matchMode.name,
                    options = MatchMode.entries.map { it.name to (MATCH_MODE_LABELS[it] ?: it.name) },
                    selected = draft.matchMode.name,
                    onPick = { picked -> viewModel.setMatchMode(MatchMode.valueOf(picked)) },
                    leadingIcon = Symbols.Tune,
                    sheetTitle = "Correspondance"
                )
            }

            EditorBlock(
                title = "Seulement si",
                icon = Symbols.Rule,
                accent = NicoColors.Condition,
                count = draft.conditions.size
            ) {
                ConditionSection(
                    conditions = draft.conditions,
                    onAdd = viewModel::addCondition,
                    onRemove = viewModel::removeCondition
                )
            }

            EditorBlock(
                title = "Alors",
                icon = Symbols.BoltFilled,
                accent = ALORS_ACCENT,
                count = draft.actions.size
            ) {
                ActionTimeline(
                    actions = draft.actions,
                    onEdit = { index ->
                        editingIndex = index
                        pickerOpen = true
                    },
                    onRemove = viewModel::removeAction,
                    onDuplicate = viewModel::duplicateAction,
                    onMove = viewModel::moveAction
                )
                if (showErrors && draft.actions.isEmpty()) {
                    InlineError("Ajoute au moins une action")
                }
                GlassButton(
                    text = "Ajouter une action",
                    onClick = {
                        editingIndex = null
                        pickerOpen = true
                    },
                    icon = Symbols.Add,
                    style = GlassButtonStyle.Secondary,
                    height = 48.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            EditorBlock(title = "Réglages", icon = Symbols.Settings, accent = NicoColors.TextTertiary) {
                GlassTextField(
                    value = draft.automation.feedbackText.orEmpty(),
                    onValueChange = viewModel::setFeedbackText,
                    label = "Réponse vocale",
                    placeholder = "Générée automatiquement",
                    supportingText = "Accepte les {slots}."
                )
                GlassToggleRow(
                    title = "Activée",
                    description = "Répond à la voix",
                    checked = draft.automation.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
                GlassToggleRow(
                    title = "Demander confirmation",
                    description = "Pose la question avant d'agir",
                    checked = draft.automation.confirmBeforeRun,
                    onCheckedChange = viewModel::setConfirmBeforeRun
                )
            }
        }
    }
}

private val ALORS_ACCENT = Color(0xFFA78BFA)

/**
 * ✓ d'enregistrement : rouge plein quand l'automatisation est complète, verre éteint sinon.
 * Toujours cliquable : éteint, il secoue la tête et révèle ce qui manque.
 */
@Composable
private fun SaveButton(valid: Boolean, onSave: () -> Unit) {
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    GlassIconButton(
        icon = Symbols.Check,
        contentDescription = if (valid) "Enregistrer" else "Enregistrer (incomplet)",
        onClick = {
            onSave()
            if (!valid) {
                scope.launch {
                    for (target in listOf(-10f, 8f, -6f, 4f, 0f)) {
                        shake.animateTo(target, NicoMotion.snappy())
                    }
                }
            }
        },
        style = if (valid) GlassIconButtonStyle.Primary else GlassIconButtonStyle.Glass,
        tint = if (valid) Color.White else NicoColors.TextDisabled,
        size = 44.dp,
        modifier = Modifier
            .padding(end = NicoSpacing.xs)
            .graphicsLayer { translationX = shake.value.dp.toPx() }
    )
}

/** Un bloc de l'éditeur : carte de verre, libellé mono coloré, compteur à droite. */
@Composable
private fun EditorBlock(
    title: String,
    icon: ImageVector,
    accent: Color,
    count: Int? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(NicoMotion.gentle()),
        verticalArrangement = Arrangement.spacedBy(NicoSpacing.md)
    ) {
        SectionLabel(
            text = title,
            icon = icon,
            color = accent,
            trailing = if (count != null && count > 0) {
                { Text("$count", style = MaterialTheme.typography.labelMedium, color = NicoColors.TextTertiary) }
            } else {
                null
            }
        )
        content()
    }
}

@Composable
private fun InlineError(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Symbols.ErrorFilled, contentDescription = null, tint = NicoColors.RedBright, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = NicoColors.RedBright)
    }
}

/** Phrases en pastilles, champ d'ajout, dictée au micro. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhraseEditor(
    phrases: List<String>,
    dictation: Dictation?,
    showError: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onDictate: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listening = dictation != null && dictation.error == null
    val submit = {
        if (input.isNotBlank()) {
            onAdd(input)
            input = ""
        }
    }

    if (phrases.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NicoSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs),
            modifier = Modifier.fillMaxWidth()
        ) {
            phrases.forEachIndexed { index, phrase ->
                key(phrase) {
                    GlassChip(
                        text = "« $phrase »",
                        onRemove = { onRemove(index) },
                        removeDescription = "Retirer « $phrase »"
                    )
                }
            }
        }
    }

    GlassTextField(
        value = if (listening) dictation?.partial.orEmpty() else input,
        onValueChange = { if (!listening) input = it },
        placeholder = if (listening) "J'écoute…" else "Ajouter une phrase",
        isError = showError || dictation?.error != null,
        supportingText = when {
            dictation?.error != null -> dictation.error
            showError -> "Ajoute au moins une phrase déclenchante"
            else -> "Un {slot} capture une variable : « ouvre {app} »"
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = listening,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith fadeOut() },
                    label = "dictée"
                ) { active ->
                    if (active) {
                        GlassIconButtonWithWave(level = dictation?.level ?: 0f, onClick = onDictate)
                    } else {
                        GlassIconButton(
                            icon = Symbols.Mic,
                            contentDescription = "Dicter une phrase",
                            onClick = onDictate,
                            tint = NicoColors.TextSecondary
                        )
                    }
                }
                GlassIconButton(
                    icon = Symbols.Add,
                    contentDescription = "Ajouter la phrase",
                    onClick = submit,
                    enabled = input.isNotBlank() && !listening,
                    style = if (input.isNotBlank()) GlassIconButtonStyle.Primary else GlassIconButtonStyle.Plain,
                    tint = if (input.isNotBlank()) Color.White else NicoColors.TextSecondary,
                    size = 40.dp,
                    iconSize = 20.dp,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    )
}

/** Micro en cours de dictée : l'onde remplace l'icône, un tap arrête. */
@Composable
private fun GlassIconButtonWithWave(level: Float, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = "Arrêter la dictée") {
                haptics.tick()
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        VoiceWave(level = level, color = NicoColors.RedBright, modifier = Modifier.size(width = 24.dp, height = 20.dp))
    }
}

/**
 * La chaîne d'actions en frise verticale : une pastille par action, reliées par un fil.
 * Appui long puis glisser pour réordonner ; le menu ⋮ garde « Monter / Descendre » pour qui
 * préfère (ou pour TalkBack).
 */
@Composable
private fun ActionTimeline(
    actions: List<ActionSpec>,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    if (actions.isEmpty()) {
        Text(
            text = "Aucune action pour l'instant. Une automatisation peut en enchaîner autant qu'il faut.",
            style = MaterialTheme.typography.bodyMedium,
            color = NicoColors.TextSecondary
        )
        return
    }

    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val spacingPx = with(density) { TIMELINE_SPACING.toPx() }
    val latestActions by rememberUpdatedState(actions)
    val latestMove by rememberUpdatedState(onMove)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    Column(verticalArrangement = Arrangement.spacedBy(TIMELINE_SPACING)) {
        actions.forEachIndexed { index, spec ->
            key(spec.id) {
                val dragging = draggingId == spec.id
                val lift by animateFloatAsState(
                    targetValue = if (dragging) 1f else 0f,
                    animationSpec = NicoMotion.snappy(),
                    label = "soulèvement"
                )
                TimelineRow(
                    index = index,
                    spec = spec,
                    isFirst = index == 0,
                    isLast = index == actions.lastIndex,
                    dragging = dragging,
                    onEdit = { onEdit(index) },
                    onRemove = { onRemove(index) },
                    onDuplicate = { onDuplicate(index) },
                    onMove = onMove,
                    modifier = Modifier
                        .onSizeChanged { heights[spec.id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            val s = 1f + 0.03f * lift
                            scaleX = s
                            scaleY = s
                        }
                        .pointerInput(spec.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = spec.id
                                    dragOffset = 0f
                                    haptics.gestureStart()
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDrag = drag@{ change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val list = latestActions
                                    val current = list.indexOfFirst { it.id == spec.id }
                                    if (current < 0) return@drag
                                    if (dragOffset > 0 && current < list.lastIndex) {
                                        val next = heights[list[current + 1].id] ?: return@drag
                                        if (dragOffset > next / 2f) {
                                            latestMove(current, current + 1)
                                            dragOffset -= next + spacingPx
                                            haptics.tick()
                                        }
                                    } else if (dragOffset < 0 && current > 0) {
                                        val previous = heights[list[current - 1].id] ?: return@drag
                                        if (-dragOffset > previous / 2f) {
                                            latestMove(current, current - 1)
                                            dragOffset += previous + spacingPx
                                            haptics.tick()
                                        }
                                    }
                                }
                            )
                        }
                )
            }
        }
    }
}

private val TIMELINE_SPACING = 10.dp
private val BADGE_SIZE = 40.dp
private val ROW_PADDING = 10.dp

@Composable
private fun TimelineRow(
    index: Int,
    spec: ActionSpec,
    isFirst: Boolean,
    isLast: Boolean,
    dragging: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val action = ActionRegistry.find(spec.type)
    val visual = spec.type.visual()
    val summary = remember(spec) {
        action?.let { ParamSummary.of(it.paramsSchema, spec.params, appLabel = { pkg -> appLabel(context, pkg) }) }.orEmpty()
    }
    val shape = RoundedCornerShape(NicoRadius.Tile)
    val lineColor = NicoColors.TextPrimary.copy(alpha = 0.14f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (dragging) return@drawBehind
                // Fil de la frise, passant sous les pastilles, prolongé dans l'espace entre deux lignes.
                val x = (ROW_PADDING + BADGE_SIZE / 2).toPx()
                val gap = TIMELINE_SPACING.toPx() / 2f
                val stroke = 2.dp.toPx()
                if (!isFirst) drawLine(lineColor, Offset(x, -gap), Offset(x, size.height / 2f), stroke)
                if (!isLast) drawLine(lineColor, Offset(x, size.height / 2f), Offset(x, size.height + gap), stroke)
            }
            .clip(shape)
            .background(if (dragging) NicoColors.GlassFillRaised else Color.Transparent, shape)
            .clickable(role = Role.Button, onClickLabel = "Modifier l'action") { onEdit() }
            .padding(start = ROW_PADDING, top = ROW_PADDING, bottom = ROW_PADDING)
    ) {
        ActionIconBadge(icon = visual.icon, color = visual.color, size = BADGE_SIZE)
        Column(
            modifier = Modifier.weight(1f).padding(start = NicoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = action?.label ?: "${spec.type.name} (non implémentée)",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = NicoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (spec.critical || spec.delayMsBefore > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (spec.critical) StatusBadge("Critique", color = NicoColors.RedBright)
                    if (spec.delayMsBefore > 0) StatusBadge("+${spec.delayMsBefore} ms", icon = Symbols.HourglassTopFilled)
                }
            }
        }
        Icon(
            Symbols.DragIndicator,
            contentDescription = null,
            tint = NicoColors.TextTertiary,
            modifier = Modifier.size(20.dp)
        )
        Box {
            GlassIconButton(
                icon = Symbols.MoreVert,
                contentDescription = "Options de l'action ${index + 1}",
                onClick = { menuOpen = true },
                tint = NicoColors.TextSecondary
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuItem("Modifier", Symbols.Edit) { menuOpen = false; onEdit() }
                MenuItem("Dupliquer", Symbols.ContentCopy) { menuOpen = false; onDuplicate() }
                if (!isFirst) MenuItem("Monter", Symbols.KeyboardArrowUp) { menuOpen = false; onMove(index, index - 1) }
                if (!isLast) MenuItem("Descendre", Symbols.KeyboardArrowDown) { menuOpen = false; onMove(index, index + 1) }
                MenuItem("Supprimer", Symbols.Delete, danger = true) { menuOpen = false; onRemove() }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    icon: ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) NicoColors.RedBright else NicoColors.TextPrimary
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick
    )
}

/** Nom d'app lisible pour un paquet ; rien pour un slot ou une app absente. */
private fun appLabel(context: Context, pkg: String): String? {
    if (pkg.contains('{')) return null
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()
}

/** Rapport action par action : indispensable pour déboguer sans hurler sur son téléphone. */
@Composable
private fun TestReportDialog(report: ExecutionReport, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    LaunchedEffect(report) { if (report.success) haptics.confirm() else haptics.reject() }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (report.success) "Chaîne exécutée" else "Exécution incomplète",
        icon = {
            Icon(
                imageVector = if (report.success) Symbols.CheckCircleFilled else Symbols.WarningFilled,
                contentDescription = null,
                tint = if (report.success) NicoColors.Success else NicoColors.Warning,
                modifier = Modifier.size(36.dp)
            )
        },
        buttons = {
            GlassButton(
                text = "Fermer",
                onClick = onDismiss,
                style = GlassButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Text(report.feedbackText(), style = MaterialTheme.typography.bodyMedium, color = NicoColors.TextSecondary)
        Spacer(modifier = Modifier.height(NicoSpacing.xs))
        report.outcomes.forEachIndexed { index, outcome ->
            val visual = outcome.spec.type.visual()
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIconBadge(icon = visual.icon, color = visual.color, size = 30.dp)
                Column(modifier = Modifier.weight(1f).padding(horizontal = NicoSpacing.sm)) {
                    Text("${index + 1}. ${visual.label}", style = MaterialTheme.typography.bodyMedium)
                    val reason = (outcome.result as? ActionResult.Failure)?.reason
                    if (reason != null) {
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = NicoColors.RedBright)
                    }
                }
                Icon(
                    imageVector = if (outcome.succeeded) Symbols.CheckCircleFilled else Symbols.ErrorFilled,
                    contentDescription = if (outcome.succeeded) "Réussie" else "Échouée",
                    tint = if (outcome.succeeded) NicoColors.Success else NicoColors.RedBright,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (report.stoppedEarly) {
            InlineError("Chaîne interrompue par une action critique.")
        }
        report.blockedBy?.let { InlineError("Condition non remplie : $it") }
    }
}

private val MATCH_MODE_LABELS = mapOf(
    MatchMode.FUZZY to "Flexible (recommandé)",
    MatchMode.EXACT to "Exacte",
    MatchMode.CONTAINS to "Contient la phrase",
    MatchMode.REGEX to "Expression régulière (expert)"
)
