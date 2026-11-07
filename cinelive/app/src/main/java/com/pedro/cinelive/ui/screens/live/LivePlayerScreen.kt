package com.pedro.cinelive.ui.screens.live

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import android.view.ViewGroup
import com.pedro.cinelive.util.AppLog
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import android.widget.Toast
import java.util.concurrent.ConcurrentHashMap
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pedro.cinelive.data.model.ResolvedStream
import com.pedro.cinelive.data.model.StreamType
import com.pedro.cinelive.ui.components.NeoCard
import com.pedro.cinelive.ui.components.NeoCatalogGridOverlay
import com.pedro.cinelive.ui.components.NeoCategoryChip
import com.pedro.cinelive.ui.components.NeoChannelCard
import com.pedro.cinelive.ui.components.NeoEpgBanner
import com.pedro.cinelive.ui.theme.NeoColors
import com.pedro.cinelive.ui.theme.NeoDimens
import com.pedro.cinelive.ui.theme.NeoType

private fun isHostAllowed(targetHost: String?, streamUrl: String?): Boolean {
    val host = targetHost?.lowercase() ?: return false
    val streamHost = try {
        if (!streamUrl.isNullOrBlank()) android.net.Uri.parse(streamUrl).host?.lowercase() else null
    } catch (e: Exception) { null }

    val baseDomains = listOf("rdcanais.org", "reidosembeds.lat", "rdembed.lat")
    val allowedDomains = if (streamHost != null) baseDomains + streamHost else baseDomains

    return allowedDomains.any { allowed ->
        host == allowed || host.endsWith(".$allowed")
    }
}

val ALLOWED_WEB_EMBED_ORIGINS: Set<String> = setOf(
    "https://penterosa.lat",
    "https://dadinhodetapioca.fun",
    "https://mastruzcomleite.site",
    "https://v1.rdembed.lat",
    "https://rdcanais.org",
    "https://pescaplay.store"
)

