package com.nico.assistant.core.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.core.executor.ActionExecutor
import com.nico.assistant.core.executor.ExecutionContext
import com.nico.assistant.core.executor.Speaker
import com.nico.assistant.core.matching.MatchEngine
import com.nico.assistant.core.stt.SpeechManager
import com.nico.assistant.data.db.AppDatabase
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le pipeline, depuis une phrase déjà transcrite jusqu'au retour vocal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantPipelineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AutomationRepository
    private lateinit var spoken: MutableList<String>
    private lateinit var executed: MutableList<ActionType>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AutomationRepository(db)
        spoken = mutableListOf()
        executed = mutableListOf()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `une phrase reconnue declenche la chaine et le retour vocal`() = runTest {
        repository.save(automation("Mode sortie", "je pars", feedback = "Bonne route"))
        val pipeline = pipeline()

        pipeline.handle("je pars")

        assertEquals(listOf(ActionType.SPEAK), executed)
        assertTrue(pipeline.state.value is AssistantState.Done)
        assertEquals(listOf("Bonne route"), spoken)
    }

    @Test
    fun `l execution alimente le journal et le compteur`() = runTest {
        val automation = automation("Mode sortie", "je pars")
        repository.save(automation)

        pipeline().handle("je pars")

        val logs = repository.observeLogs().first()
        assertEquals(1, logs.size)
        assertEquals("je pars", logs.first().heardText)
        assertEquals(true, logs.first().success)
        assertEquals(1, repository.getById(automation.id)!!.runCount)
    }

    @Test
    fun `deux candidates a egalite font poser la question`() = runTest {
        repository.save(automation("Mode nuit", "bonne nuit"))
        repository.save(automation("Bonsoir", "bonne nuit"))
        val pipeline = pipeline()

        pipeline.handle("bonne nuit")

        val state = pipeline.state.value
        assertTrue("$state", state is AssistantState.Choosing)
        assertEquals(2, (state as AssistantState.Choosing).choices.size)
        assertTrue(executed.isEmpty())
        assertTrue(spoken.first().startsWith("Tu veux"))
    }

    @Test
    fun `le choix de l utilisateur lance la bonne automatisation`() = runTest {
        repository.save(automation("Mode nuit", "bonne nuit"))
        repository.save(automation("Bonsoir", "bonne nuit"))
        val pipeline = pipeline()
        pipeline.handle("bonne nuit")
        val choices = (pipeline.state.value as AssistantState.Choosing).choices

        pipeline.confirm(choices.first { it.automation.name == "Mode nuit" }, "bonne nuit")

        val state = pipeline.state.value
        assertTrue("$state", state is AssistantState.Done)
        assertEquals("Mode nuit", (state as AssistantState.Done).report.automation.name)
    }

    @Test
    fun `une phrase inconnue propose de creer une automatisation`() = runTest {
        repository.save(automation("Mode sortie", "je pars"))
        val pipeline = pipeline()

        pipeline.handle("raconte moi une blague sur les pingouins")

        val state = pipeline.state.value
        assertTrue("$state", state is AssistantState.NotUnderstood)
        assertEquals("raconte moi une blague sur les pingouins", (state as AssistantState.NotUnderstood).heard)
        assertTrue(executed.isEmpty())
        assertEquals(listOf("J'ai pas compris"), spoken)
    }

    @Test
    fun `une phrase inconnue laisse quand meme une trace dans le journal`() = runTest {
        pipeline().handle("blablabla")

        val logs = repository.observeLogs().first()
        assertEquals(1, logs.size)
        assertEquals("blablabla", logs.first().heardText)
        assertEquals(false, logs.first().success)
        assertEquals("NoMatch", logs.first().errorMessage)
    }

    @Test
    fun `les alternatives du STT sont passees au moteur`() = runTest {
        repository.save(automation("Musique", "mets {titre}"))
        val pipeline = pipeline()

        pipeline.handle("mais dans les temps punk", listOf("mets daft punk"))

        assertTrue("${pipeline.state.value}", pipeline.state.value is AssistantState.Done)
        assertEquals(listOf(ActionType.SPEAK), executed)
    }

    // --- Helpers -------------------------------------------------------------

    private fun pipeline() = AssistantPipeline(
        speech = SpeechManager(context),
        engine = MatchEngine(repository),
        executor = ActionExecutor(
            appContext = context,
            speaker = Speaker { spoken += it },
            repository = repository,
            lookup = { type -> fake(type) }
        ),
        repository = repository,
        speaker = Speaker { spoken += it }
    )

    private fun automation(name: String, phrase: String, feedback: String? = null) = Automation(
        name = name,
        phrases = listOf(phrase),
        feedbackText = feedback,
        actions = listOf(ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to "peu importe")))
    )

    private fun fake(actionType: ActionType): Action = object : Action {
        override val type: ActionType = actionType
        override val backend: Backend = Backend.INTERNAL
        override val label: String = actionType.name
        override val category: ActionCategory = ActionCategory.UTILITAIRES
        override val paramsSchema: List<ParamSpec> = emptyList()
        override suspend fun execute(
            ctx: ExecutionContext,
            params: Map<String, String>
        ): ActionResult {
            executed += actionType
            return ActionResult.Success()
        }
    }
}
