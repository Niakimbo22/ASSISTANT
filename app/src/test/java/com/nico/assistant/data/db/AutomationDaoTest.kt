package com.nico.assistant.data.db

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nico.assistant.action.ActionType
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

/**
 * CRUD au niveau DAO, sur une vraie base SQLite exécutée sur la JVM via Robolectric —
 * aucun appareil ni émulateur nécessaire, donc exécutable dans GitHub Actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationDaoTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dao: AutomationDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.automationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `une automatisation et ses enfants se relisent a l identique`() = runTest {
        val automation = modeSortie()
        dao.upsertAutomation(automation)
        dao.upsertActions(
            listOf(
                action(automation.id, order = 0, type = ActionType.TOGGLE_WIFI, params = mapOf("state" to "off")),
                action(automation.id, order = 1, type = ActionType.SPEAK, params = mapOf("text" to "Bonne route"))
            )
        )
        dao.upsertConditions(
            listOf(condition(automation.id, ConditionType.TIME_RANGE, mapOf("start" to "07:00", "end" to "20:00")))
        )

        val row = dao.getById(automation.id)

        assertNotNull(row)
        assertEquals("Mode sortie", row!!.automation.name)
        assertEquals(listOf("je pars", "je m'en vais"), row.automation.phrases)
        assertEquals(MatchMode.FUZZY, row.automation.matchMode)
        assertEquals(2, row.actions.size)
        assertEquals(
            listOf(ActionType.TOGGLE_WIFI, ActionType.SPEAK),
            row.actions.sortedBy { it.order }.map { it.type }
        )
        assertEquals("Bonne route", row.actions.single { it.order == 1 }.params["text"])
        assertEquals(1, row.conditions.size)
        assertEquals("07:00", row.conditions.first().params["start"])
    }

    @Test
    fun `getById renvoie null pour un identifiant inconnu`() = runTest {
        assertNull(dao.getById("inexistant"))
    }

    @Test
    fun `observeEnabled ignore les automatisations desactivees`() = runTest {
        val actif = modeSortie()
        val inactif = modeSortie(name = "Mode nuit").copy(enabled = false)
        dao.upsertAutomation(actif)
        dao.upsertAutomation(inactif)

        assertEquals(2, dao.observeAll().first().size)
        assertEquals(listOf(actif.id), dao.observeEnabled().first().map { it.automation.id })
        assertEquals(listOf(actif.id), dao.getEnabled().map { it.automation.id })
    }

    @Test
    fun `setEnabled bascule le flag sans toucher au reste`() = runTest {
        val automation = modeSortie()
        dao.upsertAutomation(automation)

        dao.setEnabled(automation.id, false)

        val row = dao.getById(automation.id)!!.automation
        assertEquals(false, row.enabled)
        assertEquals(automation.name, row.name)
        assertEquals(automation.phrases, row.phrases)
    }

    @Test
    fun `supprimer une automatisation supprime ses actions et ses conditions`() = runTest {
        val automation = modeSortie()
        dao.upsertAutomation(automation)
        dao.upsertActions(listOf(action(automation.id, 0, ActionType.TOGGLE_WIFI)))
        dao.upsertConditions(listOf(condition(automation.id, ConditionType.CHARGING)))
        assertEquals(1, countChildren("actions", automation.id))

        dao.deleteById(automation.id)

        assertEquals(0, dao.count())
        assertEquals(0, countChildren("actions", automation.id))
        assertEquals(0, countChildren("conditions", automation.id))
    }

    @Test
    fun `clearActions purge la chaine avant reinsertion`() = runTest {
        val automation = modeSortie()
        dao.upsertAutomation(automation)
        dao.upsertActions(
            listOf(
                action(automation.id, 0, ActionType.TOGGLE_WIFI),
                action(automation.id, 1, ActionType.SPEAK)
            )
        )

        dao.clearActions(automation.id)
        dao.upsertActions(listOf(action(automation.id, 0, ActionType.SHOW_TOAST)))

        val actions = dao.getById(automation.id)!!.actions
        assertEquals(listOf(ActionType.SHOW_TOAST), actions.map { it.type })
    }

    @Test
    fun `markRun incremente le compteur et date la derniere execution`() = runTest {
        val automation = modeSortie()
        dao.upsertAutomation(automation)
        assertEquals(0, automation.runCount)
        assertNull(automation.lastRunAt)

        dao.markRun(automation.id, ts = 1_700_000_000_000)
        dao.markRun(automation.id, ts = 1_700_000_001_000)

        val row = dao.getById(automation.id)!!.automation
        assertEquals(2, row.runCount)
        assertEquals(1_700_000_001_000L, row.lastRunAt)
    }

    @Test
    fun `le journal d execution se lit du plus recent au plus ancien`() = runTest {
        val logDao = db.executionLogDao()
        logDao.insert(ExecutionLogEntity(heardText = "je pars", matchScore = 0.9f, success = true, timestamp = 100))
        logDao.insert(
            ExecutionLogEntity(
                automationId = "abc",
                heardText = "coupe le wifi",
                matchScore = 0.42f,
                success = false,
                errorMessage = "Shizuku indisponible",
                timestamp = 200
            )
        )

        val logs = logDao.observeRecent(limit = 10).first()

        assertEquals(2, logDao.count())
        assertEquals(listOf("coupe le wifi", "je pars"), logs.map { it.heardText })
        assertEquals("Shizuku indisponible", logs.first().errorMessage)
        assertEquals(0.42f, logs.first().matchScore, 0.0001f)
        assertTrue(logs.first().id > 0)
    }

    /**
     * Critère de validation du lot 1 : une automatisation créée en code se relit
     * après redémarrage de l'app — donc après fermeture et réouverture du fichier de base.
     */
    @Test
    fun `les donnees survivent a la fermeture puis reouverture de la base`() = runTest {
        context.deleteDatabase(PERSISTED_DB)
        val original = Room.databaseBuilder(context, AppDatabase::class.java, PERSISTED_DB).build()
        val automation = modeSortie()
        original.automationDao().upsertAutomation(automation)
        original.automationDao().upsertActions(
            listOf(action(automation.id, 0, ActionType.LAUNCH_APP, mapOf("package" to "com.nothing.launcher")))
        )
        original.close()

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, PERSISTED_DB).build()
        try {
            val row = reopened.automationDao().getById(automation.id)
            assertNotNull(row)
            assertEquals(listOf("je pars", "je m'en vais"), row!!.automation.phrases)
            assertEquals("com.nothing.launcher", row.actions.single().params["package"])
        } finally {
            reopened.close()
            context.deleteDatabase(PERSISTED_DB)
        }
    }

    // --- Helpers -------------------------------------------------------------

    private fun countChildren(table: String, automationId: String): Int =
        db.query("SELECT COUNT(*) FROM $table WHERE automationId = ?", arrayOf<Any>(automationId))
            .use { cursor: Cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

    private fun modeSortie(name: String = "Mode sortie") = AutomationEntity(
        name = name,
        phrases = listOf("je pars", "je m'en vais"),
        feedbackText = "Bonne route"
    )

    private fun action(
        automationId: String,
        order: Int,
        type: ActionType,
        params: Map<String, String> = emptyMap()
    ) = ActionEntity(automationId = automationId, order = order, type = type, params = params)

    private fun condition(
        automationId: String,
        type: ConditionType,
        params: Map<String, String> = emptyMap()
    ) = ConditionEntity(automationId = automationId, type = type, params = params)

    private companion object {
        const val PERSISTED_DB = "lot1-persistence-test.db"
    }
}
