package com.sentinelvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sentinelvault.service.VigilanceServiceLauncher
import com.sentinelvault.ui.navigation.SentinelNavHost
import com.sentinelvault.ui.theme.SentinelTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var serviceLauncher: VigilanceServiceLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SentinelTheme {
                // Surface paints the DeepBlack background edge-to-edge (incl. behind the
                // transparent system bars on Android 15+); the NavHost gets its own
                // systemBarsPadding so interactive content stays clear of the cut-outs.
                Surface(modifier = Modifier.fillMaxSize()) {
                    SentinelNavHost(modifier = Modifier.systemBarsPadding())
                }
            }
        }
    }

    /**
     * Re-evaluates the FGS prerequisites every time the activity returns to the foreground.
     * BUG-9.4: the launcher cannot start the camera-typed service before the user grants
     * `CAMERA` in onboarding, so the very first `Application.onCreate` call returns
     * [VigilanceServiceLauncher.Result.MissingPermission]; this hook re-tries on the way
     * back from the system permission sheet without forcing the user to relaunch the app.
     */
    override fun onResume() {
        super.onResume()
        serviceLauncher.ensureRunning(this)
    }
}
