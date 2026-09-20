package com.nico.assistant.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nico.assistant.action.ActionType
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.AppDatabase
import com.nico.assistant.data.db.ConditionType
import com.nico.assistant.data.db.ExecutionLogEntity
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CRUD au niveau métier : ce que verront le matching, l'executor et l'UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: AutomationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = AutomationRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sauvegarder puis relire rend l automatisation assemblee`() = runTest {
        val automation = modeSortie()

        repo.save(automation)

        val loaded = repo.getById(automation.id)
        assertNotNull(loaded)
        assertEquals("Mode sortie", loaded!!.name)
        assertEquals(MatchMode.FUZZY, loaded.matchMode)
        assertEquals(listOf("je pars", "je m'en vais"), loaded.phrases)
        assertEquals("Bonne route", loaded.feedbackText)
        assertEquals(
            listOf(ActionType.TOGGLE_WIFI, ActionType.SPEAK),
            loaded.actions.map { it.type }
        )
        assertEquals(mapOf("text" to "Bonne route"), loaded.actions[1].params)
        assertEquals(true, loaded.actions[0].critical)
        assertEquals(500L, loaded.actions[1].delayMsBefore)
        assertEquals(listOf(ConditionType.TIME_RANGE), loaded.conditions.map { it.type })
        assertEquals(true, loaded.conditions.first().negated)
    }

    @Test
    fun `l ordre de la chaine est celui de la liste editee`() = runTest {
        val automation = modeSortie()
        repo.save(automation)

        val reordered = repo.getById(automation.id)!!.let { loaded ->
            loaded.copy(actions = loaded.actions.reversed())
        }
        repo.save(reordered)

        val loaded = repo.getById(automation.id)!!
        assertEquals(
            listOf(ActionType.SPEAK, ActionType.TOGGLE_WIFI),
            loaded.actions.map { it.type }
        )
    }

    @Test
    fun `resauvegarder remplace les enfants sans en laisser d orphelins`() = runTest {
        val automation = modeSortie()
        repo.save(automation)

        repo.save(
            automation.copy(
                name = "Mode sortie v2",
                actions = listOf(ActionSpec(type = ActionType.SHOW_TOAST, params = mapOf("text" to "ok"))),
                conditions = emptyList()
            )
        )

        val loaded = repo.getById(automation.id)!!
        assertEquals(1, repo.count())
        assertEquals("Mode sortie v2", loaded.name)
        assertEquals(listOf(ActionType.SHOW_TOAST), loaded.actions.map { it.type })
        assertTrue(loaded.conditions.isEmpty())
    }

    @Test
    fun `l historique d execution survit a une reedition`() = runTest {
        val automation = modeSortie()
        repo.save(automation)
        repo.markRun(automation.id, ts = 1_700_000_000_000)

        val edited = repo.getById(automation.id)!!.copy(name = "Renommée")
        repo.save(edited)

        val loaded = repo.getById(automation.id)!!
        assertEquals("Renommée", loaded.name)
        assertEquals(1, loaded.runCount)
        assertEquals(1_700_000_000_000L, loaded.lastRunAt)
        assertEquals(automation.createdAt, loaded.createdAt)
    }

    @Test
    fun `observeAll et observeEnabled refletent l etat courant`() = runTest {
        val sortie = modeSortie()
        val nuit = modeSortie(name = "Mode nuit").copy(priority = 5)
        repo.save(sortie)
        repo.save(nuit)

        assertEquals(setOf("Mode sortie", "Mode nuit"), repo.observeAll().first().map { it.name }.toSet())
        assertEquals(2, repo.observeEnabled().first().size)

        repo.setEnabled(nuit.id, false)

        val enabled = repo.observeEnabled().first()
        assertEquals(listOf("Mode sortie"), enabled.map { it.name })
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test
    fun `enabledAutomations sert le moteur de matching, priorite en tete`() = runTest {
        repo.save(modeSortie(name = "Basse priorité"))
        repo.save(modeSortie(name = "Haute priorité").copy(priority = 10))
        repo.save(modeSortie(name = "Désactivée").copy(enabled = false))

        val candidates = repo.enabledAutomations()

        assertEquals(listOf("Haute priorité", "Basse priorité"), candidates.map { it.name })
        assertTrue(candidates.all { it.actions.isNotEmpty() })
    }

    @Test
    fun `supprimer retire l automatisation et sa descendance`() = runTest {
        val automation = modeSortie()
        repo.save(automation)

        repo.delete(automation.id)

        assertNull(repo.getById(automation.id))
        assertEquals(0, repo.count())
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test
    fun `le journal d execution s ecrit et se relit`() = runTest {
        val automation = modeSortie()
        repo.save(automation)

        repo.log(
            ExecutionLogEntity(
                automationId = automation.id,
                heardText = "je pars",
                matchScore = 0.91f,
                success = true,
                timestamp = 10
            )
        )
        repo.log(
            ExecutionLogEntity(
                heardText = "blablabla",
                matchScore = 0.12f,
                success = false,
                errorMessage = "NoMatch",
                timestamp = 20
            )
        )

        assertEquals(2, repo.logCount())
        assertEquals(listOf("blablabla", "je pars"), repo.observeLogs().first().map { it.heardText })
        assertEquals(listOf("je pars"), repo.observeLogsFor(automation.id).first().map { it.heardText })

        repo.pruneLogsBefore(15)
        assertEquals(listOf("blablabla"), repo.observeLogs().first().map { it.heardText })

        repo.clearLogs()
        assertEquals(0, repo.logCount())
    }

    @Test
    fun `un log survit a la suppression de son automatisation`() = runTest {
        val automation = modeSortie()
        repo.save(automation)
        repo.log(
            ExecutionLogEntity(
                automationId = automation.id,
                heardText = "je pars",
                matchScore = 0.91f,
                success = true
            )
        )

        repo.delete(automation.id)

        assertEquals(1, repo.logCount())
    }

    // --- Helpers -------------------------------------------------------------

    private fun modeSortie(name: String = "Mode sortie") = Automation(
        name = name,
        phrases = listOf("je pars", "je m'en vais"),
        feedbackText = "Bonne route",
        conditions = listOf(
            Condition(
                type = ConditionType.TIME_RANGE,
                params = mapOf("start" to "07:00", "end" to "20:00"),
                negated = true
            )
        ),
        actions = listOf(
            ActionSpec(
                type = ActionType.TOGGLE_WIFI,
                params = mapOf("state" to "off"),
                critical = true
            ),
            ActionSpec(
                type = ActionType.SPEAK,
                params = mapOf("text" to "Bonne route"),
                delayMsBefore = 500
            )
        )
    )
}
