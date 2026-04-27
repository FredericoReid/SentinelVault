package com.sentinelvault

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Boots the app under [android.app.Application] (no Hilt needed for raw DB tests). */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?): Application {
        return super.newApplication(cl, Application::class.java.name, ctx)
    }
}
