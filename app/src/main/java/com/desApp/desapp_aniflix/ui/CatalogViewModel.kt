// =============================================================================
// CatalogViewModel.kt — ViewModel PRINCIPAL de la aplicación
// =============================================================================
// ¿QUÉ ES ESTO?
//   Es el ViewModel central que maneja TODO el estado del catálogo de Aniflix.
//   Un ViewModel es una clase de Android que sobrevive a los cambios de
//   configuración (rotación de pantalla, etc.) y mantiene los datos en memoria.
//
// ¿QUÉ HACE?
//   - Carga el contenido reciente (series y películas) desde el BACKEND
//   - Carga los géneros disponibles
//   - Carga los favoritos del perfil actual (vía backend → Firestore)
//   - Carga el "Continue Watching" (seguir viendo) del perfil actual
//   - Maneja la búsqueda de contenido con debounce
//   - Permite alternar favoritos (agregar/remover)
//
// ARQUITECTURA DE 3 CAPAS:
//   CatalogViewModel  ──llama──>  Retrofit (ApiService)  ──HTTP──>  Backend Node.js  ──Firestore SDK──>  Firebase
//   (Android/Kotlin)         (Interfaz de red)              (Render.com)              (Admin SDK)
//
//   ⚠️ IMPORTANTE: CatalogViewModel NUNCA habla directamente con Firebase.
//      Solo usa Retrofit para comunicarse con el backend, y el backend
//      se comunica con Firestore usando Firebase Admin SDK.
//
// ¿QUÉ ES StateFlow?
//   StateFlow es un tipo de Kotlin que permite observar datos de forma reactiva.
//   _contentItems (MutableStateFlow) = el estado PRIVADO que puede cambiar
//   contentItems (StateFlow) = el estado PÚBLICO de solo lectura
//   La UI (Compose) se SUSCRIBE usando .collectAsState() y se RECOMPONE
//   automáticamente cuando los datos cambian.
// =============================================================================
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

