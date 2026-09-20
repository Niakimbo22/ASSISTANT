package com.nico.assistant.core.matching

/**
 * Compilation d'une phrase à trous en expression régulière (spec §4.2).
 *
 * « mets un timer de {duree} minutes » → `^mets un timer de (.+?) minutes$`
 *
 * Les groupes sont positionnels plutôt que nommés : les groupes nommés de Java imposent
 * des identifiants alphanumériques, alors qu'un slot peut s'appeler `mon_slot`.
 */
object SlotExtractor {

    private val PLACEHOLDER = Regex("\\{(\\w+)\\}")

    fun hasSlots(phrase: String): Boolean = PLACEHOLDER.containsMatchIn(phrase)

    fun slotNames(phrase: String): List<String> =
        PLACEHOLDER.findAll(phrase).map { it.groupValues[1] }.toList()

    /** La phrase privée de ses trous, utilisée pour scorer la partie fixe en cas d'échec. */
    fun skeleton(phrase: String): String =
        PLACEHOLDER.replace(phrase, " ").replace(Regex("\\s+"), " ").trim()

    fun compile(phrase: String): SlotPattern {
        val names = mutableListOf<String>()
        val pattern = StringBuilder()
        var cursor = 0

        for (match in PLACEHOLDER.findAll(phrase)) {
            pattern.append(Regex.escape(phrase.substring(cursor, match.range.first)))
            pattern.append("(.+?)")
            names += match.groupValues[1]
            cursor = match.range.last + 1
        }
        pattern.append(Regex.escape(phrase.substring(cursor)))

        return SlotPattern(Regex(pattern.toString(), RegexOption.IGNORE_CASE), names)
    }

    /** Raccourci : renvoie les slots capturés, ou `null` si la phrase ne colle pas. */
    fun extract(input: String, phrase: String): Map<String, String>? =
        compile(phrase).match(input)
}

/** Une phrase compilée, réutilisable : la compilation regex n'est pas gratuite. */
class SlotPattern(val regex: Regex, val slotNames: List<String>) {

    fun match(input: String): Map<String, String>? {
        val result = regex.matchEntire(input) ?: return null
        if (slotNames.isEmpty()) return emptyMap()
        return slotNames
            .mapIndexed { index, name -> name to result.groupValues[index + 1].trim() }
            .filter { (_, value) -> value.isNotEmpty() }
            .toMap()
    }
}
