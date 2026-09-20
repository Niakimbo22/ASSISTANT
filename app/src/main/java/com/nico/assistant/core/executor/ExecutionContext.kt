package com.nico.assistant.core.executor

import android.content.Context

/**
 * Tout ce qu'une action peut toucher du monde extérieur.
 *
 * La synthèse vocale passe par [Speaker] plutôt que par `TtsManager` directement : une action
 * se teste ainsi sans moteur TTS. L'accès Shizuku sera ajouté ici au lot 6.
 */
data class ExecutionContext(
    val context: Context,
    /** Slots capturés au matching, enrichis des slots système. */
    val slots: Map<String, String>,
    val speaker: Speaker
)

/** Minimum vital de la synthèse vocale, côté actions. */
fun interface Speaker {
    fun speak(text: String)

    companion object {
        /** Pour les tests et les exécutions silencieuses. */
        val SILENT = Speaker { }
    }
}
