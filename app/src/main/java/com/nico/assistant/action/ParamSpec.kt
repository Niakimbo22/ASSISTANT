package com.nico.assistant.action

/**
 * Description d'un paramètre d'action. C'est **elle qui génère l'UI** : l'éditeur ne
 * contient aucun formulaire écrit à la main (spec §5.2).
 */
data class ParamSpec(
    val key: String,
    /** « Application à lancer » */
    val label: String,
    val type: ParamType,
    val required: Boolean = true,
    val default: String? = null,
    val hint: String? = null,
    /** valeur → libellé, pour [ParamType.ENUM]. */
    val options: List<Pair<String, String>> = emptyList()
)

enum class ParamType {
    /** Champ libre, accepte les `{slots}`. */
    TEXT,
    NUMBER,
    /** Liste des apps réellement installées — jamais de paquet codé en dur. */
    APP_PICKER,
    CONTACT_PICKER,
    URL,
    DURATION,
    /** on / off / bascule */
    TOGGLE,
    /** Menu déroulant alimenté par [ParamSpec.options]. */
    ENUM,
    SOUND_PICKER
}
