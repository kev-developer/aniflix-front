// =============================================================================
// Note.kt — MÓDULO "Notas" — MODELOS (entidad + request/response)
// =============================================================================
// Igual que los demás módulos REALES (Favoritos, Comentarios): la entidad +
// los wrappers que Retrofit/Gson usan para traducir el JSON del backend.
//
// Colección Firestore: "notes" → { profileId, title, text, createdAt }
// =============================================================================

package com.desApp.desapp_aniflix.model

/** Una nota individual (la entidad). */
data class Note(
    val id: String = "",
    val profileId: String = "",
    val title: String = "",
    val text: String = "",
    val createdAt: String? = null
)

/** Respuesta de GET /api/notes (lista). */
data class NoteListResponse(
    val success: Boolean,
    val data: List<Note>,
    val count: Int? = null
)

/** Respuesta de POST/PUT (una sola nota). */
data class NoteSingleResponse(
    val success: Boolean,
    val data: Note? = null,
    val message: String? = null
)

/** Body que ENVIAMOS al crear/actualizar una nota. */
data class NoteRequest(
    val profileId: String,
    val title: String,
    val text: String
)

/** Respuesta de DELETE (propia del módulo, sin reutilizar nada). */
data class NoteDeleteResponse(
    val success: Boolean,
    val message: String? = null
)
