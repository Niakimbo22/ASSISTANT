package com.nico.assistant.core.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/** Ce que la reconnaissance vocale rapporte, au fil de l'écoute. */
sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Level(val rms: Float) : SpeechEvent
    data class Partial(val text: String) : SpeechEvent

    /**
     * @param alternatives les hypothèses suivantes du STT. Les passer au moteur de matching
     * est ce qui règle l'essentiel du problème des noms propres mal reconnus.
     */
    data class Final(val best: String, val alternatives: List<String>) : SpeechEvent
    data class Failed(val code: Int, val message: String) : SpeechEvent
}

/**
 * Wrapper Flow autour de `SpeechRecognizer` (spec §8.2).
 *
 * Contrairement à la V1, les hypothèses secondaires ne sont pas jetées : elles descendent
 * jusqu'au moteur de matching.
 */
class SpeechManager(context: Context) {

    private val appContext = context.applicationContext

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    /**
     * Une session d'écoute. Le flux se termine de lui-même sur le résultat final ou sur erreur.
     *
     * `SpeechRecognizer` exige le thread principal, d'où le [flowOn].
     */
    fun listen(preferOffline: Boolean = true): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable) {
            trySend(SpeechEvent.Failed(-1, "Reconnaissance vocale indisponible"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.Ready)
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechEvent.Level(rmsdB))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                hypotheses(partialResults).firstOrNull()
                    ?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val all = hypotheses(results)
                if (all.isEmpty()) {
                    trySend(SpeechEvent.Failed(SpeechRecognizer.ERROR_NO_MATCH, "Rien entendu"))
                } else {
                    Log.i(TAG, "Entendu : ${all.joinToString(" | ")}")
                    trySend(SpeechEvent.Final(all.first(), all.drop(1)))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(error, errorMessage(error)))
                close()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onEndOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(buildIntent(preferOffline))

        awaitClose {
            runCatching {
                recognizer.stopListening()
                recognizer.destroy()
            }
        }
    }.flowOn(Dispatchers.Main.immediate)

    private fun buildIntent(preferOffline: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
            // Les noms propres anglais (artistes, applis) doivent passer.
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }

    private fun hypotheses(bundle: Bundle?): List<String> =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Erreur audio"
        SpeechRecognizer.ERROR_CLIENT -> "Erreur client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission micro manquante"
        SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Délai réseau dépassé"
        SpeechRecognizer.ERROR_NO_MATCH -> "Je n'ai rien compris"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconnaissance occupée"
        SpeechRecognizer.ERROR_SERVER -> "Erreur serveur"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Je n'ai rien entendu"
        else -> "Erreur de reconnaissance ($code)"
    }

    private companion object {
        const val TAG = "NICO_STT"
        const val LANGUAGE = "fr-FR"

        /** Essentiel : le matching teste les 5 hypothèses, pas seulement la première. */
        const val MAX_RESULTS = 5
    }
}
