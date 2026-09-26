package com.nico.assistant.ui.list

import com.nico.assistant.action.ActionType
import com.nico.assistant.action.impl.SetBrightnessAction
import com.nico.assistant.action.impl.SetVolumeAction
import com.nico.assistant.action.impl.SpeakAction
import com.nico.assistant.action.impl.ToggleTorchAction
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import java.util.UUID

/**
 * Modèles proposés quand la liste est vide : une automatisation complète, créée en un tap.
 *
 * Ce ne sont que des données passées au dépôt comme n'importe quelle automatisation créée à
 * la main : aucune n'est connue du moteur, et chacune se modifie ensuite dans l'éditeur.
 */
object AutomationTemplates {

    private const val STATE = "state"
    private const val ON = "on"

    fun all(): List<Automation> = listOf(
        Automation(
            name = "Bonne nuit",
            phrases = listOf("bonne nuit", "je vais dormir"),
            actions = listOf(
                ActionSpec(type = ActionType.TOGGLE_DND, params = mapOf(STATE to ON)),
                ActionSpec(type = ActionType.SET_VOLUME, params = mapOf(SetVolumeAction.PARAM_LEVEL to "0")),
                ActionSpec(type = ActionType.SET_BRIGHTNESS, params = mapOf(SetBrightnessAction.PARAM_LEVEL to "10")),
                ActionSpec(type = ActionType.SPEAK, params = mapOf(SpeakAction.PARAM_TEXT to "Bonne nuit"))
            )
        ),
        Automation(
            name = "Lampe torche",
            phrases = listOf("lampe torche", "allume la lampe"),
            actions = listOf(
                ActionSpec(
                    type = ActionType.TOGGLE_TORCH,
                    params = mapOf(ToggleTorchAction.PARAM_STATE to ToggleTorchAction.STATE_TOGGLE)
                )
            )
        ),
        Automation(
            name = "Quelle heure",
            phrases = listOf("quelle heure il est", "il est quelle heure"),
            actions = listOf(
                ActionSpec(type = ActionType.SPEAK, params = mapOf(SpeakAction.PARAM_TEXT to "Il est {heure}"))
            )
        )
    )

    /** Copie neuve : identifiants frais pour l'automatisation comme pour ses actions. */
    fun instantiate(template: Automation): Automation = template.copy(
        id = UUID.randomUUID().toString(),
        createdAt = System.currentTimeMillis(),
        actions = template.actions.map { it.copy(id = UUID.randomUUID().toString()) }
    )
}
