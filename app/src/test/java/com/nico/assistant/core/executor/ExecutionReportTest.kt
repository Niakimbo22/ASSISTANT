package com.nico.assistant.core.executor

import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionReportTest {

    @Test
    fun `une chaine entierement reussie annonce OK`() {
        val report = report(ActionResult.Success(), ActionResult.Success())

        assertTrue(report.success)
        assertEquals(2, report.succeeded)
        assertEquals("OK, Mode sortie", report.feedbackText())
        assertNull(report.errorMessage)
    }

    @Test
    fun `un echec partiel est annonce chiffre`() {
        val report = report(ActionResult.Success(), ActionResult.Failure("Shizuku indisponible"))

        assertFalse(report.success)
        assertEquals("Mode sortie, 1 action sur 2", report.feedbackText())
        assertEquals("Shizuku indisponible", report.errorMessage)
    }

    @Test
    fun `un echec total le dit franchement`() {
        val report = report(ActionResult.Failure("rien"), ActionResult.Failure("non plus"))

        assertEquals("Mode sortie a échoué", report.feedbackText())
        assertEquals("rien", report.errorMessage)
    }

    @Test
    fun `le pluriel suit le nombre d actions reussies`() {
        val trois = ExecutionReport(
            automation = automation(3),
            outcomes = listOf(
                ActionOutcome(spec(0), ActionResult.Success()),
                ActionOutcome(spec(1), ActionResult.Success()),
                ActionOutcome(spec(2), ActionResult.Failure("non"))
            )
        )
        assertEquals("Mode sortie, 2 actions sur 3", trois.feedbackText())
    }

    @Test
    fun `le feedback personnalise prime et voit les slots`() {
        val auto = automation(1).copy(feedbackText = "Je mets {titre}")
        val report = ExecutionReport(
            automation = auto,
            outcomes = listOf(ActionOutcome(spec(0), ActionResult.Success())),
            slots = mapOf("titre" to "daft punk")
        )

        assertEquals("Je mets daft punk", report.feedbackText())
    }

    @Test
    fun `une chaine interrompue n est pas un succes`() {
        val report = ExecutionReport(
            automation = automation(2),
            outcomes = listOf(ActionOutcome(spec(0), ActionResult.Success())),
            stoppedEarly = true
        )

        assertFalse(report.success)
        assertEquals("Mode sortie, 1 action sur 2", report.feedbackText())
    }

    @Test
    fun `une automatisation sans action le dit`() {
        val report = ExecutionReport(automation(0), emptyList())
        assertEquals("Mode sortie n'a aucune action", report.feedbackText())
    }

    @Test
    fun `le journal reprend le score et l erreur`() {
        val report = report(ActionResult.Failure("boom"))

        val log = report.toLog(heardText = "je pars", matchScore = 0.82f, timestamp = 42)

        assertEquals("je pars", log.heardText)
        assertEquals(0.82f, log.matchScore, 0.0001f)
        assertEquals(false, log.success)
        assertEquals("boom", log.errorMessage)
        assertEquals(42L, log.timestamp)
    }

    // --- Helpers -------------------------------------------------------------

    private fun spec(index: Int) = ActionSpec(id = "action-$index", type = ActionType.SPEAK)

    private fun automation(actionCount: Int) = Automation(
        name = "Mode sortie",
        phrases = listOf("je pars"),
        actions = (0 until actionCount).map { spec(it) }
    )

    private fun report(vararg results: ActionResult) = ExecutionReport(
        automation = automation(results.size),
        outcomes = results.mapIndexed { index, result -> ActionOutcome(spec(index), result) }
    )
}
