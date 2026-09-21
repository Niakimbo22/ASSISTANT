package com.nico.assistant.ui.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nico.assistant.core.executor.Speaker
import com.nico.assistant.core.matching.MatchResult
import com.nico.assistant.core.pipeline.AssistantPipeline
import com.nico.assistant.core.pipeline.AssistantState
import com.nico.assistant.tts.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val ttsDelegate = lazy { TtsManager(application) }
    private val tts by ttsDelegate

    private val pipeline = AssistantPipeline.create(application, Speaker { text -> tts.speak(text) })

    val state: StateFlow<AssistantState> = pipeline.state

    private var session: Job? = null

    fun listen() {
        session?.cancel()
        session = viewModelScope.launch { pipeline.listenAndRun() }
    }

    fun choose(result: MatchResult, heard: String) {
        session?.cancel()
        session = viewModelScope.launch { pipeline.confirm(result, heard) }
    }

    fun reset() {
        session?.cancel()
        pipeline.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        session?.cancel()
        if (ttsDelegate.isInitialized()) tts.shutdown()
    }
}
