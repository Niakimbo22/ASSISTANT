package com.nico.assistant.action.impl

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.core.executor.ExecutionContext
import com.nico.assistant.util.ContactResolver

/**
 * Appelle un numéro, ou un contact retrouvé au nom prononcé.
 *
 * Sans l'autorisation d'appel, on ouvre le composeur pré-rempli plutôt que d'échouer :
 * l'utilisateur n'a plus qu'à appuyer.
 */
class CallNumberAction : Action {

    override val type = ActionType.CALL_NUMBER
    override val backend = Backend.INTENT
    override val label = "Appeler"
    override val category = ActionCategory.COMMUNICATION
    override val description = "Numéro direct, ou contact retrouvé au nom"

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_CONTACT,
            label = "Contact",
            type = ParamType.CONTACT_PICKER,
            required = false,
            hint = "Accepte un {slot} : « appelle {contact} »"
        ),
        ParamSpec(
            key = PARAM_NUMBER,
            label = "Numéro",
            type = ParamType.TEXT,
            required = false,
            hint = "Prioritaire sur le contact"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val number = resolveNumber(ctx, params) ?: return numberFailure(ctx, params)

        val uri = Uri.parse("tel:${Uri.encode(number)}")
        if (ctx.context.hasPermission(Manifest.permission.CALL_PHONE)) {
            val call = ctx.launch(Intent(Intent.ACTION_CALL, uri))
            if (call is ActionResult.Success) return call
        }
        return ctx.launch(Intent(Intent.ACTION_DIAL, uri), "Composeur pré-rempli")
    }

    companion object {
        const val PARAM_CONTACT = "contact"
        const val PARAM_NUMBER = "number"

        internal fun resolveNumber(ctx: ExecutionContext, params: Map<String, String>): String? {
            params.required(PARAM_NUMBER)?.let { return it }
            val contact = params.required(PARAM_CONTACT) ?: return null
            // Un contact déjà saisi sous forme de numéro doit passer tel quel.
            if (contact.any { it.isDigit() } && contact.none { it.isLetter() }) return contact
            return ContactResolver.findBest(ctx.context, contact)?.number
        }

        internal fun numberFailure(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
            val contact = params.required(PARAM_CONTACT)
                ?: return ActionResult.Failure("Ni numéro ni contact indiqué")
            return if (ContactResolver.hasPermission(ctx.context)) {
                ActionResult.Failure("Contact introuvable : $contact")
            } else {
                ActionResult.Failure("Accès aux contacts refusé")
            }
        }
    }
}

/**
 * Envoie un SMS. Par défaut l'app de messagerie s'ouvre pré-remplie : l'envoi direct,
 * irréversible, reste une case à cocher explicite.
 */
class SendSmsAction : Action {

    override val type = ActionType.SEND_SMS
    override val backend = Backend.INTENT
    override val label = "Envoyer un SMS"
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
        ParamSpec(
            key = PARAM_MESSAGE,
            label = "Message",
            type = ParamType.TEXT,
            hint = "Accepte les {slots} : « Je suis parti à {heure} »"
        ),
        ParamSpec(
            key = PARAM_DIRECT,
            label = "Envoyer directement",
            type = ParamType.TOGGLE,
            required = false,
            default = TOGGLE_OFF,
            hint = "Sans confirmation, si l'autorisation SMS est accordée"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val message = params.required(PARAM_MESSAGE) ?: return ActionResult.Failure("Message vide")
        val number = CallNumberAction.resolveNumber(ctx, params)
            ?: return CallNumberAction.numberFailure(ctx, params)

        val direct = params[PARAM_DIRECT]?.trim()?.lowercase().orEmpty() in TOGGLE_ON_VALUES
        if (direct && ctx.context.hasPermission(Manifest.permission.SEND_SMS)) {
            val sent = sendDirectly(ctx, number, message)
            if (sent is ActionResult.Success) return sent
        }

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
            .putExtra("sms_body", message)
        return ctx.launch(intent, "SMS pré-rempli")
    }

    private fun sendDirectly(ctx: ExecutionContext, number: String, message: String): ActionResult =
        runCatching {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return ActionResult.Failure("Service SMS indisponible")

            manager.sendMultipartTextMessage(
                number,
                null,
                manager.divideMessage(message),
                null,
                null
            )
            ActionResult.Success("SMS envoyé")
        }.getOrElse { ActionResult.Failure(it.message ?: "Envoi du SMS impossible") }

    companion object {
        const val PARAM_MESSAGE = "message"
        const val PARAM_DIRECT = "direct"

        /** Même vocabulaire que les bascules système, pour que l'UI n'ait qu'un composant. */
        const val TOGGLE_OFF = "off"
        private val TOGGLE_ON_VALUES = setOf("on", "true", "1")
    }
}
