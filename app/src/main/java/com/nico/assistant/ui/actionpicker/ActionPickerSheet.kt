package com.nico.assistant.ui.actionpicker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.action.Backend
import com.nico.assistant.core.matching.TextNormalizer
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.ui.theme.ActionIconTile
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassChip
import com.nico.assistant.ui.theme.GlassIconButton
import com.nico.assistant.ui.theme.GlassSheet
import com.nico.assistant.ui.theme.GlassSheetHeader
import com.nico.assistant.ui.theme.GlassTextField
import com.nico.assistant.ui.theme.GlassToggleRow
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoMotion
import com.nico.assistant.ui.theme.NicoRadius
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.SectionLabel
import com.nico.assistant.ui.theme.StatusBadge
import com.nico.assistant.ui.theme.Symbols
import com.nico.assistant.ui.theme.color
import com.nico.assistant.ui.theme.glass
import com.nico.assistant.ui.theme.pressScale
import com.nico.assistant.ui.theme.rememberHaptics

/**
 * Sélecteur d'action en deux temps (spec §7.4) : choix du type dans une grille de tuiles,
 * puis paramètres.
 *
 * La feuille s'ouvre en grand : recherche collée en haut, filtres de catégorie, et une grille
 * qui défile **sous** la recherche sans jamais passer dessus. Le second écran n'est **pas**
 * écrit à la main : il est généré depuis `paramsSchema`.
 */
@Composable
fun ActionPickerSheet(
    initial: ActionSpec?,
    availableSlots: List<String>,
    isBackendAvailable: (Backend) -> Boolean,
    onConfirm: (ActionSpec) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember {
        mutableStateOf(initial?.let { spec -> ActionRegistry.find(spec.type) })
    }
    var draft by remember { mutableStateOf(initial) }

    GlassSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        AnimatedContent(
            targetState = chosen,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(NicoMotion.gentle()) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(NicoMotion.gentle()) { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            label = "étape du sélecteur",
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) { action ->
            if (action == null) {
                TypeStep(
                    isBackendAvailable = isBackendAvailable,
                    onPick = { picked ->
                        chosen = picked
                        draft = ActionSpec(
                            type = picked.type,
                            params = picked.paramsSchema
                                .mapNotNull { param -> param.default?.let { param.key to it } }
                                .toMap()
                        )
                    }
                )
            } else {
                ParamsStep(
                    action = action,
                    spec = draft?.takeIf { it.type == action.type } ?: ActionSpec(type = action.type),
                    availableSlots = availableSlots,
                    isEditing = initial != null,
                    onChange = { draft = it },
                    onBack = { chosen = null },
                    onConfirm = { onConfirm(it) }
                )
            }
        }
    }
}

/** Ce que l'app peut faire d'une action ici et maintenant. */
private enum class Availability { READY, FALLBACK, BLOCKED }

private fun availabilityOf(action: Action, isBackendAvailable: (Backend) -> Boolean): Availability = when {
    isBackendAvailable(action.backend) -> Availability.READY
    action.hasFallback -> Availability.FALLBACK
    else -> Availability.BLOCKED
}

private val Backend.displayName: String
    get() = when (this) {
        Backend.SHIZUKU -> "Shizuku"
        Backend.ACCESSIBILITY -> "Accessibilité"
        Backend.INTENT -> "Système"
        Backend.INTERNAL -> "Interne"
    }

