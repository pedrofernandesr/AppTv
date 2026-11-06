package com.pedro.cinelive.ui.screens.live

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

@OptIn(UnstableApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePlayerScreen(
    viewModel: LiveViewModel,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
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
                        Log.e("CineLivePlayer", "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            seekToDefaultPosition()
                            prepare()
                        } else {
                            exoPlayerError = "Falha na reprodução do stream (${error.errorCodeName})"
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d("CineLivePlayer", "ExoPlayer state: $playbackState (READY=${Player.STATE_READY})")
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
                }
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.playWhenReady = false
                    activeWebView?.onPause()
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
        if (stream != null && stream.type == StreamType.DIRECT_M3U8) {
            if (stream.headers.isNotEmpty()) {
                httpDataSourceFactory.setDefaultRequestProperties(stream.headers)
            } else {
                httpDataSourceFactory.setDefaultRequestProperties(emptyMap())
            }
            val mediaItem = MediaItem.fromUri(stream.url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Foca na raiz, no catálogo ou no drawer dependendo do estado e posiciona no canal atual
    LaunchedEffect(isDrawerOpen, isCatalogGridOpen) {
        when {
            isCatalogGridOpen -> {
                delay(16)
                runCatching { catalogFocusRequester.requestFocus() }
            }
            isDrawerOpen -> {
                delay(16)
                runCatching { drawerFocusRequester.requestFocus() }
                val current = currentChannel
                if (current != null) {
                    val idx = channels.indexOfFirst { it.id == current.id }
                    if (idx >= 0) {
                        runCatching { channelListState.scrollToItem(idx) }
                    }
                }
            }
            else -> {
                delay(16)
                runCatching { rootFocusRequester.requestFocus() }
            }
        }
    }

    // Ao trocar de categoria, reseta rolagem para o início
    LaunchedEffect(selectedCategory) {
        channelListState.scrollToItem(0)
    }

    // Interceptação centralizada de Botão Voltar (Back)
    BackHandler {
        when {
            isCatalogGridOpen -> viewModel.closeCatalogGrid()
            isDrawerOpen -> viewModel.closeDrawer()
            isEpgBannerVisible -> viewModel.hideEpgBanner()
            else -> onExitApp()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoColors.Bg)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                    if (isDrawerOpen || isCatalogGridOpen) {
                        // Quando o menu ou a grade estão abertos, o D-pad navega livremente entre os componentes
                        return@onKeyEvent false
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PROG_RED -> {
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
                                activeWebView?.evaluateJavascript(
                                    """
                                    (function() {
                                        try {
                                            if (window.jwplayer) {
                                                var p = window.jwplayer();
                                                if (p && typeof p.play === 'function') {
                                                    p.setMute(false);
                                                    p.play(true);
                                                }
                                            }
                                        } catch(e) {}
                                        var vids = document.querySelectorAll('video');
                                        for (var i = 0; i < vids.length; i++) {
                                            vids[i].muted = false;
                                            if (vids[i].paused) {
                                                vids[i].play();
                                            }
                                        }
                                        var playBtns = document.querySelectorAll('.jw-display-icon-container, .jw-icon-playback, .vjs-big-play-button, .plyr__control--overlaid, button[class*="play"], div[class*="play-btn"]');
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
                                setOnKeyListener { _, keyCode, event ->
                                    if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                                        when (keyCode) {
                                            android.view.KeyEvent.KEYCODE_BACK -> {
                                                if (isCatalogGridOpen) {
                                                    viewModel.closeCatalogGrid()
                                                    true
                                                } else if (isDrawerOpen) {
                                                    viewModel.closeDrawer()
                                                    true
                                                } else {
                                                    viewModel.openDrawer()
                                                    true
                                                }
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_MENU -> {
                                                viewModel.openDrawer()
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_PROG_RED -> {
                                                viewModel.openCatalogGrid()
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                viewModel.nextChannel()
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                                                viewModel.nextChannel()
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                                viewModel.previousChannel()
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                                                viewModel.showEpgBanner()
                                                true
                                            }
                                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_INFO -> {
                                                viewModel.showEpgBanner()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                try {
                                    if (com.pedro.cinelive.BuildConfig.DEBUG) {
                                        WebView.setWebContentsDebuggingEnabled(true)
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                } catch (e: Exception) {
                                    Log.w("CineLivePlayer", "Erro ao configurar cookies na WebView", e)
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
                                        Log.d("CineLiveWeb", "[${consoleMessage?.messageLevel()}] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
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
                                            val targetHost = uri.host?.lowercase() ?: ""
                                            val currentUrl = currentStream?.url ?: ""
                                            val initialHost = android.net.Uri.parse(currentUrl).host?.lowercase() ?: ""
                                            if (targetHost.isNotEmpty() && initialHost.isNotEmpty() && !targetHost.contains(initialHost) && !initialHost.contains(targetHost)) {
                                                Log.w("CineLiveWeb", "Bloqueando redirecionamento de frame principal sem gesto: $uri")
                                                return true
                                            }
                                        }
                                        return false
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
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
                                                var css = 'body, html { margin:0!important; padding:0!important; overflow:hidden!important; background:#000!important; width:100vw!important; height:100vh!important; } ' +
                                                          '#player, iframe, video { width:100vw!important; height:100vh!important; position:fixed!important; top:0!important; left:0!important; z-index:999999!important; border:none!important; } ' +
                                                          '#sb-message, #not-found-message, .ads, .ad-banner, .ad-container, [id*="google_ads"], [id*="banner-ad"] { display:none!important; }';
                                                var head = document.head || document.getElementsByTagName('head')[0];
                                                if (head) {
                                                    var style = document.createElement('style');
                                                    style.type = 'text/css';
                                                    style.appendChild(document.createTextNode(css));
                                                    head.appendChild(style);
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
                                                        clearInterval(timer);
                                                        return;
                                                    }

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

                                                    // 3. Tenta elementos de vídeo HTML5 padrão
                                                    for (var k = 0; k < vids.length; k++) {
                                                        var v = vids[k];
                                                        v.setAttribute('playsinline', '');
                                                        if (v.paused) {
                                                            var prom = v.play();
                                                            if (prom !== undefined) {
                                                                prom.catch(function() {
                                                                    v.muted = true;
                                                                    v.play().then(function() {
                                                                        setTimeout(function() { v.muted = false; }, 600);
                                                                    });
                                                                });
                                                            }
                                                        }
                                                    }

                                                    // 4. Clica SOMENTE no ícone de play se não estiver rodando (NUNCA clica no player ou vídeo!)
                                                    var playIcon = document.querySelector('.jw-display-icon-container, .jw-icon-playback, .jw-display-icon-display, .jw-button-color, .vjs-big-play-button, .plyr__control--overlaid, button[class*="play"], div[class*="play-btn"]');
                                                    if (playIcon) {
                                                        try { playIcon.click(); } catch(e) {}
                                                    }
                                                }

                                                var count = 0;
                                                var timer = setInterval(function() {
                                                    count++;
                                                    autoPlay();
                                                    if (count >= 50) { clearInterval(timer); }
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
                            try {
                                wv.stopLoading()
                                wv.loadUrl("about:blank")
                                wv.clearHistory()
                                wv.removeAllViews()
                                wv.destroy()
                            } catch (e: Exception) {
                                Log.w("CineLivePlayer", "Erro ao destruir WebView no onRelease", e)
                            }
                            if (activeWebView === wv) {
                                activeWebView = null
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(currentStream) {
                        onDispose {
                            try {
                                activeWebView?.stopLoading()
                                activeWebView?.loadUrl("about:blank")
                            } catch (e: Exception) {}
                        }
                    }

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
