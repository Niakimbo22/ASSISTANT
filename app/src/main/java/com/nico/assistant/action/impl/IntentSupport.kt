package com.nico.assistant.action.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.nico.assistant.action.ActionResult
import com.nico.assistant.core.executor.ExecutionContext

/**
 * Lance un intent hors pile d'activité et transforme l'absence d'application capable
 * de le traiter en échec propre plutôt qu'en crash.
 */
internal fun ExecutionContext.launch(intent: Intent, message: String? = null): ActionResult = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    ActionResult.Success(message)
} catch (error: Exception) {
    ActionResult.Failure(error.message ?: "Aucune application ne sait ouvrir ça")
}

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Paramètre obligatoire, nettoyé ; `null` s'il est absent ou vide. */
internal fun Map<String, String>.required(key: String): String? =
    this[key]?.trim()?.takeIf { it.isNotEmpty() }
