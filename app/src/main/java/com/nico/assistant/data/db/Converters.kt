package com.nico.assistant.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Converters Room pour les deux seuls types composés du schéma : `List<String>` (phrases)
 * et `Map<String, String>` (paramètres d'action et de condition), stockés en JSON texte.
 *
 * kotlinx.serialization uniquement, avec des sérialiseurs explicites : pas de réflexion,
 * pas de Gson (spec §3).
 *
 * Les enums (`MatchMode`, `ConditionType`, `ActionType`) sont gérés nativement par Room,
 * qui les stocke par leur nom — aucun converter à écrire.
 */
class Converters {

    @TypeConverter
    fun stringListToJson(value: List<String>): String = JSON.encodeToString(LIST, value)

    /** Une valeur illisible (schéma changé, base corrompue) ne doit pas faire crasher l'app. */
    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        runCatching { JSON.decodeFromString(LIST, value) }.getOrDefault(emptyList())

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>): String = JSON.encodeToString(MAP, value)

    @TypeConverter
    fun jsonToStringMap(value: String): Map<String, String> =
        runCatching { JSON.decodeFromString(MAP, value) }.getOrDefault(emptyMap())

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val LIST = ListSerializer(String.serializer())
        val MAP = MapSerializer(String.serializer(), String.serializer())
    }
}
