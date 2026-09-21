package com.nico.assistant.action.impl

import android.app.Application
import android.app.SearchManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.nico.assistant.R
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionResult
import com.nico.assistant.core.executor.ExecutionContext
import com.nico.assistant.core.executor.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Les actions de la première vague, vérifiées sur les intents réellement émis. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionsTest {

    private lateinit var application: Application
    private lateinit var spoken: MutableList<String>
    private lateinit var ctx: ExecutionContext

    @Before
    fun setUp() {
        // Toast exige Dispatchers.Main ; le looper de Robolectric est en pause par défaut.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
        spoken = mutableListOf()
        ctx = ExecutionContext(application, emptyMap(), Speaker { spoken += it })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Applications & web --------------------------------------------------

    @Test
    fun `ouvrir un lien complete le schema manquant`() = runTest {
        val result = OpenUrlAction().execute(ctx, mapOf("url" to "exemple.fr"))

        assertTrue(result is ActionResult.Success)
        val intent = lastActivity()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://exemple.fr", intent.data.toString())
    }

    @Test
    fun `ouvrir un lien respecte un schema deja present`() = runTest {
        OpenUrlAction().execute(ctx, mapOf("url" to "http://nothing.tech"))
        assertEquals("http://nothing.tech", lastActivity().data.toString())
    }

    @Test
    fun `ouvrir un lien sans adresse echoue proprement`() = runTest {
        val result = OpenUrlAction().execute(ctx, mapOf("url" to "  "))
        assertTrue(result is ActionResult.Failure)
    }

    @Test
    fun `la recherche web transmet la requete`() = runTest {
        val result = SearchWebAction().execute(ctx, mapOf("query" to "horaires train lyon"))

        assertTrue(result is ActionResult.Success)
        val intent = lastActivity()
        assertEquals(Intent.ACTION_WEB_SEARCH, intent.action)
        assertEquals("horaires train lyon", intent.getStringExtra(SearchManager.QUERY))
    }

    @Test
    fun `lancer une app par son nom de paquet`() = runTest {
        val result = LaunchAppAction().execute(ctx, mapOf("package" to application.packageName))

        assertTrue(describe(result), result is ActionResult.Success)
        assertNotNull(lastActivity())
    }

    @Test
    fun `lancer une app par son libelle parle`() = runTest {
        val label = application.getString(R.string.app_name)

        val result = LaunchAppAction().execute(ctx, mapOf("package" to label))

        assertTrue(describe(result), result is ActionResult.Success)
    }

    @Test
    fun `lancer une app inconnue echoue sans planter`() = runTest {
        val result = LaunchAppAction().execute(ctx, mapOf("package" to "zzz application fantome"))

        assertTrue(result is ActionResult.Failure)
        assertTrue((result as ActionResult.Failure).reason.contains("introuvable"))
    }

    // --- Communication -------------------------------------------------------

    @Test
    fun `appeler sans numero ni contact echoue`() = runTest {
        val result = CallNumberAction().execute(ctx, emptyMap())

        assertTrue(result is ActionResult.Failure)
        assertTrue((result as ActionResult.Failure).reason.contains("Ni numéro ni contact"))
    }

    @Test
    fun `sans autorisation d appel, le composeur s ouvre pre-rempli`() = runTest {
        val result = CallNumberAction().execute(ctx, mapOf("number" to "0612345678"))

        assertTrue(describe(result), result is ActionResult.Success)
        val intent = lastActivity()
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:0612345678", intent.data.toString())
    }

    @Test
    fun `un contact deja saisi en chiffres est utilise tel quel`() = runTest {
        CallNumberAction().execute(ctx, mapOf("contact" to "0612345678"))
        assertEquals("tel:0612345678", lastActivity().data.toString())
    }

    @Test
    fun `un SMS s ouvre pre-rempli par defaut`() = runTest {
        val result = SendSmsAction().execute(
            ctx,
            mapOf("number" to "0612345678", "message" to "Je suis parti")
        )

        assertTrue(describe(result), result is ActionResult.Success)
        val intent = lastActivity()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:0612345678", intent.data.toString())
        assertEquals("Je suis parti", intent.getStringExtra("sms_body"))
    }

    @Test
    fun `un SMS sans message echoue`() = runTest {
        val result = SendSmsAction().execute(ctx, mapOf("number" to "0612345678"))
        assertTrue(result is ActionResult.Failure)
    }

    // --- Utilitaires ---------------------------------------------------------

    @Test
    fun `parler transmet le texte au moteur vocal`() = runTest {
        val result = SpeakAction().execute(ctx, mapOf("text" to "Il est 22:30"))

        assertTrue(result is ActionResult.Success)
        assertEquals(listOf("Il est 22:30"), spoken)
    }

    @Test
    fun `parler sans texte echoue`() = runTest {
        assertTrue(SpeakAction().execute(ctx, emptyMap()) is ActionResult.Failure)
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun `le message a l ecran s affiche`() = runTest {
        val result = ShowToastAction().execute(ctx, mapOf("text" to "coucou"))

        assertTrue(describe(result), result is ActionResult.Success)
        assertEquals("coucou", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `vibrer rend la main`() = runTest {
        val result = VibrateAction().execute(ctx, mapOf("pattern" to VibrateAction.PATTERN_DOUBLE))
        assertTrue(describe(result), result is ActionResult.Success)
    }

    @Test
    fun `attendre repousse son propre delai maximum`() {
        val action = WaitAction()

        assertTrue(action.timeoutMs(mapOf("ms" to "30000")) > 30_000)
        // Une valeur absurde retombe sur la valeur par défaut plutôt que de bloquer la chaîne.
        assertTrue(action.timeoutMs(mapOf("ms" to "pas un nombre")) <= Action.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun `attendre respecte la duree demandee`() = runTest {
        val result = WaitAction().execute(ctx, mapOf("ms" to "250"))
        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun `attendre une duree nulle echoue`() = runTest {
        assertTrue(WaitAction().execute(ctx, mapOf("ms" to "0")) is ActionResult.Failure)
    }

    // --- Helpers -------------------------------------------------------------

    private fun lastActivity(): Intent = shadowOf(application).nextStartedActivity

    private fun describe(result: ActionResult): String = when (result) {
        is ActionResult.Success -> "Success(${result.message})"
        is ActionResult.Failure -> "Failure(${result.reason})"
    }
}
