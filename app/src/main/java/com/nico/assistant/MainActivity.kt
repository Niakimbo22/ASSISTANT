package com.nico.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nico.assistant.a11y.MusicAccessibilityService
import com.nico.assistant.action.Backend
import com.nico.assistant.core.pipeline.AssistantState
import com.nico.assistant.shizuku.ShizukuManager
import com.nico.assistant.ui.theme.LocalHazeState
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoMotion
import com.nico.assistant.ui.voice.ListeningIsland
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nico.assistant.ui.AssistantViewModel
import com.nico.assistant.ui.theme.NicoAssistantTheme
import com.nico.assistant.ui.SettingsScreen
import com.nico.assistant.ui.editor.EditorScreen
import com.nico.assistant.ui.editor.EditorViewModel
import com.nico.assistant.ui.list.AutomationListScreen
import com.nico.assistant.ui.list.AutomationListViewModel
import com.nico.assistant.ui.logs.LogsScreen
import com.nico.assistant.ui.logs.LogsViewModel
import com.nico.assistant.ui.settings.SystemSettingsScreen
import com.nico.assistant.ui.settings.SystemSettingsViewModel
import com.nico.assistant.ui.voice.VoiceViewModel

/**
 * Activité unique de l'appli (Compose). Elle est lancée par le double-appui sur
 * le bouton power (configuré par l'utilisateur dans Réglages > Système > Gestes).
 * launchMode=singleTask (voir manifeste) pour réutiliser l'instance existante.
 */
class MainActivity : ComponentActivity() {

    /** Posé par la tuile, le widget ou le raccourci : ouvre directement l'écoute. */
    private val listenRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bord à bord : le fond animé passe sous les barres système, et chaque écran gère ses
        // marges (WindowInsets) pour que rien ne soit coupé.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Pas de voile gris derrière la barre de gestes : le verre du dock suffit.
            window.isNavigationBarContrastEnforced = false
        }
        consume(intent)
        setContent {
            NicoAssistantTheme {
                AppRoot(listenRequested)
            }
        }
    }

    // launchMode=singleTask : les déclenchements suivants arrivent ici, pas dans onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        if (intent?.action == ACTION_LISTEN) listenRequested.value = true
    }

    companion object {
        const val ACTION_LISTEN = "com.nico.assistant.action.LISTEN"
    }
}

/**
 * Navigation : une pile d'écrans, animée sur ressort. L'accueil est la liste des
 * automatisations : c'est le catalogue qui remplace les commandes codées en dur de la V1.
 *
 * L'écoute n'est plus un écran : c'est une pastille flottante (façon Dynamic Island) posée
 * au-dessus de n'importe quel écran.
 */
private enum class Screen(val depth: Int) {
    AUTOMATIONS(0),
    EDITOR(1),
    SYSTEM_SETTINGS(1),
    LOGS(2),
    SETTINGS(2)
}

