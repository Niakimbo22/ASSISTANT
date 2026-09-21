package com.nico.assistant.core.condition

import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.data.db.ConditionType

/**
 * Schéma de paramètres des conditions, sur le même principe que celui des actions :
 * l'UI d'ajout de condition est générée, pas écrite à la main.
 */
object ConditionSpecs {

    fun label(type: ConditionType): String =
        ConditionEvaluator.CONDITION_LABELS[type] ?: type.name

    fun paramsOf(type: ConditionType): List<ParamSpec> = when (type) {
        ConditionType.TIME_RANGE -> listOf(
            ParamSpec(key = "start", label = "De (HH:mm)", type = ParamType.TEXT, default = "22:00"),
            ParamSpec(key = "end", label = "À (HH:mm)", type = ParamType.TEXT, default = "07:00")
        )

        ConditionType.DAY_OF_WEEK -> listOf(
            ParamSpec(
                key = "days",
                label = "Jours",
                type = ParamType.TEXT,
                default = "MON,TUE,WED,THU,FRI",
                hint = "Codes à trois lettres séparés par des virgules"
            )
        )

        ConditionType.WIFI_CONNECTED -> listOf(
            ParamSpec(
                key = "ssid",
                label = "Nom du réseau",
                type = ParamType.TEXT,
                required = false,
                hint = "Vide = n'importe quel réseau"
            )
        )

        ConditionType.BATTERY_BELOW -> listOf(
            ParamSpec(key = "level", label = "Seuil (%)", type = ParamType.NUMBER, default = "20")
        )

        ConditionType.BLUETOOTH_CONNECTED -> listOf(
            ParamSpec(
                key = "device",
                label = "Appareil",
                type = ParamType.TEXT,
                required = false,
                hint = "Vide = n'importe quel appareil"
            )
        )

        ConditionType.APP_FOREGROUND -> listOf(
            ParamSpec(key = "package", label = "Application", type = ParamType.APP_PICKER)
        )

        ConditionType.CHARGING, ConditionType.HEADPHONES_PLUGGED -> emptyList()
    }

    /** Valeurs par défaut d'une condition qu'on vient d'ajouter. */
    fun defaultsOf(type: ConditionType): Map<String, String> =
        paramsOf(type).mapNotNull { spec -> spec.default?.let { spec.key to it } }.toMap()

    /** Résumé affiché sous le nom de la condition dans l'éditeur. */
    fun summarize(condition: Condition): String {
        val details = condition.params.entries
            .filter { it.value.isNotBlank() }
            .joinToString(" · ") { "${it.key} = ${it.value}" }
        return if (condition.negated) {
            listOf("inversée", details).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            details
        }
    }
}
