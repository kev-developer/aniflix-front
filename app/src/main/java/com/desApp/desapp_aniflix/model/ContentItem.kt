// =============================================================================
// ContentItem.kt — MODELOS DE DATOS (DATA CLASSES)
// =============================================================================
// PROPÓSITO:
//   Define TODAS las estructuras de datos (modelos) que se usan en la app.
//   Estas data classes se corresponden con los documentos JSON que devuelve
//   el backend (y por tanto, con los documentos de Firestore).
//
//   Gson convierte automáticamente el JSON del backend a estas clases Kotlin.
//   Ej: JSON { "success": true, "data": [{ "id": "...", "title": "..." }] }
//       → ContentResponse(success=true, data=listOf(ContentItem(id=..., title=...)))
//
// RELACIÓN CON FIRESTORE:
//   Colección "series"       → ContentItem (con contentType="serie", seasons)
//   Colección "animes"       → ContentItem (con contentType="pelicula")
//   Colección "genres"       → GenreItem (id, name)
//   Colección "favorites"    → FavoriteItem (con ContentItem embebido en "content")
//   Colección "continueWatching" → ContinueWatchingItem
//   Colección "profiles"     → UserProfile (en UserProfile.kt)
// =============================================================================

package com.desApp.desapp_aniflix.model

/**
 * ContentItem — modelo UNIFICADO para series y películas.
 *
 * contentType determina el tipo:
 *   "serie"    → Es una serie con temporadas (seasons) y episodios
 *   "pelicula" → Es una película/anime con un solo video (videoUrl)
 *
 * Los campos con ? son nullable (pueden venir null desde Firestore).
 */
data class ContentItem(
    val id: String = "",               // Document ID en Firestore
    val title: String = "",             // Título del contenido
    val description: String? = null,    // Descripción/sinopsis
    val thumbnail: String? = null,      // URL del thumbnail (poster pequeño)
    val coverImage: String? = null,     // URL de la imagen de portada (grande)
    val genres: List<String>? = null,   // Lista de IDs de género (referencias a colección "genres")
    val createdAt: String? = null,      // Fecha de creación (ISO string)
    val contentType: String = "",       // "serie" o "pelicula"
    val seasonCount: Int? = null,       // Número de temporadas (solo en listados)
    val videoUrl: String? = null,       // URL del video (para películas)
    val views: Int? = null,             // Contador de vistas
    val type: String? = null,           // Tipo adicional (ej: "pelicula" en colección "animes")
    val seasons: List<Season>? = null,  // Temporadas (solo para series, en detalle)
    val currentEpisode: Episode? = null // Episodio actual (para reanudar reproducción)
)

/** Temporada de una serie */
data class Season(
    val number: Int? = null,              // Número de temporada (1, 2, 3...)
    val episodes: List<Episode>? = null   // Episodios de esta temporada
)

/** Episodio individual */
data class Episode(
    val number: Int? = null,              // Número de episodio dentro de la temporada
    val title: String? = null,            // Título del episodio
    val videoUrl: String? = null,         // Ruta del video en S3 (ej: "videos/series/...")
    val seasonNumber: Int? = null,        // Número de temporada (para continue watching)
    val episodeNumber: Int? = null        // Número de episodio (para continue watching)
)

// ─── RESPONSE WRAPPERS ─────────────────────────────────────────────────────────
// Cada endpoint devuelve una estructura JSON con { success, data, ... }
// Estos wrappers permiten deserializar correctamente esa estructura.

data class ContentResponse(
    val success: Boolean,
    val data: List<ContentItem>,
    val count: Int? = null
)

data class SingleContentResponse(
    val success: Boolean,
    val data: ContentItem
)

data class GenreItem(
    val id: String = "",
    val name: String = ""
)

data class GenreResponse(
    val success: Boolean,
    val data: List<GenreItem>,
    val count: Int? = null
)

// ─── CONTINUE WATCHING ─────────────────────────────────────────────────────────

/**
 * ContinueWatchingItem — registro de "seguir viendo" desde Firestore.
 * Contiene el progreso del usuario en un contenido específico.
 */
data class ContinueWatchingItem(
    val id: String = "",                    // Document ID en Firestore
    val profileId: String = "",             // ID del perfil
    val contentId: String = "",             // ID del contenido
    val contentType: String = "",           // "serie" o "pelicula"
    val progress: Double = 0.0,             // Progreso: 0.0 a 1.0 (0% a 100%)
    val lastWatched: String? = null,        // Última vez que se vio (ISO string)
    val completed: Boolean = false,         // Si ya se completó
    val duration: Long? = null,             // Duración total en ms
    val currentEpisode: Episode? = null,    // Episodio actual (solo para series)
    val content: ContentItem? = null        // Datos del contenido (embebido)
)

data class ContinueWatchingListResponse(
    val success: Boolean,
    val data: List<ContinueWatchingItem>,
    val count: Int? = null
)

data class UpdateContinueWatchingRequest(
    val profileId: String,
    val contentId: String,
    val contentType: String,
    val progress: Double,
    val duration: Long? = null,
    val currentEpisode: Episode? = null
)

data class ContinueWatchingSingleResponse(
    val success: Boolean,
    val data: ContinueWatchingItem? = null,
    val message: String? = null
)

data class DeleteResponse(
    val success: Boolean,
    val message: String? = null
)

// ─── FAVORITES ─────────────────────────────────────────────────────────────────

/**
 * FavoriteItem — registro de favorito desde Firestore.
 * content está embebido para mostrar datos del contenido sin JOIN.
 */
data class FavoriteItem(
    val id: String = "",                    // Document ID en Firestore
    val profileId: String = "",             // ID del perfil
    val contentId: String = "",             // ID del contenido favorito
    val contentType: String = "",           // "serie" o "pelicula"
    val createdAt: String? = null,          // Fecha en que se añadió
    val content: ContentItem? = null        // Datos del contenido (embebido al añadir)
)

data class FavoritesResponse(
    val success: Boolean,
    val data: List<FavoriteItem>,
    val count: Int? = null
)

data class FavoriteCheckResponse(
    val success: Boolean,
    val data: FavoriteCheckData
)

data class FavoriteCheckData(
    val isFavorite: Boolean,
    val favorite: FavoriteItem? = null
)

data class AddFavoriteRequest(
    val profileId: String,
    val contentId: String,
    val contentType: String
)

data class FavoriteSingleResponse(
    val success: Boolean,
    val data: FavoriteItem? = null,
    val message: String? = null
)
