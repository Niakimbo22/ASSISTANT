package com.nico.assistant.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.core.condition.ConditionSpecs
import com.nico.assistant.data.db.ConditionType
import com.nico.assistant.ui.actionpicker.DropdownField
import com.nico.assistant.ui.actionpicker.ParamField

/** Section « Seulement si » de l'éditeur (spec §7.3). */
@Composable
fun ConditionSection(
    conditions: List<Condition>,
    onAdd: (Condition) -> Unit,
    onRemove: (Int) -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }

    if (conditions.isEmpty()) {
        Text(
            text = "Aucune condition : cette automatisation se déclenche toujours.",
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            conditions.forEachIndexed { index, condition ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ConditionSpecs.label(condition.type),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val summary = ConditionSpecs.summarize(condition)
                            if (summary.isNotBlank()) {
                                Text(summary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Retirer")
                        }
                    }
                }
            }
        }
    }

    TextButton(onClick = { pickerOpen = true }, modifier = Modifier.padding(top = 4.dp)) {
        Text("+ Ajouter une condition")
    }

    if (pickerOpen) {
        ConditionPickerDialog(
            onConfirm = {
                onAdd(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

/** Le formulaire est généré depuis [ConditionSpecs], comme celui des actions. */
@Composable
private fun ConditionPickerDialog(
    onConfirm: (Condition) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(ConditionType.TIME_RANGE) }
    var params by remember { mutableStateOf(ConditionSpecs.defaultsOf(ConditionType.TIME_RANGE)) }
    var negated by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(Condition(type = type, params = params, negated = negated)) }
            ) { Text("Ajouter") }
        },
        title = { Text("Seulement si") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                DropdownField(
                    label = "Condition",
                    display = ConditionSpecs.label(type),
                    options = ConditionType.entries.map { it.name to ConditionSpecs.label(it) },
                    onPick = { picked ->
                        type = ConditionType.valueOf(picked)
                        params = ConditionSpecs.defaultsOf(type)
                    }
                )

                for (spec in ConditionSpecs.paramsOf(type)) {
                    ParamField(
                        spec = spec,
                        value = params[spec.key] ?: spec.default.orEmpty(),
                        availableSlots = emptyList(),
                        onValueChange = { value -> params = params + (spec.key to value) }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Checkbox(checked = negated, onCheckedChange = { negated = it })
                    Text("Inverser la condition", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    )
}
