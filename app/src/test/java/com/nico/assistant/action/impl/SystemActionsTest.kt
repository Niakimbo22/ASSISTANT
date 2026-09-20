package com.nico.assistant.action.impl

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.nico.assistant.action.ActionResult
import com.nico.assistant.core.executor.ExecutionContext
import com.nico.assistant.core.executor.Speaker
import com.nico.assistant.shizuku.ShizukuGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Les actions système avec et sans Shizuku : quand il manque, elles doivent se rabattre,
 * jamais échouer en silence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemActionsTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    // --- Avec Shizuku --------------------------------------------------------

    @Test
    fun `couper le wifi passe la commande svc`() = runTest {
        val shizuku = FakeShizuku(ready = true)

        val result = ToggleWifiAction().execute(context(shizuku), mapOf("state" to "off"))

        assertTrue(describe(result), result is ActionResult.Success)
        assertEquals(listOf("svc wifi disable"), shizuku.commands)
    }

    @Test
    fun `allumer le bluetooth passe la commande svc`() = runTest {
        val shizuku = FakeShizuku(ready = true)

        ToggleBluetoothAction().execute(context(shizuku), mapOf("state" to "on"))

        assertEquals(listOf("svc bluetooth enable"), shizuku.commands)
    }

    @Test
    fun `le mode avion et le ne-pas-deranger ont leurs commandes`() = runTest {
        val avion = FakeShizuku(ready = true)
        ToggleAirplaneAction().execute(context(avion), mapOf("state" to "on"))
        assertEquals(listOf("cmd connectivity airplane-mode enable"), avion.commands)

        val dnd = FakeShizuku(ready = true)
        ToggleDndAction().execute(context(dnd), mapOf("state" to "on"))
        assertEquals(listOf("cmd notification set_dnd priority"), dnd.commands)
    }

    @Test
    fun `la rotation et la luminosite ecrivent dans settings`() = runTest {
        val rotation = FakeShizuku(ready = true)
        ToggleRotationAction().execute(context(rotation), mapOf("state" to "off"))
        assertEquals(listOf("settings put system accelerometer_rotation 0"), rotation.commands)

        val luminosite = FakeShizuku(ready = true)
        SetBrightnessAction().execute(context(luminosite), mapOf("level" to "100"))
        assertEquals(listOf("settings put system screen_brightness 255"), luminosite.commands)
    }

    @Test
    fun `la commande shell brute est transmise telle quelle`() = runTest {
        val shizuku = FakeShizuku(ready = true, result = Result.success("ok\n"))

        val result = RunShellAction().execute(context(shizuku), mapOf("command" to "echo ok"))

        assertEquals(listOf("echo ok"), shizuku.commands)
        assertEquals("ok", (result as ActionResult.Success).message)
    }

    // --- Sans Shizuku : les replis -------------------------------------------

    @Test
    fun `sans Shizuku, le wifi ouvre son panneau de reglages`() = runTest {
        val shizuku = FakeShizuku(ready = false)

        val result = ToggleWifiAction().execute(context(shizuku), mapOf("state" to "off"))

        assertTrue(describe(result), result is ActionResult.Success)
        assertTrue(shizuku.commands.isEmpty())
        assertEquals(Settings.ACTION_WIFI_SETTINGS, lastActivity().action)
    }

    @Test
    fun `sans Shizuku, le bluetooth ouvre ses reglages`() = runTest {
        ToggleBluetoothAction().execute(context(FakeShizuku(ready = false)), mapOf("state" to "on"))

        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, lastActivity().action)
    }

    @Test
    fun `une commande Shizuku qui echoue declenche quand meme le repli`() = runTest {
        val shizuku = FakeShizuku(ready = true, result = Result.failure(IllegalStateException("refusé")))

        val result = ToggleWifiAction().execute(context(shizuku), mapOf("state" to "off"))

        assertEquals(listOf("svc wifi disable"), shizuku.commands)
        assertTrue(describe(result), result is ActionResult.Success)
        assertEquals(Settings.ACTION_WIFI_SETTINGS, lastActivity().action)
    }

    @Test
    fun `sans Shizuku, la commande shell echoue franchement`() = runTest {
        val result = RunShellAction().execute(
            context(FakeShizuku(ready = false)),
            mapOf("command" to "echo ok")
        )

        assertTrue(result is ActionResult.Failure)
        assertTrue((result as ActionResult.Failure).reason.contains("Shizuku"))
    }

    // --- Sans Shizuku du tout ------------------------------------------------

    @Test
    fun `le volume n a jamais besoin de Shizuku`() = runTest {
        val result = SetVolumeAction().execute(
            context(FakeShizuku(ready = false)),
            mapOf("stream" to "music", "level" to "50")
        )

        assertTrue(describe(result), result is ActionResult.Success)
    }

    @Test
    fun `un niveau de volume invalide est refuse`() = runTest {
        val result = SetVolumeAction().execute(
            context(FakeShizuku(ready = false)),
            mapOf("level" to "beaucoup")
        )

        assertTrue(result is ActionResult.Failure)
    }

    // --- Helpers -------------------------------------------------------------

    private class FakeShizuku(
        private val ready: Boolean,
        private val result: Result<String> = Result.success("")
    ) : ShizukuGateway {
        val commands = mutableListOf<String>()
        override fun isReady() = ready
        override suspend fun exec(command: String): Result<String> {
            commands += command
            return result
        }
    }

    private fun context(shizuku: ShizukuGateway) =
        ExecutionContext(application, emptyMap(), Speaker.SILENT, shizuku)

    private fun lastActivity(): Intent = shadowOf(application).nextStartedActivity

    private fun describe(result: ActionResult): String = when (result) {
        is ActionResult.Success -> "Success(${result.message})"
        is ActionResult.Failure -> "Failure(${result.reason})"
    }
}
