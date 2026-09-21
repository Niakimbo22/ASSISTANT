package com.nico.assistant.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nico.assistant.data.db.ExecutionLogEntity
import com.nico.assistant.data.repo.AutomationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Une ligne du journal, prête à afficher. */
data class LogRow(
    val log: ExecutionLogEntity,
    val automationName: String?,
    val time: String
) {
    val title: String get() = automationName ?: "Non reconnu"
    val score: String get() = "%.2f".format(Locale.FRANCE, log.matchScore)
}

class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AutomationRepository.from(application)
    private val formatter = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

    val rows: StateFlow<List<LogRow>> =
        combine(repository.observeLogs(), repository.observeAll()) { logs, automations ->
            val names = automations.associate { it.id to it.name }
            logs.map { log ->
                LogRow(
                    log = log,
                    automationName = log.automationId?.let { names[it] },
                    time = formatter.format(Date(log.timestamp))
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { repository.clearLogs() }
    }
}
