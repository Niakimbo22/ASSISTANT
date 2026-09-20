package com.nico.assistant.core.executor

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remplace les `{slots}` d'un paramètre par les valeurs capturées au matching
 * ou par les slots système (spec §4.2 et §5.5).
 *
 * Un slot inconnu est laissé tel quel : mieux vaut un paramètre visiblement non résolu
 * qu'une valeur vide envoyée silencieusement à une action.
 */
object SlotResolver {

    private val PLACEHOLDER = Regex("\\{(\\w+)\\}")

    fun resolve(value: String, slots: Map<String, String>): String =
        PLACEHOLDER.replace(value) { match -> slots[match.groupValues[1]] ?: match.value }

    fun resolveAll(params: Map<String, String>, slots: Map<String, String>): Map<String, String> =
        params.mapValues { (_, value) -> resolve(value, slots) }

    fun hasUnresolved(value: String): Boolean = PLACEHOLDER.containsMatchIn(value)
}

/** Slots toujours disponibles, même sans capture (spec §4.2). */
object SystemSlots {

    const val HEURE = "heure"
    const val DATE = "date"
    const val BATTERIE = "batterie"

    fun current(context: Context, now: Long = System.currentTimeMillis()): Map<String, String> {
        val moment = Date(now)
        return buildMap {
            put(HEURE, SimpleDateFormat("HH:mm", Locale.FRENCH).format(moment))
            put(DATE, SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(moment))
            batteryLevel(context)?.let { put(BATTERIE, it.toString()) }
        }
    }

    private fun batteryLevel(context: Context): Int? = runCatching {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
    }.getOrNull()
}
