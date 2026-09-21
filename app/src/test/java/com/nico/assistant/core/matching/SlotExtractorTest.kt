package com.nico.assistant.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotExtractorTest {

    @Test
    fun `une phrase sans accolade n a pas de slot`() {
        assertFalse(SlotExtractor.hasSlots("allume la lampe"))
        assertEquals(emptyList<String>(), SlotExtractor.slotNames("allume la lampe"))
    }

    @Test
    fun `les noms de slots sont extraits dans l ordre`() {
        assertEquals(listOf("app"), SlotExtractor.slotNames("ouvre {app}"))
        assertEquals(
            listOf("contact", "message"),
            SlotExtractor.slotNames("envoie a {contact} le message {message}")
        )
    }

    @Test
    fun `un slot en fin de phrase capture le reste`() {
        assertEquals(mapOf("app" to "youtube"), SlotExtractor.extract("ouvre youtube", "ouvre {app}"))
        assertEquals(
            mapOf("contact" to "jean michel"),
            SlotExtractor.extract("appelle jean michel", "appelle {contact}")
        )
    }

    @Test
    fun `un slot au milieu s arrete a la partie fixe suivante`() {
        assertEquals(
            mapOf("duree" to "15"),
            SlotExtractor.extract("mets un timer de 15 minutes", "mets un timer de {duree} minutes")
        )
        assertEquals(
            mapOf("duree" to "90"),
            SlotExtractor.extract("mets un timer de 90 minutes", "mets un timer de {duree} minutes")
        )
    }

    @Test
    fun `plusieurs slots sont captures separement`() {
        assertEquals(
            mapOf("contact" to "paul", "message" to "j arrive"),
            SlotExtractor.extract("envoie a paul le message j arrive", "envoie a {contact} le message {message}")
        )
    }

    @Test
    fun `une entree qui ne colle pas a la structure ne capture rien`() {
        assertNull(SlotExtractor.extract("lance youtube", "ouvre {app}"))
        assertNull(SlotExtractor.extract("mets un timer", "mets un timer de {duree} minutes"))
        // Un slot exige au moins un caractère : « ouvre » tout court ne suffit pas.
        assertNull(SlotExtractor.extract("ouvre", "ouvre {app}"))
    }

    @Test
    fun `la partie fixe ne se laisse pas interpreter comme une regex`() {
        // Les points, parenthèses et crochets d'une phrase normalisée restent littéraux.
        val pattern = SlotExtractor.compile("ouvre {app} c est parti")
        assertNull(pattern.match("ouvre youtube xxest parti"))
        assertEquals(mapOf("app" to "youtube"), pattern.match("ouvre youtube c est parti"))
    }

    @Test
    fun `le squelette est la phrase privee de ses trous`() {
        assertEquals("ouvre", SlotExtractor.skeleton("ouvre {app}"))
        assertEquals("mets un timer de minutes", SlotExtractor.skeleton("mets un timer de {duree} minutes"))
        assertEquals("envoie a le message", SlotExtractor.skeleton("envoie a {contact} le message {message}"))
    }

    @Test
    fun `une phrase sans slot compile en correspondance stricte`() {
        val pattern = SlotExtractor.compile("allume la lampe")
        assertTrue(pattern.slotNames.isEmpty())
        assertEquals(emptyMap<String, String>(), pattern.match("allume la lampe"))
        assertNull(pattern.match("allume la lampe torche"))
    }
}
