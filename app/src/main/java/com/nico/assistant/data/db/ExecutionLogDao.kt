package com.nico.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Journal d'exécution : écrit par l'ActionExecutor, lu par l'écran Logs. */
@Dao
interface ExecutionLogDao {

    @Insert
    suspend fun insert(log: ExecutionLogEntity): Long

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionLogEntity>>

    @Query(
        "SELECT * FROM execution_logs WHERE automationId = :automationId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit"
    )
    fun observeFor(automationId: String, limit: Int): Flow<List<ExecutionLogEntity>>

    @Query("SELECT COUNT(*) FROM execution_logs")
    suspend fun count(): Int

    @Query("DELETE FROM execution_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM execution_logs")
    suspend fun clear()
}
