package com.nico.assistant.ui.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nico.assistant.update.ReleaseInfo
import com.nico.assistant.update.UpdateUiState
import com.nico.assistant.update.UpdateViewModel

/**
 * Popup proposée à l'ouverture quand la vérification silencieuse a trouvé mieux.
 * Elle ne s'affiche que s'il y a vraiment quelque chose à installer.
 */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel) {
    val visible by viewModel.showPrompt.collectAsState()
    val state by viewModel.state.collectAsState()
    if (!visible) return

    // « Plus tard » ferme la popup ; le même état reste disponible dans les Réglages.
    AlertDialog(
        onDismissRequest = viewModel::dismissPrompt,
        title = { Text("Mise à jour disponible") },
        text = {
            Column {
                UpdateStateBody(
                    state = state,
                    viewModel = viewModel,
                    showUpToDate = false,
                )
            }
        },
        confirmButton = {
            UpdatePrimaryAction(state = state, viewModel = viewModel)
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissPrompt) { Text("Plus tard") }
        },
    )
}

/**
 * Carte des Réglages : version installée, bouton de vérification, et la suite
 * du parcours (télécharger, autoriser, installer) au même endroit.
 */
@Composable
fun UpdateCard(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mise à jour de l'application", style = MaterialTheme.typography.titleMedium)

            Text(
                text = "Version installée : ${viewModel.currentVersionLabel}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Les builds sont publiés en release GitHub sur " +
                    "${ReleaseInfo.REPOSITORY} : l'APK se télécharge sans compte.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::checkNow,
                    enabled = state !is UpdateUiState.Checking &&
                        state !is UpdateUiState.Downloading,
                ) { Text("Vérifier les mises à jour") }

                UpdatePrimaryAction(state = state, viewModel = viewModel)
            }

            UpdateStateBody(state = state, viewModel = viewModel, showUpToDate = true)
        }
    }
}

/**
 * Le texte d'état, commun à la popup et à la carte : c'est lui qui dit ce qui se
 * passe, y compris quand ça échoue — sur un téléphone sans câble, ce message est
 * le seul diagnostic disponible.
 */
@Composable
private fun UpdateStateBody(
    state: UpdateUiState,
    viewModel: UpdateViewModel,
    showUpToDate: Boolean,
) {
    when (state) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> StatusText("Vérification en cours…")

        is UpdateUiState.UpToDate -> if (showUpToDate) {
            StatusText("À jour — build ${state.buildNumber} est le dernier publié.")
        }

        is UpdateUiState.Available -> Column {
            StatusText(
                "Build ${state.release.buildNumber} disponible " +
                    "(${state.release.readableSize}). Tu es en ${viewModel.currentVersionLabel}."
            )
            if (state.release.notes.isNotBlank()) {
                Text(
                    text = state.release.notes.lines().take(6).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        is UpdateUiState.Downloading -> Column(Modifier.padding(top = 8.dp)) {
            StatusText(
                if (state.progress < 0f) {
                    "Téléchargement du build ${state.release.buildNumber}…"
                } else {
                    "Téléchargement — ${(state.progress * 100).toInt()} %"
                }
            )
            Spacer(Modifier.height(8.dp))
            // Progression indéterminée tant que GitHub n'a pas annoncé la taille.
            if (state.progress < 0f) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is UpdateUiState.ReadyToInstall -> Column {
            StatusText("Build ${state.release.buildNumber} téléchargé, prêt à installer.")
            // Deuxième voie, au cas où l'écran système ne s'ouvre pas tout seul.
            TextButton(onClick = viewModel::openSystemInstaller) {
                Text("Ouvrir l'installeur système")
            }
        }

        is UpdateUiState.PermissionRequired -> PermissionBlock(viewModel)

        is UpdateUiState.Installing -> Column {
            StatusText(
                "Installation du build ${state.release.buildNumber} — confirme sur " +
                    "l'écran système. L'app se ferme pendant la mise à jour."
            )
            TextButton(onClick = viewModel::openSystemInstaller) {
                Text("Rien ne s'affiche ? Ouvrir l'installeur système")
            }
        }

        is UpdateUiState.Failed -> Column {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = viewModel::reset) { Text("Effacer") }
        }
    }
}

/** Le bouton d'action qui change selon l'étape. Absent quand il n'y a rien à faire. */
@Composable
private fun UpdatePrimaryAction(state: UpdateUiState, viewModel: UpdateViewModel) {
    when (state) {
        is UpdateUiState.Available ->
            Button(onClick = viewModel::download) { Text("Télécharger") }

        is UpdateUiState.ReadyToInstall ->
            Button(onClick = viewModel::install) { Text("Installer") }

        is UpdateUiState.Installing ->
            Button(onClick = viewModel::install) { Text("Relancer l'installation") }

        else -> Unit
    }
}

/**
 * « Installer des applications inconnues » ne se demande pas comme une permission
 * runtime : il faut envoyer l'utilisateur sur un écran de réglages dédié. On
 * revérifie au retour pour enchaîner sans qu'il ait à retrouver le bouton.
 */
@Composable
private fun PermissionBlock(viewModel: UpdateViewModel) {
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermission() }

    Column {
        Text(
            text = "Android bloque l'installation tant que NicoAssistant n'a pas " +
                "l'autorisation « installer des applications inconnues ».",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = { settingsLauncher.launch(viewModel.unknownSourcesSettingsIntent()) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Autoriser l'installation") }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}
