package com.nico.assistant.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Reçoit le statut des sessions `PackageInstaller`.
 *
 * Le cas important est [PackageInstaller.STATUS_PENDING_USER_ACTION] : le système
 * nous rend un intent qu'il faut lancer nous-mêmes pour que l'écran « Installer ? »
 * apparaisse. Sans ça, la session reste en attente et il ne se passe visiblement rien.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val systemMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = confirmationIntent(intent)
                if (confirmation == null) {
                    InstallEvents.post(
                        InstallEvent.Failed("Le système n'a pas renvoyé d'écran de confirmation")
                    )
                    return
                }
                // On vient d'un receiver : sans NEW_TASK l'activité ne peut pas démarrer.
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmation) }
                    .onSuccess { InstallEvents.post(InstallEvent.AwaitingUser) }
                    .onFailure {
                        Log.w(TAG, "Confirmation d'installation non lançable", it)
                        InstallEvents.post(
                            InstallEvent.Failed(
                                "Confirmation impossible à ouvrir — utilise « Ouvrir l'installeur »"
                            )
                        )
                    }
            }

            PackageInstaller.STATUS_SUCCESS -> InstallEvents.post(InstallEvent.Success)

            else -> InstallEvents.post(InstallEvent.Failed(describe(status, systemMessage)))
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    /**
     * Traduit les codes en quelque chose d'actionnable. `STATUS_FAILURE_CONFLICT`
     * est le plus fréquent en pratique : il veut presque toujours dire que l'APK
     * installé a été signé avec une autre clé que celui qu'on vient de télécharger.
     */
    private fun describe(status: Int, systemMessage: String?): String {
        val reason = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation bloquée par le système"
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Conflit avec la version installée — signature différente, " +
                    "il faut désinstaller l'app avant d'installer celle-ci"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK incompatible avec cet appareil"
            PackageInstaller.STATUS_FAILURE_INVALID -> "APK invalide ou corrompu"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant"
            else -> "Installation échouée"
        }
        return if (systemMessage.isNullOrBlank()) reason else "$reason ($systemMessage)"
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.nico.assistant.action.INSTALL_STATUS"
        private const val TAG = "NICO_UPDATE"
    }
}
