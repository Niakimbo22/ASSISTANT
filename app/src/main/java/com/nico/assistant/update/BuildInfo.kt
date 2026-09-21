package com.nico.assistant.update

import com.nico.assistant.BuildConfig

/**
 * Identité de ce build, telle que la CI l'a gravée dedans.
 *
 * `BUILD_NUMBER` vient de `github.run_number`, passé à Gradle par le workflow
 * (`-PbuildNumber=…`) et recopié dans `BuildConfig`. C'est le seul nombre qui
 * compte pour la mise à jour : la release GitHub est taguée `build-<N>` avec le
 * même numéro, donc comparer les deux suffit à savoir si on est en retard.
 *
 * Un build local vaut 0 — il ne se croira jamais plus récent qu'une release.
 */
object BuildInfo {

    /** Numéro de run CI ayant produit cet APK. 0 = build local. */
    val buildNumber: Int = BuildConfig.BUILD_NUMBER

    /** Nom de version affiché ailleurs dans Android (`1.0.<build>`). */
    val versionName: String = BuildConfig.VERSION_NAME

    /** true si cet APK sort d'un build local, jamais publié en release. */
    val isLocalBuild: Boolean = buildNumber <= 0

    /** Libellé court pour l'écran Réglages : « build 42 » ou « build local ». */
    val label: String
        get() = if (isLocalBuild) "build local" else "build $buildNumber"

    /** Le tag de release correspondant à ce build, pour tracer d'où il vient. */
    val releaseTag: String
        get() = ReleaseInfo.tagFor(buildNumber)
}
