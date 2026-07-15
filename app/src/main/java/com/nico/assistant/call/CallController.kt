package com.nico.assistant.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.nico.assistant.util.TextUtils

/** Un contact trouvé : nom affiché + numéro. */
data class ContactMatch(
    val displayName: String,
    val number: String,
)

/**
 * Recherche de contacts (ContactsContract) et déclenchement d'appel.
 * Le matching est flou et insensible aux accents ; en cas de multiples
 * correspondances, l'appelant (UI) présente un choix.
 */
class CallController(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Retourne les contacts correspondant à [query], triés par pertinence.
     * Nécessite la permission READ_CONTACTS (vérifiée en amont par l'UI).
     */
    fun findContacts(query: String): List<ContactMatch> {
        val results = mutableListOf<Pair<ContactMatch, Float>>()
        val resolver = appContext.contentResolver

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIdx =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx =
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx < 0 || numIdx < 0) return emptyList()

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                val number = cursor.getString(numIdx) ?: continue
                val score = TextUtils.fuzzyScore(query, name)
                if (score >= 0.6f) {
                    results.add(ContactMatch(name, number) to score)
                }
            }
        }

        // On dédoublonne par (nom, numéro) et on trie par score décroissant.
        return results
            .distinctBy { it.first.displayName to it.first.number.filter { c -> c.isDigit() } }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Lance l'appel via ACTION_CALL (nécessite CALL_PHONE, accordée en amont).
     * FLAG_ACTIVITY_NEW_TASK car on peut être appelé hors contexte d'activité.
     */
    fun placeCall(number: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
