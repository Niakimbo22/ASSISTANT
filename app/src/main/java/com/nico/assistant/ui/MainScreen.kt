package com.nico.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: AssistantViewModel,
    onOpenSettings: () -> Unit,
) {
    val state = vm.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NicoAssistant") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Réglages")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // Indicateur micro : gros bouton central.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilledIconButton(
                    onClick = { if (state.isListening) vm.stopListening() else vm.startListening() },
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    colors = if (state.isListening) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        IconButtonDefaults.filledIconButtonColors()
                    },
                    enabled = state.speechAvailable,
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Micro",
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            Text(
                text = when {
                    !state.speechAvailable -> "Reconnaissance vocale indisponible"
                    state.isListening -> "À l'écoute…"
                    else -> "Touchez le micro et parlez"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            // Texte reconnu en direct.
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Texte reconnu",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.recognizedText.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    state.lastParsed?.let { p ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Intention : ${p.intent} • Argument : « ${p.argument} »",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Champ de saisie manuelle (mode debug) : exécuter sans parler.
            DebugCommandInput(onSubmit = { vm.handleCommand(it) })
        }
    }

    // Popup de retour (miroir du message vocal).
    state.dialog?.let { dialog ->
        FeedbackDialogView(
            dialog = dialog,
            onDismiss = { vm.dismissDialog() },
            onPickContact = { contact ->
                vm.dismissDialog()
                vm.placeCall(contact)
            },
        )
    }
}

@Composable
private fun DebugCommandInput(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Mode debug — taper une commande",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ex : lance du Werenoi") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onSubmit(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Exécuter")
            }
        }
    }
}

@Composable
private fun FeedbackDialogView(
    dialog: FeedbackDialog,
    onDismiss: () -> Unit,
    onPickContact: (com.nico.assistant.call.ContactMatch) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialog.title) },
        text = {
            Column {
                Text(dialog.message)
                dialog.contactChoices.forEach { contact ->
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onPickContact(contact) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${contact.displayName} — ${contact.number}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
