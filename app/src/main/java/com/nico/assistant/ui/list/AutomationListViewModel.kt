package com.nico.assistant.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nico.assistant.core.matching.TextNormalizer
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Filtre de la barre de recherche.
 *
 * Passe par [TextNormalizer] : chercher « telephone » doit trouver « téléphone à {contact} ».
 */
object AutomationFilter {

    fun filter(automations: List<Automation>, query: String): List<Automation> {
        val needle = TextNormalizer.normalize(query)
        if (needle.isEmpty()) return automations
        return automations.filter { automation ->
            TextNormalizer.normalize(automation.name).contains(needle) ||
                automation.phrases.any { TextNormalizer.normalize(it).contains(needle) }
        }
    }
}

class AutomationListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AutomationRepository.from(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Dernière automatisation supprimée, gardée entière pour permettre l'annulation. */
    private val _recentlyDeleted = MutableStateFlow<Automation?>(null)
    val recentlyDeleted: StateFlow<Automation?> = _recentlyDeleted.asStateFlow()

    /** Nombre total d'automatisations et nombre d'actives, pour le sous-titre de la liste. */
    val totals: StateFlow<Pair<Int, Int>> =
        repository.observeAll()
            .map { list -> list.size to list.count { it.enabled } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    val automations: StateFlow<List<Automation>> =
        combine(repository.observeAll(), _query) { list, query ->
            AutomationFilter.filter(list, query)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setEnabled(automation: Automation, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(automation.id, enabled) }
    }

    fun delete(automation: Automation) {
        viewModelScope.launch {
            repository.delete(automation.id)
            _recentlyDeleted.value = automation
        }
    }

    /** L'objet métier porte tout : le réinsérer suffit à annuler la suppression. */
    fun undoDelete() {
        val deleted = _recentlyDeleted.value ?: return
        viewModelScope.launch {
            repository.save(deleted)
            _recentlyDeleted.value = null
        }
    }

    /** Crée directement une automatisation depuis un modèle de l'écran vide. */
    fun createFromTemplate(template: Automation) {
        viewModelScope.launch { repository.save(AutomationTemplates.instantiate(template)) }
    }

    fun clearUndo() {
        _recentlyDeleted.value = null
    }
}
