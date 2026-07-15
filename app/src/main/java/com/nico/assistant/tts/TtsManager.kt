package com.nico.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Synthèse vocale (gratuite, moteur Android natif) en français.
 * Le moteur s'initialise de façon asynchrone ; on met en file d'attente les
 * messages émis avant la fin de l'initialisation.
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val pending = mutableListOf<String>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.FRANCE)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w(TAG, "Français indisponible pour la TTS, repli sur la locale par défaut")
                tts.language = Locale.getDefault()
            }
            ready = true
            // On vide la file des messages accumulés pendant l'init.
            pending.forEach { speakNow(it) }
            pending.clear()
        } else {
            Log.e(TAG, "Échec d'initialisation de la TTS (status=$status)")
        }
    }

    /** Prononce [text] ; si le moteur n'est pas prêt, le message est mis en file. */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (ready) speakNow(text) else pending.add(text)
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nico_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "NICO_TTS"
    }
}
