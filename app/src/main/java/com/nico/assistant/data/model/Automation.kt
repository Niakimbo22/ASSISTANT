package com.nico.assistant.data.model

import com.nico.assistant.action.ActionType
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.MatchMode
import java.util.UUID

/**
 * Automatisation assemblée (spec §3, « modèle métier hors DB »).
 *
 * C'est le seul type que voient le matching, l'executor et l'UI : la découpe en trois tables
 * ne sort jamais du package `data`.
 *
 * Les compteurs [createdAt], [lastRunAt] et [runCount] sont portés par ce modèle — sans eux,
 * sauvegarder une automatisation éditée remettrait son historique d'exécution à zéro.
 */
data class Automation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val phrases: List<String> = emptyList(),
    val matchMode: MatchMode = MatchMode.FUZZY,
    val priority: Int = 0,
    val confirmBeforeRun: Boolean = false,
    val feedbackText: String? = null,
    val conditions: List<Condition> = emptyList(),
    /** Triées par ordre d'exécution. */
    val actions: List<ActionSpec> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val runCount: Int = 0
)

/**
 * Une action configurée dans une chaîne.
 *
 * La position n'est pas stockée ici : c'est l'index dans [Automation.actions] qui fait foi,
 * et il est réécrit dans la colonne `order` à chaque sauvegarde.
 */
data class ActionSpec(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val params: Map<String, String> = emptyMap(),
    /** Si l'action échoue, la chaîne s'arrête. */
    val critical: Boolean = false,
    val delayMsBefore: Long = 0
)