/**
 * ViewModel principal del catálogo.
 *
 * Contiene toda la lógica de negocio para la pantalla de inicio (CatalogScreen),
 * búsqueda (SearchScreen), detalle (DetailScreen) y favoritos (FavoritesScreen).
 *
 * Se crea en [MainActivity.AniflixApp] con `viewModel()` a nivel de actividad,
 * por lo que SOBREVIVE a la navegación y los cambios de configuración.
 *
 * ╔══════════════════════════════════════════════════════════════════════╗
 * ║  FLUJO COMPLETO: Android → Backend → Firebase                       ║
 * ║                                                                      ║
 * ║  1. UI (Compose) llama a viewModel.refresh()                        ║
 * ║  2. ViewModel lanza corrutina en viewModelScope                     ║
 * ║  3. Llama a ContentRetrofitClient.contentApiService.getRecent(50)  ║
 * ║  4. Retrofit hace HTTP GET al backend (Render.com)                  ║
 * ║  5. Backend recibe, llama a Firebase Admin SDK                      ║
 * ║  6. Firebase Admin SDK consulta Firestore                           ║
 * ║  7. Backend serializa, responde JSON                                ║
 * ║  8. Retrofit deserializa JSON a objetos Kotlin (ContentItem)        ║
 * ║  9. ViewModel actualiza StateFlow                                   ║
 * ║  10. Compose se recompone porque observa el StateFlow               ║
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
class CatalogViewModel : ViewModel() {

    // ═════════════════════════════════════════════════════════════════════
    //  ESTADOS DEL VIEWMODEL (StateFlow)
    // ═════════════════════════════════════════════════════════════════════
    // PATRÓN: _nombre (MutableStateFlow) = privado, permite escritura
    //         nombre (StateFlow) = público, solo lectura para la UI
    //
    // La UI se suscribe así:
    //   val data by viewModel.nombre.collectAsState()
    //   Esto hace que el Composable se RECOMPONGA cuando el valor cambie
    // ═════════════════════════════════════════════════════════════════════

    // ── Contenido reciente (series + películas) ──────────────────────────────
    private val _contentItems = MutableStateFlow<List<ContentItem>>(emptyList())
    /** Lista de contenido reciente (se llena desde GET /api/content/recent) */
    val contentItems: StateFlow<List<ContentItem>> = _contentItems

    // ── Géneros ──────────────────────────────────────────────────────────────
    private val _genres = MutableStateFlow<List<GenreItem>>(emptyList())
    /** Lista de géneros disponibles (GET /api/genres) */
    val genres: StateFlow<List<GenreItem>> = _genres

    // ── Estados de UI ────────────────────────────────────────────────────────
    private val _isRefreshing = MutableStateFlow(false)
    /** true mientras se está refrescando (pull-to-refresh) */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoading = MutableStateFlow(true)
    /** true durante la carga inicial */
    val isLoading: StateFlow<Boolean> = _isLoading

    // ── Favoritos ("Mi lista") ───────────────────────────────────────────────
    // Estos datos vienen del backend, que a su vez los obtiene de Firestore
    // colección "favorites". Cada favorito tiene un profileId, contentId, contentType.
    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    /** Lista completa de FavoriteItem desde el backend (Firestore) */
    val favorites: StateFlow<List<FavoriteItem>> = _favorites

    /**
     * Lista plana de objetos ContentItem derivada de favorites.
     *
     * ⚠️ IMPORTANTE: Esto NO es un StateFlow, es una propiedad calculada (get()).
     * Por eso en CatalogScreen usamos `remember(favorites) { ... }` para
     * que Compose sepa que debe recalcular watchLater cuando favorites cambie.
     *
     * Antes esto causaba un BUG: "Mi lista" no aparecía al cargar la página
     * porque Compose no sabía que watchLater dependía de favorites.
     */
    val watchLater: List<ContentItem>
        get() = _favorites.value.mapNotNull { it.content }

    private val _favoriteError = MutableStateFlow<String?>(null)
    /** Mensaje de error de la última operación de favoritos */
    val favoriteError: StateFlow<String?> = _favoriteError

    // ── Continue Watching (Seguir viendo) ────────────────────────────────────
    private val _continueWatching = MutableStateFlow<List<ContinueWatchingItem>>(emptyList())
    /** Contenido que el usuario empezó a ver pero no terminó */
    val continueWatching: StateFlow<List<ContinueWatchingItem>> = _continueWatching

    // ── Búsqueda ─────────────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    /** El texto que el usuario escribe en la barra de búsqueda */
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<ContentItem>>(emptyList())
    /** Resultados de la búsqueda (GET /api/search?q=...) */
    val searchResults: StateFlow<List<ContentItem>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    /** true mientras se realiza la búsqueda */
    val isSearching: StateFlow<Boolean> = _isSearching

    // ── Resultados por género ─────────────────────────────────────────────────
    // Cuando el usuario hace clic en un chip de género sin escribir nada
    private val _genreResults = MutableStateFlow<List<ContentItem>>(emptyList())
    /** Contenido filtrado por género (local, sin llamar al backend) */
    val genreResults: StateFlow<List<ContentItem>> = _genreResults

    /** Job de la corrutina de búsqueda (para poder cancelarlo) */
    private var searchJob: Job? = null

    // ═════════════════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ═════════════════════════════════════════════════════════════════════
    // El bloque init se ejecuta CUANDO SE CREA EL VIEWMODEL.
    // En ese momento, puede que ProfileManager.currentProfileId sea null
    // (porque el usuario aún no ha seleccionado perfil).
    // Por eso loadFavorites() dentro de refresh() tiene un check de null.
    //
    // Luego, CatalogScreen llama a loadFavorites() y loadContinueWatchingData()
    // en un LaunchedEffect(Unit) para asegurar que se carguen cuando el perfil
    // ya esté seleccionado.
    init {
        refresh()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  REFRESH (carga completa del catálogo)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Carga TODO: contenido reciente, géneros, continue watching y favoritos.
     *
     * Se llama desde:
     *   - init (cuando se crea el ViewModel)
     *   - PullToRefreshBox.onRefresh (cuando el usuario hace pull-to-refresh)
     *
     * Flujo de red:
     *   1. GET /api/content/recent?limit=50 → ContentRetrofitClient
     *   2. GET /api/genres → ContentRetrofitClient (NO necesita auth)
     *   3. GET /api/history/continue-watching → HistoryRetrofitClient (SÍ necesita auth)
     *   4. GET /api/favorites?profileId=... → FavoritesRetrofitClient (SÍ necesita auth)
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _isRefreshing.value = true
            try {
                // ── 1. Cargar contenido reciente ──
                // Llama al backend (sin auth). El backend consulta Firestore
                // colecciones "series" y "movies", ordena por createdAt DESC.
                val recentResponse = ContentRetrofitClient.contentApiService.getRecent(50)
                _contentItems.value = recentResponse.data

                // ── 2. Cargar géneros ──
                // También sin auth. El backend consulta Firestore "genres".
                val genresResponse = ContentRetrofitClient.contentApiService.getGenres()
                _genres.value = genresResponse.data

                // ── 3. Cargar Continue Watching ──
                // Necesita auth (Firebase ID Token en header Authorization).
                // El backend consulta Firestore "continueWatching" filtrado por profileId.
                loadContinueWatching()

                // ── 4. Cargar favoritos ──
                // También necesita auth.
                // El backend consulta Firestore "favorites" filtrado por profileId.
                loadFavorites()
            } catch (e: Exception) {
                // Si algo falla, el StateFlow se queda con los últimos valores válidos
                Log.e("CatalogViewModel", "refresh() failed: ${e.message}", e)
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CONTINUE WATCHING
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Carga el "Continue Watching" del perfil actual desde el backend.
     *
     * Flujo: HistoryRetrofitClient → GET /api/history/continue-watching?profileId=X&limit=20
     *         → Backend (Node.js) → Firestore colección "continueWatching"
     *
     * La respuesta incluye: progress (0.0 a 1.0), currentEpisode (season, episode, title, videoUrl),
     * y content embedido (thumbnail, coverImage, title).
     */
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

    // ═════════════════════════════════════════════════════════════════════
    //  FAVORITOS (CRUD completo)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Cargar favoritos del perfil actual desde el backend.
     *
     * Flujo:
     *   FavoritesRetrofitClient (authInterceptor agrega Firebase ID Token)
     *   → GET /api/favorites?profileId=xxx
     *   → Backend: verifyToken middleware valida el token
     *   → Backend: consulta Firestore colección "favorites" WHERE profileId == xxx
     *   → Backend: hace POPULATE de los datos del contenido (join lógico)
     *   → Responde JSON con array de FavoriteItem (cada uno con su content embedido)
     *
     * ⚠️ NOTA: Si profileId es null (perfil no seleccionado), no hace nada.
     *    Esto pasaba antes al inicio y causaba que "Mi lista" no cargara.
     */
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
     *
     * CREATE (POST):   /api/favorites  body: { profileId, contentId, contentType }
     * DELETE (DELETE): /api/favorites/{docId}
     *
     * El onResult callback retorna true si después del toggle el contenido
     * está en favoritos (se usó para el corazón en DetailScreen).
     *
     * Flujo completo:
     *   1. Buscar en _favorites local si el contentId ya existe
     *   2. Si existe → DELETE /api/favorites/{id} → Firestore delete
     *   3. Si no existe → POST /api/favorites → Firestore create
     *   4. Actualizar _favorites StateFlow (reactivo → UI se actualiza sola)
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
                // Buscar si ya está en favoritos (en la lista local en memoria)
                val existing = _favorites.value.find { it.contentId == item.id }
                Log.d("Favorites", "toggleFavorite() existing=${existing?.id ?: "null"} (contentId=${item.id})")
                if (existing != null) {
                    // ── YA ESTÁ EN FAVORITOS → ELIMINAR ──
                    // DELETE /api/favorites/{docId}
                    // El backend elimina el documento de Firestore
                    Log.d("Favorites", "toggleFavorite() → REMOVE: docId=${existing.id}")
                    val removeResponse = FavoritesRetrofitClient.favoritesApiService.removeFavorite(existing.id)
                    Log.d("Favorites", "toggleFavorite() remove response: success=${removeResponse.success}, message=${removeResponse.message}")
                    _favorites.value = _favorites.value.filter { it.id != existing.id }
                    _favoriteError.value = null
                    onResult(false)
                } else {
                    // ── NO ESTÁ → AGREGAR ──
                    // POST /api/favorites body: { profileId, contentId, contentType }
                    // El backend crea un documento en Firestore colección "favorites"
                    val contentType = if (item.contentType == "serie") "serie" else "pelicula"
                    val request = AddFavoriteRequest(
                        profileId = profileId,
                        contentId = item.id,
                        contentType = contentType
                    )
                    Log.d("Favorites", "toggleFavorite() → ADD: profileId=$profileId, contentId=${item.id}, contentType=$contentType")
                    val addResponse = FavoritesRetrofitClient.favoritesApiService.addFavorite(request)
                    Log.d("Favorites", "toggleFavorite() add response: success=${addResponse.success}, id=${addResponse.data?.id}, message=${addResponse.message}")
                    // Recargar favoritos para obtener los datos completos del contenido (populate)
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

    /**
     * Verificar si un contenido está en favoritos.
     * Simple búsqueda local en el StateFlow _favorites (no hace llamada de red).
     */
    fun isFavorite(contentId: String): Boolean {
        return _favorites.value.any { it.contentId == contentId }
    }

    /**
     * Obtener el FavoriteItem de un contenido (para obtener su ID de documento).
     * Útil cuando necesitas el doc ID para operaciones DELETE.
     */
    fun getFavoriteItem(contentId: String): FavoriteItem? {
        return _favorites.value.find { it.contentId == contentId }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  BÚSQUEDA (con DEBOUNCE)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Actualiza el query de búsqueda con DEBOUNCE de 300ms.
     *
     * ¿Qué es debounce?
     *   Es una técnica para NO hacer una llamada HTTP cada vez que el usuario
     *   escribe una letra. En lugar de eso, esperamos 300ms después de la
     *   ÚLTIMA tecla presionada antes de hacer la búsqueda. Si el usuario
     *   vuelve a escribir dentro de esos 300ms, cancelamos el job anterior
     *   y empezamos a esperar de nuevo.
     *
     * Así evitamos hacer 20 llamadas al backend mientras el usuario escribe
     * "naruto" y solo hacemos 1 cuando termina de escribir.
     *
     * Flujo: SearchScreen → onSearchQueryChanged() → espera 300ms
     *        → ContentRetrofitClient.contentApiService.search(query, limit=20)
     *        → Backend GET /api/search?q=naruto&limit=20
     *        → Firestore: búsqueda con array-contains o regex en el título
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce de 300ms
            performSearch(query)
        }
    }

    /**
     * Ejecuta la búsqueda en el backend.
     * No necesita auth — usa ContentRetrofitClient (solo cloudFrontInterceptor).
     */
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

    /**
     * Filtra contenido LOCALMENTE por género (sin llamar al backend).
     *
     * Esto es más rápido que hacer una nueva llamada HTTP. Simplemente
     * filtramos el _contentItems (que ya tenemos en memoria) por genreId.
     *
     * Se usa en SearchScreen cuando el usuario toca un chip de género
     * sin haber escrito nada en la barra de búsqueda.
     */
    fun filterByGenre(genreId: String) {
        _genreResults.value = _contentItems.value.filter { item ->
            item.genres?.contains(genreId) == true
        }
    }

    /** Limpia el filtro de género (vuelve a mostrar sugerencias) */
    fun clearGenreResults() {
        _genreResults.value = emptyList()
    }

    /** Limpia la búsqueda y vuelve al catálogo normal */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _genreResults.value = emptyList()
        _isSearching.value = false
        searchJob?.cancel()
    }
}
