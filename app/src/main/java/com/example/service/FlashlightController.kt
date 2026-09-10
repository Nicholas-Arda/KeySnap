package com.example.service

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FlashlightController {
    private const val TAG = "FlashlightController"
    
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _lastActionMessage = MutableStateFlow<String?>(null)
    val lastActionMessage: StateFlow<String?> = _lastActionMessage.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var isInitialized = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            super.onTorchModeChanged(id, enabled)
            if (id == cameraId || cameraId == null) {
                _isFlashlightOn.value = enabled
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            super.onTorchModeUnavailable(id)
            if (id == cameraId || cameraId == null) {
                _isAvailable.value = false
            }
        }
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager = cm
            if (cm != null) {
                val cameraIds = cm.cameraIdList
                for (id in cameraIds) {
                    val characteristics = cm.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id
                        break
                    }
                }
                if (cameraId == null && cameraIds.isNotEmpty()) {
                    for (id in cameraIds) {
                        val hasFlash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                        if (hasFlash) {
                            cameraId = id
                            break
                        }
                    }
                }

                cm.registerTorchCallback(torchCallback, null)
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FlashlightController", e)
            _isAvailable.value = false
        }
    }

    /** Test-only hook to simulate a torch state change without a real camera. */
    fun setTestState(on: Boolean) {
        _isFlashlightOn.value = on
    }

    fun toggle(context: Context): Boolean {
        initialize(context)
        val newState = !_isFlashlightOn.value
        return setTorchMode(context, newState)
    }

    fun setTorchMode(context: Context, enabled: Boolean): Boolean {
        initialize(context)
        val cm = cameraManager ?: run {
            _lastActionMessage.value = "Camera service unavailable"
            return false
        }
        val targetCameraId = cameraId ?: run {
            _lastActionMessage.value = "No flash hardware found"
            return false
        }

        return try {
            cm.setTorchMode(targetCameraId, enabled)
            _isFlashlightOn.value = enabled
            _lastActionMessage.value = if (enabled) "Torch on" else "Torch off"
            true
        } catch (e: CameraAccessException) {
            Log.e(TAG, "CameraAccessException while setting torch mode", e)
            _lastActionMessage.value = "Could not access the flash: ${e.localizedMessage}"
            false
        } catch (e: Exception) {
            Log.e(TAG, "Exception while setting torch mode", e)
            _lastActionMessage.value = "Could not switch the torch: ${e.localizedMessage}"
            false
        }
    }
}
