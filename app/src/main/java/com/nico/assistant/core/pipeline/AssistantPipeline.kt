package com.nico.assistant.core.pipeline

import android.content.Context
import android.util.Log
import com.nico.assistant.core.executor.ActionExecutor
import com.nico.assistant.core.executor.ExecutionReport
import com.nico.assistant.core.executor.Speaker
import com.nico.assistant.core.matching.MatchEngine
import com.nico.assistant.core.matching.MatchOutcome
import com.nico.assistant.core.matching.MatchResult
import com.nico.assistant.core.stt.SpeechEvent
import com.nico.assistant.core.stt.SpeechManager
import com.nico.assistant.data.db.ExecutionLogEntity
import com.nico.assistant.data.repo.AutomationRepository
import com.nico.assistant.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Où en est l'assistant, du micro au retour vocal (spec §2). */
sealed interface AssistantState {
    data object Idle : AssistantState
    data class Listening(val partial: String = "", val level: Float = 0f) : AssistantState
    data class Thinking(val heard: String) : AssistantState

    /** Plusieurs candidates crédibles : on demande plutôt que de deviner (spec §4.4). */
    data class Choosing(val heard: String, val choices: List<MatchResult>) : AssistantState

    data class Running(val name: String) : AssistantState
    data class Done(val report: ExecutionReport) : AssistantState

    /** C'est ici que le catalogue s'enrichit : on propose de créer l'automatisation manquante. */
    data class NotUnderstood(val heard: String) : AssistantState

    data class Failed(val message: String) : AssistantState
}

/**
 * Pipeline complet : micro → matching → conditions → exécution → retour vocal.
 *
 * Aucune commande n'est connue d'ici : le pipeline ne sait qu'interroger le moteur.
 */
class AssistantPipeline(
    private val speech: SpeechManager,
    private val engine: MatchEngine,
    private val executor: ActionExecutor,
    private val repository: AutomationRepository,
    private val speaker: Speaker,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val _state = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    /** Écoute une phrase et va jusqu'au bout de la chaîne. À collecter depuis une coroutine. */
    suspend fun listenAndRun() {
        _state.value = AssistantState.Listening()
        speech.listen().collect { event ->
            when (event) {
                is SpeechEvent.Ready -> _state.value = AssistantState.Listening()
                is SpeechEvent.Level -> updateLevel(event.rms)
                is SpeechEvent.Partial -> updatePartial(event.text)
                is SpeechEvent.Final -> handle(event.best, event.alternatives)
                is SpeechEvent.Failed -> fail(event.message)
            }
        }
    }

    /** Point d'entrée testable : ce que ferait le pipeline pour une phrase déjà transcrite. */
    suspend fun handle(heard: String, alternatives: List<String> = emptyList()) {
        _state.value = AssistantState.Thinking(heard)
        Log.i(TAG, "Entendu « $heard » (+${alternatives.size} alternatives)")

        when (val outcome = engine.match(heard, alternatives)) {
            is MatchOutcome.Confident -> execute(outcome.result, heard)
            is MatchOutcome.Ambiguous -> ask(heard, outcome.top)
            MatchOutcome.NoMatch -> notUnderstood(heard)
        }
    }

    /** Choix fait par l'utilisateur dans l'état [AssistantState.Choosing]. */
    suspend fun confirm(result: MatchResult, heard: String) {
        execute(result, heard)
    }

    fun cancel() {
        _state.value = AssistantState.Idle
    }

    private suspend fun execute(result: MatchResult, heard: String) {
        val automation = result.automation
        _state.value = AssistantState.Running(automation.name)
        Log.i(TAG, "Exécution de « ${automation.name} » (score ${result.score})")

        val report = executor.run(
            automation = automation,
            slots = result.slots,
            heardText = heard,
            matchScore = result.score
        )
        speaker.speak(report.feedbackText())
        _state.value = AssistantState.Done(report)
    }

    private fun ask(heard: String, choices: List<MatchResult>) {
        val question = if (choices.size == 1) {
            "Tu voulais dire ${choices.first().automation.name} ?"
        } else {
            "Tu veux " + choices.joinToString(" ou ") { it.automation.name } + " ?"
        }
        speaker.speak(question)
        _state.value = AssistantState.Choosing(heard, choices)
    }

    private suspend fun notUnderstood(heard: String) {
        speaker.speak("J'ai pas compris")
        journalMiss(heard, 0f, "NoMatch")
        _state.value = AssistantState.NotUnderstood(heard)
    }

    private suspend fun fail(message: String) {
        journalMiss("", 0f, message)
        _state.value = AssistantState.Failed(message)
    }

    private fun updateLevel(rms: Float) {
        val current = _state.value
        _state.value = if (current is AssistantState.Listening) {
            current.copy(level = rms)
        } else {
            AssistantState.Listening(level = rms)
        }
    }

    private fun updatePartial(text: String) {
        val current = _state.value
        _state.value = if (current is AssistantState.Listening) {
            current.copy(partial = text)
        } else {
            AssistantState.Listening(partial = text)
        }
    }

    /** Un échec de reconnaissance mérite une ligne de journal autant qu'une exécution ratée. */
    private suspend fun journalMiss(heard: String, score: Float, reason: String) {
        runCatching {
            repository.log(
                ExecutionLogEntity(
                    automationId = null,
                    heardText = heard,
                    matchScore = score,
                    success = false,
                    errorMessage = reason,
                    timestamp = clock()
                )
            )
        }
    }

    companion object {
        private const val TAG = "NICO_MATCH"

        /** Câblage standard, tel qu'utilisé par l'app. */
        fun create(context: Context, speaker: Speaker): AssistantPipeline {
            val repository = AutomationRepository.from(context)
            return AssistantPipeline(
                speech = SpeechManager(context),
                engine = MatchEngine(repository),
                executor = ActionExecutor(
                    appContext = context.applicationContext,
                    speaker = speaker,
                    repository = repository,
                    shizuku = ShizukuManager.shared()
                ),
                repository = repository,
                speaker = speaker
            )
        }
    }
}
