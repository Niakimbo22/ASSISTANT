package com.nico.assistant.core.condition

import com.nico.assistant.data.db.ConditionType
import java.util.UUID

/**
 * Condition côté métier, telle que manipulée par l'éditeur et le moteur.
 *
 * Lot 1 : donnée pure. Le `ConditionEvaluator` qui la confronte à l'état du téléphone
 * arrive au lot 7.
 */
data class Condition(
    val id: String = UUID.randomUUID().toString(),
    val type: ConditionType,
    val params: Map<String, String> = emptyMap(),
    /** Inverse le résultat : « seulement si je ne suis PAS en wifi ». */
    val negated: Boolean = false
)
