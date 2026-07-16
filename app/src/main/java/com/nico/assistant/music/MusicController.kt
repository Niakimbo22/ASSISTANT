package com.nico.assistant.music

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.nico.assistant.a11y.MusicAccessibilityService
import com.nico.assistant.prefs.Prefs

sealed class MusicResult {
    data class Launched(val usedAccessibility: Boolean) : MusicResult()
    object NoAppSelected : MusicResult()
    object LaunchFailed : MusicResult()
}

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

        val launchedViaIntent = tryStrategyA(pkg, query)

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

        if (a11yEnabled) {
            Log.i(TAG, "Service d'accessibilité actif -> armement de la stratégie B")
            MusicAccessibilityService.enqueue(pkg, query)
        } else {
            Log.i(TAG, "Service d'accessibilité inactif -> stratégie A uniquement")
        }

        return MusicResult.Launched(usedAccessibility = a11yEnabled)
    }

    private fun tryStrategyA(pkg: String, query: String): Boolean {
        val candidates = buildList {
            add(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(pkg)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager.QUERY, query)
                putExtra(Intent.EXTRA_TEXT, query)
            })
            add(Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, query)
                putExtra(Intent.EXTRA_TEXT, query)
            })
            add(Intent(Intent.ACTION_VIEW).apply {
                setPackage(pkg)
                data = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
            })
        }

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolvable = intent.resolveActivity(appContext.packageManager) != null
            Log.d(TAG, "Stratégie A: essai ${intent.action} data=${intent.data} resolvable=$resolvable")
            if (true) {
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
