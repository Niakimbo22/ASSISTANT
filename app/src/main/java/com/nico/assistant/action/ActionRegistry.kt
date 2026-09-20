package com.nico.assistant.action

import com.nico.assistant.action.impl.CallNumberAction
import com.nico.assistant.action.impl.LaunchAppAction
import com.nico.assistant.action.impl.MediaControlAction
import com.nico.assistant.action.impl.OpenUrlAction
import com.nico.assistant.action.impl.PlayMusicSearchAction
import com.nico.assistant.action.impl.PlayMusicUiAction
import com.nico.assistant.action.impl.SearchWebAction
import com.nico.assistant.action.impl.SendSmsAction
import com.nico.assistant.action.impl.ShowToastAction
import com.nico.assistant.action.impl.RunShellAction
import com.nico.assistant.action.impl.SetBrightnessAction
import com.nico.assistant.action.impl.SetVolumeAction
import com.nico.assistant.action.impl.SpeakAction
import com.nico.assistant.action.impl.ToggleAirplaneAction
import com.nico.assistant.action.impl.ToggleBluetoothAction
import com.nico.assistant.action.impl.ToggleDndAction
import com.nico.assistant.action.impl.ToggleRotationAction
import com.nico.assistant.action.impl.ToggleTorchAction
import com.nico.assistant.action.impl.ToggleWifiAction
import com.nico.assistant.action.impl.VibrateAction
import com.nico.assistant.action.impl.WaitAction

/**
 * Table type → implémentation (spec §5.4).
 *
 * Les types déclarés dans [ActionType] mais pas encore implémentés ne sont pas une erreur :
 * [find] renvoie `null` et l'executor journalise un échec explicite plutôt que de planter.
 */
object ActionRegistry {

    private val actions: Map<ActionType, Action> = listOf(
        // INTENT
        LaunchAppAction(),
        OpenUrlAction(),
        SearchWebAction(),
        CallNumberAction(),
        SendSmsAction(),
        PlayMusicSearchAction(),
        // ACCESSIBILITY
        PlayMusicUiAction(),
        // SHIZUKU, avec repli quand il n'est pas prêt
        ToggleWifiAction(),
        ToggleBluetoothAction(),
        ToggleDndAction(),
        ToggleAirplaneAction(),
        ToggleRotationAction(),
        SetBrightnessAction(),
        RunShellAction(),
        // INTERNAL
        MediaControlAction(),
        ToggleTorchAction(),
        SetVolumeAction(),
        SpeakAction(),
        VibrateAction(),
        WaitAction(),
        ShowToastAction()
    ).associateBy { it.type }

    /** Catalogue trié comme le sélecteur d'action l'affiche. */
    val all: List<Action> = actions.values.sortedWith(
        compareBy({ it.category.ordinal }, { it.label })
    )

    val implementedTypes: Set<ActionType> get() = actions.keys

    fun find(type: ActionType): Action? = actions[type]

    fun get(type: ActionType): Action = find(type) ?: error("Action non enregistrée : $type")

    fun isImplemented(type: ActionType): Boolean = actions.containsKey(type)

    /** Les actions dont le backend est utilisable ici et maintenant (spec §6.5). */
    fun availableFor(isAvailable: (Backend) -> Boolean): List<Action> =
        all.filter { isAvailable(it.backend) }

    fun byCategory(): Map<ActionCategory, List<Action>> = all.groupBy { it.category }
}
