package com.nico.assistant.action.impl

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.core.executor.ExecutionContext

/** Vocabulaire commun des bascules système : on / off / toggle. */
internal object ToggleParam {
    const val KEY = "state"
    const val ON = "on"
    const val OFF = "off"
    const val TOGGLE = "toggle"

    fun spec(label: String = "État") = ParamSpec(
        key = KEY,
        label = label,
        type = ParamType.TOGGLE,
        default = ON
    )

    /** @param current l'état réel, nécessaire pour « bascule ». Inconnu → on suppose éteint. */
    fun resolve(params: Map<String, String>, current: Boolean?): Boolean =
        when (params[KEY]?.trim()?.lowercase()) {
            OFF, "false", "0" -> false
            TOGGLE -> !(current ?: false)
            else -> true
        }
}

/** Base des actions système : commande Shizuku, repli quand il manque. */
abstract class SystemToggleAction : Action {

    override val backend = Backend.SHIZUKU
    override val category = ActionCategory.SYSTEME
    override val hasFallback = true
    override val paramsSchema = listOf(ToggleParam.spec())

    /** La commande shell à exécuter via Shizuku. */
    protected abstract fun command(enabled: Boolean): String

    /** L'état courant, pour la valeur « bascule ». */
    protected open fun current(context: Context): Boolean? = null

    /** Ce qu'on fait sans Shizuku : en général, ouvrir le bon écran de réglages. */
    protected abstract suspend fun fallback(ctx: ExecutionContext, enabled: Boolean): ActionResult

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val enabled = ToggleParam.resolve(params, runCatching { current(ctx.context) }.getOrNull())

        if (ctx.shizuku.isReady()) {
            val result = ctx.shizuku.exec(command(enabled))
            if (result.isSuccess) return ActionResult.Success(describe(enabled))
        }
        return fallback(ctx, enabled)
    }

    protected open fun describe(enabled: Boolean): String =
        "$label ${if (enabled) "activé" else "désactivé"}"
}

class ToggleWifiAction : SystemToggleAction() {
    override val type = ActionType.TOGGLE_WIFI
    override val label = "Wifi"
    override val description = "Shizuku, sinon ouvre le panneau Wifi"

    override fun command(enabled: Boolean) = if (enabled) "svc wifi enable" else "svc wifi disable"

    override fun current(context: Context): Boolean? =
        (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled

    override suspend fun fallback(ctx: ExecutionContext, enabled: Boolean): ActionResult =
        ctx.launch(Intent(Settings.ACTION_WIFI_SETTINGS), "Panneau Wifi ouvert")
}

class ToggleBluetoothAction : SystemToggleAction() {
    override val type = ActionType.TOGGLE_BLUETOOTH
    override val label = "Bluetooth"
    override val description = "Shizuku, sinon ouvre les réglages Bluetooth"

    override fun command(enabled: Boolean) =
        if (enabled) "svc bluetooth enable" else "svc bluetooth disable"

    override fun current(context: Context): Boolean? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled

    override suspend fun fallback(ctx: ExecutionContext, enabled: Boolean): ActionResult =
        ctx.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Réglages Bluetooth ouverts")
}

class ToggleAirplaneAction : SystemToggleAction() {
    override val type = ActionType.TOGGLE_AIRPLANE
    override val label = "Mode avion"

    override fun command(enabled: Boolean) =
        "cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}"

    override fun current(context: Context): Boolean? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1
    }.getOrNull()

    override suspend fun fallback(ctx: ExecutionContext, enabled: Boolean): ActionResult =
        ctx.launch(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), "Réglages du mode avion ouverts")
}

/**
 * Ne pas déranger. Le repli est réel ici : `NotificationManager` suffit dès que
 * l'utilisateur a accordé l'accès à la politique de notifications.
 */
class ToggleDndAction : SystemToggleAction() {
    override val type = ActionType.TOGGLE_DND
    override val label = "Ne pas déranger"
    override val description = "Shizuku, sinon accès à la politique de notifications"

    override fun command(enabled: Boolean) =
        if (enabled) "cmd notification set_dnd priority" else "cmd notification set_dnd off"

    override fun current(context: Context): Boolean? =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.currentInterruptionFilter
            ?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL }

    override suspend fun fallback(ctx: ExecutionContext, enabled: Boolean): ActionResult {
        val manager = ctx.context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ActionResult.Failure("Service de notifications indisponible")

        if (!manager.isNotificationPolicyAccessGranted) {
            return ctx.launch(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                "Autorise l'accès Ne pas déranger"
            )
        }
        return runCatching {
            manager.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ActionResult.Success(describe(enabled))
        }.getOrElse { ActionResult.Failure(it.message ?: "Ne pas déranger inaccessible") }
    }
}

/** Rotation automatique. Sans Shizuku, WRITE_SETTINGS suffit. */
class ToggleRotationAction : SystemToggleAction() {
    override val type = ActionType.TOGGLE_ROTATION
    override val label = "Rotation automatique"

    override fun command(enabled: Boolean) =
        "settings put system accelerometer_rotation ${if (enabled) 1 else 0}"

    override fun current(context: Context): Boolean? = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
    }.getOrNull()

    override suspend fun fallback(ctx: ExecutionContext, enabled: Boolean): ActionResult =
        writeSystemSetting(ctx, Settings.System.ACCELEROMETER_ROTATION, if (enabled) 1 else 0) {
            describe(enabled)
        }
}

