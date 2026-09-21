package com.nico.assistant.shizuku

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Accès aux API système de niveau ADB, sans root (spec §6).
 *
 * L'utilisateur démarre Shizuku une fois par redémarrage du téléphone ; l'app s'y
 * connecte par Binder et l'état est exposé en [StateFlow] pour que l'UI suive.
 */
class ShizukuManager : ShizukuGateway {

    private val _state = MutableStateFlow(ShizukuState.UNKNOWN)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var initialized = false

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { _state.value = ShizukuState.NOT_RUNNING }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            _state.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.READY
            } else {
                ShizukuState.PERMISSION_DENIED
            }
        }

    @Synchronized
    fun init() {
        if (initialized) {
            refresh()
            return
        }
        initialized = runCatching {
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
            true
        }.getOrElse {
            Log.w(TAG, "Shizuku absent de l'appareil", it)
            false
        }
        refresh()
    }

    fun refresh() {
        _state.value = runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
                Shizuku.isPreV11() -> ShizukuState.UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                else -> ShizukuState.PERMISSION_NEEDED
            }
        }.getOrElse { ShizukuState.NOT_RUNNING }
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { Log.w(TAG, "Demande d'autorisation impossible", it) }
    }

    fun release() {
        if (!initialized) return
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
        initialized = false
    }

    override fun isReady(): Boolean = _state.value.isReady

    override suspend fun exec(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isReady()) return@withContext Result.failure(IllegalStateException("Shizuku indisponible"))
        runCatching {
            val process = newProcess(arrayOf("sh", "-c", command))
                ?: error("Shizuku.newProcess indisponible sur cette version")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val errors = process.errorStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            Log.d(TAG, "exec « $command » → code=$code")
            if (code != 0 && output.isBlank()) error(errors.ifBlank { "Code de sortie $code" })
            output
        }
    }

    /**
     * `Shizuku.newProcess` est une API cachée, susceptible de disparaître de la surface
     * publique : on l'appelle par réflexion pour que la compilation ne dépende pas d'elle.
     */
    private fun newProcess(command: Array<String>): Process? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        method.invoke(null, command, null, null) as? Process
    }.getOrNull()

    companion object {
        private const val TAG = "NICO_SHIZUKU"
        const val REQUEST_CODE = 4242

        @Volatile
        private var shared: ShizukuManager? = null

        /**
         * Instance unique du processus : l'API Shizuku est statique, deux managers
         * s'enregistreraient deux fois sur les mêmes écouteurs.
         */
        fun shared(): ShizukuManager = shared ?: synchronized(this) {
            shared ?: ShizukuManager().also {
                it.init()
                shared = it
            }
        }
    }
}
