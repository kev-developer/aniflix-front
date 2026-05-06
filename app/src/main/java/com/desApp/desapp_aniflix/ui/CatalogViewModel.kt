package com.desApp.desapp_aniflix.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.model.GenreItem
import com.desApp.desapp_aniflix.network.ContentRetrofitClient
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

    private val _watchLater = mutableStateListOf<ContentItem>()
    val watchLater: List<ContentItem> = _watchLater

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val recentResponse = ContentRetrofitClient.contentApiService.getRecent(50)
                _contentItems.value = recentResponse.data

                val genresResponse = ContentRetrofitClient.contentApiService.getGenres()
                _genres.value = genresResponse.data
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addToWatchLater(item: ContentItem) {
        if (!_watchLater.contains(item)) {
            _watchLater.add(item)
        }
    }
}
