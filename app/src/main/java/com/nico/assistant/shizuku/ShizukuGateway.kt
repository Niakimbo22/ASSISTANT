package com.nico.assistant.shizuku

/**
 * Ce dont une action a besoin de Shizuku, et rien de plus.
 *
 * L'interface permet d'exécuter et de tester les actions système sans Shizuku installé.
 */
interface ShizukuGateway {

    fun isReady(): Boolean

    /** Exécute une commande shell avec les droits ADB. */
    suspend fun exec(command: String): Result<String>

    companion object {
        /** Aucun accès : toutes les actions SHIZUKU devront passer par leur repli. */
        val UNAVAILABLE = object : ShizukuGateway {
            override fun isReady() = false
            override suspend fun exec(command: String): Result<String> =
                Result.failure(IllegalStateException("Shizuku indisponible"))
        }
    }
}

/** Étapes de l'onboarding Shizuku (spec §6.4). */
enum class ShizukuState {
    UNKNOWN,
    NOT_RUNNING,
    UNSUPPORTED,
    PERMISSION_NEEDED,
    PERMISSION_DENIED,
    READY;

    val isReady: Boolean get() = this == READY

    val title: String
        get() = when (this) {
            UNKNOWN -> "État inconnu"
            NOT_RUNNING -> "Shizuku n'est pas démarré"
            UNSUPPORTED -> "Version de Shizuku trop ancienne"
            PERMISSION_NEEDED -> "Autorisation à accorder"
            PERMISSION_DENIED -> "Autorisation refusée"
            READY -> "Shizuku est prêt"
        }

    val advice: String
        get() = when (this) {
            UNKNOWN -> "Vérification en cours…"
            NOT_RUNNING ->
                "Installe Shizuku depuis le Play Store puis démarre-le. Sur Android 11+, " +
                    "pas besoin de PC : Options développeur → Débogage sans fil → colle le code " +
                    "d'appairage dans Shizuku."
            UNSUPPORTED -> "Mets Shizuku à jour : les versions antérieures à la 11 ne sont pas gérées."
            PERMISSION_NEEDED -> "Autorise NicoAssistant à utiliser Shizuku."
            PERMISSION_DENIED -> "Autorisation refusée. Relance la demande pour réessayer."
            READY -> "Shizuku doit être relancé après chaque redémarrage du téléphone."
        }
}
