package com.nico.assistant.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.core.condition.ConditionSpecs
import com.nico.assistant.data.db.ConditionType
import com.nico.assistant.ui.actionpicker.ParamField
import com.nico.assistant.ui.actionpicker.ParamSummary
import com.nico.assistant.ui.theme.ActionIconBadge
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassButtonStyle
import com.nico.assistant.ui.theme.GlassIconButton
import com.nico.assistant.ui.theme.GlassSelectField
import com.nico.assistant.ui.theme.GlassSheet
import com.nico.assistant.ui.theme.GlassSheetHeader
import com.nico.assistant.ui.theme.GlassToggleRow
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.Symbols
import com.nico.assistant.ui.theme.icon

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
            text = "Toujours — aucune condition.",
            style = MaterialTheme.typography.bodyMedium,
            color = NicoColors.TextSecondary
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs)) {
            conditions.forEachIndexed { index, condition ->
                ConditionRow(condition = condition, onRemove = { onRemove(index) })
            }
        }
    }

    GlassButton(
        text = "Ajouter une condition",
        onClick = { pickerOpen = true },
        icon = Symbols.Add,
        style = GlassButtonStyle.Secondary,
        height = 48.dp,
        modifier = Modifier.fillMaxWidth()
    )

    if (pickerOpen) {
        ConditionPickerSheet(
            onConfirm = {
                onAdd(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
    }
}

@Composable
private fun ConditionRow(condition: Condition, onRemove: () -> Unit) {
    val details = ParamSummary.of(ConditionSpecs.paramsOf(condition.type), condition.params)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ActionIconBadge(icon = condition.type.icon, color = NicoColors.Condition, size = 36.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = NicoSpacing.sm)) {
            Text(
                text = (if (condition.negated) "Sauf si : " else "") + ConditionSpecs.label(condition.type),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = NicoColors.TextSecondary)
            }
        }
        GlassIconButton(
            icon = Symbols.Close,
            contentDescription = "Retirer la condition",
            onClick = onRemove,
            tint = NicoColors.TextTertiary,
            iconSize = 20.dp
        )
    }
}

/** Le formulaire est généré depuis [ConditionSpecs], comme celui des actions. */
@Composable
private fun ConditionPickerSheet(
    onConfirm: (Condition) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(ConditionType.TIME_RANGE) }
    var params by remember { mutableStateOf(ConditionSpecs.defaultsOf(ConditionType.TIME_RANGE)) }
    var negated by remember { mutableStateOf(false) }

    GlassSheet(onDismissRequest = onDismiss) {
        GlassSheetHeader(
            title = "Seulement si",
            subtitle = "L'automatisation ne se lance que si c'est vrai",
            leading = { ActionIconBadge(icon = type.icon, color = NicoColors.Condition, size = 44.dp) }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NicoSpacing.gutter)
                .navigationBarsPadding()
                .padding(bottom = NicoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.md)
        ) {
            GlassSelectField(
                label = "Condition",
                display = ConditionSpecs.label(type),
                options = ConditionType.entries.map { it.name to ConditionSpecs.label(it) },
                selected = type.name,
                leadingIcon = type.icon,
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

            GlassToggleRow(
                title = "Inverser la condition",
                description = "Se lance seulement si c'est faux",
                checked = negated,
                onCheckedChange = { negated = it }
            )

            GlassButton(
                text = "Ajouter la condition",
                onClick = { onConfirm(Condition(type = type, params = params, negated = negated)) },
                icon = Symbols.Check,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
