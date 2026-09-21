package com.nico.assistant.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nico.assistant.a11y.MusicAccessibilityService
import com.nico.assistant.core.matching.MatchThresholds
import com.nico.assistant.data.repo.AutomationRepository
import com.nico.assistant.data.transfer.AutomationTransfer
import com.nico.assistant.prefs.Prefs
import com.nico.assistant.shizuku.ShizukuManager
import com.nico.assistant.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SystemSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val shizuku = ShizukuManager.shared()
    private val prefs = Prefs(application)
    private val repository = AutomationRepository.from(application)

    val shizukuState: StateFlow<ShizukuState> = shizuku.state

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _thresholds = MutableStateFlow(MatchThresholds.from(prefs))
    val thresholds: StateFlow<MatchThresholds> = _thresholds.asStateFlow()

    /** Dernier résultat d'import / export, affiché puis effacé. */
    private val _transferMessage = MutableStateFlow<String?>(null)
    val transferMessage: StateFlow<String?> = _transferMessage.asStateFlow()

    val accessibilityEnabled: Boolean
        get() = MusicAccessibilityService.isEnabled(getApplication())

    // --- Shizuku -------------------------------------------------------------

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

    // --- Seuils de matching --------------------------------------------------

    fun setConfident(value: Float) {
        prefs.confidentThreshold = value
        reloadThresholds()
    }

    fun setAmbiguousFloor(value: Float) {
        prefs.ambiguousFloor = value
        reloadThresholds()
    }

    fun setMinimumGap(value: Float) {
        prefs.minimumGap = value
        reloadThresholds()
    }

    fun resetThresholds() {
        prefs.resetThresholds()
        reloadThresholds()
    }

    private fun reloadThresholds() {
        _thresholds.value = MatchThresholds.from(prefs)
    }

    // --- Import / export -----------------------------------------------------

    fun export(uri: Uri) {
        viewModelScope.launch {
            _transferMessage.value = runCatching {
                val automations = repository.observeAllOnce()
                val json = AutomationTransfer.export(automations)
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    } ?: error("Fichier inaccessible")
                }
                "${automations.size} automatisations exportées"
            }.getOrElse { "Export impossible : ${it.message}" }
        }
    }

    /**
     * Les identifiants sont régénérés : un fichier partagé s'ajoute au catalogue au lieu
     * d'écraser des automatisations qui portaient le même identifiant par hasard.
     */
    fun import(uri: Uri) {
        viewModelScope.launch {
            _transferMessage.value = runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: error("Fichier illisible")
                }
                val imported = AutomationTransfer.import(text, regenerateIds = true).getOrThrow()
                imported.forEach { repository.save(it) }
                "${imported.size} automatisations importées"
            }.getOrElse { "Import impossible : ${it.message}" }
        }
    }

    fun clearTransferMessage() {
        _transferMessage.value = null
    }

    private suspend fun AutomationRepository.observeAllOnce() = observeAll().first()
}
