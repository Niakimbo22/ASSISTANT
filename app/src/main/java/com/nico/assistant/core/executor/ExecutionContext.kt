package com.nico.assistant.core.executor

import android.content.Context
import com.nico.assistant.shizuku.ShizukuGateway

/**
 * Tout ce qu'une action peut toucher du monde extérieur.
 *
 * La synthèse vocale et l'accès Shizuku passent par des interfaces plutôt que par leurs
 * implémentations : une action se teste ainsi sans moteur TTS ni Shizuku installé.
 */
data class ExecutionContext(
    val context: Context,
    /** Slots capturés au matching, enrichis des slots système. */
    val slots: Map<String, String>,
    val speaker: Speaker,
    val shizuku: ShizukuGateway = ShizukuGateway.UNAVAILABLE
)

/** Minimum vital de la synthèse vocale, côté actions. */
fun interface Speaker {
    fun speak(text: String)

    companion object {
        /** Pour les tests et les exécutions silencieuses. */
        val SILENT = Speaker { }
    }
}
