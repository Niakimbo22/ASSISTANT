package com.nico.assistant.action.impl

import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.core.executor.ExecutionContext

/**
 * Appelle une autre automatisation (spec §5.3).
 *
 * C'est ce qui permet de composer : « mode nuit » réutilise « couper le son » sans
 * dupliquer ses actions. Les garde-fous (profondeur maximale, détection de cycle) sont
 * tenus par l'executor, seul à connaître le chemin déjà parcouru.
 */
class RunAutomationAction : Action {

    override val type = ActionType.RUN_AUTOMATION
    override val backend = Backend.INTERNAL
    override val label = "Lancer une autre automatisation"
    override val category = ActionCategory.UTILITAIRES
    override val description = "Composition — réutilise une chaîne existante"

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_AUTOMATION_ID,
            label = "Automatisation à lancer",
            type = ParamType.AUTOMATION_PICKER
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val id = params.required(PARAM_AUTOMATION_ID)
            ?: return ActionResult.Failure("Aucune automatisation choisie")

        val outcome = ctx.subRunner.run(id, ctx)
        return if (outcome.success) {
            ActionResult.Success(outcome.message)
        } else {
            ActionResult.Failure(outcome.message)
        }
    }

    companion object {
        const val PARAM_AUTOMATION_ID = "automationId"
    }
}
