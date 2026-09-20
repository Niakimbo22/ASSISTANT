package com.nico.assistant.data.model

import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.ActionEntity
import com.nico.assistant.data.db.AutomationEntity
import com.nico.assistant.data.db.AutomationWithChildren
import com.nico.assistant.data.db.ConditionEntity

/** Room → métier. Les actions sont remises dans l'ordre de la chaîne. */
fun AutomationWithChildren.toDomain(): Automation = Automation(
    id = automation.id,
    name = automation.name,
    enabled = automation.enabled,
    phrases = automation.phrases,
    matchMode = automation.matchMode,
    priority = automation.priority,
    confirmBeforeRun = automation.confirmBeforeRun,
    feedbackText = automation.feedbackText,
    conditions = conditions.map { it.toDomain() },
    actions = actions.sortedBy { it.order }.map { it.toDomain() },
    createdAt = automation.createdAt,
    lastRunAt = automation.lastRunAt,
    runCount = automation.runCount
)

fun ActionEntity.toDomain(): ActionSpec = ActionSpec(
    id = id,
    type = type,
    params = params,
    critical = critical,
    delayMsBefore = delayMsBefore
)

fun ConditionEntity.toDomain(): Condition = Condition(
    id = id,
    type = type,
    params = params,
    negated = negated
)

/** Métier → Room. */
fun Automation.toEntity(): AutomationEntity = AutomationEntity(
    id = id,
    name = name,
    enabled = enabled,
    phrases = phrases,
    matchMode = matchMode,
    priority = priority,
    confirmBeforeRun = confirmBeforeRun,
    feedbackText = feedbackText,
    createdAt = createdAt,
    lastRunAt = lastRunAt,
    runCount = runCount
)

/** La position en base est (ré)écrite depuis l'index : la liste éditée fait autorité. */
fun Automation.toActionEntities(): List<ActionEntity> =
    actions.mapIndexed { index, spec ->
        ActionEntity(
            id = spec.id,
            automationId = id,
            order = index,
            type = spec.type,
            params = spec.params,
            critical = spec.critical,
            delayMsBefore = spec.delayMsBefore
        )
    }

fun Automation.toConditionEntities(): List<ConditionEntity> =
    conditions.map { condition ->
        ConditionEntity(
            id = condition.id,
            automationId = id,
            type = condition.type,
            params = condition.params,
            negated = condition.negated
        )
    }
