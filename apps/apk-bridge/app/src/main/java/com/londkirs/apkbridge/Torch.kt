package com.londkirs.apkbridge

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * Flashlight. There is no shell command for it, so the app switches it itself (CameraManager,
 * no camera permission needed). Used by the terminal ("boss torch") and the widget ("@torch").
 */
object Torch {
    /** Turns the flashlight on / off ([want]), or flips it when [want] is null. Reports the new state (null = failed). */
    fun set(context: Context, want: Boolean? = null, onDone: (Boolean?) -> Unit) {
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) ?: return onDone(null)
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return onDone(null)
        val main = Handler(Looper.getMainLooper())
        var done = false
        fun finish(target: Boolean) {
            val ok = runCatching { cm.setTorchMode(id, target) }.isSuccess
            context.getSharedPreferences("terminal", Context.MODE_PRIVATE).edit().putBoolean("toggle_torch", target && ok).apply()
            onDone(if (ok) target else null)
        }
        // Registering the callback reports the current state right away; then we flip it.
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId != id || done) return
                done = true
                cm.unregisterTorchCallback(this)
                finish(want ?: !enabled)
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId != id || done) return
                done = true
                cm.unregisterTorchCallback(this)
                onDone(null)  // the camera is in use by another app
            }
        }
        cm.registerTorchCallback(callback, main)
        main.postDelayed({
            if (!done) {
                done = true
                cm.unregisterTorchCallback(callback)
                finish(want ?: !context.getSharedPreferences("terminal", Context.MODE_PRIVATE).getBoolean("toggle_torch", false))
            }
        }, 800)
    }
}
