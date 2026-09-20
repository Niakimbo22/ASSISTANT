package com.nico.assistant.core.matching

import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Une automatisation candidate, avec ce qu'on a réussi à en extraire. */
data class MatchResult(
    val automation: Automation,
    val slots: Map<String, String>,
    /** 0.0 → 1.0 */
    val score: Float
)

/** Ce que le moteur conclut d'une phrase entendue (spec §4.4). */
sealed interface MatchOutcome {
    data class Confident(val result: MatchResult) : MatchOutcome
    data class Ambiguous(val top: List<MatchResult>) : MatchOutcome
    data object NoMatch : MatchOutcome
}

/** Seuils réglables depuis les paramètres (spec §4.4). */
data class MatchThresholds(
    val confident: Float = 0.75f,
    val minimumGap: Float = 0.15f,
    val ambiguousFloor: Float = 0.45f,
    val maxSuggestions: Int = 3
)

/**
 * Remplace intégralement la cascade de `if (texte.contains(...))` de la V1.
 *
 * Aucune commande n'est connue du code : le moteur confronte ce qui a été entendu
 * aux phrases présentes en base, extrait les slots et rend un verdict.
 */
class MatchEngine(
    private val source: AutomationSource,
    val thresholds: MatchThresholds = MatchThresholds(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * @param alternatives les hypothèses secondaires du STT (`EXTRA_MAX_RESULTS`). Les tester
     * toutes est ce qui règle l'essentiel du problème des noms propres mal reconnus.
     */
    suspend fun match(heard: String, alternatives: List<String> = emptyList()): MatchOutcome {
        val automations = source.enabledAutomations()
        return withContext(dispatcher) { matchAgainst(heard, alternatives, automations) }
    }

    /** Cœur du moteur, sans I/O ni coroutine : c'est lui que couvrent les tests. */
    fun matchAgainst(
        heard: String,
        alternatives: List<String>,
        automations: List<Automation>
    ): MatchOutcome {
        val inputs = (listOf(heard) + alternatives)
            .map(TextNormalizer::normalize)
            .filter { it.isNotEmpty() }
            .distinct()
        if (inputs.isEmpty()) return MatchOutcome.NoMatch

        val systemSlots = mapOf(SLOT_TEXTE to heard.trim())
        val candidates = mutableListOf<MatchResult>()

        for (automation in automations) {
            var best = 0f
            var bestSlots = emptyMap<String, String>()

            for (input in inputs) {
                for (phrase in automation.phrases) {
                    val scored = scorePhrase(input, phrase, automation.matchMode)
                    if (scored.score > best) {
                        best = scored.score
                        bestSlots = scored.slots
                    }
                }
            }
            // Les slots capturés priment sur les slots système, jamais l'inverse.
            if (best > 0f) candidates += MatchResult(automation, systemSlots + bestSlots, best)
        }

        val sorted = candidates.sortedWith(
            compareByDescending<MatchResult> { it.score }
                .thenByDescending { it.automation.priority }
                .thenBy { it.automation.name }
        )
        return classify(sorted)
    }

    fun scorePhrase(input: String, rawPhrase: String, mode: MatchMode): Scored {
        // En mode expert, la phrase EST une regex : la normaliser la détruirait.
        if (mode == MatchMode.REGEX) return scoreRegex(input, rawPhrase)

        val phrase = TextNormalizer.normalize(rawPhrase)
        if (phrase.isEmpty()) return Scored.NONE

        // Une phrase à trous est toujours évaluée par regex, quel que soit le mode.
        if (SlotExtractor.hasSlots(phrase)) return scoreWithSlots(input, phrase)

        return when (mode) {
            MatchMode.EXACT -> Scored(if (input == phrase) 1f else 0f)
            MatchMode.CONTAINS -> scoreContains(input, phrase)
            else -> Scored(FuzzyMatcher.score(input, phrase))
        }
    }

    private fun scoreContains(input: String, phrase: String): Scored {
        if (!input.contains(phrase)) return Scored.NONE
        // Pondéré par le ratio de longueur : la phrase qui explique le plus de ce qui a
        // été dit l'emporte sur celle qui n'en couvre qu'un bout.
        val ratio = (phrase.length.toFloat() / input.length).coerceIn(0f, 1f)
        return Scored(CONTAINS_SCORE * ratio)
    }

    private fun scoreRegex(input: String, pattern: String): Scored = runCatching {
        val found = Regex(pattern, RegexOption.IGNORE_CASE).find(input)
            ?: return@runCatching Scored.NONE
        // Les groupes capturants sont exposés en {g1}, {g2}… utilisables dans les paramètres.
        val slots = found.groupValues.drop(1)
            .mapIndexed { index, value -> "g${index + 1}" to value.trim() }
            .filter { (_, value) -> value.isNotEmpty() }
            .toMap()
        Scored(REGEX_SCORE, slots)
    }.getOrElse { Scored.NONE }

    private fun scoreWithSlots(input: String, phrase: String): Scored {
        val skeleton = SlotExtractor.skeleton(phrase)
        val slots = SlotExtractor.compile(phrase).match(input)

        if (slots != null) {
            // Plus la partie fixe couvre ce qui a été entendu, plus la phrase est spécifique :
            // c'est ce qui départage « mets {titre} » de « mets un timer de {duree} minutes ».
            val coverage = (skeleton.length.toFloat() / input.length).coerceIn(0f, 1f)
            return Scored(SLOT_BASE_SCORE + SLOT_COVERAGE_WEIGHT * coverage, slots)
        }

        // La structure ne colle pas : on note la partie fixe, sans rien capturer.
        if (skeleton.isEmpty()) return Scored.NONE
        return Scored(FuzzyMatcher.score(input, skeleton) * SKELETON_PENALTY)
    }

    private fun classify(sorted: List<MatchResult>): MatchOutcome {
        val top = sorted.firstOrNull() ?: return MatchOutcome.NoMatch

        if (top.score < thresholds.ambiguousFloor) return MatchOutcome.NoMatch
        if (top.score < thresholds.confident) return MatchOutcome.Ambiguous(listOf(top))

        val second = sorted.getOrNull(1)
        if (second == null || top.score - second.score > thresholds.minimumGap) {
            return MatchOutcome.Confident(top)
        }
        // C'est exactement le rôle de `priority` : départager les ex-aequo sans demander.
        if (top.automation.priority > second.automation.priority) {
            return MatchOutcome.Confident(top)
        }
        val close = sorted
            .takeWhile { top.score - it.score <= thresholds.minimumGap }
            .take(thresholds.maxSuggestions)
        return MatchOutcome.Ambiguous(close)
    }

    data class Scored(val score: Float, val slots: Map<String, String> = emptyMap()) {
        companion object {
            val NONE = Scored(0f)
        }
    }

    companion object {
        /** Slot système : la phrase complète entendue, brute. */
        const val SLOT_TEXTE = "texte"

        private const val CONTAINS_SCORE = 0.85f
        private const val REGEX_SCORE = 0.95f
        /**
         * Plancher d'une phrase à trous qui colle : au-dessus du seuil de confiance, pour
         * qu'un titre à rallonge ne fasse pas redemander confirmation.
         */
        private const val SLOT_BASE_SCORE = 0.76f
        private const val SLOT_COVERAGE_WEIGHT = 0.24f
        private const val SKELETON_PENALTY = 0.9f
    }
}
