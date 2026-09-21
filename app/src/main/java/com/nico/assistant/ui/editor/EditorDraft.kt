package com.nico.assistant.ui.editor

import com.nico.assistant.core.executor.SystemSlots
import com.nico.assistant.core.matching.MatchEngine
import com.nico.assistant.core.matching.SlotExtractor
import com.nico.assistant.core.matching.TextNormalizer
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import java.util.UUID

/**
 * L'automatisation en cours d'édition, et toutes les opérations de l'éditeur.
 *
 * Volontairement sans Android ni coroutine : c'est ici que vit la logique de l'écran
 * le plus complexe de l'app, elle doit être testable telle quelle.
 */
data class EditorDraft(
    val automation: Automation,
    /** Création plutôt qu'édition : change le titre de l'écran et le bouton. */
    val isNew: Boolean = true
) {

    val name: String get() = automation.name
    val phrases: List<String> get() = automation.phrases
    val actions: List<ActionSpec> get() = automation.actions
    val conditions: List<Condition> get() = automation.conditions
    val matchMode: MatchMode get() = automation.matchMode

    /** Un nom et au moins une phrase et une action : en-dessous, rien ne pourrait se déclencher. */
    val isValid: Boolean
        get() = automation.name.isNotBlank() &&
            automation.phrases.any { it.isNotBlank() } &&
            automation.actions.isNotEmpty()

    val validationMessage: String?
        get() = when {
            automation.name.isBlank() -> "Donne un nom à cette automatisation"
            automation.phrases.none { it.isNotBlank() } -> "Ajoute au moins une phrase déclenchante"
            automation.actions.isEmpty() -> "Ajoute au moins une action"
            else -> null
        }

    /**
     * Les slots utilisables dans les paramètres : ceux déclarés dans les phrases,
     * plus les slots système toujours disponibles. C'est ce qui rend les variables
     * découvrables sans documentation (spec §7.4).
     */
    val availableSlots: List<String>
        get() {
            val fromPhrases = automation.phrases.flatMap { SlotExtractor.slotNames(it) }
            return (fromPhrases + SYSTEM_SLOTS).distinct()
        }

    fun withName(value: String) = copy(automation = automation.copy(name = value))

    fun withMatchMode(mode: MatchMode) = copy(automation = automation.copy(matchMode = mode))

    fun withFeedbackText(value: String) =
        copy(automation = automation.copy(feedbackText = value.ifBlank { null }))

    fun withPriority(value: Int) = copy(automation = automation.copy(priority = value))

    fun withConfirmBeforeRun(value: Boolean) =
        copy(automation = automation.copy(confirmBeforeRun = value))

    fun withEnabled(value: Boolean) = copy(automation = automation.copy(enabled = value))

    /** Les doublons sont ignorés : deux fois la même phrase ne sert à rien au matching. */
    fun addPhrase(phrase: String): EditorDraft {
        val cleaned = phrase.trim()
        if (cleaned.isEmpty()) return this
        val normalized = TextNormalizer.normalize(cleaned)
        if (automation.phrases.any { TextNormalizer.normalize(it) == normalized }) return this
        return copy(automation = automation.copy(phrases = automation.phrases + cleaned))
    }

    fun removePhrase(index: Int): EditorDraft {
        if (index !in automation.phrases.indices) return this
        return copy(automation = automation.copy(phrases = automation.phrases.without(index)))
    }

    fun addAction(spec: ActionSpec) =
        copy(automation = automation.copy(actions = automation.actions + spec))

    fun replaceAction(index: Int, spec: ActionSpec): EditorDraft {
        if (index !in automation.actions.indices) return this
        val updated = automation.actions.toMutableList().apply { this[index] = spec }
        return copy(automation = automation.copy(actions = updated))
    }

    fun removeAction(index: Int): EditorDraft {
        if (index !in automation.actions.indices) return this
        return copy(automation = automation.copy(actions = automation.actions.without(index)))
    }

    fun duplicateAction(index: Int): EditorDraft {
        val source = automation.actions.getOrNull(index) ?: return this
        val duplicated = source.copy(id = UUID.randomUUID().toString())
        val updated = automation.actions.toMutableList().apply { add(index + 1, duplicated) }
        return copy(automation = automation.copy(actions = updated))
    }

    /** Déplacement d'un cran ; hors bornes, la liste est rendue inchangée. */
    fun moveAction(from: Int, to: Int): EditorDraft {
        if (from !in automation.actions.indices || to !in automation.actions.indices) return this
        if (from == to) return this
        val updated = automation.actions.toMutableList()
        updated.add(to, updated.removeAt(from))
        return copy(automation = automation.copy(actions = updated))
    }

    fun addCondition(condition: Condition) =
        copy(automation = automation.copy(conditions = automation.conditions + condition))

    fun removeCondition(index: Int): EditorDraft {
        if (index !in automation.conditions.indices) return this
        return copy(automation = automation.copy(conditions = automation.conditions.without(index)))
    }

    private fun <T> List<T>.without(index: Int): List<T> =
        toMutableList().apply { removeAt(index) }

    companion object {
        val SYSTEM_SLOTS = listOf(
            MatchEngine.SLOT_TEXTE,
            SystemSlots.HEURE,
            SystemSlots.DATE,
            SystemSlots.BATTERIE
        )

        /** Éditeur vierge, éventuellement pré-rempli par la phrase que le STT n'a pas su placer. */
        fun blank(initialPhrase: String? = null): EditorDraft {
            val phrases = initialPhrase?.trim()?.takeIf { it.isNotEmpty() }?.let { listOf(it) }.orEmpty()
            return EditorDraft(Automation(name = "", phrases = phrases), isNew = true)
        }

        fun of(automation: Automation) = EditorDraft(automation, isNew = false)
    }
}
