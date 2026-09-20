package com.nico.assistant.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun `distance de levenshtein`() {
        assertEquals(0, FuzzyMatcher.levenshtein("pause", "pause"))
        assertEquals(1, FuzzyMatcher.levenshtein("je part", "je pars"))
        assertEquals(2, FuzzyMatcher.levenshtein("quel heure il est", "quelle heure il est"))
        assertEquals(5, FuzzyMatcher.levenshtein("", "pause"))
        assertEquals(5, FuzzyMatcher.levenshtein("pause", ""))
    }

    @Test
    fun `la similarite est normalisee entre 0 et 1`() {
        assertEquals(1f, FuzzyMatcher.similarity("pause", "pause"), 0.0001f)
        assertEquals(0f, FuzzyMatcher.similarity("abcd", "wxyz"), 0.0001f)
        assertTrue(FuzzyMatcher.similarity("je part", "je pars") > 0.8f)
    }

    @Test
    fun `le recouvrement compte les mots de la phrase stockee`() {
        assertEquals(1f, FuzzyMatcher.tokenOverlap("allume la lampe torche", "allume la lampe"), 0.0001f)
        assertEquals(0.5f, FuzzyMatcher.tokenOverlap("stop", "stop la musique"), 0.0001f)
        assertEquals(0f, FuzzyMatcher.tokenOverlap("bonne nuit", "ouvre youtube"), 0.0001f)
    }

    @Test
    fun `une phrase identique atteint le score maximum`() {
        assertEquals(1f, FuzzyMatcher.score("bonne nuit", "bonne nuit"), 0.0001f)
    }

    @Test
    fun `une faute du STT reste largement au-dessus du seuil de confiance`() {
        assertTrue(FuzzyMatcher.score("je part", "je pars") >= 0.75f)
        assertTrue(FuzzyMatcher.score("quel heure il est", "quelle heure il est") >= 0.75f)
        assertTrue(FuzzyMatcher.score("bonne nuie", "bonne nuit") >= 0.75f)
    }

    @Test
    fun `deux phrases etrangeres restent sous le plancher`() {
        assertTrue(FuzzyMatcher.score("raconte moi une blague", "coupe le wifi") < 0.45f)
        assertTrue(FuzzyMatcher.score("ouvre youtube", "bonne nuit") < 0.45f)
    }

    @Test
    fun `le premier mot porte l intention`() {
        val memePremierMot = FuzzyMatcher.score("ouvre le garage", "ouvre le portail")
        val premierMotDifferent = FuzzyMatcher.score("ferme le garage", "ouvre le portail")
        assertTrue(memePremierMot > premierMotDifferent)
    }

    @Test
    fun `une chaine vide ne score jamais`() {
        assertEquals(0f, FuzzyMatcher.score("", "pause"), 0.0001f)
        assertEquals(0f, FuzzyMatcher.score("pause", ""), 0.0001f)
    }
}
