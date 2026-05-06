package com.desApp.desapp_aniflix.model

/**
 * Modelo unificado para series y películas desde el backend real.
 * contentType: "serie" para series, "pelicula" para películas/animes.
 */
data class ContentItem(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val thumbnail: String? = null,
    val coverImage: String? = null,
    val genres: List<String>? = null,
    val createdAt: String? = null,
    val contentType: String = "",
    val seasonCount: Int? = null,
    val videoUrl: String? = null,
    val views: Int? = null,
    val type: String? = null,
    // Campos para series con temporadas/episodios
    val seasons: List<Season>? = null,
    val currentEpisode: Episode? = null
)

data class Season(
    val number: Int? = null,
    val episodes: List<Episode>? = null
)

data class Episode(
    val number: Int? = null,
    val title: String? = null,
    val videoUrl: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

// ─── Response wrappers ─────────────────────────────────────────────────────────

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

// ─── Continue Watching ─────────────────────────────────────────────────────────

data class ContinueWatchingItem(
    val id: String = "",
    val profileId: String = "",
    val contentId: String = "",
    val contentType: String = "",
    val progress: Double = 0.0,
    val lastWatched: String? = null,
    val completed: Boolean = false,
    val duration: Long? = null,
    val currentEpisode: Episode? = null,
    val content: ContentItem? = null
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
