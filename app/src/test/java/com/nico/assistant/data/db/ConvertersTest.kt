package com.nico.assistant.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

/** Les converters sont du Kotlin pur : pas besoin de Robolectric ici. */
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `une liste de phrases fait l aller-retour sans perte`() {
        val phrases = listOf("je pars", "je m'en vais", "mets un timer de {duree} minutes")

        val restored = converters.jsonToStringList(converters.stringListToJson(phrases))

        assertEquals(phrases, restored)
    }

    @Test
    fun `les accents, guillemets et accolades survivent au JSON`() {
        val phrases = listOf("téléphone à {contact}", "dis \"bonjour\"", "écoute {titre}")

        val restored = converters.jsonToStringList(converters.stringListToJson(phrases))

        assertEquals(phrases, restored)
    }

    @Test
    fun `une map de parametres fait l aller-retour sans perte`() {
        val params = mapOf("package" to "com.spotify.music", "query" to "{titre}", "level" to "0")

        val restored = converters.jsonToStringMap(converters.stringMapToJson(params))

        assertEquals(params, restored)
    }

    @Test
    fun `les collections vides restent vides`() {
        assertEquals(emptyList<String>(), converters.jsonToStringList(converters.stringListToJson(emptyList())))
        assertEquals(emptyMap<String, String>(), converters.jsonToStringMap(converters.stringMapToJson(emptyMap())))
    }

    @Test
    fun `une valeur illisible degrade au lieu de crasher`() {
        assertEquals(emptyList<String>(), converters.jsonToStringList("pas du json"))
        assertEquals(emptyMap<String, String>(), converters.jsonToStringMap("{{{"))
    }
}
