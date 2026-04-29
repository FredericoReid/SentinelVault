package com.sentinelvault

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SentinelApp : Application() {

    @Inject lateinit var runtime: SentinelRuntime

    override fun onCreate() {
        super.onCreate()
        runtime.start()
    }
}
