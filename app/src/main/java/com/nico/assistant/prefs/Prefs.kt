package com.nico.assistant.prefs

import android.content.Context

/**
 * Accès centralisé aux préférences (SharedPreferences).
 * On y stocke le paquet de l'appli musique choisie (RVX, nom inconnu à priori)
 * et le comportement d'écoute automatique au démarrage.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Nom de paquet de l'appli musique cible sélectionnée par l'utilisateur. */
    var musicPackage: String?
        get() = sp.getString(KEY_MUSIC_PKG, null)
        set(value) = sp.edit().putString(KEY_MUSIC_PKG, value).apply()

    /** Écouter automatiquement le micro à l'ouverture de l'appli. */
    var autoListen: Boolean
        get() = sp.getBoolean(KEY_AUTO_LISTEN, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_LISTEN, value).apply()

    companion object {
        // Doit correspondre au chemin déclaré dans backup_rules.xml.
        const val FILE = "nico_assistant_prefs"
        private const val KEY_MUSIC_PKG = "music_package"
        private const val KEY_AUTO_LISTEN = "auto_listen"
    }
}
