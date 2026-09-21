package com.nico.assistant.core.trigger

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nico.assistant.MainActivity

/**
 * Tuile Quick Settings (spec §8.1) : le déclencheur le plus fiable, parce qu'il ne dépend
 * ni d'un geste système ni d'un service d'accessibilité.
 *
 * Elle ouvre l'écran d'écoute plutôt que de démarrer un service en arrière-plan : depuis
 * Android 12, un service de premier plan « micro » lancé sans fenêtre visible est soumis à
 * des restrictions qu'on ne peut pas vérifier sans appareil.
 */
class AssistantTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Écouter"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_LISTEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
