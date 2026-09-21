package com.nico.assistant.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le parsing de `/releases/latest` est la seule partie de la mise à jour qui se
 * teste sans appareil — et c'est celle qui décide si l'app se croit à jour ou non.
 */
class ReleaseInfoTest {

    @Test
    fun `lit le numero de build, l'APK et les notes`() {
        val release = ReleaseInfo.parse(RESPONSE).getOrThrow()

        assertEquals(15, release.buildNumber)
        assertEquals("build-15", release.tag)
        assertEquals("NicoAssistant-build-15.apk", release.apkName)
        assertEquals(
            "https://github.com/Niakimbo22/ASSISTANT/releases/download/build-15/" +
                "NicoAssistant-build-15.apk",
            release.apkUrl,
        )
        assertEquals(12_345_678L, release.sizeBytes)
        assertTrue(release.notes.contains("Lot 9"))
    }

    @Test
    fun `ignore les champs inconnus de l'API GitHub`() {
        // La réponse réelle contient des dizaines de champs qu'on n'utilise pas ;
        // un champ ajouté par GitHub ne doit jamais casser la vérification.
        val json = """
            {
              "tag_name": "build-3",
              "id": 987654,
              "author": { "login": "Niakimbo22", "site_admin": false },
              "assets": [
                {
                  "name": "NicoAssistant-build-3.apk",
                  "browser_download_url": "https://example.invalid/a.apk",
                  "size": 100,
                  "download_count": 42,
                  "uploader": { "login": "github-actions[bot]" }
                }
              ]
            }
        """.trimIndent()

        assertEquals(3, ReleaseInfo.parse(json).getOrThrow().buildNumber)
    }

    @Test
    fun `retient le premier asset APK meme si d'autres fichiers sont publies`() {
        val json = """
            {
              "tag_name": "build-7",
              "assets": [
                { "name": "mapping.txt", "browser_download_url": "https://x/m.txt", "size": 1 },
                { "name": "app.apk", "browser_download_url": "https://x/app.apk", "size": 2 }
              ]
            }
        """.trimIndent()

        val release = ReleaseInfo.parse(json).getOrThrow()
        assertEquals("app.apk", release.apkName)
        assertEquals("https://x/app.apk", release.apkUrl)
    }

    @Test
    fun `echoue clairement quand la release n'a pas d'APK`() {
        val json = """{ "tag_name": "build-4", "assets": [] }"""

        val error = ReleaseInfo.parse(json).exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("APK"))
    }

    @Test
    fun `echoue clairement quand le tag ne contient aucun numero`() {
        val json = """
            {
              "tag_name": "nightly",
              "assets": [
                { "name": "a.apk", "browser_download_url": "https://x/a.apk", "size": 1 }
              ]
            }
        """.trimIndent()

        val error = ReleaseInfo.parse(json).exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("Tag"))
    }

    @Test
    fun `echoue sans lever quand la reponse n'est pas du JSON`() {
        assertTrue(ReleaseInfo.parse("404: Not Found").isFailure)
    }

    @Test
    fun `accepte les tags poses a la main`() {
        assertEquals(15, ReleaseInfo.buildNumberFromTag("build-15"))
        assertEquals(15, ReleaseInfo.buildNumberFromTag("v15"))
        assertEquals(15, ReleaseInfo.buildNumberFromTag("15"))
        assertEquals(15, ReleaseInfo.buildNumberFromTag(" build-15 "))
        assertNull(ReleaseInfo.buildNumberFromTag("build-latest"))
    }

    @Test
    fun `le tag produit correspond a celui que pose la CI`() {
        assertEquals("build-42", ReleaseInfo.tagFor(42))
    }

    @Test
    fun `ne propose que les builds strictement plus recents`() {
        val release = ReleaseInfo.parse(RESPONSE).getOrThrow()

        assertTrue(release.isNewerThan(14))
        // Déjà à jour : proposer une réinstallation de la même version serait du bruit.
        assertFalse(release.isNewerThan(15))
        // Un build local en avance (cas du développeur) ne doit pas régresser.
        assertFalse(release.isNewerThan(16))
    }

    @Test
    fun `affiche une taille lisible`() {
        // La virgule décimale dépend de la locale de l'appareil : on la normalise
        // ici pour que le test dise quelque chose sur le calcul, pas sur le format.
        fun readable(bytes: Long) = info(sizeBytes = bytes).readableSize.replace(',', '.')

        assertEquals("12.3 Mo", readable(12_900_000L))
        assertEquals("500 Ko", readable(512_000L))
        assertEquals("taille inconnue", readable(0L))
    }

    private fun info(sizeBytes: Long) = ReleaseInfo(
        buildNumber = 1,
        tag = "build-1",
        apkName = "a.apk",
        apkUrl = "https://x/a.apk",
        sizeBytes = sizeBytes,
        notes = "",
    )

    private companion object {
        val RESPONSE = """
            {
              "tag_name": "build-15",
              "name": "Build 15",
              "body": "Lot 9 — mise à jour automatique\nBranche : claude/in-app-auto-update",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "NicoAssistant-build-15.apk",
                  "browser_download_url": "https://github.com/Niakimbo22/ASSISTANT/releases/download/build-15/NicoAssistant-build-15.apk",
                  "size": 12345678,
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()
    }
}
