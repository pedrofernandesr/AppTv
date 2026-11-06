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
            if (id.isNotBlank()) liveViewModel.tuneChannelById(id)
        }

        setContent {
            CineLiveTheme {
                LivePlayerScreen(
                    viewModel = liveViewModel,
                    onExitApp = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("channel_id")?.let { id ->
            if (id.isNotBlank()) liveViewModel.tuneChannelById(id)
        }
    }
}
