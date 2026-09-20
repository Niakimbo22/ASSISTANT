package com.nico.assistant.core.executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotResolverTest {

    private val slots = mapOf(
        "titre" to "daft punk",
        "duree" to "15",
        "heure" to "22:30"
    )

    @Test
    fun `un slot connu est remplace par sa valeur`() {
        assertEquals("daft punk", SlotResolver.resolve("{titre}", slots))
        assertEquals("Il est 22:30", SlotResolver.resolve("Il est {heure}", slots))
    }

    @Test
    fun `plusieurs slots dans la meme valeur`() {
        assertEquals(
            "15 minutes de daft punk",
            SlotResolver.resolve("{duree} minutes de {titre}", slots)
        )
    }

    @Test
    fun `un slot inconnu est laisse visible plutot que vide`() {
        assertEquals("appelle {contact}", SlotResolver.resolve("appelle {contact}", slots))
        assertTrue(SlotResolver.hasUnresolved(SlotResolver.resolve("appelle {contact}", slots)))
    }

    @Test
    fun `une valeur sans slot est rendue telle quelle`() {
        assertEquals("allume la lampe", SlotResolver.resolve("allume la lampe", slots))
        assertFalse(SlotResolver.hasUnresolved("allume la lampe"))
    }

    @Test
    fun `tous les parametres sont resolus d un coup`() {
        val params = mapOf("query" to "{titre}", "app" to "spotify", "text" to "Je mets {titre}")

        assertEquals(
            mapOf("query" to "daft punk", "app" to "spotify", "text" to "Je mets daft punk"),
            SlotResolver.resolveAll(params, slots)
        )
    }

    @Test
    fun `les accolades mal formees ne cassent rien`() {
        assertEquals("{ titre }", SlotResolver.resolve("{ titre }", slots))
        assertEquals("100 %", SlotResolver.resolve("100 %", slots))
    }
}
