package com.nico.assistant.core.matching

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisation appliquée **au texte entendu et aux phrases stockées**, avec le même code
 * (spec §4.1). Si les deux côtés ne passent pas par ici, rien ne matche.
 *
 * Les accolades sont volontairement préservées : elles délimitent les slots `{duree}` des
 * phrases stockées, qui sont compilés en regex après normalisation.
 */
object TextNormalizer {

    /** Mots vides retirés **uniquement pour le scoring**, jamais pour l'extraction de slots. */
    val STOP_WORDS: Set<String> = setOf(
        "le", "la", "les", "l", "un", "une", "de", "du", "des", "d", "a", "au", "aux", "et"
    )

    /** Formules de politesse : retirées en bloc, pour ne pas faire de « il » un mot vide. */
    private val POLITENESS = Regex("\\b(s il te plait|s il vous plait|stp|svp|please)\\b")

    private val PUNCTUATION = Regex("[^a-z0-9{}_ ]")
    private val WHITESPACE = Regex("\\s+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    fun normalize(input: String): String = input
        .lowercase(Locale.FRENCH)
        .let(::stripAccents)
        .replace(PUNCTUATION, " ")
        .let(::wordsToDigits)
        .replace(WHITESPACE, " ")
        .trim()

    /** é→e, ç→c, ù→u, œ→oe reste hors périmètre (le STT français ne le produit pas). */
    fun stripAccents(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

    fun tokenize(input: String): List<String> =
        input.split(' ').filter { it.isNotEmpty() }

    /** Tokens utiles au scoring : les mots vides sautent, sauf s'il ne reste plus rien. */
    fun scoringTokens(input: String): List<String> {
        val tokens = tokenize(input.replace(POLITENESS, " ").replace(WHITESPACE, " ").trim())
        val filtered = tokens.filterNot { it in STOP_WORDS }
        return filtered.ifEmpty { tokens }
    }

    /**
     * « quinze » → « 15 ». Indispensable : le STT écrit tantôt « 15 », tantôt « quinze ».
     * Gère les nombres français jusqu'à 999, « quatre-vingt » et « soixante-dix » compris.
     *
     * « un » / « une » ne sont convertis qu'à l'intérieur d'un nombre déjà commencé
     * (« vingt et un » → 21) : ailleurs ce sont des articles, qui doivent rester des mots
     * pour que la liste de mots vides puisse les écarter.
     */
    fun wordsToDigits(input: String): String {
        val out = StringBuilder()
        var total = 0
        var current = 0
        var inNumber = false
        var pendingEt = false

        fun flush() {
            if (inNumber) {
                out.append(total + current).append(' ')
                total = 0
                current = 0
                inNumber = false
            }
            if (pendingEt) {
                out.append("et ")
                pendingEt = false
            }
        }

        for (token in tokenize(input)) {
            if (token == "et") {
                if (inNumber) pendingEt = true else out.append("et ")
                continue
            }
            val value = valueOf(token, inNumber)
            if (value == null) {
                flush()
                out.append(token).append(' ')
            } else {
                pendingEt = false
                inNumber = true
                when {
                    value == 100 -> {
                        total += maxOf(current, 1) * 100
                        current = 0
                    }
                    // « quatre vingt » : multiplicatif, contrairement à « soixante dix ».
                    value >= 20 && current in 1..9 -> current *= value
                    else -> current += value
                }
            }
        }
        flush()
        return out.toString().trim()
    }

    private fun valueOf(token: String, inNumber: Boolean): Int? = when (token) {
        "un", "une" -> if (inNumber) 1 else null
        else -> NUMBER_WORDS[token]
    }

    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "zero" to 0,
        "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5,
        "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9,
        "dix" to 10, "onze" to 11, "douze" to 12, "treize" to 13,
        "quatorze" to 14, "quinze" to 15, "seize" to 16,
        "vingt" to 20, "vingts" to 20, "trente" to 30, "quarante" to 40,
        "cinquante" to 50, "soixante" to 60,
        "cent" to 100, "cents" to 100
    )
}
