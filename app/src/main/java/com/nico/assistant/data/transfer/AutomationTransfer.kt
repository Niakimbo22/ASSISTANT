package com.nico.assistant.data.transfer

import com.nico.assistant.action.ActionType
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.ConditionType
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Format d'échange des automatisations (spec §8, import/export JSON).
 *
 * DTO explicites plutôt que sérialisation directe du modèle métier : le fichier exporté
 * doit rester lisible et stable même si le modèle interne bouge.
 */
@Serializable
data class AutomationBundle(
    val version: Int = FORMAT_VERSION,
    val automations: List<AutomationDto>
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class AutomationDto(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val phrases: List<String> = emptyList(),
    val matchMode: String = MatchMode.FUZZY.name,
    val priority: Int = 0,
    val confirmBeforeRun: Boolean = false,
    val feedbackText: String? = null,
    val conditions: List<ConditionDto> = emptyList(),
    val actions: List<ActionDto> = emptyList()
)

@Serializable
data class ActionDto(
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val critical: Boolean = false,
    val delayMsBefore: Long = 0
)

@Serializable
data class ConditionDto(
    val type: String,
    val params: Map<String, String> = emptyMap(),
    val negated: Boolean = false
)

object AutomationTransfer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun export(automations: List<Automation>): String =
        json.encodeToString(
            AutomationBundle.serializer(),
            AutomationBundle(automations = automations.map { it.toDto() })
        )

    /**
     * @param regenerateIds vrai pour importer une copie à côté de l'existant, faux pour
     * remplacer les automatisations de même identifiant.
     */
    fun import(text: String, regenerateIds: Boolean = false): Result<List<Automation>> =
        runCatching {
            val bundle = json.decodeFromString(AutomationBundle.serializer(), text)
            require(bundle.version <= AutomationBundle.FORMAT_VERSION) {
                "Fichier écrit par une version plus récente (v${bundle.version})"
            }
            bundle.automations.map { it.toDomain(regenerateIds) }
        }

    private fun Automation.toDto() = AutomationDto(
        id = id,
        name = name,
        enabled = enabled,
        phrases = phrases,
        matchMode = matchMode.name,
        priority = priority,
        confirmBeforeRun = confirmBeforeRun,
        feedbackText = feedbackText,
        conditions = conditions.map {
            ConditionDto(type = it.type.name, params = it.params, negated = it.negated)
        },
        actions = actions.map {
            ActionDto(
                type = it.type.name,
                params = it.params,
                critical = it.critical,
                delayMsBefore = it.delayMsBefore
            )
        }
    )

    private fun AutomationDto.toDomain(regenerateIds: Boolean): Automation {
        require(name.isNotBlank()) { "Automatisation sans nom" }
        return Automation(
            id = if (regenerateIds) UUID.randomUUID().toString() else id,
            name = name,
            enabled = enabled,
            phrases = phrases,
            matchMode = enumOrDefault(matchMode, MatchMode.FUZZY),
            priority = priority,
            confirmBeforeRun = confirmBeforeRun,
            feedbackText = feedbackText,
            conditions = conditions.mapNotNull { dto ->
                val type = enumOrNull<ConditionType>(dto.type) ?: return@mapNotNull null
                Condition(type = type, params = dto.params, negated = dto.negated)
            },
            // Un type d'action inconnu est ignoré plutôt que de faire échouer tout le fichier.
            actions = actions.mapNotNull { dto ->
                val type = enumOrNull<ActionType>(dto.type) ?: return@mapNotNull null
                ActionSpec(
                    type = type,
                    params = dto.params,
                    critical = dto.critical,
                    delayMsBefore = dto.delayMsBefore
                )
            }
        )
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        enumOrNull<T>(name) ?: default
}
