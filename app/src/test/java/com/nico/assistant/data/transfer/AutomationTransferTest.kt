package com.nico.assistant.data.transfer

import com.nico.assistant.action.ActionType
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.ConditionType
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTransferTest {

    private val modeNuit = Automation(
        name = "Mode nuit",
        phrases = listOf("bonne nuit", "je vais dormir"),
        matchMode = MatchMode.CONTAINS,
        priority = 3,
        confirmBeforeRun = true,
        feedbackText = "Bonne nuit",
        conditions = listOf(
            Condition(
                type = ConditionType.TIME_RANGE,
                params = mapOf("start" to "22:00", "end" to "07:00"),
                negated = true
            )
        ),
        actions = listOf(
            ActionSpec(type = ActionType.TOGGLE_DND, params = mapOf("state" to "on"), critical = true),
            ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to "Bonne nuit"), delayMsBefore = 250)
        ),
        runCount = 17
    )

    @Test
    fun `un aller-retour complet ne perd rien d essentiel`() {
        val restored = AutomationTransfer.import(AutomationTransfer.export(listOf(modeNuit)))
            .getOrThrow()
            .single()

        assertEquals(modeNuit.id, restored.id)
        assertEquals(modeNuit.name, restored.name)
        assertEquals(modeNuit.phrases, restored.phrases)
        assertEquals(modeNuit.matchMode, restored.matchMode)
        assertEquals(modeNuit.priority, restored.priority)
        assertEquals(modeNuit.confirmBeforeRun, restored.confirmBeforeRun)
        assertEquals(modeNuit.feedbackText, restored.feedbackText)
        assertEquals(modeNuit.actions.map { it.type }, restored.actions.map { it.type })
        assertEquals(mapOf("state" to "on"), restored.actions.first().params)
        assertEquals(true, restored.actions.first().critical)
        assertEquals(250L, restored.actions[1].delayMsBefore)
        assertEquals(1, restored.conditions.size)
        assertEquals(true, restored.conditions.first().negated)
    }

    @Test
    fun `l historique d execution ne voyage pas`() {
        // runCount et lastRunAt sont propres à l'appareil : un fichier partagé ne les porte pas.
        val restored = AutomationTransfer.import(AutomationTransfer.export(listOf(modeNuit)))
            .getOrThrow()
            .single()

        assertEquals(0, restored.runCount)
        assertEquals(null, restored.lastRunAt)
    }

    @Test
    fun `le fichier exporte est lisible et versionne`() {
        val json = AutomationTransfer.export(listOf(modeNuit))

        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("Mode nuit"))
        assertTrue(json.contains("TOGGLE_DND"))
        assertTrue(json.contains("\n"))
    }

    @Test
    fun `importer une copie regenere les identifiants`() {
        val json = AutomationTransfer.export(listOf(modeNuit))

        val copie = AutomationTransfer.import(json, regenerateIds = true).getOrThrow().single()

        assertNotEquals(modeNuit.id, copie.id)
        assertEquals(modeNuit.name, copie.name)
    }

    @Test
    fun `un fichier vide ou invalide echoue sans exception`() {
        assertTrue(AutomationTransfer.import("").isFailure)
        assertTrue(AutomationTransfer.import("pas du json").isFailure)
        assertTrue(AutomationTransfer.import("{}").isFailure)
    }

    @Test
    fun `une version future est refusee`() {
        val result = AutomationTransfer.import("""{"version":99,"automations":[]}""")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("récente"))
    }

    @Test
    fun `un type d action inconnu est ignore, pas fatal`() {
        val json = """
            {"version":1,"automations":[{"id":"a","name":"Test","phrases":["test"],
            "actions":[{"type":"ACTION_QUI_NEXISTE_PAS"},{"type":"SPEAK","params":{"text":"ok"}}]}]}
        """.trimIndent()

        val imported = AutomationTransfer.import(json).getOrThrow().single()

        assertEquals(listOf(ActionType.SPEAK), imported.actions.map { it.type })
    }

    @Test
    fun `un mode de correspondance inconnu retombe sur FUZZY`() {
        val json = """
            {"version":1,"automations":[{"id":"a","name":"Test","phrases":["test"],
            "matchMode":"TELEPATHIE"}]}
        """.trimIndent()

        assertEquals(MatchMode.FUZZY, AutomationTransfer.import(json).getOrThrow().single().matchMode)
    }

    @Test
    fun `une automatisation sans nom est refusee`() {
        val json = """{"version":1,"automations":[{"id":"a","name":"  "}]}"""

        assertTrue(AutomationTransfer.import(json).isFailure)
    }

    @Test
    fun `un catalogue vide s exporte et se relit`() {
        val restored = AutomationTransfer.import(AutomationTransfer.export(emptyList())).getOrThrow()
        assertTrue(restored.isEmpty())
    }
}
