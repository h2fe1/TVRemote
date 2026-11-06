package com.tvremote.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import com.tvremote.app.theme.TVRemoteTheme
import com.tvremote.app.ui.DeviceListScreen
import com.tvremote.app.ui.PairingScreen
import com.tvremote.app.ui.RemoteScreen

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(Color.BLACK),
        )
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // The lock-screen media session posts an ongoing notification.
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false)) {
            viewModel.enterDemo()
        } else if (BuildConfig.DEBUG && intent.getBooleanExtra("demoDevices", false)) {
            viewModel.showDemoDevices()
        }
        setContent {
            TVRemoteTheme {
                val screen by viewModel.screen.collectAsState()
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        fadeIn(tween(260)) togetherWith fadeOut(tween(170))
                    },
                    label = "screen",
                ) { current ->
                    when (current) {
                        is Screen.Devices -> DeviceListScreen(viewModel)
                        is Screen.Pairing -> PairingScreen(viewModel, current.device)
                        is Screen.Remote -> RemoteScreen(viewModel, current.device)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    /**
     * Volume keys are captured only while the remote screen has a live session
     * and the preference is enabled. Consuming both DOWN and UP keeps the
     * system volume panel (and the phone's own volume) out of the way.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.handleHardwareVolumeKey(event.keyCode, event.action)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                runCatching {
                    window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
