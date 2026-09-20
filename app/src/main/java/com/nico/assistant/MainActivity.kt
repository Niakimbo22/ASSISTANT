package com.nico.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nico.assistant.ui.AssistantViewModel
import com.nico.assistant.ui.MainScreen
import com.nico.assistant.ui.NicoAssistantTheme
import com.nico.assistant.ui.SettingsScreen
import com.nico.assistant.ui.editor.EditorScreen
import com.nico.assistant.ui.editor.EditorViewModel
import com.nico.assistant.ui.list.AutomationListScreen
import com.nico.assistant.ui.list.AutomationListViewModel

/**
 * Activité unique de l'appli (Compose). Elle est lancée par le double-appui sur
 * le bouton power (configuré par l'utilisateur dans Réglages > Système > Gestes).
 * launchMode=singleTask (voir manifeste) pour réutiliser l'instance existante.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NicoAssistantTheme {
                AppRoot()
            }
        }
    }
}

/**
 * Navigation minimale. L'accueil est désormais la liste des automatisations : c'est le
 * catalogue qui remplace les commandes codées en dur de la V1.
 *
 * VOICE reste l'écran d'écoute de la V1 ; il sera rebranché sur le moteur V2 au lot 5.
 */
private enum class Screen { AUTOMATIONS, EDITOR, VOICE, SETTINGS }

@Composable
private fun AppRoot() {
    val vm: AssistantViewModel = viewModel()
    val listViewModel: AutomationListViewModel = viewModel()
    val editorViewModel: EditorViewModel = viewModel()
    var screen by remember { mutableStateOf(Screen.AUTOMATIONS) }

    // On demande d'emblée les permissions runtime nécessaires, avec un motif
    // implicite (micro pour écouter, contacts + téléphone pour les appels).
    var permissionsRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Si le micro est accordé et que l'écoute auto est active, on démarre.
        val micGranted = result[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted && vm.state.autoListen) {
            vm.startListening()
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            permissionsRequested = true
            val ctx = vm.getApplication<android.app.Application>()
            val needed = listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
            )
            val toRequest = needed.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            if (toRequest.isEmpty()) {
                // Tout est déjà accordé : écoute auto immédiate si activée.
                if (vm.state.autoListen) vm.startListening()
            } else {
                permissionLauncher.launch(toRequest.toTypedArray())
            }
        }
    }

    when (screen) {
        Screen.AUTOMATIONS -> AutomationListScreen(
            viewModel = listViewModel,
            onCreate = {
                editorViewModel.load(null)
                screen = Screen.EDITOR
            },
            onEdit = { automation ->
                editorViewModel.load(automation.id)
                screen = Screen.EDITOR
            },
            onOpenVoice = { screen = Screen.VOICE },
            onOpenSettings = { screen = Screen.SETTINGS },
        )

        Screen.EDITOR -> {
            BackHandler { screen = Screen.AUTOMATIONS }
            EditorScreen(
                viewModel = editorViewModel,
                onBack = { screen = Screen.AUTOMATIONS },
            )
        }

        Screen.VOICE -> {
            BackHandler { screen = Screen.AUTOMATIONS }
            MainScreen(
                vm = vm,
                onOpenSettings = { screen = Screen.SETTINGS },
            )
        }

        Screen.SETTINGS -> SettingsScreen(
            vm = vm,
            onBack = { screen = Screen.AUTOMATIONS },
        )
    }
}
