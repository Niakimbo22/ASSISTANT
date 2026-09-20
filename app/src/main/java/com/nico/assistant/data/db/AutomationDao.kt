package com.nico.assistant.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Accès aux automatisations et à leurs enfants.
 *
 * L'interface reste purement abstraite : la sauvegarde transactionnelle
 * (`clearActions` puis `upsertActions`) est orchestrée par
 * [com.nico.assistant.data.repo.AutomationRepository] via `withTransaction`.
 */
@Dao
interface AutomationDao {

    @Transaction
    @Query("SELECT * FROM automations WHERE enabled = 1 ORDER BY priority DESC, name")
    fun observeEnabled(): Flow<List<AutomationWithChildren>>

    @Transaction
    @Query("SELECT * FROM automations ORDER BY name")
    fun observeAll(): Flow<List<AutomationWithChildren>>

    @Transaction
    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationWithChildren?

    /** Lecture ponctuelle pour le MatchEngine (lot 2), qui n'a pas besoin d'observer. */
    @Transaction
    @Query("SELECT * FROM automations WHERE enabled = 1 ORDER BY priority DESC, name")
    suspend fun getEnabled(): List<AutomationWithChildren>

    @Upsert
    suspend fun upsertAutomation(e: AutomationEntity)

    @Upsert
    suspend fun upsertActions(list: List<ActionEntity>)

    @Upsert
    suspend fun upsertConditions(list: List<ConditionEntity>)

    @Query("DELETE FROM actions WHERE automationId = :id")
    suspend fun clearActions(id: String)

    @Query("DELETE FROM conditions WHERE automationId = :id")
    suspend fun clearConditions(id: String)

    @Delete
    suspend fun delete(e: AutomationEntity)

    /** Les enfants tombent avec le parent (ForeignKey CASCADE). */
    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE automations SET runCount = runCount + 1, lastRunAt = :ts WHERE id = :id")
    suspend fun markRun(id: String, ts: Long)

    @Query("SELECT COUNT(*) FROM automations")
    suspend fun count(): Int
}
