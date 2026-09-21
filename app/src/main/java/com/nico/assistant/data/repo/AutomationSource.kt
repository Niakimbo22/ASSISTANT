package com.nico.assistant.data.repo

import com.nico.assistant.data.model.Automation

/**
 * Ce dont le moteur de matching a besoin, et rien de plus.
 *
 * Permet de le tester sur des automatisations en mémoire, sans base ni Android.
 */
interface AutomationSource {
    suspend fun enabledAutomations(): List<Automation>
}
