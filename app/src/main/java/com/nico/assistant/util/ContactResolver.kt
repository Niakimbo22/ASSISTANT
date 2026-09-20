package com.nico.assistant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import com.nico.assistant.core.matching.FuzzyMatcher
import com.nico.assistant.core.matching.TextNormalizer

/** Un contact trouvé dans le carnet d'adresses. */
data class ContactMatch(val displayName: String, val number: String, val score: Float)

/**
 * Retrouve le numéro d'un contact à partir d'un nom **prononcé**, donc approximatif.
 *
 * Réutilise le [FuzzyMatcher] du moteur vocal : un nom propre mal reconnu par le STT
 * doit tomber sur le bon contact sans code spécifique.
 */
object ContactResolver {

    private const val MIN_SCORE = 0.6f

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun findBest(context: Context, spokenName: String): ContactMatch? {
        if (!hasPermission(context)) return null
        val wanted = TextNormalizer.normalize(spokenName)
        if (wanted.isEmpty()) return null

        val projection = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER)
        val cursor = runCatching {
            context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)
        }.getOrNull() ?: return null

        cursor.use { rows ->
            val nameColumn = rows.getColumnIndex(Phone.DISPLAY_NAME)
            val numberColumn = rows.getColumnIndex(Phone.NUMBER)
            if (nameColumn < 0 || numberColumn < 0) return null

            var best: ContactMatch? = null
            while (rows.moveToNext()) {
                val displayName = rows.getString(nameColumn) ?: continue
                val number = rows.getString(numberColumn) ?: continue
                val score = FuzzyMatcher.score(wanted, TextNormalizer.normalize(displayName))
                val currentBest = best
                if (score >= MIN_SCORE && (currentBest == null || score > currentBest.score)) {
                    best = ContactMatch(displayName, number, score)
                }
            }
            return best
        }
    }
}
