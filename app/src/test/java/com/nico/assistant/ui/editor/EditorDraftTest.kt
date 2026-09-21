package com.nico.assistant.ui.editor

import com.nico.assistant.action.ActionType
import com.nico.assistant.core.condition.Condition
import com.nico.assistant.data.db.ConditionType
import com.nico.assistant.data.db.MatchMode
import com.nico.assistant.data.model.ActionSpec
import com.nico.assistant.data.model.Automation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Toute la logique de l'éditeur, testée sans Compose ni Android. */
class EditorDraftTest {

    @Test
    fun `un brouillon vierge n est pas valide et dit pourquoi`() {
        val draft = EditorDraft.blank()

        assertFalse(draft.isValid)
        assertEquals("Donne un nom à cette automatisation", draft.validationMessage)
        assertTrue(draft.isNew)
    }

    @Test
    fun `la validation guide etape par etape`() {
        var draft = EditorDraft.blank().withName("Mode sortie")
        assertEquals("Ajoute au moins une phrase déclenchante", draft.validationMessage)

        draft = draft.addPhrase("je pars")
        assertEquals("Ajoute au moins une action", draft.validationMessage)

        draft = draft.addAction(ActionSpec(type = ActionType.SPEAK))
        assertNull(draft.validationMessage)
        assertTrue(draft.isValid)
    }

    @Test
    fun `la phrase entendue mais non reconnue pre-remplit l editeur`() {
        val draft = EditorDraft.blank("coupe le wifi")

        assertEquals(listOf("coupe le wifi"), draft.phrases)
    }

    @Test
    fun `une phrase vide ou en double est ignoree`() {
        val draft = EditorDraft.blank()
            .addPhrase("je pars")
            .addPhrase("  ")
            .addPhrase("Je pars !")
            .addPhrase("je m'en vais")

        assertEquals(listOf("je pars", "je m'en vais"), draft.phrases)
    }

    @Test
    fun `retirer une phrase par son index`() {
        val draft = EditorDraft.blank().addPhrase("un").addPhrase("deux").addPhrase("trois")

        assertEquals(listOf("un", "trois"), draft.removePhrase(1).phrases)
        assertEquals(3, draft.removePhrase(9).phrases.size)
    }

    @Test
    fun `les slots disponibles viennent des phrases et du systeme`() {
        val draft = EditorDraft.blank()
            .addPhrase("mets un timer de {duree} minutes")
            .addPhrase("appelle {contact}")

        val slots = draft.availableSlots

        assertTrue(slots.containsAll(listOf("duree", "contact")))
        assertTrue(slots.containsAll(EditorDraft.SYSTEM_SLOTS))
        assertEquals(slots.size, slots.distinct().size)
    }

    @Test
    fun `ajouter, remplacer et retirer une action`() {
        var draft = EditorDraft.blank()
            .addAction(ActionSpec(type = ActionType.TOGGLE_WIFI))
            .addAction(ActionSpec(type = ActionType.SPEAK))

        assertEquals(listOf(ActionType.TOGGLE_WIFI, ActionType.SPEAK), draft.actions.map { it.type })

        draft = draft.replaceAction(0, draft.actions[0].copy(critical = true))
        assertTrue(draft.actions[0].critical)

        draft = draft.removeAction(0)
        assertEquals(listOf(ActionType.SPEAK), draft.actions.map { it.type })
    }

    @Test
    fun `dupliquer une action l insere juste apres avec un nouvel identifiant`() {
        val draft = EditorDraft.blank()
            .addAction(ActionSpec(type = ActionType.SPEAK, params = mapOf("text" to "salut")))
            .addAction(ActionSpec(type = ActionType.VIBRATE))
            .duplicateAction(0)

        assertEquals(
            listOf(ActionType.SPEAK, ActionType.SPEAK, ActionType.VIBRATE),
            draft.actions.map { it.type }
        )
        assertEquals("salut", draft.actions[1].params["text"])
        assertTrue(draft.actions[0].id != draft.actions[1].id)
    }

    @Test
    fun `reordonner la chaine`() {
        val draft = EditorDraft.blank()
            .addAction(ActionSpec(type = ActionType.TOGGLE_WIFI))
            .addAction(ActionSpec(type = ActionType.SPEAK))
            .addAction(ActionSpec(type = ActionType.VIBRATE))

        assertEquals(
            listOf(ActionType.SPEAK, ActionType.TOGGLE_WIFI, ActionType.VIBRATE),
            draft.moveAction(0, 1).actions.map { it.type }
        )
        assertEquals(
            listOf(ActionType.VIBRATE, ActionType.TOGGLE_WIFI, ActionType.SPEAK),
            draft.moveAction(2, 0).actions.map { it.type }
        )
    }

    @Test
    fun `un deplacement hors bornes ne change rien`() {
        val draft = EditorDraft.blank().addAction(ActionSpec(type = ActionType.SPEAK))

        assertEquals(draft.actions, draft.moveAction(0, -1).actions)
        assertEquals(draft.actions, draft.moveAction(0, 5).actions)
        assertEquals(draft.actions, draft.moveAction(0, 0).actions)
    }

    @Test
    fun `les conditions s ajoutent et se retirent`() {
        val draft = EditorDraft.blank()
            .addCondition(Condition(type = ConditionType.CHARGING))
            .addCondition(Condition(type = ConditionType.TIME_RANGE))

        assertEquals(2, draft.conditions.size)
        assertEquals(listOf(ConditionType.TIME_RANGE), draft.removeCondition(0).conditions.map { it.type })
    }

    @Test
    fun `editer une automatisation existante ne la considere pas comme nouvelle`() {
        val existing = Automation(
            name = "Mode nuit",
            phrases = listOf("bonne nuit"),
            matchMode = MatchMode.CONTAINS,
            actions = listOf(ActionSpec(type = ActionType.SPEAK)),
            runCount = 12
        )

        val draft = EditorDraft.of(existing)

        assertFalse(draft.isNew)
        assertTrue(draft.isValid)
        assertEquals(MatchMode.CONTAINS, draft.matchMode)
        // L'historique doit traverser l'édition intacte.
        assertEquals(12, draft.withName("Mode nuit v2").automation.runCount)
    }

    @Test
    fun `un feedback vide redevient nul plutot qu une chaine blanche`() {
        val draft = EditorDraft.blank().withFeedbackText("Bonne route")
        assertEquals("Bonne route", draft.automation.feedbackText)
        assertNull(draft.withFeedbackText("   ").automation.feedbackText)
    }

    @Test
    fun `les reglages secondaires sont modifiables`() {
        val draft = EditorDraft.blank()
            .withMatchMode(MatchMode.REGEX)
            .withPriority(5)
            .withConfirmBeforeRun(true)
            .withEnabled(false)

        assertEquals(MatchMode.REGEX, draft.matchMode)
        assertEquals(5, draft.automation.priority)
        assertTrue(draft.automation.confirmBeforeRun)
        assertFalse(draft.automation.enabled)
    }
}
