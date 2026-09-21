package com.nico.assistant.core.condition

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.util.Log
import com.nico.assistant.core.matching.TextNormalizer
import com.nico.assistant.data.db.ConditionType
import java.util.Calendar

/** Verdict d'une évaluation, avec la raison quand ça bloque — pour le journal et le TTS. */
data class ConditionVerdict(val satisfied: Boolean, val blockedBy: String? = null) {
    companion object {
        val SATISFIED = ConditionVerdict(true)
    }
}

/**
 * Confronte les conditions d'une automatisation à l'état réel du téléphone (spec §3).
 *
 * Toutes les conditions doivent être remplies : une automatisation conditionnée est une
 * automatisation qu'on ne veut surtout pas voir se déclencher au mauvais moment.
 */
class ConditionEvaluator(
    private val context: Context,
    private val now: () -> Calendar = { Calendar.getInstance() }
) {

    fun evaluate(conditions: List<Condition>): ConditionVerdict {
        for (condition in conditions) {
            val raw = runCatching { test(condition) }.getOrElse {
                Log.w(TAG, "Condition ${condition.type} inévaluable", it)
                // Une condition qu'on ne sait pas évaluer ne doit pas bloquer silencieusement.
                true
            }
            val satisfied = if (condition.negated) !raw else raw
            if (!satisfied) return ConditionVerdict(false, describe(condition))
        }
        return ConditionVerdict.SATISFIED
    }

    private fun test(condition: Condition): Boolean = when (condition.type) {
        ConditionType.TIME_RANGE -> {
            val start = ConditionRules.parseTime(condition.params["start"])
            val end = ConditionRules.parseTime(condition.params["end"])
            if (start == null || end == null) true
            else ConditionRules.inTimeRange(ConditionRules.minutesOf(now()), start, end)
        }

        ConditionType.DAY_OF_WEEK ->
            ConditionRules.matchesDay(ConditionRules.dayCodeOf(now()), condition.params["days"])

        ConditionType.WIFI_CONNECTED -> wifiConnected(condition.params["ssid"])

        ConditionType.BATTERY_BELOW -> {
            val threshold = condition.params["level"]?.trim()?.toIntOrNull() ?: 20
            (batteryLevel() ?: 100) < threshold
        }

        ConditionType.CHARGING -> charging()

        ConditionType.BLUETOOTH_CONNECTED -> bluetoothConnected(condition.params["device"])

        // Demanderait l'accès aux statistiques d'usage : hors périmètre V2. On ne bloque
        // pas l'automatisation pour une condition qu'on ne sait pas évaluer.
        ConditionType.APP_FOREGROUND ->
            throw UnsupportedOperationException("APP_FOREGROUND exige l'accès aux stats d'usage")

        ConditionType.HEADPHONES_PLUGGED -> headphonesPlugged()
    }

    private fun wifiConnected(ssid: String?): Boolean {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        if (!wifi.isWifiEnabled) return false
        if (ssid.isNullOrBlank()) return true
        @Suppress("DEPRECATION")
        val current = wifi.connectionInfo?.ssid?.trim('"').orEmpty()
        return TextNormalizer.normalize(current) == TextNormalizer.normalize(ssid)
    }

    private fun batteryLevel(): Int? =
        (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

    private fun charging(): Boolean {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?: return false
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun bluetoothConnected(device: String?): Boolean {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return false
        if (!adapter.isEnabled) return false
        val connected = adapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
            BluetoothProfile.STATE_CONNECTED
        if (device.isNullOrBlank()) return connected
        // Sans BLUETOOTH_CONNECT on ne peut pas nommer l'appareil : on se rabat sur « connecté ».
        return connected
    }

    private fun headphonesPlugged(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES }
    }

    private fun describe(condition: Condition): String {
        val label = CONDITION_LABELS[condition.type] ?: condition.type.name
        return if (condition.negated) "$label (inversée)" else label
    }

    companion object {
        private const val TAG = "NICO_EXEC"

        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET
        )

        val CONDITION_LABELS = mapOf(
            ConditionType.TIME_RANGE to "Plage horaire",
            ConditionType.DAY_OF_WEEK to "Jour de la semaine",
            ConditionType.WIFI_CONNECTED to "Wifi connecté",
            ConditionType.BATTERY_BELOW to "Batterie sous un seuil",
            ConditionType.CHARGING to "En charge",
            ConditionType.BLUETOOTH_CONNECTED to "Bluetooth connecté",
            ConditionType.APP_FOREGROUND to "App au premier plan",
            ConditionType.HEADPHONES_PLUGGED to "Écouteurs branchés"
        )
    }
}
