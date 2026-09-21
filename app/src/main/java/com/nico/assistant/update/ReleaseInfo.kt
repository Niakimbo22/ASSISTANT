package com.nico.assistant.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Une release GitHub réduite à ce dont l'app a besoin : un numéro de build, et
 * une URL d'APK téléchargeable sans jeton.
 *
 * On passe par les *releases* et pas par les artefacts Actions : l'API artifacts
 * exige une authentification même sur un dépôt public, alors qu'un asset de
 * release s'attrape avec un simple GET anonyme.
 */
data class ReleaseInfo(
    val buildNumber: Int,
    val tag: String,
    val apkName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val notes: String,
) {
    /** Vrai si cette release est plus récente que le build installé. */
    fun isNewerThan(currentBuildNumber: Int): Boolean = buildNumber > currentBuildNumber

    /** Taille lisible, affichée avant de lancer un téléchargement sur données mobiles. */
    val readableSize: String
        get() = when {
            sizeBytes <= 0L -> "taille inconnue"
            sizeBytes < 1024L * 1024L -> "${sizeBytes / 1024L} Ko"
            else -> "%.1f Mo".format(sizeBytes / (1024.0 * 1024.0))
        }

    companion object {
        /** Dépôt interrogé. Public : aucune authentification n'est nécessaire. */
        const val REPOSITORY = "Niakimbo22/ASSISTANT"

        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$REPOSITORY/releases/latest"

        /** Le tag que la CI pose sur chaque build : `build-15`. */
        fun tagFor(buildNumber: Int): String = "build-$buildNumber"

        /**
         * Extrait le numéro de build d'un tag. On accepte `build-15`, `v15` ou `15`
         * pour ne pas se retrouver bloqué si un tag est posé à la main un jour.
         */
        fun buildNumberFromTag(tag: String): Int? =
            Regex("(\\d+)\\s*\\z").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

        /**
         * Lit la réponse de `/releases/latest`. Renvoie un échec explicite plutôt
         * qu'une exception opaque : le message part tel quel dans l'UI, c'est le
         * seul diagnostic disponible sur un téléphone sans câble.
         */
        fun parse(json: String): Result<ReleaseInfo> = runCatching {
            val dto = lenientJson.decodeFromString(ReleaseDto.serializer(), json)

            val buildNumber = buildNumberFromTag(dto.tagName)
                ?: error("Tag de release illisible : « ${dto.tagName} »")

            val apk = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: error("La release ${dto.tagName} ne contient pas d'APK")

            ReleaseInfo(
                buildNumber = buildNumber,
                tag = dto.tagName,
                apkName = apk.name,
                apkUrl = apk.browserDownloadUrl,
                sizeBytes = apk.size,
                notes = dto.body.orEmpty().trim(),
            )
        }

        private val lenientJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private data class AssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
)
