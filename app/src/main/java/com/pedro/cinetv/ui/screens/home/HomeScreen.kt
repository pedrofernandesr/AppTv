package com.pedro.cinetv.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pedro.cinetv.data.local.PlaybackHistoryManager
import com.pedro.cinetv.data.model.TmdbItem
import com.pedro.cinetv.ui.components.*
import com.pedro.cinetv.ui.theme.NeoColors
import com.pedro.cinetv.ui.theme.NeoDimens
import com.pedro.cinetv.ui.theme.NeoMotion
import com.pedro.cinetv.ui.theme.NeoType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onItemSelected: (TmdbItem) -> Unit,
    onPlayVideo: (TmdbItem, season: Int, episode: Int) -> Unit,
    onSearchClicked: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val playButtonFocusRequester = remember { FocusRequester() }

    var isBannerFocused by remember { mutableStateOf(true) }
    var activeFilter by remember { mutableStateOf("INICIO") }

    // Lista reativa de Continuar Assistindo: recarrega sempre que a tela é apresentada
    var continueWatching by remember {
        mutableStateOf(PlaybackHistoryManager.getContinueWatchingList(context))
    }

    LaunchedEffect(Unit) {
        continueWatching = PlaybackHistoryManager.getContinueWatchingList(context)
    }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            continueWatching = PlaybackHistoryManager.getContinueWatchingList(context)
        }
    }

    val currentFeatured = remember(state.featured, activeFilter, state.popularMovies, state.popularSeries) {
        when (activeFilter) {
            "FILMES" -> state.popularMovies.firstOrNull() ?: state.featured
            "SERIES" -> state.popularSeries.firstOrNull() ?: state.featured
            else -> state.featured
        }
    }

    // Foco padrão automático no botão "[ ASSISTIR AGORA ]" APENAS na abertura inicial da tela
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && !hasRequestedInitialFocus && currentFeatured != null) {
            hasRequestedInitialFocus = true
            delay(200)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val bannerHeight by animateDpAsState(
        targetValue = if (isBannerFocused) 390.dp else 80.dp,
        animationSpec = tween(NeoMotion.FocusMs),
        label = "bannerHeight"
    )

    if (state.isLoading) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .background(NeoColors.Bg)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NeoColors.Yellow)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CARREGANDO CATÁLOGO // TMDB...",
                    style = NeoType.Badge,
                    color = NeoColors.TextSecondary
                )
            }
        }
        return
    }

    if (state.errorMessage != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxSize()
                .background(NeoColors.Bg)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = state.errorMessage!!,
                    style = NeoType.Section,
                    color = NeoColors.Red
                )
                Spacer(modifier = Modifier.height(16.dp))
                NeoButton(
                    text = "[ TENTAR DE NOVO ]",
                    onClick = { viewModel.loadCatalog() },
                    variant = NeoButtonVariant.Secondary
                )
            }
        }
        return
    }

    val filteredWatching = when (activeFilter) {
        "FILMES" -> continueWatching.filter { it.mediaType != "tv" }
        "SERIES" -> continueWatching.filter { it.mediaType == "tv" }
        else -> continueWatching
    }
    val hasContinueWatching = filteredWatching.isNotEmpty()
    val isFirstCatalogRow = !hasContinueWatching

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoColors.Bg)
    ) {
        // 1 & 2. HEADER DINÂMICO FIXO NO TOPO: HERO BANNER QUE RECOLHE/EXPANDE COM O FOCO
        // Fica fora da LazyColumn para garantir que a tela e o banner nunca fiquem cortados ou desincronizados
        if (currentFeatured != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
            ) {
                // Imagem de Backdrop do Banner
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentFeatured.fullBackdropUrl)
                        .crossfade(150)
                        .build(),
                    contentDescription = currentFeatured.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (isBannerFocused) 1f else 0.22f
                        }
                )

                // Gradiente Suave Dinâmico
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = if (isBannerFocused) {
                                    arrayOf(
                                        0.0f to NeoColors.Bg.copy(alpha = 0.82f),
                                        0.18f to NeoColors.Bg.copy(alpha = 0.42f),
                                        0.38f to Color.Transparent,
                                        0.65f to NeoColors.Bg.copy(alpha = 0.65f),
                                        1.0f to NeoColors.Bg
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to NeoColors.Bg.copy(alpha = 0.88f),
                                        1.0f to NeoColors.Bg
                                    )
                                }
                            )
                        )
                )

                // BARRA SUPERIOR DE NAVEGAÇÃO TRANSLÚCIDA
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = NeoDimens.SafeH, vertical = 12.dp)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                isBannerFocused = true
                                coroutineScope.launch {
                                    viewModel.listState.animateScrollToItem(0)
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "CINETV //",
                            style = NeoType.Hero,
                            color = NeoColors.Yellow
                        )

                        // Quando recolhido, exibe o título em destaque sutilmente de lado
                        if (!isBannerFocused) {
                            Text(
                                text = "EM FOCO // ${currentFeatured.displayTitle.uppercase()}",
                                style = NeoType.Badge,
                                color = NeoColors.TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 280.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NeoPillButton(
                            text = "INÍCIO",
                            isSelected = (activeFilter == "INICIO"),
                            onClick = { activeFilter = "INICIO" },
                            modifier = Modifier.focusProperties { down = playButtonFocusRequester }
                        )
                        NeoPillButton(
                            text = "FILMES",
                            isSelected = (activeFilter == "FILMES"),
                            onClick = { activeFilter = "FILMES" },
                            modifier = Modifier.focusProperties { down = playButtonFocusRequester }
                        )
                        NeoPillButton(
                            text = "SÉRIES",
                            isSelected = (activeFilter == "SERIES"),
                            onClick = { activeFilter = "SERIES" },
                            modifier = Modifier.focusProperties { down = playButtonFocusRequester }
                        )
                        NeoIconButton(
                            icon = Icons.Filled.Search,
                            contentDescription = "Buscar",
                            onClick = onSearchClicked,
                            buttonSize = 42.dp,
                            modifier = Modifier.focusProperties { down = playButtonFocusRequester }
                        )
                    }
                }

                // CONTEÚDO DO HERO (TÍTULO, METADADOS E AÇÕES)
                // Totalmente visível quando expandido; mantém botões focáveis para o D-pad quando recolhido
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = NeoDimens.SafeH, vertical = if (isBannerFocused) 16.dp else 4.dp)
                        .widthIn(max = 680.dp)
                        .graphicsLayer {
                            alpha = if (isBannerFocused) 1f else 0.001f
                        }
                ) {
                    if (isBannerFocused) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NeoBadge(text = currentFeatured.year)
                            NeoBadge(text = if (currentFeatured.mediaType == "tv") "SÉRIE" else "FILME")
                            NeoRatingBar(voteAverage = currentFeatured.voteAverage)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentFeatured.displayTitle,
                            style = NeoType.Hero,
                            color = NeoColors.TextPrimary,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentFeatured.overview,
                            style = NeoType.Body,
                            color = NeoColors.TextSecondary,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Ações Diretas: Assistir Agora e Detalhes
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.onFocusChanged {
                            if (it.hasFocus) {
                                isBannerFocused = true
                                coroutineScope.launch {
                                    viewModel.listState.animateScrollToItem(0)
                                }
                            }
                        }
                    ) {
                        NeoButton(
                            text = "[ ASSISTIR AGORA ]",
                            onClick = {
                                val lastWatched = PlaybackHistoryManager.getLastWatchedEpisode(context, currentFeatured.id)
                                onPlayVideo(currentFeatured, lastWatched.first, lastWatched.second)
                            },
                            variant = NeoButtonVariant.Primary,
                            modifier = Modifier.focusRequester(playButtonFocusRequester)
                        )
                        NeoButton(
                            text = "[ DETALHES ]",
                            onClick = { onItemSelected(currentFeatured) },
                            variant = NeoButtonVariant.Secondary
                        )
                    }
                }
            }
        } else {
            // Fallback caso não haja item em destaque
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NeoDimens.SafeH, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CINETV //",
                    style = NeoType.Hero,
                    color = NeoColors.Yellow
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeoPillButton(
                        text = "INÍCIO",
                        isSelected = (activeFilter == "INICIO"),
                        onClick = { activeFilter = "INICIO" }
                    )
                    NeoPillButton(
                        text = "FILMES",
                        isSelected = (activeFilter == "FILMES"),
                        onClick = { activeFilter = "FILMES" }
                    )
                    NeoPillButton(
                        text = "SÉRIES",
                        isSelected = (activeFilter == "SERIES"),
                        onClick = { activeFilter = "SERIES" }
                    )
                    NeoIconButton(
                        icon = Icons.Filled.Search,
                        contentDescription = "Buscar",
                        onClick = onSearchClicked,
                        buttonSize = 42.dp
                    )
                }
            }
        }

        // 3. FILEIRAS DO CATÁLOGO ROLÁVEL (LAZYCOLUMN)
        // Posicionada logo abaixo do header/banner para manter o título da primeira seção 100% visível
        LazyColumn(
            state = viewModel.listState,
            contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // FILEIRA: CONTINUAR ASSISTINDO (FILMES E SÉRIES EM ANDAMENTO)
            if (hasContinueWatching) {
                item(key = "row_continue_watching") {
                    Column(modifier = Modifier.padding(bottom = 14.dp)) {
                        Text(
                            text = "CONTINUAR ASSISTINDO // EM ANDAMENTO",
                            style = NeoType.Section,
                            color = NeoColors.Yellow,
                            modifier = Modifier.padding(horizontal = NeoDimens.SafeH, vertical = 6.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(start = NeoDimens.SafeH, end = NeoDimens.SafeH + 24.dp, top = 6.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(NeoDimens.Gap)
                        ) {
                            items(filteredWatching, key = { "cw_${it.id}" }) { cw ->
                                NeoContinueWatchingCard(
                                    item = cw,
                                    onClick = {
                                        onPlayVideo(cw.toTmdbItem(), cw.season, cw.episode)
                                    },
                                    modifier = Modifier
                                        .focusProperties { up = playButtonFocusRequester }
                                        .onFocusChanged {
                                            if (it.isFocused) {
                                                isBannerFocused = false
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // FILEIRAS GERAIS DO CATÁLOGO DE ACORDO COM O FILTRO
            if (activeFilter == "INICIO" || activeFilter == "FILMES") {
                item(key = "row_trending") {
                    CatalogRow(
                        title = "EM ALTA // SEMANA",
                        items = if (activeFilter == "FILMES") state.trending.filter { it.mediaType != "tv" } else state.trending,
                        state = viewModel.trendingRowState,
                        upFocusRequester = if (isFirstCatalogRow) playButtonFocusRequester else null,
                        onItemFocused = { item ->
                            isBannerFocused = false
                            viewModel.updateFeatured(item)
                        },
                        onItemClicked = { item ->
                            viewModel.lastFocusedItemId = item.id
                            onItemSelected(item)
                        }
                    )
                }

                item(key = "row_popular_movies") {
                    CatalogRow(
                        title = "FILMES POPULARES",
                        items = state.popularMovies,
                        state = viewModel.popularMoviesRowState,
                        onItemFocused = { item ->
                            isBannerFocused = false
                            viewModel.updateFeatured(item)
                        },
                        onItemClicked = { item ->
                            viewModel.lastFocusedItemId = item.id
                            onItemSelected(item)
                        }
                    )
                }
            }

            if (activeFilter == "INICIO" || activeFilter == "SERIES") {
                item(key = "row_popular_series") {
                    CatalogRow(
                        title = "SÉRIES POPULARES",
                        items = state.popularSeries,
                        state = viewModel.popularSeriesRowState,
                        upFocusRequester = if (isFirstCatalogRow && activeFilter == "SERIES") playButtonFocusRequester else null,
                        onItemFocused = { item ->
                            isBannerFocused = false
                            viewModel.updateFeatured(item)
                        },
                        onItemClicked = { item ->
                            viewModel.lastFocusedItemId = item.id
                            onItemSelected(item)
                        }
                    )
                }
            }

            if (activeFilter == "INICIO" || activeFilter == "FILMES") {
                item(key = "row_now_playing") {
                    CatalogRow(
                        title = "EM CARTAZ // CINEMA",
                        items = state.nowPlaying,
                        state = viewModel.nowPlayingRowState,
                        onItemFocused = { item ->
                            isBannerFocused = false
                            viewModel.updateFeatured(item)
                        },
                        onItemClicked = { item ->
                            viewModel.lastFocusedItemId = item.id
                            onItemSelected(item)
                        }
                    )
                }
            }

            // Espaçamento inferior generoso para garantir que a última fileira não encoste na borda inferior da TV
            item(key = "footer_breathing_space") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CatalogRow(
    title: String,
    items: List<TmdbItem>,
    state: LazyListState,
    onItemFocused: (TmdbItem) -> Unit,
    onItemClicked: (TmdbItem) -> Unit,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = NeoDimens.Gap)
    ) {
        Text(
            text = title,
            style = NeoType.Section,
            color = NeoColors.TextPrimary,
            modifier = Modifier.padding(horizontal = NeoDimens.SafeH, vertical = 6.dp)
        )

        LazyRow(
            state = state,
            contentPadding = PaddingValues(start = NeoDimens.SafeH, end = NeoDimens.SafeH + 24.dp, top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(NeoDimens.Gap)
        ) {
            items(items, key = { it.id }) { item ->
                NeoPosterCard(
                    posterUrl = item.fullPosterUrl,
                    title = item.displayTitle,
                    onClick = { onItemClicked(item) },
                    modifier = Modifier
                        .then(
                            if (upFocusRequester != null) {
                                Modifier.focusProperties { up = upFocusRequester }
                            } else Modifier
                        )
                        .onFocusChanged {
                            if (it.isFocused) {
                                onItemFocused(item)
                            }
                        }
                )
            }
        }
    }
}
