package com.nico.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nico.assistant.data.seed.SeedInstaller

/**
 * Base Room de NicoAssistant.
 *
 * Version 1 : rien à migrer, la V1 n'avait pas de base (spec §3). Les automatisations *seed*
 * seront insérées plus tard par un `RoomDatabase.Callback.onCreate()`, une fois les actions
 * implémentées (lots 3 et 6).
 */
@Database(
    entities = [
        AutomationEntity::class,
        ActionEntity::class,
        ConditionEntity::class,
        ExecutionLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun automationDao(): AutomationDao

    abstract fun executionLogDao(): ExecutionLogDao

    companion object {
        const val NAME = "nicoassistant.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Le premier lancement crée la base et y installe les automatisations *seed*
         * (spec §3) : la V1 n'avait pas de base, il n'y a donc rien à migrer.
         */
        private fun build(context: Context): AppDatabase {
            lateinit var database: AppDatabase
            database = Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addCallback(SeedInstaller.callback(context) { database })
                .build()
            return database
        }
    }
}
