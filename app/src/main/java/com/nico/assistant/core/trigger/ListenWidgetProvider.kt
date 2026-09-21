package com.nico.assistant.core.trigger

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nico.assistant.MainActivity
import com.nico.assistant.R

/**
 * Widget d'écran d'accueil : un tap = écoute (spec §8.1).
 *
 * `RemoteViews` classique plutôt que Glance : le résultat est le même pour un widget d'un
 * seul bouton, sans ajouter un framework d'UI qu'on ne peut pas tester ici.
 */
class ListenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_LISTEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_listen).apply {
                setOnClickPendingIntent(R.id.widget_listen_root, pending)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
