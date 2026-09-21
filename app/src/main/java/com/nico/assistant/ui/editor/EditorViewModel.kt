package com.nico.assistant.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nico.assistant.core.executor.ActionExecutor
import com.nico.assistant.core.executor.ExecutionReport
import com.nico.assistant.core.executor.Speaker
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.repo.AutomationRepository
import com.nico.assistant.shizuku.ShizukuManager
import com.nico.assistant.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AutomationRepository.from(application)
    private val ttsDelegate = lazy { TtsManager(application) }
    private val tts by ttsDelegate
    private val executor by lazy {
        ActionExecutor(
            appContext = application,
            speaker = Speaker { text -> tts.speak(text) },
            repository = repository,
            shizuku = ShizukuManager.shared()
        )
    }

    private val _draft = MutableStateFlow(EditorDraft.blank())
    val draft: StateFlow<EditorDraft> = _draft.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Rapport du bouton « Tester maintenant », affiché action par action. */
    private val _testReport = MutableStateFlow<ExecutionReport?>(null)
    val testReport: StateFlow<ExecutionReport?> = _testReport.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()


    /**
     * @param automationId `null` pour une création.
     * @param initialPhrase phrase entendue mais non reconnue, pré-remplie depuis le NoMatch.
     */
    fun load(automationId: String?, initialPhrase: String? = null) {
        _saved.value = false
        _testReport.value = null
        if (automationId == null) {
            _draft.value = EditorDraft.blank(initialPhrase)
            return
        }
        viewModelScope.launch {
            val existing = repository.getById(automationId)
            _draft.value = existing?.let { EditorDraft.of(it) } ?: EditorDraft.blank(initialPhrase)
        }
    }

    private fun update(transform: (EditorDraft) -> EditorDraft) {
        _draft.value = transform(_draft.value)
    }

    fun setName(value: String) = update { it.withName(value) }
    fun setMatchMode(mode: MatchMode) = update { it.withMatchMode(mode) }
    fun setFeedbackText(value: String) = update { it.withFeedbackText(value) }
    fun setEnabled(value: Boolean) = update { it.withEnabled(value) }
    fun setConfirmBeforeRun(value: Boolean) = update { it.withConfirmBeforeRun(value) }
    fun setPriority(value: Int) = update { it.withPriority(value) }

    fun addPhrase(phrase: String) = update { it.addPhrase(phrase) }
    fun removePhrase(index: Int) = update { it.removePhrase(index) }

    fun addAction(spec: ActionSpec) = update { it.addAction(spec) }
    fun replaceAction(index: Int, spec: ActionSpec) = update { it.replaceAction(index, spec) }
    fun removeAction(index: Int) = update { it.removeAction(index) }
    fun duplicateAction(index: Int) = update { it.duplicateAction(index) }
    fun moveAction(from: Int, to: Int) = update { it.moveAction(from, to) }

    fun addCondition(condition: Condition) = update { it.addCondition(condition) }
    fun removeCondition(index: Int) = update { it.removeCondition(index) }

    fun save() {
        val draft = _draft.value
        if (!draft.isValid) return
        viewModelScope.launch {
            repository.save(draft.automation)
            _saved.value = true
        }
    }

    fun consumeSaved() {
        _saved.value = false
    }

    /** Exécute la chaîne telle qu'elle est à l'écran, sans passer par la voix ni sauvegarder. */
    fun testNow() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            try {
                // Un test manuel doit se dérouler même hors de la plage horaire prévue.
                _testReport.value = executor.run(
                    automation = _draft.value.automation,
                    heardText = "Test manuel",
                    checkConditions = false
                )
            } finally {
                _running.value = false
            }
        }
    }

    fun dismissTestReport() {
        _testReport.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Ne pas réveiller le moteur TTS juste pour l'éteindre.
        if (ttsDelegate.isInitialized()) tts.shutdown()
    }
}
