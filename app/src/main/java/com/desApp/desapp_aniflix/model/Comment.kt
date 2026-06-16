// =============================================================================
// Comment.kt — Modelos de COMENTARIOS / RESEÑAS por episodio
// =============================================================================
// Cada comentario es una "reseña" de un episodio (o de una película): lleva
// texto y, opcionalmente, una valoración de 1 a 5 estrellas.
//
// Va atado a la CUENTA (uid) y muestra el nombre de usuario de la cuenta
// (displayName de Firebase), NO el del perfil. Así una misma cuenta no puede
// comentar varias veces cambiando de perfil.
//
// Colección Firestore: "comments"
//   { uid, username, contentId, contentType, seasonNumber, episodeNumber,
//     text, rating (1-5 o null), createdAt }
//
// Para películas se usa seasonNumber=0 y episodeNumber=0 (un único hilo).
// =============================================================================

package com.desApp.desapp_aniflix.model

/** Un comentario/reseña individual. */
data class Comment(
    val id: String = "",
    val uid: String = "",                 // dueño (cuenta) — para mostrar botón de borrar al autor
    val username: String = "",            // nombre de usuario de la cuenta (displayName)
    val contentId: String = "",
    val contentType: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val text: String = "",
    val rating: Int? = null,              // 1-5 estrellas, o null si no valoró
    val createdAt: String? = null
)

/** Estadísticas de un episodio: cantidad de comentarios y promedio de estrellas. */
data class CommentStats(
    val count: Int = 0,
    val average: Double = 0.0,
    val ratingCount: Int = 0
)

/** Respuesta de GET /api/comments (lista + stats del episodio). */
data class CommentsResponse(
    val success: Boolean,
    val data: List<Comment>,
    val stats: CommentStats? = null
)

/** Valoración global del contenido (promedio de todos sus episodios). */
data class CommentSummary(
    val average: Double = 0.0,
    val ratingCount: Int = 0,
    val commentCount: Int = 0
)

/** Respuesta de GET /api/comments/summary. */
data class CommentSummaryResponse(
    val success: Boolean,
    val data: CommentSummary
)

/** Body de POST /api/comments. El uid y username los pone el backend desde el token. */
data class AddCommentRequest(
    val contentId: String,
    val contentType: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val text: String,
    val rating: Int? = null
)

/** Respuesta de POST /api/comments. */
data class CommentSingleResponse(
    val success: Boolean,
    val data: Comment? = null,
    val message: String? = null
)
