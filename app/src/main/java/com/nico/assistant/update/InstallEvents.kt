package com.nico.assistant.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce que le système a répondu à notre demande d'installation. */
sealed interface InstallEvent {
    /** Session transmise : l'écran de confirmation système va s'afficher. */
    data object AwaitingUser : InstallEvent

    /** Installé. En pratique l'app est relancée avant d'avoir pu l'afficher. */
    data object Success : InstallEvent

    /** Refusé, annulé, ou signature incompatible. Le message part tel quel dans l'UI. */
    data class Failed(val message: String) : InstallEvent
}

/**
 * Boîte aux lettres entre [InstallResultReceiver], déclaré au manifeste, et le
 * ViewModel. Le receiver tourne dans le même processus : un objet partagé suffit,
 * et évite d'enregistrer/désenregistrer un receiver au fil du cycle de vie.
 */
object InstallEvents {

    private val _latest = MutableStateFlow<InstallEvent?>(null)
    val latest: StateFlow<InstallEvent?> = _latest.asStateFlow()

    fun post(event: InstallEvent) {
        _latest.value = event
    }

    fun clear() {
        _latest.value = null
    }
}
