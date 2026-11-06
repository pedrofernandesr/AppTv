package com.pedro.cinelive.ui.screens.live

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedro.cinelive.data.api.ReiDosCanaisRepository
import com.pedro.cinelive.data.local.ChannelPreferences
import com.pedro.cinelive.data.model.CategoryItem
import com.pedro.cinelive.data.model.ChannelItem
import com.pedro.cinelive.data.model.ResolvedStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LiveViewModel(
    private val repository: ReiDosCanaisRepository = ReiDosCanaisRepository.getInstance()
) : ViewModel() {

    companion object {
        private const val TAG = "LiveViewModel"
        private const val ALL_CHANNELS_CAT = "Todos os Canais"
    }

    private val _allChannels = MutableStateFlow<List<ChannelItem>>(emptyList())

    private val _categories = MutableStateFlow<List<CategoryItem>>(emptyList())
    val categories: StateFlow<List<CategoryItem>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<CategoryItem?>(null)
    val selectedCategory: StateFlow<CategoryItem?> = _selectedCategory.asStateFlow()

    private val _channels = MutableStateFlow<List<ChannelItem>>(emptyList())
    val channels: StateFlow<List<ChannelItem>> = _channels.asStateFlow()

    private val _currentChannel = MutableStateFlow<ChannelItem?>(null)
    val currentChannel: StateFlow<ChannelItem?> = _currentChannel.asStateFlow()

    private val _currentStream = MutableStateFlow<ResolvedStream?>(null)
    val currentStream: StateFlow<ResolvedStream?> = _currentStream.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _isCatalogGridOpen = MutableStateFlow(false)
    val isCatalogGridOpen: StateFlow<Boolean> = _isCatalogGridOpen.asStateFlow()

    private val _isEpgBannerVisible = MutableStateFlow(false)
    val isEpgBannerVisible: StateFlow<Boolean> = _isEpgBannerVisible.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var epgTimerJob: Job? = null
    private var streamResolveJob: Job? = null

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // 1. Busca catálogo completo de todos os canais (null = todos)
            val chanResult = repository.fetchChannels(null)
            val allChans = if (chanResult.isSuccess) chanResult.getOrDefault(emptyList()) else emptyList()
            _allChannels.value = allChans

            // 2. Busca categorias da API
            val catResult = repository.fetchCategories()
            val rawCategories = if (catResult.isSuccess) catResult.getOrDefault(emptyList()) else emptyList()

            // 3. Monta lista de categorias com "Todos os Canais" e "Filmes e Séries" em destaque
            val orderedCategories = mutableListOf<CategoryItem>()
            orderedCategories.add(CategoryItem(id = 0, name = ALL_CHANNELS_CAT))

            val priorityOrder = listOf(
                "Filmes e Séries",
                "Canais Abertos",
                "Infantil",
                "Esportes",
                "Documentários",
                "Notícias",
                "Entretenimento"
            )

            for (name in priorityOrder) {
                val found = rawCategories.find { it.name.equals(name, ignoreCase = true) }
                if (found != null && orderedCategories.none { it.id == found.id }) {
                    orderedCategories.add(found)
                }
            }

            for (cat in rawCategories) {
                if (orderedCategories.none { it.id == cat.id }) {
                    orderedCategories.add(cat)
                }
            }

            _categories.value = orderedCategories

            // Categoria padrão selecionada: "Todos os Canais"
            val defaultCat = orderedCategories.firstOrNull()
            _selectedCategory.value = defaultCat
            _channels.value = allChans
            _isLoading.value = false

            // Seleciona canal inicial: prioriza o último canal assistido pelo usuário
            if (allChans.isNotEmpty() && _currentChannel.value == null) {
                val lastWatchedId = ChannelPreferences.getLastChannelId()
                val lastChannel = if (!lastWatchedId.isNullOrBlank()) {
                    allChans.find { it.id.equals(lastWatchedId, ignoreCase = true) }
                } else null

                val preferred = lastChannel
                    ?: allChans.find { it.id.equals("sbt", ignoreCase = true) }
                    ?: allChans.find { it.id.equals("tvcultura", ignoreCase = true) }
                    ?: allChans.find { it.id.startsWith("globo", ignoreCase = true) }
                    ?: allChans.first()

                Log.d(TAG, "Iniciando reproducao com canal: ${preferred.name} (ultimo salvo: $lastWatchedId)")
                selectChannel(preferred)
            }
        }
    }

    fun selectCategory(category: CategoryItem) {
        if (_selectedCategory.value?.id == category.id) return
        _selectedCategory.value = category

        val all = _allChannels.value
        _channels.value = if (category.id == 0) {
            all
        } else {
            all.filter { it.category.equals(category.name, ignoreCase = true) }
        }
    }

    fun selectChannel(channel: ChannelItem) {
        // Persiste imediatamente o último canal assistido para restaurar no próximo início
        ChannelPreferences.saveLastChannelId(channel.id)

        if (_currentChannel.value?.id == channel.id && _currentStream.value != null) {
            _isDrawerOpen.value = false
            _isCatalogGridOpen.value = false
            showEpgBanner(5000L)
            return
        }
        _currentChannel.value = channel
        _isDrawerOpen.value = false
        _isCatalogGridOpen.value = false
        _isLoading.value = true
        showEpgBanner(5000L)

        // Cancela resolução anterior e inicia a nova
        streamResolveJob?.cancel()
        streamResolveJob = viewModelScope.launch {
            try {
                val stream = repository.resolveStream(channel)
                _currentStream.value = stream
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao resolver stream para canal ${channel.name}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun nextChannel() {
        val list = _channels.value
        if (list.isEmpty()) return
        val current = _currentChannel.value
        val currentIndex = list.indexOfFirst { it.id == current?.id }
        val nextIndex = if (currentIndex == -1 || currentIndex + 1 >= list.size) 0 else currentIndex + 1
        selectChannel(list[nextIndex])
    }

    fun previousChannel() {
        val list = _channels.value
        if (list.isEmpty()) return
        val current = _currentChannel.value
        val currentIndex = list.indexOfFirst { it.id == current?.id }
        val prevIndex = if (currentIndex <= 0) list.size - 1 else currentIndex - 1
        selectChannel(list[prevIndex])
    }

    fun toggleDrawer() {
        _isDrawerOpen.value = !_isDrawerOpen.value
    }

    fun openDrawer() {
        _isDrawerOpen.value = true
        _isCatalogGridOpen.value = false
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
    }

    fun openCatalogGrid() {
        _isCatalogGridOpen.value = true
        _isDrawerOpen.value = false
    }

    fun closeCatalogGrid() {
        _isCatalogGridOpen.value = false
    }

    fun toggleCatalogGrid() {
        _isCatalogGridOpen.value = !_isCatalogGridOpen.value
        if (_isCatalogGridOpen.value) {
            _isDrawerOpen.value = false
        }
    }

    fun tuneChannelById(channelId: String) {
        closeCatalogGrid()
        closeDrawer()
        viewModelScope.launch {
            if (_allChannels.value.isEmpty()) {
                channels.first { it.isNotEmpty() }
            }
            val all = _allChannels.value
            val found = all.find { it.id.equals(channelId, ignoreCase = true) }
                ?: all.find { it.name.contains(channelId, ignoreCase = true) }
            if (found != null) {
                selectChannel(found)
            }
        }
    }

    fun showEpgBanner(durationMs: Long = 5000L) {
        _isEpgBannerVisible.value = true
        epgTimerJob?.cancel()
        epgTimerJob = viewModelScope.launch {
            delay(durationMs)
            _isEpgBannerVisible.value = false
        }
    }

    fun hideEpgBanner() {
        epgTimerJob?.cancel()
        _isEpgBannerVisible.value = false
    }
}
