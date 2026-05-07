package com.desApp.desapp_aniflix.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.auth.ProfileManager
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.model.ContinueWatchingItem
import com.desApp.desapp_aniflix.model.GenreItem
import com.desApp.desapp_aniflix.network.ContentRetrofitClient
import com.desApp.desapp_aniflix.network.HistoryRetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CatalogViewModel : ViewModel() {
    private val _contentItems = MutableStateFlow<List<ContentItem>>(emptyList())
    val contentItems: StateFlow<List<ContentItem>> = _contentItems

    private val _genres = MutableStateFlow<List<GenreItem>>(emptyList())
    val genres: StateFlow<List<GenreItem>> = _genres

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _watchLater = mutableStateListOf<ContentItem>()
    val watchLater: List<ContentItem> = _watchLater

    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _isRefreshing.value = true
            try {
                val recentResponse = ContentRetrofitClient.contentApiService.getRecent(50)
                _contentItems.value = recentResponse.data

                val genresResponse = ContentRetrofitClient.contentApiService.getGenres()
                _genres.value = genresResponse.data

                // Cargar "Continue Watching" del perfil actual
                loadContinueWatching()
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun loadContinueWatching() {
        try {
            val profileId = ProfileManager.currentProfileId ?: return
            val response = HistoryRetrofitClient.historyApiService.getContinueWatching(profileId, 20)
            _continueWatching.value = response.data
        } catch (_: Exception) {
            // Silenciosamente fallar — puede que no haya contenido en seguimiento
        }
    }

    /**
     * Método público para cargar Continue Watching desde la UI (CatalogScreen).
     * Útil cuando el ViewModel se crea antes de que el perfil esté seleccionado.
     */
    fun loadContinueWatchingData() {
        viewModelScope.launch {
            loadContinueWatching()
        }
    }

    fun addToWatchLater(item: ContentItem) {
        if (!_watchLater.contains(item)) {
            _watchLater.add(item)
        }
    }
}
