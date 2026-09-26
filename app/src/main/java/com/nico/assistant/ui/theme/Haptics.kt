package com.nico.assistant.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Retour haptique léger, via les constantes système : il suit le moteur de vibration du
 * téléphone et les réglages de l'utilisateur (désactivé si l'haptique tactile l'est).
 */
class Haptics(private val view: View) {

    /** Tap sur une surface : le plus discret. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun toggle(on: Boolean) {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    /** Validation d'une action importante (enregistrer, lancer l'écoute). */
    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    /** Refus : action impossible, suppression. */
    fun reject() {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    /** Début d'un geste (glisser pour supprimer, déplacer). */
    fun gestureStart() {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.GESTURE_START
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
