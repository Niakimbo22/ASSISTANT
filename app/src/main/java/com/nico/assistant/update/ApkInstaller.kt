package com.nico.assistant.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installe un APK téléchargé, en deux voies :
 *
 * 1. `PackageInstaller` — la voie moderne. On écrit l'APK dans une session, et le
 *    système renvoie l'écran de confirmation puis le résultat réel de l'installation.
 * 2. `FileProvider` + `ACTION_VIEW` — le repli historique, un simple intent que
 *    l'installeur système sait ouvrir. Sans retour de résultat, mais très robuste.
 *
 * Les deux exigent l'autorisation « installer des applications inconnues », qui se
 * demande par un écran de réglages ([unknownSourcesSettingsIntent]) et non par une
 * demande de permission runtime.
 */
class ApkInstaller(context: Context) {

    private val appContext = context.applicationContext

    /**
     * L'utilisateur a-t-il accordé « installer des applications inconnues » à cette app ?
     * Sous API 26 la question ne se pose pas, mais minSdk vaut 26 : elle se pose toujours.
     */
    fun canInstallPackages(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    /** Écran système où l'autorisation s'accorde, ciblé sur cette app. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${appContext.packageName}"))

    /**
     * Voie principale. Le succès renvoyé ici veut seulement dire « session transmise
     * au système » : la confirmation, puis le résultat, arrivent via
     * [InstallResultReceiver] qui les repose dans [InstallEvents].
     */
    fun install(apk: File): Result<Unit> = runCatching {
        require(apk.isFile && apk.length() > 0L) { "APK introuvable ou vide" }

        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(appContext.packageName)
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite(WRITE_NAME, 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(statusPendingIntent(sessionId).intentSender)
        }
        InstallEvents.post(InstallEvent.AwaitingUser)
    }.onFailure { Log.w(TAG, "PackageInstaller a échoué", it) }

    /**
     * Repli : on passe l'APK à l'installeur système par une URI de contenu.
     * Utile aussi comme bouton manuel quand la voie 1 ne fait rien apparaître.
     */
    fun openSystemInstaller(apk: File): Result<Unit> = runCatching {
        require(apk.isFile && apk.length() > 0L) { "APK introuvable ou vide" }

        val uri = FileProvider.getUriForFile(appContext, authority(), apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }.onFailure { Log.w(TAG, "Installeur système injoignable", it) }

    private fun authority() = "${appContext.packageName}.fileprovider"

    private fun statusPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(InstallResultReceiver.ACTION_INSTALL_STATUS)
            .setPackage(appContext.packageName)
        // FLAG_MUTABLE est obligatoire : c'est le système qui remplit les extras
        // de statut dans cet intent avant de nous le renvoyer.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(appContext, sessionId, intent, flags)
    }

    private companion object {
        const val TAG = "NICO_UPDATE"
        const val WRITE_NAME = "nicoassistant-update"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
