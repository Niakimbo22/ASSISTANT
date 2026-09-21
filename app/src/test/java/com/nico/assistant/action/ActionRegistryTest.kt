package com.nico.assistant.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fous du registre : c'est lui qui rend l'architecture extensible « par données ».
 * Si ces tests cassent, c'est qu'une action a été ajoutée à moitié.
 */
class ActionRegistryTest {

    @Test
    fun `chaque action est enregistree sous son propre type`() {
        for (action in ActionRegistry.all) {
            assertEquals(action.label, action, ActionRegistry.get(action.type))
        }
    }

    @Test
    fun `tout le catalogue declare est implemente`() {
        // Règle d'or de la spec §5.4 : ajouter un type sans sa classe est un bug.
        assertEquals(ActionType.entries.toSet(), ActionRegistry.implementedTypes)
        assertEquals(ActionType.entries.size, ActionRegistry.all.size)
    }

    @Test
    fun `chaque type se retrouve par son enum`() {
        for (type in ActionType.entries) {
            assertNotNull("$type introuvable", ActionRegistry.find(type))
            assertTrue(ActionRegistry.isImplemented(type))
        }
        assertFalse(ActionRegistry.all.any { it.label.isBlank() })
    }

    @Test
    fun `les cles de parametres sont uniques et non vides`() {
        for (action in ActionRegistry.all) {
            val keys = action.paramsSchema.map { it.key }
            assertEquals("doublon de clé dans ${action.type}", keys.size, keys.toSet().size)
            assertTrue("clé vide dans ${action.type}", keys.none { it.isBlank() })
            assertTrue("libellé vide pour ${action.type}", action.label.isNotBlank())
        }
    }

    @Test
    fun `les actions systeme annoncent un repli, sauf la commande shell`() {
        val shizuku = ActionRegistry.all.filter { it.backend == Backend.SHIZUKU }

        assertTrue(shizuku.isNotEmpty())
        assertTrue(shizuku.filter { it.type != ActionType.RUN_SHELL }.all { it.hasFallback })
        // Une commande shell arbitraire n'a, elle, aucun repli possible.
        assertFalse(ActionRegistry.get(ActionType.RUN_SHELL).hasFallback)
    }

    @Test
    fun `un parametre ENUM propose des options`() {
        val enums = ActionRegistry.all
            .flatMap { it.paramsSchema }
            .filter { it.type == ParamType.ENUM }

        assertTrue(enums.isNotEmpty())
        assertTrue(enums.all { it.options.isNotEmpty() })
    }

    @Test
    fun `sans Shizuku ni accessibilite, seules les actions intent et internes sont proposees`() {
        val disponibles = ActionRegistry.availableFor { it == Backend.INTENT || it == Backend.INTERNAL }

        assertTrue(disponibles.isNotEmpty())
        assertTrue(disponibles.none { it.backend == Backend.SHIZUKU })
        assertTrue(disponibles.none { it.backend == Backend.ACCESSIBILITY })
        // Le pilotage de l'app musique, lui, exige le service d'accessibilité.
        assertTrue(ActionRegistry.all.any { it.backend == Backend.ACCESSIBILITY })
        assertTrue(ActionRegistry.all.any { it.backend == Backend.SHIZUKU })
    }

    @Test
    fun `le catalogue est regroupe par categorie`() {
        val parCategorie = ActionRegistry.byCategory()

        assertTrue(parCategorie.containsKey(ActionCategory.APPS))
        assertTrue(parCategorie.containsKey(ActionCategory.COMMUNICATION))
        assertTrue(parCategorie.containsKey(ActionCategory.UTILITAIRES))
        assertTrue(parCategorie.containsKey(ActionCategory.MEDIA))
        assertTrue(parCategorie.containsKey(ActionCategory.SYSTEME))
        assertEquals(ActionRegistry.all.size, parCategorie.values.sumOf { it.size })
    }
}
