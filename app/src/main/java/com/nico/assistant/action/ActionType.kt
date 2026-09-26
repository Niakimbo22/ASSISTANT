package com.nico.assistant.action

/**
 * Catalogue des actions exécutables (spec §5.3).
 *
 * Lot 1 : seule l'énumération existe, elle sert de colonne typée à [com.nico.assistant.data.db.ActionEntity].
 * Les implémentations, le backend et le schéma de paramètres arrivent au lot 3 (`action/impl/`).
 * Ajouter une valeur ici ne doit jamais impliquer de branche `if` ailleurs.
 *
 * Chaque type porte son [ActionGlyph] : l'UI n'a jamais à décider elle-même de l'icône
 * d'une action. La couleur, elle, vient de la catégorie ([ActionCategory.accent]).
 */
enum class ActionType(
    /** Pictogramme affiché partout où l'action apparaît (liste, éditeur, sélecteur). */
    val glyph: ActionGlyph
) {
    // Applications & navigation — INTENT
    LAUNCH_APP(ActionGlyph.APPS),
    OPEN_URL(ActionGlyph.LINK),
    OPEN_SETTINGS(ActionGlyph.SETTINGS),
    SEND_INTENT(ActionGlyph.SEND),
    NAVIGATE_TO(ActionGlyph.NAVIGATION),
    SEARCH_WEB(ActionGlyph.WEB_SEARCH),

    // Communication — INTENT
    CALL_NUMBER(ActionGlyph.CALL),
    SEND_SMS(ActionGlyph.SMS),
    SHARE_TEXT(ActionGlyph.SHARE),
    OPEN_WHATSAPP_CHAT(ActionGlyph.CHAT),

    // Média — INTENT + ACCESSIBILITY
    PLAY_MUSIC_SEARCH(ActionGlyph.MUSIC),
    PLAY_MUSIC_UI(ActionGlyph.MUSIC_QUEUE),
    MEDIA_CONTROL(ActionGlyph.PLAY_PAUSE),

    // Système — SHIZUKU (sauf TOGGLE_TORCH / SET_VOLUME, réalisables en INTERNAL)
    TOGGLE_WIFI(ActionGlyph.WIFI),
    TOGGLE_BLUETOOTH(ActionGlyph.BLUETOOTH),
    TOGGLE_DND(ActionGlyph.DO_NOT_DISTURB),
    TOGGLE_AIRPLANE(ActionGlyph.AIRPLANE),
    TOGGLE_ROTATION(ActionGlyph.ROTATION),
    TOGGLE_TORCH(ActionGlyph.TORCH),
    SET_VOLUME(ActionGlyph.VOLUME),
    SET_BRIGHTNESS(ActionGlyph.BRIGHTNESS),
    RUN_SHELL(ActionGlyph.TERMINAL),

    // Utilitaires — INTERNAL
    SPEAK(ActionGlyph.SPEAK),
    VIBRATE(ActionGlyph.VIBRATE),
    WAIT(ActionGlyph.WAIT),
    SET_TIMER(ActionGlyph.TIMER),
    SET_ALARM(ActionGlyph.ALARM),
    CREATE_NOTE(ActionGlyph.NOTE),
    COPY_TO_CLIPBOARD(ActionGlyph.COPY),
    SHOW_TOAST(ActionGlyph.NOTIFICATION),
    RUN_AUTOMATION(ActionGlyph.AUTOMATION)
}
