package com.nico.assistant.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.nico.assistant.data.db.AppDatabase
import com.nico.assistant.data.db.ExecutionLogEntity
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.model.toActionEntities
import com.nico.assistant.data.model.toConditionEntities
import com.nico.assistant.data.model.toDomain
import com.nico.assistant.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Seule porte d'entrée vers la base. Expose du [Flow] et des [Automation] assemblées ;
 * ni les entités Room ni le DAO ne sortent d'ici (le journal d'exécution excepté,
 * qui est déjà un objet plat).
 */
class AutomationRepository(private val db: AppDatabase) {

    private val dao = db.automationDao()
    private val logDao = db.executionLogDao()

    fun observeAll(): Flow<List<Automation>> =
        dao.observeAll().map { rows -> rows.map { row -> row.toDomain() } }

    fun observeEnabled(): Flow<List<Automation>> =
        dao.observeEnabled().map { rows -> rows.map { row -> row.toDomain() } }

    suspend fun getById(id: String): Automation? = dao.getById(id)?.toDomain()

    /** Lecture ponctuelle utilisée par le MatchEngine à chaque phrase entendue (lot 2). */
    suspend fun enabledAutomations(): List<Automation> =
        dao.getEnabled().map { row -> row.toDomain() }

    suspend fun count(): Int = dao.count()

    /**
     * Crée ou remplace une automatisation et toute sa descendance, en une transaction.
     *
     * Les enfants sont purgés puis réinsérés plutôt que diffés : c'est plus simple et ça rend
     * impossible un ordre incohérent après un drag & drop (spec §3).
     */
    suspend fun save(automation: Automation) {
        db.withTransaction {
            dao.upsertAutomation(automation.toEntity())
            dao.clearActions(automation.id)
            dao.clearConditions(automation.id)
            dao.upsertActions(automation.toActionEntities())
            dao.upsertConditions(automation.toConditionEntities())
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    /** Les actions et conditions tombent en cascade. */
    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun markRun(id: String, ts: Long = System.currentTimeMillis()) = dao.markRun(id, ts)

    // --- Journal d'exécution -------------------------------------------------

    suspend fun log(entry: ExecutionLogEntity): Long = logDao.insert(entry)

    fun observeLogs(limit: Int = DEFAULT_LOG_LIMIT): Flow<List<ExecutionLogEntity>> =
        logDao.observeRecent(limit)

    fun observeLogsFor(automationId: String, limit: Int = DEFAULT_LOG_LIMIT): Flow<List<ExecutionLogEntity>> =
        logDao.observeFor(automationId, limit)

    suspend fun logCount(): Int = logDao.count()

    suspend fun pruneLogsBefore(timestamp: Long) = logDao.deleteOlderThan(timestamp)

    suspend fun clearLogs() = logDao.clear()

    companion object {
        const val DEFAULT_LOG_LIMIT = 200

        fun from(context: Context): AutomationRepository =
            AutomationRepository(AppDatabase.get(context))
    }
}