@OptIn(UnstableApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePlayerScreen(
    viewModel: LiveViewModel,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
    simulateNoFeatures: Boolean = false
) {
    val context = LocalContext.current

    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val currentChannel by viewModel.currentChannel.collectAsState()
    val currentStream by viewModel.currentStream.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val isCatalogGridOpen by viewModel.isCatalogGridOpen.collectAsState()
    val isEpgBannerVisible by viewModel.isEpgBannerVisible.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val rootFocusRequester = remember { FocusRequester() }
    val drawerFocusRequester = remember { FocusRequester() }
    val catalogFocusRequester = remember { FocusRequester() }
    val framePlayingMap = remember { ConcurrentHashMap<String, Boolean>() }
    val channelListState = rememberLazyListState()
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var webViewError by remember { mutableStateOf<String?>(null) }
    var exoPlayerError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentChannel) {
        webViewError = null
        exoPlayerError = null
    }

    // Fábrica de DataSource HTTP persistente para aplicar Referer/Origin por stream (DIRECT_M3U8)
    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
    }

    // Instância otimizada de ExoPlayer para HLS de alta estabilidade
    val exoPlayer = remember {
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        AppLog.e("CineLivePlayer", "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error, debugOnly = true)
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            seekToDefaultPosition()
                            prepare()
                        } else {
                            exoPlayerError = "Falha na reprodução do stream (${error.errorCodeName})"
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        AppLog.d("CineLivePlayer", "ExoPlayer state: $playbackState (READY=${Player.STATE_READY})")
                    }
                })
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Gerenciamento de ciclo de vida do Android (pausa em background / retoma em foreground)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    exoPlayer.playWhenReady = true
                    activeWebView?.onResume()
                    AppLog.d("CineLiveLifecycle", "ON_START recebido: retomando playback (ExoPlayer isPlaying=${exoPlayer.isPlaying}, playWhenReady=${exoPlayer.playWhenReady}, WebView.onResume())")
                }
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.playWhenReady = false
                    activeWebView?.onPause()
                    AppLog.d("CineLiveLifecycle", "ON_STOP recebido: pausando playback (ExoPlayer isPlaying=${exoPlayer.isPlaying}, playWhenReady=${exoPlayer.playWhenReady}, WebView.onPause())")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Gerencia fonte e headers do ExoPlayer
    LaunchedEffect(currentStream) {
        val stream = currentStream
        exoPlayerError = null
        if (stream != null) {
            val host = runCatching { java.net.URI(stream.url).host }.getOrNull() ?: "desconhecido"
            AppLog.d("CineLivePlayer", "Stream carregado: tipo=${stream.type}, host=$host, url=${stream.url}")
        }
        if (stream != null && stream.type == StreamType.DIRECT_M3U8) {
            if (stream.headers.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(stream.headers)
            } else {
                httpDataSourceFactory.setDefaultRequestProperties(emptyMap())
            }
            val mediaItem = MediaItem.fromUri(stream.url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            val isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            AppLog.d("CineLivePlayer", "DIRECT_M3U8 configurado: lifecycle.isAtLeast(STARTED)=$isStarted, currentState=${lifecycleOwner.lifecycle.currentState}, playWhenReady=${exoPlayer.playWhenReady}, isPlaying=${exoPlayer.isPlaying}")
            if (isStarted) {
                exoPlayer.play()
            }
            if (com.pedro.cinelive.BuildConfig.DEBUG) {
                launch {
                    delay(8000)
                    val window = Timeline.Window()
                    val windowStart = if (!exoPlayer.currentTimeline.isEmpty) {
                        exoPlayer.currentTimeline.getWindow(exoPlayer.currentMediaItemIndex, window).windowStartTimeMs
                    } else -1L
                    val pos = exoPlayer.currentPosition
                    val rendered = exoPlayer.videoDecoderCounters?.renderedOutputBufferCount ?: -1
                    val absPos = if (windowStart > 0) windowStart + pos else pos
                    AppLog.d("CineLiveMetrics", "[DIRECT_M3U8] t=8s canal=${currentChannel?.name}: posMs=$pos, windowStartMs=$windowStart, absolutePosMs=$absPos, renderedBuffers=$rendered, isPlaying=${exoPlayer.isPlaying}")

                    delay(4000)
                    val windowStart12 = if (!exoPlayer.currentTimeline.isEmpty) {
                        exoPlayer.currentTimeline.getWindow(exoPlayer.currentMediaItemIndex, window).windowStartTimeMs
                    } else -1L
                    val pos12 = exoPlayer.currentPosition
                    val rendered12 = exoPlayer.videoDecoderCounters?.renderedOutputBufferCount ?: -1
                    val absPos12 = if (windowStart12 > 0) windowStart12 + pos12 else pos12
                    AppLog.d("CineLiveMetrics", "[DIRECT_M3U8] t=12s canal=${currentChannel?.name}: posMs=$pos12, windowStartMs=$windowStart12, absolutePosMs=$absPos12, renderedBuffers=$rendered12, isPlaying=${exoPlayer.isPlaying}")
                }
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }

        if (stream != null && stream.type == StreamType.WEB_EMBED) {
            framePlayingMap.clear()
            val isDocStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && !simulateNoFeatures
            val isWebMessageSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && !simulateNoFeatures

            launch {
                delay(3500)
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val isAudioPlaying = audioManager?.isMusicActive == true
                val isAnyPermittedFramePlaying = if (isWebMessageSupported) framePlayingMap.values.any { it } else false
                val isPlaying = isAudioPlaying || isAnyPermittedFramePlaying

                // Desativado quando DOCUMENT_START_SCRIPT é suportado (o script do iframe já dá play);
                // mantido apenas no fallback sem doc-start e somente se o frame não reportou "playing" (nem áudio ativo).
                if (!isDocStartSupported && !isPlaying) {
                    activeWebView?.let { wv ->
                        val w = wv.width.toFloat().takeIf { it > 0 } ?: 1920f
                        val h = wv.height.toFloat().takeIf { it > 0 } ?: 1080f
                        val now = SystemClock.uptimeMillis()
                        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, w / 2f, h / 2f, 0)
                        val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, w / 2f, h / 2f, 0)
                        wv.dispatchTouchEvent(down)
                        wv.dispatchTouchEvent(up)
                        down.recycle()
                        up.recycle()
                        AppLog.d("CineLiveWeb", "Plano B (fallback sem doc-start): dispatchTouchEvent único por canal executado no centro ($w x $h)")
                    }
                } else {
                    AppLog.d("CineLiveWeb", "Plano B ignorado (docStartSupported=$isDocStartSupported, isPlaying=$isPlaying)")
                }
            }
        }
    }

    // Foca na raiz, no catálogo ou no drawer com retries e logs
    suspend fun safeRequestFocus(requester: FocusRequester, targetName: String) {
        for (attempt in 1..3) {
            delay(20L * attempt)
            val result = runCatching { requester.requestFocus() }
            if (result.isSuccess) {
                AppLog.d("CineLiveFocus", "Foco aplicado com sucesso em $targetName na tentativa $attempt")
                return
            } else {
                AppLog.w("CineLiveFocus", "Tentativa $attempt de focar em $targetName falhou com exceção: ${result.exceptionOrNull()?.message}", debugOnly = true)
            }
        }
        AppLog.e("CineLiveFocus", "FALHA DEFINITIVA: Todas as 3 tentativas de focar em $targetName falharam!", debugOnly = true)
    }

    LaunchedEffect(isDrawerOpen, isCatalogGridOpen) {
        when {
            isCatalogGridOpen -> {
                safeRequestFocus(catalogFocusRequester, "Catálogo")
            }
            isDrawerOpen -> {
                safeRequestFocus(drawerFocusRequester, "Drawer")
                val current = currentChannel
                if (current != null) {
                    val idx = channels.indexOfFirst { it.id == current.id }
                    if (idx >= 0) {
                        runCatching { channelListState.scrollToItem(idx) }
                    }
                }
            }
            else -> {
                safeRequestFocus(rootFocusRequester, "Root")
            }
        }
    }

    // Ao trocar de categoria, reseta rolagem para o início
    LaunchedEffect(selectedCategory) {
        channelListState.scrollToItem(0)
    }

    var okPressOwnedByRoot by remember { mutableStateOf(false) }
    var okLongPressHandled by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    // Interceptação centralizada de Botão Voltar (Back)
    BackHandler {
        when {
            isCatalogGridOpen -> viewModel.closeCatalogGrid()
            isDrawerOpen -> viewModel.closeDrawer()
            isEpgBannerVisible -> viewModel.hideEpgBanner()
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressTime < 2000L) {
                    AppLog.d("LivePlayerScreen", "[BACK] Calling onExitApp")
                    onExitApp()
                } else {
                    AppLog.d("LivePlayerScreen", "[BACK] First press, arming double-back timer")
                    lastBackPressTime = now
                    Toast.makeText(context, "Pressione VOLTAR novamente para sair", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoColors.Bg)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                var isShortCenterRelease = false
                val isCenterKey = keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER
                if (isCenterKey && keyEvent.type == KeyEventType.KeyDown) {
                    if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                        if (!isDrawerOpen && !isCatalogGridOpen) {
                            okPressOwnedByRoot = true
                            okLongPressHandled = false
                            return@onKeyEvent true
                        } else {
                            okPressOwnedByRoot = false
                            okLongPressHandled = false
                            return@onKeyEvent false
                        }
                    } else if (!okPressOwnedByRoot) {
                        return@onKeyEvent false
                    } else if (!okLongPressHandled && (keyEvent.nativeKeyEvent.repeatCount >= 1 || keyEvent.nativeKeyEvent.isLongPress)) {
                        okLongPressHandled = true
                        AppLog.d("CineLiveWeb", "[DPAD_CENTER] Long press -> openCatalogGrid()")
                        viewModel.openCatalogGrid()
                    }
                    return@onKeyEvent true
                }
                if (isCenterKey && keyEvent.type == KeyEventType.KeyUp) {
                    if (!okPressOwnedByRoot) return@onKeyEvent false
                    val shouldRunShort = !okLongPressHandled
                    okPressOwnedByRoot = false
                    okLongPressHandled = false
                    if (shouldRunShort) isShortCenterRelease = true else
                    return@onKeyEvent true
                }
                if (isDrawerOpen || isCatalogGridOpen) return@onKeyEvent false
                if ((keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) || isShortCenterRelease) {
                    if (isDrawerOpen || isCatalogGridOpen) {
                        // Quando o menu ou a grade estão abertos, o D-pad navega livremente entre os componentes
                        return@onKeyEvent false
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            viewModel.previousChannel()
                            true
                        }
                        KeyEvent.KEYCODE_PROG_RED -> {
                            viewModel.openCatalogGrid()
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.nextChannel()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            viewModel.nextChannel()
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.previousChannel()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> {
                            viewModel.openDrawer()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (currentStream?.type == StreamType.WEB_EMBED) {
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                val isAudioPlaying = audioManager?.isMusicActive == true
                                val isDocStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && !simulateNoFeatures
                                val isWebMessageSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && !simulateNoFeatures
                                val isAnyPermittedFramePlaying = if (isWebMessageSupported) framePlayingMap.values.any { it } else false
                                val chanceOfVideoPlaying = isAudioPlaying || isAnyPermittedFramePlaying || !isWebMessageSupported || !isDocStartSupported

                                AppLog.d("CineLiveWeb", "[DPAD_CENTER] isAudioPlaying=$isAudioPlaying, framePlaying=$isAnyPermittedFramePlaying, chanceOfPlaying=$chanceOfVideoPlaying, webMsgSupported=$isWebMessageSupported, docStartSupported=$isDocStartSupported")

                                if (!isDocStartSupported && !chanceOfVideoPlaying) {
                                    activeWebView?.let { wv ->
                                        val w = wv.width.toFloat().takeIf { it > 0 } ?: 1920f
                                        val h = wv.height.toFloat().takeIf { it > 0 } ?: 1080f
                                        val now = SystemClock.uptimeMillis()
                                        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, w / 2f, h / 2f, 0)
                                        val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, w / 2f, h / 2f, 0)
                                        wv.dispatchTouchEvent(down)
                                        wv.dispatchTouchEvent(up)
                                        down.recycle()
                                        up.recycle()
                                        AppLog.d("CineLiveWeb", "[DPAD_CENTER] Fallback legado (sem doc-start): dispatchTouchEvent acionado no centro ($w x $h)")
                                    }
                                } else {
                                    AppLog.d("CineLiveWeb", "[DPAD_CENTER] dispatchTouchEvent nao acionado (doc-start ativo ou chance de video estar tocando)")
                                }

                                activeWebView?.evaluateJavascript(
                                    """
                                    (function() {
                                        // Envia postMessage para os iframes filhos diretos (cada iframe valida evt.source === window.parent e repassa aos sub-iframes)
                                        try {
                                            for (var f = 0; f < window.frames.length; f++) {
                                                try { window.frames[f].postMessage('cine-play', '*'); } catch(e) {}
                                                try { window.frames[f].postMessage('cine-unmute', '*'); } catch(e) {}
                                            }
                                        } catch(e) {}

                                        var isPlaying = false;
                                        var vids = document.querySelectorAll('video');
                                        for (var i = 0; i < vids.length; i++) {
                                            if (!vids[i].paused && vids[i].currentTime > 0) {
                                                isPlaying = true;
                                                break;
                                            }
                                        }
                                        try {
                                            if (!isPlaying && window.jwplayer) {
                                                var p = (typeof window.jwplayer === 'function') ? (window.jwplayer('player') || window.jwplayer()) : null;
                                                if (p && typeof p.getState === 'function') {
                                                    var s = p.getState();
                                                    if (s === 'playing' || s === 'buffering') {
                                                        isPlaying = true;
                                                    }
                                                }
                                            }
                                        } catch(e) {}

                                        // Se já estiver reproduzindo, garante unmute antes do return, sem clicar em botões!
                                        if (isPlaying) {
                                            var mutedBefore = (vids.length > 0 ? vids[0].muted : false);
                                            console.log("[DPAD_CENTER] Estado ANTES de desmutar: muted=" + mutedBefore + ", paused=" + (vids.length > 0 ? vids[0].paused : false));
                                            for (var m = 0; m < vids.length; m++) {
                                                vids[m].muted = false;
                                            }
                                            try {
                                                if (window.jwplayer) {
                                                    var jwp = (typeof window.jwplayer === 'function') ? (window.jwplayer('player') || window.jwplayer()) : null;
                                                    if (jwp && typeof jwp.setMute === 'function') {
                                                        jwp.setMute(false);
                                                    }
                                                }
                                            } catch(e) {}
                                            console.log("[DPAD_CENTER] video ja tocando: playing=" + isPlaying + ", paused=" + (vids.length > 0 ? vids[0].paused : false) + ", muted=" + (vids.length > 0 ? vids[0].muted : false));
                                            return;
                                        }

                                        // Se não estiver reproduzindo, tenta dar play nativo
                                        try {
                                            if (window.jwplayer) {
                                                var jw = (typeof window.jwplayer === 'function') ? (window.jwplayer('player') || window.jwplayer()) : null;
                                                if (jw && typeof jw.play === 'function') {
                                                    jw.setMute(false);
                                                    jw.play(true);
                                                }
                                            }
                                        } catch(e) {}

                                        for (var k = 0; k < vids.length; k++) {
                                            vids[k].muted = false;
                                            if (vids[k].paused) {
                                                try { vids[k].play(); } catch(e) {}
                                            }
                                        }

                                        // Clica unicamente em botões de play overlay não ambíguos
                                        var playBtns = document.querySelectorAll('.jw-display-icon-container, .vjs-big-play-button, .plyr__control--overlaid');
                                        for (var j = 0; j < playBtns.length; j++) {
                                            try { playBtns[j].click(); } catch(e) {}
                                        }
                                    })();
                                    """.trimIndent(), null
                                )
                            }
                            viewModel.showEpgBanner()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> {
                            viewModel.showEpgBanner()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // ==========================================
        // 1. REPRODUTOR DE VÍDEO (EXOPLAYER OU WEBVIEW)
        // ==========================================
        when (currentStream?.type) {
            StreamType.DIRECT_M3U8 -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setBackgroundColor(AndroidColor.BLACK)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (exoPlayerError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NeoColors.Bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .background(NeoColors.Surface, RectangleShape)
                                    .border(3.dp, NeoColors.Yellow, RectangleShape)
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = "[ SINAL INSTÁVEL NO STREAM ]",
                                    style = NeoType.CardTitle,
                                    color = NeoColors.Yellow
                                )
                                Text(
                                    text = exoPlayerError ?: "Falha ao reproduzir o canal direto.",
                                    style = NeoType.Body,
                                    color = NeoColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
            StreamType.WEB_EMBED -> {
                val loadStreamInWebView = { wv: WebView, stream: ResolvedStream ->
                    if (wv.tag != stream.url) {
                        wv.tag = stream.url
                        val headers = if (stream.headers.isNotEmpty()) {
                            stream.headers
                        } else if (stream.url.contains("rdembed", ignoreCase = true)) {
                            mapOf(
                                "Referer" to "https://reidosembeds.lat/",
                                "Origin" to "https://reidosembeds.lat"
                            )
                        } else {
                            mapOf("Referer" to "https://rdcanais.org/")
                        }
                        wv.loadUrl(stream.url, headers)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                activeWebView = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(AndroidColor.BLACK)
                                isFocusable = false
                                isFocusableInTouchMode = false
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                val webViewPackage = WebViewCompat.getCurrentWebViewPackage(context)
                                val webViewVersion = webViewPackage?.versionName ?: "Desconhecida"
                                AppLog.d("CineLiveWeb", "Android System WebView version: $webViewVersion (pacote: ${webViewPackage?.packageName})")

                                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                    onPause()
                                    AppLog.d("CineLiveWeb", "WebView criada em background: onPause() executado")
                                }
                                try {
                                    if (com.pedro.cinelive.BuildConfig.DEBUG) {
                                        WebView.setWebContentsDebuggingEnabled(true)
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                } catch (e: Exception) {
                                    AppLog.w("CineLivePlayer", "Erro ao configurar cookies na WebView", e, debugOnly = true)
                                }
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    setSupportMultipleWindows(false)
                                    javaScriptCanOpenWindowsAutomatically = false
                                    // MIXED_CONTENT_ALWAYS_ALLOW: necessário para carregar chunks HLS e fontes em http dentro de embeds https
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                }

                                val isDocStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && !simulateNoFeatures
                                val isWebMessageSupported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) && !simulateNoFeatures

                                if (isWebMessageSupported) {
                                    try {
                                        WebViewCompat.addWebMessageListener(
                                            this,
                                            "CineBridge",
                                            ALLOWED_WEB_EMBED_ORIGINS,
                                            object : WebViewCompat.WebMessageListener {
                                                override fun onPostMessage(
                                                    view: WebView,
                                                    message: WebMessageCompat,
                                                    sourceOrigin: Uri,
                                                    isMainFrame: Boolean,
                                                    replyProxy: JavaScriptReplyProxy
                                                ) {
                                                    val originStr = sourceOrigin.toString()
                                                    if (ALLOWED_WEB_EMBED_ORIGINS.contains(originStr)) {
                                                        val data = message.data
                                                        if (data != null) {
                                                            val isPlaying = if (data.contains("\"playing\":true") || data == "true") {
                                                                true
                                                            } else if (data.contains("\"playing\":false") || data == "false") {
                                                                false
                                                            } else null

                                                            if (isPlaying != null) {
                                                                framePlayingMap[originStr] = isPlaying
                                                                AppLog.d("CineLiveWeb", "[WebMessageListener] frame=$originStr isPlaying=$isPlaying (activePermittedFrames=${framePlayingMap.filterValues { it }.keys})")
                                                            }
                                                        }
                                                    } else {
                                                        AppLog.w("CineLiveWeb", "WebMessage rejeitada de origem não permitida: $originStr", debugOnly = true)
                                                    }
                                                }
                                            }
                                        )
                                        AppLog.d("CineLiveWeb", "addWebMessageListener configurado para $ALLOWED_WEB_EMBED_ORIGINS")
                                    } catch (e: Exception) {
                                        AppLog.e("CineLiveWeb", "Erro ao registrar addWebMessageListener", e, debugOnly = true)
                                    }
                                } else {
                                    AppLog.w("CineLiveWeb", "WEB_MESSAGE_LISTENER não suportado ou desativado", debugOnly = true)
                                }

                                if (isDocStartSupported) {
                                    val iframeScript = """
                                        (function() {
                                            console.log('[CineFrameInjection] Injected into: ' + location.href);

                                            var hasAutoPlayed = false;

                                            function notifyBridge(isPlaying) {
                                                try {
                                                    if (window.CineBridge && typeof window.CineBridge.postMessage === 'function') {
                                                        window.CineBridge.postMessage(JSON.stringify({ action: 'setVideoPlaying', playing: isPlaying }));
                                                    }
                                                } catch(e) {}
                                            }

                                            function hookVideo(v) {
                                                if (!v || v.__cineHooked) return;
                                                v.__cineHooked = true;
                                                v.addEventListener('playing', function() {
                                                    if (!v.paused && v.currentTime > 0) {
                                                        notifyBridge(true);
                                                    }
                                                });
                                                v.addEventListener('pause', function() {
                                                    notifyBridge(false);
                                                });
                                                v.addEventListener('ended', function() {
                                                    notifyBridge(false);
                                                });
                                                v.addEventListener('timeupdate', function() {
                                                    notifyBridge(!v.paused && v.currentTime > 0);
                                                });
                                                if (!v.paused && v.currentTime > 0) {
                                                    notifyBridge(true);
                                                }
                                            }

                                            function safePlayAndUnmute(source) {
                                                var video = document.querySelector('video');
                                                if (!video) return false;
                                                hookVideo(video);

                                                try {
                                                    if (video.muted) {
                                                        video.muted = false;
                                                        console.log('[CineFrameInjection] Video desmutado: ' + location.href);
                                                    }
                                                } catch(e) {}

                                                if (!video.paused && video.currentTime > 0) {
                                                    notifyBridge(true);
                                                    return true;
                                                }

                                                if (video.paused) {
                                                    console.log('[CineFrameInjection] safePlayAndUnmute (' + source + '): ' + location.href);
                                                    var overlays = document.querySelectorAll('#center-play-btn, .center-play-btn, .bmpui-ui-hugeplaybacktogglebutton, .vjs-big-play-button, .plyr__control--overlaid, .jw-display-icon-container');
                                                    overlays.forEach(function(btn) {
                                                        try {
                                                            btn.click();
                                                            console.log('[CineFrameInjection] Overlay seguro acionado (' + (btn.id || btn.className || '') + '): ' + location.href);
                                                        } catch(e) {}
                                                    });
                                                    if (video.paused) {
                                                        try {
                                                            video.muted = false;
                                                            var p = video.play();
                                                            if (p && p.catch) {
                                                                p.catch(function(err) {
                                                                    console.log('[CineFrameInjection] play() desmutado falhou, tentando mutado: ' + err);
                                                                    if (err && err.name === 'NotAllowedError') {
                                                                        video.muted = true;
                                                                    }
                                                                    video.play().catch(function(){});
                                                                });
                                                            }
                                                        } catch(e) {}
                                                    }
                                                }
                                                return true;
                                            }

                                            window.addEventListener('message', function(evt) {
                                                if (evt.source !== window.parent) return;
                                                var data = evt.data;
                                                if (data === 'cine-play' || data === 'cine-unmute' || (data && (data.action === 'cine-play' || data.action === 'cine-unmute'))) {
                                                    console.log('[CineFrameInjection] postMessage recebido (' + JSON.stringify(data) + ') em ' + location.href);
                                                    safePlayAndUnmute('postMessage');
                                                    try {
                                                        for (var f = 0; f < window.frames.length; f++) {
                                                            try { window.frames[f].postMessage(data, '*'); } catch(e) {}
                                                        }
                                                    } catch(e) {}
                                                }
                                            });

                                            var timer = setInterval(function() {
                                                var video = document.querySelector('video');
                                                if (video) {
                                                    hookVideo(video);
                                                    if (!hasAutoPlayed) {
                                                        hasAutoPlayed = true;
                                                        safePlayAndUnmute('autoplay-start');
                                                    } else if (video.paused) {
                                                        safePlayAndUnmute('timer-retry');
                                                    }
                                                    if (!video.paused && video.currentTime > 0) {
                                                        notifyBridge(true);
                                                    }
                                                }
                                            }, 500);

                                            setTimeout(function() {
                                                clearInterval(timer);
                                            }, 20000);
                                        })();
                                    """.trimIndent()

                                    try {
                                        WebViewCompat.addDocumentStartJavaScript(this, iframeScript, ALLOWED_WEB_EMBED_ORIGINS)
                                        AppLog.d("CineLiveWeb", "addDocumentStartJavaScript configurado com sucesso para $ALLOWED_WEB_EMBED_ORIGINS")
                                    } catch (e: Exception) {
                                        AppLog.e("CineLiveWeb", "Erro ao registrar addDocumentStartJavaScript", e, debugOnly = true)
                                    }
                                } else {
                                    AppLog.w("CineLiveWeb", "DOCUMENT_START_SCRIPT não suportado no WebView atual", debugOnly = true)
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        // Nega permissões de hardware (câmera, microfone, sensores) para embeds externos
                                        request?.deny()
                                    }
                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        // Nega acesso a geolocalização por padrão
                                        callback?.invoke(origin, false, false)
                                    }
                                    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                                        callback?.onCustomViewHidden()
                                    }
                                    override fun onHideCustomView() {}
                                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                        AppLog.d("CineLiveWeb", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    private val antiSandboxPolyfill = """
                                        (function() {
                                            try {
                                                Object.defineProperty(document, 'domain', {
                                                    get: function() { return location.hostname; },
                                                    set: function(val) { return location.hostname; },
                                                    configurable: true
                                                });
                                            } catch (e) {}

                                            // Polyfill Fullscreen API para impedir que o JWPlayer aborte a reprodução com "TypeError: Fullscreen is not supported"
                                            var noop = function() { return Promise.resolve(); };
                                            try {
                                                if (typeof Element !== 'undefined') {
                                                    Element.prototype.requestFullscreen = noop;
                                                    Element.prototype.webkitRequestFullscreen = noop;
                                                    Element.prototype.webkitEnterFullscreen = noop;
                                                    Element.prototype.mozRequestFullScreen = noop;
                                                    Element.prototype.msRequestFullscreen = noop;
                                                }
                                                if (typeof HTMLVideoElement !== 'undefined') {
                                                    HTMLVideoElement.prototype.requestFullscreen = noop;
                                                    HTMLVideoElement.prototype.webkitRequestFullscreen = noop;
                                                    HTMLVideoElement.prototype.webkitEnterFullscreen = noop;
                                                }
                                                if (typeof Document !== 'undefined') {
                                                    document.exitFullscreen = noop;
                                                    document.webkitExitFullscreen = noop;
                                                    document.mozCancelFullScreen = noop;
                                                    document.msExitFullscreen = noop;
                                                }
                                            } catch(e) {}
                                        })();
                                    """.trimIndent()

                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString()?.lowercase() ?: return null
                                        if (reqUrl.contains("histats.com") || reqUrl.contains("llvpn.com") || reqUrl.contains("googlesyndication") || reqUrl.contains("adservice")) {
                                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val uri = request?.url ?: return false
                                        val scheme = uri.scheme?.lowercase()
                                        if (scheme != "http" && scheme != "https") {
                                            return true
                                        }
                                        if (request.isForMainFrame && !request.hasGesture()) {
                                            val targetHost = uri.host
                                            val currentUrl = currentStream?.url
                                            if (!isHostAllowed(targetHost, currentUrl)) {
                                                AppLog.w("CineLiveWeb", "Bloqueando redirecionamento de frame principal para host não autorizado: $uri", debugOnly = true)
                                                return true
                                            }
                                        }
                                        return false
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            AppLog.e("CineLiveWeb", "onReceivedError no frame principal: url=${request.url}, errorCode=${error?.errorCode}, desc=${error?.description}", debugOnly = true)
                                            webViewError = "Sinal indisponível temporariamente no servidor"
                                        }
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        view?.evaluateJavascript(antiSandboxPolyfill, null)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(antiSandboxPolyfill, null)
                                        // Injeta estilos de tela cheia e ciclo ágil de auto-play contínuo
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                if (window.__cineAuto) {
                                                    clearInterval(window.__cineAuto);
                                                    window.__cineAuto = null;
                                                }

                                                var css = 'body, html { margin:0!important; padding:0!important; overflow:hidden!important; background:#000!important; width:100vw!important; height:100vh!important; } ' +
                                                          '#player, iframe, video { width:100vw!important; height:100vh!important; position:fixed!important; top:0!important; left:0!important; z-index:999999!important; border:none!important; } ' +
                                                          '#sb-message, #not-found-message, .ads, .ad-banner, .ad-container, [id*="google_ads"], [id*="banner-ad"] { display:none!important; }';
                                                var head = document.head || document.getElementsByTagName('head')[0];
                                                if (head && !document.getElementById('cine-custom-style')) {
                                                    var style = document.createElement('style');
                                                    style.id = 'cine-custom-style';
                                                    style.type = 'text/css';
                                                    style.appendChild(document.createTextNode(css));
                                                    head.appendChild(style);
                                                }

                                                var playAttempts = 0;

                                                function playHtml5Video(videoEl) {
                                                    videoEl.setAttribute('playsinline', '');
                                                    if (videoEl.paused) {
                                                        var prom = videoEl.play();
                                                        if (prom !== undefined) {
                                                            prom.catch(function() {
                                                                videoEl.muted = true;
                                                                videoEl.play().then(function() {
                                                                    setTimeout(function() { videoEl.muted = false; }, 600);
                                                                }).catch(function(){});
                                                            });
                                                        }
                                                    }
                                                }

                                                function autoPlay() {
                                                    // 1. Verifica se já está reproduzindo para NÃO pausar nem re-clicar!
                                                    var isPlaying = false;
                                                    try {
                                                        if (window.jwplayer) {
                                                            var p = (typeof window.jwplayer === 'function') ? (window.jwplayer('player') || window.jwplayer()) : null;
                                                            if (p && typeof p.getState === 'function') {
                                                                var s = p.getState();
                                                                if (s === 'playing' || s === 'buffering') {
                                                                    isPlaying = true;
                                                                }
                                                            }
                                                        }
                                                    } catch(e) {}

                                                    var vids = document.querySelectorAll('video');
                                                    for (var i = 0; i < vids.length; i++) {
                                                        if (!vids[i].paused && vids[i].currentTime > 0) {
                                                            isPlaying = true;
                                                            break;
                                                        }
                                                    }

                                                    if (isPlaying) {
                                                        if (window.__cineAuto) {
                                                            clearInterval(window.__cineAuto);
                                                            window.__cineAuto = null;
                                                        }
                                                        return;
                                                    }

                                                    playAttempts++;

                                                    // 2. Não está rodando: tenta JWPlayer sem fullscreen
                                                    try {
                                                        if (window.jwplayer) {
                                                            var jw = (typeof window.jwplayer === 'function') ? (window.jwplayer('player') || window.jwplayer()) : null;
                                                            if (jw) {
                                                                try { jw.setFullscreen(false); } catch(e){}
                                                                if (typeof jw.play === 'function') {
                                                                    jw.setMute(false);
                                                                    jw.play(true);
                                                                }
                                                            }
                                                        }
                                                    } catch(e) {}

                                                    // 3. Tenta elementos de vídeo HTML5 padrão via função auxiliar (corrige closure)
                                                    for (var k = 0; k < vids.length; k++) {
                                                        playHtml5Video(vids[k]);
                                                    }

                                                    // 4. Clica SOMENTE se o vídeo continuar não tocando após o play() em ciclo prévio
                                                    if (playAttempts > 1) {
                                                        var playIcon = document.querySelector('.jw-display-icon-container, .vjs-big-play-button, .plyr__control--overlaid');
                                                        if (playIcon) {
                                                            try { playIcon.click(); } catch(e) {}
                                                        }
                                                    }
                                                }

                                                var count = 0;
                                                window.__cineAuto = setInterval(function() {
                                                    count++;
                                                    autoPlay();
                                                    if (count >= 50) {
                                                        if (window.__cineAuto) {
                                                            clearInterval(window.__cineAuto);
                                                            window.__cineAuto = null;
                                                        }
                                                    }
                                                }, 200);

                                                autoPlay();
                                            })();
                                            """.trimIndent(), null
                                        )
                                    }
                                }

                                val stream = currentStream
                                if (stream != null && stream.type == StreamType.WEB_EMBED) {
                                    loadStreamInWebView(this, stream)
                                }
                            }
                        },
                        update = { webView ->
                            val stream = currentStream
                            if (stream != null && stream.type == StreamType.WEB_EMBED) {
                                loadStreamInWebView(webView, stream)
                            }
                        },
                        onRelease = { wv ->
                            framePlayingMap.clear()
                            try {
                                wv.stopLoading()
                                wv.loadUrl("about:blank")
                                wv.clearHistory()
                                wv.removeAllViews()
                                wv.destroy()
                                AppLog.d("CineLiveWeb", "WebView onRelease: stopLoading, about:blank e destroy executados com sucesso")
                            } catch (e: Exception) {
                                AppLog.w("CineLivePlayer", "Erro ao destruir WebView no onRelease", e, debugOnly = true)
                            }
                            if (activeWebView === wv) {
                                activeWebView = null
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (webViewError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(NeoColors.Bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .background(NeoColors.Surface, RectangleShape)
                                    .border(3.dp, NeoColors.Yellow, RectangleShape)
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = "[ SINAL TEMPORARIAMENTE FORA DO AR ]",
                                    style = NeoType.CardTitle,
                                    color = NeoColors.Yellow
                                )
                                Text(
                                    text = "O servidor do canal está instável no momento. Use o D-pad para alternar de canal.",
                                    style = NeoType.Body,
                                    color = NeoColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
            null -> {
                // Estado de espera, carregamento ou erro do stream
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NeoColors.Bg),
                    contentAlignment = Alignment.Center
                ) {
                    if (errorMessage != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .background(NeoColors.Surface, RectangleShape)
                                .border(3.dp, NeoColors.Yellow, RectangleShape)
                                .padding(24.dp)
                        ) {
                            Text(
                                text = "[ FALHA NA SINTONIA ]",
                                style = NeoType.CardTitle,
                                color = NeoColors.Yellow
                            )
                            Text(
                                text = errorMessage ?: "Não foi possível carregar a transmissão.",
                                style = NeoType.Body,
                                color = NeoColors.TextPrimary
                            )
                        }
                    } else if (isLoading) {
                        Text(
                            text = "[ SINTONIZANDO SINAL // AGUARDE ]",
                            style = NeoType.CardTitle,
                            color = NeoColors.Yellow
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. INDICADOR DE STATUS / SINTONIA (CANTO SUPERIOR DIREITO)
        // ==========================================
        if (isLoading && currentChannel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = NeoDimens.SafeV, end = NeoDimens.SafeH)
                    .background(NeoColors.Surface, RectangleShape)
                    .border(2.dp, NeoColors.Yellow, RectangleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .zIndex(10f)
            ) {
                Text(
                    text = "[ SINTONIZANDO: ${currentChannel?.name?.uppercase()} ]",
                    style = NeoType.Badge,
                    color = NeoColors.Yellow
                )
            }
        }

        // ==========================================
        // 3. BANNER EPG OSD (SOBREPOSIÇÃO INFERIOR)
        // ==========================================
        AnimatedVisibility(
            visible = isEpgBannerVisible && currentChannel != null && !isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = NeoDimens.SafeH, vertical = NeoDimens.SafeV)
                .zIndex(2f)
        ) {
            currentChannel?.let { channel ->
                NeoEpgBanner(channel = channel)
            }
        }

        // ==========================================
        // 4. GUIA DE CANAIS (MENU LATERAL / DRAWER)
        // ==========================================
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(NeoDimens.DrawerW)
                .zIndex(3f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeoColors.Bg.copy(alpha = 0.95f), RectangleShape)
                    .border(
                        width = 3.dp,
                        color = NeoColors.Yellow,
                        shape = RectangleShape
                    )
                    .padding(top = NeoDimens.SafeV, bottom = NeoDimens.SafeV, start = 20.dp, end = 20.dp)
            ) {
                // Cabeçalho do Menu com contagem total de canais
                Text(
                    text = "CINELIVE // GUIA [ ${channels.size} CANAIS ]",
                    style = NeoType.Section,
                    color = NeoColors.Yellow
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Fileira de Categorias
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(categories, key = { _, cat -> cat.id }) { index, cat ->
                        NeoCategoryChip(
                            text = cat.name,
                            isSelected = cat.id == selectedCategory?.id,
                            onClick = { viewModel.selectCategory(cat) },
                            modifier = if (index == 0) Modifier.focusRequester(drawerFocusRequester) else Modifier
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botão de atalho para alternar para a grade estilo catálogo
                NeoCard(
                    onClick = { viewModel.openCatalogGrid() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "[ ABRIR CATÁLOGO EM GRADE ]",
                            style = NeoType.CardTitle.copy(fontSize = 12.sp),
                            color = NeoColors.Yellow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lista de Canais da Categoria
                LazyColumn(
                    state = channelListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(channels, key = { it.id }) { ch ->
                        NeoChannelCard(
                            channel = ch,
                            isCurrent = ch.id == currentChannel?.id,
                            onClick = { viewModel.selectChannel(ch) }
                        )
                    }
                }

                // Rodapé de instruções do menu
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = "[ D-PAD: NAVEGAR // OK: ASSISTIR // VOLTAR: FECHAR ]",
                        style = NeoType.Badge,
                        color = NeoColors.TextMuted
                    )
                }
            }
        }

        // ==========================================
        // 5. CATÁLOGO COMPLETO EM GRADE (ESTILO FILMES / TABELA)
        // ==========================================
        AnimatedVisibility(
            visible = isCatalogGridOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 }),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f)
        ) {
            NeoCatalogGridOverlay(
                channels = channels,
                categories = categories,
                selectedCategory = selectedCategory,
                currentChannel = currentChannel,
                onCategorySelect = { viewModel.selectCategory(it) },
                onChannelSelect = {
                    viewModel.selectChannel(it)
                    viewModel.closeCatalogGrid()
                },
                onClose = { viewModel.closeCatalogGrid() },
                firstFocusRequester = catalogFocusRequester,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
