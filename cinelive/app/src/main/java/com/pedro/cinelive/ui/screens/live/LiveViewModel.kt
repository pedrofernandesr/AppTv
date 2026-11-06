package com.pedro.cinelive.ui.screens.live

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedro.cinelive.data.api.ReiDosCanaisRepository
import com.pedro.cinelive.data.model.CategoryItem
import com.pedro.cinelive.data.model.ChannelItem
import com.pedro.cinelive.data.model.ResolvedStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveViewModel(
    private val repository: ReiDosCanaisRepository = ReiDosCanaisRepository.getInstance()
) : ViewModel() {

    companion object {
        private const val TAG = "LiveViewModel"
        private const val DEFAULT_CATEGORY = "Canais Abertos"
    }

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

            val catResult = repository.fetchCategories()
            if (catResult.isSuccess) {
                val cats = catResult.getOrDefault(emptyList())
                _categories.value = cats

                // Seleciona preferencialmente a categoria "Canais Abertos"
                val defaultCat = cats.find { it.name.equals(DEFAULT_CATEGORY, ignoreCase = true) }
                    ?: cats.firstOrNull()

                _selectedCategory.value = defaultCat

                if (defaultCat != null) {
                    loadChannelsForCategory(defaultCat, autoPlayFirst = true)
                }
            } else {
                _errorMessage.value = "Erro ao carregar categorias. Verifique a conexão."
                _isLoading.value = false
            }
        }
    }

    fun selectCategory(category: CategoryItem) {
        if (_selectedCategory.value?.id == category.id) return
        _selectedCategory.value = category
        loadChannelsForCategory(category, autoPlayFirst = false)
    }

    private fun loadChannelsForCategory(category: CategoryItem, autoPlayFirst: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.fetchChannels(category.name)
            if (result.isSuccess) {
                val channelList = result.getOrDefault(emptyList())
                _channels.value = channelList
                _isLoading.value = false

                if (autoPlayFirst && channelList.isNotEmpty() && _currentChannel.value == null) {
                    selectChannel(channelList.first())
                }
            } else {
                _errorMessage.value = "Erro ao carregar canais de ${category.name}"
                _isLoading.value = false
            }
        }
    }

    fun selectChannel(channel: ChannelItem) {
        _currentChannel.value = channel
        _isDrawerOpen.value = false
        showEpgBanner(5000L)

        // Cancela resolução anterior e inicia a nova
        streamResolveJob?.cancel()
        streamResolveJob = viewModelScope.launch {
            try {
                val stream = repository.resolveStream(channel)
                _currentStream.value = stream
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao resolver stream para canal ${channel.name}", e)
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
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
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
