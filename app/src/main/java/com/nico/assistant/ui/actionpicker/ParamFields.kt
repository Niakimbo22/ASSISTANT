package com.nico.assistant.ui.actionpicker

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.apps.AppEntry
import com.nico.assistant.apps.AppRepository
import com.nico.assistant.core.matching.TextNormalizer
import com.nico.assistant.data.repo.AutomationRepository
import com.nico.assistant.ui.theme.AppIcon
import com.nico.assistant.ui.theme.FieldLabel
import com.nico.assistant.ui.theme.GlassChip
import com.nico.assistant.ui.theme.GlassIconButton
import com.nico.assistant.ui.theme.GlassSegmentedControl
import com.nico.assistant.ui.theme.GlassSelectField
import com.nico.assistant.ui.theme.GlassSheet
import com.nico.assistant.ui.theme.GlassSheetHeader
import com.nico.assistant.ui.theme.GlassTextField
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.OptionRow
import com.nico.assistant.ui.theme.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le champ correspondant à un [ParamSpec]. **Aucun formulaire n'est écrit à la main** :
 * ajouter une action rend son formulaire disponible automatiquement (spec §5.2 et §7.4).
 * Seul le rendu change avec le design system : même boîte de verre pour tous les champs.
 */
@Composable
fun ParamField(
    spec: ParamSpec,
    value: String,
    availableSlots: List<String>,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs)
    ) {
        when (spec.type) {
            ParamType.ENUM -> EnumField(spec, value, onValueChange)
            ParamType.TOGGLE -> ToggleField(spec, value, onValueChange)
            ParamType.APP_PICKER -> AppPickerField(spec, value, onValueChange)
            ParamType.AUTOMATION_PICKER -> AutomationPickerField(spec, value, onValueChange)
            ParamType.NUMBER, ParamType.DURATION ->
                PlainTextField(spec, value, KeyboardType.Number, onValueChange)
            ParamType.URL -> PlainTextField(spec, value, KeyboardType.Uri, onValueChange)
            else -> PlainTextField(spec, value, KeyboardType.Text, onValueChange)
        }

        if (spec.acceptsSlots && availableSlots.isNotEmpty()) {
            SlotChips(availableSlots) { slot ->
                // Un choix d'app ne se complète pas : le slot remplace la valeur.
                if (spec.type == ParamType.APP_PICKER) onValueChange("{$slot}") else onValueChange(value + "{$slot}")
            }
        }
    }
}

/** Un champ libre accepte les slots ; un menu déroulant ou une bascule, non. */
private val ParamSpec.acceptsSlots: Boolean
    get() = type in setOf(
        ParamType.TEXT,
        ParamType.URL,
        ParamType.NUMBER,
        ParamType.DURATION,
        ParamType.CONTACT_PICKER,
        ParamType.APP_PICKER
    )

@Composable
private fun PlainTextField(
    spec: ParamSpec,
    value: String,
    keyboard: KeyboardType,
    onValueChange: (String) -> Unit
) {
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = spec.label,
        required = spec.required,
        placeholder = spec.default?.takeIf { it.isNotBlank() },
        supportingText = spec.hint,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard)
    )
}

@Composable
private fun EnumField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    val known = spec.options.firstOrNull { it.first == value }?.second
    GlassSelectField(
        label = spec.label,
        display = known ?: value.ifBlank { "À choisir" },
        placeholder = known == null && value.isBlank(),
        options = spec.options,
        selected = value,
        required = spec.required,
        supportingText = spec.hint,
        onPick = onValueChange
    )
}

@Composable
private fun ToggleField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    Column {
        FieldLabel(spec.label, spec.required)
        GlassSegmentedControl(options = ToggleOptions, selected = value, onSelect = onValueChange)
    }
}

/** Les automatisations existantes, pour la composition (RUN_AUTOMATION). */
@Composable
private fun AutomationPickerField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    val automations by remember { AutomationRepository.from(context).observeAll() }
        .collectAsState(initial = emptyList())
    val current = automations.firstOrNull { it.id == value }

    GlassSelectField(
        label = spec.label,
        display = current?.name ?: "À choisir",
        placeholder = current == null,
        options = automations.map { it.id to it.name.ifBlank { "Sans nom" } },
        selected = value,
        required = spec.required,
        leadingIcon = Symbols.AccountTreeFilled,
        onPick = onValueChange
    )
}

/**
 * Choix d'app : champ libre (paquet ou slot) + sélecteur des apps réellement installées.
 * Le nom lisible de l'app choisie s'affiche dessous.
 */
@Composable
private fun AppPickerField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val resolved = remember(value) { resolveAppLabel(context, value) }

    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = spec.label,
        required = spec.required,
        placeholder = "Choisis une app",
        supportingText = when {
            resolved != null -> "→ $resolved"
            value.startsWith("{") -> "L'app sera celle que tu nommes à voix haute"
            else -> spec.hint
        },
        trailing = {
            GlassIconButton(
                icon = Symbols.AppsFilled,
                contentDescription = "Choisir dans les apps installées",
                onClick = { showPicker = true },
                tint = NicoColors.TextSecondary
            )
        }
    )

    if (showPicker) {
        AppPickerSheet(
            selected = value,
            onPick = {
                onValueChange(it.packageName)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

private fun resolveAppLabel(context: Context, pkg: String): String? {
    if (pkg.isBlank() || pkg.contains('{')) return null
    return runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()
}

/**
 * Les apps réellement installées, jamais un paquet codé en dur : recherche collée en haut,
 * liste avec les vraies icônes, chargées hors du fil principal.
 */
@Composable
private fun AppPickerSheet(
    selected: String,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { AppRepository(context).listLaunchableApps() }
    }
    val filtered = remember(apps, search) {
        val needle = TextNormalizer.normalize(search)
        apps.orEmpty().filter { needle.isEmpty() || TextNormalizer.normalize(it.label).contains(needle) }
    }

    GlassSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            GlassSheetHeader(title = "Applications installées")
            GlassTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = "Rechercher une app",
                leadingIcon = Symbols.Search,
                modifier = Modifier.padding(horizontal = NicoSpacing.gutter).padding(bottom = NicoSpacing.sm)
            )
            if (apps == null) {
                Text(
                    "Chargement…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NicoColors.TextSecondary,
                    modifier = Modifier.padding(NicoSpacing.gutter)
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = NicoSpacing.md, vertical = NicoSpacing.xs)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    OptionRow(
                        label = app.label,
                        description = app.packageName,
                        selected = app.packageName == selected,
                        onClick = { onPick(app) },
                        leading = { AppIcon(app.packageName) }
                    )
                }
            }
        }
    }
}

/**
 * Les slots disponibles, cliquables : c'est ce qui rend les variables découvrables
 * sans documentation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotChips(slots: List<String>, onInsert: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "Insérer",
            style = MaterialTheme.typography.bodySmall,
            color = NicoColors.TextTertiary,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp)
        )
        for (slot in slots) {
            GlassChip(
                text = "{$slot}",
                mono = true,
                accent = SLOT_ACCENT,
                leadingIcon = Symbols.Add,
                onClick = { onInsert(slot) }
            )
        }
    }
}

private val SLOT_ACCENT = NicoColors.Condition
