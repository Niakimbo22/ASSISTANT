package com.nico.assistant.core.executor

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.data.db.AppDatabase
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionExecutorTest {

    private lateinit var context: Context
    private lateinit var speaker: RecordingSpeaker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        speaker = RecordingSpeaker()
    }

    /** Critère de validation du lot 3 : une chaîne de 3 actions s'exécute de bout en bout. */
    @Test
    fun `une chaine de trois actions se deroule entierement`() = runTest {
        val executed = mutableListOf<String>()
        val executor = executor { type ->
            fake(type) { _, params ->
                executed += "${type.name}:${params["text"].orEmpty()}"
                ActionResult.Success()
            }
        }

        val report = executor.run(
            automation(
                ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to "un")),
                ActionSpec(type = ActionType.WAIT, params = mapOf("text" to "deux")),
                ActionSpec(type = ActionType.SHOW_TOAST, params = mapOf("text" to "trois"))
            )
        )

        assertEquals(listOf("SPEAK:un", "WAIT:deux", "SHOW_TOAST:trois"), executed)
        assertTrue(report.success)
        assertEquals(3, report.succeeded)
        assertEquals("OK, Mode sortie", report.feedbackText())
    }

    @Test
    fun `une action non critique qui echoue ne coupe pas la chaine`() = runTest {
        val executed = mutableListOf<ActionType>()
        val executor = executor { type ->
            fake(type) { _, _ ->
                executed += type
                if (type == ActionType.OPEN_URL) ActionResult.Failure("pas de navigateur")
                else ActionResult.Success()
            }
        }

        val report = executor.run(
            automation(
                ActionSpec(type = ActionType.OPEN_URL, critical = false),
                ActionSpec(type = ActionType.SPEAK)
            )
        )

        assertEquals(listOf(ActionType.OPEN_URL, ActionType.SPEAK), executed)
        assertFalse(report.success)
        assertFalse(report.stoppedEarly)
        assertEquals(1, report.succeeded)
        assertEquals("pas de navigateur", report.errorMessage)
    }

    @Test
    fun `une action critique qui echoue arrete tout`() = runTest {
        val executed = mutableListOf<ActionType>()
        val executor = executor { type ->
            fake(type) { _, _ ->
                executed += type
                if (type == ActionType.OPEN_URL) ActionResult.Failure("pas de navigateur")
                else ActionResult.Success()
            }
        }

        val report = executor.run(
            automation(
                ActionSpec(type = ActionType.OPEN_URL, critical = true),
                ActionSpec(type = ActionType.SPEAK)
            )
        )

        assertEquals(listOf(ActionType.OPEN_URL), executed)
        assertTrue(report.stoppedEarly)
        assertFalse(report.success)
    }

    @Test
    fun `une action qui ne repond pas est abandonnee au bout de son delai`() = runTest {
        val executor = executor { type ->
            fake(type) { _, _ ->
                delay(Long.MAX_VALUE / 2)
                ActionResult.Success()
            }
        }

        val report = executor.run(automation(ActionSpec(type = ActionType.SPEAK)))

        assertFalse(report.success)
        assertTrue(report.errorMessage.orEmpty().contains("temps"))
    }

    @Test
    fun `une action qui leve une exception est rattrapee`() = runTest {
        val executor = executor { type -> fake(type) { _, _ -> error("boum") } }

        val report = executor.run(automation(ActionSpec(type = ActionType.SPEAK)))

        assertFalse(report.success)
        assertEquals("boum", report.errorMessage)
    }

    @Test
    fun `un type pas encore implemente echoue proprement sans couper la chaine`() = runTest {
        val executed = mutableListOf<ActionType>()
        val executor = executor { type ->
            if (type == ActionType.TOGGLE_WIFI) null
            else fake(type) { _, _ -> executed += type; ActionResult.Success() }
        }

        val report = executor.run(
            automation(
                ActionSpec(type = ActionType.TOGGLE_WIFI),
                ActionSpec(type = ActionType.SPEAK)
            )
        )

        assertEquals(listOf(ActionType.SPEAK), executed)
        assertEquals(1, report.succeeded)
        assertTrue(report.errorMessage.orEmpty().contains("TOGGLE_WIFI"))
    }

    @Test
    fun `une action dont le backend est indisponible est refusee avant execution`() = runTest {
        var appele = false
        val executor = ActionExecutor(
            appContext = context,
            speaker = speaker,
            lookup = { type -> fake(type, actionBackend = Backend.SHIZUKU) { _, _ -> appele = true; ActionResult.Success() } },
            availability = BackendAvailability.INTENT_AND_INTERNAL
        )

        val report = executor.run(automation(ActionSpec(type = ActionType.TOGGLE_WIFI)))

        assertFalse(appele)
        assertTrue(report.errorMessage.orEmpty().contains("SHIZUKU"))
    }

    @Test
    fun `les slots sont resolus dans les parametres avant l execution`() = runTest {
        var recu: Map<String, String> = emptyMap()
        val executor = executor { type -> fake(type) { _, params -> recu = params; ActionResult.Success() } }

        executor.run(
            automation(ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to "Je mets {titre}"))),
            slots = mapOf("titre" to "daft punk")
        )

        assertEquals("Je mets daft punk", recu["text"])
    }

    @Test
    fun `les slots systeme sont disponibles sans avoir ete captures`() = runTest {
        var recu: Map<String, String> = emptyMap()
        val executor = executor { type -> fake(type) { _, params -> recu = params; ActionResult.Success() } }

        executor.run(
            automation(ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to "Il est {heure}")))
        )

        assertTrue(recu["text"].orEmpty().startsWith("Il est "))
        assertFalse(SlotResolver.hasUnresolved(recu["text"].orEmpty()))
    }

    @Test
    fun `un slot capture prime sur le slot systeme du meme nom`() = runTest {
        var recu: Map<String, String> = emptyMap()
        val executor = executor { type -> fake(type) { _, params -> recu = params; ActionResult.Success() } }

        executor.run(
            automation(ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to "{heure}"))),
            slots = mapOf("heure" to "midi pile")
        )

        assertEquals("midi pile", recu["text"])
    }

    @Test
    fun `l execution alimente le journal et le compteur`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = AutomationRepository(db)
        val automation = automation(ActionSpec(type = ActionType.SPEAK))
        repo.save(automation)

        val executor = ActionExecutor(
            appContext = context,
            speaker = speaker,
            repository = repo,
            lookup = { type -> fake(type) { _, _ -> ActionResult.Success() } },
            clock = { 1_700_000_000_000 }
        )

        try {
            executor.run(automation, heardText = "je pars", matchScore = 0.91f)

            val logs = repo.observeLogs().first()
            assertEquals(1, logs.size)
            assertEquals("je pars", logs.first().heardText)
            assertEquals(true, logs.first().success)
            assertEquals(automation.id, logs.first().automationId)

            val reloaded = repo.getById(automation.id)!!
            assertEquals(1, reloaded.runCount)
            assertEquals(1_700_000_000_000L, reloaded.lastRunAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun `une automatisation sans action rend un rapport vide plutot qu une erreur`() = runTest {
        val report = executor { type -> fake(type) { _, _ -> ActionResult.Success() } }
            .run(automation())

        assertTrue(report.outcomes.isEmpty())
        assertEquals("Mode sortie n'a aucune action", report.feedbackText())
    }

    // --- Helpers -------------------------------------------------------------

    private class RecordingSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        override fun speak(text: String) {
            spoken += text
        }
    }

    private fun executor(lookup: (ActionType) -> Action?) = ActionExecutor(
        appContext = context,
        speaker = speaker,
        lookup = lookup
    )

    private fun automation(vararg actions: ActionSpec) = Automation(
        name = "Mode sortie",
        phrases = listOf("je pars"),
        actions = actions.toList()
    )

    private fun fake(
        actionType: ActionType,
        actionBackend: Backend = Backend.INTERNAL,
        body: suspend (ExecutionContext, Map<String, String>) -> ActionResult
    ): Action = object : Action {
        override val type: ActionType = actionType
        override val backend: Backend = actionBackend
        override val label: String = actionType.name
        override val category: ActionCategory = ActionCategory.UTILITAIRES
        override val paramsSchema: List<ParamSpec> = emptyList()
        override suspend fun execute(
            ctx: ExecutionContext,
            params: Map<String, String>
        ): ActionResult = body(ctx, params)
    }
}
