package com.nico.assistant.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import com.nico.assistant.data.db.ConditionType

/**
 * Icône de chaque condition « Seulement si ». Toutes partagent la couleur
 * [NicoColors.Condition] : dans l'éditeur, le cyan veut dire « condition ».
 */
val ConditionType.icon: ImageVector
    get() = when (this) {
        ConditionType.TIME_RANGE -> Symbols.Schedule
        ConditionType.DAY_OF_WEEK -> Symbols.CalendarMonth
        ConditionType.WIFI_CONNECTED -> Symbols.Wifi
        ConditionType.BATTERY_BELOW -> Symbols.Battery3BarFilled
        ConditionType.CHARGING -> Symbols.BatteryChargingFullFilled
        ConditionType.BLUETOOTH_CONNECTED -> Symbols.BluetoothConnected
        ConditionType.APP_FOREGROUND -> Symbols.AppsFilled
        ConditionType.HEADPHONES_PLUGGED -> Symbols.HeadphonesFilled
    }
