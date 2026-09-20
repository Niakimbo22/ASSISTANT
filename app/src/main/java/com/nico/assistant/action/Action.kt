package com.nico.assistant.action

import com.nico.assistant.core.executor.ExecutionContext

/**
 * Contrat unique de toute action (spec §5.1). **Aucune exception, aucun cas spécial.**
 *
 * Ajouter une action = créer une classe ici, ajouter la valeur dans [ActionType],
 * l'inscrire dans [ActionRegistry]. Ni UI, ni parsing, ni migration : le formulaire
 * de l'éditeur est généré à partir de [paramsSchema].
 */
interface Action {

    val type: ActionType

    val backend: Backend

    val paramsSchema: List<ParamSpec>

    /** Libellé affiché dans le sélecteur d'action. */
    val label: String

    val category: ActionCategory

    /** Phrase courte affichée sous le libellé dans le sélecteur. */
    val description: String get() = ""

    /**
     * Délai au-delà duquel l'action est abandonnée. Une action bloquée ne doit jamais
     * figer la chaîne ; seule `WAIT` a besoin de repousser cette limite.
     */
    fun timeoutMs(params: Map<String, String>): Long = DEFAULT_TIMEOUT_MS

    /** Les `{slots}` des paramètres sont déjà résolus quand cette méthode est appelée. */
    suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}

/** Ce dont l'action a besoin pour fonctionner, et donc ce qu'il faut vérifier avant. */
enum class Backend { INTENT, ACCESSIBILITY, SHIZUKU, INTERNAL }

/** Regroupement du sélecteur d'action (spec §7.4). */
enum class ActionCategory(val label: String) {
    APPS("Applications"),
    COMMUNICATION("Communication"),
    MEDIA("Média"),
    SYSTEME("Système"),
    UTILITAIRES("Utilitaires")
}

sealed interface ActionResult {
    data class Success(val message: String? = null) : ActionResult
    data class Failure(val reason: String, val recoverable: Boolean = true) : ActionResult
}
