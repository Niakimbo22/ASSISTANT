package com.nico.assistant.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.action.Backend
import com.nico.assistant.core.executor.ExecutionReport
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.ui.actionpicker.ActionPickerSheet
import com.nico.assistant.ui.actionpicker.DropdownField

/** Éditeur d'automatisation (spec §7.3) : l'écran central de la V2. */
@OptIn(ExperimentalMaterial3Api::class)
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

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isNew) "Nouvelle automatisation" else draft.name.ifBlank { "Automatisation" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = draft.isValid) {
                        Icon(Icons.Filled.Check, contentDescription = "Enregistrer")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            SectionTitle("Quand je dis")
            PhraseEditor(
                phrases = draft.phrases,
                onAdd = viewModel::addPhrase,
                onRemove = viewModel::removePhrase
            )
            MatchModeField(draft.matchMode, viewModel::setMatchMode)

            SectionTitle("Alors")
            ActionChain(
                actions = draft.actions,
                onEdit = { index -> editingIndex = index; pickerOpen = true },
                onRemove = viewModel::removeAction,
                onDuplicate = viewModel::duplicateAction,
                onMove = viewModel::moveAction
            )
            OutlinedButton(
                onClick = { editingIndex = null; pickerOpen = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Ajouter une action")
            }

            SectionTitle("Réglages")
            OutlinedTextField(
                value = draft.automation.feedbackText.orEmpty(),
                onValueChange = viewModel::setFeedbackText,
                label = { Text("Réponse vocale (optionnel)") },
                supportingText = { Text("Sinon un message est généré. Accepte les {slots}.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Activée", modifier = Modifier.weight(1f))
                Switch(checked = draft.automation.enabled, onCheckedChange = viewModel::setEnabled)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("Demander confirmation", modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.automation.confirmBeforeRun,
                    onCheckedChange = viewModel::setConfirmBeforeRun
                )
            }

            draft.validationMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Button(
                onClick = viewModel::testNow,
                enabled = draft.actions.isNotEmpty() && !running,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                }
                Text("  Tester maintenant")
            }
        }
    }

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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhraseEditor(
    phrases: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit
) {
    var input by remember { mutableStateOf("") }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        phrases.forEachIndexed { index, phrase ->
            InputChip(
                selected = false,
                onClick = { onRemove(index) },
                label = { Text(phrase) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Retirer") }
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Ajouter une phrase") },
            supportingText = { Text("Un {slot} capture une variable : « ouvre {app} »") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                onAdd(input)
                input = ""
            },
            enabled = input.isNotBlank()
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Ajouter")
        }
    }
}

@Composable
private fun MatchModeField(mode: MatchMode, onChange: (MatchMode) -> Unit) {
    DropdownField(
        label = "Correspondance",
        display = MATCH_MODE_LABELS[mode] ?: mode.name,
        options = MatchMode.entries.map { it.name to (MATCH_MODE_LABELS[it] ?: it.name) },
        onPick = { picked -> onChange(MatchMode.valueOf(picked)) }
    )
}

@Composable
private fun ActionChain(
    actions: List<ActionSpec>,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    if (actions.isEmpty()) {
        Text(
            text = "Aucune action pour l'instant.",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEachIndexed { index, spec ->
            ActionRow(
                index = index,
                spec = spec,
                isFirst = index == 0,
                isLast = index == actions.lastIndex,
                onEdit = { onEdit(index) },
                onRemove = { onRemove(index) },
                onDuplicate = { onDuplicate(index) },
                onMove = onMove
            )
        }
    }
}

@Composable
private fun ActionRow(
    index: Int,
    spec: ActionSpec,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val action = ActionRegistry.find(spec.type)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                text = "${index + 1}.",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    text = action?.label ?: "${spec.type.name} (non implémentée)",
                    style = MaterialTheme.typography.bodyLarge
                )
                val summary = buildList {
                    spec.params.entries.take(2).forEach { add("${it.key} = ${it.value}") }
                    if (spec.critical) add("critique")
                    if (spec.delayMsBefore > 0) add("+${spec.delayMsBefore} ms")
                }.joinToString(" · ")
                if (summary.isNotBlank()) {
                    Text(text = summary, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = { onMove(index, index - 1) }, enabled = !isFirst) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Monter")
            }
            IconButton(onClick = { onMove(index, index + 1) }, enabled = !isLast) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "Descendre")
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Plus")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Modifier") },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Dupliquer") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    onClick = { menuOpen = false; onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text("Supprimer") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onRemove() }
                )
            }
        }
    }
}

/** Rapport action par action : indispensable pour déboguer sans hurler sur son téléphone. */
@Composable
private fun TestReportDialog(report: ExecutionReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text(if (report.success) "Chaîne exécutée" else "Exécution incomplète") },
        text = {
            Column {
                Text(report.feedbackText(), style = MaterialTheme.typography.bodyMedium)
                report.outcomes.forEachIndexed { index, outcome ->
                    val label = ActionRegistry.find(outcome.spec.type)?.label
                        ?: outcome.spec.type.name
                    val mark = if (outcome.succeeded) "✓" else "✗"
                    Text(
                        text = "$mark ${index + 1}. $label",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (report.stoppedEarly) {
                    Text(
                        text = "Chaîne interrompue par une action critique.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

private val MATCH_MODE_LABELS = mapOf(
    MatchMode.FUZZY to "Flexible (recommandé)",
    MatchMode.EXACT to "Exacte",
    MatchMode.CONTAINS to "Contient la phrase",
    MatchMode.REGEX to "Expression régulière (expert)"
)
