package com.nico.assistant.action

/**
 * Pictogramme d'une action, porté par [ActionType].
 *
 * Volontairement indépendant de Compose : le design system (`ui/theme/ActionVisuals.kt`)
 * associe chaque valeur à son icône Material Symbols. Le `when` y est exhaustif, donc
 * ajouter un pictogramme ici sans lui donner d'icône ne compile pas.
 */
enum class ActionGlyph {
    APPS,
    LINK,
    SETTINGS,
    SEND,
    NAVIGATION,
    WEB_SEARCH,
    CALL,
    SMS,
    SHARE,
    CHAT,
    MUSIC,
    MUSIC_QUEUE,
    PLAY_PAUSE,
    WIFI,
    BLUETOOTH,
    DO_NOT_DISTURB,
    AIRPLANE,
    ROTATION,
    TORCH,
    VOLUME,
    BRIGHTNESS,
    TERMINAL,
    SPEAK,
    VIBRATE,
    WAIT,
    TIMER,
    ALARM,
    NOTE,
    COPY,
    NOTIFICATION,
    AUTOMATION
}
