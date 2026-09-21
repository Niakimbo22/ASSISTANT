package com.nico.assistant.data.seed

import android.content.Context
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.impl.MediaControlAction
import com.nico.assistant.action.impl.PlayMusicSearchAction
import com.nico.assistant.action.impl.ToggleTorchAction
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import com.nico.assistant.prefs.Prefs

/**
 * Automatisations livrées au premier lancement (spec §12).
 *
 * Elles reproduisent les commandes de la V1 **sans une ligne de code spécifique** : si elles
 * fonctionnent, c'est que l'architecture tient. Et contrairement à la V1, elles se modifient
 * depuis le téléphone.
 */
object SeedAutomations {

    /**
     * @param musicPackage l'app musique déjà choisie en V1, s'il y en a une. Laissée vide,
     * l'automatisation « Musique » reste à compléter dans l'éditeur — aucun paquet n'est
     * jamais codé en dur, l'APK RVX étant sideloadé.
     */
    fun all(musicPackage: String? = null): List<Automation> = listOf(
        Automation(
            name = "Lancer une app",
            phrases = listOf("ouvre {app}", "lance {app}", "démarre {app}"),
            priority = 1,
            actions = listOf(
                ActionSpec(
                    type = ActionType.LAUNCH_APP,
                    params = mapOf(LaunchParams.PACKAGE to "{app}")
                )
            )
        ),
        Automation(
            name = "Musique",
            phrases = listOf("mets {titre}", "joue {titre}", "écoute {titre}"),
            feedbackText = "Je mets {titre}",
            actions = listOf(
                // Stratégie A puis B : deux actions dans une liste, plus un `if` dans le code.
                ActionSpec(
                    type = ActionType.PLAY_MUSIC_SEARCH,
                    params = mapOf(
                        PlayMusicSearchAction.PARAM_APP to musicPackage.orEmpty(),
                        PlayMusicSearchAction.PARAM_QUERY to "{titre}"
                    ),
                    critical = false
                ),
                ActionSpec(
                    type = ActionType.PLAY_MUSIC_UI,
                    params = mapOf(
                        PlayMusicSearchAction.PARAM_APP to musicPackage.orEmpty(),
                        PlayMusicSearchAction.PARAM_QUERY to "{titre}"
                    ),
                    critical = false,
                    delayMsBefore = 800
                )
            )
        ),
        Automation(
            name = "Appeler",
            phrases = listOf("appelle {contact}", "téléphone à {contact}"),
            actions = listOf(
                ActionSpec(
                    type = ActionType.CALL_NUMBER,
                    params = mapOf(CallParams.CONTACT to "{contact}")
                )
            )
        ),
        Automation(
            name = "Quelle heure",
            phrases = listOf("quelle heure il est", "il est quelle heure"),
            actions = listOf(
                ActionSpec(
                    type = ActionType.SPEAK,
                    params = mapOf(SpeakParams.TEXT to "Il est {heure}")
                )
            )
        ),
        Automation(
            name = "Lampe",
            phrases = listOf("allume la lampe", "lampe torche", "éteins la lampe"),
            actions = listOf(
                ActionSpec(
                    type = ActionType.TOGGLE_TORCH,
                    params = mapOf(ToggleTorchAction.PARAM_STATE to ToggleTorchAction.STATE_TOGGLE)
                )
            )
        ),
        Automation(
            name = "Pause musique",
            phrases = listOf("pause", "stop la musique", "coupe la musique"),
            actions = listOf(
                ActionSpec(
                    type = ActionType.MEDIA_CONTROL,
                    params = mapOf(MediaControlAction.PARAM_COMMAND to MediaControlAction.COMMAND_PAUSE)
                )
            )
        ),
        Automation(
            name = "Mode sortie",
            phrases = listOf("je pars", "je m'en vais"),
            feedbackText = "Bonne route",
            actions = listOf(
                // TOGGLE_WIFI arrive au lot 6 : d'ici là l'échec est journalisé, pas fatal.
                ActionSpec(type = ActionType.TOGGLE_WIFI, params = mapOf("state" to "off")),
                ActionSpec(type = ActionType.SPEAK, params = mapOf(SpeakParams.TEXT to "Bonne route"))
            )
        ),
        Automation(
            name = "Mode nuit",
            phrases = listOf("bonne nuit", "je vais dormir"),
            actions = listOf(
                ActionSpec(type = ActionType.TOGGLE_DND, params = mapOf("state" to "on")),
                ActionSpec(type = ActionType.SET_VOLUME, params = mapOf("level" to "0")),
                ActionSpec(type = ActionType.SET_BRIGHTNESS, params = mapOf("level" to "10")),
                ActionSpec(type = ActionType.SPEAK, params = mapOf(SpeakParams.TEXT to "Bonne nuit"))
            )
        )
    )

    /** Reprend l'app musique déjà choisie en V1, pour ne rien faire reconfigurer. */
    fun forDevice(context: Context): List<Automation> =
        all(runCatching { Prefs(context).musicPackage }.getOrNull())

    private object LaunchParams {
        const val PACKAGE = "package"
    }

    private object CallParams {
        const val CONTACT = "contact"
    }

    private object SpeakParams {
        const val TEXT = "text"
    }
}
