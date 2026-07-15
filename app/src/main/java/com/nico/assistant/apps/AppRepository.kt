package com.nico.assistant.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.nico.assistant.util.TextUtils

/** Une appli installée : libellé affichable + nom de paquet réel. */
data class AppEntry(
    val label: String,
    val packageName: String,
)

/**
 * Énumère les applis lançables (MAIN/LAUNCHER) et fournit le matching flou.
 * On ne code EN DUR aucun nom de paquet : RVX Music est sideloadé et peut avoir
 * un paquet personnalisé, on s'appuie donc toujours sur la sélection utilisateur.
 */
class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    /** Toutes les applis résolvant MAIN/LAUNCHER, triées par libellé. */
    fun listLaunchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> =
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        return resolved
            .map { info ->
                AppEntry(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                )
            }
            // Dédoublonnage par paquet (certaines applis exposent plusieurs alias).
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Trouve la meilleure appli correspondant au [query] parlé (insensible aux
     * accents/casse). Retourne null si aucun score suffisant.
     */
    fun findBestMatch(query: String): AppEntry? {
        val apps = listLaunchableApps()
        return apps
            .map { it to TextUtils.fuzzyScore(query, it.label) }
            .filter { it.second >= 0.6f }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Libellés seuls, pour lever l'ambiguïté « lance » dans le parseur. */
    fun labels(): List<String> = listLaunchableApps().map { it.label }

    /** Intent de lancement d'un paquet, ou null s'il n'est pas lançable. */
    fun launchIntentFor(packageName: String): Intent? =
        pm.getLaunchIntentForPackage(packageName)

    /** Libellé lisible d'un paquet donné (pour l'affichage debug). */
    fun labelForPackage(packageName: String): String? =
        listLaunchableApps().firstOrNull { it.packageName == packageName }?.label
}
