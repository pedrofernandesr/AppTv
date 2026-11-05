package com.pedro.cinetv.ui.screens.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedro.cinetv.data.api.TmdbRepository
import com.pedro.cinetv.data.model.TmdbItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val featured: TmdbItem? = null,
    val trending: List<TmdbItem> = emptyList(),
    val popularMovies: List<TmdbItem> = emptyList(),
    val popularSeries: List<TmdbItem> = emptyList(),
    val nowPlaying: List<TmdbItem> = emptyList(),
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: TmdbRepository = TmdbRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Memória de rolagem e posição: mantém a posição exata das fileiras quando o usuário entra e volta de um filme
    val listState = LazyListState()
    val trendingRowState = LazyListState()
    val popularMoviesRowState = LazyListState()
    val popularSeriesRowState = LazyListState()
    val nowPlayingRowState = LazyListState()

    var lastFocusedItemId: Int? = null

    init {
        loadCatalog()
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                repository.syncConfiguration()
                val trending = repository.getTrending()
                val movies = repository.getPopularMovies()
                val series = repository.getPopularTv()
                val nowPlaying = repository.getNowPlaying()

                _uiState.value = HomeUiState(
                    isLoading = false,
                    featured = trending.firstOrNull(),
                    trending = trending,
                    popularMovies = movies,
                    popularSeries = series,
                    nowPlaying = nowPlaying
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "ERRO AO CARREGAR CATÁLOGO"
                )
            }
        }
    }

    fun updateFeatured(item: TmdbItem) {
        _uiState.value = _uiState.value.copy(featured = item)
    }
}
