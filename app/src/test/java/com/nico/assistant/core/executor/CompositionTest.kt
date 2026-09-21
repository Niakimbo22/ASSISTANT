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
import com.nico.assistant.action.impl.RunAutomationAction
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.core.condition.ConditionEvaluator
import com.nico.assistant.data.db.AppDatabase
import com.nico.assistant.data.db.ConditionType
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationRepository
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
import java.util.Calendar

/** Composition d'automatisations et conditions d'exécution (lot 7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompositionTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: AutomationRepository
    private lateinit var executed: MutableList<String>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AutomationRepository(db)
        executed = mutableListOf()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- Composition ---------------------------------------------------------

    @Test
    fun `une automatisation peut en lancer une autre`() = runTest {
        val cible = leaf("Couper le son")
        val appelante = caller("Mode nuit", cible.id)
        repository.save(cible)
        repository.save(appelante)

        val report = executor().run(appelante)

        assertTrue(describe(report), report.success)
        assertEquals(listOf("Couper le son"), executed)
    }

    @Test
    fun `une boucle est detectee au lieu de tourner sans fin`() = runTest {
        val a = Automation(name = "A", phrases = listOf("a"))
        val b = Automation(name = "B", phrases = listOf("b"))
        repository.save(a.copy(actions = listOf(runAction(b.id))))
        repository.save(b.copy(actions = listOf(runAction(a.id))))

        val report = executor().run(repository.getById(a.id)!!)

        assertFalse(report.success)
        assertTrue(describe(report), report.errorMessage.orEmpty().contains("Boucle"))
    }

    @Test
    fun `une automatisation qui s appelle elle-meme est arretee`() = runTest {
        val a = Automation(name = "A", phrases = listOf("a"))
        repository.save(a.copy(actions = listOf(runAction(a.id))))

        val report = executor().run(repository.getById(a.id)!!)

        assertFalse(report.success)
        assertTrue(describe(report), report.errorMessage.orEmpty().contains("Boucle"))
    }

    @Test
    fun `la profondeur de composition est bornee`() = runTest {
        // Sept automatisations en chaîne : la sixième ne doit plus pouvoir appeler la suivante.
        val chain = (1..7).map { Automation(name = "Étape $it", phrases = listOf("etape $it")) }
        for ((index, automation) in chain.withIndex()) {
            val next = chain.getOrNull(index + 1)
            repository.save(
                automation.copy(
                    actions = if (next == null) listOf(speakAction(automation.name))
                    else listOf(runAction(next.id))
                )
            )
        }

        val report = executor().run(repository.getById(chain.first().id)!!)

        assertFalse(describe(report), report.success)
        assertTrue(describe(report), report.errorMessage.orEmpty().contains("profonde"))
    }

    @Test
    fun `une automatisation introuvable echoue proprement`() = runTest {
        val appelante = caller("Mode nuit", "identifiant-inexistant")
        repository.save(appelante)

        val report = executor().run(appelante)

        assertFalse(report.success)
        assertTrue(describe(report), report.errorMessage.orEmpty().contains("introuvable"))
    }

    // --- Conditions ----------------------------------------------------------

    @Test
    fun `une automatisation hors de sa plage horaire ne se declenche pas`() = runTest {
        val automation = leaf("Mode nuit").copy(
            conditions = listOf(
                Condition(
                    type = ConditionType.TIME_RANGE,
                    params = mapOf("start" to "22:00", "end" to "07:00")
                )
            )
        )
        repository.save(automation)

        val report = executor(hour = 14).run(automation)

        assertFalse(report.success)
        assertTrue(executed.isEmpty())
        assertTrue(describe(report), report.feedbackText().contains("non remplie"))
    }

    @Test
    fun `la meme automatisation se declenche dans sa plage`() = runTest {
        val automation = leaf("Mode nuit").copy(
            conditions = listOf(
                Condition(
                    type = ConditionType.TIME_RANGE,
                    params = mapOf("start" to "22:00", "end" to "07:00")
                )
            )
        )
        repository.save(automation)

        val report = executor(hour = 23).run(automation)

        assertTrue(describe(report), report.success)
        assertEquals(listOf("Mode nuit"), executed)
    }

    @Test
    fun `une condition inversee fait l exact contraire`() = runTest {
        val automation = leaf("Mode jour").copy(
            conditions = listOf(
                Condition(
                    type = ConditionType.TIME_RANGE,
                    params = mapOf("start" to "22:00", "end" to "07:00"),
                    negated = true
                )
            )
        )
        repository.save(automation)

        assertTrue(executor(hour = 14).run(automation).success)
        executed.clear()
        assertFalse(executor(hour = 23).run(automation).success)
    }

    @Test
    fun `le test manuel ignore les conditions`() = runTest {
        val automation = leaf("Mode nuit").copy(
            conditions = listOf(
                Condition(
                    type = ConditionType.TIME_RANGE,
                    params = mapOf("start" to "22:00", "end" to "07:00")
                )
            )
        )

        val report = executor(hour = 14).run(automation, checkConditions = false)

        assertTrue(describe(report), report.success)
        assertEquals(listOf("Mode nuit"), executed)
    }

    @Test
    fun `une condition bloquante laisse une trace dans le journal`() = runTest {
        val automation = leaf("Mode nuit").copy(
            conditions = listOf(Condition(type = ConditionType.BATTERY_BELOW, params = mapOf("level" to "0")))
        )
        repository.save(automation)

        executor().run(automation, heardText = "bonne nuit")

        val logs = repository.observeLogsFor(automation.id).first()
        assertEquals(1, logs.size)
        assertEquals(false, logs.first().success)
        assertTrue(logs.first().errorMessage.orEmpty().contains("Condition"))
    }

    // --- Helpers -------------------------------------------------------------

    private fun executor(hour: Int = 12) = ActionExecutor(
        appContext = context,
        speaker = Speaker.SILENT,
        repository = repository,
        lookup = { type ->
            if (type == ActionType.RUN_AUTOMATION) RunAutomationAction() else fake(type)
        },
        conditions = ConditionEvaluator(context) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
            }
        }
    )

    private fun leaf(name: String) = Automation(
        name = name,
        phrases = listOf(name.lowercase()),
        actions = listOf(speakAction(name))
    )

    private fun caller(name: String, targetId: String) = Automation(
        name = name,
        phrases = listOf(name.lowercase()),
        actions = listOf(runAction(targetId))
    )

    private fun speakAction(marker: String) =
        ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to marker))

    private fun runAction(targetId: String) = ActionSpec(
        type = ActionType.RUN_AUTOMATION,
        params = mapOf(RunAutomationAction.PARAM_AUTOMATION_ID to targetId)
    )

    private fun describe(report: ExecutionReport): String =
        "success=${report.success} blocked=${report.blockedBy} error=${report.errorMessage} exécutées=$executed"

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
            params["text"]?.let { executed += it }
            return ActionResult.Success()
        }
    }
}
