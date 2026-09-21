package com.nico.assistant.action.impl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.core.executor.ExecutionContext

/** Itinéraire Maps. Accepte un slot : « emmène-moi à {destination} ». */
class NavigateToAction : Action {

    override val type = ActionType.NAVIGATE_TO
    override val backend = Backend.INTENT
    override val label = "Itinéraire"
    override val category = ActionCategory.APPS

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_DESTINATION,
            label = "Destination",
            type = ParamType.TEXT,
            hint = "Accepte un {slot}"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val destination = params.required(PARAM_DESTINATION)
            ?: return ActionResult.Failure("Aucune destination")
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
        val direct = ctx.launch(Intent(Intent.ACTION_VIEW, uri))
        if (direct is ActionResult.Success) return ActionResult.Success("Itinéraire vers $destination")

        // Repli : la recherche Maps générique, qui marche même sans l'app de navigation.
        return ctx.launch(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(destination)}")
            )
        )
    }

    companion object {
        const val PARAM_DESTINATION = "destination"
    }
}

/** Minuteur. Accepte `{duree}` en minutes, comme les phrases du catalogue. */
class SetTimerAction : Action {

    override val type = ActionType.SET_TIMER
    override val backend = Backend.INTENT
    override val label = "Minuteur"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_DURATION,
            label = "Durée (minutes)",
            type = ParamType.DURATION,
            default = "5",
            hint = "Accepte un {slot} : « timer de {duree} minutes »"
        ),
        ParamSpec(
            key = PARAM_MESSAGE,
            label = "Libellé",
            type = ParamType.TEXT,
            required = false
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val minutes = params[PARAM_DURATION]?.trim()?.toIntOrNull()
            ?: return ActionResult.Failure("Durée de minuteur invalide")
        if (minutes <= 0) return ActionResult.Failure("Durée de minuteur invalide")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .putExtra(AlarmClock.EXTRA_MESSAGE, params[PARAM_MESSAGE].orEmpty())

        return ctx.launch(intent, "Minuteur de $minutes minutes")
    }

    companion object {
        const val PARAM_DURATION = "duration"
        const val PARAM_MESSAGE = "message"
    }
}

/** Réveil à une heure donnée, au format HH:mm. */
class SetAlarmAction : Action {

    override val type = ActionType.SET_ALARM
    override val backend = Backend.INTENT
    override val label = "Réveil"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_TIME, label = "Heure (HH:mm)", type = ParamType.TEXT, hint = "07:30"),
        ParamSpec(key = PARAM_MESSAGE, label = "Libellé", type = ParamType.TEXT, required = false)
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val parts = params.required(PARAM_TIME)?.split(":", "h")
            ?: return ActionResult.Failure("Heure de réveil manquante")
        val hours = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val minutes = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        if (hours == null || hours !in 0..23 || minutes !in 0..59) {
            return ActionResult.Failure("Heure de réveil invalide")
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hours)
            .putExtra(AlarmClock.EXTRA_MINUTES, minutes)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .putExtra(AlarmClock.EXTRA_MESSAGE, params[PARAM_MESSAGE].orEmpty())

        return ctx.launch(intent, "Réveil à %02d:%02d".format(hours, minutes))
    }

    companion object {
        const val PARAM_TIME = "time"
        const val PARAM_MESSAGE = "message"
    }
}

/** Presse-papier. */
class CopyToClipboardAction : Action {

    override val type = ActionType.COPY_TO_CLIPBOARD
    override val backend = Backend.INTERNAL
    override val label = "Copier dans le presse-papier"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_TEXT, label = "Texte", type = ParamType.TEXT, hint = "Accepte les {slots}")
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val text = params.required(PARAM_TEXT) ?: return ActionResult.Failure("Rien à copier")
        val clipboard = ctx.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ActionResult.Failure("Presse-papier indisponible")

        clipboard.setPrimaryClip(ClipData.newPlainText("NicoAssistant", text))
        return ActionResult.Success("Copié")
    }

    companion object {
        const val PARAM_TEXT = "text"
    }
}

/** Feuille de partage. */
class ShareTextAction : Action {

    override val type = ActionType.SHARE_TEXT
    override val backend = Backend.INTENT
    override val label = "Partager un texte"
    override val category = ActionCategory.COMMUNICATION

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_TEXT, label = "Texte", type = ParamType.TEXT, hint = "Accepte les {slots}")
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val text = params.required(PARAM_TEXT) ?: return ActionResult.Failure("Rien à partager")
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        return ctx.launch(Intent.createChooser(share, "Partager"))
    }

    companion object {
        const val PARAM_TEXT = "text"
    }
}

/** Ouvre une conversation WhatsApp. */
class OpenWhatsAppChatAction : Action {

    override val type = ActionType.OPEN_WHATSAPP_CHAT
    override val backend = Backend.INTENT
    override val label = "Ouvrir une conversation WhatsApp"
    override val category = ActionCategory.COMMUNICATION

