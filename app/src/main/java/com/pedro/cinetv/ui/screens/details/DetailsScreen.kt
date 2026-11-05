package com.pedro.cinetv.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pedro.cinetv.data.api.TmdbRepository
import com.pedro.cinetv.data.local.PlaybackHistoryManager
import com.pedro.cinetv.data.model.TmdbItem
import com.pedro.cinetv.data.model.TvDetailsResponse
import com.pedro.cinetv.data.model.TvEpisode
import com.pedro.cinetv.data.model.TvSeasonInfo
import com.pedro.cinetv.ui.components.*
import com.pedro.cinetv.ui.theme.NeoColors
import com.pedro.cinetv.ui.theme.NeoDimens
import com.pedro.cinetv.ui.theme.NeoType
import kotlinx.coroutines.launch

@Composable
fun DetailsScreen(
    item: TmdbItem,
    onBackClicked: () -> Unit,
    onPlayVideo: (season: Int, episode: Int, episodeTitle: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { TmdbRepository() }
    val playButtonFocusRequester = remember { FocusRequester() }
    val episodesRowState = rememberLazyListState()
    val seasonsRowState = rememberLazyListState()

    var isTv by remember(item.id) { mutableStateOf(item.isTv) }
    val lastWatched = remember(item.id, isTv) {
        if (isTv) PlaybackHistoryManager.getLastWatchedEpisode(context, item.id) else Pair(1, 1)
    }
    var tvDetails by remember { mutableStateOf<TvDetailsResponse?>(null) }
    var currentSeason by remember { mutableStateOf(if (isTv) lastWatched.first else 1) }
    var episodes by remember { mutableStateOf<List<TvEpisode>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        if (isTv) {
            val details = repository.getTvDetails(item.id)
            if (details != null) {
                tvDetails = details
                val availableSeasons = details.seasons.filter { it.seasonNumber > 0 }
                if (availableSeasons.any { it.seasonNumber == lastWatched.first }) {
                    currentSeason = lastWatched.first
                } else {
                    currentSeason = availableSeasons.firstOrNull()?.seasonNumber ?: 1
                }
            }
        } else {
            // Fallback de detecção: se a TMDB tiver série com esse id e com temporadas, atualiza isTv
            val details = repository.getTvDetails(item.id)
            if (details != null && details.numberOfSeasons > 0 && details.seasons.isNotEmpty()) {
                isTv = true
                tvDetails = details
                val availableSeasons = details.seasons.filter { it.seasonNumber > 0 }
                if (availableSeasons.any { it.seasonNumber == lastWatched.first }) {
                    currentSeason = lastWatched.first
                } else {
                    currentSeason = availableSeasons.firstOrNull()?.seasonNumber ?: 1
                }
            }
        }
        try {
            playButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Sempre que trocar a temporada, busca os novos episódios e reseta a rolagem para o primeiro episódio
    LaunchedEffect(item.id, currentSeason, isTv) {
        if (isTv) {
            isLoadingEpisodes = true
            episodes = repository.getTvSeason(item.id, currentSeason)
            isLoadingEpisodes = false
            episodesRowState.scrollToItem(0, 0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeoColors.Bg)
    ) {
        // Fundo com Imagem Escurecida
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.fullBackdropUrl)
                .crossfade(150)
                .build(),
            contentDescription = item.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.35f }
        )

        // Gradiente Escuro
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NeoColors.Bg.copy(alpha = 0.7f),
                            NeoColors.Bg.copy(alpha = 0.95f),
                            NeoColors.Bg
                        )
                    )
                )
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 24.dp, bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // CABEÇALHO COM BOTÃO VOLTAR
            item(key = "header_back") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    NeoButton(
                        text = "< VOLTAR",
                        onClick = onBackClicked,
                        variant = NeoButtonVariant.Secondary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "DETALHES // ${item.displayTitle.uppercase()}",
                        style = NeoType.Badge,
                        color = NeoColors.TextSecondary
                    )
                }
            }

            // INFORMAÇÕES PRINCIPAIS DO TÍTULO
            item(key = "main_info") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeoBadge(text = item.year)
                    NeoBadge(text = if (isTv) "SÉRIE" else "FILME")
                    if (item.voteAverage > 0) {
                        NeoRatingBar(voteAverage = item.voteAverage)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.displayTitle,
                    style = NeoType.Hero,
                    color = NeoColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                NeoMetadataStripe {
                    Text(
                        text = "SINOPSE //",
                        style = NeoType.Badge,
                        color = NeoColors.Yellow
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.overview.ifBlank { "NENHUMA SINOPSE DISPONÍVEL." },
                        style = NeoType.Body,
                        color = NeoColors.TextSecondary,
                        modifier = Modifier.widthIn(max = 760.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progresso salvo no histórico (se houver)
                val targetEpisode = if (isTv) {
                    if (currentSeason == lastWatched.first) lastWatched.second else 1
                } else {
                    0
                }
                val targetSeason = if (isTv) currentSeason else 0
                var savedPos by remember(item.id, targetSeason, targetEpisode) {
                    mutableStateOf(PlaybackHistoryManager.getPlaybackPosition(context, item.id, targetSeason, targetEpisode))
                }
                var savedDur by remember(item.id, targetSeason, targetEpisode) {
                    mutableStateOf(PlaybackHistoryManager.getPlaybackDuration(context, item.id, targetSeason, targetEpisode))
                }
                var inContinueWatching by remember(item.id) {
                    mutableStateOf(PlaybackHistoryManager.isInContinueWatching(context, item.id))
                }
                val hasProgress = savedPos > 5000L

                if (hasProgress) {
                    val progressRatio = if (savedDur > 0) (savedPos.toFloat() / savedDur.toFloat()).coerceIn(0f, 1f) else 0f
                    val epLabel = if (isTv) " (T${currentSeason}E${targetEpisode})" else ""
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = "CONTINUAR DE ONDE PAROU$epLabel: ${formatTime(savedPos)} / ${formatTime(savedDur)}",
                            style = NeoType.Badge,
                            color = NeoColors.Yellow
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(300.dp)
                                .height(4.dp)
                                .background(NeoColors.Surface, RectangleShape)
                                .border(1.dp, NeoColors.BorderRest, RectangleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressRatio)
                                    .background(NeoColors.Yellow, RectangleShape)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeoButton(
                        text = when {
                            hasProgress -> "[ CONTINUAR // ${formatTime(savedPos)} ]"
                            isTv -> "[ ASSISTIR SÉRIE ]"
                            else -> "[ ASSISTIR AGORA ]"
                        },
                        onClick = {
                            onPlayVideo(targetSeason, targetEpisode, null)
                        },
                        variant = NeoButtonVariant.Primary,
                        modifier = Modifier.focusRequester(playButtonFocusRequester)
                    )

                    if (hasProgress) {
                        NeoButton(
                            text = "[ DO INÍCIO ]",
                            onClick = {
                                PlaybackHistoryManager.clearPlaybackPosition(context, item.id)
                                savedPos = 0L
                                savedDur = 0L
                                inContinueWatching = false
                                onPlayVideo(targetSeason, targetEpisode, null)
                            },
                            variant = NeoButtonVariant.Secondary
                        )
                    }

                    if (inContinueWatching || hasProgress) {
                        NeoButton(
                            text = "[ EXCLUIR DO CONTINUAR ASSISTINDO ]",
                            onClick = {
                                PlaybackHistoryManager.clearPlaybackPosition(context, item.id)
                                savedPos = 0L
                                savedDur = 0L
                                inContinueWatching = false
                            },
                            variant = NeoButtonVariant.Secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // SELETOR DE TEMPORADAS E EPISÓDIOS (PARA SÉRIES)
            if (isTv) {
                val seasons = tvDetails?.seasons?.filter { it.seasonNumber > 0 } ?: listOf(TvSeasonInfo(seasonNumber = 1, name = "Temporada 1"))

                item(key = "seasons_selector") {
                    Text(
                        text = "TEMPORADAS // ESCOLHA A TEMPORADA",
                        style = NeoType.Section,
                        color = NeoColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        state = seasonsRowState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 2.dp, end = 52.dp, top = 2.dp, bottom = 6.dp)
                    ) {
                        itemsIndexed(seasons) { index, s ->
                            val isSelected = (s.seasonNumber == currentSeason)
                            NeoPillButton(
                                text = "TEMP ${s.seasonNumber}",
                                isSelected = isSelected,
                                onClick = {
                                    if (currentSeason != s.seasonNumber) {
                                        currentSeason = s.seasonNumber
                                        coroutineScope.launch {
                                            episodesRowState.scrollToItem(0, 0)
                                        }
                                    }
                                },
                                modifier = Modifier.onFocusChanged {
                                    if (it.isFocused) {
                                        coroutineScope.launch {
                                            val targetIndex = (index - 2).coerceAtLeast(0)
                                            seasonsRowState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item(key = "episodes_header") {
                    Text(
                        text = "EPISÓDIOS // TEMPORADA $currentSeason",
                        style = NeoType.Section,
                        color = NeoColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isLoadingEpisodes) {
                    item(key = "episodes_loading") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            CircularProgressIndicator(color = NeoColors.Yellow)
                        }
                    }
                } else if (episodes.isEmpty()) {
                    item(key = "episodes_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NeoColors.BorderRest, RectangleShape)
                                .background(NeoColors.Surface, RectangleShape)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "EPISÓDIOS CARREGANDO OU INDISPONÍVEIS.",
                                style = NeoType.Badge,
                                color = NeoColors.TextMuted
                            )
                        }
                    }
                } else {
                    item(key = "episodes_row") {
                        LazyRow(
                            state = episodesRowState,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(start = 4.dp, end = 64.dp, top = 8.dp, bottom = 20.dp)
                        ) {
                            itemsIndexed(episodes, key = { _, ep -> ep.id }) { index, ep ->
                                NeoEpisodeCard(
                                    episodeNumber = ep.episodeNumber,
                                    name = ep.name.ifBlank { "Episódio ${ep.episodeNumber}" },
                                    stillUrl = if (ep.fullStillUrl.isNotBlank()) ep.fullStillUrl else item.fullBackdropUrl,
                                    runtimeMinutes = ep.runtime,
                                    onClick = {
                                        onPlayVideo(currentSeason, ep.episodeNumber, ep.name)
                                    },
                                    modifier = Modifier.onFocusChanged {
                                        if (it.isFocused) {
                                            coroutineScope.launch {
                                                val targetIndex = (index - 2).coerceAtLeast(0)
                                                episodesRowState.animateScrollToItem(targetIndex)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
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
