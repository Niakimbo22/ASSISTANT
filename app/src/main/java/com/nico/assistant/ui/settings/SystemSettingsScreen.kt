package com.nico.assistant.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nico.assistant.shizuku.ShizukuState

/**
 * Réglages système (spec §6.4) : le guide Shizuku s'adapte à l'état courant, et le service
 * d'accessibilité a son propre raccourci.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    viewModel: SystemSettingsViewModel,
    onBack: () -> Unit,
    onOpenLegacySettings: () -> Unit
) {
    val state by viewModel.shizukuState.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val context = LocalContext.current

    // L'état peut avoir changé pendant qu'on était ailleurs (Shizuku relancé, autorisation
    // accordée depuis une autre app).
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages système") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.isReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (state.isReady) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "  Shizuku — ${state.title}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = state.advice,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        when (state) {
                            ShizukuState.PERMISSION_NEEDED, ShizukuState.PERMISSION_DENIED ->
                                Button(onClick = viewModel::requestPermission) { Text("Autoriser") }

                            ShizukuState.READY ->
                                Button(onClick = viewModel::test) { Text("Tester") }

                            else -> OutlinedButton(onClick = viewModel::refresh) { Text("Revérifier") }
                        }
                        OutlinedButton(onClick = viewModel::refresh) { Text("Actualiser") }
                    }

                    testResult?.let { result ->
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Text(
                        text = "Sans Shizuku, les actions système se rabattent sur l'écran de " +
                            "réglages correspondant : rien ne casse, ça demande juste un tap.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Service d'accessibilité", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (viewModel.accessibilityEnabled) {
                            "Actif : l'action « Piloter l'app musique » peut fonctionner."
                        } else {
                            "Inactif : l'action « Piloter l'app musique » échouera proprement."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Ouvrir les réglages d'accessibilité") }
                }
            }

            OutlinedButton(
                onClick = onOpenLegacySettings,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Réglages de l'assistant (app musique, écoute auto)") }
        }
    }
}
