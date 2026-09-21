package com.nico.assistant.core.condition

import java.util.Calendar

/**
 * Le calcul pur des conditions temporelles, séparé de l'état de l'appareil pour être
 * testable sans Android — c'est là que se cachent les vrais pièges (minuit, jours).
 */
object ConditionRules {

    /** Les trois lettres attendues dans `days="MON,TUE"`, dans l'ordre de [Calendar]. */
    val DAY_CODES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

    /** `"22:00"` → 1320 minutes. `null` si la valeur est illisible. */
    fun parseTime(value: String?): Int? {
        val parts = value?.trim()?.split(":") ?: return null
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    /**
     * Vrai si [nowMinutes] tombe dans l'intervalle, bornes comprises côté début.
     *
     * Gère le passage de minuit : 22:00 → 07:00 couvre la nuit, pas la journée.
     */
    fun inTimeRange(nowMinutes: Int, start: Int, end: Int): Boolean =
        if (start <= end) nowMinutes in start until end else nowMinutes >= start || nowMinutes < end

    /** `days="MON,TUE,WED"`, insensible à la casse et aux espaces. Vide = tous les jours. */
    fun matchesDay(dayCode: String, days: String?): Boolean {
        val wanted = days?.split(",")
            ?.map { it.trim().uppercase() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (wanted.isEmpty()) return true
        return dayCode.uppercase() in wanted
    }

    fun dayCodeOf(calendar: Calendar): String =
        DAY_CODES[(calendar.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)]

    fun minutesOf(calendar: Calendar): Int =
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
