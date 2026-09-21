package com.nico.assistant.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.data.model.Automation

/**
 * Écran d'accueil de la V2 : la liste des automatisations (spec §7.2).
 *
 * C'est le catalogue que Nico construit à l'usage ; aucune commande n'est codée en dur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationListScreen(
    viewModel: AutomationListViewModel,
    onCreate: () -> Unit,
    onEdit: (Automation) -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val automations by viewModel.automations.collectAsState()
    val query by viewModel.query.collectAsState()
    val deleted by viewModel.recentlyDeleted.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(deleted) {
        val automation = deleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "« ${automation.name} » supprimée",
            actionLabel = "Annuler"
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automatisations") },
                actions = {
                    IconButton(onClick = onOpenVoice) {
                        Icon(Icons.Filled.Mic, contentDescription = "Écouter")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = "Nouvelle automatisation")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Rechercher") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (automations.isEmpty()) {
                EmptyState(hasQuery = query.isNotBlank(), onCreate = onCreate)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(automations, key = { it.id }) { automation ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAutomationCard(
    automation: Automation,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        AutomationCard(automation, onClick, onToggle)
    }
}

@Composable
private fun AutomationCard(
    automation: Automation,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = automation.name.ifBlank { "Sans nom" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    automation.phrases.firstOrNull()?.let { phrase ->
                        Text(
                            text = "« $phrase »",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (automation.runCount > 0) {
                    Text(
                        text = "${automation.runCount}×",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Switch(checked = automation.enabled, onCheckedChange = onToggle)
            }

            if (automation.actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (spec in automation.actions.take(MAX_CHIPS)) {
                        AssistChip(
                            onClick = onClick,
                            label = {
                                Text(
                                    text = ActionRegistry.find(spec.type)?.label ?: spec.type.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors()
                        )
                    }
                    if (automation.actions.size > MAX_CHIPS) {
                        Text(
                            text = "+${automation.actions.size - MAX_CHIPS}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean, onCreate: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (hasQuery) "Aucun résultat" else "Aucune automatisation",
                style = MaterialTheme.typography.titleMedium
            )
            if (!hasQuery) {
                Text(
                    text = "Crée la première : une phrase, une action, et c'est prêt.",
                    style = MaterialTheme.typography.bodyMedium
                )
                AssistChip(
                    onClick = onCreate,
                    label = { Text("Créer une automatisation") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                )
            }
        }
    }
}

private const val MAX_CHIPS = 3
