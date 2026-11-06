package com.pedro.cinelive.ui.screens.live

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

    // Foca na raiz ou no drawer dependendo do estado
    LaunchedEffect(isDrawerOpen) {
        if (!isDrawerOpen) {
            rootFocusRequester.requestFocus()
        } else {
            drawerFocusRequester.requestFocus()
        }
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
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            viewModel.openDrawer()
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
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(AndroidColor.BLACK)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
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
                                    })();
                                """.trimIndent()

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    // Bloqueia esquemas externos e intents de lojas/anúncios
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    view?.evaluateJavascript(antiSandboxPolyfill, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(antiSandboxPolyfill, null)
                                    // Força o player e qualquer iframe a ocupar 100% da tela em tela cheia na TV
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var css = 'body, html { margin:0!important; padding:0!important; overflow:hidden!important; background:#000!important; } ' +
                                                      'iframe, video { width:100vw!important; height:100vh!important; position:fixed!important; top:0!important; left:0!important; z-index:999999!important; border:none!important; }';
                                            var head = document.head || document.getElementsByTagName('head')[0];
                                            var style = document.createElement('style');
                                            style.type = 'text/css';
                                            style.appendChild(document.createTextNode(css));
                                            head.appendChild(style);

                                            var vids = document.getElementsByTagName('video');
                                            for (var i = 0; i < vids.length; i++) {
                                                vids[i].muted = false;
                                                vids[i].play();
                                            }
                                        })();
                                        """.trimIndent(), null
                                    )
                                }
                            }
                        }
                    },
                    update = { webView ->
                        val url = currentStream?.url
                        if (url != null && webView.url != url) {
                            webView.loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                // Cabeçalho do Menu
                Text(
                    text = "CINELIVE // GUIA",
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
