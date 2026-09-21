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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import com.nico.assistant.shizuku.ShizukuState
import com.nico.assistant.ui.update.UpdateCard
import com.nico.assistant.update.UpdateViewModel

/**
 * Réglages système (spec §6.4) : le guide Shizuku s'adapte à l'état courant, et le service
 * d'accessibilité a son propre raccourci.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(
    viewModel: SystemSettingsViewModel,
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit,
    onOpenLegacySettings: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val state by viewModel.shizukuState.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val thresholds by viewModel.thresholds.collectAsState()
    val transferMessage by viewModel.transferMessage.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

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
            UpdateCard(viewModel = updateViewModel)

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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Seuils de reconnaissance", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Plus le seuil de confiance est bas, plus l'app se lance sans " +
                            "demander — et plus elle se trompe.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    ThresholdSlider(
                        label = "Confiance",
                        value = thresholds.confident,
                        range = 0.5f..0.95f,
                        onChange = viewModel::setConfident
                    )
                    ThresholdSlider(
                        label = "Plancher (en dessous : j'ai pas compris)",
                        value = thresholds.ambiguousFloor,
                        range = 0.1f..0.7f,
                        onChange = viewModel::setAmbiguousFloor
                    )
                    ThresholdSlider(
                        label = "Écart minimum entre deux candidates",
                        value = thresholds.minimumGap,
                        range = 0.05f..0.4f,
                        onChange = viewModel::setMinimumGap
                    )

                    TextButton(onClick = viewModel::resetThresholds) {
                        Text("Revenir aux valeurs par défaut")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sauvegarde", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Exporte tes automatisations en JSON pour les garder, les " +
                            "partager, ou les remettre après une réinstallation.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("nicoassistant-automatisations.json") }
                        ) { Text("Exporter") }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                        ) { Text("Importer") }
                    }
                    transferMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onOpenLogs,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Journal d'exécution") }

            OutlinedButton(
                onClick = onOpenLegacySettings,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Réglages de l'assistant (app musique, écoute auto)") }
        }
    }
}

/** Un seuil de matching, avec sa valeur lisible : ils se règlent à tâtons, pas dans le code. */
@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
