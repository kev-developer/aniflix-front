package com.desApp.desapp_aniflix.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desApp.desapp_aniflix.auth.ProfileManager
import android.util.Log
import com.desApp.desapp_aniflix.model.AddFavoriteRequest
import com.desApp.desapp_aniflix.model.ContentItem
import com.desApp.desapp_aniflix.model.ContinueWatchingItem
import com.desApp.desapp_aniflix.model.FavoriteItem
import com.desApp.desapp_aniflix.model.GenreItem
import com.desApp.desapp_aniflix.network.ContentRetrofitClient
import com.desApp.desapp_aniflix.network.FavoritesRetrofitClient
import com.desApp.desapp_aniflix.network.HistoryRetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // ── Favorites (backed by backend) ─────────────────────────────────────────
    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    /** Full list of FavoriteItem objects from the backend */
    val favorites: StateFlow<List<FavoriteItem>> = _favorites

    /** Flat list of ContentItem objects (derived from favorites) for display */
    val watchLater: List<ContentItem>
        get() = _favorites.value.mapNotNull { it.content }

    private val _favoriteError = MutableStateFlow<String?>(null)
    val favoriteError: StateFlow<String?> = _favoriteError

    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching

    // ── Search state ─────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<ContentItem>>(emptyList())
    val searchResults: StateFlow<List<ContentItem>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private var searchJob: Job? = null

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

                // Cargar favoritos del perfil actual
                loadFavorites()
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

    // ── Favorites methods (backend-backed) ─────────────────────────────────────

    /** Cargar favoritos del perfil actual desde el backend */
    fun loadFavorites() {
        viewModelScope.launch {
            val profileId = ProfileManager.currentProfileId
            Log.d("Favorites", "loadFavorites() called, profileId=$profileId")
            if (profileId == null) {
                Log.w("Favorites", "loadFavorites() skipped: profileId is null")
                return@launch
            }
            try {
                val response = FavoritesRetrofitClient.favoritesApiService.getFavorites(profileId)
                Log.d("Favorites", "loadFavorites() success: count=${response.count}, data.size=${response.data.size}")
                _favorites.value = response.data
                _favoriteError.value = null
            } catch (e: Exception) {
                Log.e("Favorites", "loadFavorites() FAILED: ${e::class.simpleName}: ${e.message}", e)
                _favoriteError.value = "Error al cargar favoritos: ${e.message}"
            }
        }
    }

    /**
     * Alternar favorito: si existe lo elimina, si no lo agrega.
     * Retorna true si después del toggle el contenido está en favoritos.
     */
    fun toggleFavorite(item: ContentItem, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val profileId = ProfileManager.currentProfileId
            Log.d("Favorites", "toggleFavorite() called: item.id=${item.id}, title=${item.title}, profileId=$profileId")
            if (profileId == null) {
                Log.w("Favorites", "toggleFavorite() skipped: profileId is null")
                onResult(false)
                return@launch
            }
            try {
                // Buscar si ya está en favoritos
                val existing = _favorites.value.find { it.contentId == item.id }
                Log.d("Favorites", "toggleFavorite() existing=${existing?.id ?: "null"} (contentId=${item.id})")
                if (existing != null) {
                    // Ya está → eliminar
                    Log.d("Favorites", "toggleFavorite() → REMOVE: docId=${existing.id}")
                    val removeResponse = FavoritesRetrofitClient.favoritesApiService.removeFavorite(existing.id)
                    Log.d("Favorites", "toggleFavorite() remove response: success=${removeResponse.success}, message=${removeResponse.message}")
                    _favorites.value = _favorites.value.filter { it.id != existing.id }
                    _favoriteError.value = null
                    onResult(false)
                } else {
                    // No está → agregar
                    val contentType = if (item.contentType == "serie") "serie" else "pelicula"
                    val request = AddFavoriteRequest(
                        profileId = profileId,
                        contentId = item.id,
                        contentType = contentType
                    )
                    Log.d("Favorites", "toggleFavorite() → ADD: profileId=$profileId, contentId=${item.id}, contentType=$contentType")
                    val addResponse = FavoritesRetrofitClient.favoritesApiService.addFavorite(request)
                    Log.d("Favorites", "toggleFavorite() add response: success=${addResponse.success}, id=${addResponse.data?.id}, message=${addResponse.message}")
                    // Recargar favoritos para obtener los datos completos del contenido
                    loadFavorites()
                    _favoriteError.value = null
                    onResult(true)
                }
            } catch (e: Exception) {
                Log.e("Favorites", "toggleFavorite() FAILED: ${e::class.simpleName}: ${e.message}", e)
                _favoriteError.value = "Error al alternar favorito: ${e.message}"
                onResult(false)
            }
        }
    }

    /** Verificar si un contenido está en favoritos */
    fun isFavorite(contentId: String): Boolean {
        return _favorites.value.any { it.contentId == contentId }
    }

    /** Obtener el FavoriteItem de un contenido (para obtener su ID de documento) */
    fun getFavoriteItem(contentId: String): FavoriteItem? {
        return _favorites.value.find { it.contentId == contentId }
    }

    // ── Search methods ────────────────────────────────────────────────────────

    /** Actualiza el query de búsqueda con debounce de 300ms */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            performSearch(query)
        }
    }

    /** Ejecuta la búsqueda en el backend */
    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        try {
            val response = ContentRetrofitClient.contentApiService.search(
                query = query,
                limit = 20
            )
            _searchResults.value = response.data
        } catch (_: Exception) {
            _searchResults.value = emptyList()
        } finally {
            _isSearching.value = false
        }
    }

    /** Limpia la búsqueda y vuelve al catálogo normal */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
        searchJob?.cancel()
    }
}
