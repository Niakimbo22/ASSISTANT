package com.nico.assistant.data.seed

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nico.assistant.data.db.AppDatabase
import com.nico.assistant.data.model.Automation
import com.nico.assistant.data.repo.AutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Installe les automatisations *seed* à la création de la base, et seulement là (spec §3).
 *
 * Rien à migrer depuis la V1 : elle n'avait pas de base. Si Nico supprime une seed, elle
 * ne revient pas — c'est son catalogue, pas le nôtre.
 */
object SeedInstaller {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun callback(context: Context, database: () -> AppDatabase): RoomDatabase.Callback =
        object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                scope.launch {
                    runCatching { install(database(), SeedAutomations.forDevice(context)) }
                        .onFailure { Log.e(TAG, "Installation des seeds impossible", it) }
                }
            }
        }

    /** Ne fait rien si la base contient déjà quelque chose. */
    suspend fun install(database: AppDatabase, automations: List<Automation>): Int {
        val repository = AutomationRepository(database)
        if (repository.count() > 0) return 0
        automations.forEach { repository.save(it) }
        Log.i(TAG, "${automations.size} automatisations livrées installées")
        return automations.size
    }

    private const val TAG = "NICO_SEED"
}
