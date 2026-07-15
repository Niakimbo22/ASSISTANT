package com.nico.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
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

/** Navigation minimale entre l'écran principal et les réglages. */
private enum class Screen { MAIN, SETTINGS }

@Composable
private fun AppRoot() {
    val vm: AssistantViewModel = viewModel()
    var screen by remember { mutableStateOf(Screen.MAIN) }

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
        Screen.MAIN -> MainScreen(
            vm = vm,
            onOpenSettings = { screen = Screen.SETTINGS },
        )
        Screen.SETTINGS -> SettingsScreen(
            vm = vm,
            onBack = { screen = Screen.MAIN },
        )
    }
}
