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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.runtime.collectAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pedro.cinelive.data.model.StreamType
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
    val isEpgBannerVisible by viewModel.isEpgBannerVisible.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val rootFocusRequester = remember { FocusRequester() }
    val drawerFocusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    var webViewError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentChannel) {
        webViewError = null
    }

    // Instância otimizada de ExoPlayer para HLS de alta estabilidade
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

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

    // Gerencia fonte do ExoPlayer
    LaunchedEffect(currentStream) {
        val stream = currentStream
        if (stream != null && stream.type == StreamType.DIRECT_M3U8) {
            val mediaItem = MediaItem.fromUri(stream.url)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Foca na raiz ou no drawer dependendo do estado e posiciona no canal atual
    LaunchedEffect(isDrawerOpen) {
        if (!isDrawerOpen) {
            rootFocusRequester.requestFocus()
        } else {
            drawerFocusRequester.requestFocus()
            val current = currentChannel
            if (current != null) {
                val idx = channels.indexOfFirst { it.id == current.id }
                if (idx >= 0) {
                    channelListState.scrollToItem(idx)
                }
            }
        }
    }

    // Ao trocar de categoria, reseta rolagem para o início
    LaunchedEffect(selectedCategory) {
        channelListState.scrollToItem(0)
    }

    // Interceptação de Botão Voltar (Back)
    BackHandler {
        when {
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
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (isDrawerOpen) {
                        // Quando o menu está aberto, o D-pad navega livremente entre categorias e canais
                        return@onKeyEvent false
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.previousChannel()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.nextChannel()
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
                                        try { if (window.jwplayer) { window.jwplayer().play(true); } } catch(e){}
                                        var vids = document.querySelectorAll('video');
                                        for (var i = 0; i < vids.length; i++) {
                                            vids[i].muted = false;
                                            vids[i].play();
                                        }
                                        var btns = document.querySelectorAll('.jw-display-icon-container, .jw-icon-playback, #player, video');
                                        for (var j = 0; j < btns.length; j++) {
                                            btns[j].click();
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
            }
            StreamType.WEB_EMBED -> {
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
                                isFocusable = true
                                isFocusableInTouchMode = true
                                try {
                                    WebView.setWebContentsDebuggingEnabled(true)
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
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        request?.grant(request.resources)
                                    }
                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        callback?.invoke(origin, true, false)
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
                                        val url = request?.url?.toString() ?: return false
                                        // Bloqueia esquemas externos e intents de lojas/anúncios
                                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                            return true
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
                                                          '#sb-message, #not-found-message, .ads, [class*="ad-"], [id*="ad-"] { display:none!important; }';
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

                                                    // Se houver botão de retry do player (caso de erro temporário), clica nele
                                                    var retry = document.getElementById('retryBtn');
                                                    if (retry && retry.offsetParent !== null) {
                                                        try { retry.click(); } catch(e) {}
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
                                                    var playIcon = document.querySelector('.jw-display-icon-container, .jw-icon-playback, .jw-display-icon-display, .jw-button-color');
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
                                    tag = stream.url
                                    val headers = mapOf("Referer" to "https://rdcanais.org/")
                                    loadUrl(stream.url, headers)
                                }
                            }
                        },
                        update = { webView ->
                            val stream = currentStream
                            if (stream != null && stream.type == StreamType.WEB_EMBED) {
                                val url = stream.url
                                if (webView.tag != url) {
                                    webView.tag = url
                                    val headers = mapOf("Referer" to "https://rdcanais.org/")
                                    webView.loadUrl(url, headers)
                                }
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
                // Estado de espera ou carregamento do stream
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NeoColors.Bg),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
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

                // Lista de Canais da Categoria
                LazyColumn(
                    state = channelListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "[ D-PAD: NAVEGAR // OK: ASSISTIR // VOLTAR: FECHAR ]",
                    style = NeoType.Badge,
                    color = NeoColors.TextMuted
                )
            }
        }
    }
}
