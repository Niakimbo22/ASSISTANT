package com.nico.assistant.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Où en est la mise à jour. Un seul état à la fois, lu par l'écran comme par la popup. */
sealed interface UpdateUiState {
    /** Rien n'a encore été demandé. */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    /** Dernière release atteinte, et on est déjà dessus. */
    data class UpToDate(val buildNumber: Int) : UpdateUiState

    data class Available(val release: ReleaseInfo) : UpdateUiState

    /** [progress] vaut -1f tant que la taille totale est inconnue. */
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateUiState

    data class ReadyToInstall(val release: ReleaseInfo, val apk: File) : UpdateUiState

    /** APK prêt, mais « installer des apps inconnues » manque encore. */
    data class PermissionRequired(val release: ReleaseInfo, val apk: File) : UpdateUiState

    /** L'écran de confirmation système est censé être affiché. */
    data class Installing(val release: ReleaseInfo, val apk: File) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState
}

/**
 * Pilote la mise à jour in-app : vérification, téléchargement, installation.
 *
 * La vérification automatique à l'ouverture est silencieuse — elle ne dérange que
 * s'il y a réellement quelque chose à installer. La vérification manuelle, elle,
 * dit toujours ce qu'elle a trouvé, y compris « tu es à jour ».
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val checker = UpdateChecker(application)
    private val installer = ApkInstaller(application)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Vrai quand la vérification automatique a trouvé une mise à jour à proposer. */
    private val _showPrompt = MutableStateFlow(false)
    val showPrompt: StateFlow<Boolean> = _showPrompt.asStateFlow()

    private var checkedOnLaunch = false
    private var work: Job? = null

    /** Le build actuellement installé, affiché dans les Réglages. */
    val currentBuildNumber: Int get() = BuildInfo.buildNumber
    val currentVersionLabel: String get() = BuildInfo.label

    init {
        // Le résultat d'installation arrive par un receiver du manifeste : on le
        // replie dans l'état de l'écran.
        viewModelScope.launch {
            InstallEvents.latest.collect { event ->
                when (event) {
                    null -> Unit
                    InstallEvent.AwaitingUser -> Unit
                    InstallEvent.Success -> {
                        checker.clearDownloads()
                        InstallEvents.clear()
                    }
                    is InstallEvent.Failed -> {
                        _state.value = UpdateUiState.Failed(event.message)
                        InstallEvents.clear()
                    }
                }
            }
        }
    }

    /**
     * Vérification silencieuse au lancement, une seule fois par processus.
     * Une panne réseau ici ne produit aucun message : ouvrir l'app ne doit pas
     * devenir une occasion de râler.
     */
    fun checkOnLaunch() {
        if (checkedOnLaunch) return
        checkedOnLaunch = true
        check(silent = true)
    }

    /** Le bouton « Vérifier les mises à jour » des Réglages. */
    fun checkNow() = check(silent = false)

    private fun check(silent: Boolean) {
        work?.cancel()
        if (!silent) _state.value = UpdateUiState.Checking
        work = viewModelScope.launch {
            checker.fetchLatestRelease().fold(
                onSuccess = { release ->
                    if (release.isNewerThan(BuildInfo.buildNumber)) {
                        _state.value = UpdateUiState.Available(release)
                        if (silent) _showPrompt.value = true
                    } else if (!silent) {
                        _state.value = UpdateUiState.UpToDate(BuildInfo.buildNumber)
                    } else {
                        _state.value = UpdateUiState.Idle
                    }
                },
                onFailure = { error ->
                    _state.value = if (silent) {
                        UpdateUiState.Idle
                    } else {
                        UpdateUiState.Failed(error.message ?: "Vérification impossible")
                    }
                },
            )
        }
    }

    /** Télécharge l'APK de la release repérée, puis enchaîne sur l'autorisation. */
    fun download() {
        val release = releaseInState() ?: return
        work?.cancel()
        work = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(release, 0f)
            checker.downloadApk(release) { progress ->
                _state.value = UpdateUiState.Downloading(release, progress)
            }.fold(
                onSuccess = { apk -> _state.value = readyOrPermission(release, apk) },
                onFailure = { error ->
                    _state.value = UpdateUiState.Failed(
                        error.message ?: "Téléchargement impossible"
                    )
                },
            )
        }
    }

    /**
     * Lance l'installation. Si l'autorisation « applications inconnues » manque
     * toujours, on repasse en [UpdateUiState.PermissionRequired] plutôt que
     * d'échouer : l'écran renverra vers le réglage.
     */
    fun install() {
        val (release, apk) = downloadedApk() ?: return
        if (!installer.canInstallPackages()) {
            _state.value = UpdateUiState.PermissionRequired(release, apk)
            return
        }
        installer.install(apk).fold(
            onSuccess = { _state.value = UpdateUiState.Installing(release, apk) },
            // PackageInstaller a refusé la session : on tente la voie intent, qui
            // ne dépend que de l'installeur système.
            onFailure = { openSystemInstaller() },
        )
    }

    /** Repli manuel : passe l'APK à l'installeur système via FileProvider. */
    fun openSystemInstaller() {
        val (release, apk) = downloadedApk() ?: return
        if (!installer.canInstallPackages()) {
            _state.value = UpdateUiState.PermissionRequired(release, apk)
            return
        }
        installer.openSystemInstaller(apk).fold(
            onSuccess = { _state.value = UpdateUiState.Installing(release, apk) },
            onFailure = { error ->
                _state.value = UpdateUiState.Failed(
                    error.message ?: "Aucun installeur système trouvé"
                )
            },
        )
    }

    /** L'autorisation a pu être accordée pendant qu'on était dans les réglages. */
    fun refreshPermission() {
        val current = _state.value
        if (current is UpdateUiState.PermissionRequired && installer.canInstallPackages()) {
            _state.value = UpdateUiState.ReadyToInstall(current.release, current.apk)
        }
    }

    fun canInstallPackages(): Boolean = installer.canInstallPackages()

    fun unknownSourcesSettingsIntent() = installer.unknownSourcesSettingsIntent()

    /** Ferme la popup de lancement sans annuler ce qui est en cours. */
    fun dismissPrompt() {
        _showPrompt.value = false
    }

    /** Remet l'écran à zéro après un échec ou un « plus tard ». */
    fun reset() {
        work?.cancel()
        _showPrompt.value = false
        _state.value = UpdateUiState.Idle
    }

    private fun readyOrPermission(release: ReleaseInfo, apk: File): UpdateUiState =
        if (installer.canInstallPackages()) {
            UpdateUiState.ReadyToInstall(release, apk)
        } else {
            UpdateUiState.PermissionRequired(release, apk)
        }

    private fun releaseInState(): ReleaseInfo? = when (val s = _state.value) {
        is UpdateUiState.Available -> s.release
        is UpdateUiState.Downloading -> s.release
        is UpdateUiState.ReadyToInstall -> s.release
        is UpdateUiState.PermissionRequired -> s.release
        is UpdateUiState.Installing -> s.release
        else -> null
    }

    private fun downloadedApk(): Pair<ReleaseInfo, File>? = when (val s = _state.value) {
        is UpdateUiState.ReadyToInstall -> s.release to s.apk
        is UpdateUiState.PermissionRequired -> s.release to s.apk
        is UpdateUiState.Installing -> s.release to s.apk
        else -> null
    }
}
