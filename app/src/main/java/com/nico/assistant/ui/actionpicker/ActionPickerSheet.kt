package com.nico.assistant.ui.actionpicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.action.Backend
import com.nico.assistant.data.model.ActionSpec

/**
 * Sélecteur d'action en deux temps (spec §7.4) : choix du type, puis paramètres.
 *
 * Le second écran n'est **pas** écrit à la main : il est généré depuis `paramsSchema`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    initial: ActionSpec?,
    availableSlots: List<String>,
    isBackendAvailable: (Backend) -> Boolean,
    onConfirm: (ActionSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chosen by remember {
        mutableStateOf(initial?.let { spec -> ActionRegistry.find(spec.type) })
    }
    var draft by remember { mutableStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val action = chosen
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
                spec = draft ?: ActionSpec(type = action.type),
                availableSlots = availableSlots,
                onChange = { draft = it },
                onBack = { chosen = null },
                onConfirm = { onConfirm(it) }
            )
        }
    }
}

@Composable
private fun TypeStep(
    isBackendAvailable: (Backend) -> Boolean,
    onPick: (Action) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val grouped = remember(search) {
        ActionRegistry.byCategory().mapValues { (_, actions) ->
            actions.filter { it.label.contains(search, ignoreCase = true) }
        }.filterValues { it.isNotEmpty() }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Ajouter une action", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Rechercher") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            for ((category, actions) in grouped) {
                item(key = "cat-${category.name}") {
                    Text(
                        text = category.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(actions, key = { it.type.name }) { action ->
                    val available = isBackendAvailable(action.backend)
                    ActionRow(action, available) { if (available) onPick(action) }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(action: Action, available: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = available, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (available) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        val subtitle = when {
            !available -> "${action.backend} indisponible"
            action.description.isNotBlank() -> action.description
            else -> ""
        }
        if (subtitle.isNotBlank()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ParamsStep(
    action: Action,
    spec: ActionSpec,
    availableSlots: List<String>,
    onChange: (ActionSpec) -> Unit,
    onBack: () -> Unit,
    onConfirm: (ActionSpec) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(action.label, style = MaterialTheme.typography.titleLarge)

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = spec.critical,
                onCheckedChange = { onChange(spec.copy(critical = it)) }
            )
            Column {
                Text("Action critique", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Si elle échoue, la suite de la chaîne est abandonnée",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = if (spec.delayMsBefore > 0) spec.delayMsBefore.toString() else "",
            onValueChange = { value ->
                onChange(spec.copy(delayMsBefore = value.toLongOrNull() ?: 0))
            },
            label = { Text("Délai avant (ms)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            TextButton(onClick = onBack) { Text("Changer d'action") }
            Button(
                onClick = { onConfirm(requiredFilled(action, spec)) },
                enabled = isComplete(action, spec)
            ) { Text("Valider") }
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
