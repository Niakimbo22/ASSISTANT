package com.nico.assistant.music

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.nico.assistant.a11y.MusicAccessibilityService
import com.nico.assistant.prefs.Prefs

/** Résultat d'une tentative de lecture, pour le retour utilisateur (popup/TTS). */
sealed class MusicResult {
    data class Launched(val usedAccessibility: Boolean) : MusicResult()
    object NoAppSelected : MusicResult()
    object LaunchFailed : MusicResult()
}

/**
 * Orchestration de la lecture musicale sur l'appli cible (RVX Music sideloadé,
 * paquet INCONNU récupéré depuis les préférences — jamais codé en dur).
 *
 * STRATÉGIE A (rapide/propre) : lancer l'appli avec un intent de recherche
 * (ACTION_SEARCH / MEDIA_PLAY_FROM_SEARCH / URI YouTube Music).
 *
 * STRATÉGIE B (repli robuste) : si le service d'accessibilité est activé, on lui
 * délègue l'automatisation complète de l'UI (ouvrir la recherche, saisir, jouer
 * le premier résultat), ce qui fonctionne même si l'appli n'honore aucun intent.
 * Comme aucune API publique ne permet de savoir de façon fiable si la stratégie A
 * a réellement déclenché la lecture, B est armée automatiquement dès qu'elle est
 * disponible et prend le relais sur l'interface.
 */
class MusicController(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = Prefs(appContext)

    fun play(query: String): MusicResult {
        val pkg = prefs.musicPackage
        if (pkg.isNullOrBlank()) {
            Log.w(TAG, "Aucune appli musique sélectionnée")
            return MusicResult.NoAppSelected
        }
        Log.i(TAG, "Lecture demandée : query=\"$query\" pkg=$pkg")

        val a11yEnabled = MusicAccessibilityService.isEnabled(appContext)

        // --- Stratégie A : intents de recherche, essayés dans l'ordre. ---
        val launchedViaIntent = tryStrategyA(pkg, query)

        // Si aucun intent de recherche n'a pu être lancé, on ouvre l'appli
        // simplement afin que la stratégie B ait une fenêtre à piloter.
        if (!launchedViaIntent) {
            val plain = appContext.packageManager.getLaunchIntentForPackage(pkg)
            if (plain == null) {
                Log.e(TAG, "Impossible d'obtenir un intent de lancement pour $pkg")
                return MusicResult.LaunchFailed
            }
            plain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(plain)
            Log.i(TAG, "Appli lancée simplement (stratégie B pilotera l'UI)")
        }

        // --- Stratégie B : repli automatique via accessibilité, si activée. ---
        if (a11yEnabled) {
            Log.i(TAG, "Service d'accessibilité actif -> armement de la stratégie B")
            MusicAccessibilityService.enqueue(pkg, query)
        } else {
            Log.i(TAG, "Service d'accessibilité inactif -> stratégie A uniquement")
        }

        return MusicResult.Launched(usedAccessibility = a11yEnabled)
    }

    /**
     * Tente les intents de recherche. Retourne true si l'un d'eux a pu être
     * démarré. On force le paquet cible pour rester sur l'appli sélectionnée.
     */
    private fun tryStrategyA(pkg: String, query: String): Boolean {
        val candidates = buildList {
            // 1) Lecture directe depuis une recherche média (si supporté).
            add(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(pkg)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager.QUERY, query)
                putExtra(Intent.EXTRA_TEXT, query)
            })
            // 2) Recherche générique ACTION_SEARCH.
            add(Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, query)
                putExtra(Intent.EXTRA_TEXT, query)
            })
            // 3) URI de recherche YouTube Music (souvent honorée par les forks).
            add(Intent(Intent.ACTION_VIEW).apply {
                setPackage(pkg)
                data = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
            })
        }

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // resolveActivity nous dit si l'appli déclare gérer cet intent.
            val resolvable = intent.resolveActivity(appContext.packageManager) != null
            Log.d(TAG, "Stratégie A: essai ${intent.action} data=${intent.data} resolvable=$resolvable")
            if (resolvable) {
                return try {
                    appContext.startActivity(intent)
                    Log.i(TAG, "Stratégie A: lancé via ${intent.action}")
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Stratégie A: échec de ${intent.action}: ${e.message}")
                    false
                }
            }
        }
        return false
    }

    companion object {
        private const val TAG = "NICO_MUSIC"
    }
}
