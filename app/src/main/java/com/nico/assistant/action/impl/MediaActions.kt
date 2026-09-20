package com.nico.assistant.action.impl

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent
import com.nico.assistant.a11y.MusicAccessibilityService
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.apps.AppRepository
import com.nico.assistant.core.executor.ExecutionContext

/**
 * Stratégie A de la V1 : demander à l'app musique de chercher, par intent.
 *
 * Elle n'est plus couplée à la stratégie B par un `if` : la chaîne
 * « PLAY_MUSIC_SEARCH non critique → PLAY_MUSIC_UI » fait le repli, et se modifie
 * depuis le téléphone.
 */
class PlayMusicSearchAction : Action {

    override val type = ActionType.PLAY_MUSIC_SEARCH
    override val backend = Backend.INTENT
    override val label = "Lancer une recherche musicale"
    override val category = ActionCategory.MEDIA
    override val description = "Stratégie par intent, sans accessibilité"

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_APP, label = "Application musique", type = ParamType.APP_PICKER),
        ParamSpec(
            key = PARAM_QUERY,
            label = "Titre ou artiste",
            type = ParamType.TEXT,
            hint = "Accepte un {slot} : « mets {titre} »"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val pkg = resolvePackage(ctx, params) ?: return ActionResult.Failure("Application musique introuvable")
        val query = params.required(PARAM_QUERY) ?: return ActionResult.Failure("Rien à chercher")

        for (intent in searchIntents(pkg, query)) {
            val result = ctx.launch(intent)
            if (result is ActionResult.Success) return ActionResult.Success("Recherche « $query »")
        }
        return ActionResult.Failure("Aucun intent de recherche accepté par $pkg")
    }

    companion object {
        const val PARAM_APP = "app"
        const val PARAM_QUERY = "query"

        /**
         * Le paquet peut arriver sous forme de libellé parlé ; on ne code jamais en dur
         * un nom de paquet, l'APK RVX étant sideloadé.
         */
        internal fun resolvePackage(ctx: ExecutionContext, params: Map<String, String>): String? {
            val wanted = params.required(PARAM_APP) ?: return null
            val apps = AppRepository(ctx.context)
            if (apps.launchIntentFor(wanted) != null) return wanted
            return apps.findBestMatch(wanted)?.packageName
        }

        /** Les trois tentatives de la V1, dans le même ordre. */
        internal fun searchIntents(pkg: String, query: String): List<Intent> = listOf(
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(pkg)
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager.QUERY, query)
                putExtra(Intent.EXTRA_TEXT, query)
            },
            Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, query)
                putExtra(Intent.EXTRA_TEXT, query)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(pkg)
                data = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
            }
        )
    }
}

/** Stratégie B de la V1 : piloter l'UI de l'app musique via le service d'accessibilité. */
class PlayMusicUiAction : Action {

    override val type = ActionType.PLAY_MUSIC_UI
    override val backend = Backend.ACCESSIBILITY
    override val label = "Piloter l'app musique"
    override val category = ActionCategory.MEDIA
    override val description = "Repli : tape la recherche dans l'interface"

    override val paramsSchema = listOf(
        ParamSpec(key = PlayMusicSearchAction.PARAM_APP, label = "Application musique", type = ParamType.APP_PICKER),
        ParamSpec(key = PlayMusicSearchAction.PARAM_QUERY, label = "Titre ou artiste", type = ParamType.TEXT)
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        if (!MusicAccessibilityService.isEnabled(ctx.context)) {
            return ActionResult.Failure("Service d'accessibilité désactivé")
        }
        val pkg = PlayMusicSearchAction.resolvePackage(ctx, params)
            ?: return ActionResult.Failure("Application musique introuvable")
        val query = params.required(PlayMusicSearchAction.PARAM_QUERY)
            ?: return ActionResult.Failure("Rien à chercher")

        // L'app doit être au premier plan pour que le service ait une UI à piloter.
        AppRepository(ctx.context).launchIntentFor(pkg)?.let { ctx.launch(it) }
        MusicAccessibilityService.enqueue(pkg, query)
        return ActionResult.Success("Pilotage de $pkg")
    }
}

/**
 * Lecture / pause / piste suivante, par événement de touche média.
 *
 * `dispatchMediaKeyEvent` atteint l'app musique active sans aucune permission spéciale,
 * contrairement à `MediaSessionManager` qui exige l'accès aux notifications.
 */
class MediaControlAction : Action {

    override val type = ActionType.MEDIA_CONTROL
    override val backend = Backend.INTERNAL
    override val label = "Contrôler la lecture"
    override val category = ActionCategory.MEDIA

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_COMMAND,
            label = "Commande",
            type = ParamType.ENUM,
            default = COMMAND_PLAY_PAUSE,
            options = listOf(
                COMMAND_PLAY_PAUSE to "Lecture / pause",
                COMMAND_PLAY to "Lecture",
                COMMAND_PAUSE to "Pause",
                COMMAND_NEXT to "Piste suivante",
                COMMAND_PREVIOUS to "Piste précédente",
                COMMAND_STOP to "Arrêter"
            )
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val command = params[PARAM_COMMAND]?.trim()?.lowercase() ?: COMMAND_PLAY_PAUSE
        val keyCode = KEY_CODES[command]
            ?: return ActionResult.Failure("Commande de lecture inconnue : $command")

        val audio = ctx.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ActionResult.Failure("Service audio indisponible")

        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return ActionResult.Success()
    }

    companion object {
        const val PARAM_COMMAND = "command"
        const val COMMAND_PLAY_PAUSE = "playpause"
        const val COMMAND_PLAY = "play"
        const val COMMAND_PAUSE = "pause"
        const val COMMAND_NEXT = "next"
        const val COMMAND_PREVIOUS = "previous"
        const val COMMAND_STOP = "stop"

        private val KEY_CODES = mapOf(
            COMMAND_PLAY_PAUSE to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            COMMAND_PLAY to KeyEvent.KEYCODE_MEDIA_PLAY,
            COMMAND_PAUSE to KeyEvent.KEYCODE_MEDIA_PAUSE,
            COMMAND_NEXT to KeyEvent.KEYCODE_MEDIA_NEXT,
            COMMAND_PREVIOUS to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            COMMAND_STOP to KeyEvent.KEYCODE_MEDIA_STOP
        )
    }
}
