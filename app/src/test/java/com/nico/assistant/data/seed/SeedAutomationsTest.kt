package com.nico.assistant.data.seed

import com.nico.assistant.action.ActionRegistry
import com.nico.assistant.action.ActionType
import com.nico.assistant.core.matching.MatchEngine
import com.nico.assistant.core.matching.MatchOutcome
import com.nico.assistant.core.matching.SlotExtractor
import com.nico.assistant.core.matching.TextNormalizer
import com.nico.assistant.data.repo.AutomationSource
import com.nico.assistant.data.model.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les seeds reproduisent les commandes de la V1. Si ces tests passent, c'est que
 * l'architecture tient : aucune de ces commandes n'existe dans le code Kotlin.
 */
class SeedAutomationsTest {

    private val seeds = SeedAutomations.all(musicPackage = "app.rvx.android.apps.youtube.music")
    private val engine = MatchEngine(EmptySource)

    @Test
    fun `le catalogue livre couvre les huit automatisations de la spec`() {
        assertEquals(
            listOf(
                "Lancer une app",
                "Musique",
                "Appeler",
                "Quelle heure",
                "Lampe",
                "Pause musique",
                "Mode sortie",
                "Mode nuit"
            ),
            seeds.map { it.name }
        )
    }

    @Test
    fun `chaque seed a un nom, des phrases et au moins une action`() {
        for (seed in seeds) {
            assertTrue(seed.name.isNotBlank())
            assertTrue("${seed.name} sans phrase", seed.phrases.isNotEmpty())
            assertTrue("${seed.name} sans action", seed.actions.isNotEmpty())
            assertTrue("${seed.name} désactivée", seed.enabled)
        }
    }

    @Test
    fun `les slots utilises dans les parametres sont declares dans les phrases`() {
        val systeme = setOf("heure", "date", "batterie", "texte")
        val placeholder = Regex("\\{(\\w+)\\}")

        for (seed in seeds) {
            val declares = seed.phrases.flatMap { SlotExtractor.slotNames(it) }.toSet() + systeme
            for (action in seed.actions) {
                for (value in action.params.values) {
                    for (match in placeholder.findAll(value)) {
                        val slot = match.groupValues[1]
                        assertTrue(
                            "${seed.name} utilise {$slot} sans le déclarer dans ses phrases",
                            slot in declares
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `la musique enchaine la strategie A puis la strategie B`() {
        val musique = seeds.first { it.name == "Musique" }

        assertEquals(
            listOf(ActionType.PLAY_MUSIC_SEARCH, ActionType.PLAY_MUSIC_UI),
            musique.actions.map { it.type }
        )
        // Aucune des deux n'est critique : si A échoue, B doit encore avoir sa chance.
        assertTrue(musique.actions.none { it.critical })
    }

    @Test
    fun `l app musique deja choisie en V1 est reprise telle quelle`() {
        val musique = seeds.first { it.name == "Musique" }

        assertTrue(musique.actions.all { it.params["app"] == "app.rvx.android.apps.youtube.music" })
        // Sans choix préalable, le paramètre reste vide : jamais de paquet codé en dur.
        val sansChoix = SeedAutomations.all(musicPackage = null).first { it.name == "Musique" }
        assertEquals("", sansChoix.actions.first().params["app"])
    }

    @Test
    fun `les commandes de la V1 sont reconnues par le moteur, sans code dedie`() {
        val cases = listOf(
            "Ouvre YouTube" to "Lancer une app",
            "lance Spotify" to "Lancer une app",
            "Mets Daft Punk" to "Musique",
            "écoute France Inter" to "Musique",
            "Appelle maman" to "Appeler",
            "Téléphone à Jean-Michel" to "Appeler",
            "Quelle heure il est ?" to "Quelle heure",
            "allume la lampe" to "Lampe",
            "pause" to "Pause musique",
            "stop la musique" to "Pause musique",
            "je pars" to "Mode sortie",
            "bonne nuit" to "Mode nuit",
            "je vais dormir" to "Mode nuit"
        )

        for ((heard, expected) in cases) {
            val outcome = engine.matchAgainst(heard, emptyList(), seeds)
            assertTrue("« $heard » n'a pas été reconnu : $outcome", outcome is MatchOutcome.Confident)
            assertEquals("« $heard »", expected, (outcome as MatchOutcome.Confident).result.automation.name)
        }
    }

    @Test
    fun `les slots des seeds sont bien capures a l execution`() {
        val outcome = engine.matchAgainst("Ouvre YouTube", emptyList(), seeds)
        val result = (outcome as MatchOutcome.Confident).result

        assertEquals("youtube", result.slots["app"])
        assertEquals("{app}", result.automation.actions.first().params["package"])
    }

    @Test
    fun `les phrases des seeds survivent a la normalisation`() {
        for (seed in seeds) {
            for (phrase in seed.phrases) {
                assertTrue("« $phrase » se normalise en vide", TextNormalizer.normalize(phrase).isNotEmpty())
            }
        }
    }

    @Test
    fun `les actions non encore implementees sont connues et attendues`() {
        val manquantes = seeds
            .flatMap { it.actions }
            .map { it.type }
            .filterNot { ActionRegistry.isImplemented(it) }
            .toSet()

        // Ce qui reste dépend de Shizuku : c'est le lot 6, pas un oubli.
        assertEquals(
            setOf(
                ActionType.TOGGLE_WIFI,
                ActionType.TOGGLE_DND,
                ActionType.SET_VOLUME,
                ActionType.SET_BRIGHTNESS
            ),
            manquantes
        )
    }

    private object EmptySource : AutomationSource {
        override suspend fun enabledAutomations(): List<Automation> = emptyList()
    }
}