@Composable
private fun AppRoot(listenRequested: MutableState<Boolean>) {
    val vm: AssistantViewModel = viewModel()
    val listViewModel: AutomationListViewModel = viewModel()
    val editorViewModel: EditorViewModel = viewModel()
    val voiceViewModel: VoiceViewModel = viewModel()
    val systemSettingsViewModel: SystemSettingsViewModel = viewModel()
    val logsViewModel: LogsViewModel = viewModel()

    val voiceState by voiceViewModel.state.collectAsState()
    val shizukuState by ShizukuManager.shared().state.collectAsState()
    val context = LocalContext.current

    var backStack by remember { mutableStateOf(listOf(Screen.AUTOMATIONS)) }
    val screen = backStack.last()
    fun push(target: Screen) {
        backStack = backStack + target
    }
    fun pop() {
        if (backStack.size > 1) backStack = backStack.dropLast(1)
    }
    fun listen() {
        voiceViewModel.listen()
    }

    // Déclenchement externe (tuile, widget, raccourci) : la pastille d'écoute s'ouvre par-dessus.
    LaunchedEffect(listenRequested.value) {
        if (listenRequested.value) {
            listenRequested.value = false
            listen()
        }
    }

    // On demande d'emblée les permissions runtime nécessaires, avec un motif
    // implicite (micro pour écouter, contacts + téléphone pour les appels).
    var permissionsRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Si le micro est accordé et que l'écoute auto est active, on écoute.
        val micGranted = result[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted && vm.state.autoListen) listen()
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
                if (vm.state.autoListen) listen()
            } else {
                permissionLauncher.launch(toRequest.toTypedArray())
            }
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }
    // Déclaré après : quand la pastille est ouverte, « retour » la ferme d'abord.
    BackHandler(enabled = voiceState !is AssistantState.Idle) { voiceViewModel.reset() }

    // Disponibilité réelle des backends, pour griser les actions impossibles dans le sélecteur.
    val isBackendAvailable: (Backend) -> Boolean = remember(shizukuState) {
        { backend ->
            when (backend) {
                Backend.INTENT, Backend.INTERNAL -> true
                Backend.SHIZUKU -> shizukuState.isReady
                Backend.ACCESSIBILITY -> MusicAccessibilityService.isEnabled(context)
            }
        }
    }

    val rootHaze = remember { HazeState() }

    Box(modifier = Modifier.fillMaxSize().background(NicoColors.Void)) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(rootHaze)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = targetState.depth > initialState.depth
                    val backward = targetState.depth < initialState.depth
                    when {
                        forward -> (slideInHorizontally(NicoMotion.gentle()) { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally(NicoMotion.gentle()) { -it / 8 } + fadeOut())
                        backward -> (slideInHorizontally(NicoMotion.gentle()) { -it / 8 } + fadeIn()) togetherWith
                            (slideOutHorizontally(NicoMotion.gentle()) { it / 4 } + fadeOut())
                        else -> fadeIn() togetherWith fadeOut()
                    }
                },
                label = "navigation"
            ) { current ->
                when (current) {
                    Screen.AUTOMATIONS -> AutomationListScreen(
                        viewModel = listViewModel,
                        listening = voiceState is AssistantState.Listening,
                        voiceLevel = (voiceState as? AssistantState.Listening)?.level ?: 0f,
                        onCreate = {
                            editorViewModel.load(null)
                            push(Screen.EDITOR)
                        },
                        onEdit = { automation ->
                            editorViewModel.load(automation.id)
                            push(Screen.EDITOR)
                        },
                        onMic = {
                            if (voiceState is AssistantState.Idle) listen() else voiceViewModel.reset()
                        },
                        onOpenSettings = { push(Screen.SYSTEM_SETTINGS) },
                        onOpenLogs = { push(Screen.LOGS) },
                    )

                    Screen.EDITOR -> EditorScreen(
                        viewModel = editorViewModel,
                        isBackendAvailable = isBackendAvailable,
                        onBack = { pop() },
                    )

                    Screen.SYSTEM_SETTINGS -> SystemSettingsScreen(
                        viewModel = systemSettingsViewModel,
                        onBack = { pop() },
                        onOpenLegacySettings = { push(Screen.SETTINGS) },
                        onOpenLogs = { push(Screen.LOGS) },
                    )

                    Screen.LOGS -> LogsScreen(
                        viewModel = logsViewModel,
                        onBack = { pop() },
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        vm = vm,
                        onBack = { pop() },
                    )
                }
            }
        }

        CompositionLocalProvider(LocalHazeState provides rootHaze) {
            ListeningIsland(
                state = voiceState,
                onStop = voiceViewModel::reset,
                onRetry = { listen() },
                onChoose = voiceViewModel::choose,
                // C'est comme ça que le catalogue se construit à l'usage : la phrase
                // non reconnue ouvre l'éditeur déjà pré-rempli.
                onCreateAutomation = { phrase ->
                    voiceViewModel.reset()
                    editorViewModel.load(null, initialPhrase = phrase)
                    if (screen != Screen.EDITOR) push(Screen.EDITOR)
                },
            )
        }
    }
}
