package com.sentinelvault.vigilance.orientation

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the canonical device rotation for the headless CameraX session.
 *
 * The exposed [rotation] always emits one of `0`, `90`, `180`, `270`, debounced so the
 * service does not thrash the camera target rotation around the 45°/135°/225°/315° crossover
 * bands.
 */
@Singleton
class OrientationTracker internal constructor(
    private val backend: OrientationBackend,
    private val clock: Clock,
    private val debounceMs: Long = DEBOUNCE_MS
) {

    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    @Volatile private var started: Boolean = false
    private var pendingRotation: Int? = null
    private var pendingSinceMs: Long = 0L

    init {
        backend.setCallback(::onOrientationChanged)
    }

    @Synchronized
    fun start(): Boolean {
        if (started) return true
        val enabled = backend.startListening()
        started = enabled
        return enabled
    }

    @Synchronized
    fun stop() {
        if (!started) return
        backend.stopListening()
        started = false
        pendingRotation = null
        pendingSinceMs = 0L
    }

    @Synchronized
    internal fun onOrientationChanged(rawOrientation: Int) {
        if (!started) return
        val candidate = canonicalize(rawOrientation) ?: return
        if (candidate == _rotation.value) {
            pendingRotation = null
            pendingSinceMs = 0L
            return
        }
        if (pendingRotation != candidate) {
            pendingRotation = candidate
            pendingSinceMs = clock.nowMs()
            return
        }
        if (clock.nowMs() - pendingSinceMs >= debounceMs) {
            _rotation.value = candidate
            pendingRotation = null
            pendingSinceMs = 0L
        }
    }

    internal fun interface Clock {
        fun nowMs(): Long

        companion object {
            val SYSTEM: Clock = Clock { System.currentTimeMillis() }
        }
    }

    internal interface OrientationBackend {
        fun startListening(): Boolean
        fun stopListening()
        fun setCallback(callback: (Int) -> Unit)
    }

    private class AndroidOrientationBackend(context: Context) :
        OrientationEventListener(context), OrientationBackend {

        private var callback: ((Int) -> Unit)? = null

        override fun startListening(): Boolean {
            if (!canDetectOrientation()) return false
            super.enable()
            return true
        }

        override fun stopListening() {
            super.disable()
        }

        override fun setCallback(callback: (Int) -> Unit) {
            this.callback = callback
        }

        override fun onOrientationChanged(orientation: Int) {
            callback?.invoke(orientation)
        }
    }

    companion object {
        private const val DEBOUNCE_MS: Long = 200L

        fun create(context: Context): OrientationTracker = OrientationTracker(
            backend = AndroidOrientationBackend(context),
            clock = Clock.SYSTEM,
            debounceMs = DEBOUNCE_MS
        )

        internal fun canonicalize(rawOrientation: Int): Int? = when {
            rawOrientation == OrientationEventListener.ORIENTATION_UNKNOWN -> null
            rawOrientation >= 315 || rawOrientation < 45 -> 0
            rawOrientation < 135 -> 90
            rawOrientation < 225 -> 180
            else -> 270
        }
    }
}

internal fun Int.toSurfaceRotation(): Int = when (this) {
    90 -> Surface.ROTATION_90
    180 -> Surface.ROTATION_180
    270 -> Surface.ROTATION_270
    else -> Surface.ROTATION_0
}