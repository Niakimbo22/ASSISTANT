package com.nico.assistant.core.executor

import android.content.Context
import android.util.Log
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationRepository
import com.nico.assistant.shizuku.ShizukuGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** Ce que l'appareil sait faire à cet instant. */
fun interface BackendAvailability {
    fun isAvailable(backend: Backend): Boolean

    companion object {
        /** Sans Shizuku ni service d'accessibilité, seuls les intents et l'interne marchent. */
        val INTENT_AND_INTERNAL = BackendAvailability {
            it == Backend.INTENT || it == Backend.INTERNAL
        }

        /**
         * Seul Shizuku se vérifie d'avance. Le service d'accessibilité, lui, dépend de
         * l'action précise : c'est elle qui le contrôle au moment d'agir.
         */
        fun forShizuku(gateway: ShizukuGateway) = BackendAvailability { backend ->
            backend != Backend.SHIZUKU || gateway.isReady()
        }
    }
}

/**
 * Déroule la chaîne d'actions d'une automatisation (spec §5.5).
 *
 * Trois garanties : chaque action a un délai maximum, une action non critique qui échoue
 * n'interrompt pas la chaîne, et tout finit dans le journal d'exécution.
 */
class ActionExecutor(
    private val appContext: Context,
    private val speaker: Speaker = Speaker.SILENT,
    private val repository: AutomationRepository? = null,
    private val shizuku: ShizukuGateway = ShizukuGateway.UNAVAILABLE,
    /** Indirection volontaire : elle permet d'exécuter des chaînes factices dans les tests. */
    private val lookup: (ActionType) -> Action? = ActionRegistry::find,
    private val availability: BackendAvailability = BackendAvailability.forShizuku(shizuku),
    private val clock: () -> Long = System::currentTimeMillis
) {

    suspend fun run(
        automation: Automation,
        slots: Map<String, String> = emptyMap(),
        heardText: String = "",
        matchScore: Float = 1f
    ): ExecutionReport {
        val allSlots = SystemSlots.current(appContext, clock()) + slots
        val ctx = ExecutionContext(appContext, allSlots, speaker, shizuku)

        val outcomes = mutableListOf<ActionOutcome>()
        var stoppedEarly = false

        // La liste métier est déjà dans l'ordre de la chaîne (cf. mappers).
        for (spec in automation.actions) {
            if (spec.delayMsBefore > 0) delay(spec.delayMsBefore)

            val result = runSingle(ctx, lookup(spec.type), spec.params, allSlots, spec.type.name)
            outcomes += ActionOutcome(spec, result)

            Log.d(TAG, "${automation.name} · ${spec.type} → $result")

            if (result is ActionResult.Failure && spec.critical) {
                stoppedEarly = true
                break
            }
        }

        val report = ExecutionReport(automation, outcomes, stoppedEarly, allSlots)
        journal(report, heardText, matchScore)
        return report
    }

    private suspend fun runSingle(
        ctx: ExecutionContext,
        action: Action?,
        params: Map<String, String>,
        slots: Map<String, String>,
        typeName: String
    ): ActionResult {
        if (action == null) {
            return ActionResult.Failure("Action non implémentée : $typeName", recoverable = false)
        }
        // Une action qui sait se rabattre a le droit d'essayer même sans son backend.
        if (!availability.isAvailable(action.backend) && !action.hasFallback) {
            return ActionResult.Failure("${action.backend} indisponible")
        }

        val resolved = SlotResolver.resolveAll(params, slots)

        return try {
            withTimeout(action.timeoutMs(resolved)) { action.execute(ctx, resolved) }
        } catch (timeout: TimeoutCancellationException) {
            ActionResult.Failure("${action.label} n'a pas répondu à temps")
        } catch (cancellation: CancellationException) {
            // L'appelant a annulé : ce n'est pas un échec d'action, ça doit remonter.
            throw cancellation
        } catch (error: Exception) {
            ActionResult.Failure(error.message ?: "Erreur inconnue")
        }
    }

    private suspend fun journal(report: ExecutionReport, heardText: String, matchScore: Float) {
        val repo = repository ?: return
        val now = clock()
        runCatching {
            repo.log(report.toLog(heardText, matchScore, now))
            if (report.succeeded > 0) repo.markRun(report.automation.id, now)
        }.onFailure { Log.w(TAG, "Journal d'exécution indisponible", it) }
    }

    private companion object {
        const val TAG = "NICO_EXEC"
    }
}
