package com.nico.assistant.action

/**
 * Catalogue des actions exécutables (spec §5.3).
 *
 * Lot 1 : seule l'énumération existe, elle sert de colonne typée à [com.nico.assistant.data.db.ActionEntity].
 * Les implémentations, le backend et le schéma de paramètres arrivent au lot 3 (`action/impl/`).
 * Ajouter une valeur ici ne doit jamais impliquer de branche `if` ailleurs.
 */
enum class ActionType {
    // Applications & navigation — INTENT
    LAUNCH_APP,
    OPEN_URL,
    OPEN_SETTINGS,
    SEND_INTENT,
    NAVIGATE_TO,
    SEARCH_WEB,

    // Communication — INTENT
    CALL_NUMBER,
    SEND_SMS,
    SHARE_TEXT,
    OPEN_WHATSAPP_CHAT,

    // Média — INTENT + ACCESSIBILITY
    PLAY_MUSIC_SEARCH,
    PLAY_MUSIC_UI,
    MEDIA_CONTROL,

    // Système — SHIZUKU (sauf TOGGLE_TORCH / SET_VOLUME, réalisables en INTERNAL)
    TOGGLE_WIFI,
    TOGGLE_BLUETOOTH,
    TOGGLE_DND,
    TOGGLE_AIRPLANE,
    TOGGLE_ROTATION,
    TOGGLE_TORCH,
    SET_VOLUME,
    SET_BRIGHTNESS,
    RUN_SHELL,

    // Utilitaires — INTERNAL
    SPEAK,
    VIBRATE,
    WAIT,
    SET_TIMER,
    SET_ALARM,
    CREATE_NOTE,
    COPY_TO_CLIPBOARD,
    SHOW_TOAST,
    RUN_AUTOMATION
}
