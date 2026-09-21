package com.nico.assistant.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.nico.assistant.action.ActionType
import java.util.UUID

/** Une automatisation : des phrases déclenchantes, des conditions, une chaîne d'actions. */
@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    /** Variantes acceptées, peuvent contenir des slots `{duree}`. */
    val phrases: List<String>,
    val matchMode: MatchMode = MatchMode.FUZZY,
    /** Départage les ex-aequo au matching. */
    val priority: Int = 0,
    val confirmBeforeRun: Boolean = false,
    /** TTS custom, sinon message généré. */
    val feedbackText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,
    val runCount: Int = 0
)

/** Un maillon de la chaîne d'exécution. */
@Entity(
    tableName = "actions",
    foreignKeys = [
        ForeignKey(
            entity = AutomationEntity::class,
            parentColumns = ["id"],
            childColumns = ["automationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("automationId")]
)
data class ActionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val automationId: String,
    /** Position dans la chaîne, réindexée à chaque sauvegarde. */
    val order: Int,
    val type: ActionType,
    /** JSON via [Converters], accepte les slots `{contact}`. */
    val params: Map<String, String> = emptyMap(),
    /** Si l'action échoue et qu'elle est critique, la chaîne s'arrête. */
    val critical: Boolean = false,
    val delayMsBefore: Long = 0
)

/** Une garde évaluée avant de dérouler la chaîne. */
@Entity(
    tableName = "conditions",
    foreignKeys = [
        ForeignKey(
            entity = AutomationEntity::class,
            parentColumns = ["id"],
            childColumns = ["automationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("automationId")]
)
data class ConditionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val automationId: String,
    val type: ConditionType,
    val params: Map<String, String> = emptyMap(),
    val negated: Boolean = false
)

/**
 * Trace d'exécution, alimentée par l'ActionExecutor (lot 3) et affichée par l'écran Logs (lot 8).
 *
 * Volontairement sans clé étrangère : un log doit survivre à la suppression de son automatisation,
 * d'où un [automationId] nullable et non contraint.
 */
@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val automationId: String? = null,
    /** Ce que le STT a compris. */
    val heardText: String,
    val matchScore: Float,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Vue assemblée renvoyée par Room. L'app ne manipule jamais ce type directement :
 * le repository le convertit en [com.nico.assistant.data.model.Automation].
 */
data class AutomationWithChildren(
    @Embedded val automation: AutomationEntity,
    @Relation(parentColumn = "id", entityColumn = "automationId")
    val actions: List<ActionEntity>,
    @Relation(parentColumn = "id", entityColumn = "automationId")
    val conditions: List<ConditionEntity>
)
