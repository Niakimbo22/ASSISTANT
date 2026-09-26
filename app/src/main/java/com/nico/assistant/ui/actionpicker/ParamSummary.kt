package com.nico.assistant.ui.actionpicker

import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType

/** Valeurs d'un paramètre [ParamType.TOGGLE], partagées par le formulaire et les résumés. */
val ToggleOptions: List<Pair<String, String>> = listOf(
    "on" to "Activer",
    "off" to "Désactiver",
    "toggle" to "Basculer"
)

/**
 * Résumé lisible des paramètres d'une action ou d'une condition, dérivé de son schéma :
 * « Activer · 10 » plutôt que « state = on · level = 10 ».
 */
object ParamSummary {

    /**
     * @param appLabel résout un nom de paquet en nom d'app ; `null` si inconnu (slot, app
     * désinstallée).
     */
    fun of(
        schema: List<ParamSpec>,
        params: Map<String, String>,
        appLabel: (String) -> String? = { null },
        max: Int = 3
    ): String = schema
        .mapNotNull { spec ->
            params[spec.key]?.takeIf { it.isNotBlank() }?.let { display(spec, it, appLabel) }
        }
        .take(max)
        .joinToString(" · ")

    fun display(spec: ParamSpec, value: String, appLabel: (String) -> String? = { null }): String =
        when (spec.type) {
            ParamType.ENUM -> spec.options.firstOrNull { it.first == value }?.second ?: value
            ParamType.TOGGLE -> ToggleOptions.firstOrNull { it.first.equals(value, ignoreCase = true) }?.second ?: value
            ParamType.APP_PICKER -> appLabel(value) ?: value
            ParamType.AUTOMATION_PICKER -> "une autre automatisation"
            else -> value
        }
}
