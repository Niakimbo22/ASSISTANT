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
     * L'action sait se rabattre sur autre chose si son backend manque (spec §6.5) :
     * l'executor la laisse alors tenter sa chance plutôt que de refuser d'emblée.
     */
    val hasFallback: Boolean get() = false

    /**
     * Délai au-delà duquel l'action est abandonnée. Une action bloquée ne doit jamais
     * figer la chaîne ; `WAIT` repousse cette limite, et [NO_TIMEOUT] la retire.
     */
    fun timeoutMs(params: Map<String, String>): Long = DEFAULT_TIMEOUT_MS

    /** Les `{slots}` des paramètres sont déjà résolus quand cette méthode est appelée. */
    suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L

        /**
         * Pas de délai maximum. Réservé aux actions qui en délèguent un à autre chose :
         * une chaîne appelée applique déjà le sien à chacune de ses propres actions.
         */
        const val NO_TIMEOUT = 0L
    }
}

/** Ce dont l'action a besoin pour fonctionner, et donc ce qu'il faut vérifier avant. */
enum class Backend { INTENT, ACCESSIBILITY, SHIZUKU, INTERNAL }

/**
 * Regroupement du sélecteur d'action (spec §7.4).
 *
 * @param accent couleur de catégorie en ARGB, reprise par l'icône de chaque action de la
 * catégorie. Gardée en `Long` pour que ce package reste indépendant de Compose.
 */
enum class ActionCategory(val label: String, val accent: Long) {
    APPS("Applications", 0xFF5B8CFF),
    COMMUNICATION("Communication", 0xFF3DD68C),
    MEDIA("Média", 0xFFFF5FA2),
    SYSTEME("Système", 0xFFFF9F43),
    UTILITAIRES("Utilitaires", 0xFFA78BFA)
}

sealed interface ActionResult {
    data class Success(val message: String? = null) : ActionResult
    data class Failure(val reason: String, val recoverable: Boolean = true) : ActionResult
}
