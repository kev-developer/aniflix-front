package com.desApp.desapp_aniflix.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.model.Anime
import com.desApp.desapp_aniflix.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CatalogViewModel : ViewModel() {
    private val _animes = MutableStateFlow<List<Anime>>(emptyList())
    val animes: StateFlow<List<Anime>> = _animes

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _watchLater = mutableStateListOf<Anime>()
    val watchLater: List<Anime> = _watchLater

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val response = RetrofitClient.animeApiService.getAnimes()
                _animes.value = response
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addToWatchLater(anime: Anime) {
        if (!_watchLater.contains(anime)) {
            _watchLater.add(anime)
        }
    }
}
