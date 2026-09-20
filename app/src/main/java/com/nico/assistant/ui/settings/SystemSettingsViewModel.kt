package com.nico.assistant.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nico.assistant.a11y.MusicAccessibilityService
import com.nico.assistant.shizuku.ShizukuManager
import com.nico.assistant.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SystemSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val shizuku = ShizukuManager.shared()

    val shizukuState: StateFlow<ShizukuState> = shizuku.state

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    val accessibilityEnabled: Boolean
        get() = MusicAccessibilityService.isEnabled(getApplication())

    fun refresh() = shizuku.refresh()

    fun requestPermission() = shizuku.requestPermission()

    /** Le bouton « Tester » de la spec §6.4 : un `echo ok` qui prouve que le canal marche. */
    fun test() {
        viewModelScope.launch {
            _testResult.value = shizuku.exec("echo ok").fold(
                onSuccess = { "Réponse : ${it.trim().ifBlank { "(vide)" }}" },
                onFailure = { "Échec : ${it.message}" }
            )
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }
}
