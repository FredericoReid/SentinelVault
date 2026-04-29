package com.sentinelvault.lockdown

import android.content.Context
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SoftLockOverlayController]. Attaches a [SoftLockOverlay] to the system window
 * via `WindowManager` using `TYPE_APPLICATION_OVERLAY` (post-O) or `TYPE_SYSTEM_ERROR`
 * (legacy fallback, never reached because `minSdk = 26`).
 *
 * Resilience guarantees:
 *  * All public methods marshal onto the main looper if invoked off it (the Epic 5 state
 *    flow runs on `Dispatchers.Default`).
 *  * `addView` / `removeView` are wrapped in try/catch — if the user revoked the
 *    `SYSTEM_ALERT_WINDOW` permission between `arm()` and `show()` we degrade silently and
 *    leave [mode] in [SoftLockOverlayController.Mode.OFF] so the dashboard can flag it.
 *  * `arm()` and `show()` are idempotent: re-invoking with the same target [Mode] is a
 *    no-op, so the state-machine can re-emit `BreachConfirmed` without flicker.
 */
@Singleton
class WindowManagerOverlayController internal constructor(
    private val context: Context,
    private val windowManager: WindowManager,
    private val hostFactory: OverlayHostFactory,
    private val layoutParamsFactory: LayoutParamsFactory,
    private val mainExecutor: (Runnable) -> Unit
) : SoftLockOverlayController {

    @Inject constructor(@ApplicationContext context: Context) : this(
        context = context,
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
        hostFactory = OverlayHostFactory.DEFAULT,
        layoutParamsFactory = LayoutParamsFactory.DEFAULT,
        mainExecutor = ::executeOnMain
    )

    @Volatile private var _mode: SoftLockOverlayController.Mode = SoftLockOverlayController.Mode.OFF
    override val mode: SoftLockOverlayController.Mode get() = _mode

    private var host: OverlayHost? = null

    override fun arm() = onMain {
        if (_mode != SoftLockOverlayController.Mode.OFF || !canDrawOverlays()) return@onMain
        if (!attachWindow(armed = true)) return@onMain
        _mode = SoftLockOverlayController.Mode.ARMED
    }

    override fun show() = onMain {
        if (_mode == SoftLockOverlayController.Mode.SHOWING) return@onMain
        if (!canDrawOverlays()) return@onMain
        when (_mode) {
            SoftLockOverlayController.Mode.OFF -> if (!attachWindow(armed = false)) return@onMain
            SoftLockOverlayController.Mode.ARMED -> if (!updateLayout(armed = false)) return@onMain
            SoftLockOverlayController.Mode.SHOWING -> Unit
        }
        _mode = SoftLockOverlayController.Mode.SHOWING
    }

    override fun dismiss() = onMain {
        if (_mode == SoftLockOverlayController.Mode.OFF) return@onMain
        detachWindow()
        _mode = SoftLockOverlayController.Mode.OFF
    }

    private fun attachWindow(armed: Boolean): Boolean {
        val view = (host ?: hostFactory.create(context) { SoftLockOverlay() }).also { host = it }
        return try {
            windowManager.addView(view.view, layoutParams(armed))
            view.onAttached()
            true
        } catch (_: Throwable) {
            host = null
            false
        }
    }

    private fun updateLayout(armed: Boolean): Boolean {
        val v: View = host?.view ?: return false
        return try {
            windowManager.updateViewLayout(v, layoutParams(armed))
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun detachWindow() {
        val h = host ?: return
        try {
            windowManager.removeView(h.view)
        } catch (_: Throwable) {
            // already removed by the system; safe to swallow.
        } finally {
            h.onDetached()
            host = null
        }
    }

    private fun layoutParams(armed: Boolean): WindowManager.LayoutParams =
        layoutParamsFactory.create(armed)

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun onMain(block: () -> Unit) {
        mainExecutor(Runnable(block))
    }

    private companion object {
        fun executeOnMain(r: Runnable) {
            if (Looper.myLooper() == Looper.getMainLooper()) r.run()
            else android.os.Handler(Looper.getMainLooper()).post(r)
        }
    }
}
