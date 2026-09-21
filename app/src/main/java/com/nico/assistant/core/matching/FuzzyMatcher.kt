package com.nico.assistant.core.matching

/**
 * Score de ressemblance entre ce qui a été entendu et une phrase stockée (spec §4.3).
 *
 * Les deux chaînes sont supposées déjà normalisées par [TextNormalizer].
 */
object FuzzyMatcher {

    private const val LEVENSHTEIN_WEIGHT = 0.6f
    private const val TOKEN_WEIGHT = 0.4f

    /** Le verbe d'action porte l'intention : « ouvre » vs « ferme » ne doivent pas s'égaler. */
    private const val FIRST_WORD_BONUS = 0.1f

    /** Distance d'édition classique, en O(n·m) temps et O(m) mémoire. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** `1 - distance / max(len)`, borné à [0, 1]. */
    fun similarity(a: String, b: String): Float {
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1f
        return (1f - levenshtein(a, b).toFloat() / longest).coerceIn(0f, 1f)
    }

    /** Part des mots de la phrase stockée qu'on retrouve dans ce qui a été entendu. */
    fun tokenOverlap(input: String, phrase: String): Float {
        val phraseTokens = TextNormalizer.scoringTokens(phrase)
        if (phraseTokens.isEmpty()) return 0f
        val inputTokens = TextNormalizer.scoringTokens(input).toSet()
        val common = phraseTokens.count { it in inputTokens }
        return common.toFloat() / phraseTokens.size
    }

    /**
     * 60 % distance d'édition, 40 % recouvrement de tokens, bonus si le premier mot colle.
     * Le résultat est borné à 1.0.
     */
    fun score(input: String, phrase: String): Float {
        if (input.isEmpty() || phrase.isEmpty()) return 0f

        val base = LEVENSHTEIN_WEIGHT * similarity(input, phrase) +
            TOKEN_WEIGHT * tokenOverlap(input, phrase)

        val inputFirst = TextNormalizer.scoringTokens(input).firstOrNull()
        val phraseFirst = TextNormalizer.scoringTokens(phrase).firstOrNull()
        val bonus = if (inputFirst != null && inputFirst == phraseFirst) FIRST_WORD_BONUS else 0f

        return (base + bonus).coerceIn(0f, 1f)
    }
}
