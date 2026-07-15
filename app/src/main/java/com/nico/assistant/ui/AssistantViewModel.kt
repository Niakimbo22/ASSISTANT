package com.nico.assistant.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.nico.assistant.apps.AppEntry
import com.nico.assistant.apps.AppRepository
import com.nico.assistant.call.CallController
import com.nico.assistant.call.ContactMatch
import com.nico.assistant.music.MusicController
import com.nico.assistant.music.MusicResult
import com.nico.assistant.parser.CommandParser
import com.nico.assistant.parser.Intent as CmdIntent
import com.nico.assistant.parser.ParsedCommand
import com.nico.assistant.prefs.Prefs
import com.nico.assistant.tts.TtsManager
import com.nico.assistant.voice.SpeechManager

/** Boîte de dialogue de retour affichée à l'écran (miroir du message vocal). */
data class FeedbackDialog(
    val title: String,
    val message: String,
    val contactChoices: List<ContactMatch> = emptyList(),
)

/** État immuable rendu par l'UI Compose. */
data class UiState(
    val isListening: Boolean = false,
    val recognizedText: String = "",
    val lastParsed: ParsedCommand? = null,
    val dialog: FeedbackDialog? = null,
    val autoListen: Boolean = true,
    val musicPackage: String? = null,
    val musicPackageLabel: String? = null,
    val installedApps: List<AppEntry> = emptyList(),
    val speechAvailable: Boolean = true,
)

/**
 * ViewModel central : reconnaissance vocale -> parsing local -> exécution
 * (musique / appel / ouverture d'appli) -> retour texte + voix + popup.
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val tts = TtsManager(app)
    private val speech = SpeechManager(app)
    private val apps = AppRepository(app)
    private val music = MusicController(app)
    private val calls = CallController(app)

    var state by mutableStateOf(
        UiState(
            autoListen = prefs.autoListen,
            musicPackage = prefs.musicPackage,
            speechAvailable = speech.isAvailable,
        )
    )
        private set

    init {
        speech.onStateChange = { listening ->
            state = state.copy(isListening = listening)
        }
        speech.onPartial = { text ->
            state = state.copy(recognizedText = text)
        }
        speech.onFinal = { text ->
            state = state.copy(recognizedText = text)
            handleCommand(text)
        }
        speech.onError = { msg ->
            state = state.copy(isListening = false)
            showDialog("Erreur", msg, speak = true)
        }
        refreshMusicLabel()
    }

    // --- Reconnaissance vocale ------------------------------------------------

    fun startListening() {
        state = state.copy(recognizedText = "")
        speech.startListening()
    }

    fun stopListening() = speech.stopListening()

    // --- Exécution d'une commande (vocale OU tapée en mode debug) -------------

    fun handleCommand(rawText: String) {
        if (rawText.isBlank()) return
        val parsed = CommandParser.parse(rawText, apps.labels())
        state = state.copy(lastParsed = parsed)
        Log.i(TAG, "Commande: \"$rawText\" -> intent=${parsed.intent} arg=\"${parsed.argument}\"")

        when (parsed.intent) {
            CmdIntent.MUSIC -> executeMusic(parsed.argument)
            CmdIntent.CALL -> executeCall(parsed.argument)
            CmdIntent.OPEN_APP -> executeOpenApp(parsed.argument)
            CmdIntent.UNKNOWN -> showDialog(
                "Commande non comprise",
                "Je n'ai pas compris « $rawText ».",
                speak = true,
            )
        }
    }

    private fun executeMusic(query: String) {
        if (query.isBlank()) {
            showDialog("Musique", "Que veux-tu écouter ?", speak = true)
            return
        }
        when (music.play(query)) {
            is MusicResult.Launched ->
                showDialog("Musique", "Je lance $query", speak = true)
            MusicResult.NoAppSelected ->
                showDialog(
                    "Aucune appli musique",
                    "Choisis d'abord ton appli de musique dans les réglages.",
                    speak = true,
                )
            MusicResult.LaunchFailed ->
                showDialog("Musique", "Impossible de lancer l'application musique.", speak = true)
        }
    }

    private fun executeCall(contact: String) {
        if (contact.isBlank()) {
            showDialog("Appel", "Qui veux-tu appeler ?", speak = true)
            return
        }
        val matches = try {
            calls.findContacts(contact)
        } catch (e: SecurityException) {
            showDialog("Appel", "Permission Contacts manquante.", speak = true)
            return
        }
        when {
            matches.isEmpty() ->
                showDialog("Appel", "Contact introuvable", speak = true)
            matches.size == 1 -> placeCall(matches.first())
            else -> // Plusieurs correspondances : on demande de choisir.
                state = state.copy(
                    dialog = FeedbackDialog(
                        title = "Plusieurs contacts",
                        message = "Qui veux-tu appeler ?",
                        contactChoices = matches.take(6),
                    )
                )
        }
    }

    /** Appel effectif d'un contact choisi (ou unique). */
    fun placeCall(match: ContactMatch) {
        try {
            calls.placeCall(match.number)
            showDialog("Appel", "J'appelle ${match.displayName}", speak = true)
        } catch (e: SecurityException) {
            showDialog("Appel", "Permission Téléphone manquante.", speak = true)
        }
    }

    private fun executeOpenApp(name: String) {
        if (name.isBlank()) {
            showDialog("Ouvrir", "Quelle application veux-tu ouvrir ?", speak = true)
            return
        }
        val match = apps.findBestMatch(name)
        if (match == null) {
            showDialog("Ouvrir", "Application introuvable", speak = true)
            return
        }
        val launch = apps.launchIntentFor(match.packageName)
        if (launch == null) {
            showDialog("Ouvrir", "Impossible d'ouvrir ${match.label}.", speak = true)
            return
        }
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(launch)
        showDialog("Ouvrir", "J'ouvre ${match.label}", speak = true)
    }

    // --- Réglages -------------------------------------------------------------

    fun loadInstalledApps() {
        state = state.copy(installedApps = apps.listLaunchableApps())
    }

    fun setAutoListen(enabled: Boolean) {
        prefs.autoListen = enabled
        state = state.copy(autoListen = enabled)
    }

    fun selectMusicApp(entry: AppEntry) {
        prefs.musicPackage = entry.packageName
        // Trace demandée pour le débogage sur l'appli RVX sideloadée.
        Log.i(TAG, "Appli musique sélectionnée : ${entry.label} -> ${entry.packageName}")
        state = state.copy(
            musicPackage = entry.packageName,
            musicPackageLabel = entry.label,
        )
    }

    private fun refreshMusicLabel() {
        val pkg = prefs.musicPackage ?: return
        state = state.copy(musicPackageLabel = apps.labelForPackage(pkg))
    }

    // --- Popup + voix ---------------------------------------------------------

    private fun showDialog(title: String, message: String, speak: Boolean) {
        state = state.copy(dialog = FeedbackDialog(title, message))
        if (speak) tts.speak(message)
    }

    fun dismissDialog() {
        state = state.copy(dialog = null)
    }

    override fun onCleared() {
        super.onCleared()
        speech.destroy()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "NICO_VM"
    }
}
