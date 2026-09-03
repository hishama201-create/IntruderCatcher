package com.sabri.intrudercatcher

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * CameraX يتطلب LifecycleOwner. بما أن CaptureService خدمة بلا واجهة،
 * ننشئ دورة حياة بسيطة يدويًا فقط لمدة التقاط الصورة.
 */
class ServiceLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun start() {
        registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.STARTED
        registry.currentState = Lifecycle.State.RESUMED
    }
}
