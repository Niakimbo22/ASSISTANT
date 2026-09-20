package com.nico.assistant.shizuku

import com.nico.assistant.action.impl.ToggleParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStateTest {

    @Test
    fun `seul READY est pret`() {
        assertTrue(ShizukuState.READY.isReady)
        for (state in ShizukuState.entries.filter { it != ShizukuState.READY }) {
            assertFalse("$state", state.isReady)
        }
    }

    @Test
    fun `chaque etat sait se presenter a l utilisateur`() {
        for (state in ShizukuState.entries) {
            assertTrue("$state sans titre", state.title.isNotBlank())
            assertTrue("$state sans conseil", state.advice.isNotBlank())
        }
    }

    @Test
    fun `le rappel du redemarrage est bien la`() {
        assertTrue(ShizukuState.READY.advice.contains("redémarrage"))
    }

    @Test
    fun `l acces indisponible echoue sans exception`() {
        assertFalse(ShizukuGateway.UNAVAILABLE.isReady())
    }
}

class ToggleParamTest {

    @Test
    fun `on et off sont explicites`() {
        assertTrue(ToggleParam.resolve(mapOf("state" to "on"), current = false))
        assertFalse(ToggleParam.resolve(mapOf("state" to "off"), current = true))
        assertTrue(ToggleParam.resolve(mapOf("state" to "true"), current = false))
        assertFalse(ToggleParam.resolve(mapOf("state" to "false"), current = true))
    }

    @Test
    fun `bascule s appuie sur l etat reel`() {
        assertTrue(ToggleParam.resolve(mapOf("state" to "toggle"), current = false))
        assertFalse(ToggleParam.resolve(mapOf("state" to "toggle"), current = true))
    }

    @Test
    fun `une bascule sans etat connu allume`() {
        assertTrue(ToggleParam.resolve(mapOf("state" to "toggle"), current = null))
    }

    @Test
    fun `sans parametre, on active`() {
        assertTrue(ToggleParam.resolve(emptyMap(), current = null))
    }

    @Test
    fun `le schema propose la bascule par defaut`() {
        val spec = ToggleParam.spec()
        assertEquals(ToggleParam.KEY, spec.key)
        assertEquals(ToggleParam.ON, spec.default)
    }
}
