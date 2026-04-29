package com.sentinelvault.lockdown

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager

/**
 * Factory for the `WindowManager.LayoutParams` that back the soft-lock overlay. Split out so
 * the unit tests can substitute a no-op factory \u2014 instantiating `WindowManager.LayoutParams`
 * (and especially mutating its `gravity` / `alpha` fields) crashes against the JVM android
 * stub jar that backs `:app:testDebugUnitTest`.
 */
fun interface LayoutParamsFactory {
    fun create(armed: Boolean): WindowManager.LayoutParams

    companion object {
        val DEFAULT: LayoutParamsFactory = LayoutParamsFactory { armed ->
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (armed) {
                // Pre-arm path: do not steal touch/focus, keep the view alive but transparent.
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                flags,
                PixelFormat.OPAQUE
            ).also {
                it.gravity = Gravity.START or Gravity.TOP
                it.alpha = if (armed) 0.0f else 1.0f
            }
        }
    }
}
