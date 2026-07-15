package com.nico.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Reconnaissance vocale via l'API native Android SpeechRecognizer (gratuite,
 * aucune API/réseau tiers pour la reconnaissance).
 *
 * Langue principale fr-FR, mais on autorise les tokens anglais mêlés au français
 * (« lance du Travis Scott », « ouvre Snapchat ») en ne filtrant jamais la sortie
 * et en fournissant une préférence de langue.
 */
class SpeechManager(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    /** Callbacks vers l'UI. */
    var onPartial: ((String) -> Unit)? = null
    var onFinal: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStateChange: ((Boolean) -> Unit)? = null // true = en écoute

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening() {
        if (!isAvailable) {
            onError?.invoke("Reconnaissance vocale indisponible sur cet appareil")
            return
        }
        // On (re)crée le recognizer à chaque session pour éviter les états bloqués.
        destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(listener)
        }
        recognizer?.startListening(buildIntent())
        onStateChange?.invoke(true)
        Log.i(TAG, "Écoute démarrée (fr-FR)")
    }

    fun stopListening() {
        recognizer?.stopListening()
        onStateChange?.invoke(false)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // Langue principale : français de France.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
            // On ne restreint PAS aux seuls mots français : les noms propres
            // anglais (artistes, applis) doivent passer.
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(
                RecognizerIntent.EXTRA_CALLING_PACKAGE,
                appContext.packageName,
            )
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            onStateChange?.invoke(false)
        }

        override fun onError(error: Int) {
            onStateChange?.invoke(false)
            val msg = errorMessage(error)
            Log.w(TAG, "onError: $msg ($error)")
            onError?.invoke(msg)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            Log.i(TAG, "onResults: \"$text\"")
            onStateChange?.invoke(false)
            if (text.isNotBlank()) onFinal?.invoke(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onPartial?.invoke(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Erreur audio"
        SpeechRecognizer.ERROR_CLIENT -> "Erreur client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission micro manquante"
        SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Délai réseau dépassé"
        SpeechRecognizer.ERROR_NO_MATCH -> "Aucune commande reconnue"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconnaissance occupée"
        SpeechRecognizer.ERROR_SERVER -> "Erreur serveur"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucune parole détectée"
        else -> "Erreur inconnue ($code)"
    }

    companion object {
        private const val TAG = "NICO_SPEECH"
        private val DEFAULT_LOCALE: Locale = Locale.FRANCE
    }
}
