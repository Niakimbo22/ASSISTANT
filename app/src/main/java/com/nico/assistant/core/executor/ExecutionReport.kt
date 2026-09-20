package com.nico.assistant.core.executor

import com.nico.assistant.action.ActionResult
import com.nico.assistant.data.db.ExecutionLogEntity
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation

/** Résultat d'une action dans la chaîne, conservé pour l'écran Logs et le bouton « Tester ». */
data class ActionOutcome(val spec: ActionSpec, val result: ActionResult) {
    val succeeded: Boolean get() = result is ActionResult.Success
}

/**
 * Compte rendu d'une chaîne exécutée. Alimente le feedback TTS, le rapport action par action
 * de l'éditeur et le journal d'exécution.
 */
data class ExecutionReport(
    val automation: Automation,
    val outcomes: List<ActionOutcome>,
    /** Vrai si une action critique a échoué et a interrompu la chaîne. */
    val stoppedEarly: Boolean = false,
    val slots: Map<String, String> = emptyMap()
) {
    val total: Int get() = automation.actions.size
    val succeeded: Int get() = outcomes.count { it.succeeded }
    val failures: List<ActionOutcome> get() = outcomes.filterNot { it.succeeded }
    val success: Boolean get() = !stoppedEarly && failures.isEmpty()

    /** Première raison d'échec, celle qu'on montre à l'utilisateur. */
    val errorMessage: String?
        get() = failures.firstOrNull()?.result?.let { (it as? ActionResult.Failure)?.reason }

    /**
     * Texte prononcé après l'exécution (spec §8.4) : le feedback personnalisé s'il existe,
     * sinon un message généré qui dit franchement ce qui a échoué.
     */
    fun feedbackText(): String {
        automation.feedbackText?.takeIf { it.isNotBlank() }?.let {
            return SlotResolver.resolve(it, slots)
        }
        if (total == 0) return "${automation.name} n'a aucune action"
        return when {
            success -> "OK, ${automation.name}"
            succeeded == 0 -> "${automation.name} a échoué"
            else -> "${automation.name}, $succeeded action${plural(succeeded)} sur $total"
        }
    }

    fun toLog(heardText: String, matchScore: Float, timestamp: Long): ExecutionLogEntity =
        ExecutionLogEntity(
            automationId = automation.id,
            heardText = heardText,
            matchScore = matchScore,
            success = success,
            errorMessage = errorMessage,
            timestamp = timestamp
        )

    private fun plural(count: Int) = if (count > 1) "s" else ""
}
