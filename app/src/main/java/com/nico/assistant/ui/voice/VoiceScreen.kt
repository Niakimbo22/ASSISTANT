package com.nico.assistant.ui.voice

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nico.assistant.core.pipeline.AssistantState

/**
 * Écran d'écoute V2 (spec §7.5, version in-app).
 *
 * La fenêtre flottante par-dessus les autres apps attend le lot 8, avec la tuile Quick
 * Settings et le widget qui en ont vraiment besoin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    viewModel: VoiceViewModel,
    onBack: () -> Unit,
    onCreateAutomation: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Ouvrir cet écran, c'est vouloir parler : on démarre l'écoute sans tap supplémentaire.
    LaunchedEffect(Unit) {
        if (viewModel.state.value is AssistantState.Idle) viewModel.listen()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Écoute") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MicButton(state = state, onClick = viewModel::listen)

            Text(
                text = headline(state),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )

            detail(state)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            when (val current = state) {
                is AssistantState.Choosing -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (choice in current.choices) {
                        Button(
                            onClick = { viewModel.choose(choice, current.heard) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(choice.automation.name)
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Aucun des deux") }
                }

                is AssistantState.NotUnderstood -> Button(
                    onClick = { onCreateAutomation(current.heard) },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                ) {
                    Text("Créer une automatisation pour « ${current.heard} »")
                }

                is AssistantState.Done -> Column(modifier = Modifier.padding(top = 16.dp)) {
                    for ((index, outcome) in current.report.outcomes.withIndex()) {
                        val mark = if (outcome.succeeded) "✓" else "✗"
                        Text(
                            text = "$mark ${index + 1}. ${outcome.spec.type.name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun MicButton(state: AssistantState, onClick: () -> Unit) {
    val level = (state as? AssistantState.Listening)?.level ?: 0f
    // onRmsChanged va de -2 à 10 dB environ : on le ramène à une échelle visuelle.
    val target = 1f + (level.coerceIn(0f, 10f) / 10f) * 0.35f
    val scale by animateFloatAsState(targetValue = target, label = "niveau sonore")

    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(if (state is AssistantState.Listening) scale else 1f)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(96.dp)) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Écouter",
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

private fun headline(state: AssistantState): String = when (state) {
    AssistantState.Idle -> "Touche le micro"
    is AssistantState.Listening -> "Je t'écoute…"
    is AssistantState.Thinking -> "Je cherche…"
    is AssistantState.Choosing -> "Tu voulais dire ?"
    is AssistantState.Running -> state.name
    is AssistantState.Done -> state.report.feedbackText()
    is AssistantState.NotUnderstood -> "J'ai pas compris"
    is AssistantState.Failed -> state.message
}

private fun detail(state: AssistantState): String? = when (state) {
    is AssistantState.Listening -> state.partial.takeIf { it.isNotBlank() }
    is AssistantState.Thinking -> "« ${state.heard} »"
    is AssistantState.Choosing -> "« ${state.heard} »"
    is AssistantState.NotUnderstood -> "« ${state.heard} »"
    else -> null
}
