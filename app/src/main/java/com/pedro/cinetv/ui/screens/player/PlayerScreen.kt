package com.pedro.cinetv.ui.screens.player

import android.annotation.SuppressLint
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pedro.cinetv.data.api.MegaEmbedRepository
import com.pedro.cinetv.data.local.PlaybackHistoryManager
import com.pedro.cinetv.data.model.StreamSource
import com.pedro.cinetv.data.model.TmdbItem
import com.pedro.cinetv.ui.components.NeoBadge
import com.pedro.cinetv.ui.components.NeoButton
import com.pedro.cinetv.ui.components.NeoButtonVariant
import com.pedro.cinetv.ui.components.NeoIconButton
import com.pedro.cinetv.ui.components.NeoPillButton
import com.pedro.cinetv.ui.components.NeoSeekbar
import com.pedro.cinetv.ui.theme.NeoColors
import com.pedro.cinetv.ui.theme.NeoDimens
import com.pedro.cinetv.ui.theme.NeoType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    item: TmdbItem,
    onBackClicked: () -> Unit,
    season: Int = 1,
    episode: Int = 1,
    episodeTitle: String? = null,
    onPlayNextEpisode: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { MegaEmbedRepository() }
    val gson = remember { Gson() }

    var sources by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var currentSourceIndex by remember { mutableStateOf(0) }
    var isLoadingSources by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var seekNotice by remember { mutableStateOf<String?>(null) }
    var resumeNotice by remember { mutableStateOf<String?>(null) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }

    // Requesters de foco TV
    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    // Cria e configura o ExoPlayer
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://mgeb.lat/"))

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        if (!playing) {
                            showControls = true
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = (playbackState == Player.STATE_BUFFERING && playWhenReady)
                        if (playbackState == Player.STATE_READY) {
                            durationMs = duration.coerceAtLeast(0L)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        Log.e("CineTV_Player", "Erro ExoPlayer: ${error.errorCodeName} - ${error.message}")
                        if (sources.size > 1 && currentSourceIndex < sources.size - 1) {
                            currentSourceIndex++
                            playSource(this@apply, sources[currentSourceIndex], currentPositionMs)
                        } else {
                            errorMessage = "Erro no stream: ${error.message ?: "Fonte inacessível"}"
                        }
                    }
                })
            }
    }

    // Extrator Headless WebView (Garante extração mesmo sob Cloudflare)
    val headlessWebView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (url.contains("disable-devtool")) {
                        return WebResourceResponse(
                            "application/javascript",
                            "UTF-8",
                            ByteArrayInputStream("window.DisableDevtool = function(){};".toByteArray())
                        )
                    }
                    if (url.contains("acscdn.com") || url.contains("histats.com")) {
                        return WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream("".toByteArray()))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript("window.DisableDevtool = function(){};", null)
                }
            }
        }
    }

    // Carrega streams e restaura posição salva do filme ou episódio
    LaunchedEffect(item.id, season, episode) {
        isLoadingSources = true
        errorMessage = null
        val isTv = item.mediaType == "tv"

        // Verifica posição salva no histórico local
        val savedPos = PlaybackHistoryManager.getPlaybackPosition(context, item.id)
        if (savedPos > 3000L) {
            resumeNotice = "RETOMANDO DE ${formatTime(savedPos)}"
        }

        // 1. Tenta extração direta rápida via OkHttp (com suporte a temporada/episódio)
        val directSources = repository.getStreamSources(item.id, isTv, season, episode)
        if (directSources.isNotEmpty()) {
            sources = directSources
            currentSourceIndex = 0
            isLoadingSources = false
            playSource(exoPlayer, directSources[0], savedPos)
            return@LaunchedEffect
        }

        // 2. Fallback: Extração via Headless WebView se OkHttp falhar
        Log.d("CineTV_Player", "Iniciando extração via Headless WebView...")
        val targetUrl = if (isTv) "https://mgeb.lat/embed/${item.id}/$season/$episode" else "https://mgeb.lat/embed/${item.id}"
        headlessWebView.loadUrl(targetUrl)

        var attempts = 0
        while (attempts < 20 && isActive && sources.isEmpty()) {
            delay(500)
            attempts++
            headlessWebView.evaluateJavascript(
                "(function() { return (typeof sources !== 'undefined' && sources && sources.length > 0) ? JSON.stringify(sources) : ''; })()"
            ) { result ->
                val cleaned = result?.trim('\"')?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: ""
                if (cleaned.isNotBlank() && cleaned.startsWith("[") && cleaned.endsWith("]")) {
                    try {
                        val listType = object : TypeToken<List<StreamSource>>() {}.type
                        val parsed: List<StreamSource> = gson.fromJson(cleaned, listType)
                        val valid = parsed.filter { it.file.isNotBlank() }
                        if (valid.isNotEmpty()) {
                            Log.d("CineTV_Player", "Headless WebView extraiu ${valid.size} fontes com sucesso!")
                            sources = valid
                            currentSourceIndex = 0
                            isLoadingSources = false
                            playSource(exoPlayer, valid[0], savedPos)
                        }
                    } catch (e: Exception) {
                        Log.e("CineTV_Player", "Erro ao deserializar fontes do WebView: ${e.message}")
                    }
                }
            }
        }

        if (sources.isEmpty()) {
            isLoadingSources = false
            errorMessage = "Nenhum stream disponível no momento para este título."
        }
    }

    // Loop de salvamento de progresso a cada segundo no Storage Local
    LaunchedEffect(exoPlayer, item.id, season, episode) {
        while (isActive) {
            if (exoPlayer.isPlaying) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                PlaybackHistoryManager.savePlaybackPosition(
                    context = context,
                    itemId = item.id,
                    positionMs = currentPositionMs,
                    durationMs = durationMs,
                    item = item,
                    season = season,
                    episode = episode,
                    episodeTitle = episodeTitle
                )
            }
            delay(1000)
        }
    }

    // Auto-oculta controles após 7 segundos de reprodução
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(7000)
            showControls = false
        }
    }

    // Auto-limpa aviso de retomada após 5 segundos
    LaunchedEffect(resumeNotice) {
        if (resumeNotice != null) {
            delay(5000)
            resumeNotice = null
        }
    }

    // Auto-limpa aviso de seek rápido após 2 segundos
    LaunchedEffect(seekNotice) {
        if (seekNotice != null) {
            delay(2000)
            seekNotice = null
        }
    }

    // Transfere o foco para o botão de Play/Pause assim que os controles aparecem
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(50)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            scrubPositionMs = null
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Salva progresso ao sair da tela
    DisposableEffect(item.id, season, episode) {
        onDispose {
            val finalPos = exoPlayer.currentPosition
            val finalDur = exoPlayer.duration
            PlaybackHistoryManager.savePlaybackPosition(
                context = context,
                itemId = item.id,
                positionMs = finalPos,
                durationMs = finalDur,
                item = item,
                season = season,
                episode = episode,
                episodeTitle = episodeTitle
            )
            exoPlayer.stop()
            exoPlayer.release()
            headlessWebView.stopLoading()
            headlessWebView.destroy()
        }
    }

    BackHandler {
        if (scrubPositionMs != null) {
            scrubPositionMs = null
        } else if (showControls) {
            showControls = false
        } else {
            onBackClicked()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode

                    if (showControls) {
                        // CONTROLES ABERTOS:
                        // Permite livre navegação do foco (D-PAD) entre a Seekbar e os botões
                        when (keyCode) {
                            KeyEvent.KEYCODE_BACK -> {
                                if (scrubPositionMs != null) {
                                    scrubPositionMs = null
                                } else {
                                    showControls = false
                                }
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                exoPlayer.play()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                exoPlayer.pause()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(newPos)
                                true
                            }
                            // Permite que o Compose TV mova o foco entre a Seekbar e os botões
                            else -> false
                        }
                    } else {
                        // TELA CHEIA (CONTROLES OCULTOS):
                        when (keyCode) {
                            KeyEvent.KEYCODE_BACK -> {
                                onBackClicked()
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                showControls = true
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                    showControls = true
                                } else {
                                    exoPlayer.play()
                                }
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                exoPlayer.play()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                exoPlayer.pause()
                                showControls = true
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                seekNotice = "-10S // ${formatTime(newPos)}"
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(newPos)
                                seekNotice = "+10S // ${formatTime(newPos)}"
                                true
                            }
                            else -> false
                        }
                    }
                } else false
            }
    ) {
        // Player de Vídeo Hardware Media3
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Banner Flutuante: Retomada de Posição (Sem emojis)
        if (resumeNotice != null && !showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .background(NeoColors.SurfaceFocused, RectangleShape)
                    .border(2.dp, NeoColors.Yellow, RectangleShape)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "[ $resumeNotice ]",
                    style = NeoType.Badge,
                    color = NeoColors.Yellow
                )
            }
        }

        // Pill Flutuante: Feedback Imediato de Seek (Sem emojis)
        if (seekNotice != null && !showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .background(NeoColors.Surface, RectangleShape)
                    .border(2.dp, NeoColors.Yellow, RectangleShape)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "[ $seekNotice ]",
                    style = NeoType.Badge,
                    color = NeoColors.Yellow
                )
            }
        }

        // Overlay de Carregamento Inicial
        if (isLoadingSources || isBuffering) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isLoadingSources) NeoColors.Bg else Color.Transparent)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeoColors.Yellow)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isLoadingSources) "OBTENDO STREAMS // HLS MASTER..." else "BUFFERING // AGUARDE...",
                        style = NeoType.Badge,
                        color = NeoColors.Yellow
                    )
                }
            }
        }

        // Overlay de Erro
        if (errorMessage != null && !isLoadingSources) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(NeoColors.Bg.copy(alpha = 0.95f))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "FALHA NA REPRODUÇÃO // MEGAEMBED",
                        style = NeoType.Section,
                        color = NeoColors.Red
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        style = NeoType.Body,
                        color = NeoColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (sources.isNotEmpty()) {
                            NeoButton(
                                text = "[ TENTAR OUTRA FONTE ]",
                                onClick = {
                                    val nextIndex = (currentSourceIndex + 1) % sources.size
                                    currentSourceIndex = nextIndex
                                    errorMessage = null
                                    playSource(exoPlayer, sources[nextIndex], currentPositionMs)
                                },
                                variant = NeoButtonVariant.Primary
                            )
                        }
                        NeoButton(
                            text = "[ VOLTAR ]",
                            onClick = onBackClicked,
                            variant = NeoButtonVariant.Secondary
                        )
                    }
                }
            }
        }

        // INTERFACE HUD COMPACTA E MINIMALISTA (SEM POLUIÇÃO VISUAL, SEM EMOJIS)
        AnimatedVisibility(
            visible = showControls && !isLoadingSources,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Barra inferior compacta: apenas um gradiente sutil na base (não tampa o filme)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    NeoColors.Bg.copy(alpha = 0.85f),
                                    NeoColors.Bg.copy(alpha = 0.97f)
                                )
                            )
                        )
                        .padding(horizontal = 40.dp, vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Linha 1: Título e Timestamps limpos com indicação de arraste
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val headerTitle = if (item.mediaType == "tv") {
                                val epInfo = " // T${season}E${episode}" + if (!episodeTitle.isNullOrBlank()) " - ${episodeTitle.uppercase()}" else ""
                                "${item.displayTitle.uppercase()}$epInfo"
                            } else {
                                item.displayTitle.uppercase()
                            }

                            Text(
                                text = headerTitle,
                                style = NeoType.Badge,
                                color = NeoColors.TextPrimary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            val displayPos = scrubPositionMs ?: currentPositionMs
                            val timeInfo = if (scrubPositionMs != null) {
                                "${formatTime(displayPos)} / ${formatTime(durationMs)} [ OK PARA SALTAR ]"
                            } else {
                                "${formatTime(displayPos)} / ${formatTime(durationMs)}"
                            }

                            Text(
                                text = timeInfo,
                                style = NeoType.Badge,
                                color = NeoColors.Yellow
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Linha 2: Barra de Progresso Interativa (Scrubber / Arraste D-Pad)
                        NeoSeekbar(
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            scrubPositionMs = scrubPositionMs,
                            onScrub = { newPos ->
                                scrubPositionMs = newPos
                            },
                            onSeekCommit = { commitPos ->
                                exoPlayer.seekTo(commitPos)
                                currentPositionMs = commitPos
                                scrubPositionMs = null
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Linha 3: Controles Minimalistas com Ícones Vetoriais e Navegação Livre
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    showControls = false
                                    true
                                } else false
                            }
                        ) {
                            // Voltar
                            NeoIconButton(
                                icon = Icons.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                onClick = onBackClicked
                            )

                            // Voltar 10s
                            NeoIconButton(
                                icon = Icons.Filled.Replay10,
                                contentDescription = "-10s",
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                }
                            )

                            // Play / Pause (Foco inicial com playPauseFocusRequester)
                            NeoIconButton(
                                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Continuar",
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier.focusRequester(playPauseFocusRequester)
                            )

                            // Avançar 10s
                            NeoIconButton(
                                icon = Icons.Filled.Forward10,
                                contentDescription = "+10s",
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(newPos)
                                }
                            )

                            // Fontes de reprodução (Pílulas compactas, sem emojis)
                            if (sources.size > 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                sources.forEachIndexed { idx, src ->
                                    val isSelected = (idx == currentSourceIndex)
                                    NeoPillButton(
                                        text = "FONTE ${idx + 1}",
                                        isSelected = isSelected,
                                        onClick = {
                                            if (currentSourceIndex != idx) {
                                                currentSourceIndex = idx
                                                errorMessage = null
                                                playSource(exoPlayer, src, exoPlayer.currentPosition)
                                            }
                                        }
                                    )
                                }
                            }

                            // Botão Próximo Episódio para Séries
                            if (item.mediaType == "tv" && onPlayNextEpisode != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                NeoPillButton(
                                    text = "PRÓXIMO EP",
                                    onClick = { onPlayNextEpisode(season, episode + 1) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }
}

private fun playSource(player: ExoPlayer, source: StreamSource, startPositionMs: Long = 0L) {
    try {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://mgeb.lat/"))

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(source.file)
            .apply {
                if (source.file.contains(".m3u8") || source.type == "hls") {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                } else {
                    setMimeType(MimeTypes.APPLICATION_MP4)
                }
            }
            .build()

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        if (startPositionMs > 3000L) {
            player.seekTo(startPositionMs)
        }
        player.playWhenReady = true
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
