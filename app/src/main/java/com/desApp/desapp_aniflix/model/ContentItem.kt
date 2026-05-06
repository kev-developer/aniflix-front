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
    val type: String? = null
)

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
