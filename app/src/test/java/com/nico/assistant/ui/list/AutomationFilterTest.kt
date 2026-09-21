package com.nico.assistant.ui.list

import com.nico.assistant.data.model.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationFilterTest {

    private val catalogue = listOf(
        Automation(name = "Appeler", phrases = listOf("appelle {contact}", "téléphone à {contact}")),
        Automation(name = "Mode nuit", phrases = listOf("bonne nuit", "je vais dormir")),
        Automation(name = "Lancer une app", phrases = listOf("ouvre {app}"))
    )

    @Test
    fun `une recherche vide rend tout le catalogue`() {
        assertEquals(catalogue, AutomationFilter.filter(catalogue, ""))
        assertEquals(catalogue, AutomationFilter.filter(catalogue, "   "))
    }

    @Test
    fun `la recherche porte sur le nom`() {
        assertEquals(listOf("Mode nuit"), AutomationFilter.filter(catalogue, "nuit").map { it.name })
    }

    @Test
    fun `la recherche porte aussi sur les phrases`() {
        assertEquals(listOf("Lancer une app"), AutomationFilter.filter(catalogue, "ouvre").map { it.name })
    }

    @Test
    fun `la recherche ignore accents et casse`() {
        assertEquals(listOf("Appeler"), AutomationFilter.filter(catalogue, "TELEPHONE").map { it.name })
        assertEquals(listOf("Appeler"), AutomationFilter.filter(catalogue, "téléphone").map { it.name })
    }

    @Test
    fun `une recherche sans resultat rend une liste vide`() {
        assertTrue(AutomationFilter.filter(catalogue, "zzzz").isEmpty())
    }
}
