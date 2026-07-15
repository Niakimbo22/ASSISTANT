package com.nico.assistant.util

import java.text.Normalizer
import java.util.Locale

/**
 * Utilitaires texte partagés : normalisation insensible aux accents et à la casse.
 * Indispensable pour comparer des noms d'applis / contacts prononcés en français
 * (« démarre » vs « demarre », « Société » vs « societe »).
 */
object TextUtils {

    private val COMBINING_MARKS = Regex("\\p{InCombiningDiacriticalMarks}+")

    /**
     * Minuscule + suppression des accents + apostrophes converties en espaces
     * (« l'application » -> « l application ») + espaces compactés.
     */
    fun normalize(input: String): String {
        val lowered = input.lowercase(Locale.FRENCH).trim()
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        val noAccents = COMBINING_MARKS.replace(decomposed, "")
        val noApostrophe = noAccents.replace('\'', ' ').replace('’', ' ')
        return noApostrophe.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Score de correspondance simple entre une requête et un candidat (0f..1f).
     * Priorité : égalité > le candidat contient la requête > la requête contient
     * le candidat > sinon 0. Sert au matching flou des contacts et des applis.
     */
    fun fuzzyScore(query: String, candidate: String): Float {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isEmpty() || c.isEmpty()) return 0f
        return when {
            q == c -> 1f
            c.contains(q) -> 0.8f + (q.length.toFloat() / c.length.toFloat()) * 0.2f
            q.contains(c) -> 0.6f
            else -> 0f
        }
    }
}
