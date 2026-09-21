package com.nico.assistant.core.condition

import com.nico.assistant.action.ParamType
import com.nico.assistant.data.db.ConditionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionRulesTest {

    @Test
    fun `une heure se lit en minutes`() {
        assertEquals(0, ConditionRules.parseTime("00:00"))
        assertEquals(1320, ConditionRules.parseTime("22:00"))
        assertEquals(450, ConditionRules.parseTime(" 07:30 "))
    }

    @Test
    fun `une heure illisible ne casse rien`() {
        assertNull(ConditionRules.parseTime(null))
        assertNull(ConditionRules.parseTime("midi"))
        assertNull(ConditionRules.parseTime("25:00"))
        assertNull(ConditionRules.parseTime("22:61"))
        assertNull(ConditionRules.parseTime("2200"))
    }

    @Test
    fun `une plage dans la journee`() {
        val start = ConditionRules.parseTime("09:00")!!
        val end = ConditionRules.parseTime("18:00")!!

        assertTrue(ConditionRules.inTimeRange(ConditionRules.parseTime("09:00")!!, start, end))
        assertTrue(ConditionRules.inTimeRange(ConditionRules.parseTime("13:37")!!, start, end))
        assertFalse(ConditionRules.inTimeRange(ConditionRules.parseTime("18:00")!!, start, end))
        assertFalse(ConditionRules.inTimeRange(ConditionRules.parseTime("08:59")!!, start, end))
        assertFalse(ConditionRules.inTimeRange(ConditionRules.parseTime("23:00")!!, start, end))
    }

    @Test
    fun `une plage qui passe minuit couvre la nuit, pas la journee`() {
        val start = ConditionRules.parseTime("22:00")!!
        val end = ConditionRules.parseTime("07:00")!!

        assertTrue(ConditionRules.inTimeRange(ConditionRules.parseTime("23:30")!!, start, end))
        assertTrue(ConditionRules.inTimeRange(ConditionRules.parseTime("03:00")!!, start, end))
        assertTrue(ConditionRules.inTimeRange(ConditionRules.parseTime("22:00")!!, start, end))
        assertFalse(ConditionRules.inTimeRange(ConditionRules.parseTime("07:00")!!, start, end))
        assertFalse(ConditionRules.inTimeRange(ConditionRules.parseTime("12:00")!!, start, end))
        assertFalse(ConditionRules.inTimeRange(ConditionRules.parseTime("21:59")!!, start, end))
    }

    @Test
    fun `les jours se comparent sans se soucier de la casse`() {
        assertTrue(ConditionRules.matchesDay("MON", "MON,TUE,WED"))
        assertTrue(ConditionRules.matchesDay("mon", " mon , tue "))
        assertFalse(ConditionRules.matchesDay("SAT", "MON,TUE,WED"))
    }

    @Test
    fun `sans liste de jours, tous les jours conviennent`() {
        assertTrue(ConditionRules.matchesDay("SUN", null))
        assertTrue(ConditionRules.matchesDay("SUN", ""))
        assertTrue(ConditionRules.matchesDay("SUN", "  ,  "))
    }
}

class ConditionSpecsTest {

    @Test
    fun `chaque type de condition a un libelle`() {
        for (type in ConditionType.entries) {
            assertTrue("$type sans libellé", ConditionSpecs.label(type).isNotBlank())
        }
    }

    @Test
    fun `les conditions parametrees ont un schema, les autres non`() {
        assertEquals(2, ConditionSpecs.paramsOf(ConditionType.TIME_RANGE).size)
        assertEquals(1, ConditionSpecs.paramsOf(ConditionType.BATTERY_BELOW).size)
        assertTrue(ConditionSpecs.paramsOf(ConditionType.CHARGING).isEmpty())
        assertTrue(ConditionSpecs.paramsOf(ConditionType.HEADPHONES_PLUGGED).isEmpty())
    }

    @Test
    fun `les cles de schema sont uniques et non vides`() {
        for (type in ConditionType.entries) {
            val keys = ConditionSpecs.paramsOf(type).map { it.key }
            assertEquals("$type", keys.size, keys.toSet().size)
            assertTrue("$type", keys.none { it.isBlank() })
        }
    }

    @Test
    fun `les valeurs par defaut d une plage horaire sont lisibles`() {
        val defaults = ConditionSpecs.defaultsOf(ConditionType.TIME_RANGE)

        assertEquals(setOf("start", "end"), defaults.keys)
        assertTrue(defaults.values.all { ConditionRules.parseTime(it) != null })
    }

    @Test
    fun `le resume dit l essentiel`() {
        val condition = Condition(
            type = ConditionType.TIME_RANGE,
            params = mapOf("start" to "22:00", "end" to "07:00")
        )
        assertTrue(ConditionSpecs.summarize(condition).contains("22:00"))
        assertTrue(ConditionSpecs.summarize(condition.copy(negated = true)).contains("inversée"))
    }

    @Test
    fun `l app au premier plan se choisit dans les apps installees`() {
        val spec = ConditionSpecs.paramsOf(ConditionType.APP_FOREGROUND).single()
        assertEquals(ParamType.APP_PICKER, spec.type)
    }
}
