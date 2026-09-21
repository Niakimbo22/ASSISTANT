package com.nico.assistant.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `minuscules, accents et ponctuation disparaissent`() {
        assertEquals("quelle heure il est", TextNormalizer.normalize("Quelle heure il est ?"))
        assertEquals("ecoute france inter", TextNormalizer.normalize("Écoute France Inter"))
        assertEquals("telephone a jean michel", TextNormalizer.normalize("Téléphone à Jean-Michel"))
        assertEquals("je m en vais", TextNormalizer.normalize("Je m'en vais !"))
        assertEquals("ca va etre ou", TextNormalizer.normalize("Ça va être où"))
    }

    @Test
    fun `les espaces multiples sont ecrases`() {
        assertEquals("ouvre youtube", TextNormalizer.normalize("   Ouvre    YouTube  "))
    }

    @Test
    fun `les accolades des slots survivent a la normalisation`() {
        assertEquals("ouvre {app}", TextNormalizer.normalize("Ouvre {app}"))
        assertEquals("mets un timer de {duree} minutes", TextNormalizer.normalize("Mets un timer de {duree} minutes"))
        assertTrue(SlotExtractor.hasSlots(TextNormalizer.normalize("appelle {mon_contact}")))
    }

    @Test
    fun `les nombres francais deviennent des chiffres`() {
        val cases = mapOf(
            "quinze" to "15",
            "zero" to "0",
            "dix" to "10",
            "dix sept" to "17",
            "vingt" to "20",
            "vingt et un" to "21",
            "trente cinq" to "35",
            "soixante dix" to "70",
            "soixante quinze" to "75",
            "soixante et onze" to "71",
            "quatre vingt" to "80",
            "quatre vingts" to "80",
            "quatre vingt dix" to "90",
            "quatre vingt dix sept" to "97",
            "cent" to "100",
            "cent vingt" to "120",
            "deux cents" to "200",
            "trois cent cinquante" to "350",
            "deux cent quatre vingt dix neuf" to "299"
        )
        for ((words, digits) in cases) {
            assertEquals("« $words »", digits, TextNormalizer.wordsToDigits(words))
        }
    }

    @Test
    fun `les nombres composes passent aussi par les traits d union`() {
        assertEquals("mets un timer de 90 minutes", TextNormalizer.normalize("Mets un timer de quatre-vingt-dix minutes"))
        assertEquals("monte le volume a 75", TextNormalizer.normalize("Monte le volume à soixante-quinze"))
    }

    @Test
    fun `un article reste un mot, un un de nombre devient un chiffre`() {
        // « un » isolé est un article : il doit rester un mot pour que les mots vides l'écartent.
        assertEquals("ouvre un fichier", TextNormalizer.wordsToDigits("ouvre un fichier"))
        // Mais à l'intérieur d'un nombre, c'est bien un 1.
        assertEquals("21 bougies", TextNormalizer.wordsToDigits("vingt et un bougies"))
        assertEquals("101 dalmatiens", TextNormalizer.wordsToDigits("cent un dalmatiens"))
    }

    @Test
    fun `le et hors nombre reste intact`() {
        assertEquals("toi et moi", TextNormalizer.wordsToDigits("toi et moi"))
        assertEquals("20 et des poussieres", TextNormalizer.wordsToDigits("vingt et des poussieres"))
    }

    @Test
    fun `les chiffres deja ecrits passent sans dommage`() {
        assertEquals("mets un timer de 15 minutes", TextNormalizer.normalize("Mets un timer de 15 minutes"))
    }

    @Test
    fun `les mots vides ne sautent que pour le scoring`() {
        assertEquals(listOf("mets", "timer", "15", "minutes"), TextNormalizer.scoringTokens("mets un timer de 15 minutes"))
        assertEquals(listOf("allume", "lampe"), TextNormalizer.scoringTokens("allume la lampe"))
        // La normalisation, elle, ne retire rien.
        assertEquals("allume la lampe", TextNormalizer.normalize("Allume la lampe"))
    }

    @Test
    fun `les formules de politesse sont retirees du scoring`() {
        assertEquals(listOf("ouvre", "youtube"), TextNormalizer.scoringTokens("ouvre youtube s il te plait"))
        assertEquals(listOf("pause"), TextNormalizer.scoringTokens("pause stp"))
    }

    @Test
    fun `une phrase entierement composee de mots vides garde ses tokens`() {
        assertEquals(listOf("le", "la"), TextNormalizer.scoringTokens("le la"))
    }
}
