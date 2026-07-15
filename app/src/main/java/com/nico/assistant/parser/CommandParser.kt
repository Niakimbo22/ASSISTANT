package com.nico.assistant.parser

import com.nico.assistant.util.TextUtils

/** Intentions reconnues localement. */
enum class Intent { MUSIC, CALL, OPEN_APP, UNKNOWN }

/**
 * Résultat de l'analyse d'une commande.
 * @param intent l'action détectée
 * @param argument l'argument nettoyé (titre/artiste, contact, ou nom d'appli)
 * @param rawArgument l'argument avant retrait des mots vides (utile au débogage)
 */
data class ParsedCommand(
    val intent: Intent,
    val argument: String,
    val rawArgument: String = argument,
)

/**
 * Analyse locale, 100% hors-ligne et basée sur des règles.
 *
 * Principe : on détecte l'intention via des mots-clés en tête de phrase, on les
 * retire, et le reste devient l'argument. Le cas ambigu « lance » (musique OU
 * appli) est tranché en confrontant le reste à la liste des applis installées.
 */
object CommandParser {

    // Déclencheurs par intention. « lance » est volontairement ABSENT ici :
    // il est ambigu et traité séparément.
    private val CALL_TRIGGERS = listOf("telephone a", "appelle", "appel", "telephone")
    private val OPEN_TRIGGERS =
        listOf("lance l application", "ouvre l application", "ouvre", "demarre", "ouvrir")
    private val MUSIC_TRIGGERS = listOf("mets", "joue", "ecoute", "musique", "balance")

    // Mots vides retirés en tête de l'argument (« du Werenoi » -> « Werenoi »).
    private val FILLERS = listOf(
        "un peu de", "de la", "des", "du", "de", "le", "la", "les", "l", "d",
        "moi", "nous",
    )

    /**
     * @param rawText texte reconnu (français, tokens anglais possibles)
     * @param installedAppLabels libellés d'applis installées, pour trancher « lance »
     */
    fun parse(rawText: String, installedAppLabels: List<String>): ParsedCommand {
        val original = rawText.trim()
        if (original.isEmpty()) return ParsedCommand(Intent.UNKNOWN, "")

        val norm = TextUtils.normalize(original)

        // 1) APPEL — priorité haute, « appelle » n'entre en conflit avec rien.
        matchTrigger(norm, original, CALL_TRIGGERS)?.let { arg ->
            return ParsedCommand(Intent.CALL, stripFillers(arg), arg)
        }

        // 2) OUVERTURE d'appli explicite (« ouvre », « lance l'application », …).
        matchTrigger(norm, original, OPEN_TRIGGERS)?.let { arg ->
            return ParsedCommand(Intent.OPEN_APP, stripFillers(arg), arg)
        }

        // 3) MUSIQUE explicite (hors « lance »).
        matchTrigger(norm, original, MUSIC_TRIGGERS)?.let { arg ->
            return ParsedCommand(Intent.MUSIC, stripFillers(arg), arg)
        }

        // 4) Cas ambigu « lance … » : appli si ça matche un libellé installé,
        //    sinon on considère que c'est de la musique.
        matchTrigger(norm, original, listOf("lance"))?.let { arg ->
            val cleaned = stripFillers(arg)
            val looksLikeApp = installedAppLabels.any { label ->
                TextUtils.fuzzyScore(cleaned, label) >= 0.8f
            }
            val intent = if (looksLikeApp) Intent.OPEN_APP else Intent.MUSIC
            return ParsedCommand(intent, cleaned, arg)
        }

        return ParsedCommand(Intent.UNKNOWN, original)
    }

    /**
     * Si [norm] commence par l'un des [triggers] (comparaison normalisée),
     * retourne l'argument correspondant extrait du texte ORIGINAL (casse
     * préservée, essentielle pour les noms propres anglais type « Travis Scott »).
     */
    private fun matchTrigger(
        norm: String,
        original: String,
        triggers: List<String>,
    ): String? {
        // On teste les déclencheurs les plus longs d'abord (« lance l application »
        // avant « lance ») pour éviter les faux positifs.
        for (trigger in triggers.sortedByDescending { it.length }) {
            if (norm == trigger) return ""
            if (norm.startsWith("$trigger ")) {
                // On compte les mots du déclencheur pour découper le texte original
                // au même endroit tout en gardant la casse d'origine. On convertit
                // les apostrophes en espaces AUSSI dans l'original pour que le
                // découpage reste aligné avec le déclencheur normalisé
                // (« lance l'application X » -> tokens [lance, l, application, X]).
                val triggerWordCount = trigger.split(" ").size
                val originalForSplit = original.trim()
                    .replace('\'', ' ').replace('’', ' ')
                val originalWords = originalForSplit.split(Regex("\\s+"))
                if (originalWords.size > triggerWordCount) {
                    return originalWords.drop(triggerWordCount).joinToString(" ")
                }
                return ""
            }
        }
        return null
    }

    /** Retire les mots vides en tête (du, de la, le, …), de façon répétée. */
    private fun stripFillers(arg: String): String {
        var words = arg.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        var changed = true
        while (changed && words.isNotEmpty()) {
            changed = false
            // On teste d'abord les fillers multi-mots (« un peu de »).
            for (filler in FILLERS.sortedByDescending { it.split(" ").size }) {
                val fWords = filler.split(" ")
                if (words.size > fWords.size) {
                    val head = words.take(fWords.size).joinToString(" ")
                    if (TextUtils.normalize(head) == filler) {
                        repeat(fWords.size) { words.removeAt(0) }
                        changed = true
                        break
                    }
                }
            }
        }
        return words.joinToString(" ").trim()
    }
}
