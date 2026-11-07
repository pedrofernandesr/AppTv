package com.pedro.cinelive

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.pedro.cinelive.ui.screens.live.LivePlayerScreen
import com.pedro.cinelive.ui.screens.live.LiveViewModel
import com.pedro.cinelive.ui.theme.CineLiveTheme
import com.pedro.cinelive.util.AppLog

class MainActivity : ComponentActivity() {

    private val liveViewModel: LiveViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mantém a tela da TV sempre ligada durante a transmissão ao vivo
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Oculta barras de sistema para modo imersivo 100% em tela cheia na TV
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        intent?.getStringExtra("channel_id")?.let { id ->
            AppLog.d("MainActivity", "onCreate extra channel_id: $id")
            if (id.isNotBlank()) liveViewModel.tuneChannelById(id)
        }

        val simulateNoFeatures = if (BuildConfig.DEBUG) {
            intent?.getBooleanExtra("simulate_no_features", false) ?: false
        } else false

        setContent {
            CineLiveTheme {
                LivePlayerScreen(
                    viewModel = liveViewModel,
                    onExitApp = { finish() },
                    simulateNoFeatures = simulateNoFeatures
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra("channel_id")
        AppLog.d("MainActivity", "onNewIntent extra channel_id: $id")
        id?.let {
            if (it.isNotBlank()) liveViewModel.tuneChannelById(it)
        }
    }

    override fun onStop() {
        super.onStop()
        liveViewModel.currentChannel.value?.let { channel ->
            com.pedro.cinelive.data.local.ChannelPreferences.saveLastChannelId(channel.id)
        }
    }
}
