package com.nico.assistant.action.impl

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.apps.AppRepository
import com.nico.assistant.core.executor.ExecutionContext
import java.net.URLEncoder

/**
 * Lance une application installée.
 *
 * Le paramètre accepte aussi bien un nom de paquet (choisi dans l'APP_PICKER) qu'un
 * libellé parlé arrivé par un slot : « ouvre {app} » passe « you tube », pas un paquet.
 * Aucun nom de paquet n'est jamais codé en dur — RVX Music est sideloadé.
 */
class LaunchAppAction : Action {

    override val type = ActionType.LAUNCH_APP
    override val backend = Backend.INTENT
    override val label = "Lancer une application"
    override val category = ActionCategory.APPS
    override val description = "Ouvre l'application choisie"

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_PACKAGE,
            label = "Application à lancer",
            type = ParamType.APP_PICKER,
            hint = "Accepte un {slot} : « ouvre {app} »"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val wanted = params.required(PARAM_PACKAGE)
            ?: return ActionResult.Failure("Aucune application indiquée")

        val apps = AppRepository(ctx.context)
        val match = apps.launchIntentFor(wanted)
            ?: apps.findBestMatch(wanted)?.let { apps.launchIntentFor(it.packageName) }
            ?: return ActionResult.Failure("Application introuvable : $wanted")

        return ctx.launch(match)
    }

    companion object {
        const val PARAM_PACKAGE = "package"
    }
}

/** Ouvre un lien. Le schéma est ajouté si l'utilisateur ne l'a pas écrit. */
class OpenUrlAction : Action {

    override val type = ActionType.OPEN_URL
    override val backend = Backend.INTENT
    override val label = "Ouvrir un lien"
    override val category = ActionCategory.APPS

    override val paramsSchema = listOf(
        ParamSpec(key = PARAM_URL, label = "Adresse", type = ParamType.URL, hint = "exemple.fr")
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val raw = params.required(PARAM_URL) ?: return ActionResult.Failure("Aucune adresse indiquée")
        val url = if (raw.contains("://")) raw else "https://$raw"
        return ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object {
        const val PARAM_URL = "url"
    }
}

/** Recherche web. Repli sur une URL de recherche si aucune app ne gère ACTION_WEB_SEARCH. */
class SearchWebAction : Action {

    override val type = ActionType.SEARCH_WEB
    override val backend = Backend.INTENT
    override val label = "Rechercher sur le web"
    override val category = ActionCategory.APPS

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_QUERY,
            label = "Recherche",
            type = ParamType.TEXT,
            hint = "Accepte un {slot}"
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val query = params.required(PARAM_QUERY) ?: return ActionResult.Failure("Rien à chercher")

        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        val direct = ctx.launch(search)
        if (direct is ActionResult.Success) return direct

        val encoded = URLEncoder.encode(query, "UTF-8")
        return ctx.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")))
    }

    companion object {
        const val PARAM_QUERY = "query"
    }
}
