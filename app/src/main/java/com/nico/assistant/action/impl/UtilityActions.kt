package com.nico.assistant.action.impl

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.core.executor.ExecutionContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Fait parler l'assistant. Les slots sont résolus avant d'arriver ici : « Il est {heure} ». */
class SpeakAction : Action {

    override val type = ActionType.SPEAK
    override val backend = Backend.INTERNAL
    override val label = "Dire quelque chose"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_TEXT,
            label = "Texte à prononcer",
            type = ParamType.TEXT,
            hint = "« Il est {heure} », « Bonne route »"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val text = params.required(PARAM_TEXT) ?: return ActionResult.Failure("Rien à dire")
        ctx.speaker.speak(text)
        return ActionResult.Success(text)
    }

    companion object {
        const val PARAM_TEXT = "text"
    }
}

/** Message éphémère à l'écran, pratique pour déboguer une chaîne sans TTS. */
class ShowToastAction : Action {

    override val type = ActionType.SHOW_TOAST
    override val backend = Backend.INTERNAL
    override val label = "Afficher un message"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_TEXT, label = "Message", type = ParamType.TEXT)
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val text = params.required(PARAM_TEXT) ?: return ActionResult.Failure("Message vide")
        // Toast exige le thread principal.
        withContext(Dispatchers.Main) {
            Toast.makeText(ctx.context, text, Toast.LENGTH_SHORT).show()
        }
        return ActionResult.Success(text)
    }

    companion object {
        const val PARAM_TEXT = "text"
    }
}

/** Retour haptique. */
class VibrateAction : Action {

    override val type = ActionType.VIBRATE
    override val backend = Backend.INTERNAL
    override val label = "Vibrer"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_PATTERN,
            label = "Motif",
            type = ParamType.ENUM,
            default = PATTERN_SHORT,
            options = listOf(
                PATTERN_SHORT to "Court",
                PATTERN_DOUBLE to "Double",
                PATTERN_LONG to "Long"
            )
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val vibrator = vibrator(ctx.context)
            ?: return ActionResult.Failure("Pas de vibreur sur cet appareil")
        if (!vibrator.hasVibrator()) return ActionResult.Failure("Pas de vibreur sur cet appareil")

        val pattern = params[PARAM_PATTERN]?.trim()?.lowercase() ?: PATTERN_SHORT
        val effect = when (pattern) {
            PATTERN_DOUBLE -> VibrationEffect.createWaveform(longArrayOf(0, 60, 90, 60), -1)
            PATTERN_LONG -> VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
            else -> VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
        return ActionResult.Success()
    }

    private fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    companion object {
        const val PARAM_PATTERN = "pattern"
        const val PATTERN_SHORT = "court"
        const val PATTERN_DOUBLE = "double"
        const val PATTERN_LONG = "long"
    }
}

/**
 * Pause dans la chaîne — laisser une app s'ouvrir avant de la piloter, par exemple.
 *
 * Seule action à repousser le délai maximum : sinon une attente de 15 s serait abandonnée
 * par la garde des 10 s de l'executor.
 */
class WaitAction : Action {

    override val type = ActionType.WAIT
    override val backend = Backend.INTERNAL
    override val label = "Attendre"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_MS,
            label = "Durée (ms)",
            type = ParamType.DURATION,
            default = "1000"
        )
    )

    override fun timeoutMs(params: Map<String, String>): Long =
        durationOf(params) + TIMEOUT_MARGIN_MS

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val duration = durationOf(params)
        if (duration <= 0) return ActionResult.Failure("Durée invalide")
        delay(duration)
        return ActionResult.Success()
    }

    private fun durationOf(params: Map<String, String>): Long =
        params[PARAM_MS]?.trim()?.toLongOrNull()?.coerceIn(0, MAX_WAIT_MS) ?: DEFAULT_WAIT_MS

    companion object {
        const val PARAM_MS = "ms"
        private const val DEFAULT_WAIT_MS = 1_000L
        private const val MAX_WAIT_MS = 60_000L
        private const val TIMEOUT_MARGIN_MS = 1_000L
    }
}
