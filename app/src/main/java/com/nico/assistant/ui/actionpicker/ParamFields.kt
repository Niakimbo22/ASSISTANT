package com.nico.assistant.ui.actionpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.apps.AppEntry
import com.nico.assistant.apps.AppRepository
import com.nico.assistant.data.repo.AutomationRepository

/**
 * Le champ correspondant à un [ParamSpec]. **Aucun formulaire n'est écrit à la main** :
 * ajouter une action rend son formulaire disponible automatiquement (spec §5.2 et §7.4).
 */
@Composable
fun ParamField(
    spec: ParamSpec,
    value: String,
    availableSlots: List<String>,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        when (spec.type) {
            ParamType.ENUM -> EnumField(spec, value, onValueChange)
            ParamType.TOGGLE -> ToggleField(spec, value, onValueChange)
            ParamType.APP_PICKER -> AppPickerField(spec, value, onValueChange)
            ParamType.AUTOMATION_PICKER -> AutomationPickerField(spec, value, onValueChange)
            ParamType.NUMBER, ParamType.DURATION ->
                PlainTextField(spec, value, KeyboardType.Number, onValueChange)
            else -> PlainTextField(spec, value, KeyboardType.Text, onValueChange)
        }

        if (spec.acceptsSlots && availableSlots.isNotEmpty()) {
            SlotChips(availableSlots) { slot -> onValueChange(value + "{$slot}") }
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(spec.label + if (spec.required) " *" else "") },
        supportingText = spec.hint?.let { hint -> { Text(hint) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EnumField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    val display = spec.options.firstOrNull { it.first == value }?.second
        ?: value.ifBlank { "À choisir" }

    DropdownField(
        label = spec.label,
        display = display,
        options = spec.options,
        onPick = onValueChange
    )
}

/**
 * Menu déroulant bâti sur `DropdownMenu`, volontairement pas sur `ExposedDropdownMenuBox` :
 * l'API expérimentale de ce dernier change de signature d'une version de Material3 à l'autre,
 * ce qui n'est pas vérifiable sans build local.
 */
@Composable
fun DropdownField(
    label: String,
    display: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(display, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for ((optionValue, optionLabel) in options) {
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onPick(optionValue)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToggleField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    Text(spec.label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((optionValue, optionLabel) in TOGGLE_OPTIONS) {
            FilterChip(
                selected = value.equals(optionValue, ignoreCase = true),
                onClick = { onValueChange(optionValue) },
                label = { Text(optionLabel) }
            )
        }
    }
}

/** Les automatisations existantes, pour la composition (RUN_AUTOMATION). */
@Composable
private fun AutomationPickerField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    val automations by remember { AutomationRepository.from(context).observeAll() }
        .collectAsState(initial = emptyList())

    DropdownField(
        label = spec.label,
        display = automations.firstOrNull { it.id == value }?.name ?: "À choisir",
        options = automations.map { it.id to it.name },
        onPick = onValueChange
    )
}

@Composable
private fun AppPickerField(spec: ParamSpec, value: String, onValueChange: (String) -> Unit) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(spec.label) },
            supportingText = spec.hint?.let { hint -> { Text(hint) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = { showPicker = true },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Choisir dans les apps installées")
        }
    }

    if (showPicker) {
        // La liste des apps réellement installées, jamais un paquet codé en dur.
        val apps = remember { AppRepository(context).listLaunchableApps() }
        AppPickerDialog(
            apps = apps,
            onPick = {
                onValueChange(it.packageName)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppEntry>,
    onPick: (AppEntry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Applications installées") },
        text = {
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    DropdownMenuItem(
                        text = { Text(app.label) },
                        onClick = { onPick(app) }
                    )
                }
            }
        }
    )
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
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        for (slot in slots) {
            AssistChip(onClick = { onInsert(slot) }, label = { Text("{$slot}") })
        }
    }
}

private val TOGGLE_OPTIONS = listOf(
    "on" to "Activer",
    "off" to "Désactiver",
    "toggle" to "Basculer"
)
