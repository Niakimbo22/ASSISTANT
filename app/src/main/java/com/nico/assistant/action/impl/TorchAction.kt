package com.nico.assistant.action.impl

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.nico.assistant.action.Action
import com.nico.assistant.action.ActionCategory
import com.nico.assistant.action.ActionResult
import com.nico.assistant.action.ActionType
import com.nico.assistant.action.Backend
import com.nico.assistant.action.ParamSpec
import com.nico.assistant.action.ParamType
import com.nico.assistant.core.executor.ExecutionContext

/**
 * Lampe torche.
 *
 * Contrairement à ce que laisse penser le tableau de la spec, elle n'a pas besoin de
 * Shizuku : `CameraManager.setTorchMode` suffit, et sans la permission CAMERA.
 */
class ToggleTorchAction : Action {

    override val type = ActionType.TOGGLE_TORCH
    override val backend = Backend.INTERNAL
    override val label = "Lampe torche"
    override val category = ActionCategory.SYSTEME

    override val paramsSchema = listOf(
        ParamSpec(
            key = PARAM_STATE,
            label = "État",
            type = ParamType.TOGGLE,
            default = STATE_ON
        )
    )

    override suspend fun execute(ctx: ExecutionContext, params: Map<String, String>): ActionResult {
        val manager = ctx.context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ActionResult.Failure("Pas d'accès à la caméra")

        TorchState.attach(manager)

        val cameraId = torchCameraId(manager)
            ?: return ActionResult.Failure("Pas de lampe sur cet appareil")

        val wanted = when (params[PARAM_STATE]?.trim()?.lowercase()) {
            STATE_OFF, "false", "0" -> false
            STATE_TOGGLE -> !TorchState.isOn
            else -> true
        }

        return runCatching {
            manager.setTorchMode(cameraId, wanted)
            TorchState.isOn = wanted
            ActionResult.Success(if (wanted) "Lampe allumée" else "Lampe éteinte")
        }.getOrElse { ActionResult.Failure(it.message ?: "Lampe indisponible") }
    }

    private fun torchCameraId(manager: CameraManager): String? = runCatching {
        manager.cameraIdList.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    companion object {
        const val PARAM_STATE = "state"
        const val STATE_ON = "on"
        const val STATE_OFF = "off"
        const val STATE_TOGGLE = "toggle"
    }
}

/**
 * État réel de la lampe, suivi par un callback système : sans lui, « bascule » se
 * tromperait dès que la torche est allumée depuis les réglages rapides.
 */
internal object TorchState {

    @Volatile
    var isOn: Boolean = false

    private var attached = false

    @Synchronized
    fun attach(manager: CameraManager) {
        if (attached) return
        attached = runCatching {
            manager.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        isOn = enabled
                    }
                },
                Handler(Looper.getMainLooper())
            )
            true
        }.getOrDefault(false)
    }
}