@Composable
private fun TypeStep(
    isBackendAvailable: (Backend) -> Boolean,
    onPick: (Action) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ActionCategory?>(null) }
    val grouped = remember(search, category) {
        val needle = TextNormalizer.normalize(search)
        ActionRegistry.byCategory()
            .filterKeys { category == null || it == category }
            .mapValues { (_, actions) ->
                actions.filter { action ->
                    needle.isEmpty() ||
                        TextNormalizer.normalize(action.label).contains(needle) ||
                        TextNormalizer.normalize(action.description).contains(needle)
                }
            }
            .filterValues { it.isNotEmpty() }
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        // En-tête collé : titre, recherche, filtres. La grille défile en dessous, jamais dessus.
        GlassSheetHeader(title = "Ajouter une action", subtitle = "${ActionRegistry.all.size} actions disponibles")
        GlassTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = "Rechercher : wifi, musique, minuteur…",
            leadingIcon = Symbols.Search,
            modifier = Modifier.padding(horizontal = NicoSpacing.gutter),
            trailing = if (search.isNotEmpty()) {
                {
                    GlassIconButton(
                        icon = Symbols.Close,
                        contentDescription = "Effacer la recherche",
                        onClick = { search = "" },
                        tint = NicoColors.TextSecondary,
                        iconSize = 18.dp
                    )
                }
            } else {
                null
            }
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = NicoSpacing.gutter, vertical = NicoSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NicoSpacing.xs)
        ) {
            item(key = "all") {
                GlassChip(text = "Tout", selected = category == null, onClick = { category = null })
            }
            items(ActionCategory.entries.toList(), key = { it.name }) { entry ->
                GlassChip(
                    text = entry.label,
                    selected = category == entry,
                    accent = entry.color,
                    onClick = { category = if (category == entry) null else entry }
                )
            }
        }

        if (grouped.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(NicoSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Aucune action ne correspond", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Essaie un autre mot, ou retire le filtre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NicoColors.TextSecondary
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = NicoSpacing.md,
                end = NicoSpacing.md,
                top = NicoSpacing.xxs,
                bottom = NicoSpacing.xxl
            ),
            horizontalArrangement = Arrangement.spacedBy(NicoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
        ) {
            for ((cat, actions) in grouped) {
                item(key = "cat-${cat.name}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(
                        text = cat.label,
                        color = cat.color,
                        modifier = Modifier.padding(start = NicoSpacing.xxs, top = NicoSpacing.sm)
                    )
                }
                items(actions, key = { it.type.name }) { action ->
                    ActionTile(
                        action = action,
                        availability = availabilityOf(action, isBackendAvailable),
                        onClick = { onPick(action) }
                    )
                }
            }
            item(key = "bottom-inset", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

/**
 * Tuile d'action : icône colorée, nom, description courte. Indisponible, elle est grisée,
 * porte un badge qui dit pourquoi, et refuse le tap avec une vibration.
 */
@Composable
private fun ActionTile(action: Action, availability: Availability, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val blocked = availability == Availability.BLOCKED
    val shape = RoundedCornerShape(NicoRadius.Tile)
    val contentAlpha by animateFloatAsState(if (blocked) 0.45f else 1f, label = "tuile indisponible")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            .pressScale(interaction)
            .glass(shape, fill = if (blocked) NicoColors.GlassFillSubtle else NicoColors.GlassFill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = if (blocked) null else "Choisir"
            ) {
                if (blocked) {
                    haptics.reject()
                } else {
                    haptics.tick()
                    onClick()
                }
            }
            .padding(NicoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            ActionIconTile(type = action.type, dimmed = blocked)
            Spacer(modifier = Modifier.weight(1f))
            when (availability) {
                Availability.BLOCKED -> StatusBadge(
                    action.backend.displayName,
                    color = NicoColors.Warning,
                    icon = Symbols.LockFilled
                )
                Availability.FALLBACK -> StatusBadge("Repli", color = NicoColors.TextSecondary)
                Availability.READY -> Unit
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(contentAlpha)
        )
        val subtitle = when (availability) {
            Availability.BLOCKED -> "Nécessite ${action.backend.displayName}"
            else -> action.description
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NicoColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha)
            )
        }
    }
}

@Composable
private fun ParamsStep(
    action: Action,
    spec: ActionSpec,
    availableSlots: List<String>,
    isEditing: Boolean,
    onChange: (ActionSpec) -> Unit,
    onBack: () -> Unit,
    onConfirm: (ActionSpec) -> Unit
) {
    val complete = isComplete(action, spec)

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        GlassSheetHeader(
            title = action.label,
            subtitle = action.description.takeIf { it.isNotBlank() },
            leading = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        icon = Symbols.ArrowBack,
                        contentDescription = "Changer d'action",
                        onClick = onBack,
                        modifier = Modifier.padding(end = NicoSpacing.xxs)
                    )
                    ActionIconTile(type = action.type)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NicoSpacing.gutter)
                .padding(bottom = NicoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.md)
        ) {
            if (action.paramsSchema.isEmpty()) {
                Text(
                    "Cette action n'a rien à régler.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NicoColors.TextSecondary
                )
            }
            for (param in action.paramsSchema) {
                ParamField(
                    spec = param,
                    value = spec.params[param.key] ?: param.default.orEmpty(),
                    availableSlots = availableSlots,
                    onValueChange = { value ->
                        onChange(spec.copy(params = spec.params + (param.key to value)))
                    }
                )
            }

            SectionLabel("Options avancées", icon = Symbols.Tune, modifier = Modifier.padding(top = NicoSpacing.sm))

            GlassToggleRow(
                title = "Action critique",
                description = "Si elle échoue, la suite de la chaîne est abandonnée",
                checked = spec.critical,
                onCheckedChange = { onChange(spec.copy(critical = it)) }
            )

            GlassTextField(
                value = if (spec.delayMsBefore > 0) spec.delayMsBefore.toString() else "",
                onValueChange = { value ->
                    onChange(spec.copy(delayMsBefore = value.filter(Char::isDigit).toLongOrNull() ?: 0))
                },
                label = "Délai avant (ms)",
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        // Boutons collés en bas de la feuille, au-dessus de la navigation gestuelle.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(0.dp), fill = NicoColors.GlassFillSubtle, border = null, sheen = false)
                .navigationBarsPadding()
                .padding(horizontal = NicoSpacing.gutter, vertical = NicoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.xxs)
        ) {
            if (!complete) {
                Text(
                    "Remplis les champs marqués *",
                    style = MaterialTheme.typography.bodySmall,
                    color = NicoColors.TextTertiary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            GlassButton(
                text = if (isEditing) "Enregistrer l'action" else "Ajouter à la chaîne",
                onClick = { onConfirm(requiredFilled(action, spec)) },
                enabled = complete,
                icon = Symbols.Check,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Les valeurs par défaut non saisies sont écrites explicitement à la validation. */
private fun requiredFilled(action: Action, spec: ActionSpec): ActionSpec {
    val withDefaults = action.paramsSchema
        .mapNotNull { param ->
            val value = spec.params[param.key] ?: param.default
            value?.let { param.key to it }
        }
        .toMap()
    return spec.copy(params = withDefaults)
}

private fun isComplete(action: Action, spec: ActionSpec): Boolean =
    action.paramsSchema
        .filter { it.required }
        .all { param -> (spec.params[param.key] ?: param.default).orEmpty().isNotBlank() }