/** Luminosité, de 0 à 100 côté utilisateur, 0 à 255 côté système. */
class SetBrightnessAction : Action {

    override val type = ActionType.SET_BRIGHTNESS
    override val backend = Backend.SHIZUKU
    override val label = "Luminosité"
    override val category = ActionCategory.SYSTEME
    override val hasFallback = true
    override val description = "Shizuku, sinon autorisation « Modifier les réglages »"

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_LEVEL,
            label = "Niveau (0-100)",
            type = ParamType.NUMBER,
            default = "50",
            hint = "Accepte un {slot}"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val percent = params[PARAM_LEVEL]?.trim()?.toIntOrNull()?.coerceIn(0, 100)
            ?: return ActionResult.Failure("Niveau de luminosité invalide")
        val raw = (percent * 255 / 100).coerceIn(1, 255)

        if (ctx.shizuku.isReady()) {
            val result = ctx.shizuku.exec("settings put system screen_brightness $raw")
            if (result.isSuccess) return ActionResult.Success("Luminosité à $percent %")
        }
        return writeSystemSetting(ctx, Settings.System.SCREEN_BRIGHTNESS, raw) {
            "Luminosité à $percent %"
        }
    }

    companion object {
        const val PARAM_LEVEL = "level"
    }
}

/** Volume. N'a en réalité pas besoin de Shizuku : `AudioManager` suffit (spec §6.5). */
class SetVolumeAction : Action {

    override val type = ActionType.SET_VOLUME
    override val backend = Backend.INTERNAL
    override val label = "Volume"
    override val category = ActionCategory.SYSTEME

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_STREAM,
            label = "Sortie",
            type = ParamType.ENUM,
            default = STREAM_MUSIC,
            options = listOf(
                STREAM_MUSIC to "Musique",
                STREAM_RING to "Sonnerie",
                STREAM_ALARM to "Alarme",
                STREAM_NOTIFICATION to "Notifications"
            )
        ),
        ParamSpec(
            key = PARAM_LEVEL,
            label = "Niveau (0-100)",
            type = ParamType.NUMBER,
            default = "50",
            hint = "Accepte un {slot} : « monte le volume à {niveau} »"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val percent = params[PARAM_LEVEL]?.trim()?.toIntOrNull()?.coerceIn(0, 100)
            ?: return ActionResult.Failure("Niveau de volume invalide")
        val stream = STREAMS[params[PARAM_STREAM]?.trim()?.lowercase()] ?: AudioManager.STREAM_MUSIC

        val audio = ctx.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure("Service audio indisponible")

        return runCatching {
            val max = audio.getStreamMaxVolume(stream)
            audio.setStreamVolume(stream, percent * max / 100, 0)
            ActionResult.Success("Volume à $percent %")
        }.getOrElse {
            // Couper la sonnerie exige l'accès Ne pas déranger sur Android récent.
            ActionResult.Failure(it.message ?: "Volume inaccessible")
        }
    }

    companion object {
        const val PARAM_STREAM = "stream"
        const val PARAM_LEVEL = "level"
        const val STREAM_MUSIC = "music"
        const val STREAM_RING = "ring"
        const val STREAM_ALARM = "alarm"
        const val STREAM_NOTIFICATION = "notification"

        private val STREAMS = mapOf(
            STREAM_MUSIC to AudioManager.STREAM_MUSIC,
            STREAM_RING to AudioManager.STREAM_RING,
            STREAM_ALARM to AudioManager.STREAM_ALARM,
            STREAM_NOTIFICATION to AudioManager.STREAM_NOTIFICATION
        )
    }
}

/** Mode expert : commande shell arbitraire. Sans Shizuku, il n'y a pas de repli possible. */
class RunShellAction : Action {

    override val type = ActionType.RUN_SHELL
    override val backend = Backend.SHIZUKU
    override val label = "Commande shell"
    override val category = ActionCategory.SYSTEME
    override val description = "Mode expert — exécutée avec les droits ADB"

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_COMMAND,
            label = "Commande",
            type = ParamType.TEXT,
            hint = "Accepte les {slots}"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val command = params.required(PARAM_COMMAND) ?: return ActionResult.Failure("Commande vide")
        if (!ctx.shizuku.isReady()) return ActionResult.Failure("Shizuku indisponible")

        return ctx.shizuku.exec(command).fold(
            onSuccess = { ActionResult.Success(it.trim().take(200).ifBlank { "Commande exécutée" }) },
            onFailure = { ActionResult.Failure(it.message ?: "Commande refusée") }
        )
    }

    companion object {
        const val PARAM_COMMAND = "command"
    }
}

/**
 * Écrit un réglage système, ou envoie l'utilisateur accorder l'autorisation
 * « Modifier les réglages du système » si elle manque.
 */
internal suspend fun writeSystemSetting(
    ctx: ExecutionContext,
    key: String,
    value: Int,
    message: () -> String
): ActionResult {
    if (!Settings.System.canWrite(ctx.context)) {
        return ctx.launch(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(
                android.net.Uri.parse("package:${ctx.context.packageName}")
            ),
            "Autorise « Modifier les réglages »"
        )
    }
    return runCatching {
        Settings.System.putInt(ctx.context.contentResolver, key, value)
        ActionResult.Success(message())
    }.getOrElse { ActionResult.Failure(it.message ?: "Réglage inaccessible") }
}
