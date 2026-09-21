package com.nico.assistant.core.matching

import com.nico.assistant.action.ActionType
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le moteur est confronté au catalogue *seed* de la spec §12 : ce sont les commandes de la V1,
 * qui doivent fonctionner sans une ligne de code dédiée.
 */
class MatchEngineTest {

    private val engine = MatchEngine(EmptySource)

    // --- Le jeu de paires phrase entendue / automatisation attendue ----------

    @Test
    fun `vingt-cinq phrases entendues tombent sur la bonne automatisation`() {
        val cases = listOf(
            Triple("Ouvre YouTube", "Lancer une app", mapOf("app" to "youtube")),
            Triple("ouvre you tube", "Lancer une app", mapOf("app" to "you tube")),
            Triple("Lance Spotify", "Lancer une app", mapOf("app" to "spotify")),
            Triple("Démarre l'appareil photo", "Lancer une app", mapOf("app" to "l appareil photo")),
            Triple("Mets Daft Punk", "Musique", mapOf("titre" to "daft punk")),
            Triple("Joue du Johnny", "Musique", mapOf("titre" to "du johnny")),
            Triple("Écoute France Inter", "Musique", mapOf("titre" to "france inter")),
            Triple("Appelle maman", "Appeler", mapOf("contact" to "maman")),
            Triple("Téléphone à Jean-Michel", "Appeler", mapOf("contact" to "jean michel")),
            Triple("Quelle heure il est ?", "Quelle heure", emptyMap<String, String>()),
            Triple("Il est quelle heure", "Quelle heure", emptyMap<String, String>()),
            // Fautes typiques du STT
            Triple("quel heure il est", "Quelle heure", emptyMap<String, String>()),
            Triple("je part", "Mode sortie", emptyMap<String, String>()),
            Triple("bonne nuie", "Mode nuit", emptyMap<String, String>()),
            Triple("allume la lempe", "Lampe", emptyMap<String, String>()),
            // Formes exactes et variantes
            Triple("Allume la lampe", "Lampe", emptyMap<String, String>()),
            Triple("lampe torche", "Lampe", emptyMap<String, String>()),
            Triple("Allume la lampe torche", "Lampe", emptyMap<String, String>()),
            Triple("Pause", "Pause musique", emptyMap<String, String>()),
            Triple("stop la musique", "Pause musique", emptyMap<String, String>()),
            Triple("Je pars", "Mode sortie", emptyMap<String, String>()),
            Triple("Je m'en vais", "Mode sortie", emptyMap<String, String>()),
            Triple("Bonne nuit", "Mode nuit", emptyMap<String, String>()),
            Triple("je vais dormir", "Mode nuit", emptyMap<String, String>()),
            // Nombres en lettres, chiffres, et nombres composés
            Triple("mets un timer de quinze minutes", "Timer", mapOf("duree" to "15")),
            Triple("mets un timer de 5 minutes", "Timer", mapOf("duree" to "5")),
            Triple("mets un timer de quatre-vingt-dix minutes", "Timer", mapOf("duree" to "90"))
        )

        for ((heard, expectedName, expectedSlots) in cases) {
            val outcome = engine.matchAgainst(heard, emptyList(), seeds())
            assertTrue(
                "« $heard » aurait dû être reconnu sans ambiguïté, obtenu : ${describe(outcome)}",
                outcome is MatchOutcome.Confident
            )
            val result = (outcome as MatchOutcome.Confident).result
            assertEquals("« $heard »", expectedName, result.automation.name)
            for ((key, value) in expectedSlots) {
                assertEquals("slot {$key} de « $heard »", value, result.slots[key])
            }
        }
    }

    // --- Seuils et désambiguïsation -----------------------------------------

    @Test
    fun `une phrase inconnue ne matche rien`() {
        val outcome = engine.matchAgainst("raconte moi une blague sur les pingouins", emptyList(), seeds())
        assertEquals(MatchOutcome.NoMatch, outcome)
    }

    @Test
    fun `deux automatisations a egalite demandent a choisir`() {
        val doublons = listOf(
            automation("Mode nuit", listOf("bonne nuit")),
            automation("Bonsoir", listOf("bonne nuit"))
        )

        val outcome = engine.matchAgainst("bonne nuit", emptyList(), doublons)

        assertTrue(describe(outcome), outcome is MatchOutcome.Ambiguous)
        assertEquals(2, (outcome as MatchOutcome.Ambiguous).top.size)
    }

    @Test
    fun `la priorite departage deux ex-aequo sans demander`() {
        val doublons = listOf(
            automation("Mode nuit", listOf("bonne nuit")).copy(priority = 10),
            automation("Bonsoir", listOf("bonne nuit"))
        )

        val outcome = engine.matchAgainst("bonne nuit", emptyList(), doublons)

        assertTrue(describe(outcome), outcome is MatchOutcome.Confident)
        assertEquals("Mode nuit", (outcome as MatchOutcome.Confident).result.automation.name)
    }

    @Test
    fun `une phrase partielle ne declenche pas toute seule`() {
        val outcome = engine.matchAgainst("stop", emptyList(), seeds())
        assertTrue(describe(outcome), outcome !is MatchOutcome.Confident)
    }

    @Test
    fun `une entree vide ne matche rien`() {
        assertEquals(MatchOutcome.NoMatch, engine.matchAgainst("   ", emptyList(), seeds()))
        assertEquals(MatchOutcome.NoMatch, engine.matchAgainst("ouvre youtube", emptyList(), emptyList()))
    }

    // --- Alternatives du STT -------------------------------------------------

