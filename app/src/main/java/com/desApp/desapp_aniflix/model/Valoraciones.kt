package com.desApp.desapp_aniflix.model

data class Valoraciones(
    val id: String = "",
    val profileId: String = "",
    val contentId: String = "",
    val contentType: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: String? = null
)

/** Respuesta de GET /api/valoraciones (lista). */
data class ValoracionesListResponse(
    val success: Boolean,
    val data: List<Valoraciones>,
    val stats: ValoracionStats? = null
)

/** Estadísticas de las valoraciones (promedio, total, etc). */
data class ValoracionStats(
    val total: Int = 0,
    val average: Double = 0.0
)

/** Respuesta de POST/PUT (una sola valoracion). */
data class ValoracionesSingleResponse(
    val success: Boolean,
    val data: Valoraciones? = null,
    val message: String? = null
)

/** Body que ENVIAMOS al crear/actualizar una valoracion. */
data class ValoracionesRequest(
    val profileId: String,
    val contentId: String,
    val contentType: String,
    val rating: Int,
    val comment: String = ""
)

/** Respuesta de DELETE (propia del módulo, sin reutilizar valoracion). */
data class ValoracionesDeleteResponse(
    val success: Boolean,
    val message: String? = null
)
