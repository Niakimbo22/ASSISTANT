package com.nico.assistant.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Interroge la dernière release GitHub et télécharge son APK.
 *
 * Tout passe par `HttpURLConnection` : pas de dépendance réseau supplémentaire
 * pour deux requêtes GET anonymes.
 */
class UpdateChecker(context: Context) {

    private val appContext = context.applicationContext

    /** Dossier de travail, vidé avant chaque téléchargement. */
    private val downloadDir: File
        get() = File(appContext.cacheDir, DOWNLOAD_DIR).apply { mkdirs() }

    /**
     * Récupère la dernière release publiée. Un dépôt sans aucune release répond 404 :
     * ce n'est pas une panne, c'est juste qu'aucun build n'est encore passé.
     */
    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open(URL(ReleaseInfo.LATEST_RELEASE_URL))
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> Unit
                    HttpURLConnection.HTTP_NOT_FOUND ->
                        error("Aucune release publiée sur ${ReleaseInfo.REPOSITORY}")
                    HttpURLConnection.HTTP_FORBIDDEN ->
                        error("GitHub limite les requêtes (403), réessaie dans quelques minutes")
                    else -> error("GitHub a répondu $code")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                ReleaseInfo.parse(body).getOrThrow()
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Vérification impossible", it) }
    }

    /**
     * Télécharge l'APK de [release] dans le cache de l'app.
     *
     * [onProgress] reçoit une fraction 0f..1f, ou -1f tant que la taille est inconnue,
     * pour que l'UI puisse afficher une barre indéterminée sans mentir.
     */
    suspend fun downloadApk(
        release: ReleaseInfo,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Un téléchargement interrompu laisse un APK tronqué : on repart propre.
            downloadDir.listFiles()?.forEach { it.delete() }
            val target = File(downloadDir, release.apkName.ifBlank { "${release.tag}.apk" })

            val connection = openFollowingRedirects(URL(release.apkUrl))
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Téléchargement refusé ($code)")

                val expected = release.sizeBytes
                    .takeIf { it > 0L }
                    ?: connection.contentLengthLong.takeIf { it > 0L }
                    ?: -1L

                connection.inputStream.use { input ->
                    copyWithProgress(input, target, expected, onProgress)
                }
            } finally {
                connection.disconnect()
            }

            if (target.length() <= 0L) {
                target.delete()
                error("APK téléchargé vide")
            }
            onProgress(1f)
            target
        }.onFailure { Log.w(TAG, "Téléchargement impossible", it) }
    }

    /** Supprime l'APK téléchargé : inutile de garder 15 Mo après l'installation. */
    fun clearDownloads() {
        runCatching { downloadDir.listFiles()?.forEach { it.delete() } }
    }

    private suspend fun copyWithProgress(
        input: InputStream,
        target: File,
        expectedBytes: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var lastReported = -1
        target.outputStream().use { output ->
            while (true) {
                // Annulation coopérative : quitter l'écran doit couper le transfert.
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                if (expectedBytes > 0L) {
                    val percent = ((copied * 100L) / expectedBytes).toInt().coerceIn(0, 100)
                    // On ne notifie qu'au changement de pourcentage : sinon l'UI
                    // recompose des milliers de fois pour rien.
                    if (percent != lastReported) {
                        lastReported = percent
                        onProgress(percent / 100f)
                    }
                } else if (lastReported < 0) {
                    lastReported = 0
                    onProgress(-1f)
                }
            }
        }
    }

    private fun open(url: URL): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            // GitHub rejette les requêtes sans User-Agent.
            setRequestProperty("User-Agent", USER_AGENT)
        }

    /**
     * `browser_download_url` redirige vers un CDN. `HttpURLConnection` ne suit pas
     * les redirections qui changent d'hôte de façon fiable, donc on les suit à la main.
     */
    private fun openFollowingRedirects(start: URL): HttpURLConnection {
        var url = start
        repeat(MAX_REDIRECTS) {
            val connection = open(url).apply { instanceFollowRedirects = false }
            val code = connection.responseCode
            if (code !in REDIRECT_CODES) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) error("Redirection sans destination")
            url = URL(url, location)
        }
        error("Trop de redirections")
    }

    private companion object {
        const val TAG = "NICO_UPDATE"
        const val USER_AGENT = "NicoAssistant-UpdateChecker"
        const val DOWNLOAD_DIR = "updates"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