    override val paramsSchema = listOf(
        ParamSpec(
            key = CallNumberAction.PARAM_CONTACT,
            label = "Contact",
            type = ParamType.CONTACT_PICKER,
            required = false
        ),
        ParamSpec(
            key = CallNumberAction.PARAM_NUMBER,
            label = "Numéro",
            type = ParamType.TEXT,
            required = false
        ),
        ParamSpec(key = PARAM_MESSAGE, label = "Message", type = ParamType.TEXT, required = false)
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val number = CallNumberAction.resolveNumber(ctx, params)
            ?: return CallNumberAction.numberFailure(ctx, params)
        val digits = number.filter { it.isDigit() || it == '+' }.removePrefix("+")
        val message = params[PARAM_MESSAGE].orEmpty()

        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        return ctx.launch(Intent(Intent.ACTION_VIEW, uri))
    }

    companion object {
        const val PARAM_MESSAGE = "message"
    }
}

/** Note rapide, via l'intent de création de note des apps qui le gèrent. */
class CreateNoteAction : Action {

    override val type = ActionType.CREATE_NOTE
    override val backend = Backend.INTENT
    override val label = "Créer une note"
    override val category = ActionCategory.UTILITAIRES

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_TEXT, label = "Contenu", type = ParamType.TEXT, hint = "Accepte les {slots}")
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val text = params.required(PARAM_TEXT) ?: return ActionResult.Failure("Note vide")
        val note = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_SUBJECT, "Note")
        return ctx.launch(Intent.createChooser(note, "Enregistrer la note"))
    }

    companion object {
        const val PARAM_TEXT = "text"
    }
}

/** Écran de réglages précis. */
class OpenSettingsAction : Action {

    override val type = ActionType.OPEN_SETTINGS
    override val backend = Backend.INTENT
    override val label = "Ouvrir un écran de réglages"
    override val category = ActionCategory.APPS

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_SCREEN,
            label = "Écran",
            type = ParamType.ENUM,
            default = "wifi",
            options = SCREENS.map { it.key to it.value.second }
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val key = params[PARAM_SCREEN]?.trim()?.lowercase() ?: "wifi"
        val action = SCREENS[key]?.first ?: Settings.ACTION_SETTINGS
        return ctx.launch(Intent(action))
    }

    companion object {
        const val PARAM_SCREEN = "screen"

        private val SCREENS = mapOf(
            "wifi" to (Settings.ACTION_WIFI_SETTINGS to "Wifi"),
            "bluetooth" to (Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth"),
            "batterie" to (Settings.ACTION_BATTERY_SAVER_SETTINGS to "Batterie"),
            "apps" to (Settings.ACTION_APPLICATION_SETTINGS to "Applications"),
            "son" to (Settings.ACTION_SOUND_SETTINGS to "Son"),
            "ecran" to (Settings.ACTION_DISPLAY_SETTINGS to "Écran"),
            "accessibilite" to (Settings.ACTION_ACCESSIBILITY_SETTINGS to "Accessibilité"),
            "general" to (Settings.ACTION_SETTINGS to "Réglages généraux")
        )
    }
}

/** Intent brut : la porte de sortie pour tout ce que le catalogue ne couvre pas. */
class SendIntentAction : Action {

    override val type = ActionType.SEND_INTENT
    override val backend = Backend.INTENT
    override val label = "Intent brut"
    override val category = ActionCategory.APPS
    override val description = "Mode expert — pour tout ce qui manque au catalogue"

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_ACTION, label = "Action", type = ParamType.TEXT, hint = "android.intent.action.VIEW"),
        ParamSpec(key = PARAM_DATA, label = "Données (URI)", type = ParamType.TEXT, required = false),
        ParamSpec(key = PARAM_PACKAGE, label = "Paquet cible", type = ParamType.APP_PICKER, required = false),
        ParamSpec(
            key = PARAM_EXTRAS,
            label = "Extras",
            type = ParamType.TEXT,
            required = false,
            hint = "cle=valeur, séparés par des virgules"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val action = params.required(PARAM_ACTION) ?: return ActionResult.Failure("Action manquante")
        val intent = Intent(action)

        params.required(PARAM_DATA)?.let { intent.data = Uri.parse(it) }
        params.required(PARAM_PACKAGE)?.let { intent.setPackage(it) }
        params.required(PARAM_EXTRAS)?.split(",")?.forEach { pair ->
            val (key, value) = pair.split("=", limit = 2).let {
                it.getOrNull(0)?.trim().orEmpty() to it.getOrNull(1)?.trim().orEmpty()
            }
            if (key.isNotEmpty()) intent.putExtra(key, value)
        }

        return ctx.launch(intent)
    }

    companion object {
        const val PARAM_ACTION = "action"
        const val PARAM_DATA = "data"
        const val PARAM_PACKAGE = "package"
        const val PARAM_EXTRAS = "extras"
    }
}
