package com.nico.assistant.data.db

/** Comment une phrase stockée est comparée à ce que le STT a entendu (spec §3). */
enum class MatchMode {
    /** Égalité stricte après normalisation. */
    EXACT,

    /** La phrase stockée est contenue dans ce qui est dit. */
    CONTAINS,

    /** Distance de Levenshtein sous seuil — défaut. */
    FUZZY,

    /** Cas avancés, saisi en mode expert. */
    REGEX
}

/** Conditions optionnelles évaluées avant l'exécution d'une chaîne (spec §3). */
enum class ConditionType {
    /** `start="22:00"`, `end="07:00"` */
    TIME_RANGE,

    /** `days="MON,TUE,WED"` */
    DAY_OF_WEEK,

    /** `ssid="Freebox"` (optionnel) */
    WIFI_CONNECTED,

    /** `level="20"` */
    BATTERY_BELOW,

    CHARGING,

    /** `device="Nothing Ear (3)"` */
    BLUETOOTH_CONNECTED,

    /** `package="..."` */
    APP_FOREGROUND,

    HEADPHONES_PLUGGED
}