    @Test
    fun `une alternative du STT sauve un match que la premiere hypothese ratait`() {
        val rate = engine.matchAgainst("mais dans les temps punk", emptyList(), seeds())
        assertTrue(describe(rate), rate !is MatchOutcome.Confident)

        val rattrape = engine.matchAgainst(
            "mais dans les temps punk",
            listOf("mets daft punk"),
            seeds()
        )

        assertTrue(describe(rattrape), rattrape is MatchOutcome.Confident)
        assertEquals("Musique", (rattrape as MatchOutcome.Confident).result.automation.name)
    }

    // --- Modes de correspondance --------------------------------------------

    @Test
    fun `le mode EXACT refuse la moindre variation`() {
        val exact = listOf(automation("Sésame", listOf("ouvre toi"), mode = MatchMode.EXACT))

        assertTrue(engine.matchAgainst("ouvre toi", emptyList(), exact) is MatchOutcome.Confident)
        assertEquals(MatchOutcome.NoMatch, engine.matchAgainst("ouvre toi maintenant", emptyList(), exact))
    }

    @Test
    fun `le mode CONTAINS accepte la phrase noyee dans une autre`() {
        val contains = listOf(automation("Sésame", listOf("ouvre toi"), mode = MatchMode.CONTAINS))

        val outcome = engine.matchAgainst("ouvre toi", emptyList(), contains)
        assertTrue(describe(outcome), outcome is MatchOutcome.Confident)

        // Plus la phrase explique une petite part de ce qui a été dit, plus le score baisse.
        val noyee = engine.matchAgainst("alors ouvre toi vraiment tres vite maintenant", emptyList(), contains)
        assertTrue(describe(noyee), noyee !is MatchOutcome.Confident)
    }

    @Test
    fun `le mode REGEX expose ses groupes capturants`() {
        val regex = listOf(
            automation("Expert", listOf("^(?:ouvre|lance) (.+)$"), mode = MatchMode.REGEX)
        )

        val outcome = engine.matchAgainst("Lance Spotify", emptyList(), regex)

        assertTrue(describe(outcome), outcome is MatchOutcome.Confident)
        assertEquals("spotify", (outcome as MatchOutcome.Confident).result.slots["g1"])
    }

    @Test
    fun `une regex invalide ne fait pas tomber le moteur`() {
        val casse = listOf(automation("Cassé", listOf("^(((("), mode = MatchMode.REGEX))
        assertEquals(MatchOutcome.NoMatch, engine.matchAgainst("ouvre youtube", emptyList(), casse))
    }

    // --- Slots ---------------------------------------------------------------

    @Test
    fun `la phrase entendue est toujours disponible en slot systeme`() {
        val outcome = engine.matchAgainst("Ouvre YouTube", emptyList(), seeds())
        val result = (outcome as MatchOutcome.Confident).result

        assertEquals("Ouvre YouTube", result.slots[MatchEngine.SLOT_TEXTE])
        assertEquals("youtube", result.slots["app"])
    }

    @Test
    fun `la phrase la plus specifique l emporte sur la plus generique`() {
        val outcome = engine.matchAgainst("mets un timer de 20 minutes", emptyList(), seeds())
        val result = (outcome as MatchOutcome.Confident).result

        assertEquals("Timer", result.automation.name)
        assertEquals("20", result.slots["duree"])
    }

    // --- Intégration avec la source -----------------------------------------

    @Test
    fun `match lit les automatisations actives depuis la source`() = runTest {
        val source = object : AutomationSource {
            override suspend fun enabledAutomations(): List<Automation> = seeds()
        }

        val outcome = MatchEngine(source).match("Allume la lampe")

        assertTrue(describe(outcome), outcome is MatchOutcome.Confident)
        assertEquals("Lampe", (outcome as MatchOutcome.Confident).result.automation.name)
    }

    // --- Helpers -------------------------------------------------------------

    private object EmptySource : AutomationSource {
        override suspend fun enabledAutomations(): List<Automation> = emptyList()
    }

    private fun describe(outcome: MatchOutcome): String = when (outcome) {
        is MatchOutcome.Confident ->
            "Confident(${outcome.result.automation.name} @ ${outcome.result.score})"
        is MatchOutcome.Ambiguous ->
            "Ambiguous(${outcome.top.map { "${it.automation.name} @ ${it.score}" }})"
        MatchOutcome.NoMatch -> "NoMatch"
    }

    private fun automation(
        name: String,
        phrases: List<String>,
        mode: MatchMode = MatchMode.FUZZY
    ) = Automation(
        name = name,
        phrases = phrases,
        matchMode = mode,
        actions = listOf(ActionSpec(type = ActionType.SHOW_TOAST))
    )

    /** Le catalogue livré par défaut (spec §12). */
    private fun seeds(): List<Automation> = listOf(
        automation("Lancer une app", listOf("ouvre {app}", "lance {app}", "demarre {app}")),
        automation("Musique", listOf("mets {titre}", "joue {titre}", "ecoute {titre}")),
        automation("Appeler", listOf("appelle {contact}", "telephone a {contact}")),
        automation("Quelle heure", listOf("quelle heure il est", "il est quelle heure")),
        automation("Lampe", listOf("allume la lampe", "lampe torche")),
        automation("Pause musique", listOf("pause", "stop la musique")),
        automation("Mode sortie", listOf("je pars", "je m'en vais")),
        automation("Mode nuit", listOf("bonne nuit", "je vais dormir")),
        automation("Timer", listOf("mets un timer de {duree} minutes"))
    )
}
